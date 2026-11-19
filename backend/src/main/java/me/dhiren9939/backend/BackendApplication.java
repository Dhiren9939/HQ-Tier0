package me.dhiren9939.backend;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;

import java.util.TimeZone;

@Slf4j
@SpringBootApplication
@EnableResilientMethods
public class BackendApplication {

	public static void main(String[] args) {
		// The backend runs in UTC whatever the host's zone: timestamps in the database, in logs and in
		// published events are all UTC. It also avoids the OS reporting a deprecated alias such as
		// "Asia/Calcutta", which Postgres rejects as a JDBC session time zone.
		// TimeZone.setDefault (not System.setProperty) is required: something
		// (e.g. the static Logger field above triggering Logback init) may have
		// already cached TimeZone.getDefault() by this point, and a system
		// property change alone would not retroactively update that cache.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		log.info("JVM default timezone pinned to {}", TimeZone.getDefault().getID());
		SpringApplication.run(BackendApplication.class, args);
	}

}
