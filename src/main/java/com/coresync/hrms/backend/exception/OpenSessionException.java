// src/main/java/com/coresync/hrms/backend/exception/OpenSessionException.java
package com.coresync.hrms.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class OpenSessionException extends RuntimeException {

    private final Long openSessionId;

    public OpenSessionException(Long openSessionId) {
        super(String.format(
            "Punch-in rejected: An open session (ID: %d) already exists. Please punch out first.",
            openSessionId
        ));
        this.openSessionId = openSessionId;
    }

    public Long getOpenSessionId() { return openSessionId; }
}