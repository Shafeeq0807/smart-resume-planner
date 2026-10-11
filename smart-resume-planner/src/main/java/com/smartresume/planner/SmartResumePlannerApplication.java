package com.smartresume.planner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableAsync
@EnableScheduling
public class SmartResumePlannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartResumePlannerApplication.class, args);
    }
}
