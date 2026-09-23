
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
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(map);

var SUBSTATION_RADIUS = 6;
var SUBSTATION_WEIGHT = 2;
var LINE_WEIGHT = 2;
// same hit tolerances Leaflet's canvas renderer used for circleMarker/polyline: half the stroke width
var SUBSTATION_HIT_DISTANCE = SUBSTATION_RADIUS + SUBSTATION_WEIGHT / 2;
var LINE_HIT_DISTANCE = LINE_WEIGHT / 2;
var MAX_HIT_DISTANCE = Math.max(SUBSTATION_HIT_DISTANCE, LINE_HIT_DISTANCE);
var CANVAS_PADDING = 0.1;
var GRID_SIZE = 128;
var HOVER_THROTTLE_MS = 32;

// One Leaflet layer per substation/line made every zoom re-project, clip and redraw each layer
// individually. Instead, everything is projected once at zoom 0 into flat arrays - at any zoom a pixel
// position is just that times 2^zoom minus the pixel origin - and drawn into a single canvas, lines as one
// batched path and substations as sprite blits. Hover/click hit-testing goes through a uniform grid over
// the same zoom-0 coordinates.
var data = emptyData();

function emptyData() {
    return {
        substationIds: [], substationTexts: [], substationX: new Float64Array(0), substationY: new Float64Array(0),
        lineIds: [], lineTexts: [], lineStart: new Int32Array(1), lineBounds: new Float64Array(0),
        pointX: new Float64Array(0), pointY: new Float64Array(0), pointLine: new Int32Array(0),
        grid: null
    };
}

// base64 -> UTF-8 JSON, so equipment names with non-ASCII characters survive the Java -> JS call unescaped
function decodeBase64Json(base64) {
    var binary = atob(base64);
    var bytes = new Uint8Array(binary.length);
    for (var i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }
    return JSON.parse(new TextDecoder('utf-8').decode(bytes));
}

function buildData(json) {
    var d = emptyData();
    var substationCount = json.substations.length;
    d.substationX = new Float64Array(substationCount);
    d.substationY = new Float64Array(substationCount);
    json.substations.forEach(function (substation, i) {
        var p = map.project([substation.lat, substation.lng], 0);
        d.substationIds.push(substation.id);
        d.substationTexts.push(substation.text);
        d.substationX[i] = p.x;
        d.substationY[i] = p.y;
    });

    var lineCount = json.lines.length;
    var pointCount = 0;
    json.lines.forEach(function (line) {
        pointCount += line.points.length;
    });
    d.lineStart = new Int32Array(lineCount + 1);
    d.lineBounds = new Float64Array(lineCount * 4);
    d.pointX = new Float64Array(pointCount);
    d.pointY = new Float64Array(pointCount);
    d.pointLine = new Int32Array(pointCount);
    var k = 0;
    json.lines.forEach(function (line, i) {
        d.lineIds.push(line.id);
        d.lineTexts.push(line.text);
        d.lineStart[i] = k;
        var minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
        line.points.forEach(function (point) {
            var p = map.project(point, 0);
            d.pointX[k] = p.x;
            d.pointY[k] = p.y;
            d.pointLine[k] = i;
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            k++;
        });
        d.lineBounds[i * 4] = minX;
        d.lineBounds[i * 4 + 1] = minY;
        d.lineBounds[i * 4 + 2] = maxX;
        d.lineBounds[i * 4 + 3] = maxY;
    });
    d.lineStart[lineCount] = k;
    d.grid = buildGrid(d);
    return d;
}

// Grid cells hold substation indices (>= 0) and line segment start point indices, encoded as -(k + 1).
function buildGrid(d) {
    var minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    function extend(x, y) {
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
    }
    for (var i = 0; i < d.substationX.length; i++) {
        extend(d.substationX[i], d.substationY[i]);
    }
    for (var k = 0; k < d.pointX.length; k++) {
        extend(d.pointX[k], d.pointY[k]);
    }
    if (minX === Infinity) {
        return null;
    }
    var grid = {
        minX: minX,
        minY: minY,
        maxX: maxX,
        maxY: maxY,
        cellWidth: Math.max((maxX - minX) / GRID_SIZE, 1e-9),
        cellHeight: Math.max((maxY - minY) / GRID_SIZE, 1e-9),
        cells: new Array(GRID_SIZE * GRID_SIZE)
    };
    function insert(item, x0, y0, x1, y1) {
        var cx0 = cellX(grid, x0), cx1 = cellX(grid, x1), cy0 = cellY(grid, y0), cy1 = cellY(grid, y1);
        for (var cy = cy0; cy <= cy1; cy++) {
            for (var cx = cx0; cx <= cx1; cx++) {
                var index = cy * GRID_SIZE + cx;
                (grid.cells[index] || (grid.cells[index] = [])).push(item);
            }
        }
    }
    for (i = 0; i < d.substationX.length; i++) {
        insert(i, d.substationX[i], d.substationY[i], d.substationX[i], d.substationY[i]);
    }
    for (var line = 0; line < d.lineIds.length; line++) {
        for (k = d.lineStart[line]; k < d.lineStart[line + 1] - 1; k++) {
            insert(-(k + 1),
                Math.min(d.pointX[k], d.pointX[k + 1]), Math.min(d.pointY[k], d.pointY[k + 1]),
                Math.max(d.pointX[k], d.pointX[k + 1]), Math.max(d.pointY[k], d.pointY[k + 1]));
        }
    }
    return grid;
}

