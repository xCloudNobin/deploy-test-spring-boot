package dev.xcloud.taskboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

@Component
public class SchemaInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final DataSource dataSource;

    public SchemaInitializer(DataSource dataSource,
                             @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.dataSource = dataSource;
        ensureDataParent(datasourceUrl);
    }

    @Override
    public void afterPropertiesSet() {
        try {
            runSchemaAndSeed();
            log.info("database schema and seed data initialized");
        } catch (Exception e) {
            log.warn("database unavailable at startup (schema/seed skipped; readiness will report DOWN): {}",
                    e.getMessage());
        }
    }

    private void ensureDataParent(String url) {
        if (url == null || url.isBlank() || url.startsWith("jdbc:sqlite::memory:")) {
            return;
        }
        if (url.startsWith("jdbc:sqlite:")) {
            String path = url.substring("jdbc:sqlite:".length());
            try {
                Path parent = Path.of(path).toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
            } catch (Exception e) {
                log.warn("cannot create data directory for database '{}': {}", path, e.getMessage());
            }
        }
    }

    private void runSchemaAndSeed() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            for (String ddl : Schema.DDL_STATEMENTS) {
                st.execute(ddl);
            }
            seedIfNeeded(st);
            c.commit();
        }
    }

    public void resetAndSeed() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            st.execute("DELETE FROM task");
            st.execute("DELETE FROM project");
            st.execute("DELETE FROM seed_flag");
            st.execute("DELETE FROM heartbeat");
            seedIfNeeded(st);
            c.commit();
        }
    }

    private void seedIfNeeded(Statement st) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM seed_flag WHERE id = 1")) {
            rs.next();
            if (rs.getInt(1) > 0) {
                return;
            }
        }
        String now = LocalDateTime.now().toString();
        long design = insertProject(st, "Website Redesign", "Refactor the marketing site to the new brand system.", "active", now);
        long ops = insertProject(st, "Operations Backlog", "Cross-team operational work for the current quarter.", "active", now);
        insertTask(st, design, "Set up design tokens", "Collect colors, type scale and spacing into tokens.", "in_progress", "high", now);
        insertTask(st, design, "Homepage template", "Build the reusable homepage layout.", "todo", "medium", now);
        insertTask(st, ops, "Restock office supplies", "Order supplies before end of month.", "done", "low", now);
        insertTask(st, ops, "Review vendor contracts", "Renewal review for the three main vendors.", "todo", "high", now);
        st.executeUpdate("INSERT INTO seed_flag (id, applied_at) VALUES (1, '" + now + "')");
        log.info("seeded demo projects and tasks");
    }

    private long insertProject(Statement st, String name, String description, String status, String now)
            throws SQLException {
        return insertRow(st, "project", name, description, status, now);
    }

    private long insertTask(Statement st, long projectId, String title, String description,
                            String status, String priority, String now) throws SQLException {
        st.executeUpdate("INSERT INTO task"
                        + " (project_id, title, description, status, priority, created_at, updated_at)"
                        + " VALUES (" + projectId + ", '" + esc(title) + "', '" + esc(description)
                        + "', '" + status + "', '" + priority + "', '" + now + "', '" + now + "')",
                Statement.RETURN_GENERATED_KEYS);
        return lastId(st);
    }

    private long insertRow(Statement st, String table, String name, String description,
                           String status, String now) throws SQLException {
        st.executeUpdate("INSERT INTO " + table
                        + " (name, description, status, created_at, updated_at)"
                        + " VALUES ('" + esc(name) + "', '" + esc(description) + "', '" + status
                        + "', '" + now + "', '" + now + "')",
                Statement.RETURN_GENERATED_KEYS);
        return lastId(st);
    }

    private long lastId(Statement st) throws SQLException {
        try (ResultSet keys = st.getGeneratedKeys()) {
            keys.next();
            return keys.getLong(1);
        }
    }

    private static String esc(String value) {
        return value.replace("'", "''");
    }

    private static final class Schema {
        private static final String[] DDL_STATEMENTS = {
                "CREATE TABLE IF NOT EXISTS project ("
                        + "  id          INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "  name        TEXT    NOT NULL,"
                        + "  description TEXT    NOT NULL DEFAULT '',"
                        + "  status      TEXT    NOT NULL,"
                        + "  created_at  TEXT    NOT NULL,"
                        + "  updated_at  TEXT    NOT NULL"
                        + ")",
                "CREATE TABLE IF NOT EXISTS task ("
                        + "  id          INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "  project_id  INTEGER NOT NULL REFERENCES project(id) ON DELETE CASCADE,"
                        + "  title       TEXT    NOT NULL,"
                        + "  description TEXT    NOT NULL DEFAULT '',"
                        + "  status      TEXT    NOT NULL,"
                        + "  priority    TEXT    NOT NULL,"
                        + "  created_at  TEXT    NOT NULL,"
                        + "  updated_at  TEXT    NOT NULL"
                        + ")",
                "CREATE INDEX IF NOT EXISTS idx_task_project_id ON task(project_id)",
                "CREATE TABLE IF NOT EXISTS seed_flag ("
                        + "  id         INTEGER PRIMARY KEY CHECK (id = 1),"
                        + "  applied_at TEXT NOT NULL"
                        + ")",
                "CREATE TABLE IF NOT EXISTS heartbeat ("
                        + "  id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "  ts TEXT NOT NULL"
                        + ")"
        };
    }
}