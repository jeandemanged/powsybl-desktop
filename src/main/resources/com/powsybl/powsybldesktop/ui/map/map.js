
// JavaFX's WebKit mis-composites the translate3d-positioned panes/tiles Leaflet uses by default, which
// renders the tile grid and the vector pane as disjoint blocks stuck at stale offsets. Forcing any3d off
// (read as a property at every positioning call, so this takes effect) falls back to plain left/top.
L.Browser.any3d = false;

// OpenStreetMap's, and also the network layer's: Leaflet stacks a tile layer's zoom levels by z-index computed from
// its maxZoom, and without one, a level zoomed back to could end up below the level it should replace.
var MAX_ZOOM = 19;
// Tiles replaced after a zoom are removed this long after all their replacements loaded, so that the WebView has
// painted those first: removing a tile as soon as its replacement loaded flickered.
var PRUNE_DELAY_MS = 100;
// Zooming is animated by animateZoom, as zoom levels in quarter steps: the mouse wheel moves one zoom level per
// WHEEL_PIXELS_PER_ZOOM_LEVEL of Leaflet's wheel delta, which in the WebView is about one level per notch; double
// click and the zoom buttons move one level.
var ZOOM_ANIMATION_MS = 180;
var ZOOM_STEP = 0.25;
var WHEEL_PIXELS_PER_ZOOM_LEVEL = 30;

// Animations off: the WebView doesn't reliably finish Leaflet's CSS transitions, and an interrupted one
// leaves stale frames on screen. Zooming is animated by animateZoom instead, hence Leaflet's own wheel, double
// click and zoom buttons handling off, and zoomSnap 0 for its intermediate zoom levels. With any3d off, Leaflet
// ignores zoomSnap and rounds every zoom to a whole level, hence _limitZoom, as Leaflet's with any3d on.
// the world as Leaflet's Web Mercator projection shows it, up to its max latitude
var WORLD_BOUNDS = L.latLngBounds([-85.0511287798, -180], [85.0511287798, 180]);

var FractionalZoomMap = L.Map.extend({
    _limitZoom: function (zoom) {
        var snap = this.options.zoomSnap;
        if (snap) {
            zoom = Math.round(zoom / snap) * snap;
        }
        return Math.max(this.getMinZoom(), Math.min(this.getMaxZoom(), zoom));
    }
});
var map = new FractionalZoomMap('map', {
    maxZoom: MAX_ZOOM,
    zoomSnap: 0,
    zoomDelta: 0.5,
    zoomAnimation: false,
    fadeAnimation: false,
    markerZoomAnimation: false,
    scrollWheelZoom: false,
    doubleClickZoom: false,
    zoomControl: false,
    // a single world: no panning past its edges, and tile layers with noWrap, so no copies of it side by side
    maxBounds: WORLD_BOUNDS,
    maxBoundsViscosity: 1
}).setView([48.8566, 2.3522], 5);

// zooming out stops once the whole world fits in the view
function limitZoomOutToWorld() {
    map.setMinZoom(map.getBoundsZoom(WORLD_BOUNDS));
}
limitZoomOutToWorld();
map.on('resize', function () {
    limitZoomOutToWorld();
    fitView();
});

// Moves the zoom from the current level towards target, easing out, re-centering at each step so that the
// point under containerPoint stays put. Each step only moves and scales the tiles already there: new ones are
// requested whenever the rounded zoom level changes, and replace the scaled ones as they arrive. A new zoom
// request during the animation restarts it from where it is towards the new target.
var zoomAnimation = null;

function animateZoom(containerPoint, target) {
    target = Math.max(map.getMinZoom(), Math.min(map.getMaxZoom(), target));
    var start = map.getZoom();
    if (target === start) {
        return;
    }
    takeOverView();
    var restarting = zoomAnimation !== null;
    zoomAnimation = {containerPoint: containerPoint, start: start, target: target, startTime: Date.now()};
    if (!restarting) {
        L.Util.requestAnimFrame(zoomFrame);
    }
}

