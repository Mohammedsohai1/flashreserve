package com.example.flashreserve;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class FlashreserveApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashreserveApplication.class, args);
    }

}
