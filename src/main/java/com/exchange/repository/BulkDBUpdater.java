package com.exchange.repository;

import com.exchange.repository.entities.Currency;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Component
public class BulkDBUpdater {
    private final JdbcTemplate jdbcTemplate;

    @Value("${defaultSchema}")
    private String defaultSchema;

    public BulkDBUpdater(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void insertOrUpdateBulkData(List<Currency> dataList) {
        String sql =
                "INSERT INTO " + defaultSchema + ".currencies (currency_name, currency_full_name, rates, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?) " +
                        "ON CONFLICT (currency_name) DO UPDATE SET " +
                        "updated_at = EXCLUDED.updated_at";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Currency data = dataList.get(i);
                ps.setString(1, data.getCurrencyName());
                ps.setString(2, data.getCurrencyFullName());
                ps.setString(3, data.getRates());
                ps.setLong(4, data.getCreatedAt());
                ps.setLong(5, data.getUpdatedAt());
            }

            @Override
            public int getBatchSize() {
                return dataList.size();
            }
        });
    }
}