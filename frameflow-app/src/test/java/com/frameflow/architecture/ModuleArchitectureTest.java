package com.frameflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/** Executable module-boundary rules; violations fail the normal Maven test phase. */
class ModuleArchitectureTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.frameflow");

    static final ArchRule APPLICATION_MUST_USE_PORTS = noClasses()
            .that().resideInAPackage("com.frameflow.identity.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.frameflow.identity.infrastructure..",
                    "com.frameflow.identity.web..")
            .because("application code must depend on ports and application models, not adapters or HTTP DTOs");

    static final ArchRule IDENTITY_PRIVATE_PACKAGES_STAY_INSIDE_THE_MODULE = noClasses()
            .that().resideOutsideOfPackage("com.frameflow.identity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.frameflow.identity.application..",
                    "com.frameflow.identity.config..",
                    "com.frameflow.identity.domain..",
                    "com.frameflow.identity.error..",
                    "com.frameflow.identity.infrastructure..",
                    "com.frameflow.identity.security..",
                    "com.frameflow.identity.web..")
            .because("other modules may only consume an explicit identity api package, never identity internals");

    static final ArchRule SHARED_KERNEL_MUST_NOT_DEPEND_ON_FEATURE_MODULES = noClasses()
            .that().resideInAPackage("com.frameflow.shared..")
            .should().dependOnClassesThat().resideInAnyPackage("com.frameflow.identity..", "com.frameflow.probe..")
            .because("the shared kernel must remain dependency-inward and feature-neutral");

    @Test
    void applicationUsesPortsInsteadOfInfrastructureOrWebDtos() {
        APPLICATION_MUST_USE_PORTS.check(PRODUCTION_CLASSES);
    }

    @Test
    void modulePrivatePackagesDoNotLeakAcrossBoundaries() {
        IDENTITY_PRIVATE_PACKAGES_STAY_INSIDE_THE_MODULE.check(PRODUCTION_CLASSES);
        SHARED_KERNEL_MUST_NOT_DEPEND_ON_FEATURE_MODULES.check(PRODUCTION_CLASSES);
    }
}
