package com.oms.aftersales;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.oms.aftersales.mapper")
@EnableFeignClients
public class AfterSalesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AfterSalesServiceApplication.class, args);
    }
}
