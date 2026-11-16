package com.ai.studenthelper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StudentHelperApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentHelperApplication.class, args);
    }

}
