package com.alon.bookstore;

import org.springframework.boot.SpringApplication;

public class TestBookstoreApplication {

	public static void main(String[] args) {
		SpringApplication.from(BookstoreApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
