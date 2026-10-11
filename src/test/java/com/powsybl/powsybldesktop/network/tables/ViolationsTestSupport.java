/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.limitmodification.LimitsComputer;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.violations.NetworkViolations;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Stands in for MainController, which computes the violations of a loaded network and after each load flow run.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class ViolationsTestSupport {

    private ViolationsTestSupport() {
    }

    // FX thread
    static void recompute(MainModel mainModel, Network network) {
        mainModel.getStudy(network).setViolations(NetworkViolations.compute(network.getNetwork(), LimitsComputer.NO_MODIFICATIONS));
        mainModel.setUpdate();
    }

    // the columns of the violation tables are built by ViolationColumns, identified by their ids
    @SuppressWarnings("unchecked")
    static <S, T> TableColumn<S, T> column(TableView<S> table, String id) {
        return (TableColumn<S, T>) table.getColumns().stream().filter(c -> id.equals(c.getId())).findFirst().orElseThrow();
    }

    static TableColumnSupport.LoadingBar loadingBar(Node flowViolationGraphic) {
        return ((Parent) flowViolationGraphic).getChildrenUnmodifiable().stream()
                .filter(TableColumnSupport.LoadingBar.class::isInstance)
                .map(TableColumnSupport.LoadingBar.class::cast)
                .findFirst().orElseThrow();
    }

    static TableColumnSupport.VoltageBar voltageBar(Node voltageRangeGraphic) {
        return ((Parent) voltageRangeGraphic).getChildrenUnmodifiable().stream()
                .filter(TableColumnSupport.VoltageBar.class::isInstance)
                .map(TableColumnSupport.VoltageBar.class::cast)
                .findFirst().orElseThrow();
    }

    static Label voltageLabel(Node voltageRangeGraphic) {
        return ((Parent) voltageRangeGraphic).getChildrenUnmodifiable().stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .findFirst().orElseThrow();
    }

    static String loadingText(Node flowViolationGraphic) {
        return ((Parent) flowViolationGraphic).getChildrenUnmodifiable().stream()
                .filter(node -> node instanceof Label)
                .map(node -> ((Label) node).getText())
                .findFirst().orElseThrow();
    }
}
