package me.dhiren9939.backend;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The dependency rule of the Ingestion module: infrastructure and presentation -> application -> domain. The core
 * (domain and application) speaks Ingestion's language only: translating Access's published events into it is the
 * stream adapter's job, so the core never depends on the {@code common} contracts or on Access.
 */
class IngestionArchitectureTest {

    private static final String ROOT = "me.dhiren9939.backend";
    private static final String DOMAIN = ROOT + ".ingestion.domain..";
    private static final String APPLICATION = ROOT + ".ingestion.application..";
    private static final String INFRASTRUCTURE = ROOT + ".ingestion.infrastructure..";
    private static final String PRESENTATION = ROOT + ".ingestion.presentation..";

    private static JavaClasses mainClasses;

    @BeforeAll
    static void importProductionClasses() {
        mainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    void theDomainDependsOnNoOtherLayer() {
        ArchRule rule = classes().that().resideInAPackage(DOMAIN)
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        DOMAIN, "java..", "jakarta.persistence..", "lombok..", "org.jspecify..")
                .because("the domain may use JPA annotations and lombok, nothing else");
        rule.check(mainClasses);
    }

    @Test
    void theApplicationNeverDependsOnInfrastructureOrPresentation() {
        noClasses().that().resideInAPackage(APPLICATION)
                .should().dependOnClassesThat().resideInAnyPackage(INFRASTRUCTURE, PRESENTATION)
                .check(mainClasses);
    }

    @Test
    void theCoreNeverDependsOnThePublishedContracts() {
        noClasses().that().resideInAnyPackage(DOMAIN, APPLICATION)
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".common..")
                .because("the stream adapter translates Access's events into Ingestion's snapshot")
                .check(mainClasses);
    }

    @Test
    void useCaseHandlersAreHiddenFromEveryoneButSpring() {
        classes().that().resideInAPackage(ROOT + ".ingestion.application.usecase..")
                .and().haveSimpleNameEndingWith("Handler")
                .should().notBePublic()
                .check(mainClasses);
    }
}