function zoomFrame() {
    var a = zoomAnimation;
    // stopped by fitToNetwork
    if (a === null) {
        return;
    }
    var t = Math.min(1, (Date.now() - a.startTime) / ZOOM_ANIMATION_MS);
    var eased = 1 - (1 - t) * (1 - t);
    map.setZoomAround(a.containerPoint, a.start + (a.target - a.start) * eased, {animate: false});
    if (t < 1) {
        L.Util.requestAnimFrame(zoomFrame);
    } else {
        zoomAnimation = null;
    }
}

// the zoom the animation is heading to, so that a burst of wheel notches adds up
function zoomTarget() {
    return zoomAnimation !== null ? zoomAnimation.target : map.getZoom();
}

function snapZoom(zoom) {
    return Math.round(zoom / ZOOM_STEP) * ZOOM_STEP;
}

var wheelZoom = 0;
L.DomEvent.on(map.getContainer(), 'wheel', function (e) {
    L.DomEvent.stop(e);
    // touchpads send many small deltas: accumulated, as each would round to no zoom change on its own
    wheelZoom += L.DomEvent.getWheelDelta(e) / WHEEL_PIXELS_PER_ZOOM_LEVEL;
    var step = snapZoom(wheelZoom);
    if (step !== 0) {
        wheelZoom -= step;
        animateZoom(map.mouseEventToContainerPoint(e), snapZoom(zoomTarget() + step));
    }
});

map.on('dblclick', function (e) {
    // deletes a route's point while editing it, see deleteEditPoint
    var index = edit && edit.route ? editVertexAt(e.containerPoint.x, e.containerPoint.y) : -1;
    if (index >= 0) {
        deleteEditPoint(index);
        return;
    }
    animateZoom(e.containerPoint, snapZoom(zoomTarget() + (e.originalEvent.shiftKey ? -1 : 1)));
});

var AnimatedZoomControl = L.Control.Zoom.extend({
    _zoomIn: function (e) {
        this._animatedZoom(e.shiftKey ? 3 : 1);
    },
    _zoomOut: function (e) {
        this._animatedZoom(e.shiftKey ? -3 : -1);
    },
    _animatedZoom: function (delta) {
        if (!this._disabled) {
            animateZoom(this._map.getSize().divideBy(2), snapZoom(zoomTarget() + delta));
        }
    }
});
new AnimatedZoomControl().addTo(map);

// Below the zoom buttons, in their style. The icon is Material Design's fit-to-page-outline, as the diagrams' fit to
// screen button's.
var FitToNetworkControl = L.Control.extend({
    onAdd: function () {
        var bar = L.DomUtil.create('div', 'leaflet-bar');
        var link = L.DomUtil.create('a', '', bar);
        link.href = '#';
        link.title = this.options.title;
        link.style.display = 'flex';
        link.style.alignItems = 'center';
        link.style.justifyContent = 'center';
        link.setAttribute('role', 'button');
        link.setAttribute('aria-label', this.options.title);
        link.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24">'
            + '<path fill="currentColor" d="M20.02 2.02L3.98 2.02Q3.19 2.02 2.59 2.61Q2.02 3.19 2.02 3.98L2.02 20.02'
            + 'Q2.02 20.81 2.59 21.39Q3.19 21.98 3.98 21.98L20.02 21.98Q20.81 21.98 21.39 21.39Q21.98 20.81 21.98 20.02'
            + 'L21.98 3.98Q21.98 3.19 21.39 2.61Q20.81 2.02 20.02 2.02ZM20.02 20.02L3.98 20.02L3.98 3.98L20.02 3.98'
            + 'L20.02 20.02ZM12.98 8.02L12.98 9.98L11.02 9.98L11.02 8.02L9 8.02L12 5.02L15 8.02L12.98 8.02Z'
            + 'M15.98 15L15.98 12.98L14.02 12.98L14.02 11.02L15.98 11.02L15.98 9L18.98 12L15.98 15Z'
            + 'M9.98 12.98L8.02 12.98L8.02 15L5.02 12L8.02 9L8.02 11.02L9.98 11.02L9.98 12.98Z'
            + 'M15 15.98L12 18.98L9 15.98L11.02 15.98L11.02 14.02L12.98 14.02L12.98 15.98L15 15.98Z"/></svg>';
        L.DomEvent.disableClickPropagation(link);
        L.DomEvent.on(link, 'click', function (e) {
            L.DomEvent.preventDefault(e);
            fitToNetwork();
        });
        return bar;
    }
});

