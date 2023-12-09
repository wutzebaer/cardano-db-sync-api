package de.peterspace.cardanodbsyncapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@ServletComponentScan
@EnableScheduling
public class CardanoDbSyncApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CardanoDbSyncApiApplication.class, args);
	}

}
