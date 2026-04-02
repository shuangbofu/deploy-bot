package top.fusb.deploybot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动后对历史 H2 表结构做兜底修正，避免 Hibernate 对旧列约束迁移不彻底。
 */
@Component
public class SchemaMigrationBootstrap {
    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaMigrationBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        List<String> statements = List.of(
                "ALTER TABLE IF EXISTS deployments ALTER COLUMN status VARCHAR(255)",
                "ALTER TABLE IF EXISTS deployments ALTER COLUMN pipeline_id BIGINT NULL",
                "ALTER TABLE IF EXISTS deployments ADD COLUMN IF NOT EXISTS execution_snapshot_json CLOB",
                "ALTER TABLE IF EXISTS deployments ADD COLUMN IF NOT EXISTS stopped_by VARCHAR(1000)",
                "ALTER TABLE IF EXISTS pipelines ADD COLUMN IF NOT EXISTS application_name VARCHAR(255)",
                "ALTER TABLE IF EXISTS pipelines ADD COLUMN IF NOT EXISTS spring_profile VARCHAR(255)",
                "ALTER TABLE IF EXISTS pipelines ADD COLUMN IF NOT EXISTS runtime_config_yaml CLOB",
                "ALTER TABLE IF EXISTS pipelines ADD COLUMN IF NOT EXISTS notification_bindings_json CLOB",
                "ALTER TABLE IF EXISTS pipelines ADD COLUMN IF NOT EXISTS maven_settings_id BIGINT",
                "ALTER TABLE IF EXISTS maven_settings ADD COLUMN IF NOT EXISTS runtime_environment_id BIGINT",
                "ALTER TABLE IF EXISTS maven_settings ADD COLUMN IF NOT EXISTS is_default BOOLEAN DEFAULT FALSE",
                "ALTER TABLE IF EXISTS users ADD COLUMN IF NOT EXISTS avatar VARCHAR(500)"
        );
        statements.forEach(this::executeSilently);
    }

    private void executeSilently(String sql) {
        try {
            jdbcTemplate.execute(sql);
            log.info("Schema migration applied: {}", sql);
        } catch (Exception ex) {
            log.warn("Schema migration skipped or failed: {} -> {}", sql, ex.getMessage());
        }
    }
}
