package com.palworldadmin.app.service.rcon;

public class RconException extends RuntimeException {
    public RconException(String message) {
        super(message);
    }

    public RconException(String message, Throwable cause) {
        super(message, cause);
    }
}
