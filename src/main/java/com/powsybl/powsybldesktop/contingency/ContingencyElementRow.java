/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.contingency;

import com.powsybl.contingency.ContingencyElementType;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * Editable (type, id) row backing {@link DefaultContingencyListFormController}'s per-contingency elements table -
 * a local, mutable stand-in for PowSyBl's immutable {@code ContingencyElement}, rebuilt into the real thing via
 * {@code Contingency.builder(id).addXxx(elementId)} on commit.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ContingencyElementRow {

    private final SimpleObjectProperty<ContingencyElementType> type;
    private final SimpleStringProperty id;

    public ContingencyElementRow(ContingencyElementType type, String id) {
        this.type = new SimpleObjectProperty<>(type);
        this.id = new SimpleStringProperty(id);
    }

    public SimpleObjectProperty<ContingencyElementType> typeProperty() {
        return type;
    }

    public SimpleStringProperty idProperty() {
        return id;
    }

    public ContingencyElementType getType() {
        return type.get();
    }

    public String getId() {
        return id.get();
    }
}
