package com.babs.crudwizardrygenerator.actions;

public class GenerateRepositoryAction extends BaseGenerateAction {
    @Override protected boolean isGenerateController() { return false; }
    @Override protected boolean isGenerateService() { return false; }
    @Override protected boolean isGenerateRepository() { return true; }
    @Override protected boolean isGenerateDto() { return false; }
}