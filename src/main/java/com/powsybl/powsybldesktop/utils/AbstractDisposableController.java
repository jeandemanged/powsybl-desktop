/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public abstract class AbstractDisposableController implements DisposableController {

    protected final ListenerManager listenerManager = new ListenerManager();

    @Override
    public void dispose() {
        listenerManager.dispose();
    }
}
