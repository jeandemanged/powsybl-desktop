
// JavaFX's WebKit mis-composites the translate3d-positioned panes/tiles Leaflet uses by default, which
// renders the tile grid and the vector pane as disjoint blocks stuck at stale offsets. Forcing any3d off
// (read as a property at every positioning call, so this takes effect) falls back to plain left/top.
L.Browser.any3d = false;

// animations off: the WebView doesn't reliably finish Leaflet's CSS transitions, and an interrupted one
// leaves stale frames on screen.
var map = L.map('map', {
    zoomAnimation: false,
    fadeAnimation: false,
    markerZoomAnimation: false
}).setView([48.8566, 2.3522], 5);

var osmLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
});
osmLayer.on('tileload', function () {
    window.controller.onTileLoad(true);
});
osmLayer.on('tileerror', function () {
    window.controller.onTileLoad(false);
});
// sea color behind the country outlines; OSM tiles cover it
var SEA_COLOR = '#aad3df';
var LAND_COLOR = '#f2efe9';
var BORDER_COLOR = '#9e9e9e';
var showCountries = false;

// The offline basemap is drawn by the network layer, below the network, rather than as an L.geoJSON: that
// re-projected ~100k points through one Leaflet layer per country on every zoom and filled/stroked each
// country separately, which made it far slower than the network itself.
function setBasemap(name) {
    showCountries = name === 'offline';
    if (showCountries) {
        map.removeLayer(osmLayer);
    } else {
        osmLayer.addTo(map);
    }
    map.getContainer().style.background = showCountries ? SEA_COLOR : '';
    networkLayer.redraw();
}

// Substation markers are sized to SUBSTATION_SPACING_FACTOR times the typical on-screen distance between
// neighbouring substations, within these bounds, so a sparse view gets big markers and a dense one small
// markers that don't pile up.
var SUBSTATION_MIN_SIZE = 2;
var SUBSTATION_MAX_SIZE = 12;
var SUBSTATION_SPACING_FACTOR = 0.5;
var SUBSTATION_CLICK_TOLERANCE = 5;
var LINE_WEIGHT = 2;
// dash and gap lengths for disconnected lines; round caps eat LINE_WEIGHT out of each gap
var LINE_DASH = [6, 6];
// same hit tolerance Leaflet's canvas renderer used for polyline: half the stroke width
var LINE_HIT_DISTANCE = LINE_WEIGHT / 2;
var MAX_HIT_DISTANCE = Math.max(SUBSTATION_MAX_SIZE / 2 + SUBSTATION_CLICK_TOLERANCE, LINE_HIT_DISTANCE);
var CANVAS_PADDING = 0.1;
// the full redraw after a zoom waits for this long without another zoom, showing a scaled preview meanwhile
var ZOOM_REDRAW_DELAY_MS = 150;
var HOVER_THROTTLE_MS = 32;
// between two network chunks, giving the FX thread a pulse to paint the map filling in
var LOAD_CHUNK_DELAY_MS = 16;

// One Leaflet layer per substation/line made every zoom re-project, clip and redraw each layer
// individually. Instead, everything is projected once at zoom 0 into flat arrays - at any zoom a pixel
// position is just that times 2^zoom minus the pixel origin - and drawn into a single canvas, lines as one
// batched path and substations as sprite blits. Hover/click hit-testing goes through a uniform grid over
// the same zoom-0 coordinates.
var data = emptyData();
var countries = buildCountries(COUNTRIES);

