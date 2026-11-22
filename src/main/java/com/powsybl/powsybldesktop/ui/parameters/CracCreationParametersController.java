/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.parameters;

import com.powsybl.commons.PowsyblException;
import com.powsybl.entsoe.utils.CapacityCalculationRegion;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.api.parameters.JsonCracCreationParameters;
import com.powsybl.openrao.data.crac.io.cim.parameters.CimCracCreationParameters;
import com.powsybl.openrao.data.crac.io.cse.parameters.CseCracCreationParameters;
import com.powsybl.openrao.data.crac.io.fbconstraint.parameters.FbConstraintCracCreationParameters;
import com.powsybl.openrao.data.crac.io.nc.parameters.NcCracCreationParameters;
import com.powsybl.openrao.data.crac.io.nc.parameters.SweNcCracCreationParameters;
import com.powsybl.powsybldesktop.parameters.ParametersModel;
import com.powsybl.powsybldesktop.rao.CracCreationParametersSupport;
import com.powsybl.powsybldesktop.ui.controls.FileChooserPreferences;
import com.powsybl.powsybldesktop.ui.controls.RowsTableEditor;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Edits OpenRAO's {@link CracCreationParameters}, those of a CRAC file's import, by format: the default monitored line
 * side and the remedial action usage limits per instant (all formats), the aligned range action groups and busbar
 * changes (CSE), the range action groups, time series, range action speeds and voltage CNECs (CIM), the capacity
 * calculation region, curative instants and the TSOs not using PATL in the final state (NC), the ICS costs and internal
 * HVDCs (flow-based constraint document). Lists, maps and nested structures are {@link RowsTableEditor}s whose rows
 * {@link CracCreationParametersSupport} parses. The time stamp of a file is asked at its import, not here. A format's
 * extension is added once one of its values is set. Import and export in OpenRAO's JSON format.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class CracCreationParametersController extends AbstractDiagramParametersController<CracCreationParameters> {

    private static final String CAT_GENERAL = "general";
    private static final String CAT_CSE = "cse";
    private static final String CAT_CIM = "cim";
    private static final String CAT_NC = "nc";
    private static final String CAT_FLOW_BASED = "flowBased";

    private final Map<String, RowsTableEditor> tables = new LinkedHashMap<>();
    private BiConsumer<Path, RuntimeException> onImportFailed = (_, _) -> { };

    @FXML
    private void initialize() {
        Map<String, String> titles = new LinkedHashMap<>();
        for (String category : new String[] {CAT_GENERAL, CAT_CSE, CAT_CIM, CAT_NC, CAT_FLOW_BASED}) {
            titles.put(category, Messages.get("cracCreation.category." + category));
        }
        buildCategoryList(titles);
    }

    public void setParametersModel(ParametersModel parametersModel) {
        setParametersProperty(parametersModel.cracCreationParametersProperty());
        setOnChange(parametersModel::parametersChanged);
    }

    public void setOnImportFailed(BiConsumer<Path, RuntimeException> onImportFailed) {
        this.onImportFailed = Objects.requireNonNull(onImportFailed);
    }

    /**
     * The table of {@code param}, for tests.
     */
    RowsTableEditor table(String param) {
        return tables.get(param);
    }

    private static CseCracCreationParameters readCse(CracCreationParameters parameters) {
        return CracCreationParametersSupport.read(parameters, CseCracCreationParameters.class, CseCracCreationParameters::new);
    }

    private static CimCracCreationParameters readCim(CracCreationParameters parameters) {
        return CracCreationParametersSupport.read(parameters, CimCracCreationParameters.class, CimCracCreationParameters::new);
    }

    private static NcCracCreationParameters readNc(CracCreationParameters parameters) {
        return CracCreationParametersSupport.read(parameters, NcCracCreationParameters.class, NcCracCreationParameters::new);
    }

    private static FbConstraintCracCreationParameters readFlowBased(CracCreationParameters parameters) {
        return CracCreationParametersSupport.read(parameters, FbConstraintCracCreationParameters.class, FbConstraintCracCreationParameters::new);
    }

    @Override
    protected void buildFields() {
        ChoiceBox<CracCreationParameters.MonitoredLineSide> sideChoiceBox =
                new ChoiceBox<>(FXCollections.observableArrayList(CracCreationParameters.MonitoredLineSide.values()));
        sideChoiceBox.setConverter(converter(side -> Messages.get("crac.import.side." + side.name())));
        refreshers.add(() -> sideChoiceBox.setValue(CracCreationParametersSupport.monitoredLineSide(parametersProperty.getValue())));
        sideChoiceBox.valueProperty().addListener((_, _, side) -> {
            if (!refreshing && side != null) {
                parametersProperty.getValue().setDefaultMonitoredLineSide(side);
                notifyChange();
            }
        });
        addRow(CAT_GENERAL, label("monitoredLineSide"), tooltip("monitoredLineSide"), sideChoiceBox);
        addTable(CAT_GENERAL, "usageLimits", List.of("instant", "maxRa"), CracCreationParametersSupport::usageLimitsRows,
                (p, rows) -> CracCreationParametersSupport.setUsageLimits(p, rows, tables.get("usageLimitsPerTso").getRows()));
        addTable(CAT_GENERAL, "usageLimitsPerTso", List.of("instant", "tso", "maxTopo", "maxPst", "maxRa", "maxElementary"),
                CracCreationParametersSupport::usageLimitsPerTsoRows,
                (p, rows) -> CracCreationParametersSupport.setUsageLimits(p, tables.get("usageLimits").getRows(), rows));

        addTable(CAT_CSE, "cseRangeActionGroups", List.of("rangeActions"), p -> CracCreationParametersSupport.rangeActionGroupsRows(readCse(p)),
                (p, rows) -> CracCreationParametersSupport.setRangeActionGroups(CracCreationParametersSupport.cse(p), rows));
        addTable(CAT_CSE, "busBarChanges", List.of("remedialAction", "switchToOpen", "switchToClose"),
                p -> CracCreationParametersSupport.busBarChangeSwitchesRows(readCse(p)),
                (p, rows) -> CracCreationParametersSupport.setBusBarChangeSwitches(CracCreationParametersSupport.cse(p), rows));

        addTable(CAT_CIM, "cimRangeActionGroups", List.of("rangeActions"), p -> CracCreationParametersSupport.rangeActionGroupsRows(readCim(p)),
                (p, rows) -> CracCreationParametersSupport.setRangeActionGroups(CracCreationParametersSupport.cim(p), rows));
        addTable(CAT_CIM, "timeseries", List.of("mrid"), p -> CracCreationParametersSupport.timeseriesMridsRows(readCim(p)),
                (p, rows) -> CracCreationParametersSupport.setTimeseriesMrids(CracCreationParametersSupport.cim(p), rows));
        addTable(CAT_CIM, "speeds", List.of("rangeAction", "speed"), p -> CracCreationParametersSupport.rangeActionSpeedsRows(readCim(p)),
                (p, rows) -> CracCreationParametersSupport.setRangeActionSpeeds(CracCreationParametersSupport.cim(p), rows));
        addTable(CAT_CIM, "voltageElements", List.of("networkElement"), p -> CracCreationParametersSupport.voltageMonitoredElementsRows(readCim(p)),
                (p, rows) -> CracCreationParametersSupport.setVoltageCnecs(CracCreationParametersSupport.cim(p), rows,
                        tables.get("voltageStates").getRows(), tables.get("voltageThresholds").getRows()));
        addTable(CAT_CIM, "voltageStates", List.of("instant", "contingencies"), p -> CracCreationParametersSupport.voltageMonitoredStatesRows(readCim(p)),
                (p, rows) -> CracCreationParametersSupport.setVoltageCnecs(CracCreationParametersSupport.cim(p), tables.get("voltageElements").getRows(),
                        rows, tables.get("voltageThresholds").getRows()));
        addTable(CAT_CIM, "voltageThresholds", List.of("instant", "nominalV", "min", "max"),
                p -> CracCreationParametersSupport.voltageThresholdsRows(readCim(p)),
                (p, rows) -> CracCreationParametersSupport.setVoltageCnecs(CracCreationParametersSupport.cim(p), tables.get("voltageElements").getRows(),
                        tables.get("voltageStates").getRows(), rows));

        List<String> regions = new ArrayList<>(List.of(""));
        Arrays.stream(CapacityCalculationRegion.values()).map(Enum::name).forEach(regions::add);
        ChoiceBox<String> regionChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(regions));
        regionChoiceBox.setConverter(converter(name -> name.isEmpty() ? Messages.get("cracCreation.region.none") : regionName(CapacityCalculationRegion.valueOf(name))));
        refreshers.add(() -> {
            CapacityCalculationRegion region = readNc(parametersProperty.getValue()).getCapacityCalculationRegion();
            regionChoiceBox.setValue(region == null ? "" : region.name());
        });
        regionChoiceBox.valueProperty().addListener((_, _, name) -> {
            if (!refreshing && name != null) {
                CracCreationParametersSupport.nc(parametersProperty.getValue()).setCapacityCalculationRegion(name.isEmpty() ? null : CapacityCalculationRegion.valueOf(name));
                notifyChange();
            }
        });
        addRow(CAT_NC, label("region"), tooltip("region"), regionChoiceBox);
        addTable(CAT_NC, "curativeInstants", List.of("instant", "applicationTime"), p -> CracCreationParametersSupport.curativeInstantsRows(readNc(p)),
                (p, rows) -> CracCreationParametersSupport.setCurativeInstants(CracCreationParametersSupport.nc(p), rows));
        addTable(CAT_NC, "sweTsos", List.of("tso"), p -> CracCreationParametersSupport.tsosRows(
                        CracCreationParametersSupport.read(p, SweNcCracCreationParameters.class, SweNcCracCreationParameters::new).getTsosWhichDoNotUsePatlInFinalState()),
                (p, rows) -> CracCreationParametersSupport.sweNc(p).setTsosWhichDoNotUsePatlInFinalState(CracCreationParametersSupport.tsos(rows)));

        addDoubleField(CAT_FLOW_BASED, label("icsCostUp"), tooltip("icsCostUp"), p -> readFlowBased(p).getIcsCostUp(),
                (p, v) -> CracCreationParametersSupport.flowBasedConstraint(p).setIcsCostUp(v));
        addDoubleField(CAT_FLOW_BASED, label("icsCostDown"), tooltip("icsCostDown"), p -> readFlowBased(p).getIcsCostDown(),
                (p, v) -> CracCreationParametersSupport.flowBasedConstraint(p).setIcsCostDown(v));
        addTable(CAT_FLOW_BASED, "hvdcConverters", List.of("hvdcEic", "hvdcCode", "pole", "converterNode", "converterStation"),
                p -> CracCreationParametersSupport.hvdcConvertersRows(readFlowBased(p)),
                (p, rows) -> CracCreationParametersSupport.setInternalHvdcs(CracCreationParametersSupport.flowBasedConstraint(p), rows,
                        tables.get("hvdcLines").getRows()));
        addTable(CAT_FLOW_BASED, "hvdcLines", List.of("hvdcEic", "hvdcCode", "pole", "line", "from", "to"),
                p -> CracCreationParametersSupport.hvdcLinesRows(readFlowBased(p)),
                (p, rows) -> CracCreationParametersSupport.setInternalHvdcs(CracCreationParametersSupport.flowBasedConstraint(p),
                        tables.get("hvdcConverters").getRows(), rows));
    }

    static String regionName(CapacityCalculationRegion region) {
        return region.name().replace('_', ' ') + " (" + region.getEIC() + ")";
    }

    // the rows are read on each refresh; a change writes all of them, the parameters unchanged when one is invalid
    private void addTable(String category, String param, List<String> columns, Function<CracCreationParameters, List<List<String>>> rows,
                          BiConsumer<CracCreationParameters, List<List<String>>> write) {
        RowsTableEditor editor = new RowsTableEditor(columns.stream().map(column -> Messages.get("cracCreation.column." + column)).toList());
        refreshers.add(() -> editor.setRows(rows.apply(parametersProperty.getValue())));
        editor.setOnEdit(newRows -> {
            write.accept(parametersProperty.getValue(), newRows);
            notifyChange();
        });
        tables.put(param, editor);
        addRow(category, label(param), tooltip(param), editor);
    }

    private static <T> StringConverter<T> converter(Function<T, String> toString) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : toString.apply(value);
            }

            @Override
            public T fromString(String string) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static String label(String param) {
        return Messages.get("cracCreation.param." + param + ".label");
    }

    private static String tooltip(String param) {
        return Messages.get("cracCreation.param." + param + ".tooltip");
    }

    @FXML
    private void onImport() {
        File file = createFileChooser().showOpenDialog(splitPane.getScene().getWindow());
        if (file != null) {
            FileChooserPreferences.saveLastDirectory(file);
            importFrom(file.toPath());
        }
    }

    @FXML
    private void onExport() {
        File file = createFileChooser().showSaveDialog(splitPane.getScene().getWindow());
        if (file != null) {
            FileChooserPreferences.saveLastDirectory(file);
            exportTo(file.toPath());
        }
    }

    private static FileChooser createFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.supportedFiles", "json"), "*.json"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.allFiles"), "*.*"));
        FileChooserPreferences.applyLastDirectory(fileChooser);
        return fileChooser;
    }

    // OpenRAO's CRAC creation parameters JSON format
    void importFrom(Path path) {
        CracCreationParameters parameters;
        try {
            parameters = JsonCracCreationParameters.read(path);
        } catch (PowsyblException | OpenRaoException | UncheckedIOException e) {
            onImportFailed.accept(path, e);
            return;
        }
        parametersProperty.setValue(parameters);
    }

    void exportTo(Path path) {
        JsonCracCreationParameters.write(parametersProperty.getValue(), path);
    }
}
