package br.com.brasil_saas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(
    basePackages = "br.com.brasil_saas",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "br\\.com\\.brasil_saas\\..*Application"
    )
)
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EnableScheduling
public class BrasilSaasErpApplication {

    static void main(String[] args) {
        SpringApplication.run(BrasilSaasErpApplication.class, args);
    }
}