// every polygon ring of every country, projected at zoom 0 like the network
function buildCountries(geojson) {
    var rings = [];
    geojson.features.forEach(function (feature) {
        var polygons = feature.geometry.type === 'Polygon' ? [feature.geometry.coordinates] : feature.geometry.coordinates;
        polygons.forEach(function (polygon) {
            polygon.forEach(function (ring) {
                rings.push(ring);
            });
        });
    });
    var pointCount = 0;
    rings.forEach(function (ring) {
        pointCount += ring.length;
    });
    var c = {
        ringStart: new Int32Array(rings.length + 1), ringBounds: new Float64Array(rings.length * 4),
        pointX: new Float64Array(pointCount), pointY: new Float64Array(pointCount)
    };
    var k = 0;
    rings.forEach(function (ring, i) {
        c.ringStart[i] = k;
        var minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
        ring.forEach(function (point) {
            // GeoJSON is [lng, lat]
            var p = map.project([point[1], point[0]], 0);
            c.pointX[k] = p.x;
            c.pointY[k] = p.y;
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            k++;
        });
        c.ringBounds[i * 4] = minX;
        c.ringBounds[i * 4 + 1] = minY;
        c.ringBounds[i * 4 + 2] = maxX;
        c.ringBounds[i * 4 + 3] = maxY;
    });
    c.ringStart[rings.length] = k;
    return c;
}

function emptyData() {
    return {
        colors: [], substationCount: 0, lineCount: 0,
        substationIds: [], substationTexts: [], substationX: new Float64Array(0), substationY: new Float64Array(0),
        substationColor: new Int32Array(0), substationBaseVoltages: [],
        lineIds: [], lineTexts: [], lineStart: new Int32Array(1), lineBounds: new Float64Array(0),
        lineColor: new Int32Array(0), lineBaseVoltages: [], lineDisconnected: new Uint8Array(0),
        pointX: new Float64Array(0), pointY: new Float64Array(0), pointLine: new Int32Array(0),
        grid: null, substationSpacing: Infinity
    };
}

// whole pixels, so zooming only ever creates a handful of sprite sizes
function substationSize(scale) {
    return Math.max(SUBSTATION_MIN_SIZE,
        Math.min(SUBSTATION_MAX_SIZE, Math.round(data.substationSpacing * scale * SUBSTATION_SPACING_FACTOR)));
}

// The grid is built by MapController.buildGrid: cell c lists items[cellStart[c]] to items[cellStart[c + 1] - 1],
// substation indices (>= 0) and line segment start point indices, encoded as -(k + 1).
function cellX(grid, x) {
    return Math.min(grid.size - 1, Math.max(0, Math.floor((x - grid.minX) / grid.cellWidth)));
}

function cellY(grid, y) {
    return Math.min(grid.size - 1, Math.max(0, Math.floor((y - grid.minY) / grid.cellHeight)));
}

function squaredDistanceToSegment(px, py, ax, ay, bx, by) {
    var dx = bx - ax, dy = by - ay;
    var lengthSquared = dx * dx + dy * dy;
    var t = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSquared));
    var ex = px - (ax + t * dx), ey = py - (ay + t * dy);
    return ex * ex + ey * ey;
}

// base voltage name -> true, for the base voltages whose substations and lines are neither drawn nor hit-tested
var hiddenBaseVoltages = {};

function setHiddenBaseVoltages(names) {
    hiddenBaseVoltages = {};
    names.forEach(function (name) {
        hiddenBaseVoltages[name] = true;
    });
    setHovered(null);
    networkLayer.redraw();
}

