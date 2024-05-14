package com.ysdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.ysdp.mapper")
@SpringBootApplication
public class YunShangDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(YunShangDianPingApplication.class, args);
    }

}
