/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.powsybldesktop.rao.CracEdits;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.Arrays;
import java.util.List;

/**
 * Asks the id and instants of a new, empty CRAC: preventive and outage, an optional auto instant, curative ones
 * (separated by commas). OK creates it, staying open with the reason when OpenRAO refuses. A {@code Dialog} subclass
 * so that its test can reach its controls.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class NewCracDialog extends Dialog<Crac> {

    final TextField idField = new TextField();
    final TextField autoField = new TextField();
    final TextField curativeField = new TextField("curative");
    final Label errorLabel = new Label();
    private Crac created;

    NewCracDialog(Window owner, String defaultId, boolean replacing) {
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(Messages.get("crac.new.title"));
        idField.setText(defaultId);
        autoField.setPromptText(Messages.get("crac.new.auto.prompt"));
        errorLabel.getStyleClass().add("alert-label");
        errorLabel.setWrapText(true);
        errorLabel.setMinHeight(Region.USE_PREF_SIZE);
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        errorLabel.setVisible(false);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label(Messages.get("crac.details.id")), idField);
        grid.addRow(1, new Label(Messages.get("crac.new.auto")), autoField);
        grid.addRow(2, new Label(Messages.get("crac.new.curative")), curativeField);
        Label instants = new Label(Messages.get(replacing ? "crac.new.replacing" : "crac.new.instants"));
        instants.setWrapText(true);
        instants.setMinHeight(Region.USE_PREF_SIZE);
        VBox content = new VBox(10, grid, instants, errorLabel);
        content.setPrefWidth(480);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, event -> {
            if (!create()) {
                event.consume();
            }
        });
        setResultConverter(button -> button == ButtonType.OK ? created : null);
    }

    // OK's action: false keeps the dialog open
    boolean create() {
        String auto = autoField.getText().isBlank() ? null : autoField.getText().trim();
        List<String> curative = Arrays.stream(curativeField.getText().split(",")).map(String::trim).filter(name -> !name.isEmpty()).toList();
        try {
            created = CracEdits.newCrac(idField.getText(), auto, curative);
            errorLabel.setVisible(false);
            return true;
        } catch (IllegalArgumentException e) {
            errorLabel.setText(e.getMessage());
            errorLabel.setVisible(true);
            return false;
        }
    }

    Crac created() {
        return created;
    }
}
