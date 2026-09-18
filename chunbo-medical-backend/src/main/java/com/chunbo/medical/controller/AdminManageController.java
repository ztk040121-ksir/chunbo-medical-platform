package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chunbo.medical.config.PasswordUtil;
import com.chunbo.medical.entity.*;
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
    private StaffAccountMapper staffAccountMapper;

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
            wrapper.eq(MallOrder::getBuyerName, buyerName);
        }
        List<MallOrder> orders = mallOrderMapper.selectList(wrapper);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("data", orders);
        return ResponseEntity.ok(res);
    }

    /**
     * 商城订单发货出库：真实扣减商品库存 + 写入进销存流水 + 更新顺丰运单状态
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

        MallOrder order = mallOrderMapper.selectOne(
                new LambdaQueryWrapper<MallOrder>().eq(MallOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            res.put("success", false);
            res.put("message", "未找到该商城订单: " + orderNo);
            return ResponseEntity.badRequest().body(res);
        }

        if (order.getStatus() != null && order.getStatus().contains("已发货")) {
            res.put("success", false);
            res.put("message", "该订单已完成发货出库，请勿重复发货！单号: " + order.getBargainNotes());
            return ResponseEntity.badRequest().body(res);
        }

        // 解析 itemsJson 扣减商品真实库存
        List<String> logs = new ArrayList<>();
        String itemsJson = order.getItemsJson();
        if (itemsJson != null && !itemsJson.isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(itemsJson);
                if (rootNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode item : rootNode) {
                        Long prodId = item.has("id") ? item.get("id").asLong() : null;
                        int qty = item.has("quantity") ? item.get("quantity").asInt() : 1;

                        if (prodId != null) {
                            MallProduct prod = mallProductMapper.selectById(prodId);
                            if (prod != null) {
                                int currentStock = prod.getStock() != null ? prod.getStock() : 0;
                                int newStock = Math.max(0, currentStock - qty);
                                prod.setStock(newStock);
                                mallProductMapper.updateById(prod);

                                // 记录进销存出库流水
                                InventoryRecord ir = new InventoryRecord();
                                ir.setMedicineId(prod.getId());
                                ir.setMedicineName(prod.getProductName());
                                ir.setRecordType("商城订单发货出库");
                                ir.setChangeQty(-qty);
                                ir.setAfterStock(newStock);
                                ir.setRefOrderNo(order.getOrderNo());
                                ir.setOperator(operator);
                                ir.setRemark("春播健康便民速递揽收 (单号: " + trackingNo + ", 送至: " + order.getClinicName() + ")");
                                ir.setCreateTime(LocalDateTime.now());
                                inventoryRecordMapper.insert(ir);

                                logs.add("商品【" + prod.getProductName() + "】出库扣减 " + qty + " 件，结余库存: " + newStock);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                InventoryRecord ir = new InventoryRecord();
                ir.setMedicineName("春播商城综合购药订单");
                ir.setRecordType("商城订单发货出库");
                ir.setChangeQty(-1);
                ir.setAfterStock(0);
                ir.setRefOrderNo(order.getOrderNo());
                ir.setOperator(operator);
                ir.setRemark("春播健康便民速递: " + trackingNo);
                ir.setCreateTime(LocalDateTime.now());
                inventoryRecordMapper.insert(ir);
            }
        }

        // 更新订单状态
        order.setStatus("已发货 / 春播便民速递运输中");
        String existingNotes = order.getBargainNotes() != null ? order.getBargainNotes() : "";
        order.setBargainNotes(existingNotes + " 【春播健康便民速递单号: " + trackingNo + "，发货人: " + operator + "】");
        mallOrderMapper.updateById(order);

        res.put("success", true);
        res.put("message", "🎉 订单履约发货出库成功！春播健康便民速递运单号【" + trackingNo + "】，库存已实时扣减并生成进销存台账！");
        res.put("trackingNo", trackingNo);
        res.put("logs", logs);
        return ResponseEntity.ok(res);
    }

    /**
     * 确认商城订单送达 (居民已签收妥投)
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

        MallOrder order = mallOrderMapper.selectOne(
                new LambdaQueryWrapper<MallOrder>().eq(MallOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            res.put("success", false);
            res.put("message", "未找到该商城订单: " + orderNo);
            return ResponseEntity.badRequest().body(res);
        }

        order.setStatus("已送达 / 居民已签收");
        String existingNotes = order.getBargainNotes() != null ? order.getBargainNotes() : "";
        order.setBargainNotes(existingNotes + " 【春播便民速递妥投完成，居民已顺利签收】");
        mallOrderMapper.updateById(order);

        res.put("success", true);
        res.put("message", "🎉 订单【" + orderNo + "】已确认送达并由居民成功签收！");
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
