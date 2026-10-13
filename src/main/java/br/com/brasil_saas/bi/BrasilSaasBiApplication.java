package br.com.brasil_saas.bi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableFeignClients
@ComponentScan(basePackages = {
    "br.com.brasil_saas.bi",
    "br.com.brasil_saas.shared.config"
})
@EnableJpaRepositories(basePackages = {
    "br.com.brasil_saas.bi.repository",
    "br.com.brasil_saas.shared.repository"
})
@EntityScan(basePackages = {
    "br.com.brasil_saas.bi.model",
    "br.com.brasil_saas.shared.model"
})
public class BrasilSaasBiApplication {

    static void main(String[] args) {
        SpringApplication.run(BrasilSaasBiApplication.class, args);
    }
}
