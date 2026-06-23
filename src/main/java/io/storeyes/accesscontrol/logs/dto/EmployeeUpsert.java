package io.storeyes.accesscontrol.logs.dto;

import io.storeyes.accesscontrol.employees.entities.Credential;

import java.util.Set;

/** Employee descriptor sent with a punch batch; created on the fly if the code is unknown. */
public record EmployeeUpsert(String name, String code, Set<Credential> credentials) {}
