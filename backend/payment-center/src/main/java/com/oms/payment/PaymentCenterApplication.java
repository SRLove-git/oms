package com.oms.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@EnableFeignClients
@MapperScan("com.oms.payment.mapper")
public class PaymentCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentCenterApplication.class, args);
    }
}
