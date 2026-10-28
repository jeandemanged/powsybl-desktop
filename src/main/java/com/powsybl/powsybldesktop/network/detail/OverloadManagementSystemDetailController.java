/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.OverloadManagementSystem;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;

/**
 * Overload management system detail: whether enabled, the element and side it monitors, then a tab listing its
 * trippings - the switch, branch or three-winding transformer side each opens or closes above its current limit -, their
 * name, limit and action editable (IIDM doesn't add or remove a tripping of an existing system).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class OverloadManagementSystemDetailController extends AbstractEquipmentDetailController<OverloadManagementSystem> {

    private static final StringConverter<Double> CURRENT_FORMAT = new StringConverter<>() {
        @Override
        public String toString(Double value) {
            return value == null || value.isNaN() ? "-" : String.valueOf(value);
        }

        @Override
        public Double fromString(String text) {
            return DetailForm.parseDouble(text);
        }
    };

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return List.of();
    }

    @Override
    protected List<? extends Container<?>> linkedContainers() {
        return List.of(equipment.getSubstation());
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.SINGLE_LINE;
    }

    @Override
    protected void buildMain(DetailForm form) {
        form.section("detail.section.characteristics")
                .booleanField("edit.oms.enabled", equipment::isEnabled, equipment::setEnabled)
                .text("edit.oms.monitoredElement", equipment::getMonitoredElementId)
                .text("edit.oms.monitoredSide", () -> equipment.getMonitoredSide().name());
    }

    @Override
    protected List<Tab> extraTabs() {
        return List.of(new Tab(Messages.get("edit.oms.trippings"), register(new TrippingsPane())));
    }

    @Override
    protected List<ExtensionsPane.Editor<OverloadManagementSystem>> extensionEditors() {
        return ExtensionEditors.forIdentifiable();
    }

    private final class TrippingsPane extends VBox implements DetailPart {
        private final BooleanProperty editable = new SimpleBooleanProperty(true);
        private final TableView<OverloadManagementSystem.Tripping> table = new TableView<>();

        private TrippingsPane() {
            setPadding(new Insets(8));
            VBox.setVgrow(table, Priority.ALWAYS);
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            table.editableProperty().bind(editable);
            table.getColumns().add(DetailTables.readOnlyColumn("edit.oms.trippingKey", DetailTables.TEXT_FORMAT, OverloadManagementSystem.Tripping::getKey));
            table.getColumns().add(DetailTables.editableColumn("desktop.common.column.name", DetailTables.TEXT_FORMAT,
                    OverloadManagementSystem.Tripping::getNameOrKey, OverloadManagementSystem.Tripping::setName, onEdit()));
            table.getColumns().add(DetailTables.readOnlyColumn("edit.oms.trippingType", DetailTables.TEXT_FORMAT, tripping -> tripping.getType().name()));
            table.getColumns().add(DetailTables.readOnlyColumn("edit.oms.trippingElement", DetailTables.TEXT_FORMAT,
                    OverloadManagementSystemDetailController::element));
            table.getColumns().add(DetailTables.editableColumn("edit.oms.currentLimit", CURRENT_FORMAT,
                    OverloadManagementSystem.Tripping::getCurrentLimit, OverloadManagementSystem.Tripping::setCurrentLimit, onEdit()));
            table.getColumns().add(DetailTables.checkBoxColumn("edit.oms.openAction", OverloadManagementSystem.Tripping::isOpenAction,
                    OverloadManagementSystem.Tripping::setOpenAction, onEdit(), editable));
            getChildren().add(table);
        }

        @Override
        public void refresh() {
            table.getItems().setAll(equipment.getTrippings());
        }

        @Override
        public BooleanProperty editableProperty() {
            return editable;
        }
    }

    // the switch, branch or three-winding transformer side the tripping operates
    private static String element(OverloadManagementSystem.Tripping tripping) {
        return switch (tripping) {
            case OverloadManagementSystem.SwitchTripping switchTripping -> switchTripping.getSwitchToOperateId();
            case OverloadManagementSystem.BranchTripping branch -> branch.getBranchToOperateId() + " (" + branch.getSideToOperate() + ")";
            case OverloadManagementSystem.ThreeWindingsTransformerTripping transformer ->
                transformer.getThreeWindingsTransformerToOperateId() + " (" + transformer.getSideToOperate() + ")";
            default -> tripping.getKey();
        };
    }
}