// Called by MapController once loaded, with the button's localized tooltip
function addFitToNetworkControl(title) {
    new FitToNetworkControl({position: 'topleft', title: title}).addTo(map);
}

// the attribution's links would otherwise navigate the WebView itself away from the map
L.DomEvent.on(map.attributionControl.getContainer(), 'click', function (e) {
    var link = e.target.closest('a');
    if (link) {
        L.DomEvent.preventDefault(e);
        window.controller.openLink(link.href);
    }
});

// With zoom animation off, every zoom resets the map view, and grid layers drop all their tiles on the
// viewprereset event that starts it: the map went blank until the new zoom level's tiles arrived. Without that
// handler, the viewreset that follows updates the layer as an animated zoom would, keeping the previous level's
// tiles until the new ones replace them.
// Tiles are drawn for whole zoom levels, and each level's tiles scaled to the current zoom, also fractional -
// except with any3d off, where Leaflet only positions them, at their own level's size. _setZoomTransform scales
// them too, as Leaflet's with any3d on, with a 2D transform rather than the translate3d the WebView mis-composites.
var keepTilesOnViewReset = {
    getEvents: function () {
        var events = L.GridLayer.prototype.getEvents.call(this);
        delete events.viewprereset;
        return events;
    },

    _setZoomTransform: function (level, center, zoom) {
        var scale = this._map.getZoomScale(zoom, level.zoom);
        var translate = level.origin.multiplyBy(scale).subtract(this._map._getNewPixelOrigin(center, zoom)).round();
        level.el.style.transform = 'translate(' + translate.x + 'px,' + translate.y + 'px) scale(' + scale + ')';
    }
};

var osmLayer = new (L.TileLayer.extend(keepTilesOnViewReset))('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: MAX_ZOOM,
    noWrap: true,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
});
osmLayer.on('tileload', function () {
    window.controller.onTileLoad(true);
});
osmLayer.on('tileerror', function () {
    window.controller.onTileLoad(false);
});
// sea color behind the offline basemap's countries, drawn in the network tiles; OSM tiles cover it
var SEA_COLOR = '#aad3df';
var HOVER_THROTTLE_MS = 32;

// The network, and the offline basemap, are drawn by MapController into tiles, on background threads: drawn
// here, on the FX thread that runs this script, a large network froze the UI on every load, pan and zoom. A
// requested tile arrives later through onTileRendered, as a PNG data URL, or an empty string when there is nothing
// to draw in it.
var NetworkTileLayer = L.GridLayer.extend(keepTilesOnViewReset).extend({
    options: {
        maxZoom: MAX_ZOOM,
        noWrap: true
    },

    createTile: function (coords, done) {
        var tile = document.createElement('img');
        tile.alt = '';
        requestTile(tile, coords, done);
        return tile;
    },

    // Draws all tiles again, e.g. with other base voltages hidden, in place: unlike redraw(), which drops them all
    // first, each tile keeps its current image until the new one is ready.
    refresh: function () {
        for (var key in this._tiles) {
            var tile = this._tiles[key];
            var id = tile.el.networkTileId;
            var pending = pendingTiles[id];
            if (pending) {
                delete pendingTiles[id];
                window.controller.cancelTile(id);
            }
            // a tile still waiting for its first image keeps Leaflet waiting for the new one instead
            requestTile(tile.el, this._wrapCoords(tile.coords), pending ? pending.done : null);
        }
    },

    // Leaflet prunes as each tile loads, dropping the tiles of the previous zoom level it covers; deferred here
    // until all tiles are loaded, so the previous level's tiles go all at once, see PRUNE_DELAY_MS.
    _pruneTiles: function () {
        if (!this._noTilesToLoad()) {
            return;
        }
        clearTimeout(this._pruneTimer);
        var layer = this;
        this._pruneTimer = setTimeout(function () {
            L.GridLayer.prototype._pruneTiles.call(layer);
        }, PRUNE_DELAY_MS);
    }
});
var lastTileId = 0;
var pendingTiles = {};

