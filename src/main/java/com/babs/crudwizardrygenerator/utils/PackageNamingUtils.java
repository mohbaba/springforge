package com.babs.crudwizardrygenerator.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class PackageNamingUtils {

    public enum Layer {
        REPOSITORY("repository", "repositories"),
        SERVICE("service", "services"),
        CONTROLLER("controller", "controllers"),
        DTO("dto", "dtos"),
        ENTITY("entity", "entities");

        private final String singular;
        private final String plural;

        Layer(String singular, String plural) {
            this.singular = singular;
            this.plural = plural;
        }

        public String singular() {
            return singular;
        }

        public String plural() {
            return plural;
        }
    }

    private PackageNamingUtils() {}

    public static String deriveLayerPackageFromEntity(String entityPackage, Layer targetLayer) {
        if (entityPackage == null || entityPackage.isBlank()) {
            return targetLayer.singular();
        }

        if (endsWithSegment(entityPackage, "entity")) {
            return replaceTerminalSegment(entityPackage, "entity", targetLayer.singular());
        }
        if (endsWithSegment(entityPackage, "entities")) {
            return replaceTerminalSegment(entityPackage, "entities", targetLayer.plural());
        }
        if (endsWithSegment(entityPackage, "model")) {
            return replaceTerminalSegment(entityPackage, "model", targetLayer.singular());
        }
        if (endsWithSegment(entityPackage, "models")) {
            return replaceTerminalSegment(entityPackage, "models", targetLayer.plural());
        }

        return entityPackage + "." + targetLayer.singular();
    }

    public static String deriveEntityPackageFromLayer(String currentPackage) {
        if (currentPackage == null || currentPackage.isBlank()) {
            return currentPackage;
        }

        if (endsWithComposite(currentPackage, "service.impl")) {
            return replaceCompositeSuffix(currentPackage, "service.impl", "entity");
        }
        if (endsWithComposite(currentPackage, "services.impl")) {
            return replaceCompositeSuffix(currentPackage, "services.impl", "entities");
        }
        if (endsWithSegment(currentPackage, "controller")) {
            return replaceTerminalSegment(currentPackage, "controller", "entity");
        }
        if (endsWithSegment(currentPackage, "controllers")) {
            return replaceTerminalSegment(currentPackage, "controllers", "entities");
        }
        if (endsWithSegment(currentPackage, "service")) {
            return replaceTerminalSegment(currentPackage, "service", "entity");
        }
        if (endsWithSegment(currentPackage, "services")) {
            return replaceTerminalSegment(currentPackage, "services", "entities");
        }
        if (endsWithSegment(currentPackage, "repository")) {
            return replaceTerminalSegment(currentPackage, "repository", "entity");
        }
        if (endsWithSegment(currentPackage, "repositories")) {
            return replaceTerminalSegment(currentPackage, "repositories", "entities");
        }
        if (endsWithSegment(currentPackage, "dto")) {
            return replaceTerminalSegment(currentPackage, "dto", "entity");
        }
        if (endsWithSegment(currentPackage, "dtos")) {
            return replaceTerminalSegment(currentPackage, "dtos", "entities");
        }

        return currentPackage;
    }

    public static List<String> candidateControllerPackagesFromService(String servicePackage) {
        LinkedHashSet<String> packages = new LinkedHashSet<>();

        addIfChanged(packages, servicePackage, replaceCompositeSuffix(servicePackage, "service.impl", "controller"));
        addIfChanged(packages, servicePackage, replaceCompositeSuffix(servicePackage, "services.impl", "controllers"));
        addIfChanged(packages, servicePackage, replaceTerminalSegmentIfPresent(servicePackage, "service", "controller"));
        addIfChanged(packages, servicePackage, replaceTerminalSegmentIfPresent(servicePackage, "services", "controllers"));

        String parentPackage = parentPackage(servicePackage);
        if (parentPackage != null && !parentPackage.isBlank()) {
            packages.add(parentPackage + ".controller");
            packages.add(parentPackage + ".controllers");
        }

        if (packages.isEmpty()) {
            packages.add(servicePackage + ".controller");
        }

        return new ArrayList<>(packages);
    }

    private static void addIfChanged(LinkedHashSet<String> packages, String original, String candidate) {
        if (candidate != null && !candidate.equals(original)) {
            packages.add(candidate);
        }
    }

    private static boolean endsWithSegment(String packageName, String segment) {
        return packageName.equals(segment) || packageName.endsWith("." + segment);
    }

    private static boolean endsWithComposite(String packageName, String suffix) {
        return packageName.equals(suffix) || packageName.endsWith("." + suffix);
    }

    private static String replaceTerminalSegment(String packageName, String oldSegment, String newSegment) {
        if (!endsWithSegment(packageName, oldSegment)) {
            return packageName;
        }
        int lastDot = packageName.lastIndexOf('.');
        return lastDot >= 0 ? packageName.substring(0, lastDot + 1) + newSegment : newSegment;
    }

    private static String replaceTerminalSegmentIfPresent(String packageName, String oldSegment, String newSegment) {
        return endsWithSegment(packageName, oldSegment) ? replaceTerminalSegment(packageName, oldSegment, newSegment) : packageName;
    }

    private static String replaceCompositeSuffix(String packageName, String oldSuffix, String newSuffix) {
        if (!endsWithComposite(packageName, oldSuffix)) {
            return packageName;
        }
        String marker = "." + oldSuffix;
        int idx = packageName.lastIndexOf(marker);
        if (idx >= 0) {
            return packageName.substring(0, idx + 1) + newSuffix;
        }
        return newSuffix;
    }

    private static String parentPackage(String packageName) {
        int lastDot = packageName.lastIndexOf('.');
        return lastDot > 0 ? packageName.substring(0, lastDot) : null;
    }
}
