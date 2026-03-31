package com.farm.sales.config;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

class FlywayBootstrapConfigTest {

  @Test
  void repairRunsBeforeMigrateWhenFlagEnabled() {
    Flyway flyway = Mockito.mock(Flyway.class);
    FlywayMigrationStrategy strategy = new FlywayBootstrapConfig()
        .flywayMigrationStrategy(true);

    strategy.migrate(flyway);

    InOrder inOrder = inOrder(flyway);
    inOrder.verify(flyway).repair();
    inOrder.verify(flyway).migrate();
  }

  @Test
  void migrateRunsWithoutRepairWhenFlagDisabled() {
    Flyway flyway = Mockito.mock(Flyway.class);
    FlywayMigrationStrategy strategy = new FlywayBootstrapConfig()
        .flywayMigrationStrategy(false);

    strategy.migrate(flyway);

    verify(flyway, never()).repair();
    verify(flyway).migrate();
  }
}
