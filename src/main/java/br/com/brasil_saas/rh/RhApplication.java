package br.com.brasil_saas.rh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "br.com.brasil_saas.rh",
    "br.com.brasil_saas.core",
    "br.com.brasil_saas.shared"
})
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EnableScheduling
@EnableJpaRepositories(
    basePackages = {
        "br.com.brasil_saas.rh.repository",
        "br.com.brasil_saas.core.repository",
        "br.com.brasil_saas.shared.repository"
    }
)
@EntityScan(
    basePackages = {
        "br.com.brasil_saas.rh.model",
        "br.com.brasil_saas.core.model",
        "br.com.brasil_saas.shared.model"
    }
)
public class RhApplication {

    public static void main(String[] args) {
        SpringApplication.run(RhApplication.class, args);
    }
}
