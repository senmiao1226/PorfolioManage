package com.training.portfolio.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fix: MySQL `holdings.asset_type` enum may be created without `fund`,
 * causing insert/update failures when frontend sends `assetType = fund`.
 *
 * <p>We do a lightweight runtime schema check and extend the enum if needed.
 * This is safe for dev; in prod prefer Flyway/Liquibase migrations.</p>
 */
@Component
@RequiredArgsConstructor
public class HoldingAssetTypeEnumFixer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensureFundEnumValue() {
        try {
            String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            if (schema == null || schema.isBlank()) return;

            // JPA entity uses `holdings`, but some projects use `holding`.
            List<String> tablesToCheck = List.of("holdings", "holding");
            for (String table : tablesToCheck) {
                String columnType = getAssetTypeColumnType(schema, table);
                if (columnType == null) continue;

                String normalized = columnType.toLowerCase();
                if (!normalized.contains("enum(")) {
                    continue;
                }

                if (normalized.contains("'fund'") || normalized.contains("fund,")) {
                    return; // already has fund
                }

                // Add fund while keeping the known existing values.
                // If your DB had different values, use a proper migration instead.
                String alterSql = ""
                        + "ALTER TABLE " + table + " "
                        + "MODIFY asset_type ENUM('stock','bond','cash','fund') NOT NULL";

                jdbcTemplate.execute(alterSql);
                return;
            }
        } catch (Exception ignored) {
            // Best-effort: don't prevent the whole app from starting.
        }
    }

    private String getAssetTypeColumnType(String schema, String table) {
        String sql = ""
                + "SELECT COLUMN_TYPE "
                + "FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = ? "
                + "AND TABLE_NAME = ? "
                + "AND COLUMN_NAME = 'asset_type'";
        List<String> rows = jdbcTemplate.query(sql, new Object[]{schema, table}, (rs, rowNum) -> rs.getString("COLUMN_TYPE"));
        return rows.isEmpty() ? null : rows.get(0);
    }
}

