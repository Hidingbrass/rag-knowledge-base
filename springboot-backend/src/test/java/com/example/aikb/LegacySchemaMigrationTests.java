package com.example.aikb;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

class LegacySchemaMigrationTests {

    @Test
    void migrationsShouldUpgradeLegacyBaselineWithoutJobTables() throws Exception {
        String url = "jdbc:h2:mem:legacy_migration_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("create table legacy_existing_table (id integer primary key)");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(tableExists(connection, "job_generated_task")).isTrue();
            assertThat(tableExists(connection, "job_favorite")).isTrue();
            assertThat(tableExists(connection, "job_resume_version")).isTrue();
            assertThat(columnExists(connection, "job_generated_task", "status")).isTrue();
            assertThat(columnExists(connection, "job_generated_task", "error_message")).isTrue();
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws Exception {
        try (ResultSet resultSet = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            return resultSet.next();
        }
    }
}
