/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.Connectable;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.powsybldesktop.MainApplication;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Modal dialog choosing a terminal of a view's network: an equipment found with the search box, then one of its
 * terminals (side). Used to set a regulated terminal.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class TerminalPickerDialog {

    // the kinds whose equipment are connectables, i.e. have terminals
    private static final EnumSet<NetworkSearch.Kind> KINDS = EnumSet.of(NetworkSearch.Kind.GENERATOR, NetworkSearch.Kind.BATTERY,
            NetworkSearch.Kind.SHUNT_COMPENSATOR, NetworkSearch.Kind.STATIC_VAR_COMPENSATOR, NetworkSearch.Kind.VSC_CONVERTER_STATION,
            NetworkSearch.Kind.LCC_CONVERTER_STATION, NetworkSearch.Kind.LOAD, NetworkSearch.Kind.LINE, NetworkSearch.Kind.TRANSFORMER,
            NetworkSearch.Kind.BOUNDARY_LINE, NetworkSearch.Kind.BUSBAR_SECTION);

    private TerminalPickerDialog() {
    }

    /**
     * @param sceneModel the network of its view, searched for the equipment
     */
    static Optional<Terminal> show(Window owner, SceneModel sceneModel) {
        FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource("network/search/search-box.fxml"), Messages.bundle());
        Parent searchBox;
        try {
            searchBox = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SearchBoxController searchBoxController = loader.getController();

        Label equipmentLabel = new Label(Messages.get("detail.terminalPicker.none"));
        ChoiceBox<Terminal> terminalChoiceBox = new ChoiceBox<>();
        terminalChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Terminal terminal) {
                return terminal == null ? "" : terminalLabel(terminal);
            }

            @Override
            public Terminal fromString(String string) {
                throw new UnsupportedOperationException();
            }
        });
        searchBoxController.bind(sceneModel, KINDS, match -> {
            List<? extends Terminal> terminals = match instanceof Connectable<?> connectable ? connectable.getTerminals() : List.of();
            equipmentLabel.setText(terminals.isEmpty() ? Messages.get("detail.terminalPicker.none") : match.getNameOrId());
            terminalChoiceBox.getItems().setAll(terminals);
            terminalChoiceBox.getSelectionModel().selectFirst();
        });

        Dialog<Terminal> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(Messages.get("detail.voltageRegulation.terminal"));
        dialog.setHeaderText(Messages.get("detail.terminalPicker.header"));
        dialog.getDialogPane().setContent(new VBox(8, searchBox, equipmentLabel, terminalChoiceBox));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(terminalChoiceBox.valueProperty().isNull());
        dialog.setResultConverter(button -> button == ButtonType.OK ? terminalChoiceBox.getValue() : null);
        try {
            return dialog.showAndWait();
        } finally {
            searchBoxController.dispose();
        }
    }

    /**
     * The terminal's equipment, its side if it has several, and its voltage level.
     */
    static String terminalLabel(Terminal terminal) {
        Identifiable<?> connectable = terminal.getConnectable();
        String side = Terminal.getConnectableSide(terminal)
                .filter(s -> connectable instanceof Connectable<?> c && c.getTerminals().size() > 1)
                .map(s -> " " + Messages.get("limits.side", s.getNum()))
                .orElse("");
        return connectable.getNameOrId() + side + " (" + terminal.getVoltageLevel().getNameOrId() + ")";
    }
}
