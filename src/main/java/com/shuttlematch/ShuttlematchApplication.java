package com.shuttlematch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ShuttlematchApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShuttlematchApplication.class, args);
	}

}
