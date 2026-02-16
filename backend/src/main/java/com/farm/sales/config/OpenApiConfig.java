package com.farm.sales.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
  @Bean
  public OpenAPI farmSalesOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("API системы сбыта фермерского хозяйства")
            .version("v1")
            .description("API автоматизации отдела сбыта с JWT-авторизацией и ролевым доступом"))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }
}
