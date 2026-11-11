package me.dhiren9939.backend;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The dependency rule of the Access module, enforced: presentation and infrastructure -> application -> domain.
 * Presentation (web, facade) and infrastructure (persistence, key formats, wiring) never know each other.
 * The domain may use {@code jakarta.persistence} (aggregates are rich JPA entities) and nothing else
 * from a framework.
 */
class ArchitectureTest {

    private static final String ROOT = "me.dhiren9939.backend";
    private static final String DOMAIN = ROOT + ".access.domain..";
    private static final String APPLICATION = ROOT + ".access.application..";
    private static final String PRESENTATION = ROOT + ".access.presentation..";
    private static final String INFRASTRUCTURE = ROOT + ".access.infrastructure..";
    private static final String SHARED = ROOT + ".shared..";

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
                        DOMAIN, SHARED, "java..", "jakarta.persistence..", "lombok..",
                        "com.github.f4b6a3.uuid..", "org.jspecify..")
                .because("the domain may use JPA annotations, lombok, a UUID generator and the published contracts, nothing else");
        rule.check(mainClasses);
    }

    @Test
    void theDomainIsFreeOfSpring() {
        ArchRule rule = classes().that().resideInAPackage(DOMAIN)
                .should().onlyDependOnClassesThat().resideOutsideOfPackage("org.springframework..");
        rule.check(mainClasses);
    }

    @Test
    void sharedContractsAreFreeOfFrameworksAndOfAccessInternals() {
        ArchRule rule = classes().that().resideInAPackage(SHARED)
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        SHARED, "java..", "lombok..")
                .because("published contracts must not leak JPA, Spring or JSON libraries, nor depend on a context's internals");
        rule.check(mainClasses);
    }

    @Test
    void theApplicationNeverDependsOnPresentationOrInfrastructure() {
        ArchRule rule = classes().that().resideInAPackage(APPLICATION)
                .should().onlyDependOnClassesThat().resideOutsideOfPackages(PRESENTATION, INFRASTRUCTURE);
        rule.check(mainClasses);
    }

    @Test
    void theLayersOnlyPointInwards() {
        ArchRule rule = com.tngtech.archunit.library.Architectures.layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Presentation").definedBy(PRESENTATION)
                .layer("Infrastructure").definedBy(INFRASTRUCTURE)
                .layer("Application").definedBy(APPLICATION)
                .layer("Domain").definedBy(DOMAIN)
                .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation", "Infrastructure")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Presentation", "Infrastructure");
        rule.check(mainClasses);
    }

    @Test
    void useCaseInterfacesAreOnlyImplementedByHandlers() {
        ArchRule rule = classes().that().implement(
                        com.tngtech.archunit.base.DescribedPredicate.describe("a use case interface",
                                c -> c.getPackageName().contains("application.usecase") && c.isInterface()))
                .should().resideInAPackage(ROOT + ".access.application.usecase..");
        rule.check(mainClasses);
    }

    @Test
    void presentationTalksToUseCasesNotToRepositoriesCredentialsOrInfrastructure() {
        ArchRule rule = classes().that().resideInAPackage(PRESENTATION)
                .should().onlyDependOnClassesThat().resideOutsideOfPackages(
                        INFRASTRUCTURE, ROOT + ".access.domain.repositories..",
                        ROOT + ".access.application.interfaces..");
        rule.check(mainClasses);
    }

    @Test
    void infrastructureNeverReachesIntoPresentationOrTheUseCaseHandlers() {
        ArchRule rule = classes().that().resideInAPackage(INFRASTRUCTURE)
                .should().onlyDependOnClassesThat().resideOutsideOfPackages(
                        PRESENTATION, ROOT + ".access.application.usecase..");
        rule.check(mainClasses);
    }

    @Test
    void publishedContractsNeverDependOnAContextInternals() {
        ArchRule rule = classes().that().resideInAPackage(SHARED)
                .should().onlyDependOnClassesThat().resideOutsideOfPackage(ROOT + ".access..");
        rule.check(mainClasses);
    }

    @Test
    void noFieldInjection() {
        ArchRule rule = fields().should().notBeAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                .andShould().notBeAnnotatedWith("jakarta.inject.Inject");
        rule.check(mainClasses);
    }

    @Test
    void useCaseHandlersAreHiddenFromEveryoneButSpring() {
        ArchRule rule = classes().that().resideInAPackage(ROOT + ".access.application.usecase..")
                .and().haveSimpleNameEndingWith("Handler")
                .should().notBePublic();
        rule.check(mainClasses);
    }

    @Test
    void packagesInsideTheAccessModuleHaveNoCycles() {
        ArchRule rule = slices().matching(ROOT + ".access.(*)..").should().beFreeOfCycles();
        rule.check(mainClasses);
    }
}
