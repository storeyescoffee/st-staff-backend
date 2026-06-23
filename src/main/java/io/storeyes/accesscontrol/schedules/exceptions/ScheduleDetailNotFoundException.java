package io.storeyes.accesscontrol.schedules.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ScheduleDetailNotFoundException extends RuntimeException {

    public ScheduleDetailNotFoundException(UUID id) {
        super("Schedule detail not found with id: " + id);
    }
}
