/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.cgmes.extensions.CgmesTopologyKind;
import com.powsybl.cgmes.extensions.CimCharacteristicsAdder;
import com.powsybl.commons.extensions.Extendable;
import com.powsybl.entsoe.util.EntsoeArea;
import com.powsybl.entsoe.util.EntsoeGeographicalCode;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.DiscreteMeasurement;
import com.powsybl.iidm.network.extensions.DiscreteMeasurements;
import com.powsybl.iidm.network.extensions.LinePosition;
import com.powsybl.iidm.network.extensions.LinePositionAdder;
import com.powsybl.iidm.network.extensions.Measurement;
import com.powsybl.iidm.network.extensions.Measurements;
import com.powsybl.iidm.network.extensions.ReferenceTerminals;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControl;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControlAdder;
import com.powsybl.iidm.network.extensions.SubstationPositionAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
@SuppressWarnings("unchecked")
class ExtensionsPaneTest extends AbstractHeadlessApplicationTest {

    @Override
    public void start(Stage stage) {
        // panes are built per test
    }

    private static <O extends Extendable<O>> ExtensionsPane<O> pane(O extendable, List<ExtensionsPane.Editor<O>> editors) {
        List<ExtensionsPane<O>> created = new ArrayList<>();
        // as a detail view does on the network update an edit triggers
        Runnable onEdit = () -> {
            created.getFirst().refresh();
            showSections(created.getFirst());
        };
        ExtensionsPane<O> pane = new ExtensionsPane<>(extendable, editors, GuiParameters::new, onEdit);
        created.add(pane);
        new Scene(pane);
        showSections(pane);
        return pane;
    }

    // the sections' content is only part of the scene graph once their skins exist
    private static void showSections(Node pane) {
        pane.applyCss();
        pane.lookupAll(".titled-pane").forEach(node -> ((TitledPane) node).setExpanded(true));
        pane.applyCss();
    }

    private static void fireAdd(Node pane, int... choices) {
        HBox row = (HBox) value(pane, "detail.extension.measurements.add");
        for (int i = 0; i < choices.length; i++) {
            ((ChoiceBox<?>) row.getChildren().get(i)).getSelectionModel().select(choices[i]);
        }
        ((Button) row.getChildren().getLast()).fire();
    }

    private static void fireRemoveFirstMeasurement(Node pane) {
        ((Button) ((HBox) value(pane, "detail.extension.measurements.measurement")).getChildren().getLast()).fire();
    }

    @Test
    void addsAndRemovesMeasurements() {
        Network network = EurostagTutorialExample1Factory.create();
        Line line = network.getLine("NHV1_NHV2_1");
        Load load = network.getLoad("LOAD");
        interact(() -> {
            ExtensionEditors.<Line>measurements().creator().accept(line);
            ExtensionsPane<Line> linePane = pane(line, ExtensionEditors.forLine());
            fireAdd(linePane, Measurement.Type.ACTIVE_POWER.ordinal(), 1);
            fireAdd(linePane, Measurement.Type.ACTIVE_POWER.ordinal(), 1);

            ExtensionEditors.<Load>measurements().creator().accept(load);
            ExtensionsPane<Load> loadPane = pane(load, ExtensionEditors.forLoad());
            fireAdd(loadPane, Measurement.Type.REACTIVE_POWER.ordinal());
        });
        Measurements<Line> lineMeasurements = line.getExtension(Measurements.class);
        assertEquals(2, lineMeasurements.getMeasurements().size());
        Measurement measurement = lineMeasurements.getMeasurement("NHV1_NHV2_1_ACTIVE_POWER_2");
        assertEquals(ThreeSides.TWO, measurement.getSide());
        assertFalse(measurement.isValid());
        Measurement loadMeasurement = load.<Measurements<Load>>getExtension(Measurements.class).getMeasurements().iterator().next();
        assertEquals(Measurement.Type.REACTIVE_POWER, loadMeasurement.getType());
        assertNull(loadMeasurement.getSide());

        interact(() -> {
            ExtensionsPane<Line> linePane = pane(line, ExtensionEditors.forLine());
            fireRemoveFirstMeasurement(linePane);
            assertEquals(1, linePane.lookupAll(".detail-form").stream()
                    .flatMap(form -> ((GridPane) form).getChildren().stream())
                    .filter(node -> node instanceof Label l && Messages.get("detail.extension.measurements.measurement").equals(l.getText()))
                    .count());
        });
        assertEquals(1, lineMeasurements.getMeasurements().size());
    }

