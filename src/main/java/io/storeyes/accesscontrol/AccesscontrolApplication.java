package io.storeyes.accesscontrol;

import io.storeyes.accesscontrol.devices.config.DeviceIngestProperties;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DeviceIngestProperties.class)
public class AccesscontrolApplication {

	public static void main(String[] args) {
		// Run the server in GMT+0 regardless of the host's timezone.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(AccesscontrolApplication.class, args);
	}

}
