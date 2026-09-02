package io.storeyes.accesscontrol.tenant;

import io.storeyes.accesscontrol.devices.config.DeviceIngestProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    /** Device push notifications carry no X-STORE-CODE; they are attributed to a configured tenant. */
    private static final String DEVICE_PATH_PREFIX = "/api/device-events";

    private final SchemaService schemaService;
    private final DeviceIngestProperties deviceIngestProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String storeCode = request.getHeader("X-STORE-CODE");
        String schema;
        if (storeCode != null && !storeCode.isBlank()) {
            schema = storeCode.trim().toLowerCase();
        } else if (request.getRequestURI().startsWith(DEVICE_PATH_PREFIX)) {
            schema = deviceIngestProperties.tenant().trim().toLowerCase();
        } else {
            schema = "public";
        }
        try {
            schemaService.ensureSchema(schema);
            TenantContext.set(schema);
            chain.doFilter(request, response);
        } catch (ResponseStatusException e) {
            response.sendError(e.getStatusCode().value(), e.getReason());
        } finally {
            TenantContext.clear();
        }
    }
}
