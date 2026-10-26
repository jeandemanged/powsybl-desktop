/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Component;
import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.TapChanger;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.violations.FlowViolation;
import com.powsybl.powsybldesktop.violations.Loading;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Polygon;
import javafx.stage.Window;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.controlsfx.control.Notifications;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Cell value/factory wiring shared by the Loads, Generators, Batteries, ShuntCompensators, StaticVarCompensators,
 * VscConverterStations, LccConverterStations, Lines, Transformers, TieLines, BoundaryLines, HvdcLines, Components,
 * Substations and VoltageLevels tables, which display the same substation/voltage-level
 * link, connected checkbox, component, and formatted-double column shapes for their respective row entity type.
 * The connected checkbox toggles its terminal's connection (disconnect if connected, connect otherwise). The
 * "two-sided" variants stack a value per terminal (or per side's substation/voltage level) in a single cell,
 * for the Lines, TieLines and HvdcLines tables. The
 * "multi-sided" variants do the same for a variable number of sides (2 or 3), for the Transformers table, whose
 * editable variants give each side its own field (see {@link CellFields}). The
 * "nullable editable" variants show "-" as text for a field that only exists on some rows, for the BoundaryLines
 * table's optional generation part. configureMultiSidedTapChangerColumn combines both: a per-side stepper, blank on a
 * side without that tap changer type, for the Transformers table's ratio/phase tap changer columns (their steps are
 * edited in the transformer's detail view).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class TableColumnSupport {

    // Briefly marks a field or cell after an edit, so styles.css can flash it red (rejected, e.g. an IIDM validation
    // exception) or green (applied), both removed again after their respective duration below.
    public static final String EDIT_ERROR_STYLE_CLASS = "edit-error";
    public static final String EDIT_SUCCESS_STYLE_CLASS = "edit-success";
    private static final Duration EDIT_SUCCESS_FLASH_DURATION = Duration.millis(200);
    private static final Duration EDIT_ERROR_FLASH_DURATION = Duration.seconds(1.5);

    static Duration flashDuration(String styleClass) {
        return EDIT_ERROR_STYLE_CLASS.equals(styleClass) ? EDIT_ERROR_FLASH_DURATION : EDIT_SUCCESS_FLASH_DURATION;
    }

    // Decimal places read on every call rather than captured: GuiParameters is edited in place, or replaced on a
    // parameters reset/import, and the tables are refreshed then (see AbstractEquipmentTableController.setSceneModel).
    // Blank or "-" parses to NaN, which IIDM refuses where a value is mandatory, like DetailForm's double fields.
    public static StringConverter<Double> doubleFormat(Supplier<GuiParameters> guiParameters, GuiParameters.Quantity quantity) {
        return new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return isMissing(value) ? "-" : guiParameters.get().format(quantity, value);
            }

            @Override
            public Double fromString(String text) {
                String trimmed = text.trim();
                return trimmed.isEmpty() || "-".equals(trimmed) ? Double.NaN : Double.valueOf(trimmed);
            }
        };
    }

    private static final StringConverter<String> STRING_FORMAT = new StringConverter<>() {
        @Override
        public String toString(String value) {
            return value == null || value.isBlank() ? "-" : value;
        }

        @Override
        public String fromString(String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() || "-".equals(trimmed) ? null : trimmed;
        }
    };

    private TableColumnSupport() {
    }

    static <S> void configureContainerColumn(TableColumn<S, S> column,
                                              Function<S, Optional<? extends Container<?>>> containerGetter,
                                              BiFunction<Container<?>, S, Node> linkFactory) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : containerGetter.apply(item).map(container -> linkFactory.apply(container, item)).orElse(null));
            }
        });
    }

    static <S> void configureTwoSidedContainerColumn(TableColumn<S, S> column,
                                                       Function<S, Optional<? extends Container<?>>> container1Getter,
                                                       Function<S, Optional<? extends Container<?>>> container2Getter,
                                                       BiFunction<Container<?>, S, Node> linkFactory) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : twoSidedBox(containerNode(container1Getter.apply(item), item, linkFactory),
                                containerNode(container2Getter.apply(item), item, linkFactory), Pos.CENTER_LEFT));
            }
        });
    }

    private static <S> Node containerNode(Optional<? extends Container<?>> container, S item, BiFunction<Container<?>, S, Node> linkFactory) {
        return container.<Node>map(c -> linkFactory.apply(c, item)).orElseGet(Label::new);
    }

    static <S> void configureConnectedColumn(TableColumn<S, S> column, Function<S, Terminal> terminalGetter, Consumer<Terminal> onToggle) {
        connectedColumn(column, item -> List.of(terminalGetter.apply(item)), onToggle, checkBoxes -> checkBoxes.getFirst());
    }

    static <S> void configureTwoSidedConnectedColumn(TableColumn<S, S> column, Function<S, Terminal> terminal1Getter,
                                                       Function<S, Terminal> terminal2Getter, Consumer<Terminal> onToggle) {
        configureMultiSidedConnectedColumn(column, item -> List.of(terminal1Getter.apply(item), terminal2Getter.apply(item)), onToggle);
    }

    static <S> void configureMultiSidedConnectedColumn(TableColumn<S, S> column, Function<S, List<Terminal>> terminalsGetter, Consumer<Terminal> onToggle) {
        column.setSortable(false);
        connectedColumn(column, terminalsGetter, onToggle, checkBoxes -> sidedBox(checkBoxes, Pos.CENTER));
    }

    // The flash is keyed by row (see CellFields.KeyedFlash): onToggle (wired by every caller to
    // mainModel.setUpdate(terminal.getVoltageLevel())) refreshes the table, which can rebind this cell to another row.
    private static <S> void connectedColumn(TableColumn<S, S> column, Function<S, List<Terminal>> terminalsGetter, Consumer<Terminal> onToggle,
                                            Function<List<Node>, Node> layout) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        CellFields.KeyedFlash flash = new CellFields.KeyedFlash();
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                S row = empty ? null : item;
                setGraphic(row == null ? null
                        : layout.apply(terminalsGetter.apply(row).stream().<Node>map(terminal -> connectedCheckBox(this, row, terminal, onToggle, flash)).toList()));
                flash.show(this, row);
            }
        });
        disableUnlessTableEditable(column);
    }

    static <S> void configureComponentColumn(TableColumn<S, Integer> column, Function<S, Bus> busGetter,
                                              Function<Bus, Component> componentGetter) {
        column.setCellValueFactory(cellData -> {
            Bus bus = busGetter.apply(cellData.getValue());
            Component component = bus == null ? null : componentGetter.apply(bus);
            return new ReadOnlyObjectWrapper<>(component == null ? null : component.getNum());
        });
        column.setCellFactory(col -> new TableCell<S, Integer>() {
            @Override
            protected void updateItem(Integer num, boolean empty) {
                super.updateItem(num, empty);
                setText(empty ? null : num == null ? "-" : String.valueOf(num));
            }
        });
        column.setComparator(missingLast(column, Objects::isNull));
    }

    static <S> void configureTwoSidedComponentColumn(TableColumn<S, S> column, Function<S, Bus> bus1Getter,
                                                       Function<S, Bus> bus2Getter, Function<Bus, Component> componentGetter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : twoSidedBox(new Label(componentText(bus1Getter.apply(item), componentGetter)),
                                new Label(componentText(bus2Getter.apply(item), componentGetter)), Pos.CENTER));
            }
        });
    }

    private static String componentText(Bus bus, Function<Bus, Component> componentGetter) {
        Component component = bus == null ? null : componentGetter.apply(bus);
        return component == null ? "-" : String.valueOf(component.getNum());
    }

    // also used for read-only values, in columns made non-editable
    static <S> void configureDoubleColumn(TableColumn<S, Double> column, StringConverter<Double> format) {
        if (column.isEditable()) {
            CellFields.textColumn(column, format, row -> true, CellFields.RowKey.ITEM);
        } else {
            column.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(Double value, boolean empty) {
                    super.updateItem(value, empty);
                    setText(empty ? null : format.toString(value));
                }
            });
        }
    }

    // An editing cell is disabled (greyed, inert) while its table isn't editable, which a table is made while its
    // network is busy with a background job (see AbstractEquipmentTableController.setSceneModel).
    static <S, T> void disableUnlessTableEditable(TableColumn<S, T> column) {
        Callback<TableColumn<S, T>, TableCell<S, T>> factory = column.getCellFactory();
        column.setCellFactory(col -> {
            TableCell<S, T> cell = factory.call(col);
            cell.disableProperty().bind(cell.tableViewProperty()
                    .flatMap(TableView::editableProperty)
                    .map(editable -> !editable)
                    .orElse(false));
            return cell;
        });
    }

    // Nullable like doubleFormat's "-": unlike configureNullableEditableDoubleColumn, every row can be edited here
    // (the field just may hold no value), so clearing the text field back to "-"/blank is how a value gets unset.
    static <S> void configureEditableTextColumn(TableColumn<S, String> column) {
        CellFields.textColumn(column, STRING_FORMAT, row -> true, CellFields.RowKey.ITEM);
    }

    // For a field that only exists on some rows (e.g. a boundary line's optional generation part): shows "-" where the
    // getter returns null, editable like configureDoubleColumn otherwise.
    static <S> void configureNullableEditableDoubleColumn(TableColumn<S, Double> column, Function<S, Double> getter,
                                                            BiConsumer<S, Double> setter, StringConverter<Double> format) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(getter.apply(cellData.getValue())));
        CellFields.textColumn(column, format, row -> getter.apply(row) != null, CellFields.RowKey.ITEM);
        column.setOnEditCommit(event -> setter.accept(event.getRowValue(), event.getNewValue()));
        column.setComparator(missingLast(column, TableColumnSupport::isMissing));
    }

    // Same "-" when absent as configureNullableEditableDoubleColumn, for a field that only exists on some rows.
    static <S> void configureNullableEditableBooleanColumn(TableColumn<S, Boolean> column, Function<S, Boolean> getter,
                                                             BiConsumer<S, Boolean> setter) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(getter.apply(cellData.getValue())));
        column.setCellFactory(col -> new TableCell<S, Boolean>() {
            @Override
            protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                } else if (value == null) {
                    setGraphic(null);
                    setText("-");
                } else {
                    setText(null);
                    setGraphic(booleanCheckBox(this, value, checked -> setter.accept(getTableRow().getItem(), checked)));
                }
            }
        });
        disableUnlessTableEditable(column);
        column.setComparator(missingLast(column, Objects::isNull));
    }

    // Shared by the Lines/TieLines/Transformers/BoundaryLines "Loading" columns, the Violations view and
    // FlowViolationsDialog: the loading (percentage of the permanent limit) as a LoadingBar, orange/red when violated,
    // green otherwise, with the violation's acceptable duration in minutes, plus an info button opening onInfoClick if
    // not null. Sorts most severe first.
    static <S> void configureLoadingColumn(TableColumn<S, Loading> column, Function<S, Loading> loadingGetter,
                                           Supplier<GuiParameters> guiParameters, BiConsumer<Window, S> onInfoClick) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(loadingGetter.apply(cellData.getValue())));
        column.setCellFactory(col -> new LoadingTableCell<>(guiParameters, onInfoClick));
        column.setComparator(missingLast(column, Objects::isNull));
    }

    private static final class LoadingTableCell<S> extends TableCell<S, Loading> {
        private final Supplier<GuiParameters> guiParameters;
        private final LoadingBar bar = new LoadingBar();
        private final Label label = new Label();
        private final HBox box = new HBox(6);

        LoadingTableCell(Supplier<GuiParameters> guiParameters, BiConsumer<Window, S> onInfoClick) {
            this.guiParameters = guiParameters;
            box.setAlignment(Pos.CENTER_LEFT);
            if (onInfoClick != null) {
                Button infoButton = new Button();
                infoButton.setGraphic(new FontIcon("mdi2i-information-outline"));
                infoButton.getStyleClass().add("icon-button");
                infoButton.setTooltip(new Tooltip(Messages.get("violations.details")));
                // a read-only popup: unlike configureInfoButtonColumn's, not disabled while the network is busy
                infoButton.setOnAction(event -> {
                    S row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row != null) {
                        onInfoClick.accept(infoButton.getScene().getWindow(), row);
                    }
                });
                box.getChildren().add(infoButton);
            }
            box.getChildren().addAll(bar, label);
        }

        @Override
        protected void updateItem(Loading loading, boolean empty) {
            super.updateItem(loading, empty);
            if (empty || loading == null) {
                setGraphic(null);
                setTooltip(null);
            } else {
                bar.setLoading(loading.percent(), loading.isViolated());
                label.setText(loadingText(loading, guiParameters.get()));
                setTooltip(loading.isViolated() ? new Tooltip(flowViolationText(loading.violation())) : null);
                setGraphic(box);
            }
        }
    }

    // e.g. "120.0 % 10'", the acceptable duration of a temporary limit violation in minutes
    static String loadingText(Loading loading, GuiParameters guiParameters) {
        String text = guiParameters.format(GuiParameters.Quantity.PERCENTAGE, loading.percent()) + " %";
        int acceptableDuration = loading.isViolated() ? loading.violation().violation().getAcceptableDuration() : Integer.MAX_VALUE;
        return acceptableDuration == Integer.MAX_VALUE ? text : text + " " + minutesText(acceptableDuration);
    }

    static String minutesText(int seconds) {
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "'";
    }

    static String flowViolationText(FlowViolation violation) {
        LimitViolation limitViolation = violation.violation();
        return Messages.get("violations.flowTooltip", limitViolationTypeText(limitViolation.getLimitType()),
                Objects.requireNonNullElse(limitViolation.getLimitName(), "-"),
                limitViolation.getSide() == null ? "-" : limitViolation.getSide().getNum(), acceptableDurationText(limitViolation.getAcceptableDuration()));
    }

    // as a string: MessageFormat would group the digits, e.g. "2,400 s"
    static String acceptableDurationText(int acceptableDuration) {
        return acceptableDuration == Integer.MAX_VALUE ? Messages.get("limits.permanent") : Messages.get("limits.duration", String.valueOf(acceptableDuration));
    }

    static String limitViolationTypeText(LimitViolationType type) {
        return Messages.get("desktop.common.limitViolationType." + type.name());
    }

    /**
     * Loading from 0 to 200 % (capped, an arrow at the end then), with a marker at 100 % (mid-width) and the part
     * beyond it in the alert color; all green when not violated. Fixed width, so that the 100 % markers of a column
     * stay aligned whatever the width of the text next to them.
     */
    static final class LoadingBar extends Pane {
        private static final double WIDTH = 80;
        private static final double HEIGHT = 10;
        private static final double MAX_PERCENT = 200;

        private final Region fill = new Region();
        private final Region excess = new Region();
        private final Region marker = new Region();
        private final Polygon overflow = overflowArrow(true);
        private double percent;

        LoadingBar() {
            getStyleClass().add("violation-bar");
            fill.getStyleClass().add("violation-bar-fill");
            excess.getStyleClass().add("violation-bar-excess");
            marker.getStyleClass().add("violation-bar-marker");
            getChildren().addAll(fill, excess, marker, overflow);
            setMinSize(WIDTH, HEIGHT);
            setPrefSize(WIDTH, HEIGHT);
            setMaxSize(WIDTH, HEIGHT);
        }

        void setLoading(double percent, boolean violated) {
            this.percent = percent;
            overflow.setVisible(percent > MAX_PERCENT);
            getStyleClass().remove("violation-bar-ok");
            if (!violated) {
                getStyleClass().add("violation-bar-ok");
            }
            requestLayout();
        }

        double getPercent() {
            return percent;
        }

        boolean isViolated() {
            return !getStyleClass().contains("violation-bar-ok");
        }

        boolean isTruncated() {
            return overflow.isVisible();
        }

        @Override
        protected void layoutChildren() {
            double barWidth = getWidth();
            double width = Double.isNaN(percent) ? 0 : Math.clamp(percent, 0, MAX_PERCENT) / MAX_PERCENT * barWidth;
            fill.resizeRelocate(0, 0, Math.min(width, barWidth / 2), HEIGHT);
            excess.resizeRelocate(barWidth / 2, 0, Math.max(0, width - barWidth / 2), HEIGHT);
            marker.resizeRelocate(Math.floor(barWidth / 2), 0, 1, HEIGHT);
            overflow.setLayoutX(barWidth - OVERFLOW_ARROW_WIDTH);
        }
    }

    private static final double OVERFLOW_ARROW_WIDTH = 7;

    // A chevron at a bar's end, shown when the value is beyond the bar's scale
    private static Polygon overflowArrow(boolean pointingRight) {
        double w = OVERFLOW_ARROW_WIDTH;
        Polygon arrow = pointingRight
                ? new Polygon(0, 0, w, 4, 0, 8, 2, 4)
                : new Polygon(w, 0, 0, 4, w, 8, w - 2, 4);
        arrow.getStyleClass().add("bar-overflow");
        // set by geometry, not bounds: relocate() would shift it by half the stroke width
        arrow.setLayoutY(1);
        arrow.setManaged(false);
        arrow.setVisible(false);
        return arrow;
    }

    // Shared by AbstractBusesController and BusbarSectionsController's "Voltage Range" column, and the Violations
    // view: a VoltageBar and the percentage of the nominal voltage. Sorts most severe first.
    static <S> void configureVoltageRangeColumn(TableColumn<S, VoltageRange> column, Function<S, VoltageRange> rangeGetter,
                                                Supplier<GuiParameters> guiParameters) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(rangeGetter.apply(cellData.getValue())));
        column.setCellFactory(col -> new VoltageRangeTableCell<>(guiParameters));
        column.setComparator(missingLast(column, Objects::isNull));
    }

    private static final class VoltageRangeTableCell<S> extends TableCell<S, VoltageRange> {
        private final Supplier<GuiParameters> guiParameters;
        private final VoltageBar bar = new VoltageBar();
        private final Label label = new Label();
        private final HBox box = new HBox(6, bar, label);

        VoltageRangeTableCell(Supplier<GuiParameters> guiParameters) {
            this.guiParameters = guiParameters;
            box.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(VoltageRange range, boolean empty) {
            super.updateItem(range, empty);
            if (empty || range == null) {
                setGraphic(null);
                setTooltip(null);
            } else {
                bar.setRange(range);
                label.setText(guiParameters.get().format(GuiParameters.Quantity.PERCENTAGE, range.percentOfNominal()) + " %");
                setGraphic(box);
                setTooltip(new Tooltip(voltageRangeTooltip(range, guiParameters.get())));
            }
        }
    }

    private static String voltageRangeTooltip(VoltageRange range, GuiParameters guiParameters) {
        Function<Double, String> kV = value -> Double.isNaN(value) ? "-" : guiParameters.format(GuiParameters.Quantity.VOLTAGE, value) + " kV";
        return Messages.get("buses.voltageRange.tooltip", kV.apply(range.v()), kV.apply(range.nominalV()),
                kV.apply(range.lowLimit()), kV.apply(range.highLimit()));
    }

    /**
     * A voltage on a scale from 80 % to 120 % of the nominal voltage (capped, an arrow at that end then), the nominal at
     * the center: filled from the nominal to the voltage, the part beyond a limit in red, with the allowed band between
     * the low and high limits (up to the scale's end on a side without limit). Fixed width, so that the nominal markers
     * of a column stay aligned.
     */
    static final class VoltageBar extends Pane {
        private static final double WIDTH = 100;
        private static final double HEIGHT = 10;
        private static final double HALF_SCALE = 0.2;

        private final Region band = new Region();
        private final Region fill = new Region();
        private final Region lowExcess = new Region();
        private final Region highExcess = new Region();
        private final Region nominal = new Region();
        private final Region lowMarker = new Region();
        private final Region highMarker = new Region();
        private final Polygon lowOverflow = overflowArrow(false);
        private final Polygon highOverflow = overflowArrow(true);
        private VoltageRange range;

        VoltageBar() {
            getStyleClass().add("voltage-bar");
            band.getStyleClass().add("voltage-bar-band");
            fill.getStyleClass().add("voltage-bar-fill");
            lowExcess.getStyleClass().add("voltage-bar-excess");
            highExcess.getStyleClass().add("voltage-bar-excess");
            nominal.getStyleClass().add("voltage-bar-nominal");
            lowMarker.getStyleClass().add("voltage-bar-limit");
            highMarker.getStyleClass().add("voltage-bar-limit");
            getChildren().addAll(band, fill, lowExcess, highExcess, nominal, lowMarker, highMarker, lowOverflow, highOverflow);
            setMinSize(WIDTH, HEIGHT);
            setPrefSize(WIDTH, HEIGHT);
            setMaxSize(WIDTH, HEIGHT);
        }

        void setRange(VoltageRange range) {
            this.range = range;
            double offset = scaleOffset(range.v());
            lowOverflow.setVisible(offset < -1);
            highOverflow.setVisible(offset > 1);
            requestLayout();
        }

        VoltageRange getRange() {
            return range;
        }

        // -1 at the scale's low end, 0 at the nominal voltage, 1 at its high end
        private double scaleOffset(double v) {
            return (v - range.nominalV()) / range.nominalV() / HALF_SCALE;
        }

        private double x(double v) {
            double width = getWidth();
            return Math.clamp(width / 2 + scaleOffset(v) * width / 2, 0, width);
        }

        private static void place(Region region, double from, double to) {
            region.setVisible(to > from);
            region.resizeRelocate(from, 2, Math.max(0, to - from), HEIGHT - 4);
        }

        boolean isTruncated() {
            return lowOverflow.isVisible() || highOverflow.isVisible();
        }

        @Override
        protected void layoutChildren() {
            if (range == null) {
                return;
            }
            double width = getWidth();
            boolean hasLow = !Double.isNaN(range.lowLimit());
            boolean hasHigh = !Double.isNaN(range.highLimit());
            double bandStart = hasLow ? x(range.lowLimit()) : 0;
            double bandEnd = hasHigh ? x(range.highLimit()) : width;
            band.resizeRelocate(bandStart, 0, Math.max(0, bandEnd - bandStart), HEIGHT);
            // from the nominal to the voltage: within the limits in green, beyond them in red
            double center = Math.floor(width / 2);
            double value = x(range.v());
            double from = Math.min(center, value);
            double to = Math.max(center, value);
            place(fill, Math.max(from, bandStart), Math.min(to, bandEnd));
            place(lowExcess, from, Math.min(to, bandStart));
            place(highExcess, Math.max(from, bandEnd), to);
            nominal.resizeRelocate(center, 0, 1, HEIGHT);
            lowMarker.setVisible(hasLow);
            lowMarker.resizeRelocate(bandStart, 0, 2, HEIGHT);
            highMarker.setVisible(hasHigh);
            highMarker.resizeRelocate(Math.max(0, bandEnd - 2), 0, 2, HEIGHT);
            lowOverflow.setLayoutX(0);
            highOverflow.setLayoutX(width - OVERFLOW_ARROW_WIDTH);
        }
    }

    // Generic read-only counterpart of configureNullableIntColumn, for a field whose type has no dedicated
    // formatting of its own (e.g. an Optional-backed enum) - "-" when absent, T::toString otherwise.
    static <S, T extends Comparable<T>> void configureNullableColumn(TableColumn<S, T> column, Function<S, T> valueGetter) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(valueGetter.apply(cellData.getValue())));
        column.setCellFactory(col -> new TableCell<S, T>() {
            @Override
            protected void updateItem(T value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value == null ? "-" : value.toString());
            }
        });
        column.setComparator(missingLast(column, Objects::isNull));
    }

    static <S> void configureNullableIntColumn(TableColumn<S, Integer> column, Function<S, Integer> valueGetter) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(valueGetter.apply(cellData.getValue())));
        column.setCellFactory(col -> new TableCell<S, Integer>() {
            @Override
            protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value == null ? "-" : String.valueOf(value));
            }
        });
        column.setComparator(missingLast(column, Objects::isNull));
    }

    static <S> void configureNullableDoubleColumn(TableColumn<S, Double> column, Function<S, Double> valueGetter,
                                                   StringConverter<Double> format) {
        configureNullableDoubleColumn(column, valueGetter, row -> format);
    }

    // For a column whose unit depends on the row, e.g. a limit violation's value
    static <S> void configureNullableDoubleColumn(TableColumn<S, Double> column, Function<S, Double> valueGetter,
                                                   Function<S, StringConverter<Double>> formatGetter) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(valueGetter.apply(cellData.getValue())));
        column.setCellFactory(col -> new TableCell<S, Double>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                S row = empty || getTableRow() == null ? null : getTableRow().getItem();
                setText(row == null ? null : formatGetter.apply(row).toString(value));
            }
        });
        column.setComparator(missingLast(column, TableColumnSupport::isMissing));
    }

    private static boolean isMissing(Double value) {
        return value == null || value.isNaN();
    }

    static <S> void configureTwoSidedDoubleColumn(TableColumn<S, S> column, Function<S, Double> value1Getter,
                                                   Function<S, Double> value2Getter, StringConverter<Double> format) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : twoSidedBox(new Label(format.toString(value1Getter.apply(item))), new Label(format.toString(value2Getter.apply(item))), Pos.CENTER_RIGHT));
            }
        });
    }

    static <S> void configureTwoSidedTextColumn(TableColumn<S, S> column, Function<S, String> value1Getter,
                                                 Function<S, String> value2Getter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : twoSidedBox(new Label(value1Getter.apply(item)), new Label(value2Getter.apply(item)), Pos.CENTER_LEFT));
            }
        });
    }

    static <S, T> void configureTwoSidedLinkColumn(TableColumn<S, S> column, Function<S, T> value1Getter,
                                                    Function<S, T> value2Getter, BiFunction<T, S, Hyperlink> linkFactory) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : twoSidedBox(linkFactory.apply(value1Getter.apply(item), item), linkFactory.apply(value2Getter.apply(item), item), Pos.CENTER_LEFT));
            }
        });
    }

    // Tall enough for a per-side ChoiceBox (the Regulation Mode columns) rendered in the table's monospace font.
    // Pinning every side of a row with several to this same height (rather than each side's own natural size, e.g. a
    // Label next to a taller control) is what keeps side 1 values horizontally aligned across columns, side 2 as well,
    // side 3 too. A row's single value (e.g. a line's R, or a two-windings transformer's) keeps its own height instead,
    // centered in the row like any single-valued column.
    private static final double SIDE_ROW_HEIGHT = 31;

    static void sideHeight(Node side, int sideCount) {
        if (side instanceof Region region) {
            double height = sideCount > 1 ? SIDE_ROW_HEIGHT : Region.USE_COMPUTED_SIZE;
            region.setMinHeight(height);
            region.setPrefHeight(height);
        }
    }

    private static VBox twoSidedBox(Node first, Node second, Pos alignment) {
        return sidedBox(List.of(first, second), alignment);
    }

    static <S> void configureMultiSidedContainerColumn(TableColumn<S, S> column,
                                                         Function<S, List<? extends Container<?>>> containersGetter,
                                                         BiFunction<Container<?>, S, Node> linkFactory) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : sidedBox(containersGetter.apply(item).stream().<Node>map(container -> linkFactory.apply(container, item)).toList(), Pos.CENTER_LEFT));
            }
        });
    }

    static <S> void configureMultiSidedComponentColumn(TableColumn<S, S> column, Function<S, List<Bus>> busesGetter,
                                                         Function<Bus, Component> componentGetter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : sidedBox(busesGetter.apply(item).stream().<Node>map(bus -> new Label(componentText(bus, componentGetter))).toList(), Pos.CENTER));
            }
        });
    }

    static <S> void configureMultiSidedDoubleColumn(TableColumn<S, S> column, Function<S, List<Double>> valuesGetter,
                                                     StringConverter<Double> format) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : sidedBox(valuesGetter.apply(item).stream().<Node>map(value -> new Label(format.toString(value))).toList(), Pos.CENTER_RIGHT));
            }
        });
    }

    private static VBox sidedBox(List<Node> nodes, Pos alignment) {
        VBox box = new VBox();
        nodes.forEach(node -> sideHeight(node, nodes.size()));
        box.getChildren().addAll(nodes);
        box.setAlignment(alignment);
        return box;
    }

    // Sets one side's value given its index in the row's per-side list (e.g. a TwoWindingsTransformer's ratedU1/
    // ratedU2, or a ThreeWindingsTransformer leg's ratedU) - unlike BiConsumer, there's no built-in 2-arg-plus-index
    // functional interface for this.
    @FunctionalInterface
    interface IndexedDoubleSetter<S> {
        void set(S item, int index, double value);
    }

    // Editable counterpart of configureMultiSidedDoubleColumn, a field per side.
    static <S> void configureMultiSidedEditableDoubleColumn(TableColumn<S, S> column, Function<S, List<Double>> valuesGetter,
                                                              IndexedDoubleSetter<S> setter, StringConverter<Double> format) {
        CellFields.sidedColumn(column, row -> valuesGetter.apply(row).size(), CellFields.CellTextField::new, (field, key, row, side) -> {
            field.bind(key, () -> format.toString(valuesGetter.apply(row).get(side)), text -> setter.set(row, side, format.fromString(text)));
            return true;
        });
    }

    // TableView's descending sort doesn't negate the column comparator, it swaps the compared values
    // (see com.sun.javafx.scene.control.TableColumnComparatorBase), which would otherwise flip a
    // nulls-last comparator into nulls-first. Consulting the column's own sort type here keeps
    // missing ("-") values last regardless of sort direction.
    private static <S, T extends Comparable<T>> Comparator<T> missingLast(TableColumn<S, T> column, Predicate<T> isMissing) {
        return (left, right) -> {
            boolean descending = column.getSortType() == TableColumn.SortType.DESCENDING;
            boolean leftMissing = isMissing.test(left);
            boolean rightMissing = isMissing.test(right);
            if (leftMissing && rightMissing) {
                return 0;
            }
            if (leftMissing) {
                return descending ? -1 : 1;
            }
            if (rightMissing) {
                return descending ? 1 : -1;
            }
            return left.compareTo(right);
        };
    }

    // Editable counterpart of a multi-sided column for a value that may not exist on every side (e.g. a transformer leg
    // without a ratio/phase tap changer): a blank slot where tapChangersGetter's list holds Optional.empty(), a stepper
    // bounded to [low, high] tap position otherwise, writing straight through to the mutable TapChanger.
    static <S> void configureMultiSidedTapChangerColumn(TableColumn<S, S> column,
                                                          Function<S, List<Optional<? extends TapChanger<?, ?, ?, ?>>>> tapChangersGetter) {
        CellFields.sidedColumn(column, row -> tapChangersGetter.apply(row).size(), CellFields.IntStepper::new, (stepper, key, row, side) -> {
            Optional<? extends TapChanger<?, ?, ?, ?>> tapChanger = tapChangersGetter.apply(row).get(side);
            tapChanger.ifPresent(tc -> stepper.bind(key, tc.getLowTapPosition(), tc.getHighTapPosition(), tc::getTapPosition, tc::setTapPosition));
            return tapChanger.isPresent();
        });
    }

    // Shared blank-slot rule for every per-side tap changer sub-field column below (Regulating, Regulation Mode,
    // Regulation Value, Target Deadband, Solved Tap): besides a side lacking this tap changer type altogether,
    // IIDM itself refuses to enable regulation on one that doesn't support on-load tap changing, so those fields
    // are left blank there too rather than shown disabled.
    private static boolean tapChangerFieldEnabled(Optional<? extends TapChanger<?, ?, ?, ?>> tapChangerOpt) {
        return tapChangerOpt.filter(TapChanger::hasLoadTapChangingCapabilities).isPresent();
    }

    static <S> void configureMultiSidedTapChangerBooleanColumn(TableColumn<S, S> column,
                                                                 Function<S, List<Optional<? extends TapChanger<?, ?, ?, ?>>>> tapChangersGetter,
                                                                 Function<TapChanger<?, ?, ?, ?>, Boolean> getter,
                                                                 BiConsumer<TapChanger<?, ?, ?, ?>, Boolean> setter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                List<Node> nodes = tapChangersGetter.apply(item).stream()
                        .<Node>map(opt -> tapChangerFieldEnabled(opt)
                                ? booleanCheckBox(this, getter.apply(opt.get()), checked -> setter.accept(opt.get(), checked))
                                : new Label())
                        .toList();
                setGraphic(sidedBox(nodes, Pos.CENTER));
            }
        });
        disableUnlessTableEditable(column);
    }

    static <S> void configureMultiSidedTapChangerDoubleColumn(TableColumn<S, S> column,
                                                                Function<S, List<Optional<? extends TapChanger<?, ?, ?, ?>>>> tapChangersGetter,
                                                                Function<TapChanger<?, ?, ?, ?>, Double> getter,
                                                                BiConsumer<TapChanger<?, ?, ?, ?>, Double> setter,
                                                                Function<TapChanger<?, ?, ?, ?>, StringConverter<Double>> formatGetter) {
        CellFields.sidedColumn(column, row -> tapChangersGetter.apply(row).size(), CellFields.CellTextField::new, (field, key, row, side) -> {
            Optional<? extends TapChanger<?, ?, ?, ?>> tapChanger = tapChangersGetter.apply(row).get(side);
            if (!tapChangerFieldEnabled(tapChanger)) {
                return false;
            }
            TapChanger<?, ?, ?, ?> tc = tapChanger.get();
            field.bind(key, () -> formatGetter.apply(tc).toString(getter.apply(tc)), text -> setter.accept(tc, formatGetter.apply(tc).fromString(text)));
            return true;
        });
    }

    static <S> void configureMultiSidedTapChangerNullableIntColumn(TableColumn<S, S> column,
                                                                     Function<S, List<Optional<? extends TapChanger<?, ?, ?, ?>>>> tapChangersGetter,
                                                                     Function<TapChanger<?, ?, ?, ?>, Integer> getter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : sidedBox(tapChangersGetter.apply(item).stream()
                                .<Node>map(opt -> new Label(tapChangerFieldEnabled(opt) ? nullableIntText(getter.apply(opt.get())) : ""))
                                .toList(), Pos.CENTER_RIGHT));
            }
        });
    }

    private static String nullableIntText(Integer value) {
        return value == null ? "-" : String.valueOf(value);
    }

    static <S, T> void configureMultiSidedTapChangerChoiceColumn(TableColumn<S, S> column,
                                                                   Function<S, List<Optional<? extends TapChanger<?, ?, ?, ?>>>> tapChangersGetter,
                                                                   Function<TapChanger<?, ?, ?, ?>, T> getter,
                                                                   BiConsumer<TapChanger<?, ?, ?, ?>, T> setter,
                                                                   List<T> choices, StringConverter<T> converter) {
        CellFields.sidedColumn(column, row -> tapChangersGetter.apply(row).size(),
            (cell, flash) -> new CellFields.CellChoiceBox<>(cell, flash, choices, converter), (choiceBox, key, row, side) -> {
                Optional<? extends TapChanger<?, ?, ?, ?>> tapChanger = tapChangersGetter.apply(row).get(side);
                if (!tapChangerFieldEnabled(tapChanger)) {
                    return false;
                }
                TapChanger<?, ?, ?, ?> tc = tapChanger.get();
                choiceBox.bind(key, () -> getter.apply(tc), value -> setter.accept(tc, value));
                return true;
            });
    }

    // A single value of a table with multi-sided rows, as a one-slot multi-sided column like its editable doubles.
    static <S, T> void configureSingleSlotChoiceColumn(TableColumn<S, S> column, Function<S, T> getter, BiConsumer<S, T> setter,
                                                        List<T> choices, StringConverter<T> converter) {
        CellFields.sidedColumn(column, row -> 1, (cell, flash) -> new CellFields.CellChoiceBox<>(cell, flash, choices, converter),
            (choiceBox, key, row, side) -> {
                choiceBox.bind(key, () -> getter.apply(row), value -> setter.accept(row, value));
                return true;
            });
    }

    // The name of an equipment with a detail view, after an info button opening it. Unlike editing controls, the button
    // stays enabled while the network is busy: the (non-modal) detail view has its own busy lock.
    static <S> void configureDetailNameColumn(TableColumn<S, String> column, Consumer<S> onDetail) {
        column.setCellFactory(col -> new DetailNameTableCell<>(onDetail));
    }

    // The info button opening a detail view, before content (e.g. a container link), for name columns whose cells
    // aren't plain text (see configureDetailNameColumn).
    static HBox withDetailButton(Node content, Runnable onDetail) {
        HBox box = new HBox(4, detailButton(onDetail), content);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Button detailButton(Runnable onDetail) {
        Button infoButton = new Button();
        infoButton.setGraphic(new FontIcon("mdi2i-information-outline"));
        // detail-button: tells EditLockTest this button opens a view, not an editing dialog
        infoButton.getStyleClass().addAll("icon-button", "detail-button");
        infoButton.setTooltip(new Tooltip(Messages.get("detail.open.tooltip")));
        infoButton.setOnAction(event -> onDetail.run());
        return infoButton;
    }

    private static final class DetailNameTableCell<S> extends TableCell<S, String> {
        private final Label label = new Label();
        private final HBox box;

        DetailNameTableCell(Consumer<S> onDetail) {
            box = withDetailButton(label, () -> {
                S item = getTableRow() == null ? null : getTableRow().getItem();
                if (item != null) {
                    onDetail.accept(item);
                }
            });
        }

        @Override
        protected void updateItem(String name, boolean empty) {
            super.updateItem(name, empty);
            if (empty || name == null) {
                setGraphic(null);
            } else {
                label.setText(name);
                setGraphic(box);
            }
        }
    }

    // The section field's [min, max] range is per-row (clamped to each shunt compensator's own maximum section count).
    static <S> void configureSpinnerIntColumn(TableColumn<S, S> column, Function<S, Integer> valueGetter,
                                               Function<S, Integer> maxGetter, BiConsumer<S, Integer> setter) {
        CellFields.sidedColumn(column, row -> 1, CellFields.IntStepper::new, (stepper, key, row, side) -> {
            stepper.bind(key, 0, maxGetter.apply(row), () -> valueGetter.apply(row), value -> setter.accept(row, value));
            return true;
        });
    }

    // editable == null means "always editable" (no per-row disabling) - every checkbox column whose value
    // is meaningful on every row.
    static <S> void configureEditableBooleanColumn(TableColumn<S, Boolean> column, Function<S, Boolean> getter,
                                                    BiConsumer<S, Boolean> setter) {
        configureEditableBooleanColumn(column, getter, setter, null);
    }

    // For a row where the field can't be edited (e.g. a switch's retain status, only meaningful in a
    // node/breaker voltage level): the checkbox is disabled (greyed out by the default skin) rather than
    // hidden, so the field's current value stays visible.
    //
    // The flash is keyed by row (see CellFields.KeyedFlash): setter can trigger a mainModel.setUpdate() (e.g. a
    // switch's open/retained status affecting the diagram), which refreshes this very table and can rebind this
    // cell to another row before the flash fades.
    static <S> void configureEditableBooleanColumn(TableColumn<S, Boolean> column, Function<S, Boolean> getter,
                                                    BiConsumer<S, Boolean> setter, Predicate<S> editable) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(getter.apply(cellData.getValue())));
        CellFields.KeyedFlash flash = new CellFields.KeyedFlash();
        column.setCellFactory(col -> new EditableBooleanTableCell<>(setter, editable, flash));
        disableUnlessTableEditable(column);
    }

    private static final class EditableBooleanTableCell<S> extends TableCell<S, Boolean> {
        private final BiConsumer<S, Boolean> setter;
        private final Predicate<S> editable;
        private final CellFields.KeyedFlash flash;

        EditableBooleanTableCell(BiConsumer<S, Boolean> setter, Predicate<S> editable, CellFields.KeyedFlash flash) {
            this.setter = setter;
            this.editable = editable;
            this.flash = flash;
        }

        @Override
        protected void updateItem(Boolean value, boolean empty) {
            super.updateItem(value, empty);
            S row = CellFields.rowOf(this, empty);
            setGraphic(row == null || value == null ? null : checkBox(value, row));
            flash.show(this, row);
        }

        private CheckBox checkBox(boolean value, S row) {
            CheckBox checkBox = new CheckBox();
            checkBox.setSelected(value);
            checkBox.setDisable(editable != null && !editable.test(row));
            checkBox.setOnAction(event -> {
                try {
                    setter.accept(row, checkBox.isSelected());
                    flash.flash(this, row, EDIT_SUCCESS_STYLE_CLASS);
                } catch (PowsyblException e) {
                    checkBox.setSelected(!checkBox.isSelected());
                    showEditErrorPopup(this, e);
                    flash.flash(this, row, EDIT_ERROR_STYLE_CLASS);
                }
            });
            return checkBox;
        }
    }

    static <S, T> void configureChoiceColumn(TableColumn<S, T> column, Function<S, T> getter, BiConsumer<S, T> setter,
                                              List<T> choices, StringConverter<T> converter) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(getter.apply(cellData.getValue())));
        CellFields.choiceColumn(column, choices, converter);
        column.setOnEditCommit(event -> setter.accept(event.getRowValue(), event.getNewValue()));
    }

    private static <S> CheckBox connectedCheckBox(TableCell<?, ?> cell, S row, Terminal terminal, Consumer<Terminal> onToggle, CellFields.KeyedFlash flash) {
        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(terminal.isConnected());
        checkBox.setOnAction(event -> {
            try {
                toggleConnection(terminal);
                flash.flash(cell, row, EDIT_SUCCESS_STYLE_CLASS);
                onToggle.accept(terminal);
            } catch (PowsyblException e) {
                checkBox.setSelected(terminal.isConnected());
                showEditErrorPopup(cell, e);
                flash.flash(cell, row, EDIT_ERROR_STYLE_CLASS);
            }
        });
        return checkBox;
    }

    // Shared by every editable boolean column so its checkbox renders identically to connectedCheckBox's.
    private static CheckBox booleanCheckBox(TableCell<?, ?> cell, boolean selected, Consumer<Boolean> onChange) {
        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(selected);
        checkBox.setOnAction(event -> {
            boolean newValue = checkBox.isSelected();
            try {
                onChange.accept(newValue);
                flashEditSuccess(cell);
            } catch (PowsyblException e) {
                checkBox.setSelected(!newValue);
                notifyEditError(cell, e);
            }
        });
        return checkBox;
    }

    // Reports a rejected edit (e.g. an IIDM validation exception from a setter such as Generator::setVoltageRegulatorOn):
    // a ControlsFX popup with the exception message (not its stack trace), anchored to the failing node, plus a brief
    // red flash on it. Does not revert the displayed value - callers do that themselves.
    public static void notifyEditError(Node node, PowsyblException exception) {
        showEditErrorPopup(node, exception);
        flash(node, EDIT_ERROR_STYLE_CLASS);
    }

    static void showEditErrorPopup(Node node, PowsyblException exception) {
        Notifications.create()
                .owner(node)
                .title(Messages.get("desktop.common.editError.title"))
                .text(exception.getMessage())
                .showError();
    }

    // Briefly flashes a cell/field green after a setter call applied successfully.
    public static void flashEditSuccess(Node node) {
        flash(node, EDIT_SUCCESS_STYLE_CLASS);
    }

    private static void flash(Node node, String styleClass) {
        node.getStyleClass().add(styleClass);
        PauseTransition pause = new PauseTransition(flashDuration(styleClass));
        pause.setOnFinished(event -> node.getStyleClass().remove(styleClass));
        pause.play();
    }

    // connect()/disconnect() report a no-op (e.g. no switch that can be operated) by returning false, not throwing
    private static void toggleConnection(Terminal terminal) {
        boolean changed = terminal.isConnected() ? terminal.disconnect() : terminal.connect();
        if (!changed) {
            throw new PowsyblException(Messages.get("desktop.common.editError.connectionUnchanged"));
        }
    }
}