// Substations win over lines, as they're drawn on top; among each kind the closest one wins.
function hitTest(latlng) {
    var grid = data.grid;
    if (!grid) {
        return null;
    }
    var p = map.project(latlng, 0);
    var scale = map.getZoomScale(map.getZoom(), 0);
    var tolerance = MAX_HIT_DISTANCE / scale;
    var cx0 = cellX(grid, p.x - tolerance), cx1 = cellX(grid, p.x + tolerance);
    var cy0 = cellY(grid, p.y - tolerance), cy1 = cellY(grid, p.y + tolerance);
    var substationLimit = (substationSize(scale) / 2 + SUBSTATION_CLICK_TOLERANCE) / scale;
    var lineLimit = LINE_HIT_DISTANCE / scale;
    var bestSubstation = -1, bestSubstationDistance = substationLimit * substationLimit;
    var bestLine = -1, bestLineDistance = lineLimit * lineLimit;
    for (var cy = cy0; cy <= cy1; cy++) {
        for (var cx = cx0; cx <= cx1; cx++) {
            var cell = cy * grid.size + cx;
            for (var j = grid.cellStart[cell]; j < grid.cellStart[cell + 1]; j++) {
                var item = grid.items[j];
                var distance;
                if (item >= 0) {
                    if (hiddenBaseVoltages[data.substationBaseVoltages[item]]) {
                        continue;
                    }
                    var sx = data.substationX[item] - p.x, sy = data.substationY[item] - p.y;
                    distance = sx * sx + sy * sy;
                    if (distance <= bestSubstationDistance) {
                        bestSubstationDistance = distance;
                        bestSubstation = item;
                    }
                } else {
                    var k = -item - 1;
                    if (hiddenBaseVoltages[data.lineBaseVoltages[data.pointLine[k]]]) {
                        continue;
                    }
                    distance = squaredDistanceToSegment(p.x, p.y,
                        data.pointX[k], data.pointY[k], data.pointX[k + 1], data.pointY[k + 1]);
                    if (distance <= bestLineDistance) {
                        bestLineDistance = distance;
                        bestLine = data.pointLine[k];
                    }
                }
            }
        }
    }
    if (bestSubstation >= 0) {
        return {substation: true, index: bestSubstation, text: data.substationTexts[bestSubstation]};
    }
    if (bestLine >= 0) {
        return {substation: false, index: bestLine, text: data.lineTexts[bestLine]};
    }
    return null;
}

