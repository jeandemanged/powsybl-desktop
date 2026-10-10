/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.openloadflow.OpenLoadFlowParameters;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class CategoryTitlesTest {

    @Test
    void buildCategoryTitlesCoversAllExpectedKeysInOrder() {
        Map<String, String> titles = LoadFlowParametersController.buildCategoryTitles();

        assertEquals(21, titles.size());
        assertEquals("Model", titles.get(OpenLoadFlowParameters.MODEL_CATEGORY_KEY));
        assertEquals("DC", titles.get(OpenLoadFlowParameters.DC_CATEGORY_KEY));
        assertEquals("Slack Distribution", titles.get(OpenLoadFlowParameters.SLACK_DISTRIBUTION_CATEGORY_KEY));
        assertEquals("Voltage Init", titles.get(OpenLoadFlowParameters.VOLTAGE_INIT_CATEGORY_KEY));
        assertEquals("Debug", titles.get(OpenLoadFlowParameters.DEBUG_CATEGORY_KEY));
        assertEquals("Reporting", titles.get(OpenLoadFlowParameters.REPORTING_CATEGORY_KEY));
    }
}