function requestTile(tile, coords, done) {
    var id = ++lastTileId;
    tile.networkTileId = id;
    pendingTiles[id] = {tile: tile, done: done};
    window.controller.requestTile(id, coords.z, coords.x, coords.y, window.devicePixelRatio || 1);
}

function onTileRendered(id, url) {
    var pending = pendingTiles[id];
    if (!pending) {
        return;
    }
    delete pendingTiles[id];
    var tile = pending.tile;
    function finish() {
        if (pending.done) {
            pending.done(null, tile);
        }
    }
    if (!url) {
        tile.removeAttribute('src');
        finish();
        return;
    }
    // decoded off-screen first, so that a refreshed tile goes straight from its previous image to this one
    var image = new Image();
    image.onload = function () {
        // unless refreshed again meanwhile, the newer image then being the one to show
        if (tile.networkTileId === id) {
            tile.src = url;
        }
        finish();
    };
    image.src = url;
}

// above the OSM tiles, whose z-index is 1
var networkLayer = new NetworkTileLayer({zIndex: 10});
// Leaflet drops tiles e.g. when zooming or panning past them before they arrive: no need to draw them anymore
networkLayer.on('tileunload', function (e) {
    var id = e.tile.networkTileId;
    if (pendingTiles[id]) {
        delete pendingTiles[id];
        window.controller.cancelTile(id);
    }
});

// Called by MapController once window.controller is set: adding the layer requests its first tiles right away,
// which failed when done as this script loaded, before the page load completed and MapController set it.
function addNetworkLayer() {
    networkLayer.addTo(map);
}

function redrawNetwork() {
    setHovered(null);
    networkLayer.refresh();
}

function setBasemap(name) {
    if (name === 'offline') {
        map.removeLayer(osmLayer);
    } else {
        osmLayer.addTo(map);
    }
    map.getContainer().style.background = name === 'offline' ? SEA_COLOR : '';
    redrawNetwork();
}

// A single shared tooltip, re-anchored whenever the hovered element changes. Hover and clicks are hit-tested by
// MapController, which returns what is under the mouse as {key, text, lat, lng}.
var tooltip = L.tooltip();
var hovered = null;
var pendingHoverEvent = null;
var hoverTimer = null;

function setHovered(hit) {
    if ((hit && hit.key) === (hovered && hovered.key)) {
        return;
    }
    hovered = hit;
    // while placing, a click places rather than opens what is under it
    map.getContainer().style.cursor = placing ? 'crosshair' : (hit ? 'pointer' : '');
    if (hit) {
        tooltip.setLatLng([hit.lat, hit.lng]).setContent(hit.text);
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
            // while editing, the cursor tells the edit handles instead
            if (!map.dragging.moving() && !edit) {
                var latlng = pendingHoverEvent.latlng;
                var hit = window.controller.hitTest(latlng.lat, latlng.lng, map.getZoom());
                setHovered(hit ? JSON.parse(hit) : null);
            }
        }, HOVER_THROTTLE_MS);
    }
});

map.on('mouseout', function () {
    setHovered(null);
});

