package de.peterspace.cardanodbsyncapi.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Cardano DB-Sync API", version = "1.0"), servers = { @Server(url = "/", description = "Default Server URL") })
public class OpenAPIConfig {

}
