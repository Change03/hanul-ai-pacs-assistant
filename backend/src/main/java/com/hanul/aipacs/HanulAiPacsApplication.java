package com.hanul.aipacs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class HanulAiPacsApplication {
    public static void main(String[] args) {
        SpringApplication.run(HanulAiPacsApplication.class, args);
    }
}
