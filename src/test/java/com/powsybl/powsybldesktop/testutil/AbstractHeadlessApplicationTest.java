/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.testutil;

import org.testfx.framework.junit5.ApplicationTest;

/**
 * Base class for JavaFX component tests: boots the toolkit against JavaFX's built-in headless Glass
 * platform instead of a real display. The system properties must be set before the toolkit
 * initializes, so this runs in a static initializer rather than a {@code @BeforeAll}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public abstract class AbstractHeadlessApplicationTest extends ApplicationTest {

    static {
        System.setProperty("testfx.robot", "glass");
        // deliberately no testfx.headless: that flag makes TestFX reflectively install the external
        // Monocle platform, which would override the built-in one selected below
        System.setProperty("prism.order", "sw");
        System.setProperty("java.awt.headless", "true");
        System.setProperty("glass.platform", "Headless");
    }
}
