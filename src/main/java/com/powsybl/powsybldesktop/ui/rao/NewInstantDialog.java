/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.InstantKind;
import com.powsybl.powsybldesktop.rao.CracEdits;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.Locale;

/**
 * Asks the kind and id of an instant to add to a CRAC, among the kinds it can still have (see
 * {@link CracEdits#addableInstantKinds}), and adds it on OK, staying open with the reason when OpenRAO refuses. A
 * {@code Dialog} subclass so that its test can reach its controls.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class NewInstantDialog extends Dialog<Boolean> {

    private final Crac crac;
    final ChoiceBox<InstantKind> kindChoiceBox;
    final TextField idField = new TextField();
    final Label errorLabel = new Label();

    NewInstantDialog(Window owner, Crac crac) {
        this.crac = crac;
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(Messages.get("crac.instant.add.title"));
        kindChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(CracEdits.addableInstantKinds(crac)));
        kindChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(InstantKind kind) {
                return kind == null ? "" : Messages.get("crac.instant.kind." + kind.name().toLowerCase(Locale.ROOT));
            }

            @Override
            public InstantKind fromString(String string) {
                throw new UnsupportedOperationException();
            }
        });
        kindChoiceBox.setValue(InstantKind.CURATIVE);
        errorLabel.getStyleClass().add("alert-label");
        errorLabel.setWrapText(true);
        errorLabel.setMinHeight(Region.USE_PREF_SIZE);
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        errorLabel.setVisible(false);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label(Messages.get("crac.instant.kind")), kindChoiceBox);
        grid.addRow(1, new Label(Messages.get("crac.details.id")), idField);
        Label order = new Label(Messages.get("crac.instant.add.order"));
        order.setWrapText(true);
        order.setMinHeight(Region.USE_PREF_SIZE);
        VBox content = new VBox(10, grid, order, errorLabel);
        content.setPrefWidth(440);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, event -> {
            if (!add()) {
                event.consume();
            }
        });
        setResultConverter(button -> button == ButtonType.OK);
    }

    // OK's action: false keeps the dialog open
    boolean add() {
        try {
            CracEdits.addInstant(crac, idField.getText(), kindChoiceBox.getValue());
            errorLabel.setVisible(false);
            return true;
        } catch (IllegalArgumentException e) {
            errorLabel.setText(e.getMessage());
            errorLabel.setVisible(true);
            return false;
        }
    }
}