map.on('click', function (e) {
    if (!editClick(e.latlng)) {
        window.controller.onMapClick(e.latlng.lat, e.latlng.lng, map.getZoom());
    }
});

// called by MapController on a right-click, the WebView not dispatching contextmenu events to the page (its own
// context menu disabled), with the pointer in CSS pixels of the viewport
function mapContextMenuAt(x, y) {
    if (edit) {
        window.controller.onEditContextMenu(editVertexAt(x, y));
        return;
    }
    var latlng = map.containerPointToLatLng(L.point(x, y));
    window.controller.onMapContextMenu(latlng.lat, latlng.lng, map.getZoom());
}

// Called by MapController once a network is ready to be drawn, with the south, west, north and east of what it
// draws, null for nothing, and the view the user last left it at, as {latitude, longitude, zoom}, null for none.
function renderNetwork(boundsJson, viewJson) {
    // before fitting the view: tiles it adds are drawn from the new network already
    redrawNetwork();
    highlight('[]');
    userView = false;
    networkBounds = JSON.parse(boundsJson);
    renderedBounds = networkBounds;
    restoredView = JSON.parse(viewJson);
    fitView();
}

// The view is restored, or fitted to the network, again on each resize until the user pans or zooms: a small network
// is ready before the WebView has its final size, and fitting to the size the map had then zoomed in far too much.
// invalidateSize also keeps the top left corner in place rather than the center, which moved a restored view.
var networkBounds = null;
// networkBounds, kept once the user took over the view, for fitToNetwork
var renderedBounds = null;
var restoredView = null;
// Whether the user panned or zoomed since the network was rendered: only such a view is reported to MapController,
// a fitted one is fitted again next time, to the size the map then has.
var userView = false;
var FIT_PADDING = 20;

function takeOverView() {
    networkBounds = null;
    restoredView = null;
    userView = true;
}

map.on('dragstart', takeOverView);
// Leaflet's keyboard panning and zooming
map.on('keydown', takeOverView);

// Called by the fit to network button. The fitted view is reported to MapController like one the user
// moved to, and fitted again on resize until the user pans or zooms, like the one the network was first rendered at.
function fitToNetwork() {
    fitTo(renderedBounds);
}

// Called by MapController for a search match, with the south, west, north and east to show, fitted as the network
// is by fitToNetwork.
function zoomTo(boundsJson) {
    fitTo(JSON.parse(boundsJson));
}

// A search match's markers: DOM markers rather than vector ones, since Leaflet's vector renderers are moved by
// translate3d while zooming, which the WebView mis-composites; markers are positioned by left/top with any3d off.
// Not interactive, so clicks and hover go through to what they mark.
var HIGHLIGHT_SIZE = 26;
// A new marker's ring first pulses HIGHLIGHT_PULSES times, each shrinking it from HIGHLIGHT_PULSE_START_SIZE to a
// point, before it settles at HIGHLIGHT_SIZE. Driven frame by frame like animateZoom, as the WebView doesn't reliably
// finish CSS animations; its size rather than a scale transform is animated, which keeps its border's thickness.
var HIGHLIGHT_PULSES = 1;
var HIGHLIGHT_PULSE_MS = 350;
var HIGHLIGHT_PULSE_START_SIZE = 2 * HIGHLIGHT_SIZE;
var HIGHLIGHT_FADE_SIZE = 10;
// the ring is centered in the icon by its stylesheet, so it can outgrow it
var highlightIcon = L.divIcon({className: '', html: '<div class="search-highlight"></div>', iconSize: [HIGHLIGHT_SIZE, HIGHLIGHT_SIZE]});
var highlightLayer = L.layerGroup().addTo(map);
var highlightAnimation = null;

