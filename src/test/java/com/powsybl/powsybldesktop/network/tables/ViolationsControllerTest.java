/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.violations.FlowViolation;
import com.powsybl.powsybldesktop.violations.Loading;
import com.powsybl.powsybldesktop.violations.VoltageViolation;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ViolationsControllerTest extends AbstractHeadlessApplicationTest {

    private String eurostagId;
    private String threeWindingsId;
    private Network network;
    private ViolationsController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        Network eurostag = EurostagTutorialExample1Factory.createWithLFResults();
        Line line1 = eurostag.getLine("NHV1_NHV2_1");
        double i1 = line1.getTerminal1().getI();
        double i2 = line1.getTerminal2().getI();
        // side 1: 600 s temporary limit violation, 130 %; side 2: 60 s temporary limit violation, 120 %
        line1.newCurrentLimits1()
                .setPermanentLimit(i1 / 1.3)
                .beginTemporaryLimit().setName("TL600").setAcceptableDuration(600).setValue(i1 * 1.1).endTemporaryLimit()
                .add();
        line1.newCurrentLimits2()
                .setPermanentLimit(i2 / 1.2)
                .beginTemporaryLimit().setName("TL600").setAcceptableDuration(600).setValue(i2 * 0.9).endTemporaryLimit()
                .beginTemporaryLimit().setName("TL60").setAcceptableDuration(60).setValue(i2 * 1.1).endTemporaryLimit()
                .add();
        Line line2 = eurostag.getLine("NHV1_NHV2_2");
        line2.newCurrentLimits1().setPermanentLimit(line2.getTerminal1().getI() / 1.5).add();
        Bus high = eurostag.getVoltageLevel("VLHV1").getBusView().getBuses().iterator().next();
        high.getVoltageLevel().setHighVoltageLimit(high.getV() - 1);
        Bus low = eurostag.getVoltageLevel("VLHV2").getBusView().getBuses().iterator().next();
        low.getVoltageLevel().setLowVoltageLimit(low.getV() + 2);

        Network threeWindings = ThreeWindingsTransformerNetworkFactory.create();
        ThreeWindingsTransformer.Leg leg1 = threeWindings.getThreeWindingsTransformerStream().findFirst().orElseThrow().getLeg1();
        // also an undervoltage: VL_132's low limit is higher
        leg1.getTerminal().getBusView().getBus().setV(100);
        leg1.getTerminal().setP(1000).setQ(1000);
        leg1.newCurrentLimits().setPermanentLimit(leg1.getTerminal().getI() / 3).add();

        eurostagId = eurostag.getId();
        threeWindingsId = threeWindings.getId();
        network = Network.merge(eurostag, threeWindings);
        mainModel = new MainModel();
        mainModel.addNetwork(network);
        ViolationsTestSupport.recompute(mainModel, network);

        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/violations-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        controller.setSceneModel(SceneModel.main(mainModel));

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    private List<String> flowSubjects() {
        return controller.flowTableView.getItems().stream().map(row -> row.equipment().getId()).toList();
    }

    @Test
    void oneFlowRowPerEquipmentSortedMostSevereFirst() {
        // the permanent limit violations (of permanent limits only, not to be exceeded at all) by percentage first, then
        // the 60 s temporary limit violation
        assertEquals(List.of("3WT", "NHV1_NHV2_2", "NHV1_NHV2_1"), flowSubjects());
        assertEquals(Messages.get("violations.tab.flow", 3), controller.flowTab.getText());

        ViolationsController.FlowRow line1 = controller.flowTableView.getItems().get(2);
        assertEquals(2, line1.violations().size());
        FlowViolation worst = line1.worst();
        assertEquals(ThreeSides.TWO, worst.violation().getSide());
        assertEquals(60, worst.violation().getAcceptableDuration());
        TableColumn<ViolationsController.FlowRow, Loading> loadingColumn = ViolationsTestSupport.column(controller.flowTableView, "loading");
        TableColumn<ViolationsController.FlowRow, Integer> durationColumn = ViolationsTestSupport.column(controller.flowTableView, "acceptableDuration");
        assertEquals(worst, loadingColumn.getCellObservableValue(2).getValue().violation());
        assertEquals(60, durationColumn.getCellObservableValue(2).getValue());
        // a permanent limit violation has no acceptable duration to show
        assertNull(durationColumn.getCellObservableValue(0).getValue());
    }

    @Test
    void voltageRowsAreSortedMostSevereFirst() {
        List<VoltageViolation> rows = controller.voltageTableView.getItems();
        assertEquals(List.of("VL_132", "VLHV2", "VLHV1"), rows.stream().map(v -> v.violation().getSubjectId()).toList());
        assertEquals(LimitViolationType.LOW_VOLTAGE, rows.getFirst().violation().getLimitType());
    }

    @Test
    void onlyTheSelectedSubnetworkViolationsAreShown() {
        interact(() -> mainModel.setNetwork(network.getSubnetwork(threeWindingsId)));

        assertEquals(List.of("3WT"), flowSubjects());
        assertEquals(1, controller.voltageTableView.getItems().size());

        interact(() -> mainModel.setNetwork(network.getSubnetwork(eurostagId)));

        assertEquals(List.of("NHV1_NHV2_2", "NHV1_NHV2_1"), flowSubjects());
        assertEquals(2, controller.voltageTableView.getItems().size());
    }
}
