package com.flashsale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FlashSaleApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashSaleApiApplication.class, args);
    }
}
