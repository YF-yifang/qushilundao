package com.lsdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.lsdp.mapper")
@SpringBootApplication
public class LsDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(LsDianPingApplication.class, args);
    }

}
