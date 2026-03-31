package com.farm.sales.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayBootstrapConfig {

  private static final Logger log = LoggerFactory.getLogger(FlywayBootstrapConfig.class);

  @Bean
  FlywayMigrationStrategy flywayMigrationStrategy(
      @Value("${app.flyway.repair-before-migrate:false}") boolean repairBeforeMigrate
  ) {
    return flyway -> migrateWithOptionalRepair(flyway, repairBeforeMigrate);
  }

  private void migrateWithOptionalRepair(Flyway flyway, boolean repairBeforeMigrate) {
    if (repairBeforeMigrate) {
      log.warn("Flyway repair-before-migrate is enabled. Repairing schema history before migrate.");
      flyway.repair();
    }

    flyway.migrate();
  }
}
