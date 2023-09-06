package de.peterspace.cardanodbsyncapi.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Cardano DB-Sync API", version = "1.0"))
public class OpenAPIConfig {

}
