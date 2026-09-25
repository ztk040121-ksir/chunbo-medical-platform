package com.chunbo.medical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.MallOrder;
import com.chunbo.medical.entity.Medicine;
import com.chunbo.medical.entity.OaSalarySlip;
import com.chunbo.medical.mapper.MallOrderMapper;
import com.chunbo.medical.mapper.MedicineMapper;
import com.chunbo.medical.mapper.OaSalarySlipMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 数据导出中心：工资 / 进销存 / 订单统一导出 Excel（Apache POI）。
 * 统一成一个中心，避免各业务页零散导出。
 */
@Service
public class ExportService {

    @Autowired
    private OaSalarySlipMapper salarySlipMapper;

    @Autowired
    private MedicineMapper medicineMapper;

    @Autowired
    private MallOrderMapper mallOrderMapper;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 导出工资条 */
    public void exportSalary(HttpServletResponse response) throws Exception {
        List<OaSalarySlip> list = salarySlipMapper.selectList(
                new LambdaQueryWrapper<OaSalarySlip>().orderByDesc(OaSalarySlip::getCreateTime));
        String[] headers = {"工号", "姓名", "归属月份", "基本底薪", "门诊/电商提成", "贴敷理疗/合规奖", "社保代扣", "个税", "实发工资", "状态", "发放时间"};
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("工资发放台账");
        writeHeader(sheet, headers);
        int r = 1;
        for (OaSalarySlip s : list) {
            Row row = sheet.createRow(r++);
            int c = 0;
            row.createCell(c++).setCellValue(s.getDoctorId() == null ? "" : s.getDoctorId());
            row.createCell(c++).setCellValue(s.getDoctorName() == null ? "" : s.getDoctorName());
            row.createCell(c++).setCellValue(s.getSalaryMonth() == null ? "" : s.getSalaryMonth());
            row.createCell(c++).setCellValue(s.getBaseSalary() == null ? 0 : s.getBaseSalary().doubleValue());
            row.createCell(c++).setCellValue(s.getClinicCommission() == null ? 0 : s.getClinicCommission().doubleValue());
            row.createCell(c++).setCellValue(s.getPlasterCommission() == null ? 0 : s.getPlasterCommission().doubleValue());
            row.createCell(c++).setCellValue(s.getDeductionSocial() == null ? 0 : s.getDeductionSocial().doubleValue());
            row.createCell(c++).setCellValue(s.getTax() == null ? 0 : s.getTax().doubleValue());
            row.createCell(c++).setCellValue(s.getNetSalary() == null ? 0 : s.getNetSalary().doubleValue());
            row.createCell(c++).setCellValue(s.getStatus() == null ? "" : s.getStatus());
            row.createCell(c).setCellValue(s.getCreateTime() == null ? "" : s.getCreateTime().format(DT));
        }
        write(response, wb, "工资发放台账.xlsx");
    }

    /** 导出药品进销存（库存台账） */
    public void exportInventory(HttpServletResponse response) throws Exception {
        List<Medicine> list = medicineMapper.selectList(new LambdaQueryWrapper<Medicine>().orderByAsc(Medicine::getId));
        String[] headers = {"药品ID", "药品通用名", "商品规格", "当前库存", "单位", "剂型分类", "处方类别", "零售指导价", "安全预警线", "生产药企"};
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("药品进销存台账");
        writeHeader(sheet, headers);
        int r = 1;
        for (Medicine m : list) {
            Row row = sheet.createRow(r++);
            int c = 0;
            row.createCell(c++).setCellValue(m.getId() == null ? 0 : m.getId());
            row.createCell(c++).setCellValue(m.getName() == null ? "" : m.getName());
            row.createCell(c++).setCellValue(m.getSpecification() == null ? "" : m.getSpecification());
            row.createCell(c++).setCellValue(m.getStock() == null ? 0 : m.getStock());
            row.createCell(c++).setCellValue(m.getUnit() == null ? "盒" : m.getUnit());
            row.createCell(c++).setCellValue(m.getCategory() == null ? "" : m.getCategory());
            row.createCell(c++).setCellValue("1".equals(m.getIsPrescription()) ? "处方药" : "OTC");
            row.createCell(c++).setCellValue(m.getPrice() == null ? 0 : m.getPrice().doubleValue());
            row.createCell(c++).setCellValue(m.getWarningStock() == null ? 0 : m.getWarningStock());
            row.createCell(c).setCellValue(m.getManufacturer() == null ? "" : m.getManufacturer());
        }
        write(response, wb, "药品进销存台账.xlsx");
    }

