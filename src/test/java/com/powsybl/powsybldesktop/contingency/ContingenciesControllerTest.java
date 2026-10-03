/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.contingency;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.contingency.list.DefaultContingencyList;
import com.powsybl.contingency.list.LineCriterionContingencyList;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ContingenciesControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();
    private ContingenciesController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/contingency/contingencies-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();

        mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.setNetwork(network);
        controller.setMainModel(mainModel);

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    @Test
    void aggregatesContingenciesAcrossAllSubLists() {
        Line line = network.getLineStream().findFirst().orElseThrow();

        interact(() -> {
            mainModel.getContingencyLists(network).add(
                    new LineCriterionContingencyList("all-lines", null, null, List.of(), null));
            mainModel.getContingencyLists(network).add(
                    new DefaultContingencyList("explicit", List.of(Contingency.line(line.getId()))));
        });

        // one Contingency per network line from the criterion list, plus the one explicit entry
        assertEquals(network.getLineCount() + 1, controller.contingenciesTableView.getItems().size());
    }

    @Test
    void invalidExplicitContingencyIsShownButNotValid() {
        interact(() -> mainModel.getContingencyLists(network).add(
                new DefaultContingencyList("explicit", List.of(Contingency.line("does-not-exist")))));

        List<Contingency> shown = controller.contingenciesTableView.getItems();
        assertEquals(1, shown.size());
        assertEquals("does-not-exist", shown.get(0).getId());
        assertTrue(ContingencyList.getValidContingencies(shown, network).isEmpty());
    }

    @Test
    void removingASubListRecomputesTheTable() {
        interact(() -> mainModel.getContingencyLists(network).add(
                new DefaultContingencyList("explicit", List.of(Contingency.line("does-not-exist")))));
        assertEquals(1, controller.contingenciesTableView.getItems().size());

        interact(() -> mainModel.getContingencyLists(network).clear());
        assertTrue(controller.contingenciesTableView.getItems().isEmpty());
    }
}