// Positioned like Leaflet's own canvas renderer: sized to the viewport plus padding, placed in the overlay
// pane so it pans along with the tiles during a drag, and redrawn once the view settles.
var NetworkLayer = L.Layer.extend({
    onAdd: function () {
        this._canvas = L.DomUtil.create('canvas');
        this.getPane().appendChild(this._canvas);
        this.redraw();
    },

    onRemove: function () {
        L.DomUtil.remove(this._canvas);
    },

    getEvents: function () {
        // no viewreset: Leaflet's view reset always ends with a moveend, so listening to both drew twice
        return {moveend: this._onMoveEnd, resize: this.redraw};
    },

    // A full redraw of a large network takes long enough to make each zoom step lag, and a burst of mouse wheel
    // steps queue one each. So a zoom first rescales what the canvas already shows, which costs a couple of
    // bitmap copies, and redraws once the zooming has paused.
    _onMoveEnd: function () {
        if (!this._view || map.getZoomScale(map.getZoom(), 0) === this._view.scale) {
            this.redraw();
            return;
        }
        this._previewZoom();
        clearTimeout(this._redrawTimer);
        var layer = this;
        this._redrawTimer = setTimeout(function () {
            layer.redraw();
        }, ZOOM_REDRAW_DELAY_MS);
    },

    // Redraws the current canvas content, as laid out for the previous view, scaled and shifted to the current
    // one: a point p at zoom 0 was at p * oldScale - oldOffset on the canvas, and belongs at p * scale - offset.
    _previewZoom: function () {
        var old = this._view;
        var canvas = this._canvas;
        if (!this._snapshot) {
            this._snapshot = document.createElement('canvas');
        }
        var snapshot = this._snapshot;
        if (snapshot.width !== canvas.width || snapshot.height !== canvas.height) {
            snapshot.width = canvas.width;
            snapshot.height = canvas.height;
        }
        var snapshotCtx = snapshot.getContext('2d');
        snapshotCtx.clearRect(0, 0, snapshot.width, snapshot.height);
        snapshotCtx.drawImage(canvas, 0, 0);
        // WebKit's JavaFX port defers canvas calls, replaying them through Prism later: without forcing this copy
        // to happen now, it read the canvas after _layOut cleared it, and the preview came out blank. Reading a
        // pixel back flushes the pending calls.
        snapshotCtx.getImageData(0, 0, 1, 1);

        var v = this._layOut();
        var k = v.scale / old.scale;
        v.ctx.drawImage(snapshot, k * old.offsetX - v.offsetX, k * old.offsetY - v.offsetY,
            k * snapshot.width / old.ratio, k * snapshot.height / old.ratio);
    },

    redraw: function () {
        clearTimeout(this._redrawTimer);
        var v = this._layOut();
        if (showCountries) {
            drawCountries(v.ctx, v.scale, v.offsetX, v.offsetY, v.viewMinX, v.viewMinY, v.viewMaxX, v.viewMaxY);
        }
        this.drawRange(0, data.lineCount, 0, data.substationCount);
    },

    // Positions, sizes and clears the canvas for the current view, which it returns and records for drawRange.
    _layOut: function () {
        var mapSize = map.getSize();
        var topLeft = map.containerPointToLayerPoint(mapSize.multiplyBy(-CANVAS_PADDING)).round();
        var canvasSize = mapSize.multiplyBy(1 + CANVAS_PADDING * 2).round();
        var ratio = window.devicePixelRatio || 1;
        var canvas = this._canvas;
        L.DomUtil.setPosition(canvas, topLeft);
        var ctx = canvas.getContext('2d');
        var width = Math.round(canvasSize.x * ratio);
        var height = Math.round(canvasSize.y * ratio);
        // Assigning canvas.width/height reallocates the backing store even when the value is unchanged - in
        // the WebView, a new viewport-sized Prism texture per redraw. Only reallocate on an actual size change.
        if (canvas.width !== width || canvas.height !== height) {
            canvas.width = width;
            canvas.height = height;
            canvas.style.width = canvasSize.x + 'px';
            canvas.style.height = canvasSize.y + 'px';
        } else {
            ctx.setTransform(1, 0, 0, 1, 0, 0);
            ctx.clearRect(0, 0, width, height);
        }
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';

        var scale = map.getZoomScale(map.getZoom(), 0);
        var origin = map.getPixelOrigin();
        var offsetX = origin.x + topLeft.x;
        var offsetY = origin.y + topLeft.y;
        var viewMinX = offsetX / scale, viewMinY = offsetY / scale;
        var viewMaxX = (offsetX + canvasSize.x) / scale, viewMaxY = (offsetY + canvasSize.y) / scale;

        this._view = {
            ctx: ctx, ratio: ratio, scale: scale, offsetX: offsetX, offsetY: offsetY,
            viewMinX: viewMinX, viewMinY: viewMinY, viewMaxX: viewMaxX, viewMaxY: viewMaxY
        };
        return this._view;
    },

    // Draws lines lineFrom to lineTo (exclusive), then substations substationFrom to substationTo (exclusive), over
    // the canvas as the last redraw laid it out: while the view hasn't moved since, this adds a newly loaded chunk
    // without redrawing everything loaded before it.
    drawRange: function (lineFrom, lineTo, substationFrom, substationTo) {
        var v = this._view;
        var ctx = v.ctx, ratio = v.ratio, scale = v.scale, offsetX = v.offsetX, offsetY = v.offsetY;
        var viewMinX = v.viewMinX, viewMinY = v.viewMinY, viewMaxX = v.viewMaxX, viewMaxY = v.viewMaxY;

        ctx.lineWidth = LINE_WEIGHT;
        // one batched path per color and dash style, as a canvas path has a single stroke style
        for (var dashed = 0; dashed < 2; dashed++) {
            ctx.setLineDash(dashed ? LINE_DASH : []);
            for (var color = 0; color < data.colors.length; color++) {
                ctx.beginPath();
                for (var line = lineFrom; line < lineTo; line++) {
                    if (data.lineColor[line] !== color || data.lineDisconnected[line] !== dashed
                        || hiddenBaseVoltages[data.lineBaseVoltages[line]]) {
                        continue;
                    }
                    var b = line * 4;
                    if (data.lineBounds[b + 2] < viewMinX || data.lineBounds[b] > viewMaxX
                        || data.lineBounds[b + 3] < viewMinY || data.lineBounds[b + 1] > viewMaxY) {
                        continue;
                    }
                    var start = data.lineStart[line], end = data.lineStart[line + 1];
                    var lastX = data.pointX[start] * scale - offsetX;
                    var lastY = data.pointY[start] * scale - offsetY;
                    ctx.moveTo(lastX, lastY);
                    for (var k = start + 1; k < end; k++) {
                        var x = data.pointX[k] * scale - offsetX;
                        var y = data.pointY[k] * scale - offsetY;
                        // sub-pixel steps are invisible, and skipping them keeps zoomed-out paths short
                        if (k < end - 1 && Math.abs(x - lastX) < 1 && Math.abs(y - lastY) < 1) {
                            continue;
                        }
                        ctx.lineTo(x, y);
                        lastX = x;
                        lastY = y;
                    }
                }
                ctx.strokeStyle = data.colors[color];
                ctx.stroke();
            }
        }
        // the countries are stroked with this context on the next redraw
        ctx.setLineDash([]);

        // WebKit's JavaFX port replays canvas calls through Prism on the FX thread, and on the Map test
        // network 10k substations as arcs in one path measured ~375 ms of that replay (~140 ms as rects),
        // against ~20 ms as blits of a pre-rendered sprite.
        var size = substationSize(scale);
        var colorSprites = data.colors.map(function (color) {
            return substationSprite(ratio, color, size);
        });
        var spriteSize = Math.ceil(size * ratio) / ratio;
        var spriteHalf = spriteSize / 2;
        for (var i = substationFrom; i < substationTo; i++) {
            if (hiddenBaseVoltages[data.substationBaseVoltages[i]]) {
                continue;
            }
            var sx = data.substationX[i], sy = data.substationY[i];
            if (sx < viewMinX - spriteHalf / scale || sx > viewMaxX + spriteHalf / scale
                || sy < viewMinY - spriteHalf / scale || sy > viewMaxY + spriteHalf / scale) {
                continue;
            }
            // snapped to whole device pixels so the sprite is copied 1:1 rather than resampled
            var left = Math.round((sx * scale - offsetX - spriteHalf) * ratio) / ratio;
            var top = Math.round((sy * scale - offsetY - spriteHalf) * ratio) / ratio;
            ctx.drawImage(colorSprites[data.substationColor[i]], left, top, spriteSize, spriteSize);
        }
    }
});

