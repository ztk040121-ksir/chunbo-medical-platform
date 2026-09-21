package com.chunbo.medical.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 经典经方库接口（数据源：tcm_formula 表，替代前端写死的经方模板）
 */
@RestController
@RequestMapping("/api/tcm-formulas")
public class TcmFormulaController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper om = new ObjectMapper();

    @GetMapping
    public List<Map<String, Object>> listFormulas() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, name, description, herbs_json FROM tcm_formula ORDER BY id");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> f = new HashMap<>();
            f.put("id", row.get("id"));
            f.put("name", row.get("name"));
            f.put("desc", row.get("description"));
            String herbsJson = row.get("herbs_json") != null ? row.get("herbs_json").toString() : "[]";
            try {
                f.put("herbs", om.readValue(herbsJson, List.class));
            } catch (Exception e) {
                f.put("herbs", List.of());
            }
            result.add(f);
        }
        return result;
    }
}
