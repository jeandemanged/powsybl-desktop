/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.limitscaling;

import com.powsybl.iidm.criteria.AtLeastOneNominalVoltageCriterion;
import com.powsybl.iidm.criteria.BoundaryLineCriterion;
import com.powsybl.iidm.criteria.IdentifiableCriterion;
import com.powsybl.iidm.criteria.LineCriterion;
import com.powsybl.iidm.criteria.NetworkElementCriterion;
import com.powsybl.iidm.criteria.NetworkElementIdListCriterion;
import com.powsybl.iidm.criteria.ThreeWindingsTransformerCriterion;
import com.powsybl.iidm.criteria.TieLineCriterion;
import com.powsybl.iidm.criteria.TwoWindingsTransformerCriterion;
import com.powsybl.iidm.criteria.VoltageInterval;
import com.powsybl.powsybldesktop.utils.Messages;

import java.util.Set;

/**
 * The equipment criteria a limit scaling can hold, with the number of country and nominal voltage sides their
 * editor shows. {@link #ANY_EQUIPMENT}'s are "at least one side".
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public enum CriterionKind {
    ANY_EQUIPMENT("limitScalings.criterion.anyEquipment", 1, 1),
    LINE("desktop.common.equipment.line", 2, 2),
    TIE_LINE("desktop.common.equipment.tieLine", 2, 2),
    TWO_WINDINGS_TRANSFORMER("desktop.common.equipment.twoWindingsTransformer", 1, 2),
    THREE_WINDINGS_TRANSFORMER("desktop.common.equipment.threeWindingsTransformer", 1, 3),
    BOUNDARY_LINE("desktop.common.equipment.boundaryLine", 1, 1),
    ID_LIST("limitScalings.criterion.idList", 0, 0);

    private final String labelKey;
    private final int countryArity;
    private final int voltageArity;

    CriterionKind(String labelKey, int countryArity, int voltageArity) {
        this.labelKey = labelKey;
        this.countryArity = countryArity;
        this.voltageArity = voltageArity;
    }

    public String label() {
        return Messages.get(labelKey);
    }

    public int countryArity() {
        return countryArity;
    }

    public int voltageArity() {
        return voltageArity;
    }

    // matches every equipment of the kind, except ID_LIST which matches none. An IdentifiableCriterion needs a country
    // or a nominal voltage criterion: any nominal voltage, which Open Load Flow supports, unlike countries
    public NetworkElementCriterion createDefault() {
        return switch (this) {
            case ANY_EQUIPMENT -> new IdentifiableCriterion(new AtLeastOneNominalVoltageCriterion(VoltageInterval.greaterThan(0, true)));
            case LINE -> new LineCriterion(null, null);
            case TIE_LINE -> new TieLineCriterion(null, null);
            case TWO_WINDINGS_TRANSFORMER -> new TwoWindingsTransformerCriterion(null, null);
            case THREE_WINDINGS_TRANSFORMER -> new ThreeWindingsTransformerCriterion(null, null);
            case BOUNDARY_LINE -> new BoundaryLineCriterion(null, null);
            case ID_LIST -> new NetworkElementIdListCriterion(Set.of());
        };
    }

    public static CriterionKind of(NetworkElementCriterion criterion) {
        return switch (criterion) {
            case IdentifiableCriterion _ -> ANY_EQUIPMENT;
            case LineCriterion _ -> LINE;
            case TieLineCriterion _ -> TIE_LINE;
            case TwoWindingsTransformerCriterion _ -> TWO_WINDINGS_TRANSFORMER;
            case ThreeWindingsTransformerCriterion _ -> THREE_WINDINGS_TRANSFORMER;
            case BoundaryLineCriterion _ -> BOUNDARY_LINE;
            case NetworkElementIdListCriterion _ -> ID_LIST;
            default -> throw new IllegalArgumentException("Unsupported criterion " + criterion.getNetworkElementCriterionType());
        };
    }
}
