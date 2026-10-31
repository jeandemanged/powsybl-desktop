/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.modification.topology.TopologyModificationUtils;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Connectable;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import com.powsybl.powsybldesktop.network.detail.DetailSections;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where a new connectable (or one side of it) is connected, as {@code CreateFeederBay} and
 * {@code CreateBranchFeederBays} take it: a voltage level, then a busbar section with the feeder's position order and
 * direction in a node breaker voltage level (its bay of switches is created), or a bus in a bus breaker one. The voltage
 * level is a {@link VoltageLevelChoice}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class ConnectionSection {

    private final VoltageLevelChoice voltageLevel;
    private final CreationForm.Field<Identifiable<?>> busOrBusbarSection;
    private final CreationForm.Field<Integer> positionOrder;
    private final CreationForm.Field<ConnectablePosition.Direction> direction;

    /**
     * @param title the section's title
     * @param initial the initial busbar section or bus, or null for the first one of the initial voltage level
     */
    ConnectionSection(CreationForm form, String title, List<VoltageLevel> voltageLevels, VoltageLevel initialVoltageLevel,
                      Identifiable<?> initial, ConnectablePosition.Direction defaultDirection) {
        form.title(title);
        voltageLevel = new VoltageLevelChoice(form, voltageLevels, initialVoltageLevel);
        busOrBusbarSection = form.choice("edit.create.connection.busOrBusbarSection", List.of(), CreationForm.identifiableFormat(), null, true);
        ComboBox<Identifiable<?>> busBox = comboBox(busOrBusbarSection);
        positionOrder = form.integer("edit.create.connection.positionOrder", 0)
                .visibleWhen(busBox.valueProperty().map(BusbarSection.class::isInstance).orElse(false));
        direction = form.choice("edit.create.connection.direction", DetailSections.values(ConnectablePosition.Direction.class),
                DetailSections.enumFormat(), defaultDirection, true)
                .visibleWhen(busBox.valueProperty().map(BusbarSection.class::isInstance).orElse(false));
        TextField orderField = (TextField) positionOrder.getEditor();
        orderField.setTooltip(new Tooltip(Messages.get("edit.create.connection.positionOrderHint")));
        busBox.valueProperty().addListener((_, _, newValue) -> {
            if (newValue instanceof BusbarSection busbarSection) {
                orderField.setText(String.valueOf(suggestedPositionOrder(busbarSection)));
            }
        });
        voltageLevel.valueProperty().addListener((_, _, newValue) -> showBusesOf(busBox, newValue, null));
        showBusesOf(busBox, initialVoltageLevel, initial);
    }

    @SuppressWarnings("unchecked")
    private static <T> ComboBox<T> comboBox(CreationForm.Field<T> field) {
        return (ComboBox<T>) field.getEditor();
    }

    private static void showBusesOf(ComboBox<Identifiable<?>> busBox, VoltageLevel voltageLevel, Identifiable<?> initial) {
        List<Identifiable<?>> buses = new ArrayList<>();
        if (voltageLevel != null) {
            if (voltageLevel.getTopologyKind() == TopologyKind.NODE_BREAKER) {
                voltageLevel.getNodeBreakerView().getBusbarSectionStream().forEach(buses::add);
            } else {
                voltageLevel.getBusBreakerView().getBusStream().forEach(buses::add);
            }
        }
        buses.sort(Comparator.comparing(Identifiable::getId));
        busBox.getItems().setAll(buses);
        busBox.setValue(initial != null && buses.contains(initial) ? initial : buses.stream().findFirst().orElse(null));
    }

    // at the end of the busbar section's feeders
    static int suggestedPositionOrder(BusbarSection busbarSection) {
        return FeederOrders.endOf(busbarSection);
    }

    VoltageLevel voltageLevel() {
        return voltageLevel.get();
    }

    String busOrBusbarSectionId() {
        return busOrBusbarSection.get().getId();
    }

    boolean isNodeBreaker() {
        return busOrBusbarSection.get() instanceof BusbarSection;
    }

    /**
     * Reserves the position orders of {@code count} feeders at the given position order, fitted as
     * {@link FeederOrders#reserve} does; only for a node breaker voltage level.
     */
    FeederOrders.Slot reserve(FeederOrders orders, int count) {
        int order = positionOrder.get();
        if (order < 0) {
            throw new CreationForm.FieldException(positionOrder.getEditor(), Messages.get("edit.create.notNegative"));
        }
        return orders.reserve((BusbarSection) busOrBusbarSection.get(), order, count);
    }

    /**
     * Whether a feeder position is given to {@code connectable} in {@code voltageLevel}, as {@code CreateFeederBay}
     * decides: not where the other connectables have none (e.g. imported without).
     */
    static boolean usesPositions(VoltageLevel voltageLevel, Connectable<?> connectable) {
        return !TopologyModificationUtils.getFeederPositions(voltageLevel).isEmpty()
                || voltageLevel.getConnectableStream().allMatch(other -> other instanceof BusbarSection || other == connectable);
    }

    ConnectablePosition.Direction direction() {
        return direction.get();
    }
}