    @Test
    void addsDiscreteMeasurements() {
        Network network = EurostagTutorialExample1Factory.create();
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformer("NHV2_NLOAD");
        Network nodeBreaker = FourSubstationsNodeBreakerFactory.create();
        Switch aSwitch = nodeBreaker.getSwitches().iterator().next();
        interact(() -> {
            ExtensionEditors.<TwoWindingsTransformer>discreteMeasurements().creator().accept(transformer);
            fireAdd(pane(transformer, ExtensionEditors.forTwoWindingsTransformer()), 0);
            ExtensionEditors.<Switch>discreteMeasurements().creator().accept(aSwitch);
            fireAdd(pane(aSwitch, ExtensionEditors.forSwitch()), 0);
        });
        DiscreteMeasurement tapPosition = transformer.<DiscreteMeasurements<TwoWindingsTransformer>>getExtension(DiscreteMeasurements.class)
                .getDiscreteMeasurements().iterator().next();
        assertEquals(DiscreteMeasurement.Type.TAP_POSITION, tapPosition.getType());
        assertEquals(DiscreteMeasurement.TapChanger.RATIO_TAP_CHANGER, tapPosition.getTapChanger());
        assertEquals(DiscreteMeasurement.ValueType.INT, tapPosition.getValueType());
        DiscreteMeasurement switchPosition = aSwitch.<DiscreteMeasurements<Switch>>getExtension(DiscreteMeasurements.class)
                .getDiscreteMeasurements().iterator().next();
        assertEquals(DiscreteMeasurement.Type.SWITCH_POSITION, switchPosition.getType());
        assertEquals(DiscreteMeasurement.ValueType.BOOLEAN, switchPosition.getValueType());
    }

    /**
     * The value node in the row of {@code labelKey}, the first one in case of duplicates.
     */
    private static Node value(Node pane, String labelKey) {
        String label = Messages.get(labelKey);
        return pane.lookupAll(".detail-form").stream()
                .map(GridPane.class::cast)
                .flatMap(grid -> grid.getChildren().stream()
                        .filter(node -> node instanceof Label l && label.equals(l.getText()) && GridPane.getColumnIndex(node) == 0)
                        .map(node -> grid.getChildren().stream()
                                .filter(n -> GridPane.getColumnIndex(n) == 1 && Objects.equals(GridPane.getRowIndex(n), GridPane.getRowIndex(node)))
                                .findFirst().orElseThrow()))
                .findFirst().orElseThrow(() -> new AssertionError("No row " + labelKey));
    }

    private static void assertNoSectionWithoutEditor(Node pane) {
        String notEditable = Messages.get("detail.extensions.notEditable");
        assertTrue(pane.lookupAll(".label").stream().noneMatch(node -> notEditable.equals(((Label) node).getText())));
    }

    private static <O extends Extendable<O>> void assertEveryEditorCreatesAndShows(O extendable, List<ExtensionsPane.Editor<O>> editors) {
        List<ExtensionsPane.Editor<O>> creatable = editors.stream().filter(editor -> editor.creator() != null).toList();
        creatable.forEach(editor -> editor.creator().accept(extendable));
        assertEquals(creatable.size(), extendable.getExtensions().size());
        assertNoSectionWithoutEditor(pane(extendable, editors));
    }

