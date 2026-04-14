/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import javafx.beans.NamedArg;
import javafx.scene.control.ChoiceBox;

import java.util.Arrays;
import java.util.Optional;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class EnumChoiceBox<E extends Enum<E>> extends ChoiceBox<E> {

    public EnumChoiceBox(@NamedArg("enumType") String enumType, @NamedArg("initialValue") String initialValue) throws ClassNotFoundException {
        Class<E> enumClass = (Class<E>) Class.forName(enumType);
        E[] values = enumClass.getEnumConstants();
        getItems().setAll(values);
        Optional<E> value = Arrays.stream(values).filter(v -> v.name().compareTo(initialValue) == 0).findAny();
        value.ifPresentOrElse(v -> getSelectionModel().select(v), getSelectionModel()::selectFirst);
    }
}
