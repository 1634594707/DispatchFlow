package com.fsd.bootstrap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.fsd")
@MapperScan(basePackages = {
        "com.fsd.order.mapper",
        "com.fsd.dispatch.mapper",
        "com.fsd.vehicle.mapper"
})
@EnableScheduling
public class FsdCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(FsdCoreApplication.class, args);
    }
}
