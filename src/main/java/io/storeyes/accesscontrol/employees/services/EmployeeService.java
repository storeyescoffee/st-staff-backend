package io.storeyes.accesscontrol.employees.services;

import io.storeyes.accesscontrol.employees.dto.EmployeeRequest;
import io.storeyes.accesscontrol.employees.dto.EmployeeResponse;
import io.storeyes.accesscontrol.employees.entities.Credential;
import io.storeyes.accesscontrol.employees.entities.Employee;
import io.storeyes.accesscontrol.employees.entities.Role;
import io.storeyes.accesscontrol.employees.exceptions.EmployeeNotFoundException;
import io.storeyes.accesscontrol.employees.exceptions.RoleNotFoundException;
import io.storeyes.accesscontrol.employees.repositories.EmployeeRepository;
import io.storeyes.accesscontrol.employees.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<EmployeeResponse> findAll() {
        return employeeRepository.findAll().stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse findById(UUID id) {
        return EmployeeResponse.from(getOrThrow(id));
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        Employee employee = Employee.builder()
                .name(request.name())
                .role(resolveRole(request.roleId()))
                .code(request.code())
                .credentials(resolveCredentials(request.credentials()))
                .synced(request.synced() == null || request.synced())
                .build();
        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse update(UUID id, EmployeeRequest request) {
        Employee employee = getOrThrow(id);
        employee.setName(request.name());
        employee.setRole(resolveRole(request.roleId()));
        employee.setCode(request.code());
        employee.setCredentials(resolveCredentials(request.credentials()));
        if (request.synced() != null) {
            employee.setSynced(request.synced());
        }
        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    @Transactional
    public void delete(UUID id) {
        if (!employeeRepository.existsById(id)) {
            throw new EmployeeNotFoundException(id);
        }
        employeeRepository.deleteById(id);
    }

    private Employee getOrThrow(UUID id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    private Role resolveRole(UUID roleId) {
        if (roleId == null) {
            return null;
        }
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
    }

    private Set<Credential> resolveCredentials(Set<Credential> credentials) {
        return credentials == null ? new LinkedHashSet<>() : new LinkedHashSet<>(credentials);
    }
}
