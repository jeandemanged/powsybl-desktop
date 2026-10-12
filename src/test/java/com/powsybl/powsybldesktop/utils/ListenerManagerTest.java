/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ListenerManagerTest {

    private final ListenerManager listenerManager = new ListenerManager();

    @Test
    void disposeDetachesPropertyListener() {
        SimpleObjectProperty<String> property = new SimpleObjectProperty<>("initial");
        int[] callCount = {0};
        listenerManager.listen(property, (observable, oldValue, newValue) -> callCount[0]++);

        property.set("changed");
        assertEquals(1, callCount[0]);

        listenerManager.dispose();
        property.set("changed again");
        assertEquals(1, callCount[0], "listener should no longer be notified after dispose");
    }

    @Test
    void disposeDetachesListListener() {
        ObservableList<String> list = FXCollections.observableArrayList();
        int[] callCount = {0};
        listenerManager.listen(list, change -> callCount[0]++);

        list.add("a");
        assertEquals(1, callCount[0]);

        listenerManager.dispose();
        list.add("b");
        assertEquals(1, callCount[0], "listener should no longer be notified after dispose");
    }
}
