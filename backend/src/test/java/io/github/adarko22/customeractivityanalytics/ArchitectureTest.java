package io.github.adarko22.customeractivityanalytics;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

  private static JavaClasses importedClasses;

  @BeforeAll
  static void importClasses() {
    importedClasses =
        new ClassFileImporter().importPackages("io.github.adarko22.customeractivityanalytics");
  }

  @Test
  void packagesShouldBeFreeOfCycles() {
    SlicesRuleDefinition.slices()
        .matching("io.github.adarko22.customeractivityanalytics.(*)..")
        .should()
        .beFreeOfCycles()
        .check(importedClasses);
  }

  @Test
  void controllersShouldNotDependOnRepositories() {
    noClasses()
        .that()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .dependOnClassesThat()
        .haveSimpleNameEndingWith("Repository")
        .check(importedClasses);
  }

  @Test
  void controllersShouldNotDependOnPersistenceApi() {
    noClasses()
        .that()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("jakarta.persistence..")
        .check(importedClasses);
  }
}
