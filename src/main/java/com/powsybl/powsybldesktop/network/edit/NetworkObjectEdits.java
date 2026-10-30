/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Area;
import com.powsybl.iidm.network.AreaAdder;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.OverloadManagementSystem;
import com.powsybl.iidm.network.OverloadManagementSystemAdder;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.VoltageAngleLimit;
import com.powsybl.iidm.network.VoltageAngleLimitAdder;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.network.detail.DetailForm;
import com.powsybl.powsybldesktop.network.detail.DetailSections;
import com.powsybl.powsybldesktop.network.detail.TerminalPickerDialog;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.controlsfx.control.CheckListView;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Creation dialogs of the network objects outside of voltage levels: areas, overload management systems (in a
 * substation), voltage angle limits; and the removal of the latter, which isn't an identifiable.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class NetworkObjectEdits {

    private NetworkObjectEdits() {
    }

    /**
     * Creates an area of voltage levels, its boundaries being set afterwards in its detail view.
     */
    public static Optional<Area> newArea(SceneModel sceneModel, Window owner, Network network) {
        CreationForm form = new CreationForm();
        CreationForm.Field<String> id = form.id(network, NetworkEdits.uniqueId(network, "AREA"));
        CreationForm.Field<String> name = form.text("desktop.common.column.name", null, false);
        ComboBox<String> areaTypeBox = new ComboBox<>();
        areaTypeBox.setEditable(true);
        areaTypeBox.getItems().setAll(network.getAreaTypeStream().sorted().toList());
        areaTypeBox.setValue(areaTypeBox.getItems().isEmpty() ? "ControlArea" : areaTypeBox.getItems().getFirst());
        CreationForm.Field<String> areaType = form.custom("edit.area.type", areaTypeBox, null, () -> {
            String text = areaTypeBox.getEditor().getText();
            if (text == null || text.isBlank()) {
                throw new CreationForm.FieldException(areaTypeBox, Messages.get("desktop.common.editError.mandatory"));
            }
            return text.trim();
        });
        CreationForm.Field<Double> interchangeTarget = form.number("edit.area.interchangeTarget", DetailForm.MW, Double.NaN, false);
        CheckListView<VoltageLevel> voltageLevels = TopologyEdits.checkList(network.getVoltageLevelStream()
                .sorted(Comparator.comparing(VoltageLevel::getNameOrId)).toList());
        form.custom("desktop.common.equipment.voltageLevel.plural", voltageLevels, null, () -> null);
        return CreateDialog.showThenDetails(sceneModel, owner, Messages.get("edit.create.title", Messages.get("desktop.common.equipment.area")), form, () -> {
            String areaId = id.get();
            AreaAdder adder = network.newArea().setId(areaId).setName(name.get()).setAreaType(areaType.get());
            if (!Double.isNaN(interchangeTarget.get())) {
                adder.setInterchangeTarget(interchangeTarget.get());
            }
            voltageLevels.getCheckModel().getCheckedItems().forEach(adder::addVoltageLevel);
            NetworkEdits.apply(sceneModel.getMainModel(), network, _ -> adder.add());
            return network.getArea(areaId);
        });
    }

    private record TrippingRow(OverloadManagementSystem.Tripping.Type type, Identifiable<?> element, Object side, String key,
                               double currentLimit, boolean open) {
        @Override
        public String toString() {
            return key + ": " + (open ? "open " : "close ") + element.getNameOrId() + (side == null ? "" : " (" + side + ")") + " > " + currentLimit + " A";
        }
    }

    /**
     * Creates an overload management system in {@code substation}: the element it monitors, the switches or branches
     * it trips above their current limits.
     */
    public static Optional<OverloadManagementSystem> newOverloadManagementSystem(SceneModel sceneModel, Window owner, Substation substation) {
        Network network = substation.getParentNetwork();
        List<Identifiable<?>> branches = branchesOf(substation);
        CreationForm form = new CreationForm();
        CreationForm.Field<String> id = form.id(network, NetworkEdits.uniqueId(network, substation.getId() + "_OMS"));
        CreationForm.Field<String> name = form.text("desktop.common.column.name", null, false);
        CreationForm.Field<Boolean> enabled = form.check("edit.oms.enabled", true);
        CreationForm.Field<Identifiable<?>> monitored = form.choice("edit.oms.monitoredElement", branches, CreationForm.identifiableFormat(),
                branches.stream().findFirst().orElse(null), true);
        CreationForm.Field<ThreeSides> monitoredSide = form.choice("edit.oms.monitoredSide", DetailSections.values(ThreeSides.class),
                DetailSections.enumFormat(), ThreeSides.ONE, true);
        form.section("edit.oms.trippings");
        ListView<TrippingRow> trippings = new ListView<>();
        trippings.setPrefHeight(120);
        form.custom("edit.oms.trippings", trippings, null, () -> null);
        addTrippingEditor(form, substation, trippings);
        return CreateDialog.showThenDetails(sceneModel, owner,
                Messages.get("edit.create.title", Messages.get("desktop.common.equipment.overloadManagementSystem")), form, () -> {
                    String systemId = id.get();
                    if (trippings.getItems().isEmpty()) {
                        throw new CreationForm.FieldException(trippings, Messages.get("edit.oms.noTripping"));
                    }
                    OverloadManagementSystemAdder adder = substation.newOverloadManagementSystem().setId(systemId).setName(name.get())
                            .setEnabled(enabled.get()).setMonitoredElementId(monitored.get().getId()).setMonitoredElementSide(monitoredSide.get());
                    trippings.getItems().forEach(row -> addTripping(adder, row));
                    NetworkEdits.apply(sceneModel.getMainModel(), network, _ -> adder.add());
                    return network.getOverloadManagementSystem(systemId);
                });
    }

    private static List<Identifiable<?>> branchesOf(Substation substation) {
        return Stream.concat(substation.getVoltageLevelStream().flatMap(VoltageLevel::getLineStream).distinct().map(line -> (Identifiable<?>) line),
                        Stream.concat(substation.getTwoWindingsTransformerStream(), substation.getThreeWindingsTransformerStream()))
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
    }

    private static void addTripping(OverloadManagementSystemAdder adder, TrippingRow row) {
        switch (row.type()) {
            case SWITCH_TRIPPING -> adder.newSwitchTripping().setKey(row.key()).setCurrentLimit(row.currentLimit()).setOpenAction(row.open())
                    .setSwitchToOperateId(row.element().getId()).add();
            case BRANCH_TRIPPING -> adder.newBranchTripping().setKey(row.key()).setCurrentLimit(row.currentLimit()).setOpenAction(row.open())
                    .setBranchToOperateId(row.element().getId()).setSideToOperate((TwoSides) row.side()).add();
            case THREE_WINDINGS_TRANSFORMER_TRIPPING -> adder.newThreeWindingsTransformerTripping().setKey(row.key()).setCurrentLimit(row.currentLimit())
                    .setOpenAction(row.open()).setThreeWindingsTransformerToOperateId(row.element().getId()).setSideToOperate((ThreeSides) row.side()).add();
        }
    }

    /**
     * The fields of a tripping to add to the list, with its add and remove buttons.
     */
    private static void addTrippingEditor(CreationForm form, Substation substation, ListView<TrippingRow> trippings) {
        List<Switch> switches = substation.getVoltageLevelStream().flatMap(voltageLevel -> voltageLevel.getTopologyKind() == TopologyKind.NODE_BREAKER
                        ? voltageLevel.getNodeBreakerView().getSwitchStream() : voltageLevel.getBusBreakerView().getSwitchStream())
                .sorted(Comparator.comparing(Switch::getNameOrId)).toList();
        List<Identifiable<?>> branches = branchesOf(substation);
        CreationForm.Field<OverloadManagementSystem.Tripping.Type> type = form.choice("edit.oms.trippingType",
                DetailSections.values(OverloadManagementSystem.Tripping.Type.class), DetailSections.enumFormat(),
                OverloadManagementSystem.Tripping.Type.SWITCH_TRIPPING, true);
        ComboBox<Identifiable<?>> elementBox = new ComboBox<>();
        elementBox.setConverter(CreationForm.identifiableFormat());
        ComboBox<Object> sideBox = new ComboBox<>();
        Runnable showElements = () -> {
            OverloadManagementSystem.Tripping.Type chosen = type.get();
            List<Identifiable<?>> elements = switch (chosen) {
                case SWITCH_TRIPPING -> new ArrayList<>(switches);
                case BRANCH_TRIPPING -> branches.stream().filter(branch -> !(branch instanceof ThreeWindingsTransformer)).toList();
                case THREE_WINDINGS_TRANSFORMER_TRIPPING -> branches.stream().filter(ThreeWindingsTransformer.class::isInstance).toList();
            };
            elementBox.getItems().setAll(elements);
            elementBox.setValue(elements.stream().findFirst().orElse(null));
            sideBox.getItems().setAll(switch (chosen) {
                case SWITCH_TRIPPING -> List.of();
                case BRANCH_TRIPPING -> List.<Object>of(TwoSides.values());
                case THREE_WINDINGS_TRANSFORMER_TRIPPING -> List.<Object>of(ThreeSides.values());
            });
            sideBox.setValue(sideBox.getItems().stream().findFirst().orElse(null));
        };
        ((ComboBox<?>) type.getEditor()).valueProperty().addListener((_, _, _) -> showElements.run());
        showElements.run();
        form.custom("edit.oms.trippingElement", elementBox, null, () -> null);
        form.custom("desktop.common.column.side", sideBox, null, () -> null);
        CreationForm.Field<Double> currentLimit = form.number("edit.oms.currentLimit", DetailForm.A, 1000, false);
        CreationForm.Field<Boolean> open = form.check("edit.oms.openAction", true);
        Button add = new Button(Messages.get("desktop.common.action.add"), new FontIcon("mdi2p-plus"));
        Button remove = new Button(Messages.get("desktop.common.action.remove"), new FontIcon("mdi2d-delete"));
        remove.disableProperty().bind(trippings.getSelectionModel().selectedItemProperty().isNull());
        add.setOnAction(_ -> {
            if (elementBox.getValue() == null || Double.isNaN(currentLimit.get())) {
                return;
            }
            trippings.getItems().add(new TrippingRow(type.get(), elementBox.getValue(), sideBox.getValue(),
                    "T" + (trippings.getItems().size() + 1), currentLimit.get(), open.get()));
        });
        remove.setOnAction(_ -> trippings.getItems().remove(trippings.getSelectionModel().getSelectedItem()));
        HBox buttons = new HBox(8, add, remove);
        buttons.setAlignment(Pos.CENTER_LEFT);
        form.custom("edit.oms.trippingActions", buttons, null, () -> null);
    }

    /**
     * Creates a voltage angle limit between two terminals.
     */
    public static Optional<Boolean> newVoltageAngleLimit(SceneModel sceneModel, Window owner, Network network) {
        CreationForm form = new CreationForm();
        CreationForm.Field<String> id = form.text("desktop.common.column.id", uniqueVoltageAngleLimitId(network), true);
        CreationForm.Field<Terminal> from = terminalField(form, "edit.angleLimit.from", sceneModel);
        CreationForm.Field<Terminal> to = terminalField(form, "edit.angleLimit.to", sceneModel);
        CreationForm.Field<Double> low = form.number("edit.angleLimit.low", DetailForm.DEGREE, Double.NaN, false);
        CreationForm.Field<Double> high = form.number("edit.angleLimit.high", DetailForm.DEGREE, Double.NaN, false);
        return CreateDialog.show(owner, Messages.get("edit.create.title", Messages.get("desktop.common.equipment.voltageAngleLimit")), form, () -> {
            String limitId = id.get();
            if (network.getVoltageAngleLimit(limitId) != null) {
                throw new CreationForm.FieldException(id.getEditor(), Messages.get("edit.create.idTaken", limitId));
            }
            VoltageAngleLimitAdder adder = network.newVoltageAngleLimit().setId(limitId).from(from.get()).to(to.get());
            if (!Double.isNaN(low.get())) {
                adder.setLowLimit(low.get());
            }
            if (!Double.isNaN(high.get())) {
                adder.setHighLimit(high.get());
            }
            return NetworkEdits.apply(sceneModel.getMainModel(), network, _ -> adder.add());
        });
    }

    private static String uniqueVoltageAngleLimitId(Network network) {
        int n = 1;
        while (network.getVoltageAngleLimit("VAL" + n) != null) {
            n++;
        }
        return "VAL" + n;
    }

    // the chosen terminal, picked with a TerminalPickerDialog
    private static CreationForm.Field<Terminal> terminalField(CreationForm form, String labelKey, SceneModel sceneModel) {
        Label label = new Label("-");
        Terminal[] terminal = {null};
        Button pick = new Button(null, new FontIcon("mdi2p-pencil"));
        pick.getStyleClass().add("icon-button");
        pick.setOnAction(_ -> TerminalPickerDialog.show(pick.getScene().getWindow(), sceneModel).ifPresent(picked -> {
            terminal[0] = picked;
            label.setText(picked.getConnectable().getNameOrId() + (picked.getSide() == null ? "" : " (" + picked.getSide() + ")"));
        }));
        HBox box = new HBox(4, pick, label);
        box.setAlignment(Pos.CENTER_LEFT);
        return form.custom(labelKey, new VBox(box), null, () -> {
            if (terminal[0] == null) {
                throw new CreationForm.FieldException(pick, Messages.get("desktop.common.editError.mandatory"));
            }
            return terminal[0];
        });
    }

    /**
     * Asks for confirmation, then removes {@code limit}; a failure is reported as an error notification.
     */
    public static void removeVoltageAngleLimit(MainModel mainModel, Window owner, VoltageAngleLimit limit) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle(Messages.get("desktop.common.action.remove"));
        String description = Messages.get("desktop.common.equipment.voltageAngleLimit") + " \"" + limit.getId() + "\"";
        alert.setHeaderText(Messages.get("edit.remove.confirm", description));
        if (alert.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return;
        }
        try {
            NetworkEdits.apply(mainModel, limit.getTerminalFrom().getVoltageLevel().getParentNetwork(), _ -> limit.remove());
        } catch (PowsyblException e) {
            mainModel.getNotificationsModel().add(Notification.createError(Instant.now(), "edit.remove.failed").withMessageArgs(description, e.getMessage()));
        }
    }
}
