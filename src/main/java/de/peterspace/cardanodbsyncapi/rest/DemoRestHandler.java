package de.peterspace.cardanodbsyncapi.rest;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

//@RestController
@RequiredArgsConstructor
@RequestMapping("/cardanoDbSyncApi/demo")
@Slf4j
public class DemoRestHandler {

	@GetMapping(value = "/test")
	public String getTest() {
		return "test";
	}

	@GetMapping(value = "/cached")
	@Cacheable("getCached")
	public String getCached() {
		log.info("Cacing");
		return "cached";
	}

	@GetMapping(value = "/rated")
	public String getRated() {
		return "/rated";
	}

}