// Whole countries in one path, so the fill and the stroke are one canvas call each for the whole basemap.
// Countries don't overlap, so even-odd filling leaves exactly the holes (e.g. Lesotho in South Africa) unfilled.
function drawCountries(ctx, scale, offsetX, offsetY, viewMinX, viewMinY, viewMaxX, viewMaxY) {
    ctx.beginPath();
    for (var ring = 0; ring < countries.ringStart.length - 1; ring++) {
        var b = ring * 4;
        if (countries.ringBounds[b + 2] < viewMinX || countries.ringBounds[b] > viewMaxX
            || countries.ringBounds[b + 3] < viewMinY || countries.ringBounds[b + 1] > viewMaxY) {
            continue;
        }
        var start = countries.ringStart[ring], end = countries.ringStart[ring + 1];
        var lastX = countries.pointX[start] * scale - offsetX;
        var lastY = countries.pointY[start] * scale - offsetY;
        ctx.moveTo(lastX, lastY);
        for (var k = start + 1; k < end; k++) {
            var x = countries.pointX[k] * scale - offsetX;
            var y = countries.pointY[k] * scale - offsetY;
            if (Math.abs(x - lastX) < 1 && Math.abs(y - lastY) < 1) {
                continue;
            }
            ctx.lineTo(x, y);
            lastX = x;
            lastY = y;
        }
        ctx.closePath();
    }
    ctx.fillStyle = LAND_COLOR;
    ctx.fill('evenodd');
    ctx.strokeStyle = BORDER_COLOR;
    ctx.lineWidth = 1;
    ctx.stroke();
}

var sprites = {};

// one substation marker pre-rendered at device resolution
function substationSprite(ratio, color, size) {
    var key = ratio + '|' + color + '|' + size;
    if (!sprites[key]) {
        var sprite = document.createElement('canvas');
        sprite.width = sprite.height = Math.ceil(size * ratio);
        var ctx = sprite.getContext('2d');
        ctx.fillStyle = color;
        ctx.fillRect(0, 0, sprite.width, sprite.height);
        sprites[key] = sprite;
    }
    return sprites[key];
}

