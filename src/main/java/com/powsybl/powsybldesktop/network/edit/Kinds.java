/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LccConverterStation;
import com.powsybl.powsybldesktop.utils.Messages;

/**
 * Display names of the network object types, for the edit dialogs and menus.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class Kinds {

    private Kinds() {
    }

    public static String name(Identifiable<?> identifiable) {
        String key = switch (identifiable.getType()) {
            case NETWORK -> "desktop.common.equipment.network";
            case SUBSTATION -> "desktop.common.equipment.substation";
            case VOLTAGE_LEVEL -> "desktop.common.equipment.voltageLevel";
            case AREA -> "desktop.common.equipment.area";
            case HVDC_LINE -> "desktop.common.equipment.hvdcLine";
            case BUS -> "desktop.common.equipment.bus";
            case SWITCH -> "desktop.common.equipment.switch";
            case BUSBAR_SECTION -> "desktop.common.equipment.busbarSection";
            case LINE -> "desktop.common.equipment.line";
            case TIE_LINE -> "desktop.common.equipment.tieLine";
            case TWO_WINDINGS_TRANSFORMER -> "desktop.common.equipment.twoWindingsTransformer";
            case THREE_WINDINGS_TRANSFORMER -> "desktop.common.equipment.threeWindingsTransformer";
            case GENERATOR -> "desktop.common.equipment.generator";
            case BATTERY -> "desktop.common.equipment.battery";
            case LOAD -> "desktop.common.equipment.load";
            case SHUNT_COMPENSATOR -> "desktop.common.equipment.shuntCompensator";
            case BOUNDARY_LINE -> "desktop.common.equipment.boundaryLine";
            case STATIC_VAR_COMPENSATOR -> "desktop.common.equipment.staticVarCompensator";
            case HVDC_CONVERTER_STATION -> identifiable instanceof LccConverterStation
                    ? "desktop.common.equipment.lccConverterStation" : "desktop.common.equipment.vscConverterStation";
            case OVERLOAD_MANAGEMENT_SYSTEM -> "desktop.common.equipment.overloadManagementSystem";
            case GROUND -> "desktop.common.equipment.ground";
            default -> null;
        };
        return key == null ? identifiable.getType().name() : Messages.get(key);
    }

    /**
     * {@code Kind "name"}, e.g. {@code Load "LD1"}.
     */
    public static String describe(Identifiable<?> identifiable) {
        return name(identifiable) + " \"" + identifiable.getNameOrId() + "\"";
    }
}
