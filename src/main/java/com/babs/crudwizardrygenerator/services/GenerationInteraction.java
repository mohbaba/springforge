package com.babs.crudwizardrygenerator.services;

/** Keeps UI prompts outside agent tools while letting both entry points share generator code. */
public interface GenerationInteraction {
    boolean confirmOverwrite(String fileName, String packageName);

    boolean confirmCreateController(String serviceName);

    default void info(String message) {
    }

    default void success(String message) {
    }

    static GenerationInteraction headless(boolean overwrite, boolean createController) {
        return new GenerationInteraction() {
            public boolean confirmOverwrite(String fileName, String packageName) { return overwrite; }
            public boolean confirmCreateController(String serviceName) { return createController; }
        };
    }
}
