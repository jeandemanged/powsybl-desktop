
// JavaFX's WebKit mis-composites the translate3d-positioned panes/tiles Leaflet uses by default, which
// renders the tile grid and the vector pane as disjoint blocks stuck at stale offsets. Forcing any3d off
// (read as a property at every positioning call, so this takes effect) falls back to plain left/top.
L.Browser.any3d = false;

// preferCanvas: substations/lines are drawn into one canvas instead of an SVG pane, which has the same
// transform-positioning problem. animations off: the WebView doesn't reliably finish Leaflet's CSS
// transitions, and an interrupted one leaves stale frames on screen.
var map = L.map('map', {
    preferCanvas: true,
    zoomAnimation: false,
    fadeAnimation: false,
    markerZoomAnimation: false
}).setView([48.8566, 2.3522], 5);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(map);

var substationMarkers = {};
var lineLayers = {};

function clearNetwork() {
    Object.values(substationMarkers).forEach(function (marker) {
        map.removeLayer(marker);
    });
    Object.values(lineLayers).forEach(function (line) {
        map.removeLayer(line);
    });
    substationMarkers = {};
    lineLayers = {};
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

function renderNetwork(base64) {
    clearNetwork();
    var data = decodeBase64Json(base64);
    var allPoints = [];

    data.substations.forEach(function (substation) {
        var point = [substation.lat, substation.lng];
        allPoints.push(point);
        var marker = L.circleMarker(point, {
            radius: 6,
            weight: 2,
            color: '#1565c0',
            fillColor: '#42a5f5',
            fillOpacity: 0.9
        }).addTo(map);
        marker.bindTooltip(substation.text);
        marker.on('click', function () {
            window.controller.onSubstationClick(substation.id);
        });
        substationMarkers[substation.id] = marker;
    });

    data.lines.forEach(function (line) {
        line.points.forEach(function (point) {
            allPoints.push(point);
        });
        var polyline = L.polyline(line.points, {color: '#616161', weight: 2}).addTo(map);
        polyline.bindTooltip(line.text);
        polyline.on('click', function () {
            window.controller.onLineClick(line.id);
        });
        lineLayers[line.id] = polyline;
    });

    if (allPoints.length === 1) {
        map.setView(allPoints[0], 12, {animate: false});
    } else if (allPoints.length > 1) {
        map.fitBounds(allPoints, {padding: [20, 20], animate: false});
    }
}
