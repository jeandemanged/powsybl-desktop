/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.limitscaling;

import com.powsybl.iidm.criteria.IdentifiableCriterion;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.limitscaling.AffectedLimits;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.security.limitscaling.LimitScaling;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class LimitScalingsControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();
    private LimitScalingsController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/ui/limitscaling/limit-scalings-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();

        mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.setNetwork(network);
        controller.setSceneModel(SceneModel.main(mainModel));

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    private NetworkStudy study() {
        return mainModel.getStudy(network);
    }

    private void add() {
        Button addButton = lookup("#addButton").query();
        interact(addButton::fire);
    }

    private void commitValue(String text) {
        TextField valueField = lookup("#valueField").query();
        interact(() -> {
            valueField.setText(text);
            valueField.fireEvent(new ActionEvent());
        });
    }

    @Test
    void addedScalingIsSelectedAndListsTheLimitsItScales() {
        add();

        assertEquals(1, study().getLimitScalings().size());
        LimitScaling added = study().getLimitScalings().getFirst();
        assertEquals(LimitType.CURRENT, added.getLimitType());
        assertEquals(1.0, added.getValue());
        assertSame(added, controller.scalingsTableView.getSelectionModel().getSelectedItem());
        assertFalse(controller.affectedTableView.getItems().isEmpty());
    }

    @Test
    void formEditReplacesTheScalingKeepingItsPlaceSelectionAndEnabledFlag() {
        interact(() -> study().getLimitScalings().setAll(new LimitScaling(LimitType.CURRENT, 1.0), new LimitScaling(LimitType.CURRENT, 1.0)));
        interact(() -> {
            study().limitScalingEnabledProperty(study().getLimitScalings().getLast()).set(false);
            controller.scalingsTableView.getSelectionModel().select(1);
        });
        int revision = study().limitScalingsRevisionProperty().get();

        commitValue("80");

        LimitScaling edited = study().getLimitScalings().getLast();
        assertEquals(0.8, edited.getValue(), 1e-9);
        assertSame(edited, controller.scalingsTableView.getSelectionModel().getSelectedItem());
        assertFalse(study().limitScalingEnabledProperty(edited).get());
        assertTrue(study().limitScalingsRevisionProperty().get() > revision);
        AffectedLimits.AffectedLimit line = controller.affectedTableView.getItems().stream()
                .filter(a -> a.equipment().getId().equals("NHV1_NHV2_1")).findFirst().orElseThrow();
        assertEquals(line.permanentLimit() * 0.8, line.scaledPermanentLimit(), 1e-6);
    }

    @Test
    void invalidFactorKeepsTheLastValidScaling() {
        add();
        LimitScaling before = study().getLimitScalings().getFirst();

        commitValue("-10");

        assertSame(before, study().getLimitScalings().getFirst());
    }

    @Test
    void movingAScalingChangesTheOrder() {
        LimitScaling first = new LimitScaling(LimitType.CURRENT, 0.9);
        LimitScaling second = new LimitScaling(LimitType.CURRENT, 0.8);
        interact(() -> {
            study().getLimitScalings().setAll(first, second);
            controller.scalingsTableView.getSelectionModel().select(1);
        });

        Button moveUpButton = lookup("#moveUpButton").query();
        interact(moveUpButton::fire);

        assertEquals(List.of(second, first), study().getLimitScalings());
        assertSame(second, controller.scalingsTableView.getSelectionModel().getSelectedItem());
    }

    @Test
    void openLoadFlowSupportIsShown() {
        add();
        Label olfLabel = lookup("#olfLabel").query();
        assertFalse(olfLabel.isVisible());
        assertEquals("", controller.olfSummaryLabel.getText());

        CheckBox monitoringOnly = lookup("#monitoringOnlyCheckBox").query();
        interact(() -> monitoringOnly.setSelected(true));

        assertTrue(study().getLimitScalings().getFirst().isMonitoringOnly());
        assertTrue(olfLabel.isVisible());
        assertTrue(olfLabel.getText().contains(Messages.get("limitScalings.olf.monitoringOnly")));
        assertEquals(Messages.get("limitScalings.olf.summary", 1, 1), controller.olfSummaryLabel.getText());
    }

    @Test
    void addedCriterionIsPartOfTheScaling() {
        add();
        MenuButton addCriterion = lookup("#addCriterionMenuButton").query();

        interact(() -> addCriterion.getItems().getFirst().fire());

        assertInstanceOf(IdentifiableCriterion.class, study().getLimitScalings().getFirst().getNetworkElementCriteria().getFirst());
    }

    @Test
    void disablingAScalingLeavesItOutOfTheEnabledOnes() {
        add();
        Button disableAll = lookup("#disableAllButton").query();

        interact(disableAll::fire);

        assertTrue(study().getEnabledLimitScalings().isEmpty());
    }
}
