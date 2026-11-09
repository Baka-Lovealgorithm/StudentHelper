package com.ai.studenthelper.common.dto;

public record OptionalSectionsResponse(boolean idea, boolean knowledge, boolean code) {

    public boolean any() {
        return idea || knowledge || code;
    }
}
