/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.powsybldesktop.utils.Messages;

import java.util.List;

/**
 * Parsing the text cells of a table's rows (see {@code RowsTableEditor}): a blank cell is an absent value; an invalid one
 * throws an {@link IllegalArgumentException} telling the row and the column, numbered from 1.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class TableRows {

    private TableRows() {
    }

    static String text(Object value) {
        return value == null ? "" : value instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString(d.longValue()) : value.toString();
    }

    static String cell(List<String> row, int column) {
        return column < row.size() && row.get(column) != null ? row.get(column).trim() : "";
    }

    static String required(List<String> row, int column, int rowIndex) {
        String value = cell(row, column);
        if (value.isEmpty()) {
            throw invalid(rowIndex, Messages.get("cracCreation.row.required", column + 1));
        }
        return value;
    }

    static Integer integer(List<String> row, int column, int rowIndex) {
        String value = cell(row, column);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException _) {
            throw invalid(rowIndex, Messages.get("cracCreation.row.integer", column + 1, value));
        }
    }

    static int requiredInteger(List<String> row, int column, int rowIndex) {
        Integer value = integer(row, column, rowIndex);
        if (value == null) {
            throw invalid(rowIndex, Messages.get("cracCreation.row.required", column + 1));
        }
        return value;
    }

    static Double number(List<String> row, int column, int rowIndex) {
        String value = cell(row, column);
        if (value.isEmpty()) {
            return null;
        }
        try {
            double number = Double.parseDouble(value);
            if (!Double.isFinite(number)) {
                throw new NumberFormatException(value);
            }
            return number;
        } catch (NumberFormatException _) {
            throw invalid(rowIndex, Messages.get("cracCreation.row.number", column + 1, value));
        }
    }

    static double requiredNumber(List<String> row, int column, int rowIndex) {
        Double value = number(row, column, rowIndex);
        if (value == null) {
            throw invalid(rowIndex, Messages.get("cracCreation.row.required", column + 1));
        }
        return value;
    }

    static IllegalArgumentException invalid(int rowIndex, String reason) {
        return new IllegalArgumentException(Messages.get("cracCreation.row.invalid", rowIndex + 1, reason));
    }
}
