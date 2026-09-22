package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chunbo.medical.config.PasswordUtil;
import com.chunbo.medical.entity.*;
import com.chunbo.medical.enums.OrderStatusEnum;
import com.chunbo.medical.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminManageController {

    @Autowired
    private com.chunbo.medical.service.OaAssistantService oaAssistantService;

    @Autowired
    private com.chunbo.medical.service.FileUploadService fileUploadService;

    @Autowired
    private StaffAccountMapper staffAccountMapper;

    @Autowired
    private com.chunbo.medical.mapper.DoctorAccountMapper doctorAccountMapper;

    @Autowired
    private MallProductMapper mallProductMapper;

    @Autowired
    private MallUserMapper mallUserMapper;

    @Autowired
    private MallOrderMapper mallOrderMapper;

    @Autowired
    private InventoryRecordMapper inventoryRecordMapper;

    // ==========================================
    // 1. 人事账号注册与全员管理 (管理员最高权限)
    // ==========================================
    @PostMapping("/hr/register")
    public ResponseEntity<Map<String, Object>> registerHr(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "123456").trim();
        String realName = body.getOrDefault("realName", "").trim();
        String phone = body.getOrDefault("phone", "").trim();
        String department = body.getOrDefault("department", "\u4EBA\u4E8B\u884C\u653F\u79D1").trim();
        String title = body.getOrDefault("title", "\u4EBA\u4E8B\u4E3B\u7BA1").trim();

        Map<String, Object> res = new HashMap<>();
        if (username.isEmpty() || realName.isEmpty()) {
            res.put("success", false);
            res.put("message", "\u8BF7\u586B\u5199\u4EBA\u4E8B\u5DE5\u53F7\u4E0E\u771F\u5B9E\u59D3\u540D");
            return ResponseEntity.badRequest().body(res);
        }

        Long count = staffAccountMapper.selectCount(
                new LambdaQueryWrapper<StaffAccount>().eq(StaffAccount::getUsername, username)
        );
        if (count > 0) {
            res.put("success", false);
            res.put("message", "\u8D26\u53F7 [" + username + "] \u5DF2\u5B58\u5728\uFF0C\u8BF7\u66F4\u6362");
            return ResponseEntity.badRequest().body(res);
        }

        StaffAccount staff = new StaffAccount();
        staff.setUsername(username);
        staff.setPassword(PasswordUtil.encode(password));
        staff.setRealName(realName);
        staff.setStaffId("HR_" + (1000 + System.currentTimeMillis() % 9000));
        staff.setRole("HR");
        staff.setDepartment(department);
        staff.setTitle(title);
        staff.setPhone(phone);
        staff.setStatus("ENABLE");
        staff.setCreateTime(LocalDateTime.now());

        staffAccountMapper.insert(staff);

        res.put("success", true);
        res.put("message", "\u4EBA\u4E8B\u8D26\u53F7\u6CE8\u518C\u6210\u529F\uFF01\u8BE5\u4EBA\u4E8B\u53EF\u7ACB\u5373\u767B\u5F55OA\u7BA1\u7406\u7CFB\u7EDF");
        return ResponseEntity.ok(res);
    }

    @GetMapping("/staff/list")
    public ResponseEntity<Map<String, Object>> listStaff() {
        List<StaffAccount> list = staffAccountMapper.selectList(
                new LambdaQueryWrapper<StaffAccount>().orderByDesc(StaffAccount::getId)
        );
        list.forEach(s -> s.setPassword("******"));
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("data", list);
        return ResponseEntity.ok(res);
    }

    /** 统一账号注册（医护账号与权限管理页）：按所选角色直接建号，权限范围由 sys_role_permission 动态生效 */
    @PostMapping("/staff/register")
    public ResponseEntity<Map<String, Object>> registerStaff(@RequestBody Map<String, String> body) {
        Map<String, Object> res = new HashMap<>();
        String role = body.getOrDefault("role", "").trim().toUpperCase();
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();
        String realName = body.getOrDefault("realName", "").trim();
        String phone = body.getOrDefault("phone", "").trim();
        String department = body.getOrDefault("department", "").trim();
        String title = body.getOrDefault("title", "").trim();
        List<String> allowedRoles = List.of("DOCTOR", "NURSE", "HR", "MERCHANT", "ADMIN");
        if (!allowedRoles.contains(role)) {
            res.put("success", false);
            res.put("message", "系统角色必须是 " + String.join("/", allowedRoles) + " 之一");
            return ResponseEntity.badRequest().body(res);
        }
        if (username.isEmpty() || realName.isEmpty()) {
            res.put("success", false);
            res.put("message", "登录账号与真实姓名为必填项");
            return ResponseEntity.badRequest().body(res);
        }
        if (phone.isEmpty() || !phone.matches("1\\d{10}")) {
            res.put("success", false);
            res.put("message", "请输入 1 开头的 11 位手机号");
            return ResponseEntity.badRequest().body(res);
        }
        if (password.isEmpty()) password = "123456";
        Long dup = staffAccountMapper.selectCount(
                new LambdaQueryWrapper<StaffAccount>().eq(StaffAccount::getUsername, username));
        if (dup > 0) {
            res.put("success", false);
            res.put("message", "登录账号 [" + username + "] 已存在，请更换");
            return ResponseEntity.badRequest().body(res);
        }
        // 工号按角色前缀自动分配：DOCTOR→DOC_ / NURSE→NUR_ / HR→HR_ / MERCHANT→MERCH_ / ADMIN→ADM_
        String prefix = switch (role) {
            case "DOCTOR" -> "DOC_";
            case "NURSE" -> "NUR_";
            case "HR" -> "HR_";
            case "MERCHANT" -> "MERCH_";
            default -> "ADM_";
        };
        String staffId = prefix + (1000 + System.currentTimeMillis() % 9000);
        StaffAccount account = new StaffAccount();
        account.setStaffId(staffId);
        account.setUsername(username);
        account.setPassword(com.chunbo.medical.config.PasswordUtil.encode(password));
        account.setRealName(realName);
        account.setPhone(phone);
        account.setDepartment(department.isEmpty() ? "—" : department);
        account.setTitle(title.isEmpty() ? "—" : title);
        account.setRole(role);
        account.setStatus("ENABLE");
        try {
            staffAccountMapper.insert(account);
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "注册失败：" + e.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
        res.put("success", true);
        res.put("message", "账号 [" + username + "]（" + staffId + "，角色 " + role + "）注册成功，自动获得该角色的系统权限，重新登录即生效");
        return ResponseEntity.ok(res);
    }

    /** 修改员工角色（如把某员工改为 NURSE 护士角色），权限范围由 sys_role_permission 动态配置 */
    @PostMapping("/staff/update-role")
    public ResponseEntity<Map<String, Object>> updateStaffRole(@RequestBody Map<String, Object> body) {
        Map<String, Object> res = new HashMap<>();
        String staffId = body.getOrDefault("staffId", "").toString().trim();
        String role = body.getOrDefault("role", "").toString().trim().toUpperCase();
        Long id = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        if (role.isEmpty() || (staffId.isEmpty() && id == null)) {
            res.put("success", false);
            res.put("message", "缺少 staffId/id 或 role 参数");
            return ResponseEntity.badRequest().body(res);
        }
        StaffAccount staff = id != null ? staffAccountMapper.selectById(id)
                : staffAccountMapper.selectOne(new LambdaQueryWrapper<StaffAccount>().eq(StaffAccount::getStaffId, staffId));
        if (staff == null && !staffId.isEmpty()) {
            // 员工表中没有 → 医生账号表找，自动同步创建对应角色员工账号（如把医生改为 NURSE）
            DoctorAccount doc = doctorAccountMapper.selectOne(
                    new LambdaQueryWrapper<DoctorAccount>().eq(DoctorAccount::getDoctorId, staffId));
            if (doc == null) {
                doc = doctorAccountMapper.selectOne(
                        new LambdaQueryWrapper<DoctorAccount>().eq(DoctorAccount::getUsername, staffId));
            }
            if (doc != null) {
                StaffAccount ns = new StaffAccount();
                ns.setStaffId(doc.getDoctorId());
                ns.setUsername(doc.getUsername());
                ns.setPassword(doc.getPassword());
                ns.setRealName(doc.getDoctorName());
                ns.setDepartment(doc.getDepartment());
                ns.setTitle(doc.getTitle());
                ns.setRole(role);
                ns.setStatus("ENABLE");
                try {
                    staffAccountMapper.insert(ns);
                } catch (Exception e) {
                    res.put("success", false);
                    res.put("message", "同步创建员工角色账号失败（可能与现有账号冲突）");
                    return ResponseEntity.badRequest().body(res);
                }
                res.put("success", true);
                res.put("message", "人员 [" + doc.getDoctorName() + " (" + doc.getDoctorId() + ")] 已创建为 " + role + " 角色账号，重新登录后生效");
                return ResponseEntity.ok(res);
            }
        }
        if (staff == null) {
            res.put("success", false);
            res.put("message", "员工账号不存在");
            return ResponseEntity.badRequest().body(res);
        }
        String oldRole = staff.getRole();
        staff.setRole(role);
        staffAccountMapper.updateById(staff);
        res.put("success", true);
        res.put("message", "员工 [" + staff.getRealName() + " (" + staff.getStaffId() + ")] 角色已从 " + oldRole + " 变更为 " + role + "，重新登录后生效");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/staff/status")
    public ResponseEntity<Map<String, Object>> updateStaffStatus(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        String status = body.getOrDefault("status", "ENABLE").toString();
        StaffAccount staff = staffAccountMapper.selectById(id);
        Map<String, Object> res = new HashMap<>();
        if (staff != null) {
            staff.setStatus(status);
            staffAccountMapper.updateById(staff);
            res.put("success", true);
            res.put("message", "\u8D26\u53F7\u72B6\u6001\u66F4\u65B0\u6210\u529F");
            return ResponseEntity.ok(res);
        }
        res.put("success", false);
        res.put("message", "\u8D26\u53F7\u4E0D\u5B58\u5728");
        return ResponseEntity.badRequest().body(res);
    }

    @PostMapping("/staff/reset-password")
    public ResponseEntity<Map<String, Object>> resetStaffPassword(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        String newPassword = body.getOrDefault("newPassword", "123456").toString();
        StaffAccount staff = staffAccountMapper.selectById(id);
        Map<String, Object> res = new HashMap<>();
        if (staff != null) {
            staff.setPassword(PasswordUtil.encode(newPassword));
            staffAccountMapper.updateById(staff);
            res.put("success", true);
            res.put("message", "\u5BC6\u7801\u5DF2\u91CD\u7F6E\u4E3A: " + newPassword);
            return ResponseEntity.ok(res);
        }
        res.put("success", false);
        res.put("message", "\u8D26\u53F7\u4E0D\u5B58\u5728");
        return ResponseEntity.badRequest().body(res);
    }

    @DeleteMapping("/staff/{id}")
    public ResponseEntity<Map<String, Object>> deleteStaff(@PathVariable("id") Long id) {
        staffAccountMapper.deleteById(id);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "\u8D26\u53F7\u5DF2\u5220\u9664");
        return ResponseEntity.ok(res);
    }

    // ==========================================
    // 2. 商城商品管理与进销存 (上下架、调价、入库补货)
    // ==========================================
    @GetMapping("/mall/products")
    public ResponseEntity<Map<String, Object>> getAllProducts() {
        List<MallProduct> list = mallProductMapper.selectList(
                new LambdaQueryWrapper<MallProduct>().orderByDesc(MallProduct::getId)
        );
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("data", list);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/mall/product/status")
    public ResponseEntity<Map<String, Object>> updateProductStatus(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        String status = body.getOrDefault("status", "ON_SALE").toString();
        MallProduct product = mallProductMapper.selectById(id);
        Map<String, Object> res = new HashMap<>();
        if (product != null) {
            product.setStatus(status);
            mallProductMapper.updateById(product);
            res.put("success", true);
            res.put("message", status.equals("ON_SALE") ? "\u5546\u54C1\u5DF2\u4E0A\u67B6\u81F3\u5546\u57CE" : "\u5546\u54C1\u5DF2\u4E0B\u67B6");
            return ResponseEntity.ok(res);
        }
        res.put("success", false);
        res.put("message", "\u5546\u54C1\u4E0D\u5B58\u5728");
        return ResponseEntity.badRequest().body(res);
    }

    @PostMapping("/mall/product/update-price")
    public ResponseEntity<Map<String, Object>> updateProductPrice(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        BigDecimal retailPrice = new BigDecimal(body.get("retailGuidePrice").toString());
        BigDecimal wholesalePrice = body.containsKey("wholesalePrice") ? new BigDecimal(body.get("wholesalePrice").toString()) : null;

        MallProduct product = mallProductMapper.selectById(id);
        Map<String, Object> res = new HashMap<>();
        if (product != null) {
            product.setRetailGuidePrice(retailPrice);
            if (wholesalePrice != null) {
                product.setWholesalePrice(wholesalePrice);
            }
            mallProductMapper.updateById(product);
            res.put("success", true);
            res.put("message", "\u5546\u54C1\u4EF7\u683C\u8C03\u6574\u6210\u529F");
            return ResponseEntity.ok(res);
        }
        res.put("success", false);
        res.put("message", "\u5546\u54C1\u4E0D\u5B58\u5728");
        return ResponseEntity.badRequest().body(res);
    }

    @PostMapping("/mall/product/inbound")
    public ResponseEntity<Map<String, Object>> inboundProductStock(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        int addQty = Integer.parseInt(body.getOrDefault("quantity", "100").toString());
        String operator = body.getOrDefault("operator", "\u9662\u529E\u7BA1\u7406\u5458").toString();

        MallProduct product = mallProductMapper.selectById(id);
        Map<String, Object> res = new HashMap<>();
        if (product != null) {
            int oldStock = product.getStock() != null ? product.getStock() : (product.getStockQty() != null ? product.getStockQty() : 0);
            int newStock = oldStock + addQty;
            product.setStock(newStock);
            product.setStockQty(newStock);
            mallProductMapper.updateById(product);

            // 自动写入进销存流水台账
            try {
                InventoryRecord record = new InventoryRecord();
                record.setMedicineId(product.getId());
                record.setMedicineName(product.getProductName());
                record.setRecordType("\u5165\u5E93");
                record.setChangeQty(addQty);
                record.setAfterStock(newStock);
                record.setRefOrderNo("INB_" + System.currentTimeMillis());
                record.setOperator(operator);
                record.setRemark("\u5546\u57CE\u4E2D\u53F0\u8865\u8D27\u5165\u5E93");
                record.setCreateTime(LocalDateTime.now());
                inventoryRecordMapper.insert(record);
            } catch (Exception e) {}

            res.put("success", true);
            res.put("message", "\u5165\u5E93\u8865\u8D27\u6210\u529F\uFF0C\u5F53\u524D\u5E93\u5B58: " + newStock);
            res.put("currentStock", newStock);
            return ResponseEntity.ok(res);
        }
        res.put("success", false);
        res.put("message", "\u5546\u54C1\u4E0D\u5B58\u5728");
        return ResponseEntity.badRequest().body(res);
    }

    @PostMapping("/mall/product/upload-image")
    public ResponseEntity<Map<String, Object>> uploadProductImage(@RequestParam("file") MultipartFile file) {
        Map<String, Object> res = new HashMap<>();
        if (file == null || file.isEmpty()) {
            res.put("success", false);
            res.put("message", "请选择要上传的图片文件");
            return ResponseEntity.badRequest().body(res);
        }
        try {
            String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg";
            String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase() : ".jpg";
            if (!ext.matches("\\.(jpg|jpeg|png|gif|webp|bmp)")) {
                res.put("success", false);
                res.put("message", "仅支持 jpg/jpeg/png/gif/webp/bmp 格式图片");
                return ResponseEntity.badRequest().body(res);
            }
            if (file.getSize() > 5 * 1024 * 1024) {
                res.put("success", false);
                res.put("message", "图片大小不能超过 5MB");
                return ResponseEntity.badRequest().body(res);
            }

            Path dir = Paths.get(System.getProperty("user.dir"), "uploads", "products");
            Files.createDirectories(dir);
            String filename = "prod_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + ext;
            Path target = dir.resolve(filename);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            res.put("success", true);
            res.put("url", "/uploads/products/" + filename);
            res.put("message", "图片上传成功");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "图片上传失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }

    @PostMapping("/mall/product/image")
    public ResponseEntity<Map<String, Object>> updateProductImage(@RequestBody Map<String, Object> body) {
        Map<String, Object> res = new HashMap<>();
        try {
            Long id = Long.valueOf(body.get("id").toString());
            String url = body.getOrDefault("imageUrl", "").toString();
            MallProduct upd = new MallProduct();
            upd.setImageUrl(url);
            int rows = mallProductMapper.update(upd, new UpdateWrapper<MallProduct>().eq("id", id));
            res.put("success", rows > 0);
            res.put("message", rows > 0 ? "商品图片更新成功" : "商品不存在");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "图片更新失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }

    /**
     * 商品表格批量导入（进销存页面「表格新增商品」）
     * POST /api/admin/mall/product/batch-import  multipart: file
     * 新商品建档上架；已存在同价库存累加；价格不匹配拒绝。
     */
    @PostMapping("/mall/product/batch-import")
    public ResponseEntity<Map<String, Object>> batchImportProducts(@RequestParam("file") MultipartFile file) {
        Map<String, Object> res = new HashMap<>();
        if (file == null || file.isEmpty()) {
            res.put("success", false);
            res.put("message", "上传文件为空");
            return ResponseEntity.badRequest().body(res);
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            res.put("success", false);
            res.put("message", "请上传 Excel 商品表（xlsx / xls）");
            return ResponseEntity.badRequest().body(res);
        }
        try {
            List<Map<String, Object>> rows = fileUploadService.excelToProductRows(file);
            if (rows == null) {
                res.put("success", false);
                res.put("message", "未识别到商品表表头（需包含「商品名称」与「零售价」列）");
                return ResponseEntity.badRequest().body(res);
            }
            Map<String, Object> r = oaAssistantService.importProductsFromRows(rows);
            res.putAll(r);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "商品表解析失败：" + e.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }

    /** 商城订单批量删除 */
    @PostMapping("/mall/order/batch-delete")
    public ResponseEntity<Map<String, Object>> batchDeleteMallOrders(@RequestBody Map<String, Object> body) {
        int n = 0;
        Object raw = body.get("ids");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                try {
                    n += mallOrderMapper.deleteById(Long.valueOf(String.valueOf(o)));
                } catch (Exception ignored) {
                }
            }
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("deleted", n);
        res.put("message", "已删除 " + n + " 条商城订单");
        return ResponseEntity.ok(res);
    }

    /** 商城商品批量删除 */
    @PostMapping("/mall/product/batch-delete")
    public ResponseEntity<Map<String, Object>> batchDeleteMallProducts(@RequestBody Map<String, Object> body) {
        int n = 0;
        Object raw = body.get("ids");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                try {
                    n += mallProductMapper.deleteById(Long.valueOf(String.valueOf(o)));
                } catch (Exception ignored) {
                }
            }
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("deleted", n);
        res.put("message", "已删除 " + n + " 个商品档案");
        return ResponseEntity.ok(res);
    }

    /** 商城注册用户批量删除 */
    @PostMapping("/mall/user/batch-delete")
    public ResponseEntity<Map<String, Object>> batchDeleteMallUsers(@RequestBody Map<String, Object> body) {
        int n = 0;
        Object raw = body.get("ids");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                try {
                    n += mallUserMapper.deleteById(Long.valueOf(String.valueOf(o)));
                } catch (Exception ignored) {
                }
            }
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("deleted", n);
        res.put("message", "已删除 " + n + " 个商城注册用户");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/mall/product/save")
    public ResponseEntity<Map<String, Object>> saveProduct(@RequestBody MallProduct product) {
        Map<String, Object> res = new HashMap<>();
        try {
            // 兜底必填字段，避免表单未填写导致 insert 违反非空约束
            if (product.getGenericName() == null || product.getGenericName().trim().isEmpty()) {
                product.setGenericName(product.getProductName());
            }
            if (product.getId() != null) {
                mallProductMapper.updateById(product);
            } else {
                if (product.getStatus() == null) product.setStatus("ON_SALE");
                if (product.getStock() == null) product.setStock(500);
                if (product.getStockQty() == null) product.setStockQty(product.getStock());
                if (product.getProfitRate() == null) {
                    double ws = product.getWholesalePrice() != null ? product.getWholesalePrice().doubleValue() : 0;
                    double rt = product.getRetailGuidePrice() != null ? product.getRetailGuidePrice().doubleValue() : 0;
                    product.setProfitRate(ws > 0
                            ? BigDecimal.valueOf((rt - ws) / ws * 100).setScale(2, java.math.RoundingMode.HALF_UP)
                            : BigDecimal.ZERO);
                }
                product.setCreateTime(LocalDateTime.now());
                mallProductMapper.insert(product);
            }
            res.put("success", true);
            res.put("message", "商品档案保存成功");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "保存失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }

    // ==========================================
    // 3. 商城注册用户管理与订单追溯
    // ==========================================
    @GetMapping("/mall/users")
    public ResponseEntity<Map<String, Object>> listMallUsers() {
        List<MallUser> list = mallUserMapper.selectList(
                new LambdaQueryWrapper<MallUser>().orderByDesc(MallUser::getId)
        );
        list.forEach(u -> u.setPassword("******"));
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("data", list);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/mall/user/status")
    public ResponseEntity<Map<String, Object>> updateMallUserStatus(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        String status = body.getOrDefault("status", "ENABLE").toString();
        MallUser user = mallUserMapper.selectById(id);
        Map<String, Object> res = new HashMap<>();
        if (user != null) {
            user.setStatus(status);
            mallUserMapper.updateById(user);
            res.put("success", true);
            res.put("message", status.equals("ENABLE") ? "\u7528\u6237\u5DF2\u6062\u590D\u6B63\u5E38" : "\u7528\u6237\u8D26\u53F7\u5DF2\u51BB\u7ED3");
            return ResponseEntity.ok(res);
        }
        res.put("success", false);
        res.put("message", "\u7528\u6237\u4E0D\u5B58\u5728");
        return ResponseEntity.badRequest().body(res);
    }

    @GetMapping("/mall/user/orders")
    public ResponseEntity<Map<String, Object>> listUserOrders(@RequestParam(value = "buyerName", required = false) String buyerName) {
        // 严格限定只查询春播商城 C端居民购药订单 (B2C开头)，不混杂医生门诊/院内批发采购单
        LambdaQueryWrapper<MallOrder> wrapper = new LambdaQueryWrapper<MallOrder>()
                .likeRight(MallOrder::getOrderNo, "B2C")
                .orderByDesc(MallOrder::getId);
        if (buyerName != null && !buyerName.isEmpty()) {
            // buyer_name 落库格式为「昵称 (手机号)」，eq 精确匹配必然查空。
            // 先按 账号/手机号/昵称 定位商城用户，再用其昵称+手机号做 like 双向匹配
            String kw = buyerName.trim();
            MallUser user = mallUserMapper.selectOne(new LambdaQueryWrapper<MallUser>()
                    .eq(MallUser::getUsername, kw).or().eq(MallUser::getPhone, kw));
            if (user == null) {
                List<MallUser> byNick = mallUserMapper.selectList(
                        new LambdaQueryWrapper<MallUser>().like(MallUser::getNickname, kw));
                if (byNick.size() == 1) user = byNick.get(0);
            }
            String kwNick = user != null && user.getNickname() != null && !user.getNickname().isEmpty()
                    ? user.getNickname() : kw;
            String kwPhone = user != null && user.getPhone() != null && !user.getPhone().isEmpty()
                    ? user.getPhone() : kw;
            // 嵌套 and 包装，避免 or 破坏前面的 B2C 前缀条件
            wrapper.and(w -> w.like(MallOrder::getBuyerName, kwNick)
                    .or().like(MallOrder::getBuyerName, kwPhone));
        }
        List<MallOrder> orders = mallOrderMapper.selectList(wrapper);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("data", orders);
        return ResponseEntity.ok(res);
    }

    /**
     * 商城订单发货出库：真实扣减商品库存 + 写入进销存流水 + 更新顺丰运单状态
     * （逻辑已下沉到 OaAssistantService.shipOrder，与 AI 工具共用同一份确定性实现）
     */
    @PostMapping("/mall/order/ship")
    public ResponseEntity<Map<String, Object>> shipMallOrder(@RequestBody Map<String, Object> body) {
        String orderNo = body.getOrDefault("orderNo", "").toString().trim();
        String trackingNo = body.getOrDefault("trackingNo", "CB" + System.currentTimeMillis()).toString().trim();
        String operator = body.getOrDefault("operator", "王商户 (供应链主管)").toString().trim();

        Map<String, Object> res = new HashMap<>();
        if (orderNo.isEmpty()) {
            res.put("success", false);
            res.put("message", "订单号不能为空");
            return ResponseEntity.badRequest().body(res);
        }
        Map<String, Object> r = oaAssistantService.shipOrder(orderNo, trackingNo, operator);
        if (Boolean.FALSE.equals(r.get("success"))) {
            res.putAll(r);
            return ResponseEntity.badRequest().body(res);
        }
        res.putAll(r);
        return ResponseEntity.ok(res);
    }

    /**
     * 确认商城订单送达 (居民已签收妥投)
     * （逻辑已下沉到 OaAssistantService.confirmOrderDelivered，与 AI 工具共用同一份确定性实现）
     */
    @PostMapping("/mall/order/deliver")
    public ResponseEntity<Map<String, Object>> deliverMallOrder(@RequestBody Map<String, Object> body) {
        String orderNo = body.getOrDefault("orderNo", "").toString().trim();
        Map<String, Object> res = new HashMap<>();
        if (orderNo.isEmpty()) {
            res.put("success", false);
            res.put("message", "订单号不能为空");
            return ResponseEntity.badRequest().body(res);
        }
        Map<String, Object> r = oaAssistantService.confirmOrderDelivered(orderNo);
        if (Boolean.FALSE.equals(r.get("success"))) {
            res.putAll(r);
            return ResponseEntity.badRequest().body(res);
        }
        res.putAll(r);
        return ResponseEntity.ok(res);
    }

    /**
     * 注册商户账号 (ADMIN 或 HR 可操作)
     */
    @PostMapping("/merchant/register")
    public ResponseEntity<Map<String, Object>> registerMerchant(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "123456").trim();
        String realName = body.getOrDefault("realName", "").trim();
        String phone = body.getOrDefault("phone", "").trim();

        Map<String, Object> res = new HashMap<>();
        if (username.isEmpty() || realName.isEmpty()) {
            res.put("success", false);
            res.put("message", "请填写商户登录账号与真实姓名");
            return ResponseEntity.badRequest().body(res);
        }

        Long count = staffAccountMapper.selectCount(
                new LambdaQueryWrapper<StaffAccount>().eq(StaffAccount::getUsername, username)
        );
        if (count > 0) {
            res.put("success", false);
            res.put("message", "账号 [" + username + "] 已存在");
            return ResponseEntity.badRequest().body(res);
        }

        StaffAccount staff = new StaffAccount();
        staff.setUsername(username);
        staff.setPassword(PasswordUtil.encode(password));
        staff.setRealName(realName);
        staff.setStaffId("MERCH_" + (System.currentTimeMillis() % 10000));
        staff.setRole("MERCHANT");
        staff.setDepartment("春播商城运营中心 / 特约药企直供部");
        staff.setTitle("商户店长 / 供应链主管");
        staff.setPhone(phone.isEmpty() ? "13800000000" : phone);
        staff.setStatus("ENABLE");
        staff.setCreateTime(LocalDateTime.now());
        staffAccountMapper.insert(staff);

        res.put("success", true);
        res.put("message", "商户账号注册成功！用户名: " + username + "，姓名: " + realName);
        return ResponseEntity.ok(res);
    }
}
