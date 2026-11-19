package com.example.animalpictures;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Animal pictures service backed by PostgreSQL. The REST API and logic live in animal-pictures-core. */
@SpringBootApplication
public class PostgresAnimalPicturesApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostgresAnimalPicturesApplication.class, args);
    }
}
