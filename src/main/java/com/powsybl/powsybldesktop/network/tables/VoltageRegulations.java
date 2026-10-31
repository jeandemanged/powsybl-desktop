/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.iidm.network.regulation.VoltageRegulation;
import com.powsybl.iidm.network.regulation.VoltageRegulationHolder;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.util.StringConverter;

import java.util.Arrays;

/**
 * Table editing on top of the powsybl-core 7.4 {@link VoltageRegulation} model, which replaced the per-equipment
 * regulation setters (setVoltageRegulatorOn, setTargetV, setRegulationMode...). An equipment may have no
 * {@link VoltageRegulation} yet: editing one of its attributes then creates a non-regulating one in the equipment's
 * default mode, as the deprecated setters did.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class VoltageRegulations {

    public static final StringConverter<RegulationMode> MODE_FORMAT = new StringConverter<>() {
        @Override
        public String toString(RegulationMode value) {
            return value == null ? "" : modeLabel(value);
        }

        @Override
        public RegulationMode fromString(String text) {
            return Arrays.stream(RegulationMode.values())
                    .filter(mode -> toString(mode).equals(text))
                    .findFirst().orElseThrow();
        }
    };

    private VoltageRegulations() {
    }

    private static String modeLabel(RegulationMode mode) {
        return Messages.get(switch (mode) {
            case VOLTAGE -> "desktop.common.regulationMode.voltage";
            case REACTIVE_POWER -> "desktop.common.regulationMode.reactivePower";
            case VOLTAGE_PER_REACTIVE_POWER -> "desktop.common.regulationMode.voltagePerReactivePower";
        });
    }

    static RegulationMode mode(VoltageRegulationHolder<?> holder) {
        VoltageRegulation regulation = holder.getVoltageRegulation();
        return regulation == null ? null : regulation.getMode();
    }

    public static void setMode(VoltageRegulationHolder<?> holder, RegulationMode mode) {
        VoltageRegulation regulation = holder.getVoltageRegulation();
        if (regulation != null) {
            regulation.setMode(mode);
        } else {
            holder.newVoltageRegulation().withMode(mode).withRegulating(false).build();
        }
    }

    public static void setRegulating(VoltageRegulationHolder<?> holder, RegulationMode defaultMode, boolean regulating) {
        VoltageRegulation regulation = holder.getVoltageRegulation();
        if (regulation != null) {
            regulation.setRegulating(regulating);
        } else if (regulating) {
            holder.newVoltageRegulation().withMode(defaultMode).withRegulating(true).build();
        }
    }

    static double targetValue(VoltageRegulationHolder<?> holder) {
        VoltageRegulation regulation = holder.getVoltageRegulation();
        return regulation == null ? Double.NaN : regulation.getTargetValue();
    }

    public static void setTargetValue(VoltageRegulationHolder<?> holder, RegulationMode defaultMode, double targetValue) {
        VoltageRegulation regulation = holder.getVoltageRegulation();
        if (regulation != null) {
            regulation.setTargetValue(targetValue);
        } else {
            holder.newVoltageRegulation().withMode(defaultMode).withRegulating(false).withTargetValue(targetValue).build();
        }
    }

    static double targetDeadband(VoltageRegulationHolder<?> holder) {
        VoltageRegulation regulation = holder.getVoltageRegulation();
        return regulation == null ? Double.NaN : regulation.getTargetDeadband();
    }

    public static void setTargetDeadband(VoltageRegulationHolder<?> holder, RegulationMode defaultMode, double targetDeadband) {
        VoltageRegulation regulation = holder.getVoltageRegulation();
        if (regulation != null) {
            regulation.setTargetDeadband(targetDeadband);
        } else {
            holder.newVoltageRegulation().withMode(defaultMode).withRegulating(false).withTargetDeadband(targetDeadband).build();
        }
    }

    /**
     * Writes the value {@link VoltageRegulationHolder#getRegulatingTargetV()} reads: the remote target when
     * regulating a remote terminal in a voltage mode, the local target otherwise.
     */
    static void setRegulatingTargetV(VoltageRegulationHolder<?> holder, double targetV) {
        if (holder.hasRegulatingTerminal()
                && (holder.isWithMode(RegulationMode.VOLTAGE) || holder.isWithMode(RegulationMode.VOLTAGE_PER_REACTIVE_POWER))) {
            holder.getVoltageRegulation().setTargetValue(targetV);
        } else {
            holder.setLocalTargetV(targetV);
        }
    }

    /**
     * The remote target when regulating a remote terminal's reactive power, the local target otherwise, each in its
     * own sign convention: load convention for the remote target, the holder's for the local one. Unlike
     * {@link VoltageRegulationHolder#getRegulatingTargetQ()}, a remote target isn't converted to the holder's
     * convention.
     */
    static double regulatingTargetQ(VoltageRegulationHolder<?> holder) {
        return remoteReactivePowerRegulation(holder) ? holder.getVoltageRegulation().getTargetValue() : holder.getLocalTargetQ();
    }

    static void setRegulatingTargetQ(VoltageRegulationHolder<?> holder, double targetQ) {
        if (remoteReactivePowerRegulation(holder)) {
            holder.getVoltageRegulation().setTargetValue(targetQ);
        } else {
            holder.setLocalTargetQ(targetQ);
        }
    }

    private static boolean remoteReactivePowerRegulation(VoltageRegulationHolder<?> holder) {
        return holder.hasRegulatingTerminal() && holder.isWithMode(RegulationMode.REACTIVE_POWER);
    }
}
