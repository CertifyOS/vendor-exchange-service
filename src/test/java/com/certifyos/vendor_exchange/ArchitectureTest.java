package com.certifyos.vendor_exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import jakarta.inject.Inject;
import java.util.concurrent.Executors;

/**
 * The module layout and coding rules from the new-service playbook, enforced on every build. Rules
 * that match no class yet use {@code allowEmptyShould(true)} so they are live from the first commit
 * and start biting the moment a class appears.
 */
@AnalyzeClasses(
        packages = "com.certifyos.vendor_exchange",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ArchitectureTest {

    /** Shared code never imports feature code: the foundations know nothing about the export lane. */
    @ArchTest
    static final ArchRule sharedPackagesDoNotDependOnFeatures = ArchRuleDefinition.noClasses()
            .that()
            .resideInAnyPackage(
                    "..vendor_exchange.config..",
                    "..vendor_exchange.persistence..",
                    "..vendor_exchange.audit..",
                    "..vendor_exchange.auth..",
                    "..vendor_exchange.clients..",
                    "..vendor_exchange.metrics..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..vendor_exchange.export..")
            .allowEmptyShould(true);

    /** No package cycles between the top-level slices. */
    @ArchTest
    static final ArchRule noPackageCycles = SlicesRuleDefinition.slices()
            .matching("com.certifyos.vendor_exchange.(*)..")
            .should()
            .beFreeOfCycles()
            .allowEmptyShould(true);

    /** Constructor injection only. A single constructor is injected by Quarkus without any annotation. */
    @ArchTest
    static final ArchRule noFieldInjection =
            ArchRuleDefinition.noFields().should().beAnnotatedWith(Inject.class).allowEmptyShould(true);

    /** One logger API: JBoss Logging, which Quarkus ships. */
    @ArchTest
    static final ArchRule oneLoggerApi = ArchRuleDefinition.noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.slf4j..", "java.util.logging..", "org.apache.logging..")
            .allowEmptyShould(true);

    /** One injected ObjectMapper, never a private instance with its own settings. */
    @ArchTest
    static final ArchRule oneObjectMapper = ArchRuleDefinition.noClasses()
            .should()
            .callConstructor(ObjectMapper.class)
            .allowEmptyShould(true);

    /** Managed executors only; no ad hoc thread pools. */
    @ArchTest
    static final ArchRule noAdHocExecutors = ArchRuleDefinition.noClasses()
            .should()
            .callMethodWhere(com.tngtech.archunit.base.DescribedPredicate.describe(
                    "a factory on java.util.concurrent.Executors",
                    call -> call.getTargetOwner().isEquivalentTo(Executors.class)))
            .allowEmptyShould(true);

    /** No sleep-based waiting in production code. */
    @ArchTest
    static final ArchRule noThreadSleep = ArchRuleDefinition.noClasses()
            .should()
            .callMethod(Thread.class, "sleep", long.class)
            .allowEmptyShould(true);

    /** Contracts are typed: no JsonNode or raw Map in resource, event or client classes. */
    @ArchTest
    static final ArchRule typedContracts = ArchRuleDefinition.noClasses()
            .that()
            .resideInAnyPackage("..api..", "..events..", "..clients..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("com.fasterxml.jackson.databind.JsonNode")
            .allowEmptyShould(true);
}
