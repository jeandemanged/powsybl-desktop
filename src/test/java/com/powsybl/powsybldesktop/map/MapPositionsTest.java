/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.map;

import com.powsybl.cgmes.conformity.CgmesConformity1Catalog;
import com.powsybl.cgmes.conversion.CgmesImport;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class MapPositionsTest {

    @Test
    void networkImportedWithGeographicalLayoutHasPositions() {
        Properties parameters = new Properties();
        parameters.setProperty(CgmesImport.POST_PROCESSORS, "cgmesGLImport");
        Network network = Network.read(CgmesConformity1Catalog.microGridBaseCaseBE().dataSource(), parameters);

        assertTrue(MapController.hasPositions(network));
    }

    @Test
    void networkImportedWithoutGeographicalLayoutHasNoPositions() {
        Network network = Network.read(CgmesConformity1Catalog.microGridBaseCaseBE().dataSource());

        assertFalse(MapController.hasPositions(network));
    }

    @Test
    void networkWithoutPositionExtensionsHasNoPositions() {
        assertFalse(MapController.hasPositions(IeeeCdfNetworkFactory.create14()));
    }

    @Test
    void noNetworkHasNoPositions() {
        assertFalse(MapController.hasPositions(null));
    }
}
