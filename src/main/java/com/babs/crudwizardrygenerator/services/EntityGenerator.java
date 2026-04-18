package com.babs.crudwizardrygenerator.services;

import com.babs.crudwizardrygenerator.dtos.EntityData;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import dtos.FieldData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EntityGenerator {

    private final Project project;
    private final EntityData data;
    private final PsiDirectory initialDirectory;

    public EntityGenerator(Project project, EntityData data, PsiDirectory initialDirectory) {
        this.project = project;
        this.data = data;
        this.initialDirectory = initialDirectory;
    }

    public void generate() {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            // 0. Process custom fields
            List<String> extraImports = processCustomFields();

            // 1. Generate Entity
            generateFile(data.getPackageName(), data.getEntityName(), renderEntityTemplate(extraImports));

            // 2. Generate Repository
            if (data.isGenerateRepository()) {
                String repoPackage = data.getPackageName().replace(".entity", ".repository").replace(".model", ".repository");
                if (repoPackage.equals(data.getPackageName())) repoPackage += ".repository";
                
                generateFile(repoPackage, data.getEntityName() + "Repository", renderRepositoryTemplate(repoPackage));
            }

            // 3. Generate Service
            if (data.isGenerateService()) {
                String servicePackage = data.getPackageName().replace(".entity", ".service").replace(".model", ".service");
                if (servicePackage.equals(data.getPackageName())) servicePackage += ".service";

                if (data.isGenerateServiceInterface()) {
                    generateFile(servicePackage, data.getEntityName() + "Service", renderServiceInterfaceTemplate(servicePackage));
                    generateFile(servicePackage + ".impl", data.getEntityName() + "ServiceImpl", renderServiceImplTemplate(servicePackage));
                } else {
                    generateFile(servicePackage, data.getEntityName() + "Service", renderServiceTemplate(servicePackage));
                }
            }

            // 4. Generate Controller
            if (data.isGenerateController()) {
                String controllerPackage = data.getPackageName().replace(".entity", ".controller").replace(".model", ".controller");
                if (controllerPackage.equals(data.getPackageName())) controllerPackage += ".controller";

                generateFile(controllerPackage, data.getEntityName() + "Controller", renderControllerTemplate(controllerPackage));
            }
            
            // 5. Generate DTO
            if (data.isGenerateDto()) {
                String dtoPackage = data.getPackageName().replace(".entity", ".dto").replace(".model", ".dto");
                if (dtoPackage.equals(data.getPackageName())) dtoPackage += ".dto";

                generateFile(dtoPackage, data.getEntityName() + "Dto", renderDtoTemplate(dtoPackage));
            }
        });
    }

    private List<String> processCustomFields() {
        Set<String> imports = new HashSet<>();
        Set<String> processedTypes = new HashSet<>();

        for (FieldData field : data.getFields()) {
            String typeRaw = field.getType();
            String[] potentialClasses = typeRaw.split("[^a-zA-Z0-9_]");
            
            for (String typeName : potentialClasses) {
                if (typeName.isEmpty()) continue;
                if (processedTypes.contains(typeName)) continue;
                processedTypes.add(typeName);
                
                if (isPrimitive(typeName)) continue;

                PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(typeName, GlobalSearchScope.allScope(project));
                
                if (classes.length > 0) {
                    PsiClass targetClass = null;
                    // Prioritize java.util
                    for (PsiClass cls : classes) {
                        String fqn = cls.getQualifiedName();
                        if (fqn != null && fqn.startsWith("java.util.")) {
                            targetClass = cls;
                            break;
                        }
                    }
                    
                    if (targetClass == null) {
                        for (PsiClass cls : classes) {
                            String fqn = cls.getQualifiedName();
                            if (fqn != null && !fqn.startsWith("java.lang.")) {
                                targetClass = cls;
                                break;
                            }
                        }
                    }
                    
                    if (targetClass != null) {
                        String fqn = targetClass.getQualifiedName();
                        if (fqn != null) {
                            String packageName = fqn.substring(0, fqn.lastIndexOf('.'));
                            if (!packageName.equals(data.getPackageName())) {
                                imports.add(fqn);
                            }
                        }
                    }
                } else {
                    // Not found
                    if (Character.isUpperCase(typeName.charAt(0)) && !isStandardJavaType(typeName)) {
                        if (!fileExistsInCurrentPackage(typeName)) {
                            createCustomEntity(typeName);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(imports);
    }

    private boolean isPrimitive(String type) {
        return type.equals("int") || type.equals("long") || type.equals("boolean") || 
               type.equals("double") || type.equals("float") || type.equals("short") || 
               type.equals("byte") || type.equals("char") || type.equals("void");
    }

    private boolean isStandardJavaType(String type) {
        return type.equals("String") || type.equals("Integer") || type.equals("Long") || 
               type.equals("Boolean") || type.equals("Double") || type.equals("Float") || 
               type.equals("Date") || type.equals("LocalDate") || type.equals("LocalDateTime") ||
               type.equals("List") || type.equals("Set") || type.equals("Map") || 
               type.equals("Collection") || type.equals("UUID");
    }

    private void createCustomEntity(String entityName) {
        StringBuilder sb = new StringBuilder();
        if (data.getPackageName() != null && !data.getPackageName().isBlank()) {
            sb.append("package ").append(data.getPackageName()).append(";\n\n");
        }

        sb.append("import jakarta.persistence.*;\n");
        sb.append("import lombok.Data;\n");
        sb.append("import lombok.Builder;\n");
        sb.append("import lombok.NoArgsConstructor;\n");
        sb.append("import lombok.AllArgsConstructor;\n\n");

        sb.append("@Data\n");
        sb.append("@Builder\n");
        sb.append("@NoArgsConstructor\n");
        sb.append("@AllArgsConstructor\n");
        sb.append("@Entity\n");
        sb.append("public class ").append(entityName).append(" {\n\n");
        
        sb.append("    @Id\n");
        sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
        sb.append("    private Long id;\n\n");
        
        sb.append("}\n");
        
        generateFile(data.getPackageName(), entityName, sb.toString());
    }

    private void generateFile(String packageName, String className, String content) {
        PsiDirectory targetDir = resolveTargetDirectory(packageName);
        if (targetDir == null) return;

        String fileName = className + ".java";
        PsiFile existingFile = targetDir.findFile(fileName);

        if (existingFile != null) {
            int result = Messages.showYesNoDialog(
                    project,
                    "File '" + fileName + "' already exists in " + packageName + ".\nDo you want to overwrite it?",
                    "File Already Exists",
                    Messages.getQuestionIcon()
            );

            if (result == Messages.NO) {
                return; 
            }
            
            existingFile.delete();
        }

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, content);

        targetDir.add(file);

        PsiFile createdFile = targetDir.findFile(fileName);
        if (createdFile != null) {
            CodeStyleManager.getInstance(project).reformat(createdFile);
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(createdFile);
        }
    }

    private boolean fileExistsInCurrentPackage(String className) {
        PsiDirectory targetDir = findTargetDirectory(data.getPackageName());
        if (targetDir == null) return false;
        return targetDir.findFile(className + ".java") != null;
    }

    private PsiDirectory findTargetDirectory(String packageName) {
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
        VirtualFile sourceRoot = fileIndex.getSourceRootForFile(initialDirectory.getVirtualFile());

        if (sourceRoot == null) return initialDirectory;

        PsiDirectory currentDir = PsiManager.getInstance(project).findDirectory(sourceRoot);
        if (currentDir == null) return initialDirectory;

        if (packageName == null || packageName.isBlank()) return currentDir;

        String[] parts = packageName.split("\\.");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            PsiDirectory subDir = currentDir.findSubdirectory(part);
            if (subDir == null) return null;
            currentDir = subDir;
        }
        return currentDir;
    }

    private PsiDirectory resolveTargetDirectory(String packageName) {
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
        VirtualFile sourceRoot = fileIndex.getSourceRootForFile(initialDirectory.getVirtualFile());

        if (sourceRoot == null) return initialDirectory;

        PsiDirectory currentDir = PsiManager.getInstance(project).findDirectory(sourceRoot);
        if (currentDir == null) return initialDirectory;

        if (packageName == null || packageName.isBlank()) return currentDir;

        String[] parts = packageName.split("\\.");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            PsiDirectory subDir = currentDir.findSubdirectory(part);
            if (subDir == null) subDir = currentDir.createSubdirectory(part);
            currentDir = subDir;
        }
        return currentDir;
    }

    private String renderEntityTemplate(List<String> extraImports) {
        StringBuilder sb = new StringBuilder();
        if (data.getPackageName() != null && !data.getPackageName().isBlank()) {
            sb.append("package ").append(data.getPackageName()).append(";\n\n");
        }

        sb.append("import jakarta.persistence.*;\n");
        if (data.isLombokData()) sb.append("import lombok.Data;\n");
        if (data.isLombokBuilder()) sb.append("import lombok.Builder;\n");
        sb.append("import lombok.NoArgsConstructor;\n");
        sb.append("import lombok.AllArgsConstructor;\n");
        
        for (String importStmt : extraImports) {
            sb.append("import ").append(importStmt).append(";\n");
        }
        sb.append("\n");

        if (data.isLombokData()) sb.append("@Data\n");
        if (data.isLombokBuilder()) sb.append("@Builder\n");
        sb.append("@NoArgsConstructor\n@AllArgsConstructor\n");
        sb.append("@Entity\n");
        sb.append("public class ").append(data.getEntityName()).append(" {\n\n");
        
        boolean hasId = false;
        for (FieldData field : data.getFields()) {
            if (field.getName().equals("id")) {
                sb.append("    @Id\n    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
                hasId = true;
            }
            sb.append("    private ")
                    .append(field.getType())
                    .append(" ")
                    .append(field.getName())
                    .append(";\n");
        }
        
        sb.append("}\n");
        return sb.toString();
    }

    private String renderRepositoryTemplate(String packageName) {
        String idType = "Long";
        for (FieldData field : data.getFields()) {
            if (field.getName().equals("id")) {
                idType = field.getType();
                break;
            }
        }

        return "package " + packageName + ";\n\n" +
                "import " + data.getPackageName() + "." + data.getEntityName() + ";\n" +
                "import org.springframework.data.jpa.repository.JpaRepository;\n" +
                "import org.springframework.stereotype.Repository;\n\n" +
                "@Repository\n" +
                "public interface " + data.getEntityName() + "Repository extends JpaRepository<" + data.getEntityName() + ", " + idType + "> {\n" +
                "}\n";
    }

    private String renderServiceInterfaceTemplate(String packageName) {
        String entityName = data.getEntityName();
        String idType = "Long";
        for (FieldData field : data.getFields()) {
            if (field.getName().equals("id")) {
                idType = field.getType();
                break;
            }
        }
        
        String returnType = data.isGenerateDto() ? entityName + "Dto" : entityName;
        String paramType = data.isGenerateDto() ? entityName + "Dto" : entityName;

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        
        if (!data.isGenerateDto()) {
            sb.append("import ").append(data.getPackageName()).append(".").append(entityName).append(";\n");
        } else {
            String dtoPackage = data.getPackageName().replace(".entity", ".dto").replace(".model", ".dto");
            if (dtoPackage.equals(data.getPackageName())) dtoPackage += ".dto";
            sb.append("import ").append(dtoPackage).append(".").append(entityName).append("Dto;\n");
        }
        
        sb.append("import java.util.List;\n\n");
        
        sb.append("public interface ").append(entityName).append("Service {\n\n");
        sb.append("    List<").append(returnType).append("> findAll();\n\n");
        sb.append("    ").append(returnType).append(" save(").append(paramType).append(" entity);\n\n");
        sb.append("    ").append(returnType).append(" findById(").append(idType).append(" id);\n\n");
        sb.append("    void deleteById(").append(idType).append(" id);\n");
        sb.append("}\n");
        
        return sb.toString();
    }

    private String renderServiceImplTemplate(String packageName) {
        // This is essentially the same as renderServiceTemplate but implements the interface
        // and has @Service annotation on the Impl class
        String content = renderServiceTemplate(packageName + ".impl");
        String entityName = data.getEntityName();
        
        // Replace class declaration to implement interface
        String oldClassDecl = "public class " + entityName + "Service {";
        String newClassDecl = "public class " + entityName + "ServiceImpl implements " + entityName + "Service {";
        
        // We need to fix imports because the interface is in the parent package
        String importInterface = "import " + packageName + "." + entityName + "Service;\n";
        
        content = content.replace(oldClassDecl, newClassDecl);
        
        // Insert import after package declaration
        int packageEnd = content.indexOf(";") + 1;
        content = content.substring(0, packageEnd) + "\n\n" + importInterface + content.substring(packageEnd);
        
        return content;
    }

    private String renderServiceTemplate(String packageName) {
        String entityName = data.getEntityName();
        String repoName = entityName + "Repository";
        String repoVar = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1) + "Repository";
        
        String idType = "Long";
        for (FieldData field : data.getFields()) {
            if (field.getName().equals("id")) {
                idType = field.getType();
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import ").append(data.getPackageName()).append(".").append(entityName).append(";\n");
        
        // Import DTO if generated
        if (data.isGenerateDto()) {
            String dtoPackage = data.getPackageName().replace(".entity", ".dto").replace(".model", ".dto");
            if (dtoPackage.equals(data.getPackageName())) dtoPackage += ".dto";
            sb.append("import ").append(dtoPackage).append(".").append(entityName).append("Dto;\n");
        }
        
        sb.append("import org.springframework.stereotype.Service;\n");
        sb.append("import lombok.RequiredArgsConstructor;\n");
        sb.append("import java.util.List;\n");
        if (data.isGenerateDto()) {
            sb.append("import java.util.stream.Collectors;\n");
        }
        sb.append("\n");
        
        sb.append("@Service\n");
        sb.append("@RequiredArgsConstructor\n");
        sb.append("public class ").append(entityName).append("Service {\n\n");
        sb.append("    private final ").append(repoName).append(" ").append(repoVar).append(";\n\n");
        
        // findAll
        if (data.isGenerateDto()) {
            sb.append("    public List<").append(entityName).append("Dto> findAll() {\n");
            sb.append("        return ").append(repoVar).append(".findAll().stream()\n");
            sb.append("                .map(this::mapToDto)\n");
            sb.append("                .collect(Collectors.toList());\n");
            sb.append("    }\n\n");
        } else {
            sb.append("    public List<").append(entityName).append("> findAll() {\n");
            sb.append("        return ").append(repoVar).append(".findAll();\n");
            sb.append("    }\n\n");
        }
        
        // save
        if (data.isGenerateDto()) {
            sb.append("    public ").append(entityName).append("Dto save(").append(entityName).append("Dto dto) {\n");
            sb.append("        ").append(entityName).append(" entity = mapToEntity(dto);\n");
            sb.append("        ").append(entityName).append(" saved = ").append(repoVar).append(".save(entity);\n");
            sb.append("        return mapToDto(saved);\n");
            sb.append("    }\n\n");
        } else {
            sb.append("    public ").append(entityName).append(" save(").append(entityName).append(" entity) {\n");
            sb.append("        return ").append(repoVar).append(".save(entity);\n");
            sb.append("    }\n\n");
        }
        
        // findById
        if (data.isGenerateDto()) {
            sb.append("    public ").append(entityName).append("Dto findById(").append(idType).append(" id) {\n");
            sb.append("        ").append(entityName).append(" entity = ").append(repoVar).append(".findById(id).orElse(null);\n");
            sb.append("        return entity != null ? mapToDto(entity) : null;\n");
            sb.append("    }\n\n");
        } else {
            sb.append("    public ").append(entityName).append(" findById(").append(idType).append(" id) {\n");
            sb.append("        return ").append(repoVar).append(".findById(id).orElse(null);\n");
            sb.append("    }\n\n");
        }
        
        // deleteById
        sb.append("    public void deleteById(").append(idType).append(" id) {\n");
        sb.append("        ").append(repoVar).append(".deleteById(id);\n");
        sb.append("    }\n\n");
        
        // Mappers
        if (data.isGenerateDto()) {
            sb.append("    private ").append(entityName).append("Dto mapToDto(").append(entityName).append(" entity) {\n");
            sb.append("        return ").append(entityName).append("Dto.builder()\n");
            for (FieldData field : data.getFields()) {
                String fieldName = field.getName();
                String getter = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1) + "()";
                sb.append("                .").append(fieldName).append("(entity.").append(getter).append(")\n");
            }
            sb.append("                .build();\n");
            sb.append("    }\n\n");
            
            sb.append("    private ").append(entityName).append(" mapToEntity(").append(entityName).append("Dto dto) {\n");
            sb.append("        return ").append(entityName).append(".builder()\n");
            for (FieldData field : data.getFields()) {
                String fieldName = field.getName();
                String getter = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1) + "()";
                sb.append("                .").append(fieldName).append("(dto.").append(getter).append(")\n");
            }
            sb.append("                .build();\n");
            sb.append("    }\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String renderControllerTemplate(String packageName) {
        String entityName = data.getEntityName();
        String serviceName = entityName + "Service";
        String serviceVar = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1) + "Service";
        
        // Pluralization logic
        String basePath;
        if (entityName.endsWith("y")) {
            basePath = "/" + Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1, entityName.length() - 1) + "ies";
        } else {
            basePath = "/" + Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1) + "s";
        }
        
        String idType = "Long";
        for (FieldData field : data.getFields()) {
            if (field.getName().equals("id")) {
                idType = field.getType();
                break;
            }
        }
        
        String returnType = data.isGenerateDto() ? entityName + "Dto" : entityName;

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        
        // Import Service
        String servicePackage = data.getPackageName().replace(".entity", ".service").replace(".model", ".service");
        if (servicePackage.equals(data.getPackageName())) servicePackage += ".service";
        sb.append("import ").append(servicePackage).append(".").append(serviceName).append(";\n");
        
        // Import DTO or Entity
        if (data.isGenerateDto()) {
            String dtoPackage = data.getPackageName().replace(".entity", ".dto").replace(".model", ".dto");
            if (dtoPackage.equals(data.getPackageName())) dtoPackage += ".dto";
            sb.append("import ").append(dtoPackage).append(".").append(entityName).append("Dto;\n");
        } else {
            sb.append("import ").append(data.getPackageName()).append(".").append(entityName).append(";\n");
        }
        
        sb.append("import org.springframework.web.bind.annotation.*;\n");
        sb.append("import org.springframework.http.ResponseEntity;\n");
        sb.append("import lombok.RequiredArgsConstructor;\n");
        sb.append("import java.util.List;\n\n");
        
        sb.append("@RestController\n");
        sb.append("@RequestMapping(\"").append(basePath).append("\")\n");
        sb.append("@RequiredArgsConstructor\n");
        sb.append("public class ").append(entityName).append("Controller {\n\n");
        
        sb.append("    private final ").append(serviceName).append(" ").append(serviceVar).append(";\n\n");
        
        // getAll
        sb.append("    @GetMapping\n");
        sb.append("    public ResponseEntity<List<").append(returnType).append(">> getAll() {\n");
        sb.append("        return ResponseEntity.ok(").append(serviceVar).append(".findAll());\n");
        sb.append("    }\n\n");
        
        // create
        sb.append("    @PostMapping\n");
        sb.append("    public ResponseEntity<").append(returnType).append("> create(@RequestBody ").append(returnType).append(" dto) {\n");
        sb.append("        return ResponseEntity.ok(").append(serviceVar).append(".save(dto));\n");
        sb.append("    }\n\n");
        
        // getById
        sb.append("    @GetMapping(\"/{id}\")\n");
        sb.append("    public ResponseEntity<").append(returnType).append("> getById(@PathVariable ").append(idType).append(" id) {\n");
        sb.append("        return ResponseEntity.ok(").append(serviceVar).append(".findById(id));\n");
        sb.append("    }\n\n");
        
        // delete
        sb.append("    @DeleteMapping(\"/{id}\")\n");
        sb.append("    public ResponseEntity<Void> delete(@PathVariable ").append(idType).append(" id) {\n");
        sb.append("        ").append(serviceVar).append(".deleteById(id);\n");
        sb.append("        return ResponseEntity.noContent().build();\n");
        sb.append("    }\n");
        
        sb.append("}\n");
        return sb.toString();
    }
    
    private String renderDtoTemplate(String packageName) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        
        if (data.isLombokData()) sb.append("import lombok.Data;\n");
        if (data.isLombokBuilder()) sb.append("import lombok.Builder;\n");
        sb.append("\n");

        if (data.isLombokData()) sb.append("@Data\n");
        if (data.isLombokBuilder()) sb.append("@Builder\n");
        
        sb.append("public class ").append(data.getEntityName()).append("Dto {\n\n");
        
        for (FieldData field : data.getFields()) {
            sb.append("    private ")
                    .append(field.getType())
                    .append(" ")
                    .append(field.getName())
                    .append(";\n");
        }

        sb.append("}\n");
        return sb.toString();
    }
}