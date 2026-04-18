package com.babs.crudwizardrygenerator.actions;

public class GenerateControllerAction extends BaseGenerateAction {
    @Override protected boolean isGenerateController() { return true; }
    @Override protected boolean isGenerateService() { return false; }
    @Override protected boolean isGenerateRepository() { return false; }
    @Override protected boolean isGenerateDto() { return false; }
}