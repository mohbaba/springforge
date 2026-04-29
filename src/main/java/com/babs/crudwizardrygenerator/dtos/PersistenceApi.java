package com.babs.crudwizardrygenerator.dtos;

public enum PersistenceApi {
    AUTO("Auto (recommended)"),
    JAKARTA("Jakarta"),
    JAVAX("Javax");

    private final String displayName;

    PersistenceApi(String displayName) {
        this.displayName = displayName;
    }

    public String getImportPrefix() {
        return this == JAVAX ? "javax.persistence" : "jakarta.persistence";
    }

    @Override
    public String toString() {
        return displayName;
    }
}
