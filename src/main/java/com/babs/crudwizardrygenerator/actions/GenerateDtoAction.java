package com.babs.crudwizardrygenerator.actions;

public class GenerateDtoAction extends BaseGenerateAction {
    @Override protected boolean isGenerateController() { return false; }
    @Override protected boolean isGenerateService() { return false; }
    @Override protected boolean isGenerateRepository() { return false; }
    @Override protected boolean isGenerateDto() { return true; }
}