// Called by MapController with the [latitude, longitude] of each marker to show, replacing the previous ones
function highlight(pointsJson) {
    highlightLayer.clearLayers();
    var rings = JSON.parse(pointsJson).map(function (point) {
        var marker = L.marker(point, {icon: highlightIcon, interactive: false, keyboard: false}).addTo(highlightLayer);
        return marker.getElement().firstChild;
    });
    var restarting = highlightAnimation !== null;
    highlightAnimation = rings.length > 0 ? {rings: rings, startTime: Date.now()} : null;
    if (highlightAnimation !== null && !restarting) {
        L.Util.requestAnimFrame(highlightFrame);
    }
}

function highlightFrame() {
    var a = highlightAnimation;
    // stopped by a highlight without markers
    if (a === null) {
        return;
    }
    var elapsed = Date.now() - a.startTime;
    var done = elapsed >= HIGHLIGHT_PULSES * HIGHLIGHT_PULSE_MS;
    var t = (elapsed % HIGHLIGHT_PULSE_MS) / HIGHLIGHT_PULSE_MS;
    // easing in: the collapse speeds up towards the point
    var size = HIGHLIGHT_PULSE_START_SIZE * (1 - t * t);
    a.rings.forEach(function (ring) {
        ring.style.width = done ? '' : size + 'px';
        ring.style.height = done ? '' : size + 'px';
        // faded out once it's no bigger than its border, which it can't shrink past
        ring.style.opacity = done ? '' : Math.min(1, size / HIGHLIGHT_FADE_SIZE);
    });
    if (done) {
        highlightAnimation = null;
    } else {
        L.Util.requestAnimFrame(highlightFrame);
    }
}

function fitTo(bounds) {
    zoomAnimation = null;
    networkBounds = bounds;
    restoredView = null;
    userView = true;
    fitView();
}

map.on('moveend', function () {
    if (userView) {
        var center = map.getCenter();
        window.controller.onViewChanged(center.lat, center.lng, map.getZoom());
    }
});

function fitView() {
    var v = restoredView;
    if (v) {
        map.setView([v.latitude, v.longitude], v.zoom, {animate: false});
        return;
    }
    var b = networkBounds;
    var size = map.getSize();
    // not laid out yet: no room for the padding below
    if (!b || size.x <= FIT_PADDING * 2 || size.y <= FIT_PADDING * 2) {
        return;
    }
    if (b[0] === b[2] && b[1] === b[3]) {
        map.setView([b[0], b[1]], 12, {animate: false});
    } else {
        // as fitBounds with FIT_PADDING pixels of padding would, but with the zoom rounded down to a ZOOM_STEP: tiles
        // are only crisp at whole levels, and are scaled in between
        var bounds = L.latLngBounds([b[0], b[1]], [b[2], b[3]]);
        var zoom = Math.floor(map.getBoundsZoom(bounds, false, L.point(FIT_PADDING * 2, FIT_PADDING * 2)) / ZOOM_STEP) * ZOOM_STEP;
        var center = map.project(bounds.getSouthWest(), zoom).add(map.project(bounds.getNorthEast(), zoom)).divideBy(2);
        map.setView(map.unproject(center, zoom), zoom, {animate: false});
    }
}

// Position editing: a substation being moved, or a line's route being edited, drawn on a canvas laid over the map
// container rather than in a Leaflet pane, which the WebView mis-composites while zooming (see the search markers), and
// redrawn in container pixels whenever the view moves. Its handles are hit-tested here: a press on one is caught before
// Leaflet starts panning the map. MapController holds what is edited and applies the result, read by getEditPoints.
var EDIT_HANDLE_RADIUS = 6;
var EDIT_GHOST_RADIUS = 4;
var EDIT_HIT_PIXELS = 4;
var EDIT_SNAP_PIXELS = 10;
var EDIT_COLOR = '#ff6d00';
var editCanvas = L.DomUtil.create('canvas', '', map.getContainer());
editCanvas.style.position = 'absolute';
editCanvas.style.left = '0';
editCanvas.style.top = '0';
editCanvas.style.pointerEvents = 'none';
// above the map panes (400), below the controls (1000)
editCanvas.style.zIndex = '500';
// {route: whether a route rather than a substation, points: [L.LatLng], snaps: [L.LatLng]}, null when not editing
var edit = null;
// index of the point being dragged
var editDrag = null;
// the click ending a drag must not then add or move a point
var editDragEnded = false;
var placing = false;

