package br.com.brasil_saas.ia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableFeignClients
@ComponentScan(basePackages = {
    "br.com.brasil_saas.ia",
    "br.com.brasil_saas.shared.config"
})
@EnableJpaRepositories(basePackages = {
    "br.com.brasil_saas.ia.repository",
    "br.com.brasil_saas.shared.repository"
})
@EntityScan(basePackages = {
    "br.com.brasil_saas.ia.model",
    "br.com.brasil_saas.shared.model"
})
public class BrasilSaasIaApplication {

    static void main(String[] args) {
        SpringApplication.run(BrasilSaasIaApplication.class, args);
    }
}
