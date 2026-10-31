/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.modification.topology.CreateFeederBayBuilder;
import com.powsybl.iidm.network.EnergySource;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.InjectionAdder;
import com.powsybl.iidm.network.LoadType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensatorAdder;
import com.powsybl.iidm.network.ShuntCompensatorModelType;
import com.powsybl.iidm.network.ShuntCompensatorNonLinearModelAdder;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.iidm.network.regulation.VoltageRegulationHolderAdder;

import java.util.List;

/**
 * Creating injections, with their bay in a node breaker voltage level.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class Injections {

    public enum Kind {
        GENERATOR("desktop.common.equipment.generator", "_G"),
        BATTERY("desktop.common.equipment.battery", "_BAT"),
        LOAD("desktop.common.equipment.load", "_LD"),
        SHUNT_COMPENSATOR("desktop.common.equipment.shuntCompensator", "_SH"),
        STATIC_VAR_COMPENSATOR("desktop.common.equipment.staticVarCompensator", "_SVC"),
        VSC_CONVERTER_STATION("desktop.common.equipment.vscConverterStation", "_VSC"),
        LCC_CONVERTER_STATION("desktop.common.equipment.lccConverterStation", "_LCC"),
        BOUNDARY_LINE("desktop.common.equipment.boundaryLine", "_BL"),
        GROUND("desktop.common.equipment.ground", "_GND");

        private final String key;
        private final String idSuffix;

        Kind(String key, String idSuffix) {
            this.key = key;
            this.idSuffix = idSuffix;
        }

        public String key() {
            return key;
        }

        // the suffix of the ids proposed, after the voltage level's id
        public String idSuffix() {
            return idSuffix;
        }
    }

    /**
     * Local targets and an optional voltage regulation, as IIDM holds them (see
     * {@link com.powsybl.powsybldesktop.network.VoltageRegulations}): a local regulation, of the local targets (its own
     * target value being for a remote terminal, set in the detail view).
     *
     * @param mode    no voltage regulation if null
     * @param targetV the voltage level's nominal voltage if NaN
     */
    public record Regulation(RegulationMode mode, boolean regulating, double targetV, double targetQ) {

        <A extends VoltageRegulationHolderAdder<A>> A apply(VoltageLevel voltageLevel, A adder) {
            adder.setLocalTargetV(Double.isNaN(targetV) ? voltageLevel.getNominalV() : targetV).setLocalTargetQ(targetQ);
            if (mode != null) {
                adder.newVoltageRegulation()
                        .withMode(mode)
                        .withRegulating(regulating)
                        .add();
            }
            return adder;
        }
    }

    /**
     * The values of an injection of a kind.
     */
    public sealed interface Spec permits Load, Generator, Battery, ShuntCompensator, StaticVarCompensator, VscConverterStation,
            LccConverterStation, BoundaryLine, Ground {
    }

    public record Load(LoadType type, double p0, double q0) implements Spec {
    }

    public record Generator(EnergySource energySource, double minP, double maxP, double targetP, Regulation regulation) implements Spec {
    }

    public record Battery(double minP, double maxP, double targetP, Regulation regulation) implements Spec {
    }

    /**
     * @param bPerSection          linear model only
     * @param gPerSection          linear model only
     * @param maximumSectionCount  linear model only
     * @param sectionSusceptances  non linear model only: each section's B, its G being 0
     */
    public record ShuntCompensator(ShuntCompensatorModelType model, double bPerSection, double gPerSection, int maximumSectionCount,
                                   List<Double> sectionSusceptances, int sectionCount, Regulation regulation) implements Spec {
    }

    public record StaticVarCompensator(double bMin, double bMax, Regulation regulation) implements Spec {
    }

    public record VscConverterStation(double lossFactor, Regulation regulation) implements Spec {
    }

    public record LccConverterStation(double lossFactor, double powerFactor) implements Spec {
    }

    public record BoundaryLine(double r, double x, double g, double b, double p0, double q0, String pairingKey) implements Spec {
    }

    public record Ground() implements Spec {
    }

    private Injections() {
    }

    /**
     * Creates the injection of {@code spec}, connected at {@code connection}, with its bay in a node breaker voltage level.
     */
    public static Identifiable<?> add(Network network, String id, String name, Spec spec, Connection connection) {
        CreateFeederBayBuilder builder = new CreateFeederBayBuilder()
                .withInjectionAdder(adder(connection.voltageLevel(), id, name, spec))
                .withBusOrBusbarSectionId(connection.busOrBusbarSectionId())
                .withLogOrThrowIfIncorrectPositionOrder(true);
        FeederOrders.reserving(orders -> {
            if (connection.isNodeBreaker()) {
                builder.withInjectionPositionOrder(connection.reserve(orders, 1).order()).withInjectionDirection(connection.direction());
            }
            NetworkEdits.apply(network, builder.build());
        });
        return network.getIdentifiable(id);
    }

    private static InjectionAdder<?, ?> adder(VoltageLevel vl, String id, String name, Spec spec) {
        return switch (spec) {
            case Load load -> vl.newLoad().setId(id).setName(name).setLoadType(load.type()).setP0(load.p0()).setQ0(load.q0());
            case Generator generator -> generator.regulation().apply(vl, vl.newGenerator().setId(id).setName(name)
                    .setEnergySource(generator.energySource())
                    .setMinP(generator.minP())
                    .setMaxP(generator.maxP())
                    .setTargetP(generator.targetP()));
            case Battery battery -> battery.regulation().apply(vl, vl.newBattery().setId(id).setName(name)
                    .setMinP(battery.minP())
                    .setMaxP(battery.maxP())
                    .setTargetP(battery.targetP()));
            case ShuntCompensator shunt -> shuntAdder(vl, id, name, shunt);
            case StaticVarCompensator svc -> svc.regulation().apply(vl, vl.newStaticVarCompensator().setId(id).setName(name)
                    .setBmin(svc.bMin()).setBmax(svc.bMax()));
            case VscConverterStation vsc -> vsc.regulation().apply(vl, vl.newVscConverterStation().setId(id).setName(name)
                    .setLossFactor((float) vsc.lossFactor()));
            case LccConverterStation lcc -> vl.newLccConverterStation().setId(id).setName(name)
                    .setLossFactor((float) lcc.lossFactor()).setPowerFactor((float) lcc.powerFactor());
            case BoundaryLine line -> vl.newBoundaryLine().setId(id).setName(name).setR(line.r()).setX(line.x()).setG(line.g()).setB(line.b())
                    .setP0(line.p0()).setQ0(line.q0()).setPairingKey(line.pairingKey());
            case Ground _ -> vl.newGround().setId(id).setName(name);
        };
    }

    private static ShuntCompensatorAdder shuntAdder(VoltageLevel vl, String id, String name, ShuntCompensator shunt) {
        ShuntCompensatorAdder adder = vl.newShuntCompensator().setId(id).setName(name).setSectionCount(shunt.sectionCount());
        if (shunt.model() == ShuntCompensatorModelType.LINEAR) {
            adder.newLinearModel().setBPerSection(shunt.bPerSection()).setGPerSection(shunt.gPerSection())
                    .setMaximumSectionCount(shunt.maximumSectionCount()).add();
        } else {
            ShuntCompensatorNonLinearModelAdder nonLinear = adder.newNonLinearModel();
            shunt.sectionSusceptances().forEach(b -> nonLinear.beginSection().setB(b).setG(0).endSection());
            nonLinear.add();
        }
        return shunt.regulation().apply(vl, adder);
    }
}
