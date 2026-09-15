package workshop.bookshelf;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Supplied sensor: one dependency direction, no architecture lab. */
class ArchitectureTest {
    @Test
    void domainDoesNotDependOnServiceOrStorage() {
        var classes = new ClassFileImporter().importPackages("workshop.bookshelf");
        assertFalse(classes.stream().noneMatch(type -> type.getPackageName().endsWith(".domain")),
                "The rule must inspect real domain classes");
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..service..", "..storage..")
                .because("domain concepts must remain usable without service or storage code")
                .check(classes);
    }
}