    /** 导出商城订单 */
    public void exportOrders(HttpServletResponse response) throws Exception {
        List<MallOrder> list = mallOrderMapper.selectList(new LambdaQueryWrapper<MallOrder>().orderByDesc(MallOrder::getCreateTime));
        String[] headers = {"订单号", "所属机构", "买家", "订单总额", "优惠金额", "实付金额", "订单状态", "下单时间"};
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("商城订单台账");
        writeHeader(sheet, headers);
        int r = 1;
        for (MallOrder o : list) {
            Row row = sheet.createRow(r++);
            int c = 0;
            row.createCell(c++).setCellValue(o.getOrderNo() == null ? "" : o.getOrderNo());
            row.createCell(c++).setCellValue(o.getClinicName() == null ? "" : o.getClinicName());
            row.createCell(c++).setCellValue(o.getBuyerName() == null ? "" : o.getBuyerName());
            row.createCell(c++).setCellValue(o.getTotalAmount() == null ? 0 : o.getTotalAmount().doubleValue());
            row.createCell(c++).setCellValue(o.getDiscountAmount() == null ? 0 : o.getDiscountAmount().doubleValue());
            row.createCell(c++).setCellValue(o.getFinalAmount() == null ? 0 : o.getFinalAmount().doubleValue());
            row.createCell(c++).setCellValue(o.getStatus() == null ? "" : o.getStatus());
            row.createCell(c).setCellValue(o.getCreateTime() == null ? "" : o.getCreateTime().format(DT));
        }
        write(response, wb, "商城订单台账.xlsx");
    }

    @Autowired
    private com.chunbo.medical.mapper.PrescriptionMapper prescriptionMapper;

    /** 导出门诊处方记录台账 */
    public void exportPrescriptions(HttpServletResponse response) throws Exception {
        List<com.chunbo.medical.entity.Prescription> list = prescriptionMapper.selectList(
                new LambdaQueryWrapper<com.chunbo.medical.entity.Prescription>().orderByDesc(com.chunbo.medical.entity.Prescription::getCreateTime));
        String[] headers = {"处方单号", "患者姓名", "临床诊断", "接诊医生", "处方分类", "处方总额", "支付状态", "发药状态", "签字医生", "开方时间"};
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("门诊处方台账");
        writeHeader(sheet, headers);
        int r = 1;
        for (com.chunbo.medical.entity.Prescription p : list) {
            Row row = sheet.createRow(r++);
            int c = 0;
            row.createCell(c++).setCellValue(p.getPrescriptionNo() == null ? "" : p.getPrescriptionNo());
            row.createCell(c++).setCellValue(p.getPatientName() == null ? "" : p.getPatientName());
            row.createCell(c++).setCellValue(p.getDiagnosis() == null ? "" : p.getDiagnosis());
            row.createCell(c++).setCellValue(p.getDoctorName() == null ? "" : p.getDoctorName());
            row.createCell(c++).setCellValue(p.getType() == null ? "" : p.getType());
            row.createCell(c++).setCellValue(p.getTotalAmount() == null ? 0 : p.getTotalAmount().doubleValue());
            row.createCell(c++).setCellValue(p.getPayStatus() == null ? "待支付" : p.getPayStatus());
            row.createCell(c++).setCellValue("1".equals(p.getStatus()) ? "已发药" : ("2".equals(p.getStatus()) ? "已作废" : "待发药"));
            row.createCell(c++).setCellValue(p.getSignedBy() == null ? "" : p.getSignedBy());
            row.createCell(c).setCellValue(p.getCreateTime() == null ? "" : p.getCreateTime().format(DT));
        }
        write(response, wb, "门诊处方台账.xlsx");
    }

    private void writeHeader(Sheet sheet, String[] headers) {
        Row header = sheet.createRow(0);
        Font font = sheet.getWorkbook().createFont();
        font.setBold(true);
        CellStyle style = sheet.getWorkbook().createCellStyle();
        style.setFont(font);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void write(HttpServletResponse response, Workbook wb, String fileName) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, "UTF-8"));
        try (OutputStream out = response.getOutputStream()) {
            wb.write(out);
        } finally {
            wb.close();
        }
    }
}
