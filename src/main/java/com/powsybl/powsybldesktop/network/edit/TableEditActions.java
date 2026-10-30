/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.network.detail.EquipmentDetails;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.binding.Bindings;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The creation and removal actions of an equipment table: add and remove buttons at the start of its toolbar, and a
 * row context menu (details, what {@link EditMenus} offers for the row, the table's creations).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class TableEditActions {

    private TableEditActions() {
    }

    /**
     * @param creations the creation items of the table's kinds, built when shown; empty for a table that creates none
     */
    public static <T extends Identifiable<?>> void install(TableView<T> table, SceneModel sceneModel, Supplier<List<MenuItem>> creations) {
        Supplier<Window> owner = () -> table.getScene().getWindow();
        EditMenus.installContextMenu(table, () -> rowItems(table, sceneModel, owner, creations));

        ToolBar toolBar = toolBar(table);
        if (toolBar == null) {
            return;
        }
        List<Node> buttons = new ArrayList<>();
        MenuButton addButton = new MenuButton(null, new FontIcon("mdi2p-plus"));
        addButton.setTooltip(new Tooltip(Messages.get("edit.menu.new")));
        addButton.setOnShowing(_ -> addButton.getItems().setAll(disabledIfBusy(sceneModel, creations.get())));
        if (!creations.get().isEmpty()) {
            buttons.add(addButton);
        }
        Button removeButton = new Button(null, new FontIcon("mdi2d-delete"));
        removeButton.setTooltip(new Tooltip(Messages.get("desktop.common.action.remove")));
        removeButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> table.getSelectionModel().getSelectedItem() == null || !Removals.supports(table.getSelectionModel().getSelectedItem()),
                table.getSelectionModel().selectedItemProperty()).or(sceneModel.getMainModel().networkBusyProperty()));
        removeButton.setOnAction(_ -> {
            T selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Removals.remove(sceneModel.getMainModel(), owner.get(), selected);
            }
        });
        buttons.add(removeButton);
        buttons.add(new Separator(Orientation.VERTICAL));
        toolBar.getItems().addAll(0, buttons);
    }

    // the view's top toolbar, created if it has none
    private static ToolBar toolBar(TableView<?> table) {
        Parent parent = table.getParent();
        if (parent instanceof VBox root) {
            if (!root.getChildren().isEmpty() && root.getChildren().getFirst() instanceof ToolBar toolBar) {
                return toolBar;
            }
            ToolBar toolBar = new ToolBar();
            root.getChildren().addFirst(toolBar);
            return toolBar;
        }
        return null;
    }

    private static <T extends Identifiable<?>> List<MenuItem> rowItems(TableView<T> table, SceneModel sceneModel, Supplier<Window> owner,
                                                                      Supplier<List<MenuItem>> creations) {
        List<MenuItem> items = new ArrayList<>();
        T selected = table.getSelectionModel().getSelectedItem();
        if (selected != null && EquipmentDetails.supports(selected)) {
            MenuItem details = new MenuItem(Messages.get("detail.open.tooltip"));
            details.setOnAction(_ -> EquipmentDetails.open(sceneModel, selected));
            items.add(details);
        }
        List<MenuItem> created = disabledIfBusy(sceneModel, creations.get());
        if (!created.isEmpty()) {
            if (!items.isEmpty()) {
                items.add(new SeparatorMenuItem());
            }
            items.addAll(created);
        }
        if (selected != null) {
            Network network = selected.getParentNetwork();
            List<MenuItem> edits = EditMenus.items(sceneModel, owner, network, selected);
            if (!items.isEmpty() && !edits.isEmpty()) {
                items.add(new SeparatorMenuItem());
            }
            items.addAll(edits);
        }
        return items;
    }

    private static List<MenuItem> disabledIfBusy(SceneModel sceneModel, List<MenuItem> items) {
        boolean busy = sceneModel.getMainModel().networkBusyProperty().get();
        if (busy) {
            items.forEach(item -> item.setDisable(true));
        }
        return items;
    }
}
