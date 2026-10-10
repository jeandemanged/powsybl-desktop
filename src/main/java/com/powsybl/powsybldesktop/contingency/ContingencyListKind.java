/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.contingency;

import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.contingency.list.DefaultContingencyList;
import com.powsybl.contingency.list.HvdcLineCriterionContingencyList;
import com.powsybl.contingency.list.InjectionCriterionContingencyList;
import com.powsybl.contingency.list.LineCriterionContingencyList;
import com.powsybl.contingency.list.ThreeWindingsTransformerCriterionContingencyList;
import com.powsybl.contingency.list.TieLineCriterionContingencyList;
import com.powsybl.contingency.list.TwoWindingsTransformerCriterionContingencyList;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.powsybldesktop.utils.Messages;

import java.util.List;
import java.util.Optional;

/**
 * The contingency list types this app can create/edit, one entry per creatable {@link ContingencyList}
 * concrete class. Drives both the "Add" menu and {@link CriterionListFormController}'s field visibility -
 * a single source of truth for the country/voltage criterion arity of each of the 6 equipment-criterion types,
 * avoiding 6 near-duplicate form controllers for what is structurally the same shape.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public enum ContingencyListKind {
    DEFAULT("contingencies.kind.default", DefaultContingencyList.TYPE, 0, 0, false),
    LINE_CRITERION("contingencies.kind.lineCriterion", LineCriterionContingencyList.TYPE, 2, 2, false),
    TIE_LINE_CRITERION("contingencies.kind.tieLineCriterion", TieLineCriterionContingencyList.TYPE, 2, 1, false),
    INJECTION_CRITERION("contingencies.kind.injectionCriterion", InjectionCriterionContingencyList.TYPE, 1, 1, true),
    TWO_WINDINGS_TRANSFORMER_CRITERION("contingencies.kind.twoWindingsTransformerCriterion",
            TwoWindingsTransformerCriterionContingencyList.TYPE, 1, 2, false),
    THREE_WINDINGS_TRANSFORMER_CRITERION("contingencies.kind.threeWindingsTransformerCriterion",
            ThreeWindingsTransformerCriterionContingencyList.TYPE, 1, 3, false),
    HVDC_LINE_CRITERION("contingencies.kind.hvdcCriterion", HvdcLineCriterionContingencyList.TYPE, 2, 2, false);

    // Injection-capable equipment types offered by the identifiable type picker (INJECTION_CRITERION only).
    public static final List<IdentifiableType> INJECTION_TYPES = List.of(
            IdentifiableType.GENERATOR, IdentifiableType.LOAD, IdentifiableType.BATTERY,
            IdentifiableType.SHUNT_COMPENSATOR, IdentifiableType.STATIC_VAR_COMPENSATOR,
            IdentifiableType.BUSBAR_SECTION, IdentifiableType.BOUNDARY_LINE,
            IdentifiableType.HVDC_CONVERTER_STATION, IdentifiableType.BUS);

    private final String labelKey;
    private final String type;
    private final int countryArity;
    private final int voltageArity;
    private final boolean hasIdentifiableTypePicker;

    ContingencyListKind(String labelKey, String type, int countryArity, int voltageArity, boolean hasIdentifiableTypePicker) {
        this.labelKey = labelKey;
        this.type = type;
        this.countryArity = countryArity;
        this.voltageArity = voltageArity;
        this.hasIdentifiableTypePicker = hasIdentifiableTypePicker;
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

    public boolean hasIdentifiableTypePicker() {
        return hasIdentifiableTypePicker;
    }

    // All-null/empty criteria: matches every equipment of this kind's type, per
    // AbstractEquipmentCriterionContingencyList.getContingencies null-checking each criterion before filtering.
    public ContingencyList createDefault(String name) {
        return switch (this) {
            case DEFAULT -> new DefaultContingencyList(name, List.of());
            case LINE_CRITERION -> new LineCriterionContingencyList(name, null, null, List.of(), null);
            case TIE_LINE_CRITERION -> new TieLineCriterionContingencyList(name, null, null, List.of(), null);
            case INJECTION_CRITERION -> new InjectionCriterionContingencyList(name, IdentifiableType.GENERATOR, null, null, List.of(), null);
            case TWO_WINDINGS_TRANSFORMER_CRITERION -> new TwoWindingsTransformerCriterionContingencyList(name, null, null, List.of(), null);
            case THREE_WINDINGS_TRANSFORMER_CRITERION -> new ThreeWindingsTransformerCriterionContingencyList(name, null, null, List.of(), null);
            case HVDC_LINE_CRITERION -> new HvdcLineCriterionContingencyList(name, null, null, List.of(), null);
        };
    }

    public static Optional<ContingencyListKind> of(ContingencyList list) {
        for (ContingencyListKind kind : values()) {
            if (kind.type.equals(list.getType())) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    // Falls back to the raw PowSyBl type string for a list this app doesn't know how to edit
    // (e.g. an imported IdentifierContingencyList), so it still shows something meaningful.
    public static String labelFor(ContingencyList list) {
        return of(list).map(ContingencyListKind::label).orElse(list.getType());
    }
}
