package com.chunbo.medical.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 多模态附件上传与解析服务（图片 / Excel）
 * <p>
 * 三个业务智能体（云诊所 / OA 中台 / 商城）共用：
 * 1. 图片：上传后由 {@link #toImageMedia} 转成 Spring AI {@link Media}，随用户消息一起送入大模型做视觉识别；
 * 2. Excel / 表格：由 {@link #excelToMarkdown} 解析成 Markdown 表格文本，拼进 system prompt 供 LLM 提取后走 function-calling。
 * <p>
 * 文件以 fileId + 扩展名落盘到 uploads/attachments，读取时按 fileId 前缀匹配（重启不丢）。
 */
@Service
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    /** 附件落盘目录（绝对路径，基于进程工作目录，避免 Tomcat 临时目录导致的相对路径错乱） */
    private static final Path UPLOAD_DIR = Paths.get(System.getProperty("user.dir", "."), "uploads", "attachments");

    /** 支持的图片扩展名 → MimeType */
    private static final Map<String, MimeType> IMAGE_TYPES = new LinkedHashMap<>();

    static {
        IMAGE_TYPES.put("png", MimeTypeUtils.IMAGE_PNG);
        IMAGE_TYPES.put("jpg", MimeTypeUtils.IMAGE_JPEG);
        IMAGE_TYPES.put("jpeg", MimeTypeUtils.IMAGE_JPEG);
        IMAGE_TYPES.put("webp", MimeTypeUtils.parseMimeType("image/webp"));
        IMAGE_TYPES.put("gif", MimeTypeUtils.IMAGE_GIF);
    }

    /**
     * 上传附件并落盘，返回 fileId（后续聊天接口用 attachmentId 引用）
     */
    public Map<String, Object> upload(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        if (file == null || file.isEmpty()) {
            result.put("success", false);
            result.put("message", "上传文件为空");
            return result;
        }
        try {
            String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            String ext = extOf(original);
            String fileId = UUID.randomUUID().toString().replace("-", "");
            Path dir = UPLOAD_DIR;
            Files.createDirectories(dir);
            Path target = dir.resolve(fileId + (ext.isEmpty() ? "" : "." + ext));
            file.transferTo(target.toFile());

            result.put("success", true);
            result.put("fileId", fileId);
            result.put("fileName", original);
            result.put("fileType", classify(ext));
            result.put("size", file.getSize());
            // 持久访问 URL（/uploads/** 已做静态映射且 JWT 放行）：会话历史里用它，重启后图片仍可显示
            result.put("url", "/uploads/attachments/" + target.getFileName().toString());
            // Excel 同步返回 Markdown 表格预览，前端气泡直接渲染真实表格
            if ("excel".equals(classify(ext))) {
                result.put("excelPreview", excelToMarkdown(fileId));
            }
            log.info("[附件上传] fileId={} 文件名={} 类型={} 大小={}字节", fileId, original, classify(ext), file.getSize());
        } catch (Exception e) {
            log.error("附件上传失败", e);
            result.put("success", false);
            result.put("message", "上传失败: " + e.getMessage());
        }
        return result;
    }

    /** 按 fileId 找到落盘文件（容忍扩展名差异） */
    private Path resolveFile(String fileId) {
        Path dir = UPLOAD_DIR;
        if (!Files.isDirectory(dir)) return null;
        try {
            try (var stream = Files.list(dir)) {
                return stream
                        .filter(p -> p.getFileName().toString().startsWith(fileId + "."))
                        .findFirst()
                        .orElse(null);
            }
        } catch (IOException e) {
            return null;
        }
    }

    /** 读取文件字节 */
    public byte[] readBytes(String fileId) {
        if (fileId == null || fileId.isBlank()) return null;
        Path p = resolveFile(fileId);
        if (p == null) return null;
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            log.warn("读取附件失败 fileId={}: {}", fileId, e.getMessage());
            return null;
        }
    }

    /** 判断 fileId 对应的附件是否为图片 */
    public boolean isImage(String fileId) {
        Path p = resolveFile(fileId);
        if (p == null) return false;
        String ext = extOf(p.getFileName().toString());
        return IMAGE_TYPES.containsKey(ext);
    }

    /** 判断 fileId 对应的附件是否为 Excel */
    public boolean isExcel(String fileId) {
        Path p = resolveFile(fileId);
        if (p == null) return false;
        String ext = extOf(p.getFileName().toString());
        return "xlsx".equals(ext) || "xls".equals(ext);
    }

    /** 图片转 Spring AI Media（送入多模态大模型做视觉识别） */
    public Media toImageMedia(String fileId) {
        byte[] bytes = readBytes(fileId);
        if (bytes == null) return null;
        Path p = resolveFile(fileId);
        String ext = p == null ? "png" : extOf(p.getFileName().toString());
        MimeType mime = IMAGE_TYPES.getOrDefault(ext, MimeTypeUtils.IMAGE_PNG);
        return new Media(mime, new ByteArrayResource(bytes));
    }

    /** Excel 解析成 Markdown 表格文本（供 LLM 提取员工姓名/金额等字段） */
    public String excelToMarkdown(String fileId) {
        byte[] bytes = readBytes(fileId);
        if (bytes == null) return "";
        Path p = resolveFile(fileId);
        String ext = p == null ? "xlsx" : extOf(p.getFileName().toString());
        try (Workbook wb = "xls".equals(ext)
                ? new HSSFWorkbook(new ByteArrayInputStream(bytes))
                : new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            int sheetCount = wb.getNumberOfSheets();
            for (int s = 0; s < sheetCount; s++) {
                Sheet sheet = wb.getSheetAt(s);
                if (sheetCount > 1) sb.append("### 工作表：").append(sheet.getSheetName()).append("\n");
                int maxRow = Math.min(sheet.getLastRowNum(), 200);
                for (int r = 0; r <= maxRow; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    List<String> cells = new ArrayList<>();
                    int lastCell = row.getLastCellNum();
                    for (int c = 0; c < lastCell; c++) {
                        Cell cell = row.getCell(c);
                        cells.add(cell == null ? "" : cellText(cell));
                    }
                    // 去掉行尾空单元格
                    while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) cells.remove(cells.size() - 1);
                    if (cells.isEmpty()) continue;
                    sb.append("| ").append(String.join(" | ", cells)).append(" |\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Excel 解析失败 fileId={}: {}", fileId, e.getMessage());
            return "";
        }
    }

    /**
     * Excel 解析成结构化行记录（确定性发工资用，不依赖 LLM）
     * 表头定位：前 20 行内同时含「姓名/工号」与「工资/金额」列；
     * 金额列优先「实发工资」→「应发工资」→ 任意含工资/金额的列。
     * 返回 [{employeeId, employeeName, department, amount}]，无表头返回 null。
     */
    public List<Map<String, Object>> excelToRows(String fileId) {
        byte[] bytes = readBytes(fileId);
        if (bytes == null) return null;
        Path p = resolveFile(fileId);
        String ext = p == null ? "xlsx" : extOf(p.getFileName().toString());
        try (Workbook wb = "xls".equals(ext)
                ? new HSSFWorkbook(new ByteArrayInputStream(bytes))
                : new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = null;
            int headerIdx = -1;
            for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 20); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                boolean hasName = false, hasAmount = false;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String t = cellText(row.getCell(c));
                    if (t.contains("姓名") || t.contains("工号")) hasName = true;
                    if (t.contains("工资") || t.contains("金额")) hasAmount = true;
                }
                if (hasName && hasAmount) { header = row; headerIdx = r; break; }
            }
            if (header == null) return null;
            int idCol = -1, nameCol = -1, deptCol = -1, amtCol = -1;
            for (int c = 0; c < header.getLastCellNum(); c++) {
                String t = cellText(header.getCell(c));
                if (idCol < 0 && t.contains("工号")) idCol = c;
                if (nameCol < 0 && t.contains("姓名")) nameCol = c;
                if (deptCol < 0 && t.contains("部门")) deptCol = c;
            }
            for (int c = 0; c < header.getLastCellNum(); c++) {
                String t = cellText(header.getCell(c));
                if (t.contains("实发") && t.contains("工资")) { amtCol = c; break; }
            }
            if (amtCol < 0) {
                for (int c = 0; c < header.getLastCellNum(); c++) {
                    String t = cellText(header.getCell(c));
                    if (t.contains("应发") && t.contains("工资")) { amtCol = c; break; }
                }
            }
            if (amtCol < 0) {
                for (int c = 0; c < header.getLastCellNum(); c++) {
                    String t = cellText(header.getCell(c));
                    if (t.contains("工资") || t.contains("金额")) { amtCol = c; break; }
                }
            }
            if ((idCol < 0 && nameCol < 0) || amtCol < 0) return null;
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int r = headerIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String idVal = idCol >= 0 ? cellText(row.getCell(idCol)) : "";
                String nameVal = nameCol >= 0 ? cellText(row.getCell(nameCol)) : "";
                String deptVal = deptCol >= 0 ? cellText(row.getCell(deptCol)) : "";
                String amtStr = cellText(row.getCell(amtCol));
                if (idVal.isBlank() && nameVal.isBlank() && amtStr.isBlank()) continue;
                Map<String, Object> d = new HashMap<>();
                d.put("employeeId", idVal);
                d.put("employeeName", nameVal);
                d.put("department", deptVal);
                d.put("amount", amtStr);
                rows.add(d);
            }
            return rows;
        } catch (Exception e) {
            log.warn("Excel 结构化解析失败 fileId={}: {}", fileId, e.getMessage());
            return null;
        }
    }

    /**
     * Excel 解析成商品结构化行（商品表格批量导入用，确定性不依赖 LLM）
     * 表头映射：商品名称/商品名/名称 → productName；类别/分类 → category；规格 → specification；
     * 厂家/生产厂家/制造商 → manufacturer；批发价/进货价 → wholesalePrice；
     * 零售价/售价/零售指导价 → retailPrice；数量/库存/入库数量 → quantity；图片链接/图片 → imageUrl。
     * 无表头返回 null。
     */
    public List<Map<String, Object>> excelToProductRows(String fileId) {
        byte[] bytes = readBytes(fileId);
        if (bytes == null) return null;
        Path p = resolveFile(fileId);
        String ext = p == null ? "xlsx" : extOf(p.getFileName().toString());
        try {
            return parseProductWorkbook(new ByteArrayInputStream(bytes), ext);
        } catch (Exception e) {
            log.warn("商品表解析失败 fileId={}: {}", fileId, e.getMessage());
            return null;
        }
    }

    /** 商品表工作簿解析（表头定位 + 列映射），工资表列映射不含时由调用方兜底 */
    private List<Map<String, Object>> parseProductWorkbook(java.io.InputStream in, String ext) throws Exception {
        try (Workbook wb = "xls".equals(ext)
                ? new HSSFWorkbook(in)
                : new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = null;
            int headerIdx = -1;
            for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 20); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String t = cellText(row.getCell(c));
                    if (t.contains("商品") || t.contains("名称")) { header = row; headerIdx = r; break; }
                }
                if (header != null) break;
            }
            if (header == null) return null;
            int nameCol = -1, catCol = -1, specCol = -1, mfrCol = -1, wsCol = -1, rtCol = -1, qtyCol = -1, imgCol = -1;
            for (int c = 0; c < header.getLastCellNum(); c++) {
                String t = cellText(header.getCell(c));
                if (nameCol < 0 && (t.contains("商品名称") || t.contains("商品名") || t.equals("名称") || t.contains("品名"))) nameCol = c;
                if (catCol < 0 && (t.contains("类别") || t.contains("分类"))) catCol = c;
                if (specCol < 0 && t.contains("规格")) specCol = c;
                if (mfrCol < 0 && (t.contains("厂家") || t.contains("制造商") || t.contains("生产"))) mfrCol = c;
                if (wsCol < 0 && (t.contains("批发") || t.contains("进货"))) wsCol = c;
                if (rtCol < 0 && (t.contains("零售") || t.contains("售价"))) rtCol = c;
                if (qtyCol < 0 && (t.contains("数量") || t.contains("库存") || t.contains("入库"))) qtyCol = c;
                if (imgCol < 0 && t.contains("图片")) imgCol = c;
            }
            if (nameCol < 0 || rtCol < 0) return null;
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int r = headerIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String name = cellText(row.getCell(nameCol));
                String retail = cellText(row.getCell(rtCol));
                if (name.isBlank() && retail.isBlank()) continue;
                Map<String, Object> d = new HashMap<>();
                d.put("productName", name);
                d.put("category", catCol >= 0 ? cellText(row.getCell(catCol)) : "");
                d.put("specification", specCol >= 0 ? cellText(row.getCell(specCol)) : "");
                d.put("manufacturer", mfrCol >= 0 ? cellText(row.getCell(mfrCol)) : "");
                d.put("wholesalePrice", wsCol >= 0 ? cellText(row.getCell(wsCol)) : "0");
                d.put("retailPrice", retail);
                d.put("quantity", qtyCol >= 0 ? cellText(row.getCell(qtyCol)) : "0");
                d.put("imageUrl", imgCol >= 0 ? cellText(row.getCell(imgCol)) : "");
                rows.add(d);
            }
            return rows;
        } catch (Exception e) {
            log.warn("商品表解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** MultipartFile 版商品表解析（页面批量导入接口直接传文件，不经落盘） */
    public List<Map<String, Object>> excelToProductRows(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try {
            return parseProductWorkbook(file.getInputStream(),
                    file.getOriginalFilename() == null ? "xlsx" : extOf(file.getOriginalFilename()));
        } catch (Exception e) {
            log.warn("商品表解析失败: {}", e.getMessage());
            return null;
        }
    }

    private String cellText(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                double v = cell.getNumericCellValue();
                // 整数不带小数点（如工号、金额的整数部分）
                if (v == Math.floor(v) && !Double.isInfinite(v)) {
                    return String.valueOf((long) v);
                }
                return String.valueOf(v);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }

    private String extOf(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }

    private String classify(String ext) {
        if (IMAGE_TYPES.containsKey(ext)) return "image";
        if ("xlsx".equals(ext) || "xls".equals(ext)) return "excel";
        return "file";
    }
}
