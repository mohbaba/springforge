package com.babs.crudwizardrygenerator.actions;

public class GenerateServiceAction extends BaseGenerateAction {
    @Override protected boolean isGenerateController() { return false; }
    @Override protected boolean isGenerateService() { return true; }
    @Override protected boolean isGenerateRepository() { return false; }
    @Override protected boolean isGenerateDto() { return false; }
}