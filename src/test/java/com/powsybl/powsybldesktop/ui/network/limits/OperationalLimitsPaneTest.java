/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.network.limits;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.limitmodification.LimitsComputer;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import com.powsybl.powsybldesktop.limitscaling.LimitScalings;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.violations.FlowViolation;
import com.powsybl.powsybldesktop.violations.NetworkViolations;
import com.powsybl.security.limitscaling.LimitScaling;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class OperationalLimitsPaneTest extends AbstractHeadlessApplicationTest {

    private OperationalLimitsPane pane;

    @Override
    public void start(Stage stage) {
        pane = new OperationalLimitsPane(new GuiParameters());
        stage.setScene(new Scene(new StackPane(pane), 800, 600));
        stage.show();
    }

    private List<String> sideTitles() {
        return pane.getChildren().stream()
                .filter(node -> node.getStyleClass().contains("limits-side-title"))
                .map(node -> ((Label) node).getText())
                .toList();
    }

    private List<VBox> groups() {
        return pane.getChildren().stream()
                .filter(node -> node.getStyleClass().contains("limits-group"))
                .map(VBox.class::cast)
                .toList();
    }

    private static String title(VBox group) {
        return ((Label) group.getChildren().getFirst()).getText();
    }

    @SuppressWarnings("unchecked")
    private static List<TableView<OperationalLimitsPane.LimitRow>> tables(VBox group) {
        return group.getChildren().stream()
                .filter(TableView.class::isInstance)
                .map(node -> (TableView<OperationalLimitsPane.LimitRow>) node)
                .toList();
    }

    @Test
    void scaledLimitsAreShownInAColumnOfTheirOwn() {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();
        Line line = network.getLine("NHV1_NHV2_1");

        interact(() -> pane.setEquipment(line, List.of()));
        TableView<OperationalLimitsPane.LimitRow> unscaled = tables(groups().getFirst()).getFirst();
        assertEquals(List.of(Messages.get("limits.column.name"), Messages.get("limits.column.value"),
                Messages.get("limits.column.acceptableDuration"), Messages.get("limits.column.fictitious")),
                unscaled.getColumns().stream().map(TableColumn::getText).toList());

        interact(() -> pane.setEquipment(line, List.of(), true, List.of(new OperationalLimitsPane.ScaledLimits(null,
                LimitScalings.limitsComputer(List.of(new LimitScaling(LimitType.CURRENT, 0.5)))))));
        TableView<OperationalLimitsPane.LimitRow> scaled = tables(groups().getFirst()).getFirst();
        TableColumn<OperationalLimitsPane.LimitRow, ?> scaledColumn = scaled.getColumns().get(2);
        assertEquals(Messages.get("limits.column.scaledValue"), scaledColumn.getText());
        OperationalLimitsPane.LimitRow permanent = scaled.getItems().getFirst();
        assertEquals(current(permanent.value() * 0.5), scaledColumn.getCellData(permanent));
    }

    private static String current(double value) {
        return new GuiParameters().format(GuiParameters.Quantity.CURRENT, value);
    }

    @Test
    void statesScalingDifferentlyGetAColumnEach() {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();
        Line line = network.getLine("NHV1_NHV2_1");
        List<LimitScaling> scalings = List.of(LimitScaling.builder(LimitType.CURRENT, 0.5)
                .withContingencyContext(ContingencyContext.specificContingency("C1")).build());

        interact(() -> pane.setEquipment(line, List.of(), false, List.of(
                new OperationalLimitsPane.ScaledLimits("Pre", LimitScalings.limitsComputer(scalings)),
                new OperationalLimitsPane.ScaledLimits("C1", LimitScalings.limitsComputer(scalings, "C1")),
                new OperationalLimitsPane.ScaledLimits("C2", LimitScalings.limitsComputer(scalings, "C2")))));

        TableView<OperationalLimitsPane.LimitRow> table = tables(groups().getFirst()).getFirst();
        // only C1 scales the limits
        assertEquals(Messages.get("limits.column.scaledValue.states", "C1"), table.getColumns().get(2).getText());
        assertEquals(Messages.get("limits.column.acceptableDuration"), table.getColumns().get(3).getText());
    }

    @Test
    void editableModeShowsTheScaledLimitsReadOnly() {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();
        Line line = network.getLine("NHV1_NHV2_1");
        List<LimitScaling> scalings = new ArrayList<>();
        OperationalLimitsPane[] editable = new OperationalLimitsPane[1];
        interact(() -> {
            editable[0] = OperationalLimitsPane.editable(line, GuiParameters::new, () -> { }, () -> LimitScalings.limitsComputer(List.copyOf(scalings)));
            pane.getChildren().setAll(editable[0]);
        });
        assertEquals(4, editableTable(editable[0]).getColumns().size());

        scalings.add(new LimitScaling(LimitType.CURRENT, 0.5));
        interact(editable[0]::refresh);

        TableView<OperationalLimitsPane.LimitRow> table = editableTable(editable[0]);
        TableColumn<OperationalLimitsPane.LimitRow, ?> scaledColumn = table.getColumns().get(2);
        assertEquals(Messages.get("limits.column.scaledValue"), scaledColumn.getText());
        assertEquals(current(table.getItems().getFirst().value() * 0.5), scaledColumn.getCellData(table.getItems().getFirst()));
    }

    @SuppressWarnings("unchecked")
    private static TableView<OperationalLimitsPane.LimitRow> editableTable(OperationalLimitsPane editable) {
        return editable.getChildren().stream()
                .filter(node -> node.getStyleClass().contains("limits-group"))
                .flatMap(node -> ((VBox) node).getChildren().stream())
                .filter(TableView.class::isInstance)
                .map(node -> (TableView<OperationalLimitsPane.LimitRow>) node)
                .findFirst().orElseThrow();
    }

    @Test
    void everySelectedGroupOfEverySideIsDetailedAndViolatedOnesHighlighted() {
        Network network = EurostagTutorialExample1Factory.createWithMultipleSelectedFixedCurrentLimits();
        Line line = network.getLine("NHV1_NHV2_1");
        // 1300 A on side 1
        line.getTerminal1().getBusView().getBus().setV(380);
        line.getTerminal1().setP(1300 * Math.sqrt(3) * 380 / 1000).setQ(0);
        List<FlowViolation> violations = NetworkViolations.compute(network, LimitsComputer.NO_MODIFICATIONS).getFlowViolations(line.getId());
        assertFalse(violations.isEmpty());

        interact(() -> pane.setEquipment(line, violations));

        assertEquals(List.of(Messages.get("limits.side", 1), Messages.get("limits.side", 2)), sideTitles());
        List<VBox> groups = groups();
        assertEquals(line.getAllSelectedOperationalLimitsGroups(TwoSides.ONE).size()
                + line.getAllSelectedOperationalLimitsGroups(TwoSides.TWO).size(), groups.size());
        VBox activated11 = groups.stream().filter(g -> title(g).equals(Messages.get("limits.group", "activated_1_1"))).findFirst().orElseThrow();
        assertTrue(activated11.getStyleClass().contains("limits-group-violated"));
        // permanent limit, then the temporary ones
        List<OperationalLimitsPane.LimitRow> rows = tables(activated11).getFirst().getItems();
        assertEquals(List.of(1100.0, 1200.0, 1500.0), rows.stream().map(OperationalLimitsPane.LimitRow::value).toList());
        List<LimitViolation> groupViolations = violations.stream().map(FlowViolation::violation)
                .filter(v -> "activated_1_1".equals(v.getOperationalLimitsGroupId())).toList();
        // 1300 A: the 1200 A limit is the crossed one
        assertEquals(List.of(false, true, false), rows.stream().map(row -> OperationalLimitsPane.isCrossed(row, groupViolations)).toList());
        // a computation's limit, rounded through per-unit conversions
        LimitViolation rounded = groupViolations.getFirst();
        List<LimitViolation> roundedViolations = List.of(new LimitViolation(rounded.getSubjectId(), null, rounded.getOperationalLimitsGroupId(), rounded.getLimitType(), rounded.getLimitName(),
                rounded.getAcceptableDuration(), Math.nextUp(rounded.getLimit()), rounded.getLimitScaling(), rounded.getValue(), rounded.getSide(), null));
        assertEquals(List.of(false, true, false), rows.stream().map(row -> OperationalLimitsPane.isCrossed(row, roundedViolations)).toList());
        // side 2 carries no current
        assertTrue(groups.stream().filter(g -> title(g).equals(Messages.get("limits.group", "activated_2_1")))
                .noneMatch(g -> g.getStyleClass().contains("limits-group-violated")));
    }

    @Test
    void threeWindingsTransformerHasOneSidePerLeg() {
        Network network = ThreeWindingsTransformerNetworkFactory.createWithCurrentLimits();
        ThreeWindingsTransformer transformer = network.getThreeWindingsTransformerStream().findFirst().orElseThrow();

        interact(() -> pane.setEquipment(transformer, List.of()));

        assertEquals(List.of(Messages.get("limits.side", 1), Messages.get("limits.side", 2), Messages.get("limits.side", 3)), sideTitles());
        assertTrue(groups().stream().noneMatch(g -> g.getStyleClass().contains("limits-group-violated")));
    }

    @Test
    void sidesWithoutSelectedGroupAndEquipmentWithoutLimitsSaySo() {
        Network network = EurostagTutorialExample1Factory.create();
        Line line = network.getLine("NHV1_NHV2_1");

        interact(() -> pane.setEquipment(line, List.of()));

        assertEquals(2, pane.getChildren().stream()
                .filter(node -> node instanceof Label label && Messages.get("limits.noSelectedGroup").equals(label.getText())).count());

        interact(() -> pane.setEquipment(network.getGenerator("GEN"), List.of()));

        List<Node> children = pane.getChildren();
        assertEquals(1, children.size());
        assertEquals(Messages.get("limits.noLimits"), ((Label) children.getFirst()).getText());
    }
}
