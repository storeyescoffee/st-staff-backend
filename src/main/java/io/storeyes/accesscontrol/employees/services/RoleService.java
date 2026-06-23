package io.storeyes.accesscontrol.employees.services;

import io.storeyes.accesscontrol.employees.dto.RoleRequest;
import io.storeyes.accesscontrol.employees.entities.Role;
import io.storeyes.accesscontrol.employees.exceptions.RoleNotFoundException;
import io.storeyes.accesscontrol.employees.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<Role> findAll() {
        return roleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Role findById(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new RoleNotFoundException(id));
    }

    @Transactional
    public Role create(RoleRequest request) {
        Role role = Role.builder()
                .name(request.name())
                .build();
        return roleRepository.save(role);
    }

    @Transactional
    public Role update(UUID id, RoleRequest request) {
        Role role = findById(id);
        role.setName(request.name());
        return roleRepository.save(role);
    }

    @Transactional
    public void delete(UUID id) {
        if (!roleRepository.existsById(id)) {
            throw new RoleNotFoundException(id);
        }
        roleRepository.deleteById(id);
    }
}
