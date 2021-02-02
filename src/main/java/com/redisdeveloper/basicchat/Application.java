package com.redisdeveloper.basicchat;

import java.util.Collections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        // Read environment variables
        String port = System.getenv("PORT");
        if (port == null) {
            port = "8080";
        }
        SpringApplication app = new SpringApplication(Application.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", port));
        app.run(args);

    }
}
