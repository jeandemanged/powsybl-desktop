/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.contingency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.json.JsonUtil;
import com.powsybl.contingency.json.ContingencyJsonModule;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.contingency.list.ListOfContingencyLists;

import java.io.IOException;
import java.nio.file.Path;

/**
 * PowSyBl ships no {@code ContingencyList.readJson/writeJson} helper (unlike e.g. ReportNode) - the pattern is a
 * plain Jackson module ({@link ContingencyJsonModule}) registered on a PowSyBl-flavored {@link ObjectMapper},
 * which resolves the "type" discriminator polymorphism internally. Factored out here so it's testable without
 * the JavaFX toolkit, same rationale as SubstationDiagramRenderer.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class ContingencyListsIO {

    private ContingencyListsIO() {
    }

    static ObjectMapper mapper() {
        ObjectMapper mapper = JsonUtil.createObjectMapper();
        mapper.registerModule(new ContingencyJsonModule());
        return mapper;
    }

    static ContingencyList read(Path path) throws IOException {
        return mapper().readValue(path.toFile(), ContingencyList.class);
    }

    static void write(ListOfContingencyLists list, Path path) throws IOException {
        mapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), list);
    }
}
