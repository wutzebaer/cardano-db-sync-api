package de.peterspace.cardanodbsyncapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@ServletComponentScan
@EnableScheduling
@OpenAPIDefinition(servers = {@Server(url = "${server.servlet.context-path}")})
public class CardanoDbSyncApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(CardanoDbSyncApiApplication.class, args);
  }
}
