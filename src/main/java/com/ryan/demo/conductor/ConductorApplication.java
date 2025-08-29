package com.ryan.demo.conductor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Conductor Application - Command Pattern Demo
 *
 * @author Ryan
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync
@EnableTransactionManagement
public class ConductorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConductorApplication.class, args);
    }
}