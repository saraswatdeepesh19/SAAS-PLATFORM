package com.saas.usage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.TimeZone;

@SpringBootApplication
public class UsageServiceApplication {
    /** Sets UTC before starting usage processing so monthly period boundaries are consistent. */
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(UsageServiceApplication.class, args);
    }
}