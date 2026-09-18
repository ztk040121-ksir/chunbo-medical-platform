package com.chunbo.medical;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 春播云诊所 - 基于 Spring AI 的医疗问诊智能体平台启动类
 */
@SpringBootApplication
@MapperScan("com.chunbo.medical.mapper")
public class ChunboMedicalApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChunboMedicalApplication.class, args);
        System.out.println("=================================================");
        System.out.println(">>> 春播云诊所 AI 医疗问诊智能体后端服务启动成功 <<<");
        System.out.println(">>> 接口文档与工作台服务监听端口: 8080        <<<");
        System.out.println("=================================================");
    }
}
