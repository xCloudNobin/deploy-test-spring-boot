package dev.xcloud.taskboard.service;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;

@Component("dbProbe")
public class DatabaseHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbc;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public Health health() {
        try {
            String ts = LocalDateTime.now().toString();
            jdbc.update("INSERT INTO heartbeat (ts) VALUES (?)", ts);
            Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM heartbeat", Integer.class);
            return Health.up()
                    .withDetail("database", "sqlite")
                    .withDetail("writeProbe", "ok")
                    .withDetail("heartbeatRows", rows)
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}