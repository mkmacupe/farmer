package com.farm.sales.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

class FlywayMigrationCompatibilityTest {

  @Test
  void migrationsApplyOnH2InPostgreSqlMode() {
    String dbName = "farm_sales_migration_" + System.nanoTime();
    String url = "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    Flyway flyway = Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .load();

    MigrateResult result = flyway.migrate();

    assertTrue(result.success, "Flyway migration run must complete successfully");
    assertTrue(result.getFailedMigrations().isEmpty(), "No migration should fail on dev H2 profile");
    assertTrue(result.migrationsExecuted >= 10, "All SQL migrations must be executable on dev H2 profile");
  }
}
