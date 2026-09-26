/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.contingency;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.contingency.list.DefaultContingencyList;
import com.powsybl.contingency.list.LineCriterionContingencyList;
import com.powsybl.contingency.list.ListOfContingencyLists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ContingencyListsIOTest {

    @Test
    void roundTripsAMixedListOfContingencyLists(@TempDir Path tempDir) throws Exception {
        ListOfContingencyLists original = new ListOfContingencyLists("my-lists", List.of(
                new DefaultContingencyList("explicit", List.of(Contingency.line("L1"))),
                new LineCriterionContingencyList("all-lines", null, null, List.of(), null)));

        Path file = tempDir.resolve("contingencies.json");
        ContingencyListsIO.write(original, file);

        ContingencyList loaded = ContingencyListsIO.read(file);

        ListOfContingencyLists loadedList = assertInstanceOf(ListOfContingencyLists.class, loaded);
        assertEquals("my-lists", loadedList.getName());
        assertEquals(2, loadedList.getContingencyLists().size());
        assertInstanceOf(DefaultContingencyList.class, loadedList.getContingencyLists().get(0));
        assertInstanceOf(LineCriterionContingencyList.class, loadedList.getContingencyLists().get(1));
        assertEquals("explicit", loadedList.getContingencyLists().get(0).getName());
    }
}
