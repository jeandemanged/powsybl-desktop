/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.powsybldesktop.utils.DisposableController;

/**
 * A view controller set up for the scene showing it, the main window's or a separate window's (see
 * {@link SceneModel}).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public interface SceneView extends DisposableController {

    void setSceneModel(SceneModel sceneModel);
}
