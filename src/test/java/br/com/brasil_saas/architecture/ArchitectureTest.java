package br.com.brasil_saas.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Primeiro teste ArchUnit do Brasil SaaS ERP.
 *
 * Dependência já no pom.xml:
 *   com.tngtech.archunit:archunit:1.5.1 (scope test)
 *
 * Execute:
 *   mvn test -Dtest=ArchitectureTest
 */
class ArchitectureTest {

    private static final String BASE = "br.com.brasil_saas";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages(BASE);
    }

    // -------------------------------------------------------------------------
    // Convenções de pacote
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Classes *Controller devem residir em pacotes ..controller..")
    void controllers_no_pacote_controller() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Controller")
                .and().areNotAnonymousClasses()
                .should().resideInAPackage("..controller..")
                .because("controllers HTTP ficam em pacotes controller");

        FreezingArchRule.freeze(rule).check(classes);
    }

    @Test
    @DisplayName("Interfaces *Repository devem residir em pacotes ..repository..")
    void repositories_no_pacote_repository() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Repository")
                .and().areInterfaces()
                .should().resideInAPackage("..repository..")
                .because("repositórios JPA ficam em pacotes repository");

        FreezingArchRule.freeze(rule).check(classes);
    }

    // -------------------------------------------------------------------------
    // Independência de camadas
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Model/entity não deve depender de controller")
    void model_nao_depende_de_controller() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..model..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .because("entidades de domínio não conhecem a camada web");

        rule.check(classes);
    }

    @Test
    @DisplayName("Repository não deve depender de controller")
    void repository_nao_depende_de_controller() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..repository..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .because("repositórios não conhecem a camada web");

        rule.check(classes);
    }

    @Test
    @DisplayName("DTO não deve depender de controller")
    void dto_nao_depende_de_controller() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..dto..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .because("DTOs são contratos de dados, não de HTTP");

        rule.check(classes);
    }

    // -------------------------------------------------------------------------
    // Módulo IA
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Controllers do módulo IA ficam em br.com.brasil_saas.ia.controller")
    void controllers_ia_no_pacote_correto() {
        ArchRule rule = classes()
                .that().resideInAPackage(BASE + ".ia..")
                .and().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage(BASE + ".ia.controller..")
                .because("controllers de IA ficam em ia.controller");

        rule.check(classes);
    }

    @Test
    @DisplayName("Assistente ERP (ia.assistente) não deve depender de controllers de outros módulos")
    void assistente_nao_depende_de_controllers_externos() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".ia.assistente..")
                .should().dependOnClassesThat()
                .resideInAPackage("..controller..")
                .because("o copiloto usa services/repositórios, não controllers HTTP");

        rule.check(classes);
    }
}
