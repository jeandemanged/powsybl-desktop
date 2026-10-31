/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A searchable voltage level choice of a creation form. When the voltage levels are in several substations, it is
 * narrowed by a searchable substation choice above it ("no substation" first): many voltage levels of a large network
 * are named alike, their substations tell them apart.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class VoltageLevelChoice {

    // the substation choice's entry for the voltage levels without substation
    private static final Object NO_SUBSTATION = new Object();

    private final CreationForm.Field<VoltageLevel> field;

    /**
     * @param initial the voltage level chosen first, its substation too
     */
    @SuppressWarnings("unchecked")
    VoltageLevelChoice(CreationForm form, List<VoltageLevel> voltageLevels, VoltageLevel initial) {
        List<VoltageLevel> sorted = voltageLevels.stream().sorted(Comparator.comparing(VoltageLevel::getNameOrId)).toList();
        Map<Object, List<VoltageLevel>> bySubstation = sorted.stream()
                .collect(Collectors.groupingBy(VoltageLevelChoice::substationOf, LinkedHashMap::new, Collectors.toList()));
        if (bySubstation.size() < 2) {
            field = form.searchableChoice("desktop.common.equipment.voltageLevel", sorted, CreationForm.identifiableFormat(), initial, true);
            return;
        }
        List<Object> substations = bySubstation.keySet().stream()
                .sorted(Comparator.comparing((Object substation) -> substation != NO_SUBSTATION).thenComparing(VoltageLevelChoice::text))
                .toList();
        Object initialSubstation = bySubstation.containsKey(substationOf(initial)) ? substationOf(initial) : substations.getFirst();
        ComboBox<Object> substationBox = (ComboBox<Object>) form.searchableChoice("desktop.common.equipment.substation", substations,
                new StringConverter<>() {
                    @Override
                    public String toString(Object substation) {
                        return substation == null ? "" : text(substation);
                    }

                    @Override
                    public Object fromString(String string) {
                        throw new UnsupportedOperationException();
                    }
                }, initialSubstation, false).getEditor();
        field = form.searchableChoice("desktop.common.equipment.voltageLevel", bySubstation.get(initialSubstation),
                CreationForm.identifiableFormat(), initial, true);
        ComboBox<VoltageLevel> box = (ComboBox<VoltageLevel>) field.getEditor();
        // ControlsFX's searchable box briefly reports no value as its popup hides, and its cells fail on a list changed
        // under them: the transient null is ignored, and the voltage levels replaced by a new list, not changed
        substationBox.valueProperty().addListener((_, _, newValue) -> {
            if (newValue == null) {
                return;
            }
            List<VoltageLevel> ofSubstation = bySubstation.get(newValue);
            VoltageLevel kept = ofSubstation.contains(box.getValue()) ? box.getValue() : ofSubstation.getFirst();
            box.setValue(null);
            box.setItems(FXCollections.observableArrayList(ofSubstation));
            box.setValue(kept);
        });
    }

    private static Object substationOf(VoltageLevel voltageLevel) {
        return voltageLevel == null ? null : voltageLevel.getSubstation().<Object>map(substation -> substation).orElse(NO_SUBSTATION);
    }

    private static String text(Object substation) {
        return substation instanceof Substation s ? s.getNameOrId() : Messages.get("edit.create.connection.noSubstation");
    }

    /**
     * The chosen voltage level, throwing a {@link CreationForm.FieldException} if none.
     */
    VoltageLevel get() {
        return field.get();
    }

    @SuppressWarnings("unchecked")
    ObjectProperty<VoltageLevel> valueProperty() {
        return ((ComboBox<VoltageLevel>) field.getEditor()).valueProperty();
    }
}
