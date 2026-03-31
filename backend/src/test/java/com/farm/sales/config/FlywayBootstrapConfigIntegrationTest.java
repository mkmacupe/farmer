package com.farm.sales.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

class FlywayBootstrapConfigIntegrationTest {

  @Test
  void repairBeforeMigrateRecoversFromLegacyChecksumDrift() throws Exception {
    String dbName = "farm_sales_repair_" + System.nanoTime();
    String url = "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    Flyway initialFlyway = Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .load();
    initialFlyway.migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "");
         PreparedStatement statement = connection.prepareStatement(
             "UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = ?")) {
      for (String version : List.of("1", "3", "4", "5", "6", "13", "19")) {
        statement.setString(1, version);
        statement.addBatch();
      }
      statement.executeBatch();
    }

    Flyway repairedFlyway = Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .load();
    FlywayMigrationStrategy strategy = new FlywayBootstrapConfig()
        .flywayMigrationStrategy(false, "srv-render");

    assertThatCode(() -> strategy.migrate(repairedFlyway))
        .doesNotThrowAnyException();
  }
}
