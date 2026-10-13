package br.com.brasil_saas.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EnableScheduling
@EnableJpaRepositories(
    basePackages = {
        "br.com.brasil_saas.core.repository",
        "br.com.brasil_saas.shared.repository"
    }
)
@EntityScan(
    basePackages = {
        "br.com.brasil_saas.core.model",
        "br.com.brasil_saas.shared.model"
    }
)
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}
