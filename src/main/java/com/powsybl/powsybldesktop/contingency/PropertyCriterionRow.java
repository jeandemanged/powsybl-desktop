/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.contingency;

import com.powsybl.iidm.criteria.PropertyCriterion;
import com.powsybl.iidm.criteria.PropertyCriterion.EquipmentToCheck;
import com.powsybl.iidm.criteria.PropertyCriterion.SideToCheck;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Editable row backing {@link CriterionListFormController}'s property criteria table - {@code values} is
 * comma-separated text rather than a {@code List<String>}, since a plain editable text cell is simplest for
 * this internal tool's free-form value list.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class PropertyCriterionRow {

    private final SimpleStringProperty key = new SimpleStringProperty("");
    private final SimpleStringProperty values = new SimpleStringProperty("");
    private final SimpleObjectProperty<EquipmentToCheck> equipmentToCheck = new SimpleObjectProperty<>(EquipmentToCheck.SELF);
    private final SimpleObjectProperty<SideToCheck> sideToCheck = new SimpleObjectProperty<>();

    public PropertyCriterionRow() {
    }

    PropertyCriterionRow(PropertyCriterion criterion) {
        key.set(criterion.getPropertyKey());
        values.set(String.join(",", criterion.getPropertyValues()));
        equipmentToCheck.set(criterion.getEquipmentToCheck());
        sideToCheck.set(criterion.getSideToCheck());
    }

    PropertyCriterion toCriterion() {
        List<String> parsedValues = Arrays.stream(values.get().split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toList());
        return new PropertyCriterion(key.get(), parsedValues, equipmentToCheck.get(), sideToCheck.get());
    }

    public SimpleStringProperty keyProperty() {
        return key;
    }

    public SimpleStringProperty valuesProperty() {
        return values;
    }

    public SimpleObjectProperty<EquipmentToCheck> equipmentToCheckProperty() {
        return equipmentToCheck;
    }

    public SimpleObjectProperty<SideToCheck> sideToCheckProperty() {
        return sideToCheck;
    }
}
