/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TableView;
import javafx.scene.control.skin.NestedTableColumnHeader;
import javafx.scene.control.skin.TableColumnHeader;
import javafx.scene.control.skin.TableHeaderRow;
import javafx.scene.control.skin.TableViewSkin;

/**
 * Double-clicking a column header's resize border makes JavaFX's default {@code TableColumnHeader} call
 * {@code resizeColumnToFitContent(-1)}, which measures every row in the table on the FX thread - for a table
 * with several thousand rows (a large network's Generators/Lines/Buses/... table, or the Logs table) that
 * stalls or crashes the UI. {@link #install(TableView)} installs a custom skin whose column headers cap that
 * scan at {@link #MAX_SCANNED_ROWS} rows instead, which every {@code protected}/overridable creation method
 * involved ({@code TableViewSkin.createTableHeaderRow}, {@code TableHeaderRow.createRootHeader},
 * {@code NestedTableColumnHeader.createTableColumnHeader}, {@code TableColumnHeader.resizeColumnToFitContent})
 * is designed for.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class TableAutoFitLimiter {

    private static final int MAX_SCANNED_ROWS = 1000;

    private TableAutoFitLimiter() {
    }

    public static <S> void install(TableView<S> tableView) {
        tableView.setSkin(new LimitedTableViewSkin<>(tableView));
    }

    private static final class LimitedTableViewSkin<S> extends TableViewSkin<S> {
        LimitedTableViewSkin(TableView<S> tableView) {
            super(tableView);
        }

        @Override
        protected TableHeaderRow createTableHeaderRow() {
            return new LimitedTableHeaderRow(this);
        }
    }

    private static final class LimitedTableHeaderRow extends TableHeaderRow {
        LimitedTableHeaderRow(TableViewSkin<?> skin) {
            super(skin);
        }

        @Override
        protected NestedTableColumnHeader createRootHeader() {
            return new LimitedNestedTableColumnHeader(null);
        }
    }

    private static final class LimitedNestedTableColumnHeader extends NestedTableColumnHeader {
        LimitedNestedTableColumnHeader(TableColumnBase<?, ?> tc) {
            super(tc);
        }

        // Mirrors NestedTableColumnHeader's own default: null col is the header's title-label placeholder,
        // and col == getTableColumn() is a nested header's own label recursing on itself - both need the
        // plain leaf header, not another nesting level, or construction stack-overflows/NPEs.
        @Override
        @SuppressWarnings("unchecked")
        protected TableColumnHeader createTableColumnHeader(TableColumnBase col) {
            return col == null || col.getColumns().isEmpty() || col == getTableColumn()
                    ? new LimitedTableColumnHeader(col) : new LimitedNestedTableColumnHeader(col);
        }
    }

    private static final class LimitedTableColumnHeader extends TableColumnHeader {
        LimitedTableColumnHeader(TableColumnBase<?, ?> tc) {
            super(tc);
        }

        // maxRows <= 0 is the double-click gesture's "scan every row" request - see class doc.
        @Override
        protected void resizeColumnToFitContent(int maxRows) {
            super.resizeColumnToFitContent(maxRows <= 0 ? MAX_SCANNED_ROWS : maxRows);
        }
    }
}
