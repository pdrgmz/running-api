package com.running.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RunningApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RunningApiApplication.class, args);
    }
}
