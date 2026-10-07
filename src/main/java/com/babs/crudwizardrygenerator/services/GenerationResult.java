package com.babs.crudwizardrygenerator.services;

import java.util.List;

public record GenerationResult(boolean success, List<String> createdFiles, List<String> modifiedFiles,
                               List<String> warnings) {
    public static GenerationResult failure(String warning) {
        return new GenerationResult(false, List.of(), List.of(), List.of(warning));
    }
}
