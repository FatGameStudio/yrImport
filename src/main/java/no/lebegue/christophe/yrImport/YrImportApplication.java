package no.lebegue.christophe.yrImport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAutoConfiguration
public class YrImportApplication {

	public static void main(String[] args) {
		SpringApplication.run(YrImportApplication.class, args);
	}
}