// Called by MapController with the route's [latitude, longitude] points, and those its ends snap to: its substations
function startRouteEdit(pointsJson, snapsJson) {
    edit = {route: true, points: toLatLngs(pointsJson), snaps: toLatLngs(snapsJson)};
    setHovered(null);
    drawEdit();
}

function startSubstationMove(lat, lng) {
    edit = {route: false, points: [L.latLng(lat, lng)], snaps: []};
    setHovered(null);
    drawEdit();
}

function setEditPoints(pointsJson) {
    if (edit) {
        edit.points = toLatLngs(pointsJson);
        drawEdit();
        window.controller.onEditChanged(edit.points.length);
    }
}

function getEditPoints() {
    return JSON.stringify(edit ? edit.points.map(function (p) {
        return [p.lat, p.lng];
    }) : []);
}

function deleteEditPoint(index) {
    if (edit && edit.route && index >= 0 && index < edit.points.length) {
        edit.points.splice(index, 1);
        drawEdit();
        window.controller.onEditChanged(edit.points.length);
    }
}

function stopEdit() {
    edit = null;
    editDrag = null;
    map.dragging.enable();
    map.getContainer().style.cursor = placing ? 'crosshair' : '';
    drawEdit();
}

function startPlacement() {
    placing = true;
    map.getContainer().style.cursor = 'crosshair';
}

function stopPlacement() {
    placing = false;
    map.getContainer().style.cursor = '';
}

function toLatLngs(json) {
    return JSON.parse(json).map(function (p) {
        return L.latLng(p[0], p[1]);
    });
}

// the midpoints of the route's segments, dragged to insert a point there
function editGhosts() {
    var ghosts = [];
    for (var i = 0; edit.route && i + 1 < edit.points.length; i++) {
        var a = map.latLngToContainerPoint(edit.points[i]);
        var b = map.latLngToContainerPoint(edit.points[i + 1]);
        // a too short segment has no room for it between its ends' handles
        if (a.distanceTo(b) > 4 * EDIT_HANDLE_RADIUS) {
            ghosts.push({index: i + 1, point: a.add(b).divideBy(2)});
        }
    }
    return ghosts;
}

// Also called by MapController for its context menu: the index of the point at x, y in container pixels, -1 for none
function editVertexAt(x, y) {
    if (!edit) {
        return -1;
    }
    var p = L.point(x, y);
    for (var i = edit.points.length - 1; i >= 0; i--) {
        if (map.latLngToContainerPoint(edit.points[i]).distanceTo(p) <= EDIT_HANDLE_RADIUS + EDIT_HIT_PIXELS) {
            return i;
        }
    }
    return -1;
}

function editGhostAt(p) {
    var ghosts = edit ? editGhosts() : [];
    for (var i = 0; i < ghosts.length; i++) {
        if (ghosts[i].point.distanceTo(p) <= EDIT_GHOST_RADIUS + EDIT_HIT_PIXELS) {
            return ghosts[i];
        }
    }
    return null;
}

// a route's end dropped near one of its substations goes exactly there
function snapped(end, latlng) {
    if (!end) {
        return latlng;
    }
    var p = map.latLngToContainerPoint(latlng);
    for (var i = 0; i < edit.snaps.length; i++) {
        if (map.latLngToContainerPoint(edit.snaps[i]).distanceTo(p) <= EDIT_SNAP_PIXELS) {
            return edit.snaps[i];
        }
    }
    return latlng;
}

