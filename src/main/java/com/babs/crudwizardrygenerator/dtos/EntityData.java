package com.babs.crudwizardrygenerator.dtos;

import dtos.FieldData;
import java.util.List;

public class EntityData {

    private final String entityName;
    private final String packageName;
    private final boolean lombokData;
    private final boolean lombokBuilder;
    private final PersistenceApi persistenceApi;
    private final List<FieldData> fields;
    
    // New fields for CRUD generation options
    private final boolean generateEntity;
    private final boolean generateController;
    private final boolean generateService;
    private final boolean generateRepository;
    private final boolean generateDto;
    private final boolean generateServiceInterface;

    public EntityData(
            String entityName,
            String packageName,
            boolean lombokData,
            boolean lombokBuilder,
            PersistenceApi persistenceApi,
            List<FieldData> fields,
            boolean generateEntity,
            boolean generateController,
            boolean generateService,
            boolean generateRepository,
            boolean generateDto,
            boolean generateServiceInterface
    ) {
        this.entityName = entityName;
        this.packageName = packageName;
        this.lombokData = lombokData;
        this.lombokBuilder = lombokBuilder;
        this.persistenceApi = persistenceApi;
        this.fields = fields;
        this.generateEntity = generateEntity;
        this.generateController = generateController;
        this.generateService = generateService;
        this.generateRepository = generateRepository;
        this.generateDto = generateDto;
        this.generateServiceInterface = generateServiceInterface;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getPackageName() {
        return packageName;
    }

    public boolean isLombokData() {
        return lombokData;
    }

    public boolean isLombokBuilder() {
        return lombokBuilder;
    }

    public PersistenceApi getPersistenceApi() {
        return persistenceApi;
    }

    public List<FieldData> getFields() {
        return fields;
    }

    public boolean isGenerateEntity() {
        return generateEntity;
    }

    public boolean isGenerateController() {
        return generateController;
    }

    public boolean isGenerateService() {
        return generateService;
    }

    public boolean isGenerateRepository() {
        return generateRepository;
    }

    public boolean isGenerateDto() {
        return generateDto;
    }

    public boolean isGenerateServiceInterface() {
        return generateServiceInterface;
    }
}