function cellX(grid, x) {
    return Math.min(GRID_SIZE - 1, Math.max(0, Math.floor((x - grid.minX) / grid.cellWidth)));
}

function cellY(grid, y) {
    return Math.min(GRID_SIZE - 1, Math.max(0, Math.floor((y - grid.minY) / grid.cellHeight)));
}

function squaredDistanceToSegment(px, py, ax, ay, bx, by) {
    var dx = bx - ax, dy = by - ay;
    var lengthSquared = dx * dx + dy * dy;
    var t = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSquared));
    var ex = px - (ax + t * dx), ey = py - (ay + t * dy);
    return ex * ex + ey * ey;
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
    var substationLimit = SUBSTATION_HIT_DISTANCE / scale;
    var lineLimit = LINE_HIT_DISTANCE / scale;
    var bestSubstation = -1, bestSubstationDistance = substationLimit * substationLimit;
    var bestLine = -1, bestLineDistance = lineLimit * lineLimit;
    for (var cy = cy0; cy <= cy1; cy++) {
        for (var cx = cx0; cx <= cx1; cx++) {
            var cell = grid.cells[cy * GRID_SIZE + cx];
            if (!cell) {
                continue;
            }
            for (var j = 0; j < cell.length; j++) {
                var item = cell[j];
                var distance;
                if (item >= 0) {
                    var sx = data.substationX[item] - p.x, sy = data.substationY[item] - p.y;
                    distance = sx * sx + sy * sy;
                    if (distance <= bestSubstationDistance) {
                        bestSubstationDistance = distance;
                        bestSubstation = item;
                    }
                } else {
                    var k = -item - 1;
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
        return {moveend: this.redraw, resize: this.redraw};
    },

    redraw: function () {
        var size = map.getSize();
        var topLeft = map.containerPointToLayerPoint(size.multiplyBy(-CANVAS_PADDING)).round();
        var canvasSize = size.multiplyBy(1 + CANVAS_PADDING * 2).round();
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

        ctx.beginPath();
        for (var line = 0; line < data.lineIds.length; line++) {
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
        ctx.strokeStyle = '#616161';
        ctx.lineWidth = LINE_WEIGHT;
        ctx.stroke();

        // WebKit's JavaFX port replays canvas calls through Prism on the FX thread, and on the Map test
        // network 10k substations as arcs in one path measured ~375 ms of that replay (~140 ms as rects),
        // against ~20 ms as blits of a pre-rendered sprite.
        var sprite = substationSprite(ratio);
        var spriteSize = sprite.width / ratio;
        var spriteHalf = spriteSize / 2;
        for (var i = 0; i < data.substationIds.length; i++) {
            var sx = data.substationX[i], sy = data.substationY[i];
            if (sx < viewMinX - spriteHalf / scale || sx > viewMaxX + spriteHalf / scale
                || sy < viewMinY - spriteHalf / scale || sy > viewMaxY + spriteHalf / scale) {
                continue;
            }
            // snapped to whole device pixels so the sprite is copied 1:1 rather than resampled
            var left = Math.round((sx * scale - offsetX - spriteHalf) * ratio) / ratio;
            var top = Math.round((sy * scale - offsetY - spriteHalf) * ratio) / ratio;
            ctx.drawImage(sprite, left, top, spriteSize, spriteSize);
        }
    }
});

var sprites = {};

// one substation marker (Leaflet circleMarker look) pre-rendered at device resolution
function substationSprite(ratio) {
    if (!sprites[ratio]) {
        var sprite = document.createElement('canvas');
        sprite.width = sprite.height = Math.ceil((SUBSTATION_RADIUS + SUBSTATION_WEIGHT) * 2 * ratio);
        var center = sprite.width / ratio / 2;
        var ctx = sprite.getContext('2d');
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
        ctx.beginPath();
        ctx.arc(center, center, SUBSTATION_RADIUS, 0, Math.PI * 2);
        ctx.globalAlpha = 0.9;
        ctx.fillStyle = '#42a5f5';
        ctx.fill();
        ctx.globalAlpha = 1;
        ctx.strokeStyle = '#1565c0';
        ctx.lineWidth = SUBSTATION_WEIGHT;
        ctx.stroke();
        sprites[ratio] = sprite;
    }
    return sprites[ratio];
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

function renderNetwork(base64) {
    setHovered(null);
    data = buildData(decodeBase64Json(base64));
    networkLayer.redraw();

    var grid = data.grid;
    if (!grid) {
        return;
    }
    if (grid.minX === grid.maxX && grid.minY === grid.maxY) {
        map.setView(map.unproject([grid.minX, grid.minY], 0), 12, {animate: false});
    } else {
        map.fitBounds(L.latLngBounds(map.unproject([grid.minX, grid.minY], 0), map.unproject([grid.maxX, grid.maxY], 0)),
            {padding: [20, 20], animate: false});
    }
}