function drawEdit() {
    var size = map.getSize();
    var ratio = window.devicePixelRatio || 1;
    editCanvas.style.width = size.x + 'px';
    editCanvas.style.height = size.y + 'px';
    editCanvas.width = Math.round(size.x * ratio);
    editCanvas.height = Math.round(size.y * ratio);
    var g = editCanvas.getContext('2d');
    g.setTransform(ratio, 0, 0, ratio, 0, 0);
    g.clearRect(0, 0, size.x, size.y);
    if (!edit) {
        return;
    }
    var points = edit.points.map(function (latlng) {
        return map.latLngToContainerPoint(latlng);
    });
    if (edit.route && points.length > 1) {
        g.lineJoin = 'round';
        g.lineCap = 'round';
        g.beginPath();
        points.forEach(function (p, i) {
            if (i === 0) {
                g.moveTo(p.x, p.y);
            } else {
                g.lineTo(p.x, p.y);
            }
        });
        g.strokeStyle = '#fff';
        g.lineWidth = 6;
        g.stroke();
        g.strokeStyle = EDIT_COLOR;
        g.lineWidth = 3;
        g.stroke();
    }
    editGhosts().forEach(function (ghost) {
        circle(g, ghost.point, EDIT_GHOST_RADIUS, 'rgba(255, 255, 255, 0.7)', EDIT_COLOR);
    });
    points.forEach(function (p, i) {
        // a substation, or a route's end, filled; a route's breakpoints hollow
        var filled = !edit.route || i === 0 || i === points.length - 1;
        circle(g, p, EDIT_HANDLE_RADIUS, filled ? EDIT_COLOR : '#fff', filled ? '#000' : EDIT_COLOR);
    });
}

function circle(g, p, radius, fill, stroke) {
    g.beginPath();
    g.arc(p.x, p.y, radius, 0, 2 * Math.PI);
    g.fillStyle = fill;
    g.fill();
    g.lineWidth = 2;
    g.strokeStyle = stroke;
    g.stroke();
}

map.on('move zoom resize viewreset', drawEdit);

// in the capture phase, so before Leaflet's own handlers start panning the map
map.getContainer().addEventListener('mousedown', function (e) {
    if (!edit || e.button !== 0) {
        return;
    }
    var p = map.mouseEventToContainerPoint(e);
    var index = editVertexAt(p.x, p.y);
    if (index < 0) {
        var ghost = editGhostAt(p);
        if (ghost === null) {
            return;
        }
        edit.points.splice(ghost.index, 0, map.containerPointToLatLng(ghost.point));
        index = ghost.index;
    }
    editDrag = index;
    map.dragging.disable();
    e.stopPropagation();
    e.preventDefault();
    drawEdit();
}, true);

document.addEventListener('mousemove', function (e) {
    if (editDrag !== null) {
        var end = edit.route && (editDrag === 0 || editDrag === edit.points.length - 1);
        edit.points[editDrag] = snapped(end, map.mouseEventToLatLng(e));
        drawEdit();
    } else if (edit) {
        var p = map.mouseEventToContainerPoint(e);
        map.getContainer().style.cursor = editVertexAt(p.x, p.y) >= 0 || editGhostAt(p) !== null ? 'move' : '';
    }
});

document.addEventListener('mouseup', function () {
    if (editDrag !== null) {
        editDrag = null;
        editDragEnded = true;
        setTimeout(function () {
            editDragEnded = false;
        }, 0);
        map.dragging.enable();
        window.controller.onEditChanged(edit.points.length);
    }
});

// While editing, a click adds a route's first points, or moves the substation there; while placing, it places the
// chosen substation. Returns whether it took the click.
function editClick(latlng) {
    if (edit) {
        if (!editDragEnded) {
            if (!edit.route) {
                edit.points = [latlng];
            } else if (edit.points.length < 2) {
                edit.points.push(snapped(true, latlng));
            }
            drawEdit();
            window.controller.onEditChanged(edit.points.length);
        }
        return true;
    }
    if (placing) {
        window.controller.onPlace(latlng.lat, latlng.lng);
        return true;
    }
    return false;
}
