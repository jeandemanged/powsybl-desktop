/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.ToolBar;

import java.util.List;

/**
 * Reusable, optional toolbar (see {@code column-visibility-toolbar.fxml}) of one checkbox per {@link ColumnGroup},
 * toggling that whole group of related columns' visibility together rather than one column at a time. Not every
 * equipment table needs this - only included (via {@code fx:include}) in tables with enough optional columns to
 * warrant grouping them, starting with {@link TransformersController}'s ratio/phase tap changer and solved-value
 * columns.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ColumnVisibilityToolbarController {

    @FXML
    ToolBar root;

    public void configure(List<ColumnGroup> groups) {
        for (ColumnGroup group : groups) {
            group.columns().forEach(column -> column.setVisible(group.defaultVisible()));
            CheckBox checkBox = new CheckBox(Messages.get(group.labelKey()));
            checkBox.setSelected(group.defaultVisible());
            checkBox.selectedProperty().addListener((observable, oldValue, visible) ->
                    group.columns().forEach(column -> column.setVisible(visible)));
            root.getItems().add(checkBox);
        }
    }

    public record ColumnGroup(String labelKey, boolean defaultVisible, List<TableColumnBase<?, ?>> columns) {
        public static ColumnGroup of(String labelKey, boolean defaultVisible, TableColumnBase<?, ?>... columns) {
            return new ColumnGroup(labelKey, defaultVisible, List.of(columns));
        }
    }
}
