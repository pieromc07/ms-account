package com.nttdata.msaccount;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.nttdata.msaccount.config.AccountProperties;

@SpringBootApplication
@EnableConfigurationProperties(AccountProperties.class)
public class MsAccountApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsAccountApplication.class, args);
	}

}