    @Test
    void everyEditorCreatesAndShows() {
        Network network = EurostagTutorialExample1Factory.create();
        Network threeWindings = ThreeWindingsTransformerNetworkFactory.create();
        interact(() -> {
            assertEveryEditorCreatesAndShows(network.getLine("NHV1_NHV2_1"), ExtensionEditors.forLine());
            assertEveryEditorCreatesAndShows(network.getTwoWindingsTransformer("NGEN_NHV1"), ExtensionEditors.forTwoWindingsTransformer());
            assertEveryEditorCreatesAndShows(network.getLoad("LOAD"), ExtensionEditors.forLoad());
            assertEveryEditorCreatesAndShows(network.getSubstation("P1"), ExtensionEditors.forSubstation());
            assertEveryEditorCreatesAndShows(threeWindings.getThreeWindingsTransformer("3WT"), ExtensionEditors.forThreeWindingsTransformer());
        });
        assertEquals(EntsoeGeographicalCode.FR, network.getSubstation("P1").getExtension(EntsoeArea.class).getCode());
    }

    private static Button button(Node pane, String key) {
        String text = Messages.get(key);
        return pane.lookupAll(".button").stream()
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow(() -> new AssertionError("No button " + key));
    }

    // staged in the pane until applied
    @Test
    void editsLinePositionInAWorkingCopy() {
        Network network = EurostagTutorialExample1Factory.create();
        Line line = network.getLine("NHV1_NHV2_1");
        Coordinate p1 = new Coordinate(48, 2);
        Coordinate p2 = new Coordinate(49, 3);
        network.getSubstation("P1").newExtension(SubstationPositionAdder.class).withCoordinate(p1).add();
        network.getSubstation("P2").newExtension(SubstationPositionAdder.class).withCoordinate(p2).add();
        line.newExtension(LinePositionAdder.class).withCoordinates(List.of(p1, new Coordinate(48.8, 2.2), p2)).add();
        interact(() -> {
            Node coordinates = value(pane(line, ExtensionEditors.forLine()), "detail.extension.linePosition.coordinates");
            TableView<?> table = (TableView<?>) coordinates.lookup(".table-view");
            assertEquals(3, table.getItems().size());

            button(coordinates, "detail.extension.linePosition.straight").fire();
            assertEquals(2, table.getItems().size());
            assertEquals(3, line.<LinePosition<Line>>getExtension(LinePosition.class).getCoordinates().size());
            button(coordinates, "detail.action.revert").fire();
            assertEquals(3, table.getItems().size());

            table.getSelectionModel().select(1);
            button(coordinates, "desktop.common.action.remove").fire();
            button(coordinates, "detail.action.apply").fire();
        });
        assertEquals(List.of(p1, p2), line.<LinePosition<Line>>getExtension(LinePosition.class).getCoordinates());
    }

    @Test
    void showsReadOnlyExtensions() {
        Network network = EurostagTutorialExample1Factory.create();
        ReferenceTerminals.addTerminal(network.getGenerator("GEN").getTerminal());
        network.newExtension(CimCharacteristicsAdder.class).setTopologyKind(CgmesTopologyKind.BUS_BRANCH).setCimVersion(16).add();
        interact(() -> {
            ExtensionsPane<Network> networkPane = pane(network, ExtensionEditors.forNetwork());
            assertNoSectionWithoutEditor(networkPane);
            assertEquals(TerminalPickerDialog.terminalLabel(network.getGenerator("GEN").getTerminal()),
                    ((Label) value(networkPane, "detail.extension.terminals")).getText());
            assertEquals("16", ((Label) value(networkPane, "detail.extension.cimCharacteristics.cimVersion")).getText());
        });
    }

    @Test
    void editsSecondaryVoltageControl() {
        Network network = EurostagTutorialExample1Factory.create();
        network.newExtension(SecondaryVoltageControlAdder.class)
                .newControlZone().withName("z1")
                .newPilotPoint().withBusbarSectionsOrBusesIds(List.of("NLOAD")).withTargetV(150).add()
                .newControlUnit().withId("GEN").withParticipate(true).add()
                .add()
                .add();
        interact(() -> {
            TextField targetV = (TextField) value(pane(network, ExtensionEditors.forNetwork()), "detail.extension.secondaryVoltageControl.targetV");
            targetV.setText("152");
            targetV.getOnAction().handle(null);
        });
        assertEquals(152, network.getExtension(SecondaryVoltageControl.class).getControlZones().getFirst().getPilotPoint().getTargetV());
    }
}