var networkLayer = new NetworkLayer().addTo(map);

// Where Leaflet's non-sticky bindTooltip anchored a path's tooltip: a circle's center, and the point
// halfway along a polyline's length. Projection is linear, so measuring in zoom-0 coordinates is exact.
function tooltipAnchor(hit) {
    if (hit.substation) {
        return map.unproject([data.substationX[hit.index], data.substationY[hit.index]], 0);
    }
    var start = data.lineStart[hit.index], end = data.lineStart[hit.index + 1];
    var length = 0;
    for (var k = start; k < end - 1; k++) {
        length += Math.hypot(data.pointX[k + 1] - data.pointX[k], data.pointY[k + 1] - data.pointY[k]);
    }
    var remaining = length / 2;
    for (k = start; k < end - 1; k++) {
        var segment = Math.hypot(data.pointX[k + 1] - data.pointX[k], data.pointY[k + 1] - data.pointY[k]);
        if (segment > 0 && remaining <= segment) {
            var t = remaining / segment;
            return map.unproject([data.pointX[k] + t * (data.pointX[k + 1] - data.pointX[k]),
                data.pointY[k] + t * (data.pointY[k + 1] - data.pointY[k])], 0);
        }
        remaining -= segment;
    }
    return map.unproject([data.pointX[start], data.pointY[start]], 0);
}

// A single shared tooltip, re-anchored whenever the hovered element changes.
var tooltip = L.tooltip();
var hovered = null;
var pendingHoverEvent = null;
var hoverTimer = null;

function setHovered(hit) {
    var same = hit && hovered && hit.substation === hovered.substation && hit.index === hovered.index;
    if (same) {
        return;
    }
    hovered = hit;
    map.getContainer().style.cursor = hit ? 'pointer' : '';
    if (hit) {
        tooltip.setLatLng(tooltipAnchor(hit)).setContent(hit.text);
        if (!map.hasLayer(tooltip)) {
            tooltip.addTo(map);
        }
    } else if (map.hasLayer(tooltip)) {
        map.removeLayer(tooltip);
    }
}

map.on('mousemove', function (e) {
    pendingHoverEvent = e;
    if (hoverTimer === null) {
        hoverTimer = setTimeout(function () {
            hoverTimer = null;
            if (!map.dragging.moving()) {
                setHovered(hitTest(pendingHoverEvent.latlng));
            }
        }, HOVER_THROTTLE_MS);
    }
});

map.on('mouseout', function () {
    setHovered(null);
});

map.on('click', function (e) {
    var hit = hitTest(e.latlng);
    if (hit && hit.substation) {
        window.controller.onSubstationClick(data.substationIds[hit.index]);
    } else if (hit) {
        window.controller.onLineClick(data.lineIds[hit.index]);
    }
});

// The network arrives from MapController.buildNetworkData in pieces, everything already computed there: a header
// sizing the arrays and fitting the view, chunks each filling a range of substations, lines and line points, then
// the hit-testing grid, which enables hover and clicks. The FX thread runs this script, so the chunks are applied
// one per timer tick, and the map fills in progressively instead of freezing until complete. Each chunk is only
// drawn over what is already on the canvas: redrawing everything loaded so far made every chunk slower than the
// last. Its lines may then cover earlier chunks' substations, until the full redraw once the grid arrives.
var loadQueue = [];
var loadGeneration = 0;

