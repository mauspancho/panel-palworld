package com.palworldadmin.app.entity;

public enum ServerStatus {
    RUNNING("Encendido"),
    STOPPED("Detenido"),
    RESTARTING("Reiniciando / actualizando"),
    ERROR("Error"),
    UNKNOWN("Desconocido");

    private final String label;

    ServerStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
