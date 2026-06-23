package io.storeyes.accesscontrol;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads a .env file from the JAR's working directory before Spring binds properties.
 * The file is optional — missing is silently ignored (useful for dev without .env).
 * Env vars always win over .env (system env is added by Spring at higher priority).
 */
public class DotenvEnvironmentPostProcessor implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        Dotenv dotenv = Dotenv.configure()
                .directory("./")
                .ignoreIfMissing()
                .load();

        Map<String, Object> props = new HashMap<>();
        dotenv.entries().forEach(e -> props.put(e.getKey(), e.getValue()));

        if (!props.isEmpty()) {
            // addLast so that real env vars and system properties still override .env
            environment.getPropertySources().addLast(new MapPropertySource("dotenvFile", props));
        }
    }
}
