/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.VscConverterStation;
import com.powsybl.powsybldesktop.utils.Messages;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * An element to pick: its id, displayed name and kind.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record ElementCandidate(String id, String name, String kind) {

    /**
     * The candidates of {@code identifiables}, with their kind.
     */
    public static List<ElementCandidate> of(Stream<? extends Identifiable<?>> identifiables) {
        return identifiables.map(identifiable -> new ElementCandidate(identifiable.getId(), identifiable.getNameOrId(), kind(identifiable))).toList();
    }

    private static String kind(Identifiable<?> identifiable) {
        String key = switch (identifiable.getType()) {
            case LINE -> "line";
            case TIE_LINE -> "tieLine";
            case TWO_WINDINGS_TRANSFORMER -> "twoWindingsTransformer";
            case THREE_WINDINGS_TRANSFORMER -> "threeWindingsTransformer";
            case BOUNDARY_LINE -> "boundaryLine";
            case GENERATOR -> "generator";
            case LOAD -> "load";
            case BATTERY -> "battery";
            case SHUNT_COMPENSATOR -> "shuntCompensator";
            case STATIC_VAR_COMPENSATOR -> "staticVarCompensator";
            case HVDC_CONVERTER_STATION -> identifiable instanceof VscConverterStation ? "vscConverterStation" : "lccConverterStation";
            case HVDC_LINE -> "hvdcLine";
            case BUS -> "bus";
            case BUSBAR_SECTION -> "busbarSection";
            case SWITCH -> "switch";
            default -> null;
        };
        return key == null ? Objects.toString(identifiable.getType()) : Messages.get("desktop.common.equipment." + key);
    }
}
