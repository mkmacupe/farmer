package com.farm.sales.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
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
      @Value("${app.flyway.repair-before-migrate:false}") boolean repairBeforeMigrate,
      @Value("${RENDER_SERVICE_ID:}") String renderServiceId
  ) {
    return flyway -> migrateWithOptionalRepair(flyway, repairBeforeMigrate, renderServiceId);
  }

  private void migrateWithOptionalRepair(
      Flyway flyway,
      boolean repairBeforeMigrate,
      String renderServiceId
  ) {
    if (repairBeforeMigrate) {
      log.warn("Flyway repair-before-migrate is enabled. Repairing schema history before migrate.");
      flyway.repair();
      flyway.migrate();
      return;
    }

    try {
      flyway.migrate();
    } catch (FlywayValidateException ex) {
      if (!renderServiceId.isBlank()) {
        log.warn(
            "Flyway validation failed on Render service {}. Attempting schema history repair and retry.",
            renderServiceId
        );
        flyway.repair();
        flyway.migrate();
        return;
      }
      throw ex;
    }
  }
}
