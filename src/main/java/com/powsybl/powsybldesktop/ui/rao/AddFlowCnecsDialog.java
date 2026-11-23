/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.powsybldesktop.rao.CnecEdits.StateRef;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Asks the states and monitored sides of the flow CNECs to add on the branches picked: all the states checked at first,
 * the sides the CRAC Creation tab's default ones. OK is disabled while no state or side is checked. A {@code Dialog}
 * subclass so that its test can reach its controls.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class AddFlowCnecsDialog extends Dialog<AddFlowCnecsDialog.Request> {

    /**
     * The CNECs asked: each branch at each state, with thresholds on these sides.
     */
    record Request(List<StateRef> states, Set<TwoSides> sides) {
    }

    final Map<StateRef, BooleanProperty> checkedStates = new LinkedHashMap<>();
    final ListView<StateRef> statesListView = new ListView<>();
    final CheckBox side1CheckBox = new CheckBox(Messages.get("crac.cnec.side1"));
    final CheckBox side2CheckBox = new CheckBox(Messages.get("crac.cnec.side2"));

    AddFlowCnecsDialog(Window owner, int branchCount, List<StateRef> states, Function<StateRef, String> stateName, Set<TwoSides> defaultSides) {
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(Messages.get("crac.cnec.add.title"));
        setResizable(true);
        states.forEach(state -> checkedStates.put(state, new SimpleBooleanProperty(true)));
        statesListView.getItems().setAll(states);
        statesListView.setCellFactory(CheckBoxListCell.forListView(checkedStates::get, new StringConverter<>() {
            @Override
            public String toString(StateRef state) {
                return state == null ? "" : stateName.apply(state);
            }

            @Override
            public StateRef fromString(String string) {
                throw new UnsupportedOperationException();
            }
        }));
        statesListView.setPrefHeight(220);
        Button allButton = new Button(Messages.get("crac.cnec.add.all"));
        allButton.setOnAction(_ -> checkedStates.values().forEach(checked -> checked.set(true)));
        Button noneButton = new Button(Messages.get("crac.cnec.add.none"));
        noneButton.setOnAction(_ -> checkedStates.values().forEach(checked -> checked.set(false)));
        side1CheckBox.setSelected(defaultSides.contains(TwoSides.ONE));
        side2CheckBox.setSelected(defaultSides.contains(TwoSides.TWO));

        Label intro = new Label(Messages.get("crac.cnec.add.intro", branchCount));
        intro.setWrapText(true);
        intro.setMinHeight(Region.USE_PREF_SIZE);
        Label thresholds = new Label(Messages.get("crac.cnec.add.thresholds"));
        thresholds.setWrapText(true);
        thresholds.setMinHeight(Region.USE_PREF_SIZE);
        VBox content = new VBox(8, intro, new Label(Messages.get("crac.cnec.add.states")), statesListView, new HBox(4, allButton, noneButton),
                new Label(Messages.get("crac.cnec.add.sides")), new HBox(10, side1CheckBox, side2CheckBox), thresholds);
        content.setPrefWidth(460);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        List<Observable> dependencies = new ArrayList<>(checkedStates.values());
        dependencies.add(side1CheckBox.selectedProperty());
        dependencies.add(side2CheckBox.selectedProperty());
        getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(Bindings.createBooleanBinding(
                () -> request().states().isEmpty() || request().sides().isEmpty(), dependencies.toArray(Observable[]::new)));
        setResultConverter(button -> button == ButtonType.OK ? request() : null);
    }

    Request request() {
        Set<TwoSides> sides = EnumSet.noneOf(TwoSides.class);
        if (side1CheckBox.isSelected()) {
            sides.add(TwoSides.ONE);
        }
        if (side2CheckBox.isSelected()) {
            sides.add(TwoSides.TWO);
        }
        return new Request(checkedStates.entrySet().stream().filter(entry -> entry.getValue().get()).map(Map.Entry::getKey).toList(), sides);
    }
}
