package io.storeyes.accesscontrol.employees.dto;

import io.storeyes.accesscontrol.employees.entities.Credential;

import java.util.Set;
import java.util.UUID;

/**
 * Payload for creating or updating an employee.
 * {@code synced} is optional and defaults to {@code true} when omitted.
 */
public record EmployeeRequest(
        String name,
        UUID roleId,
        String code,
        Set<Credential> credentials,
        Boolean synced) {
}
