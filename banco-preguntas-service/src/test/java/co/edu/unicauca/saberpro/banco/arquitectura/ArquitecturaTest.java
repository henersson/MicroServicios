package co.edu.unicauca.saberpro.banco.arquitectura;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Tests de arquitectura: convierten las reglas de Clean Architecture en algo que
 * falla el build cuando alguien las rompe.
 *
 * <p>Son tres reglas, que es lo que hace falta para sostener el diseño: que las
 * capas vayan en orden, que el dominio no conozca ningún framework y que la
 * aplicación hable con el exterior solo por sus puertos.
 */
@DisplayName("Arquitectura — Clean Architecture")
class ArquitecturaTest {

    private static final String BASE = "co.edu.unicauca.saberpro.banco";

    private static final String DOMINIO = BASE + ".dominio..";
    private static final String APLICACION = BASE + ".aplicacion..";
    private static final String INFRAESTRUCTURA = BASE + ".infraestructura..";
    private static final String INTERFACES = BASE + ".interfaces..";

    private static JavaClasses clases;

    @BeforeAll
    static void importarClases() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                // El código gRPC generado a partir del .proto vive en el paquete
                // .grpc.v1 y no lo escribimos nosotros: no tiene sentido pedirle
                // que cumpla reglas de capas.
                .withImportOption(location -> !location.contains("/grpc/v1/"))
                .importPackages(BASE);
    }

    @Test
    @DisplayName("las dependencias entre capas apuntan hacia adentro")
    void capasEnOrden() {
        Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Dominio").definedBy(DOMINIO)
                .layer("Aplicacion").definedBy(APLICACION)
                .layer("Infraestructura").definedBy(INFRAESTRUCTURA)
                .layer("Interfaces").definedBy(INTERFACES)

                // El dominio es el centro: lo usan todas, no usa a ninguna.
                .whereLayer("Interfaces").mayNotBeAccessedByAnyLayer()
                .whereLayer("Infraestructura").mayOnlyBeAccessedByLayers("Interfaces")
                .whereLayer("Aplicacion")
                .mayOnlyBeAccessedByLayers("Interfaces", "Infraestructura")

                .because("en Clean Architecture la dependencia siempre apunta al dominio")
                .check(clases);
    }

    @Test
    @DisplayName("el dominio no conoce ningún framework")
    void dominioSinFrameworks() {
        noClasses()
                .that().resideInAPackage(DOMINIO)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.springframework.amqp..",
                        "io.grpc..",
                        "com.fasterxml.jackson..")
                .because("el dominio tiene que poder compilarse y probarse solo, y no sabe "
                        + "cómo se guarda una pregunta ni por qué cable viajan sus eventos")
                .check(clases);
    }

    @Test
    @DisplayName("la aplicación no depende de la infraestructura ni de las interfaces")
    void aplicacionNoDependeDeFuera() {
        noClasses()
                .that().resideInAPackage(APLICACION)
                .should().dependOnClassesThat()
                .resideInAnyPackage(INFRAESTRUCTURA, INTERFACES)
                .because("los casos de uso hablan con el exterior solo a través de sus puertos")
                .check(clases);
    }
}
