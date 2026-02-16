package com.flashsale.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.flashsale")
@EntityScan("com.flashsale")
@EnableJpaRepositories("com.flashsale")
@EnableScheduling
public class CacheRefreshApplication {

    public static void main(String[] args) {
        SpringApplication.run(CacheRefreshApplication.class, args);
    }
}
