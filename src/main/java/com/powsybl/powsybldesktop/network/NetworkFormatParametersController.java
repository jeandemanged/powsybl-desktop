/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.commons.parameters.Parameter;
import com.powsybl.iidm.network.Exporter;
import com.powsybl.iidm.network.Importer;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
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
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Edits the per-format network import or export parameters held by {@link MainModel}, as a format list + detail
 * pane like {@code LoadFlowParametersController}. Also the single source of the format lists shown in
 * {@link NetworksController}'s import/export menus, so both always agree on format keys.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class NetworkFormatParametersController extends AbstractDisposableController {

    private static final List<String> IIDM_FORMATS = List.of("XIIDM", "BIIDM", "JIIDM");

    @FXML
    public SplitPane splitPane;

    /**
     * Import formats in menu order, XIIDM/BIIDM/JIIDM grouped under a single "IIDM" entry since the importer
     * is picked from the file content at import time.
     */
    static Map<String, List<Importer>> importFormats() {
        Map<String, List<Importer>> formats = new LinkedHashMap<>();
        List<Importer> iidmImporters = IIDM_FORMATS.stream().map(Importer::find).filter(Objects::nonNull).toList();
        if (!iidmImporters.isEmpty()) {
            formats.put("IIDM", iidmImporters);
        }
        Importer.getFormats().stream()
                .filter(format -> !IIDM_FORMATS.contains(format))
                .sorted()
                .forEach(format -> formats.put(format, List.of(Importer.find(format))));
        return formats;
    }

    /**
     * Key under which an export format's parameters are stored: XIIDM/BIIDM/JIIDM share a single "IIDM" entry,
     * like on import, since they only differ by serialization and expose the same parameters.
     */
    static String exportParametersKey(String format) {
        return IIDM_FORMATS.contains(format) ? "IIDM" : format;
    }

    static List<String> exportFormats() {
        return Stream.concat(
                IIDM_FORMATS.stream().filter(Exporter.getFormats()::contains),
                Exporter.getFormats().stream().filter(format -> !IIDM_FORMATS.contains(format)).sorted()
        ).toList();
    }

    public void setImportParameters(MainModel mainModel) {
        Map<String, List<Parameter>> parameters = new LinkedHashMap<>();
        importFormats().forEach((format, importers) -> parameters.put(format, importers.getFirst().getParameters()));
        build(parameters, mainModel::getNetworkImportParameters);
    }

    public void setExportParameters(MainModel mainModel) {
        Map<String, List<Parameter>> parameters = new LinkedHashMap<>();
        exportFormats().forEach(format -> parameters.putIfAbsent(exportParametersKey(format), Exporter.find(format).getParameters()));
        build(parameters, mainModel::getNetworkExportParameters);
    }

    private void build(Map<String, List<Parameter>> formatParameters, Function<String, Properties> properties) {
        ListView<String> formatListView = new ListView<>();
        formatListView.getItems().addAll(formatParameters.keySet());

        StackPane detailPane = new StackPane();
        formatListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                detailPane.getChildren().setAll(buildDetail(formatParameters.get(newValue), properties.apply(newValue)));
            }
        });
        formatListView.setPrefWidth(220);
        formatListView.getSelectionModel().selectFirst();

        splitPane.getItems().setAll(formatListView, detailPane);
        splitPane.setDividerPositions(0.18);
    }

    private static Node buildDetail(List<Parameter> parameters, Properties properties) {
        if (parameters.isEmpty()) {
            return new Label(Messages.get("parameters.networkFormat.noParameters"));
        }
        ScrollPane scrollPane = new ScrollPane(ParameterFormBuilder.build(parameters, properties));
        scrollPane.setFitToWidth(true);
        return scrollPane;
    }
}
