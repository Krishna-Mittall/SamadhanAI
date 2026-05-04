package com.samadhanai.samadhanai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SamadhanAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SamadhanAiApplication.class, args);
    }
}
