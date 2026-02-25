package com.farm.sales;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class SalesApplicationTest {
  @Test
  void mainDelegatesToSpringApplicationRun() {
    try (MockedStatic<SpringApplication> mocked = mockStatic(SpringApplication.class)) {
      SalesApplication.main(new String[] {"--spring.profiles.active=test"});
      mocked.verify(() -> SpringApplication.run(eq(SalesApplication.class), any(String[].class)));
    }
  }
}

