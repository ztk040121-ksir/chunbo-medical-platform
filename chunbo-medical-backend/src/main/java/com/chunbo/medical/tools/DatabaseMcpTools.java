package com.chunbo.medical.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MySQL 数据操作 MCP 工具：让大模型通过 MCP 协议安全地查询 / 修改业务数据库。
 * 安全策略（三重防线）：
 * 1) 只允许 SELECT / INSERT / UPDATE / DELETE 四种语句；
 * 2) 目标表必须在业务表白名单内；
 * 3) 拦截 DROP / TRUNCATE / ALTER 等危险 DDL 操作。
 */
@Component
public class DatabaseMcpTools {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper om = new ObjectMapper();

    /** 业务表白名单（与实体一一对应，杜绝任意表访问） */
    private static final Set<String> ALLOW_TABLES = Set.of(
            "medicine", "prescription", "prescription_item", "clinic_registration", "patient",
            "clinic_treatment_record", "clinic_inbound_order", "clinic_inbound_item",
            "clinic_stocktake", "clinic_stocktake_item", "clinic_supplier", "clinic_schedule",
            "clinic_patient_followup", "inventory_record", "mall_product", "mall_order", "mall_user",
            "oa_salary_slip", "oa_approval", "oa_plaster_record",
            "pay_account", "pay_transaction", "pay_audit_record",
            "staff_account", "doctor_account", "sys_token_log", "clinical_case", "clinic_emr_record"
    );

    /** 危险操作黑名单 */
    private static final Set<String> FORBIDDEN = Set.of(
            "DROP", "TRUNCATE", "ALTER", "CREATE", "RENAME", "GRANT", "REVOKE",
            "EXEC", "EXECUTE", "CALL", "INTO OUTFILE", "INTO DUMPFILE", "LOAD_FILE", "UNION"
    );

    @Tool(description = "执行只读 SQL 查询（仅允许 SELECT，最多返回 50 行），用于查询药品、处方、患者、订单等业务数据")
    public String queryDatabase(@ToolParam(description = "只读 SELECT SQL 语句") String sql) {
        try {
            String s = sql == null ? "" : sql.trim();
            if (s.isEmpty()) return json("error", "SQL 不能为空");
            String upper = s.toUpperCase();
            if (!upper.startsWith("SELECT")) return json("error", "仅允许 SELECT 查询");
            checkSafety(s);
            if (!upper.contains("LIMIT")) s = s + " LIMIT 50";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(s);
            Map<String, Object> res = new java.util.LinkedHashMap<>();
            res.put("success", true);
            res.put("count", rows.size());
            res.put("rows", rows);
            return om.writeValueAsString(res);
        } catch (Exception e) {
            return json("error", e.getMessage());
        }
    }

    @Tool(description = "执行数据修改 SQL（仅允许 INSERT/UPDATE/DELETE，目标表必须在白名单内），返回影响行数")
    public String updateDatabase(@ToolParam(description = "INSERT/UPDATE/DELETE SQL 语句") String sql) {
        try {
            String s = sql == null ? "" : sql.trim();
            if (s.isEmpty()) return json("error", "SQL 不能为空");
            String upper = s.toUpperCase();
            boolean isInsert = upper.startsWith("INSERT");
            boolean isUpdate = upper.startsWith("UPDATE");
            boolean isDelete = upper.startsWith("DELETE");
            if (!(isInsert || isUpdate || isDelete)) return json("error", "仅允许 INSERT/UPDATE/DELETE");
            checkSafety(s);
            String table = extractTable(upper);
            if (table == null || !ALLOW_TABLES.contains(table)) {
                return json("error", "表 [" + table + "] 不在允许操作清单内");
            }
            int affected = jdbcTemplate.update(s);
            Map<String, Object> res = new java.util.LinkedHashMap<>();
            res.put("success", true);
            res.put("affectedRows", affected);
            res.put("table", table);
            return om.writeValueAsString(res);
        } catch (Exception e) {
            return json("error", e.getMessage());
        }
    }

    private void checkSafety(String sql) {
        String upper = sql.toUpperCase();
        for (String bad : FORBIDDEN) {
            if (upper.contains(bad)) throw new IllegalArgumentException("禁止执行危险操作: " + bad);
        }
    }

    private String extractTable(String upperSql) {
        String s = upperSql.trim();
        String after;
        if (s.startsWith("INSERT")) after = s.substring("INSERT INTO".length());
        else if (s.startsWith("UPDATE")) after = s.substring("UPDATE".length());
        else if (s.startsWith("DELETE")) after = s.substring("DELETE FROM".length());
        else return null;
        String token = after.trim();
        int i = 0;
        while (i < token.length() && (Character.isLetterOrDigit(token.charAt(i)) || token.charAt(i) == '_')) i++;
        return i == 0 ? null : token.substring(0, i).toLowerCase();
    }

    private String json(String k, String v) {
        return "{\"" + k + "\":\"" + (v == null ? "" : v.replace("\"", "'").replace("\n", " ")) + "\"}";
    }
}
