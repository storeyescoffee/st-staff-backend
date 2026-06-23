package io.storeyes.accesscontrol.workmodes.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class WorkModeNotFoundException extends RuntimeException {

    public WorkModeNotFoundException(UUID id) {
        super("Work mode not found with id: " + id);
    }
}
