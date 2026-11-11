/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.study;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.io.TreeDataFormat;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.ReportNodeDeserializer;
import com.powsybl.commons.report.ReportNodeSerializer;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.contingency.list.ListOfContingencyLists;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.serde.ExportOptions;
import com.powsybl.iidm.serde.ImportOptions;
import com.powsybl.iidm.serde.NetworkSerDe;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.json.LoadFlowResultDeserializer;
import com.powsybl.loadflow.json.LoadFlowResultSerializer;
import com.powsybl.powsybldesktop.contingency.ContingencyListsIO;
import com.powsybl.powsybldesktop.limitscaling.LimitScalings;
import com.powsybl.powsybldesktop.logs.LogsModel.LogEntry;
import com.powsybl.powsybldesktop.parameters.ApplicationParameters;
import com.powsybl.powsybldesktop.parameters.ApplicationParametersJson;
import com.powsybl.powsybldesktop.sensitivity.SensitivityFactorMatrix;
import com.powsybl.powsybldesktop.sensitivity.SensitivityFactorsIO;
import com.powsybl.powsybldesktop.util.PowsyblDesktopVersion;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.json.SecurityAnalysisResultDeserializer;
import com.powsybl.security.json.SecurityAnalysisResultSerializer;
import com.powsybl.security.limitscaling.LimitScaling;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityVariableSet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Writes and reads a {@link Study} as a zip file ({@code .psbx}), through the zip file system so that PowSyBl's own
 * serializers write and read its entries as files:
 * <pre>
 * manifest.json                      format version, networks in order with their selection, contingency lists' and limit
 *                                    scalings' enabled flags and whether their sensitivity analysis result is DC, report count
 * parameters.json                    as the parameters configuration file
 * networks/&lt;i&gt;/network.biidm          binary IIDM, subnetworks included
 * networks/&lt;i&gt;/contingencies.json     a ListOfContingencyLists
 * networks/&lt;i&gt;/limit-scalings.json    a LimitScalingList, in order
 * networks/&lt;i&gt;/loadflow-result.json
 * networks/&lt;i&gt;/security-analysis-result.json
 * networks/&lt;i&gt;/security-analysis-limit-scalings.json   the LimitScalingList the security analysis was run with
 * networks/&lt;i&gt;/sensitivity-factors.json          the factor matrices, in the app's format
 * networks/&lt;i&gt;/sensitivity-variable-sets.json    PowSyBl's
 * networks/&lt;i&gt;/sensitivity-analysis-result.json  PowSyBl's
 * networks/&lt;i&gt;/sensitivity-analysis-matrices.json the factor matrices the sensitivity analysis was run with
 * reports/&lt;i&gt;.json
 * logs.json
 * </pre>
 * Every file but the manifest and the networks is optional. Violations and search indexes are not saved: they are
 * rebuilt once the networks, which carry the load flow's solved values, are loaded.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class StudyArchive {

    public static final String EXTENSION = "psbx";
    private static final String FORMAT_VERSION = "1.0";
    private static final String MANIFEST = "manifest.json";
    private static final String PARAMETERS = "parameters.json";
    private static final String NETWORK = "network.biidm";
    private static final String CONTINGENCIES = "contingencies.json";
    private static final String LIMIT_SCALINGS = "limit-scalings.json";
    private static final String SECURITY_ANALYSIS_LIMIT_SCALINGS = "security-analysis-limit-scalings.json";
    private static final String LOAD_FLOW_RESULT = "loadflow-result.json";
    private static final String SECURITY_ANALYSIS_RESULT = "security-analysis-result.json";
    private static final String SENSITIVITY_FACTORS = "sensitivity-factors.json";
    private static final String SENSITIVITY_VARIABLE_SETS = "sensitivity-variable-sets.json";
    private static final String SENSITIVITY_ANALYSIS_RESULT = "sensitivity-analysis-result.json";
    private static final String SENSITIVITY_ANALYSIS_MATRICES = "sensitivity-analysis-matrices.json";
    private static final String LOGS = "logs.json";
    // a study can be written by a later version having added fields
    private static final ObjectMapper MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * Told before each step of a write or read, {@code done} of {@code total} steps being over.
     */
    @FunctionalInterface
    public interface Progress {
        void update(int done, int total, String message);
    }

    // reports: null when not saved
    record Manifest(String formatVersion, String appVersion, List<NetworkEntry> networks, Integer reports) {
    }

    // sensitivityAnalysisDc: null without a sensitivity analysis result
    record NetworkEntry(String id, boolean selected, List<Boolean> contingencyListsEnabled, List<Boolean> limitScalingsEnabled,
                        Boolean sensitivityAnalysisDc) {
    }

    private record LogLine(long timeStamp, String level, String message) {
    }

    @FunctionalInterface
    private interface IOAction {
        void run() throws IOException;
    }

    private record Step(String message, IOAction action) {
    }

    private StudyArchive() {
    }

    /**
     * Writes {@code study} to {@code path}, replacing it only once fully written. Off the FX thread, the study's networks
     * being left unedited meanwhile.
     */
    public static void write(Study study, Path path, Progress progress) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.deleteIfExists(temporary);
        try {
            // useTempFile: the entries would otherwise be held in memory until the file system is closed
            try (FileSystem zip = FileSystems.newFileSystem(temporary, Map.of("create", "true", "useTempFile", Boolean.TRUE))) {
                List<Step> steps = writeSteps(study, zip.getPath("/"));
                int total = steps.size() + 1;
                run(steps, total, progress);
                progress.update(steps.size(), total, Messages.get("study.progress.finishing"));
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static List<Step> writeSteps(Study study, Path root) {
        List<Step> steps = new ArrayList<>();
        List<NetworkEntry> entries = study.networks().stream()
                .map(n -> new NetworkEntry(n.network().getId(), n.selected(), n.contingencyListsEnabled(), n.limitScalingsEnabled(),
                        n.sensitivityAnalysisResult() == null ? null : n.sensitivityAnalysisDc()))
                .toList();
        Integer reportCount = study.reports() == null ? null : study.reports().size();
        Manifest manifest = new Manifest(FORMAT_VERSION, new PowsyblDesktopVersion().getMavenProjectVersion(), entries, reportCount);
        steps.add(new Step(Messages.get("study.progress.manifest"), () -> writeJson(root.resolve(MANIFEST), manifest)));
        if (study.parameters() != null) {
            steps.add(new Step(Messages.get("study.progress.parameters"),
                    () -> writeJson(root.resolve(PARAMETERS), ApplicationParametersJson.toJson(study.parameters()))));
        }
        for (int i = 0; i < study.networks().size(); i++) {
            Study.StudyNetwork studyNetwork = study.networks().get(i);
            Path folder = networkFolder(root, i);
            String name = studyNetwork.network().getNameOrId();
            steps.add(new Step(Messages.get("study.progress.network", name), () -> {
                Files.createDirectories(folder);
                NetworkSerDe.write(studyNetwork.network(), new ExportOptions().setFormat(TreeDataFormat.BIN), folder.resolve(NETWORK));
            }));
            if (!studyNetwork.contingencyLists().isEmpty()) {
                steps.add(new Step(Messages.get("study.progress.contingencies", name), () -> ContingencyListsIO.write(
                        new ListOfContingencyLists(studyNetwork.network().getId(), studyNetwork.contingencyLists()), folder.resolve(CONTINGENCIES))));
            }
            if (!studyNetwork.limitScalings().isEmpty()) {
                steps.add(new Step(Messages.get("study.progress.limitScalings", name),
                        () -> LimitScalings.write(studyNetwork.limitScalings(), folder.resolve(LIMIT_SCALINGS))));
            }
            if (!studyNetwork.sensitivityFactorMatrices().isEmpty() || !studyNetwork.sensitivityVariableSets().isEmpty()) {
                steps.add(new Step(Messages.get("study.progress.sensitivityFactors", name), () -> {
                    SensitivityFactorsIO.writeMatrices(studyNetwork.sensitivityFactorMatrices(), folder.resolve(SENSITIVITY_FACTORS));
                    SensitivityFactorsIO.writeVariableSets(studyNetwork.sensitivityVariableSets(), folder.resolve(SENSITIVITY_VARIABLE_SETS));
                }));
            }
            if (studyNetwork.sensitivityAnalysisResult() != null) {
                steps.add(new Step(Messages.get("study.progress.sensitivityAnalysisResult", name), () -> {
                    SensitivityFactorsIO.writeResult(studyNetwork.sensitivityAnalysisResult(), folder.resolve(SENSITIVITY_ANALYSIS_RESULT));
                    SensitivityFactorsIO.writeMatrices(studyNetwork.sensitivityAnalysisMatrices(), folder.resolve(SENSITIVITY_ANALYSIS_MATRICES));
                }));
            }
            if (studyNetwork.loadFlowResult() != null) {
                steps.add(new Step(Messages.get("study.progress.loadFlowResult", name),
                        () -> LoadFlowResultSerializer.write(studyNetwork.loadFlowResult(), folder.resolve(LOAD_FLOW_RESULT))));
            }
            if (studyNetwork.securityAnalysisResult() != null) {
                steps.add(new Step(Messages.get("study.progress.securityAnalysisResult", name), () -> {
                    try (Writer writer = Files.newBufferedWriter(folder.resolve(SECURITY_ANALYSIS_RESULT))) {
                        SecurityAnalysisResultSerializer.write(studyNetwork.securityAnalysisResult(), writer);
                    }
                }));
                if (!studyNetwork.securityAnalysisLimitScalings().isEmpty()) {
                    steps.add(new Step(Messages.get("study.progress.securityAnalysisLimitScalings", name),
                            () -> LimitScalings.write(studyNetwork.securityAnalysisLimitScalings(), folder.resolve(SECURITY_ANALYSIS_LIMIT_SCALINGS))));
                }
            }
        }
        List<ReportNode> reports = study.reports() == null ? List.of() : study.reports();
        for (int i = 0; i < reports.size(); i++) {
            ReportNode report = reports.get(i);
            Path file = reportFile(root, i);
            steps.add(new Step(Messages.get("study.progress.report", i + 1, reports.size()), () -> {
                Files.createDirectories(file.getParent());
                ReportNodeSerializer.write(report, file);
            }));
        }
        if (study.logs() != null) {
            List<LogLine> lines = study.logs().stream().map(e -> new LogLine(e.timeStamp(), e.level().toString(), e.message())).toList();
            steps.add(new Step(Messages.get("study.progress.logs"), () -> writeJson(root.resolve(LOGS), lines)));
        }
        return steps;
    }

    /**
     * Reads the study at {@code path}, off the FX thread.
     */
    public static Study read(Path path, Progress progress) throws IOException {
        try (FileSystem zip = FileSystems.newFileSystem(path)) {
            Path root = zip.getPath("/");
            Manifest manifest = readJson(root.resolve(MANIFEST), new TypeReference<>() { });
            if (manifest.formatVersion() == null || !manifest.formatVersion().startsWith("1.")) {
                throw new IOException("Unsupported study format version " + manifest.formatVersion() + " in " + path);
            }
            int networkCount = manifest.networks().size();
            ApplicationParameters[] parameters = new ApplicationParameters[1];
            Network[] networks = new Network[networkCount];
            List<List<ContingencyList>> contingencyLists = new ArrayList<>(Collections.nCopies(networkCount, List.of()));
            List<List<LimitScaling>> limitScalings = new ArrayList<>(Collections.nCopies(networkCount, List.of()));
            List<List<LimitScaling>> securityAnalysisLimitScalings = new ArrayList<>(Collections.nCopies(networkCount, List.of()));
            List<List<SensitivityFactorMatrix>> sensitivityFactorMatrices = new ArrayList<>(Collections.nCopies(networkCount, List.of()));
            List<List<SensitivityVariableSet>> sensitivityVariableSets = new ArrayList<>(Collections.nCopies(networkCount, List.of()));
            List<List<SensitivityFactorMatrix>> sensitivityAnalysisMatrices = new ArrayList<>(Collections.nCopies(networkCount, List.of()));
            SensitivityAnalysisResult[] sensitivityAnalysisResults = new SensitivityAnalysisResult[networkCount];
            LoadFlowResult[] loadFlowResults = new LoadFlowResult[networkCount];
            SecurityAnalysisResult[] securityAnalysisResults = new SecurityAnalysisResult[networkCount];
            ReportNode[] reports = new ReportNode[manifest.reports() == null ? 0 : manifest.reports()];
            List<List<LogEntry>> logs = new ArrayList<>(List.of(List.of()));

            List<Step> steps = new ArrayList<>();
            Path parametersFile = root.resolve(PARAMETERS);
            if (Files.exists(parametersFile)) {
                steps.add(new Step(Messages.get("study.progress.parameters"),
                        () -> parameters[0] = ApplicationParametersJson.fromJson(MAPPER.readTree(Files.readAllBytes(parametersFile)))));
            }
            for (int i = 0; i < networkCount; i++) {
                int index = i;
                Path folder = networkFolder(root, i);
                String name = manifest.networks().get(i).id();
                steps.add(new Step(Messages.get("study.progress.network", name),
                        () -> networks[index] = NetworkSerDe.read(folder.resolve(NETWORK), new ImportOptions().setFormat(TreeDataFormat.BIN))));
                Path contingencies = folder.resolve(CONTINGENCIES);
                if (Files.exists(contingencies)) {
                    steps.add(new Step(Messages.get("study.progress.contingencies", name),
                            () -> contingencyLists.set(index, ((ListOfContingencyLists) ContingencyListsIO.read(contingencies)).getContingencyLists())));
                }
                Path scalings = folder.resolve(LIMIT_SCALINGS);
                if (Files.exists(scalings)) {
                    steps.add(new Step(Messages.get("study.progress.limitScalings", name),
                            () -> limitScalings.set(index, LimitScalings.read(scalings))));
                }
                Path sensitivityFactors = folder.resolve(SENSITIVITY_FACTORS);
                if (Files.exists(sensitivityFactors)) {
                    steps.add(new Step(Messages.get("study.progress.sensitivityFactors", name), () -> {
                        sensitivityFactorMatrices.set(index, SensitivityFactorsIO.readMatrices(sensitivityFactors));
                        sensitivityVariableSets.set(index, SensitivityFactorsIO.readVariableSets(folder.resolve(SENSITIVITY_VARIABLE_SETS)));
                    }));
                }
                Path sensitivityAnalysisResult = folder.resolve(SENSITIVITY_ANALYSIS_RESULT);
                if (Files.exists(sensitivityAnalysisResult)) {
                    steps.add(new Step(Messages.get("study.progress.sensitivityAnalysisResult", name), () -> {
                        sensitivityAnalysisResults[index] = SensitivityFactorsIO.readResult(sensitivityAnalysisResult);
                        sensitivityAnalysisMatrices.set(index, SensitivityFactorsIO.readMatrices(folder.resolve(SENSITIVITY_ANALYSIS_MATRICES)));
                        checkSensitivityAnalysisResult(sensitivityAnalysisResults[index], sensitivityAnalysisMatrices.get(index), path);
                    }));
                }
                Path loadFlowResult = folder.resolve(LOAD_FLOW_RESULT);
                if (Files.exists(loadFlowResult)) {
                    steps.add(new Step(Messages.get("study.progress.loadFlowResult", name),
                            () -> loadFlowResults[index] = LoadFlowResultDeserializer.read(loadFlowResult)));
                }
                Path securityAnalysisResult = folder.resolve(SECURITY_ANALYSIS_RESULT);
                if (Files.exists(securityAnalysisResult)) {
                    steps.add(new Step(Messages.get("study.progress.securityAnalysisResult", name),
                            () -> securityAnalysisResults[index] = SecurityAnalysisResultDeserializer.read(securityAnalysisResult)));
                }
                Path securityAnalysisScalings = folder.resolve(SECURITY_ANALYSIS_LIMIT_SCALINGS);
                if (Files.exists(securityAnalysisScalings)) {
                    steps.add(new Step(Messages.get("study.progress.securityAnalysisLimitScalings", name),
                            () -> securityAnalysisLimitScalings.set(index, LimitScalings.read(securityAnalysisScalings))));
                }
            }
            for (int i = 0; i < reports.length; i++) {
                int index = i;
                steps.add(new Step(Messages.get("study.progress.report", i + 1, reports.length),
                        () -> reports[index] = ReportNodeDeserializer.read(reportFile(root, index))));
            }
            Path logsFile = root.resolve(LOGS);
            boolean hasLogs = Files.exists(logsFile);
            if (hasLogs) {
                steps.add(new Step(Messages.get("study.progress.logs"), () -> logs.set(0, readLogs(logsFile))));
            }
            run(steps, steps.size(), progress);

            List<Study.StudyNetwork> studyNetworks = new ArrayList<>();
            for (int i = 0; i < networkCount; i++) {
                NetworkEntry entry = manifest.networks().get(i);
                List<ContingencyList> lists = contingencyLists.get(i);
                List<Boolean> enabled = entry.contingencyListsEnabled() != null && entry.contingencyListsEnabled().size() == lists.size()
                        ? entry.contingencyListsEnabled()
                        : Collections.nCopies(lists.size(), true);
                List<LimitScaling> scalings = limitScalings.get(i);
                List<Boolean> scalingsEnabled = entry.limitScalingsEnabled() != null && entry.limitScalingsEnabled().size() == scalings.size()
                        ? entry.limitScalingsEnabled()
                        : Collections.nCopies(scalings.size(), true);
                studyNetworks.add(new Study.StudyNetwork(networks[i], entry.selected(), lists, enabled, scalings, scalingsEnabled,
                        sensitivityFactorMatrices.get(i), sensitivityVariableSets.get(i),
                        loadFlowResults[i], securityAnalysisResults[i], securityAnalysisLimitScalings.get(i),
                        sensitivityAnalysisResults[i], sensitivityAnalysisMatrices.get(i), Boolean.TRUE.equals(entry.sensitivityAnalysisDc())));
            }
            return new Study(parameters[0], studyNetworks, manifest.reports() == null ? null : List.of(reports),
                    hasLogs ? logs.getFirst() : null);
        }
    }

    // the results view maps the result's factors back to the matrices they are the expansion of
    private static void checkSensitivityAnalysisResult(SensitivityAnalysisResult result, List<SensitivityFactorMatrix> matrices, Path path) throws IOException {
        if (matrices.stream().mapToInt(SensitivityFactorMatrix::factorCount).sum() != result.getFactors().size()) {
            throw new IOException("Sensitivity analysis result inconsistent with its factor matrices in " + path);
        }
    }

    // numbered below any log of the running session, whose numbers start at 0, so that they stay ordered before them
    private static List<LogEntry> readLogs(Path file) throws IOException {
        List<LogLine> lines = readJson(file, new TypeReference<>() { });
        List<LogEntry> entries = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            LogLine line = lines.get(i);
            entries.add(new LogEntry(i - (long) lines.size(), line.timeStamp(), Level.toLevel(line.level()), line.message()));
        }
        return entries;
    }

    private static void run(List<Step> steps, int total, Progress progress) throws IOException {
        for (int i = 0; i < steps.size(); i++) {
            progress.update(i, total, steps.get(i).message());
            steps.get(i).action().run();
        }
    }

    private static Path networkFolder(Path root, int index) {
        return root.resolve("networks").resolve(Integer.toString(index));
    }

    private static Path reportFile(Path root, int index) {
        return root.resolve("reports").resolve(index + ".json");
    }

    private static void writeJson(Path file, Object value) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(out, value);
        }
    }

    private static <T> T readJson(Path file, TypeReference<T> type) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return MAPPER.readValue(in, type);
        }
    }
}
