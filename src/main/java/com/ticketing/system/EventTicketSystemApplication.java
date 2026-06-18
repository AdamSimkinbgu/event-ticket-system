package com.ticketing.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling 
public class EventTicketSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventTicketSystemApplication.class, args);
	}

}
