package de.peterspace.cardanodbsyncapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@ServletComponentScan
public class CardanoDbSyncApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CardanoDbSyncApiApplication.class, args);
	}

}
