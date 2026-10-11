package com.smartresume.planner.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Enables @CreatedDate / @LastModifiedDate on the base entities. */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
