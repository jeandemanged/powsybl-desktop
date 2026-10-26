/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.Battery;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LccConverterStation;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.VscConverterStation;
import com.powsybl.powsybldesktop.MainApplication;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.window.SeparateWindows;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Opens an equipment's detail view, non-modal, as a tab of a separate window (see {@link SeparateWindows}): one per
 * equipment, brought to front if already open, closed with the other views bound to the network. Every equipment type
 * has its {@link AbstractEquipmentDetailController} subclass, all on {@code equipment-detail-view.fxml}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class EquipmentDetails {

    private static final double WIDTH = 1050;
    private static final double HEIGHT = 760;

    private record Kind<T extends Identifiable<T>>(Class<T> type, String typeKey, Supplier<AbstractEquipmentDetailController<T>> controller) {
    }

    private static final List<Kind<?>> KINDS = List.of(
            new Kind<>(Generator.class, "desktop.common.equipment.generator", GeneratorDetailController::new),
            new Kind<>(Battery.class, "desktop.common.equipment.battery", BatteryDetailController::new),
            new Kind<>(Load.class, "desktop.common.equipment.load", LoadDetailController::new),
            new Kind<>(ShuntCompensator.class, "desktop.common.equipment.shuntCompensator", ShuntCompensatorDetailController::new),
            new Kind<>(StaticVarCompensator.class, "desktop.common.equipment.staticVarCompensator", StaticVarCompensatorDetailController::new),
            new Kind<>(VscConverterStation.class, "desktop.common.equipment.vscConverterStation", VscConverterStationDetailController::new),
            new Kind<>(LccConverterStation.class, "desktop.common.equipment.lccConverterStation", LccConverterStationDetailController::new),
            new Kind<>(HvdcLine.class, "desktop.common.equipment.hvdcLine", HvdcLineDetailController::new),
            new Kind<>(Line.class, "desktop.common.equipment.line", LineDetailController::new),
            new Kind<>(TieLine.class, "desktop.common.equipment.tieLine", TieLineDetailController::new),
            new Kind<>(BoundaryLine.class, "desktop.common.equipment.boundaryLine", BoundaryLineDetailController::new),
            new Kind<>(TwoWindingsTransformer.class, "desktop.common.equipment.transformer", TwoWindingsTransformerDetailController::new),
            new Kind<>(ThreeWindingsTransformer.class, "desktop.common.equipment.transformer", ThreeWindingsTransformerDetailController::new),
            new Kind<>(BusbarSection.class, "desktop.common.equipment.busbarSection", BusbarSectionDetailController::new),
            new Kind<>(Switch.class, "desktop.common.equipment.switch", SwitchDetailController::new),
            new Kind<>(Substation.class, "desktop.common.equipment.substation", SubstationDetailController::new),
            new Kind<>(VoltageLevel.class, "desktop.common.equipment.voltageLevel", VoltageLevelDetailController::new));

    private EquipmentDetails() {
    }

    public static boolean supports(Identifiable<?> equipment) {
        return kind(equipment).isPresent();
    }

    /**
     * A no-op where detail views can't be opened (tests), or for an equipment without detail view.
     */
    public static void open(SceneModel sceneModel, Identifiable<?> equipment) {
        Optional<Kind<?>> kind = kind(equipment);
        if (kind.isEmpty()) {
            return;
        }
        sceneModel.getDetailWindows().ifPresent(windows -> windows.open("detail:" + equipment.getId(),
                () -> Messages.get("detail.title", Messages.get(kind.get().typeKey()), equipment.getNameOrId()),
                scene -> {
                    SeparateWindows.View view = load(equipment, scene);
                    return new SeparateWindows.View(view.root(), view.controller(), WIDTH, HEIGHT);
                }));
    }

    /**
     * Loads {@code equipment}'s detail view for {@code sceneModel}.
     */
    public static SeparateWindows.View load(Identifiable<?> equipment, SceneModel sceneModel) {
        return load(kind(equipment).orElseThrow(), equipment, sceneModel);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Identifiable<T>> SeparateWindows.View load(Kind<T> kind, Identifiable<?> equipment, SceneModel sceneModel) {
        AbstractEquipmentDetailController<T> controller = kind.controller().get();
        FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource("network/detail/equipment-detail-view.fxml"), Messages.bundle());
        loader.setController(controller);
        try {
            Parent root = loader.load();
            controller.setEquipment((T) equipment, kind.typeKey());
            controller.setSceneModel(sceneModel);
            return new SeparateWindows.View(root, controller);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<Kind<?>> kind(Identifiable<?> equipment) {
        return equipment == null ? Optional.empty() : KINDS.stream().filter(kind -> kind.type().isInstance(equipment)).findFirst();
    }
}
