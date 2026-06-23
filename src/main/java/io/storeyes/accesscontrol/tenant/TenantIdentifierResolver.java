package io.storeyes.accesscontrol.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        String schema = TenantContext.get();
        return schema != null ? schema : "public";
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
