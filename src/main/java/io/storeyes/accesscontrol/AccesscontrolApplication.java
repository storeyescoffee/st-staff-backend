package io.storeyes.accesscontrol;

import io.storeyes.accesscontrol.devices.config.DeviceIngestProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DeviceIngestProperties.class)
public class AccesscontrolApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccesscontrolApplication.class, args);
	}

}
