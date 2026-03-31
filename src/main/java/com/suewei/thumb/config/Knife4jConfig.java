package com.suewei.thumb.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("thumb-backend接口文档")
                        .version("1.0")
                        .description("thumb-backend项目接口文档")
                        .contact(new Contact().name("suewei").email("2941794982@qq.com"))
                        .license(new License().name("Apache 2.0").url("https://springdoc.org"))
                );
    }
}