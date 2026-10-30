/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.MinMaxReactiveLimits;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ReactiveCapabilityCurve;
import com.powsybl.iidm.network.ReactiveLimitsKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ActivePowerControl;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.window.SeparateWindows;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class GeneratorDetailControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();
    private final Generator generator = network.getGenerator("B1-G");

    private GeneratorDetailController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) {
        mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.setNetwork(network);
        SeparateWindows.View view = EquipmentDetails.load(generator, SceneModel.main(mainModel));
        controller = (GeneratorDetailController) view.controller();

        stage.setScene(new Scene(view.root(), 1000, 700));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    /**
     * The editor in the row of {@code labelKey}, the first one in case of duplicates.
     */
    private static <T extends Node> T field(Node form, String labelKey, Class<T> type) {
        String label = Messages.get(labelKey);
        return grids(form)
                .flatMap(grid -> grid.getChildren().stream()
                        .filter(node -> node instanceof Label l && label.equals(l.getText()) && GridPane.getColumnIndex(node) == 0)
                        .map(node -> grid.getChildren().stream()
                                .filter(n -> GridPane.getColumnIndex(n) == 1 && GridPane.getRowIndex(n).equals(GridPane.getRowIndex(node)))
                                .findFirst().orElseThrow()))
                .map(type::cast)
                .findFirst().orElseThrow(() -> new AssertionError("No row " + labelKey));
    }

    private static Stream<GridPane> grids(Node node) {
        if (node instanceof GridPane grid) {
            return Stream.of(grid);
        }
        if (node instanceof Pane pane) {
            return pane.getChildren().stream().flatMap(GeneratorDetailControllerTest::grids);
        }
        if (node instanceof javafx.scene.control.TitledPane titledPane) {
            return grids(titledPane.getContent());
        }
        return Stream.empty();
    }

    private void commit(Node form, String labelKey, String text) {
        TextField field = field(form, labelKey, TextField.class);
        interact(() -> {
            field.setText(text);
            field.getOnAction().handle(null);
        });
    }

    @Test
    void showsGenerator() {
        assertTrue(controller.titleLabel.getText().contains(generator.getNameOrId()));
        assertEquals(generator.getTerminal().getVoltageLevel().getNameOrId(), ((Hyperlink) controller.linksBox.getChildren().get(1)).getText());
        Node form = controller.form.getNode();
        assertEquals(generator.getId(), field(form, "desktop.common.column.id", TextField.class).getText());
        assertEquals(generator.getEnergySource(), field(form, "detail.generator.energySource", ChoiceBox.class).getValue());
        assertEquals(4, controller.tabPane.getTabs().size());
        assertEquals(generator, controller.getEquipment());
    }

    @Test
    void editsAndRefreshes() {
        Instant before = mainModel.updateProperty().get();
        commit(controller.form.getNode(), "generators.column.targetP", "123.5");
        assertEquals(123.5, generator.getTargetP());
        assertNotEquals(before, mainModel.updateProperty().get());

        interact(() -> {
            generator.setMaxP(321);
            mainModel.setUpdate();
        });
        assertEquals("321.0", field(controller.form.getNode(), "desktop.common.column.maxP", TextField.class).getText());
    }

    @Test
    void editsId() {
        commit(controller.form.getNode(), "desktop.common.column.id", "B1-G2");
        assertEquals("B1-G2", generator.getId());
        assertEquals(generator, network.getGenerator("B1-G2"));
        // IIDM refuses an existing id
        commit(controller.form.getNode(), "desktop.common.column.id", "B2-G");
        assertEquals("B1-G2", generator.getId());
    }

    @Test
    void copiesIdAndName() {
        List<Node> copyButtons = controller.form.getNode().lookupAll(".icon-button").stream().limit(2).toList();
        interact(() -> ((Button) copyButtons.getFirst()).fire());
        assertEquals(generator.getId(), clipboardString());
        // no name
        assertFalse(copyButtons.getLast().isVisible());
        interact(() -> {
            generator.setName("Generator 1");
            mainModel.setUpdate();
        });
        assertTrue(copyButtons.getLast().isVisible());
        interact(() -> ((Button) copyButtons.getLast()).fire());
        assertEquals("Generator 1", clipboardString());
    }

    @Test
    void connectsFromSymbol() {
        Node symbol = controller.titleLabel.getScene().getRoot().lookup(".equipment-symbol");
        assertTrue(symbol.lookupAll(".symbol-value").stream()
                .anyMatch(label -> ((Label) label).getText().startsWith(Messages.get("desktop.common.column.p") + " = ")));
        CheckBox connected = (CheckBox) symbol.lookup(".check-box");
        interact(connected::fire);
        assertFalse(generator.getTerminal().isConnected());
        assertFalse(connected.isSelected());
        interact(connected::fire);
        assertTrue(generator.getTerminal().isConnected());
    }

    @Test
    void movesToAnotherBus() {
        VoltageLevel voltageLevel = generator.getTerminal().getVoltageLevel();
        interact(() -> {
            voltageLevel.getBusBreakerView().newBus().setId("B1-BIS").add();
            mainModel.setUpdate();
        });
        @SuppressWarnings("unchecked")
        ChoiceBox<String> bus = field(controller.form.getNode(), "detail.bus.busBreakerView", ChoiceBox.class);
        assertTrue(bus.getItems().contains("B1-BIS"));
        interact(() -> bus.setValue("B1-BIS"));
        assertEquals("B1-BIS", generator.getTerminal().getBusBreakerView().getConnectableBus().getId());
        assertTrue(generator.getTerminal().isConnected());
    }

    @Test
    void rejectsInvalidEdit() {
        double minP = generator.getMinP();
        commit(controller.form.getNode(), "desktop.common.column.minP", "abc");
        assertEquals(minP, generator.getMinP());
        // IIDM refuses a NaN max P
        double maxP = generator.getMaxP();
        commit(controller.form.getNode(), "desktop.common.column.maxP", "");
        assertEquals(maxP, generator.getMaxP());
    }

    @Test
    void editsVoltageRegulationBare() {
        @SuppressWarnings("unchecked")
        ChoiceBox<RegulationMode> mode = field(controller.form.getNode(), "desktop.common.column.regulationMode", ChoiceBox.class);
        // IIDM refuses a local reactive power regulation for a generator
        interact(() -> mode.setValue(RegulationMode.REACTIVE_POWER));
        assertEquals(RegulationMode.VOLTAGE, generator.getVoltageRegulation().getMode());
        assertEquals(RegulationMode.VOLTAGE, mode.getValue());

        commit(controller.form.getNode(), "detail.voltageRegulation.slope", "0.25");
        assertEquals(0.25, generator.getVoltageRegulation().getSlope());

        commit(controller.form.getNode(), "desktop.common.column.targetDeadband", "0.5");
        assertEquals(0.5, generator.getVoltageRegulation().getTargetDeadband());

        // a local regulation's target is the local target V
        commit(controller.form.getNode(), "detail.voltageRegulation.targetValue", "140");
        assertTrue(Double.isNaN(generator.getVoltageRegulation().getTargetValue()));

        interact(() -> {
            generator.getVoltageRegulation().setTerminal(network.getLoad("B2-L").getTerminal(), 140);
            mainModel.setUpdate();
        });
        commit(controller.form.getNode(), "detail.voltageRegulation.targetValue", "141");
        assertEquals(141, generator.getVoltageRegulation().getTargetValue());
        interact(() -> mode.setValue(RegulationMode.REACTIVE_POWER));
        assertEquals(RegulationMode.REACTIVE_POWER, generator.getVoltageRegulation().getMode());
    }

    @Test
    void setsRegulatingTerminal() {
        var loadTerminal = network.getLoad("B2-L").getTerminal();
        interact(() -> controller.form.apply(controller.titleLabel, () -> DetailSections.setRegulatingTerminal(generator, RegulationMode.VOLTAGE, loadTerminal)));
        assertEquals(loadTerminal, generator.getVoltageRegulation().getTerminal());
        Node link = field(controller.form.getNode(), "detail.voltageRegulation.terminal", javafx.scene.layout.HBox.class).getChildren().get(1);
        assertTrue(link.isVisible());

        interact(() -> generator.getVoltageRegulation().setMode(RegulationMode.REACTIVE_POWER));
        interact(() -> controller.form.apply(controller.titleLabel, () -> DetailSections.setRegulatingTerminal(generator, RegulationMode.VOLTAGE, null)));
        assertNull(generator.getVoltageRegulation().getTerminal());
        assertEquals(RegulationMode.VOLTAGE, generator.getVoltageRegulation().getMode());
        assertFalse(link.isVisible());
    }

    @Test
    void stagesReactiveLimitsUntilApplied() {
        ReactiveLimitsPane pane = reactiveLimitsPane();
        @SuppressWarnings("unchecked")
        ChoiceBox<ReactiveLimitsKind> kind = (ChoiceBox<ReactiveLimitsKind>) pane.lookupAll(".choice-box").iterator().next();
        interact(() -> kind.setValue(ReactiveLimitsKind.CURVE));
        // staged only
        assertInstanceOf(MinMaxReactiveLimits.class, generator.getReactiveLimits());

        Button apply = button(pane, "detail.action.apply");
        assertFalse(apply.isDisabled());
        interact(apply::fire);
        ReactiveCapabilityCurve curve = assertInstanceOf(ReactiveCapabilityCurve.class, generator.getReactiveLimits());
        assertEquals(2, curve.getPointCount());
        assertEquals(generator.getMinP(), curve.getMinP());
        assertEquals(generator.getMaxP(), curve.getMaxP());
        assertTrue(apply.isDisabled());
    }

    @Test
    void addsAndRemovesExtension() {
        MenuButton add = (MenuButton) controller.extensionsPane.getChildren().getFirst();
        String label = Messages.get("detail.extension.activePowerControl");
        interact(() -> add.getItems().stream().filter(item -> item.getText().equals(label)).findFirst().orElseThrow().fire());
        assertNotNull(generator.getExtension(ActivePowerControl.class));

        commit(controller.extensionsPane, "detail.extension.activePowerControl.droop", "4");
        assertEquals(4, generator.<ActivePowerControl<Generator>>getExtension(ActivePowerControl.class).getDroop());

        interact(() -> button(controller.extensionsPane, "desktop.common.action.remove").fire());
        assertNull(generator.getExtension(ActivePowerControl.class));
    }

    @Test
    void everyExtensionEditorCreatesAndShows() {
        List<ExtensionsPane.Editor<Generator>> editors = ExtensionEditors.forGenerator();
        interact(() -> editors.stream()
                .filter(editor -> editor.creator() != null)
                .forEach(editor -> editor.creator().accept(generator)));
        interact(() -> mainModel.setUpdate());
        long created = editors.stream().filter(editor -> editor.creator() != null).count();
        assertEquals(created, generator.getExtensions().size());
        assertEquals(created, controller.extensionsPane.lookupAll(".titled-pane").size());
    }

    @Test
    void editsPropertiesAndAliases() {
        interact(() -> {
            generator.setProperty("owner", "me");
            generator.addAlias("alias1", "type1");
            mainModel.setUpdate();
        });
        assertEquals(List.of("owner"), table(controller.propertiesPane).getItems());
        assertEquals(List.of("alias1"), table(controller.aliasesPane).getItems());

        List<TextField> fields = controller.aliasesPane.lookupAll(".text-field").stream().map(TextField.class::cast).toList();
        interact(() -> {
            fields.getFirst().setText("alias2");
            button(controller.aliasesPane, "desktop.common.action.add").fire();
        });
        assertTrue(generator.getAliases().contains("alias2"));
        assertEquals(Optional.empty(), generator.getAliasType("alias2"));
    }

    @Test
    void disabledWhileBusy() {
        TextField targetP = field(controller.form.getNode(), "generators.column.targetP", TextField.class);
        CheckBox fictitious = field(controller.form.getNode(), "detail.fictitious", CheckBox.class);
        Runnable[] release = new Runnable[1];
        interact(() -> release[0] = mainModel.markBusy(network));
        assertTrue(targetP.isDisabled());
        assertTrue(fictitious.isDisabled());
        assertTrue(button(reactiveLimitsPane(), "detail.action.apply").isDisabled());
        interact(release[0]);
        assertFalse(targetP.isDisabled());
    }

    private ReactiveLimitsPane reactiveLimitsPane() {
        return controller.tabPane.getTabs().stream()
                .map(Tab::getContent)
                .filter(ReactiveLimitsPane.class::isInstance)
                .map(ReactiveLimitsPane.class::cast)
                .findFirst().orElseThrow();
    }

    private static Button button(Node root, String textKey) {
        String text = Messages.get(textKey);
        return root.lookupAll(".button").stream()
                .filter(node -> node instanceof Button b && text.equals(b.getText()))
                .map(Button.class::cast)
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static javafx.scene.control.TableView<String> table(Node root) {
        return (javafx.scene.control.TableView<String>) root.lookup(".table-view");
    }
}
