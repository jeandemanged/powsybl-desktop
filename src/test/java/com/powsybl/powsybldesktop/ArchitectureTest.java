/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.powsybldesktop.core.MainModel;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.Slice;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

/**
 * The layering of docs/ai/architecture.md. Rules that the code base doesn't fully follow yet are frozen: their
 * current violations are recorded in src/test/archunit_store (see src/test/resources/archunit.properties), only new
 * ones fail, and fixed ones are removed from the store by the next run.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ArchitectureTest {

    private static final String ROOT = "com.powsybl.powsybldesktop";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    private static final DescribedPredicate<JavaClass> SHELL = classes("the application shell",
            "Launcher", "MainApplication", "MainController", "SplashScreen");

    // the shared state, wherever it lives: core and the sub-models MainModel aggregates
    private static final DescribedPredicate<JavaClass> STATE = packages("", "core", "navigation")
            .or(classes("", "logs.LogsModel",
                    "notification.Notification", "notification.NotificationAction", "notification.NotificationStatus",
                    "notification.NotificationsModel",
                    "parameters.ParametersModel", "parameters.ApplicationParameters", "parameters.GuiParameters",
                    "parameters.DesktopNadParameters", "parameters.DesktopSldParameters", "parameters.ApplicationParametersJson",
                    "parameters.ParametersConfigFile", "parameters.Basemap"))
            .as("the shared state");

    // computations and their results, no UI
    private static final DescribedPredicate<JavaClass> DOMAIN = packages("the domain", "violations", "loadflow", "security", "search");

    private static final DescribedPredicate<JavaClass> BASE = packages("the base utilities", "utils", "util", "reports");

    private static final DescribedPredicate<JavaClass> VIEWS = DescribedPredicate.describe("the views",
            c -> c.getPackageName().startsWith(ROOT) && !SHELL.test(c) && !STATE.test(c) && !DOMAIN.test(c) && !BASE.test(c));

    private static final SliceAssignment VIEW_PACKAGES = new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
            String viewPackage = viewPackageOf(javaClass);
            return viewPackage == null ? SliceIdentifier.ignore() : SliceIdentifier.of(viewPackage);
        }

        @Override
        public String getDescription() {
            return "view, domain and base packages";
        }
    };

    @Test
    void layersOnlyDependOnLowerLayers() {
        ArchRule rule = layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Shell").definedBy(SHELL)
                .layer("Views").definedBy(VIEWS)
                .layer("State").definedBy(STATE)
                .layer("Domain").definedBy(DOMAIN)
                .layer("Base").definedBy(BASE)
                .whereLayer("Shell").mayNotBeAccessedByAnyLayer()
                .whereLayer("Views").mayOnlyBeAccessedByLayers("Shell")
                .whereLayer("State").mayOnlyBeAccessedByLayers("Shell", "Views")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Shell", "Views", "State");
        freeze(rule).check(CLASSES);
    }

    @Test
    void domainDoesNotUseJavaFxUi() {
        noClasses().that(DOMAIN).should().dependOnClassesThat().resideInAnyPackage("javafx.scene..", "javafx.stage..")
                .check(CLASSES);
    }

    // The shell and the shared state are left out: the layers rule already orders them against the views.
    // Not beFreeOfCycles(): its violation lists every dependency of the cycle, so that once frozen any new call along
    // an already cyclic package edge would fail. One violation per package edge instead: only a new one fails.
    @Test
    void viewPackagesAreFreeOfCycles() {
        ArchRule rule = slices().assignedFrom(VIEW_PACKAGES).should(new ArchCondition<>("be free of cycles") {
            private final Map<String, Set<String>> edges = new HashMap<>();

            @Override
            public void init(Collection<Slice> allSlices) {
                for (Slice slice : allSlices) {
                    edges.put(packageOf(slice), targetPackages(slice));
                }
            }

            @Override
            public void check(Slice slice, ConditionEvents events) {
                String source = packageOf(slice);
                for (String target : new TreeSet<>(edges.get(source))) {
                    if (reaches(target, source)) {
                        events.add(SimpleConditionEvent.violated(slice, source + " depends on " + target + ", which depends back on it, directly or not"));
                    }
                }
            }

            private boolean reaches(String from, String to) {
                Set<String> visited = new HashSet<>();
                Deque<String> toVisit = new ArrayDeque<>(List.of(from));
                while (!toVisit.isEmpty()) {
                    String current = toVisit.pop();
                    if (current.equals(to)) {
                        return true;
                    }
                    if (visited.add(current)) {
                        toVisit.addAll(edges.getOrDefault(current, Set.of()));
                    }
                }
                return false;
            }
        });
        freeze(rule).check(CLASSES);
    }

    // null for the shell and the shared state
    private static String viewPackageOf(JavaClass javaClass) {
        return VIEWS.test(javaClass) || DOMAIN.test(javaClass) || BASE.test(javaClass)
                ? javaClass.getPackageName().substring(ROOT.length() + 1)
                : null;
    }

    private static String packageOf(Slice slice) {
        return viewPackageOf(slice.iterator().next());
    }

    private static Set<String> targetPackages(Slice slice) {
        String source = packageOf(slice);
        return slice.getDependenciesFromSelf().stream()
                .map(dependency -> viewPackageOf(dependency.getTargetClass()))
                .filter(target -> target != null && !target.equals(source))
                .collect(Collectors.toSet());
    }

    // views navigate through SceneModel, so that one shown in a separate window drives the main window without
    // recording its own state
    @Test
    void onlyTheShellAndSceneModelUseTheNavigationHistoryDirectly() {
        ArchRule rule = noClasses().that(DescribedPredicate.not(classes("the shell or SceneModel", "MainController", "window.SceneModel")))
                .should().callMethod(MainModel.class, "getNavigationHistory");
        freeze(rule).check(CLASSES);
    }

    // every window showing a view goes through SeparateWindows, which owns their lifecycle
    @Test
    void onlySeparateWindowsAndTheShellCreateStages() {
        noClasses().that(DescribedPredicate.not(SHELL.or(classes("", "window.SeparateWindows")).as("the shell or SeparateWindows")))
                .should().callConstructorWhere(target(owner(assignableTo(Stage.class))))
                .check(CLASSES);
    }

    private static DescribedPredicate<JavaClass> packages(String description, String... names) {
        Set<String> packageNames = Set.of(names);
        // the trailing dot matches the package itself and its subpackages, not a package merely prefixed by its name
        return DescribedPredicate.describe(description, c -> packageNames.stream()
                .anyMatch(p -> (c.getPackageName() + ".").startsWith(ROOT + "." + p + ".")));
    }

    // names relative to the root package, nested and anonymous classes included
    private static DescribedPredicate<JavaClass> classes(String description, String... names) {
        Set<String> classNames = Set.of(names);
        return DescribedPredicate.describe(description, c -> {
            String name = c.getName();
            int nested = name.indexOf('$');
            String topLevel = nested < 0 ? name : name.substring(0, nested);
            return topLevel.startsWith(ROOT + ".") && classNames.contains(topLevel.substring(ROOT.length() + 1));
        });
    }
}
