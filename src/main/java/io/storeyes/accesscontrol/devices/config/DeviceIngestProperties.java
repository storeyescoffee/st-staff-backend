package io.storeyes.accesscontrol.devices.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the device push-notification endpoint ({@code POST /api/device-events}).
 *
 * <p>The devices push each verification event straight to us over plain HTTP and carry no
 * {@code X-STORE-CODE} header, so for now every device event is attributed to a single tenant.
 *
 * @param tenant       store code every device event is attributed to (schema name, lower-cased)
 * @param enabled      when false the endpoint answers 503 and processes nothing
 * @param sharedSecret optional; when set, a request must present it as {@code ?token=} or the
 *                     {@code X-Device-Token} header, otherwise it is rejected with 401
 */
@ConfigurationProperties(prefix = "device.ingest")
public record DeviceIngestProperties(String tenant, Boolean enabled, String sharedSecret) {

    public DeviceIngestProperties {
        if (tenant == null || tenant.isBlank()) tenant = "lendys";
        if (enabled == null) enabled = Boolean.TRUE;
    }

    public boolean requiresSecret() {
        return sharedSecret != null && !sharedSecret.isBlank();
    }
}
