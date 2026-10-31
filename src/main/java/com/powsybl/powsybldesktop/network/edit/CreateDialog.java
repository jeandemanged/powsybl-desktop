/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.network.detail.EquipmentDetails;
import com.powsybl.powsybldesktop.network.tables.TableColumnSupport;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.parameters.ParametersModel;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Window;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The modal dialog of a {@link CreationForm}: OK applies it, and only closes the dialog once applied - a value that
 * can't be read or a change IIDM refuses is shown on its editor (or on the form), the dialog staying open to fix it.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class CreateDialog {

    private static final double MAX_SCREEN_HEIGHT_RATIO = 0.6;

    private CreateDialog() {
    }

    /**
     * @param apply reads the form and applies it, returning what it created
     */
    public static <T> Optional<T> show(Window owner, String title, CreationForm form, Supplier<T> apply) {
        return show(owner, title, form, null, apply);
    }

    /**
     * Same as {@link #show(Window, String, CreationForm, Supplier)}, then opens the created equipment's detail view
     * unless the user unchecked it, to set what the form doesn't ask for. The choice is remembered in the
     * {@link GuiParameters}.
     */
    public static <T extends Identifiable<?>> Optional<T> showThenDetails(SceneModel sceneModel, Window owner, String title,
                                                                         CreationForm form, Supplier<T> apply) {
        ParametersModel parametersModel = sceneModel.getMainModel().getParametersModel();
        CheckBox openDetails = new CheckBox(Messages.get("edit.create.openDetails"));
        openDetails.setSelected(parametersModel.getGuiParameters().isOpenDetailsAfterCreation());
        Optional<T> created = show(owner, title, form, openDetails, apply);
        if (openDetails.isSelected() != parametersModel.getGuiParameters().isOpenDetailsAfterCreation()) {
            parametersModel.getGuiParameters().setOpenDetailsAfterCreation(openDetails.isSelected());
            parametersModel.guiParametersChanged();
        }
        if (openDetails.isSelected()) {
            created.ifPresent(identifiable -> EquipmentDetails.open(sceneModel, identifiable));
        }
        return created;
    }

    private static <T> Optional<T> show(Window owner, String title, CreationForm form, Node footer, Supplier<T> apply) {
        Dialog<T> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.setResizable(true);
        VBox content = new VBox(12);
        ScrollPane scrollPane = new ScrollPane(form.getNode());
        scrollPane.setFitToWidth(true);
        // scrolled beyond, e.g. a three-winding transformer's legs
        double maxHeight = Screen.getPrimary().getVisualBounds().getHeight() * MAX_SCREEN_HEIGHT_RATIO;
        scrollPane.prefViewportHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.min(maxHeight, form.getNode().prefHeight(-1)), form.getNode().layoutBoundsProperty()));
        // a wider editor than its column's preferred width (e.g. a date and time) widens the form's minimum only
        scrollPane.prefViewportWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(form.getNode().prefWidth(-1), form.getNode().minWidth(-1)), form.getNode().layoutBoundsProperty()));
        scrollPane.getStyleClass().add("edge-to-edge");
        content.getChildren().add(scrollPane);
        if (footer != null) {
            content.getChildren().add(footer);
        }
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(form.validProperty().not());
        Object[] created = {null};
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                created[0] = apply.get();
            } catch (CreationForm.FieldException e) {
                TableColumnSupport.notifyEditError(e.getEditor(), e);
                event.consume();
            } catch (PowsyblException e) {
                TableColumnSupport.notifyEditError(form.getNode(), e);
                event.consume();
            } catch (IllegalArgumentException | IllegalStateException e) {
                TableColumnSupport.notifyEditError(form.getNode(), new PowsyblException(e.getMessage(), e));
                event.consume();
            }
        });
        @SuppressWarnings("unchecked")
        Supplier<T> result = () -> (T) created[0];
        dialog.setResultConverter(buttonType -> buttonType == ButtonType.OK ? result.get() : null);
        return dialog.showAndWait();
    }
}
