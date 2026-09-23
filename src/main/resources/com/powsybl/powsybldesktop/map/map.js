
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
    fitNetwork();
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
    // the user took over the view
    networkBounds = null;
    var restarting = zoomAnimation !== null;
    zoomAnimation = {containerPoint: containerPoint, start: start, target: target, startTime: Date.now()};
    if (!restarting) {
        L.Util.requestAnimFrame(zoomFrame);
    }
}

function zoomFrame() {
    var a = zoomAnimation;
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
    map.getContainer().style.cursor = hit ? 'pointer' : '';
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
            if (!map.dragging.moving()) {
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
    window.controller.onMapClick(e.latlng.lat, e.latlng.lng, map.getZoom());
});

// Called by MapController once a network is ready to be drawn, with the south, west, north and east of what it
// draws, null for nothing.
function renderNetwork(boundsJson) {
    // before fitting the view: tiles it adds are drawn from the new network already
    redrawNetwork();
    networkBounds = JSON.parse(boundsJson);
    fitNetwork();
}

// The view is fitted to the network again on each resize until the user pans or zooms: a small network is ready
// before the WebView has its final size, and fitting to the size the map had then zoomed in far too much.
var networkBounds = null;
var FIT_PADDING = 20;
map.on('dragstart', function () {
    networkBounds = null;
});

function fitNetwork() {
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
