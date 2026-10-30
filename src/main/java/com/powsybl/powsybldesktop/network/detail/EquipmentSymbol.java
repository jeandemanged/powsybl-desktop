/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.commons.PowsyblException;
import com.powsybl.diagram.components.ComponentSize;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.powsybldesktop.diagram.BaseVoltages;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.sld.library.SldComponent;
import com.powsybl.sld.library.SldComponentLibrary;
import com.powsybl.sld.library.SldComponentTypeName;
import com.powsybl.sld.model.coordinate.Orientation;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An equipment drawn as in a single line diagram, above its detail view's tabs: with the diagrams' component library
 * (SLD parameters) and its styles, colored by nominal voltage with the diagrams' base voltage colors (as the map), a
 * disconnected end grey. Each end shows its bus's solved voltage and angle and its connection, editable, and its wire
 * the solved P, Q and I. Two-sided equipment is laid out left to right (side 1, or a boundary line's network side, on
 * the left), a three-winding transformer's side 1 on top, sides 2 and 3 below, left and right; an injection or a
 * busbar section hangs below its bus. Redrawn when the component library or the equipment's component changes (e.g.
 * a phase tap changer added), otherwise only re-read.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class EquipmentSymbol extends Pane implements DetailPart {

    private static final double SCALE = 3;
    // in component units, i.e. before SCALE, where the library's styles don't set it
    private static final double STROKE_WIDTH = 0.7;
    // an outlined text (e.g. converter stations') thicker blurs it
    private static final double TEXT_STROKE_WIDTH = 0.2;
    private static final double WIRE_WIDTH = 2;
    private static final double BUS_WIDTH = 5;
    private static final double WIRE = 150;
    private static final double VERTICAL_WIRE = 70;
    private static final double LINE = 160;
    private static final double DC_LINE = 80;
    private static final double BUS = 120;
    private static final double BOX_WIDTH = 150;
    private static final double BOX_HEIGHT = 64;
    private static final double FLOWS_WIDTH = 140;
    private static final double FLOWS_HEIGHT = 56;
    private static final double GAP = 8;
    private static final double LEG_OFFSET = 110;
    private static final String DEFAULT_COLOR = "#000000";
    // the single line diagrams' topological style's
    private static final Color DISCONNECTED = Color.web("#808080");
    private static final BaseVoltages BASE_VOLTAGES = new BaseVoltages();
    // by library name
    private static final Map<String, Map<String, Style>> STYLES = new HashMap<>();
    private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^}]*)}");
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern SINGLE_CLASS = Pattern.compile("\\.[\\w-]+");
    // SVG's
    private static final Style DEFAULT_STYLE = new Style("black", null, null, null);

    private final Supplier<SldComponentLibrary> library;
    private final Supplier<GuiParameters> guiParameters;
    private final Runnable onEdit;
    private final BooleanProperty editable = new SimpleBooleanProperty(true);
    private final Group shapes = new Group();
    private final List<Runnable> refreshers = new ArrayList<>();
    private final List<Painted> painted = new ArrayList<>();
    private Supplier<List<String>> componentTypes = List::of;
    private Runnable drawing = () -> { };
    private List<String> drawn;

    /**
     * @param onEdit run after every applied edit, e.g. {@code mainModel.setUpdate(...)}
     */
    public EquipmentSymbol(Supplier<SldComponentLibrary> library, Supplier<GuiParameters> guiParameters, Runnable onEdit) {
        this.library = library;
        this.guiParameters = guiParameters;
        this.onEdit = onEdit;
        getStyleClass().add("equipment-symbol");
        // not shrunk by the tabs below
        setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);
        getChildren().add(shapes);
    }

    @Override
    public BooleanProperty editableProperty() {
        return editable;
    }

    @Override
    public void refresh() {
        List<String> key = new ArrayList<>(componentTypes.get());
        key.add(library.get().getName());
        if (!key.equals(drawn)) {
            drawn = key;
            shapes.getChildren().clear();
            painted.clear();
            drawing.run();
        }
        painted.forEach(Painted::paint);
        refreshers.forEach(Runnable::run);
    }

    /**
     * An injection of {@code type} (a {@link SldComponentTypeName}) hanging below its bus.
     */
    public EquipmentSymbol injection(Supplier<String> type, Supplier<Terminal> terminal) {
        End end = terminalEnd(terminal);
        return drawn(() -> List.of(type.get()), () -> {
            double x = GAP + BOX_WIDTH / 2;
            double busY = GAP + BOX_HEIGHT + GAP;
            topBus(end, x, busY);
            verticalWire(end, x, busY, busY + VERTICAL_WIRE, true);
            Point2D top = anchors(type.get(), Orientation.DOWN).stream().min(Comparator.comparingDouble(Point2D::getY)).orElse(Point2D.ZERO);
            component(type.get(), Orientation.DOWN, x - top.getX(), busY + VERTICAL_WIRE - top.getY(), _ -> end.color());
        });
    }

    /**
     * A busbar section's bus, with no wire (IIDM gives it no flows) and no connection: disconnecting a busbar section
     * isn't meaningful.
     */
    public EquipmentSymbol busbarSection(BusbarSection busbarSection) {
        End end = end(() -> voltageColor(busbarSection.getTerminal().getVoltageLevel().getNominalV()), null, null, null,
                busbarSection::getV, busbarSection::getAngle, null);
        return drawn(List::of, () -> topBus(end, GAP + BOX_WIDTH / 2, GAP + BOX_HEIGHT + GAP));
    }

    /**
     * A line ({@code type} null) or a two-winding transformer of {@code type} between its sides' buses.
     */
    public EquipmentSymbol branch(Supplier<String> type, Supplier<Terminal> terminal1, Supplier<Terminal> terminal2) {
        End end1 = terminalEnd(terminal1);
        End end2 = terminalEnd(terminal2);
        if (type == null) {
            return drawn(List::of, () -> row(end1, end2, false, x -> line(end1, end2, x)));
        }
        return drawn(() -> List.of(type.get()), () -> row(end1, end2, false, x -> component(type.get(), Orientation.RIGHT, x, subComponent ->
                "WINDING2".equals(subComponent) ? end2.color() : end1.color())));
    }

    /**
     * A boundary line between its network side's bus and its boundary side.
     */
    public EquipmentSymbol boundaryLine(BoundaryLine boundaryLine) {
        End network = terminalEnd(boundaryLine::getTerminal);
        End boundary = end(network.color(), () -> boundaryLine.getBoundary().getP(), () -> boundaryLine.getBoundary().getQ(),
                () -> boundaryLine.getBoundary().getI(), () -> boundaryLine.getBoundary().getV(), () -> boundaryLine.getBoundary().getAngle(), null);
        return drawn(List::of, () -> row(network, boundary, true, x -> line(network, network, x)));
    }

    /**
     * An HVDC line's converter stations between their buses, linked by the DC line.
     */
    public EquipmentSymbol hvdcLine(HvdcLine hvdcLine) {
        End end1 = terminalEnd(() -> hvdcLine.getConverterStation1().getTerminal());
        End end2 = terminalEnd(() -> hvdcLine.getConverterStation2().getTerminal());
        Supplier<Color> dcColor = () -> voltageColor(hvdcLine.getNominalV());
        Supplier<String> type1 = () -> stationType(hvdcLine.getConverterStation(TwoSides.ONE));
        Supplier<String> type2 = () -> stationType(hvdcLine.getConverterStation(TwoSides.TWO));
        return drawn(() -> List.of(type1.get(), type2.get()), () -> row(end1, end2, false, x -> {
            double dcStart = component(type1.get(), Orientation.RIGHT, x, _ -> end1.color());
            wire(dcStart, rowY(), dcStart + DC_LINE, rowY(), dcColor, WIRE_WIDTH);
            return component(type2.get(), Orientation.LEFT, dcStart + DC_LINE, _ -> end2.color());
        }));
    }

    /**
     * A three-winding transformer: side 1's bus on top, sides 2 and 3's below, left and right. Each winding takes the
     * color of the side it faces.
     */
    public EquipmentSymbol threeWindingsTransformer(ThreeWindingsTransformer transformer) {
        End end1 = terminalEnd(transformer.getLeg1()::getTerminal);
        End end2 = terminalEnd(transformer.getLeg2()::getTerminal);
        End end3 = terminalEnd(transformer.getLeg3()::getTerminal);
        String type = SldComponentTypeName.THREE_WINDINGS_TRANSFORMER;
        return drawn(() -> List.of(type), () -> drawThreeWindingsTransformer(type, end1, end2, end3));
    }

    private void drawThreeWindingsTransformer(String type, End end1, End end2, End end3) {
        List<Point2D> anchors = anchors(type, Orientation.DOWN);
        Point2D top = anchors.stream().min(Comparator.comparingDouble(Point2D::getY)).orElseThrow();
        Point2D left = anchors.stream().min(Comparator.comparingDouble(Point2D::getX)).orElseThrow();
        Point2D right = anchors.stream().max(Comparator.comparingDouble(Point2D::getX)).orElseThrow();
        double x2 = GAP + Math.max(FLOWS_WIDTH + GAP, Math.max(BOX_WIDTH, BUS) / 2);
        double centerX = x2 + LEG_OFFSET - left.getX();
        double busY = GAP + BOX_HEIGHT + GAP;
        double centerY = busY + VERTICAL_WIRE - top.getY();
        topBus(end1, centerX + top.getX(), busY);
        verticalWire(end1, centerX + top.getX(), busY, busY + VERTICAL_WIRE, true);
        Map<String, Supplier<Color>> windingColors = new HashMap<>();
        Map<String, Point2D> windings = component(type, Orientation.DOWN, centerX, centerY,
                subComponent -> () -> windingColors.getOrDefault(subComponent, end1.color()).get());
        List<String> byHeight = windings.keySet().stream().sorted(Comparator.comparingDouble(name -> windings.get(name).getY())).toList();
        windingColors.put(byHeight.getFirst(), end1.color());
        List<String> bottom = byHeight.subList(1, byHeight.size()).stream().sorted(Comparator.comparingDouble(name -> windings.get(name).getX())).toList();
        windingColors.put(bottom.getFirst(), end2.color());
        windingColors.put(bottom.getLast(), end3.color());
        leg(end2, centerX + left.getX(), centerY + left.getY(), centerX + left.getX() - LEG_OFFSET, false);
        leg(end3, centerX + right.getX(), centerY + right.getY(), centerX + right.getX() + LEG_OFFSET, true);
    }

    private EquipmentSymbol drawn(Supplier<List<String>> componentTypes, Runnable drawing) {
        this.componentTypes = componentTypes;
        this.drawing = drawing;
        return this;
    }

    private static String stationType(HvdcConverterStation<?> station) {
        return station.getHvdcType() == HvdcConverterStation.HvdcType.VSC
                ? SldComponentTypeName.VSC_CONVERTER_STATION : SldComponentTypeName.LCC_CONVERTER_STATION;
    }

    /**
     * Left end's bus, wire, {@code middle} from a start abscissa to its end one, right end's wire and bus (or
     * boundary point), along {@link #rowY}.
     */
    private void row(End left, End right, boolean rightBoundary, DoubleUnaryOperator middle) {
        double y = rowY();
        double leftBus = GAP + BOX_WIDTH + GAP;
        wire(leftBus, y - BUS / 4, leftBus, y + BUS / 4, left.color(), BUS_WIDTH);
        place(left.bus(), leftBus - GAP - BOX_WIDTH, y - BOX_HEIGHT / 2, BOX_WIDTH, BOX_HEIGHT, Pos.CENTER_RIGHT);
        horizontalWire(left, leftBus, leftBus + WIRE, y);
        double middleEnd = middle.applyAsDouble(leftBus + WIRE);
        double rightBus = middleEnd + WIRE;
        horizontalWire(right, middleEnd, rightBus, y);
        if (rightBoundary) {
            Circle point = new Circle(rightBus, y, BUS_WIDTH);
            shapes.getChildren().add(point);
            painted.add(new Painted(point, "var", null, right.color()));
        } else {
            wire(rightBus, y - BUS / 4, rightBus, y + BUS / 4, right.color(), BUS_WIDTH);
        }
        place(right.bus(), rightBus + GAP, y - BOX_HEIGHT / 2, BOX_WIDTH, BOX_HEIGHT, Pos.CENTER_LEFT);
    }

    private static double rowY() {
        return GAP + FLOWS_HEIGHT + GAP;
    }

    /**
     * A line from {@code x}, half in each end's color.
     */
    private double line(End end1, End end2, double x) {
        wire(x, rowY(), x + LINE / 2, rowY(), end1.color(), WIRE_WIDTH);
        wire(x + LINE / 2, rowY(), x + LINE, rowY(), end2.color(), WIRE_WIDTH);
        return x + LINE;
    }

    /**
     * A component along {@link #rowY}, its left anchor at {@code x}; returns its right anchor's abscissa.
     */
    private double component(String type, Orientation orientation, double x, Function<String, Supplier<Color>> colors) {
        List<Point2D> anchors = anchors(type, orientation);
        Point2D left = anchors.stream().min(Comparator.comparingDouble(Point2D::getX)).orElse(Point2D.ZERO);
        Point2D right = anchors.stream().max(Comparator.comparingDouble(Point2D::getX)).orElse(Point2D.ZERO);
        double centerX = x - left.getX();
        component(type, orientation, centerX, rowY() - left.getY(), colors);
        return centerX + right.getX();
    }

    private void leg(End end, double anchorX, double anchorY, double x, boolean flowsRight) {
        wire(anchorX, anchorY, x, anchorY, end.color(), WIRE_WIDTH);
        verticalWire(end, x, anchorY, anchorY + VERTICAL_WIRE, flowsRight);
        double busY = anchorY + VERTICAL_WIRE;
        wire(x - BUS / 2, busY, x + BUS / 2, busY, end.color(), BUS_WIDTH);
        place(end.bus(), x - BOX_WIDTH / 2, busY + GAP, BOX_WIDTH, BOX_HEIGHT, Pos.TOP_CENTER);
    }

    private void topBus(End end, double x, double busY) {
        wire(x - BUS / 2, busY, x + BUS / 2, busY, end.color(), BUS_WIDTH);
        place(end.bus(), x - BOX_WIDTH / 2, busY - GAP - BOX_HEIGHT, BOX_WIDTH, BOX_HEIGHT, Pos.BOTTOM_CENTER);
    }

    private void verticalWire(End end, double x, double y1, double y2, boolean flowsRight) {
        wire(x, y1, x, y2, end.color(), WIRE_WIDTH);
        double y = (y1 + y2 - FLOWS_HEIGHT) / 2;
        if (flowsRight) {
            place(end.flows(), x + GAP, y, FLOWS_WIDTH, FLOWS_HEIGHT, Pos.CENTER_LEFT);
        } else {
            place(end.flows(), x - GAP - FLOWS_WIDTH, y, FLOWS_WIDTH, FLOWS_HEIGHT, Pos.CENTER_RIGHT);
        }
    }

    private void horizontalWire(End end, double x1, double x2, double y) {
        wire(x1, y, x2, y, end.color(), WIRE_WIDTH);
        place(end.flows(), x1, y - GAP / 2 - FLOWS_HEIGHT, x2 - x1, FLOWS_HEIGHT, Pos.BOTTOM_CENTER);
    }

    private void wire(double x1, double y1, double x2, double y2, Supplier<Color> color, double width) {
        Line line = new Line(x1, y1, x2, y2);
        line.setStrokeWidth(width);
        shapes.getChildren().add(line);
        painted.add(new Painted(line, null, "var", color));
    }

    private static void place(VBox box, double x, double y, double width, double height, Pos alignment) {
        box.setMinSize(width, height);
        box.setPrefSize(width, height);
        box.setMaxSize(width, height);
        box.setAlignment(alignment);
        box.relocate(x, y);
    }

    /**
     * The anchors of {@code type} oriented as {@code orientation}, as single line diagrams transform them, relative to
     * its center.
     */
    private List<Point2D> anchors(String type, Orientation orientation) {
        SldComponentLibrary componentLibrary = library.get();
        SldComponent.Transformation transformation = componentLibrary.getTransformations(type).get(orientation);
        return componentLibrary.getAnchorPoints(type).stream()
                .map(anchor -> anchor.transformAnchorPoint(orientation, transformation))
                .map(anchor -> new Point2D(anchor.getX() * SCALE, anchor.getY() * SCALE))
                .toList();
    }

    /**
     * The library's drawing of {@code type} centered on ({@code x}, {@code y}), oriented as single line diagrams do,
     * with the library's styles, its subcomponents painted in {@code colors}; returns their centers.
     */
    private Map<String, Point2D> component(String type, Orientation orientation, double x, double y, Function<String, Supplier<Color>> colors) {
        SldComponentLibrary componentLibrary = library.get();
        ComponentSize size = componentLibrary.getSize(type);
        Group group = new Group();
        group.getTransforms().addAll(new Translate(x, y), new Scale(SCALE, SCALE), new Translate(-size.width() / 2, -size.height() / 2));
        SldComponent.Transformation transformation = componentLibrary.getTransformations(type).get(orientation);
        if (transformation == SldComponent.Transformation.ROTATION) {
            group.getTransforms().add(new Rotate(orientation.toRotationAngle(), size.width() / 2, size.height() / 2));
        } else if (transformation == SldComponent.Transformation.FLIP) {
            group.getTransforms().addAll(new Scale(1, -1), new Translate(0, -size.height()));
        }
        Map<String, Style> styles = styles(componentLibrary);
        Style componentStyle = DEFAULT_STYLE.with(componentLibrary.getComponentStyleClass(type).map(styles::get).orElse(null));
        Map<String, Group> subComponents = new LinkedHashMap<>();
        componentLibrary.getSvgElements(type).forEach((subComponent, elements) -> {
            Style style = componentStyle.with(componentLibrary.getSubComponentStyleClass(type, subComponent).map(styles::get).orElse(null));
            Group subGroup = new Group();
            elements.forEach(element -> addShapes(subGroup, element, style, colors.apply(subComponent)));
            group.getChildren().add(subGroup);
            subComponents.put(subComponent, subGroup);
        });
        shapes.getChildren().add(group);
        Map<String, Point2D> centers = new LinkedHashMap<>();
        subComponents.forEach((name, subGroup) -> {
            Bounds bounds = group.localToParent(subGroup.getBoundsInParent());
            centers.put(name, new Point2D(bounds.getCenterX(), bounds.getCenterY()));
        });
        return centers;
    }

    private void addShapes(Group group, Element element, Style inherited, Supplier<Color> color) {
        Style style = inherited.with(Style.parse(element.getAttribute("style")));
        Shape shape = switch (element.getTagName()) {
            case "circle" -> new Circle(number(element, "cx"), number(element, "cy"), number(element, "r"));
            case "ellipse" -> new Ellipse(number(element, "cx"), number(element, "cy"), number(element, "rx"), number(element, "ry"));
            case "line" -> new Line(number(element, "x1"), number(element, "y1"), number(element, "x2"), number(element, "y2"));
            case "rect" -> new Rectangle(number(element, "x"), number(element, "y"), number(element, "width"), number(element, "height"));
            case "polygon" -> new Polygon(points(element));
            case "polyline" -> new Polyline(points(element));
            case "path" -> {
                SVGPath path = new SVGPath();
                path.setContent(element.getAttribute("d"));
                yield path;
            }
            case "text" -> {
                Text text = new Text(number(element, "x"), number(element, "y"), element.getTextContent());
                text.setFont(Font.font(style.fontSize() == null ? Font.getDefault().getSize() : style.fontSize()));
                yield text;
            }
            default -> null;
        };
        if (shape != null) {
            shape.setStrokeWidth(style.strokeWidth() != null ? style.strokeWidth() : shape instanceof Text ? TEXT_STROKE_WIDTH : STROKE_WIDTH);
            group.getChildren().add(shape);
            painted.add(new Painted(shape, style.fill(), style.stroke(), color));
        } else if ("g".equals(element.getTagName())) {
            for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element childElement) {
                    addShapes(group, childElement, style, color);
                }
            }
        }
    }

    private static double number(Element element, String attribute) {
        String value = element.getAttribute(attribute);
        return value.isEmpty() ? 0 : Double.parseDouble(value);
    }

    private static double[] points(Element element) {
        return Arrays.stream(element.getAttribute("points").trim().split("[\\s,]+")).mapToDouble(Double::parseDouble).toArray();
    }

    /**
     * The fill, stroke, stroke width and font size of each single class selector of the library's style sheets.
     */
    private static Map<String, Style> styles(SldComponentLibrary componentLibrary) {
        return STYLES.computeIfAbsent(componentLibrary.getName(), _ -> {
            Map<String, Style> styles = new HashMap<>();
            for (URL url : componentLibrary.getCssUrls()) {
                Matcher rule = RULE.matcher(COMMENT.matcher(read(url)).replaceAll(""));
                while (rule.find()) {
                    Style style = Style.parse(rule.group(2));
                    for (String selector : rule.group(1).split(",")) {
                        if (SINGLE_CLASS.matcher(selector.trim()).matches()) {
                            styles.merge(selector.trim().substring(1), style, Style::with);
                        }
                    }
                }
            }
            return styles;
        });
    }

    private static String read(URL url) {
        try (InputStream stream = url.openStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private End terminalEnd(Supplier<Terminal> terminal) {
        return end(() -> terminalColor(terminal.get()), () -> terminal.get().getP(), () -> terminal.get().getQ(), () -> terminal.get().getI(),
                () -> DetailSections.busValue(terminal.get(), true), () -> DetailSections.busValue(terminal.get(), false), terminal);
    }

    /**
     * @param p          null for an end without flows
     * @param connection the terminal connected or disconnected with the end's checkbox, null for none
     */
    private End end(Supplier<Color> color, DoubleSupplier p, DoubleSupplier q, DoubleSupplier i, DoubleSupplier v, DoubleSupplier angle,
                    Supplier<Terminal> connection) {
        VBox bus = new VBox(2,
                value("detail.symbol.v", "detail.busV", DetailForm.KV, GuiParameters.Quantity.VOLTAGE, v),
                value("detail.symbol.angle", "detail.busAngle", DetailForm.DEGREE, GuiParameters.Quantity.ANGLE, angle));
        if (connection != null) {
            bus.getChildren().add(connectedCheckBox(connection));
        }
        VBox flows = new VBox(2);
        if (p != null) {
            flows.getChildren().addAll(
                    value("desktop.common.column.p", null, DetailForm.MW, GuiParameters.Quantity.POWER, p),
                    value("desktop.common.column.q", null, DetailForm.MVAR, GuiParameters.Quantity.POWER, q),
                    value("desktop.common.column.i", null, DetailForm.A, GuiParameters.Quantity.CURRENT, i));
        }
        getChildren().addAll(bus, flows);
        return new End(color, bus, flows);
    }

    private Label value(String nameKey, String tooltipKey, String unit, GuiParameters.Quantity quantity, DoubleSupplier getter) {
        Label label = new Label();
        label.getStyleClass().add("symbol-value");
        if (tooltipKey != null) {
            label.setTooltip(new Tooltip(Messages.get(tooltipKey)));
        }
        refreshers.add(() -> {
            double value = getter.getAsDouble();
            label.setText(Messages.get(nameKey) + " = " + (Double.isNaN(value) ? "-" : guiParameters.get().format(quantity, value) + " " + unit));
        });
        return label;
    }

    private CheckBox connectedCheckBox(Supplier<Terminal> terminal) {
        CheckBox checkBox = new CheckBox(Messages.get("desktop.common.column.connected"));
        checkBox.disableProperty().bind(editable.not());
        checkBox.setOnAction(_ -> {
            DetailForm.apply(checkBox, () -> {
                Terminal t = terminal.get();
                if (!(checkBox.isSelected() ? t.connect() : t.disconnect())) {
                    throw new PowsyblException(Messages.get("desktop.common.editError.connectionUnchanged"));
                }
            }, onEdit);
            checkBox.setSelected(terminal.get().isConnected());
        });
        refreshers.add(() -> checkBox.setSelected(terminal.get().isConnected()));
        return checkBox;
    }

    private static Color terminalColor(Terminal terminal) {
        return terminal.isConnected() ? voltageColor(terminal.getVoltageLevel().getNominalV()) : DISCONNECTED;
    }

    private static Color voltageColor(double nominalV) {
        return Color.web(BASE_VOLTAGES.color(BASE_VOLTAGES.name(nominalV), DEFAULT_COLOR));
    }

    /**
     * What is drawn of a terminal (or a boundary line's boundary side): its color, its bus's values box and its
     * wire's flows box.
     */
    private record End(Supplier<Color> color, VBox bus, VBox flows) {
    }

    /**
     * A shape painted on every refresh: {@code fill} and {@code stroke} as the style sheets give them, "var" being the
     * end's color, null or "none" nothing.
     */
    private record Painted(Shape shape, String fill, String stroke, Supplier<Color> color) {

        void paint() {
            Color endColor = color.get();
            shape.setFill(paint(fill, endColor));
            shape.setStroke(paint(stroke, endColor));
        }

        private static Paint paint(String value, Color endColor) {
            if (value == null || "none".equals(value)) {
                return null;
            }
            return "var".equals(value) ? endColor : Color.web(value);
        }
    }

    /**
     * The style attributes drawing a component uses, null where unset; the base voltage color variable is "var".
     */
    private record Style(String fill, String stroke, Double strokeWidth, Double fontSize) {

        static Style parse(String declarations) {
            String fill = null;
            String stroke = null;
            Double strokeWidth = null;
            Double fontSize = null;
            for (String declaration : declarations.split(";")) {
                int colon = declaration.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String value = declaration.substring(colon + 1).trim();
                switch (declaration.substring(0, colon).trim()) {
                    case "fill" -> fill = color(value);
                    case "stroke" -> stroke = color(value);
                    case "stroke-width" -> strokeWidth = length(value);
                    case "font-size" -> fontSize = length(value);
                    default -> {
                    }
                }
            }
            return new Style(fill, stroke, strokeWidth, fontSize);
        }

        private static String color(String value) {
            return value.startsWith("var(") ? "var" : value;
        }

        private static Double length(String value) {
            return Double.valueOf(value.replace("px", "").trim());
        }

        Style with(Style other) {
            if (other == null) {
                return this;
            }
            return new Style(other.fill != null ? other.fill : fill, other.stroke != null ? other.stroke : stroke,
                    other.strokeWidth != null ? other.strokeWidth : strokeWidth, other.fontSize != null ? other.fontSize : fontSize);
        }
    }
}
