package com.chunbo.medical.controller;

import com.chunbo.medical.service.ExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 数据导出中心：工资 / 进销存 / 订单统一导出 Excel。
 * 前端导出中心页按需调用对应接口下载。
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    @Autowired
    private ExportService exportService;

    @GetMapping("/salary")
    public void exportSalary(HttpServletResponse response) throws Exception {
        exportService.exportSalary(response);
    }

    @GetMapping("/inventory")
    public void exportInventory(HttpServletResponse response) throws Exception {
        exportService.exportInventory(response);
    }

    @GetMapping("/orders")
    public void exportOrders(HttpServletResponse response) throws Exception {
        exportService.exportOrders(response);
    }

    @GetMapping("/prescriptions")
    public void exportPrescriptions(HttpServletResponse response) throws Exception {
        exportService.exportPrescriptions(response);
    }
}
