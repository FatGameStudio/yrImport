package no.lebegue.christophe.yrImport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAutoConfiguration
public class YrImportApplication {
	private static boolean importFile = false;
	private static boolean parseFile = false;
	
	public static boolean isImportFile() {
		return importFile;
	}

	public static boolean isParseFile() {
		return parseFile;
	}
	public static void main(String[] args) {
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-i")) {
				importFile = true;
			}else if(args[i].equals("-p")){
				parseFile = true;
			}
		}
		
		SpringApplication.run(YrImportApplication.class, args);
	}
}