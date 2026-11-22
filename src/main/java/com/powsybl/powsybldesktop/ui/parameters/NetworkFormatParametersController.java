/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.parameters;

import com.powsybl.commons.parameters.Parameter;
import com.powsybl.iidm.network.Exporter;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.network.NetworkIO;
import com.powsybl.powsybldesktop.ui.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.StackPane;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

/**
 * Edits the per-format network import or export parameters held by {@link MainModel}, as a format list + detail
 * pane like {@code LoadFlowParametersController}, for the formats of {@link NetworkIO}, like the networks view's
 * import/export menus, so both always agree on format keys.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class NetworkFormatParametersController extends AbstractDisposableController {

    @FXML
    public SplitPane splitPane;

    private Runnable refresher = () -> { };

    public void setImportParameters(MainModel mainModel) {
        Map<String, List<Parameter>> parameters = new LinkedHashMap<>();
        NetworkIO.importFormats().forEach((format, importers) -> parameters.put(format, importers.getFirst().getParameters()));
        build(parameters, mainModel.getParametersModel()::getNetworkImportParameters, mainModel.getParametersModel()::parametersChanged);
    }

    public void setExportParameters(MainModel mainModel) {
        Map<String, List<Parameter>> parameters = new LinkedHashMap<>();
        NetworkIO.exportFormats().forEach(format -> parameters.putIfAbsent(NetworkIO.exportParametersKey(format), Exporter.find(format).getParameters()));
        build(parameters, mainModel.getParametersModel()::getNetworkExportParameters, mainModel.getParametersModel()::parametersChanged);
    }

    // the detail form writes into the Properties it was built from: rebuilt once MainModel's are replaced
    public void refresh() {
        refresher.run();
    }

    private void build(Map<String, List<Parameter>> formatParameters, Function<String, Properties> properties, Runnable onChange) {
        ListView<String> formatListView = new ListView<>();
        formatListView.getItems().addAll(formatParameters.keySet());

        StackPane detailPane = new StackPane();
        refresher = () -> {
            String format = formatListView.getSelectionModel().getSelectedItem();
            if (format != null) {
                detailPane.getChildren().setAll(buildDetail(formatParameters.get(format), properties.apply(format), onChange));
            }
        };
        formatListView.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> refresher.run());
        formatListView.setPrefWidth(220);
        formatListView.getSelectionModel().selectFirst();

        splitPane.getItems().setAll(formatListView, detailPane);
        splitPane.setDividerPositions(0.18);
    }

    private static Node buildDetail(List<Parameter> parameters, Properties properties, Runnable onChange) {
        if (parameters.isEmpty()) {
            return new Label(Messages.get("parameters.networkFormat.noParameters"));
        }
        ScrollPane scrollPane = new ScrollPane(ParameterFormBuilder.build(parameters, properties, onChange));
        scrollPane.setFitToWidth(true);
        return scrollPane;
    }
}
