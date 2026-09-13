package me.dhiren9939.api;

import java.util.TimeZone;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class ApiApplication {

	public static void main(String[] args) {
		// The JVM reports the OS timezone as the deprecated alias "Asia/Calcutta";
		// Postgres no longer recognizes it and rejects the JDBC connection outright.
		// TimeZone.setDefault (not System.setProperty) is required: something
		// (e.g. the static Logger field above triggering Logback init) may have
		// already cached TimeZone.getDefault() by this point, and a system
		// property change alone would not retroactively update that cache.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		log.info("JVM default timezone pinned to {}", TimeZone.getDefault().getID());
		SpringApplication.run(ApiApplication.class, args);
	}

}
