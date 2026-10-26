/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.LoadingLimits;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.OperationalLimitsGroup;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.TapChanger;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.test.BatteryNetworkFactory;
import com.powsybl.iidm.network.test.BoundaryLineNetworkFactory;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.network.limits.OperationalLimitsPane;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.window.SeparateWindows;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every equipment type's detail view, and the limits, tap changer and shunt sections editors.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class EquipmentDetailViewsTest extends AbstractHeadlessApplicationTest {

    private final StackPane host = new StackPane();
    private final List<SeparateWindows.View> views = new ArrayList<>();
    private MainModel mainModel;

    @Override
    public void start(Stage stage) {
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(() -> views.forEach(view -> view.controller().dispose()));
    }

    private AbstractEquipmentDetailController<?> open(Network network, Identifiable<?> equipment) {
        SeparateWindows.View[] view = new SeparateWindows.View[1];
        interact(() -> {
            mainModel = new MainModel();
            mainModel.addNetwork(network);
            mainModel.setNetwork(network);
            view[0] = EquipmentDetails.load(equipment, SceneModel.main(mainModel));
            host.getChildren().setAll(view[0].root());
        });
        views.add(view[0]);
        return (AbstractEquipmentDetailController<?>) view[0].controller();
    }

    private static <T extends Node> T content(AbstractEquipmentDetailController<?> controller, String tabKey, Class<T> type) {
        Node content = controller.tabPane.getTabs().stream()
                .filter(tab -> tab.getText().equals(Messages.get(tabKey)))
                .map(Tab::getContent)
                .findFirst().orElseThrow();
        return type.cast(content instanceof ScrollPane scrollPane ? scrollPane.getContent() : content);
    }

    private static <T extends ButtonBase> T button(Node root, Class<T> type, String text) {
        return root.lookupAll("*").stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow(() -> new AssertionError("No button " + text));
    }

    @Test
    void everyEquipmentTypeHasADetailView() {
        Network fourSubstations = FourSubstationsNodeBreakerFactory.create();
        Network eurostag = EurostagTutorialExample1Factory.createWithTieLine();
        Network threeWindings = ThreeWindingsTransformerNetworkFactory.create();
        Network battery = BatteryNetworkFactory.create();
        Network boundaryLine = BoundaryLineNetworkFactory.create();
        List<Identifiable<?>> equipments = new ArrayList<>();
        Stream.of(fourSubstations.getGeneratorStream(), fourSubstations.getLoadStream(), fourSubstations.getShuntCompensatorStream(),
                        fourSubstations.getStaticVarCompensatorStream(), fourSubstations.getVscConverterStationStream(),
                        fourSubstations.getLccConverterStationStream(), fourSubstations.getHvdcLineStream(), fourSubstations.getLineStream(),
                        fourSubstations.getTwoWindingsTransformerStream(), fourSubstations.getBusbarSectionStream(), fourSubstations.getSwitchStream(),
                        fourSubstations.getSubstationStream(), fourSubstations.getVoltageLevelStream(),
                        eurostag.getTieLineStream(), threeWindings.getThreeWindingsTransformerStream(), battery.getBatteryStream(),
                        boundaryLine.getBoundaryLineStream())
                .forEach(stream -> equipments.add(stream.findFirst().orElseThrow()));
        for (Identifiable<?> equipment : equipments) {
            assertTrue(EquipmentDetails.supports(equipment), equipment.getId());
            AbstractEquipmentDetailController<?> controller = open(equipment.getNetwork(), equipment);
            interact(() -> mainModel.setUpdate());
            assertTrue(controller.titleLabel.getText().contains(equipment.getNameOrId()), equipment.getId());
            // main, extensions and properties tabs at least
            assertTrue(controller.tabPane.getTabs().size() >= 3, equipment.getId());
            assertFalse(controller.linksBox.getChildren().size() < 2, equipment.getId());
        }
    }

    @Test
    void editsLineLimits() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        Line line = network.getLineStream().findFirst().orElseThrow();
        AbstractEquipmentDetailController<?> controller = open(network, line);
        OperationalLimitsPane pane = content(controller, "detail.tab.limits", OperationalLimitsPane.class);

        TextField groupId = (TextField) pane.lookupAll(".text-field").iterator().next();
        interact(() -> {
            groupId.setText("summer");
            button(pane, Button.class, Messages.get("limits.edit.addGroup")).fire();
        });
        OperationalLimitsGroup group = line.getOperationalLimitsGroup1("summer").orElseThrow();

        MenuButton addLimits = button(pane, MenuButton.class, Messages.get("limits.edit.addLimits"));
        interact(() -> addLimits.getItems().stream()
                .filter(item -> item.getText().equals(Messages.get("desktop.common.limitViolationType.CURRENT")))
                .findFirst().orElseThrow().fire());
        assertTrue(group.getCurrentLimits().isPresent());

        // IIDM refuses temporary limits without permanent limit
        interact(() -> button(pane, Button.class, Messages.get("limits.edit.addTemporaryLimit")).fire());
        assertTrue(group.getLoadingLimits(LimitType.CURRENT).orElseThrow().getTemporaryLimits().isEmpty());

        interact(() -> {
            group.getLoadingLimits(LimitType.CURRENT).orElseThrow().setPermanentLimit(500);
            mainModel.setUpdate();
        });
        interact(() -> button(pane, Button.class, Messages.get("limits.edit.addTemporaryLimit")).fire());
        LoadingLimits limits = group.getLoadingLimits(LimitType.CURRENT).orElseThrow();
        assertEquals(500, limits.getPermanentLimit());
        assertEquals(1, limits.getTemporaryLimits().size());
        assertEquals(1200, limits.getTemporaryLimits().iterator().next().getAcceptableDuration());
        @SuppressWarnings("unchecked")
        TableView<Object> table = (TableView<Object>) pane.lookup(".table-view");
        assertEquals(2, table.getItems().size());

        // the selection checkbox
        interact(() -> pane.lookupAll(".check-box").stream()
                .filter(node -> Messages.get("limits.edit.selected").equals(((Labeled) node).getText()))
                .map(ButtonBase.class::cast)
                .reduce((first, second) -> second).orElseThrow().fire());
        assertTrue(line.getAllSelectedOperationalLimitsGroupIds(TwoSides.ONE).contains("summer"));

        interact(() -> pane.lookupAll(".button").stream()
                .map(ButtonBase.class::cast)
                .filter(button -> Messages.get("limits.edit.removeLimits").equals(button.getText()))
                .reduce((first, second) -> second).orElseThrow().fire());
        assertTrue(line.getOperationalLimitsGroup1("summer").orElseThrow().getCurrentLimits().isEmpty());
    }

    @Test
    void editsTapChangers() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream()
                .filter(TwoWindingsTransformer::hasPhaseTapChanger)
                .findFirst().orElseThrow();
        transformer.getOptionalRatioTapChanger().ifPresent(TapChanger::remove);
        AbstractEquipmentDetailController<?> controller = open(network, transformer);
        Parent tapChangers = content(controller, "detail.tab.tapChangers", Parent.class);

        int steps = transformer.getPhaseTapChanger().getStepCount();
        TapChangerPane phasePane = (TapChangerPane) tapChangers.getChildrenUnmodifiable().get(1);
        interact(() -> button(phasePane, Button.class, Messages.get("detail.tapChanger.addStep")).fire());
        assertEquals(steps + 1, transformer.getPhaseTapChanger().getStepCount());

        TapChangerPane ratioPane = (TapChangerPane) tapChangers.getChildrenUnmodifiable().get(0);
        interact(() -> button(ratioPane, Button.class, Messages.get("desktop.common.action.add")).fire());
        assertNotNull(transformer.getRatioTapChanger());
        assertEquals(1, transformer.getRatioTapChanger().getStepCount());

        interact(() -> button(ratioPane, Button.class, Messages.get("desktop.common.action.remove")).fire());
        assertNull(transformer.getRatioTapChanger());
    }

    @Test
    void showsShuntSections() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        ShuntCompensator shunt = network.getShuntCompensatorStream().findFirst().orElseThrow();
        AbstractEquipmentDetailController<?> controller = open(network, shunt);
        TableView<?> sections = (TableView<?>) content(controller, "detail.section.sections", Parent.class).lookup(".table-view");
        assertEquals(shunt.getMaximumSectionCount(), sections.getItems().size());
        TableColumn<?, ?> qColumn = sections.getColumns().get(3);
        assertEquals(-shunt.getB(1) * Math.pow(shunt.getTerminal().getVoltageLevel().getNominalV(), 2), (Double) qColumn.getCellData(0), 1e-9);
    }
}
