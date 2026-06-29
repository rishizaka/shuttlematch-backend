package com.shuttlematch;

import org.springframework.boot.SpringApplication;

public class TestShuttlematchApplication {

	public static void main(String[] args) {
		SpringApplication.from(ShuttlematchApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
