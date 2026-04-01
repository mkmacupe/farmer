package com.farm.sales.config;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.ErrorDetails;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

  @Test
  void validationFailureDoesNotRepairImplicitly() {
    Flyway flyway = Mockito.mock(Flyway.class);
    FlywayMigrationStrategy strategy = new FlywayBootstrapConfig()
        .flywayMigrationStrategy(false);

    FlywayValidateException exception =
        new FlywayValidateException(new ErrorDetails(null, "boom"), "boom");
    doThrow(exception).when(flyway).migrate();

    assertThatThrownBy(() -> strategy.migrate(flyway))
        .isSameAs(exception);

    verify(flyway, never()).repair();
    verify(flyway).migrate();
  }
}
