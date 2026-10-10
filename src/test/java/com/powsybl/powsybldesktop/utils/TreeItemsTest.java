/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class TreeItemsTest {

    @Test
    void findReturnsNullForNullItem() {
        assertNull(TreeItems.find(null, value -> true));
    }

    @Test
    void findReturnsRootWhenItMatches() {
        TreeItem<String> root = new TreeItem<>("root");

        assertEquals(root, TreeItems.find(root, "root"::equals));
    }

    @Test
    void findRecursesIntoChildren() {
        TreeItem<String> root = new TreeItem<>("root");
        TreeItem<String> child = new TreeItem<>("child");
        TreeItem<String> grandchild = new TreeItem<>("grandchild");
        child.getChildren().add(grandchild);
        root.getChildren().add(child);

        assertEquals(grandchild, TreeItems.find(root, "grandchild"::equals));
    }

    @Test
    void findReturnsNullWhenNothingMatches() {
        TreeItem<String> root = new TreeItem<>("root");
        root.getChildren().add(new TreeItem<>("child"));

        assertNull(TreeItems.find(root, "missing"::equals));
    }
}