function renderNetwork(headerJson) {
    // drops what is still queued from a previous network
    loadQueue = [];
    loadGeneration++;
    setHovered(null);
    var header = JSON.parse(headerJson);
    var d = emptyData();
    d.colors = header.colors;
    d.substationIds = new Array(header.substationCount);
    d.substationTexts = new Array(header.substationCount);
    d.substationBaseVoltages = new Array(header.substationCount);
    d.substationX = new Float64Array(header.substationCount);
    d.substationY = new Float64Array(header.substationCount);
    d.substationColor = new Int32Array(header.substationCount);
    d.lineIds = new Array(header.lineCount);
    d.lineTexts = new Array(header.lineCount);
    d.lineBaseVoltages = new Array(header.lineCount);
    d.lineStart = new Int32Array(header.lineCount + 1);
    d.lineBounds = new Float64Array(header.lineCount * 4);
    d.lineColor = new Int32Array(header.lineCount);
    d.lineDisconnected = new Uint8Array(header.lineCount);
    d.pointX = new Float64Array(header.pointCount);
    d.pointY = new Float64Array(header.pointCount);
    d.pointLine = new Int32Array(header.pointCount);
    // JSON has no Infinity
    d.substationSpacing = header.substationSpacing === null ? Infinity : header.substationSpacing;
    data = d;
    networkLayer.redraw();

    var b = header.bounds;
    if (!b) {
        return;
    }
    if (b[0] === b[2] && b[1] === b[3]) {
        map.setView(map.unproject([b[0], b[1]], 0), 12, {animate: false});
    } else {
        map.fitBounds(L.latLngBounds(map.unproject([b[0], b[1]], 0), map.unproject([b[2], b[3]], 0)),
            {padding: [20, 20], animate: false});
    }
}

function addNetworkChunk(chunkJson) {
    enqueueLoad(function () {
        var c = JSON.parse(chunkJson);
        var d = data;
        var lineFrom = d.lineCount, substationFrom = d.substationCount;
        for (var i = 0; i < c.substationIds.length; i++) {
            d.substationIds[c.substationFrom + i] = c.substationIds[i];
            d.substationTexts[c.substationFrom + i] = c.substationTexts[i];
            d.substationBaseVoltages[c.substationFrom + i] = c.substationBaseVoltages[i];
        }
        d.substationX.set(c.substationX, c.substationFrom);
        d.substationY.set(c.substationY, c.substationFrom);
        d.substationColor.set(c.substationColor, c.substationFrom);
        for (var j = 0; j < c.lineIds.length; j++) {
            d.lineIds[c.lineFrom + j] = c.lineIds[j];
            d.lineTexts[c.lineFrom + j] = c.lineTexts[j];
            d.lineBaseVoltages[c.lineFrom + j] = c.lineBaseVoltages[j];
        }
        d.lineStart.set(c.lineStart, c.lineFrom);
        d.lineBounds.set(c.lineBounds, c.lineFrom * 4);
        d.lineColor.set(c.lineColor, c.lineFrom);
        d.lineDisconnected.set(c.lineDisconnected, c.lineFrom);
        d.pointX.set(c.pointX, c.pointFrom);
        d.pointY.set(c.pointY, c.pointFrom);
        d.pointLine.set(c.pointLine, c.pointFrom);
        // chunks come in order, so everything below these counts is loaded
        d.substationCount = c.substationFrom + c.substationIds.length;
        d.lineCount = c.lineFrom + c.lineIds.length;
        networkLayer.drawRange(lineFrom, d.lineCount, substationFrom, d.substationCount);
    });
}

function endNetwork(gridJson) {
    enqueueLoad(function () {
        var g = JSON.parse(gridJson);
        data.grid = g && {
            size: g.size, minX: g.minX, minY: g.minY, maxX: g.maxX, maxY: g.maxY, cellWidth: g.cellWidth, cellHeight: g.cellHeight,
            cellStart: new Int32Array(g.cellStart), items: new Int32Array(g.items)
        };
        networkLayer.redraw();
        window.controller.onNetworkRendered();
    });
}

function enqueueLoad(task) {
    loadQueue.push(task);
    if (loadQueue.length === 1) {
        scheduleLoad(loadGeneration);
    }
}

function scheduleLoad(generation) {
    setTimeout(function () {
        if (generation !== loadGeneration) {
            return;
        }
        loadQueue.shift()();
        if (loadQueue.length > 0) {
            scheduleLoad(generation);
        }
    }, LOAD_CHUNK_DELAY_MS);
}
