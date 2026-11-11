/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.powsybldesktop.core.MainModel;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The layering of docs/ai/architecture.md, by package only: a new package must be added to a layer here.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ArchitectureTest {

    private static final String ROOT = "com.powsybl.powsybldesktop";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    // for the package rules only: tests legitimately do what production code may not, e.g. use the navigation history
    private static final JavaClasses CLASSES_AND_TESTS = new ClassFileImporter().importPackages(ROOT);

    private static final String[] STATE = packages("core..", "navigation..", "logs..", "study..");
    private static final String[] DOMAIN = packages("network..", "search..", "diagram..", "map..", "contingency..", "limitscaling..", "loadflow..",
            "security..", "sensitivity..", "violations..", "memory..", "parameters..", "notification..");
    private static final String[] BASE = packages("utils..", "util..", "reports..");

    @Test
    void layersOnlyDependOnLowerLayers() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .ensureAllClassesAreContainedInArchitecture()
                .layer("Shell").definedBy(ROOT)
                .layer("UI").definedBy(ROOT + ".ui..")
                .layer("State").definedBy(STATE)
                .layer("Domain").definedBy(DOMAIN)
                .layer("Base").definedBy(BASE)
                .whereLayer("Shell").mayNotBeAccessedByAnyLayer()
                .whereLayer("UI").mayOnlyBeAccessedByLayers("Shell")
                .whereLayer("State").mayOnlyBeAccessedByLayers("Shell", "UI")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Shell", "UI", "State")
                .check(CLASSES_AND_TESTS);
    }

    // what keeps the code outside ui unit-testable without a JavaFX toolkit
    @Test
    void onlyTheShellAndUiUseJavaFxUi() {
        noClasses().that().resideOutsideOfPackages(ROOT, ROOT + ".ui..")
                .should().dependOnClassesThat().resideInAnyPackage("javafx.scene..", "javafx.stage..", "javafx.fxml..",
                        "org.controlsfx..", "org.kordamp.ikonli..")
                .check(CLASSES_AND_TESTS);
    }

    @Test
    void packagesAreFreeOfCycles() {
        slices().matching(ROOT + ".(**)").should().beFreeOfCycles().check(CLASSES_AND_TESTS);
    }

    // views navigate through SceneModel, so that one shown in a separate window drives the main window without
    // recording its own state
    @Test
    void onlyTheShellAndSceneModelUseTheNavigationHistoryDirectly() {
        noClasses().that().doNotHaveFullyQualifiedName(ROOT + ".MainController")
                .and().doNotHaveFullyQualifiedName(ROOT + ".ui.window.SceneModel")
                .should().callMethod(MainModel.class, "getNavigationHistory")
                .check(CLASSES);
    }

    // every window showing a view goes through SeparateWindows, which owns their lifecycle
    @Test
    void onlySeparateWindowsAndTheShellCreateStages() {
        noClasses().that().resideOutsideOfPackage(ROOT)
                .and(nameMatching("^(?!" + ROOT.replace(".", "\\.") + "\\.ui\\.window\\.SeparateWindows\\b).*"))
                .should().callConstructorWhere(target(owner(assignableTo(Stage.class))))
                .check(CLASSES);
    }

    private static String[] packages(String... relative) {
        return Arrays.stream(relative).map(p -> ROOT + "." + p).toArray(String[]::new);
    }
}
