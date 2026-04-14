/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.report;

import com.powsybl.commons.report.ReportNode;
import javafx.fxml.FXML;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ReportNodeViewController {

    @FXML
    public TreeView<ReportNode> treeView;

    private ReportNode rootReportNode;

    // A node is kept if its own severity meets the threshold, or a descendant's does - so raising the
    // threshold only prunes branches that are entirely below it, rather than orphaning matching descendants.
    private String minSeverity = ReportNodeTreeCell.SEVERITIES.get(0);

    @FXML
    private void initialize() {
        treeView.setCellFactory(tv -> new ReportNodeTreeCell());
    }

    public void setRootReportNode(ReportNode reportNode) {
        this.rootReportNode = Objects.requireNonNull(reportNode);
        refresh();
    }

    public void setMinSeverity(String minSeverity) {
        this.minSeverity = Objects.requireNonNull(minSeverity);
        refresh();
    }

    private void refresh() {
        treeView.setRoot(rootReportNode == null ? null : toTreeItem(rootReportNode).orElse(null));
    }

    private Optional<TreeItem<ReportNode>> toTreeItem(ReportNode reportNode) {
        List<TreeItem<ReportNode>> children = reportNode.getChildren().stream()
                .map(this::toTreeItem)
                .flatMap(Optional::stream)
                .toList();
        if (children.isEmpty() && !meetsMinSeverity(reportNode)) {
            return Optional.empty();
        }
        TreeItem<ReportNode> item = new TreeItem<>(reportNode);
        item.setExpanded(true);
        item.getChildren().addAll(children);
        return Optional.of(item);
    }

    private boolean meetsMinSeverity(ReportNode reportNode) {
        return ReportNodeTreeCell.SEVERITIES.indexOf(ReportNodeTreeCell.severityOf(reportNode))
                >= ReportNodeTreeCell.SEVERITIES.indexOf(minSeverity);
    }
}
