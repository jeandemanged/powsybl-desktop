/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.limits;

import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.limitmodification.LimitsComputer;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.violations.FlowViolation;
import com.powsybl.powsybldesktop.violations.NetworkViolations;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

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
