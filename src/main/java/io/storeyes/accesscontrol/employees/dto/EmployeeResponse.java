package io.storeyes.accesscontrol.employees.dto;

import io.storeyes.accesscontrol.employees.entities.Credential;
import io.storeyes.accesscontrol.employees.entities.Employee;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** API view of an {@link Employee}, with the role flattened to id + name. */
public record EmployeeResponse(
        UUID id,
        String name,
        RoleSummary role,
        String code,
        List<Credential> credentials,
        boolean synced) {

    public record RoleSummary(UUID id, String name) {
    }

    public static EmployeeResponse from(Employee e) {
        RoleSummary role = e.getRole() == null
                ? null
                : new RoleSummary(e.getRole().getId(), e.getRole().getName());
        // Emit credentials in a stable enum order (BADGE, PIN, FINGERPRINT).
        List<Credential> creds = Arrays.stream(Credential.values())
                .filter(e.getCredentials()::contains)
                .toList();
        return new EmployeeResponse(e.getId(), e.getName(), role, e.getCode(), creds, e.isSynced());
    }
}
