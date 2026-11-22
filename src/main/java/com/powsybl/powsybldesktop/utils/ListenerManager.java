/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ListenerManager {

    private final List<Runnable> cleanupTasks = new ArrayList<>();

    public <T> void listen(
            ObservableValue<T> observable,
            ChangeListener<T> listener) {

        observable.addListener(listener);

        cleanupTasks.add(() ->
                observable.removeListener(listener));
    }

    public <T> void listen(
            ObservableList<T> observable,
            ListChangeListener<T> listener) {

        observable.addListener(listener);

        cleanupTasks.add(() ->
                observable.removeListener(listener));
    }

    public void dispose() {
        cleanupTasks.forEach(Runnable::run);
        cleanupTasks.clear();
    }
}
