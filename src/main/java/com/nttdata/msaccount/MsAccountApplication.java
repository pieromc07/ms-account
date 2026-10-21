package com.nttdata.msaccount;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.nttdata.msaccount.config.AccountProperties;
import com.nttdata.msaccount.config.CatalogProperties;
import com.nttdata.msaccount.config.JwtProperties;

@SpringBootApplication
@EnableConfigurationProperties({AccountProperties.class, CatalogProperties.class, JwtProperties.class})
public class MsAccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsAccountApplication.class, args);
    }

}
