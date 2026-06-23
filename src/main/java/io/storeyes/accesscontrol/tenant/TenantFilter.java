package io.storeyes.accesscontrol.tenant;

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

    private final SchemaService schemaService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String storeCode = request.getHeader("X-STORE-CODE");
        String schema = (storeCode == null || storeCode.isBlank())
                ? "public"
                : storeCode.trim().toLowerCase();
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
