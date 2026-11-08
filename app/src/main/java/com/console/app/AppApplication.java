package com.console.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AppApplication {


	public static void main(String[] args) {
		System.setProperty("java.net.preferIPv6Addresses", "true");
		SpringApplication.run(AppApplication.class, args);
	}

}
