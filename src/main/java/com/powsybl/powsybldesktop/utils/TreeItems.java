/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import javafx.scene.control.TreeItem;

import java.util.function.Predicate;

/**
 * Generic recursive lookup over a {@link TreeItem} hierarchy, shared by tree/tree-table views that need to
 * reveal a node matching some criterion (navigation, search).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class TreeItems {

    private TreeItems() {
    }

    public static <T> TreeItem<T> find(TreeItem<T> item, Predicate<T> predicate) {
        if (item == null) {
            return null;
        }
        if (predicate.test(item.getValue())) {
            return item;
        }
        for (TreeItem<T> child : item.getChildren()) {
            TreeItem<T> found = find(child, predicate);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
