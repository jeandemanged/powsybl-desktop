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
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.BusbarSectionPosition;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import org.apache.commons.lang3.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Position orders of the feeders about to be created, made to fit: an order out of its busbar section's range is
 * brought back to the nearest end of the section, and when it is taken, every feeder of the voltage level from it on
 * moves up to make room - so a feeder is inserted anywhere, sections keeping their feeders in order.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class FeederOrders {

    /**
     * Consecutive orders reserved for feeders not created yet, moving up with the others when a later reservation
     * makes room before them.
     */
    static final class Slot {
        private final VoltageLevel voltageLevel;
        private final int count;
        private int order;

        private Slot(VoltageLevel voltageLevel, int order, int count) {
            this.voltageLevel = voltageLevel;
            this.order = order;
            this.count = count;
        }

        int order() {
            return order;
        }
    }

    private record Shift(List<ConnectablePosition.Feeder> feeders, int count) {
    }

    private final List<Slot> slots = new ArrayList<>();
    private final List<Shift> shifts = new ArrayList<>();

    private FeederOrders() {
    }

    /**
     * Runs {@code change}, which reserves the orders of the feeders it creates, moving back the feeders moved for
     * them if it fails.
     */
    static void reserving(Consumer<FeederOrders> change) {
        FeederOrders orders = new FeederOrders();
        boolean done = false;
        try {
            change.accept(orders);
            done = true;
        } finally {
            if (!done) {
                orders.rollback();
            }
        }
    }

    /**
     * Reserves {@code count} consecutive orders for feeders on {@code busbarSection}, from {@code requested} fitted
     * into the section's range, moving up the feeders already there.
     */
    Slot reserve(BusbarSection busbarSection, int requested, int count) {
        VoltageLevel voltageLevel = busbarSection.getTerminal().getVoltageLevel();
        Set<Integer> taken = taken(voltageLevel);
        int order = requested;
        Optional<Range<Integer>> range = positionRange(busbarSection);
        if (range.isPresent()) {
            int min = range.get().getMinimum();
            order = Math.clamp(requested, min, Math.max(min, endOf(busbarSection, taken)));
        }
        int from = order;
        if (IntStream.range(from, from + count).anyMatch(taken::contains)) {
            shift(voltageLevel, from, count);
        }
        Slot slot = new Slot(voltageLevel, order, count);
        slots.add(slot);
        return slot;
    }

    /**
     * The order right after {@code busbarSection}'s feeders: the first free one, or else the first of the next
     * section's feeders, which would move up.
     */
    static int endOf(BusbarSection busbarSection) {
        return endOf(busbarSection, TopologyModificationUtils.getFeederPositions(busbarSection.getTerminal().getVoltageLevel()));
    }

    private static int endOf(BusbarSection busbarSection, Set<Integer> taken) {
        if (hasSections(busbarSection)) {
            Optional<Range<Integer>> after = TopologyModificationUtils.getUnusedOrderPositionsAfter(busbarSection);
            if (after.isPresent()) {
                return after.get().getMinimum();
            }
            Optional<Range<Integer>> range = TopologyModificationUtils.getPositionRange(busbarSection);
            if (range.isPresent() && range.get().getMaximum() != Integer.MAX_VALUE) {
                return range.get().getMaximum() + 1;
            }
        }
        return taken.stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
    }

    // empty when the sections' feeders are not in order, as imported: then the order is kept as given
    private static Optional<Range<Integer>> positionRange(BusbarSection busbarSection) {
        return hasSections(busbarSection) ? TopologyModificationUtils.getPositionRange(busbarSection) : Optional.empty();
    }

    // the section ranges are computed from every busbar section's position, failing on any without
    private static boolean hasSections(BusbarSection busbarSection) {
        return busbarSection.getTerminal().getVoltageLevel().getNodeBreakerView().getBusbarSectionStream()
                .allMatch(other -> other.getExtension(BusbarSectionPosition.class) != null);
    }

    private Set<Integer> taken(VoltageLevel voltageLevel) {
        Set<Integer> taken = TopologyModificationUtils.getFeederPositions(voltageLevel);
        slots.stream().filter(slot -> slot.voltageLevel == voltageLevel)
                .forEach(slot -> IntStream.range(slot.order, slot.order + slot.count).forEach(taken::add));
        return taken;
    }

    private void shift(VoltageLevel voltageLevel, int from, int count) {
        List<ConnectablePosition.Feeder> moved = TopologyModificationUtils.getFeedersByConnectable(voltageLevel).values().stream()
                .flatMap(List::stream)
                .filter(feeder -> feeder.getOrder().filter(order -> order >= from).isPresent())
                .toList();
        moved.forEach(feeder -> feeder.setOrder(feeder.getOrder().orElseThrow() + count));
        slots.stream().filter(slot -> slot.voltageLevel == voltageLevel && slot.order >= from).forEach(slot -> slot.order += count);
        shifts.add(new Shift(moved, count));
    }

    private void rollback() {
        for (Shift shift : shifts.reversed()) {
            shift.feeders().forEach(feeder -> feeder.setOrder(feeder.getOrder().orElseThrow() - shift.count()));
        }
    }
}
