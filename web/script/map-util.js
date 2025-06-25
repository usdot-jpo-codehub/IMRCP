function pointToPaddedBounds(oPoint, nTol = 4)
{
	return [{x: oPoint.x - nTol, y: oPoint.y - nTol}, {x: oPoint.x + nTol, y: oPoint.y + nTol}];
}
const featureInSources = sources => feature => sources.has(feature.source);

let g_oLayers = 
{
	'network-polygons': {'id': 'network-polygons', 'type': 'fill', 'source': 'network-polygons', 'paint':{'fill-opacity': ['case', ['boolean', ['feature-state', 'delete'], false], 0, ['boolean', ['feature-state', 'hidden'], false], 0.0, ['boolean', ['feature-state', 'hover'], false], 1.0, 0.6], 'fill-color': ['match', ['get', 'displaystatus'], 0, '#808080', 1, '#006400', 2, '#9370db', 3, '#90ee90', 4, '#ff3333', 5, '#00ffff', '#ff3333']}},
	'network-polygons-map': {'id': 'network-polygons-map', 'minzoom':0, 'maxzoom':6, 'type': 'fill', 'source': 'network-polygons', 'paint':{'fill-opacity': ['case', ['boolean', ['feature-state', 'delete'], false], 0, ['boolean', ['feature-state', 'hover'], false], 1.0, ['boolean', ['get', 'hidden'], false], 0.0, 0.6], 'fill-color': ['match', ['get', 'displaystatus'], 0, '#808080', 1, '#006400', 2, '#9370db', 3, '#90ee90', 4, '#ff3333', 5, '#00ffff', '#ff3333']}},
	'network-polygons-report': {'id': 'network-polygons-report', 'minzoom':0, 'maxzoom':10, 'type': 'fill', 'source': 'network-polygons', 'paint':{'fill-opacity': ['case', ['boolean', ['feature-state', 'hover'], false], 1.0, 0.6], 'fill-color': ['match', ['get', 'displaystatus'], 0, '#808080', 1, '#006400', 2, '#9370db', 3, '#90ee90', 4, '#ff3333', 5, '#00ffff', '#ff3333']}},
	'network-polygons-del': {'id': 'network-polygons-del', 'type': 'fill', 'source': 'network-polygons', 'paint': {'fill-opacity': ['case', ['boolean', ['feature-state', 'delete'], false], 1.0, 0], 'fill-pattern': 'delete'}},
	'geo-lines': {'id': 'geo-lines', 'type': 'line', 'source': 'geo-lines', 'layout':{'line-cap':'round', 'line-join':'round'}, 'paint':{'line-opacity': ['case', ['boolean', ['get', 'hidden']], 0, ['boolean', ['get', 'include'], false], 1, 0.4], 'line-color': ['match', ['get', 'type'], 'motorway', '#cc8500', 'motorway_link', '#cc8500', 'trunk', '#0000cc', 'trunk_link', '#0000cc', 'primary', '#00cc00', 'primary_link', '#00cc00', 'secondary', '#cc00cc', 'secondary_link', '#cc00cc', 'tertiary', '#cc0000', 'tertiary_link', '#cc0000', 'residential', '#000000', 'unclassified', '#651a1a', '#651a1a'], 'line-gap-width':['case', ['has', 'bridge'], 3, 0], 'line-width': ['interpolate', ['exponential', 2], ['zoom'], 0.0, ['case', ['boolean', ['feature-state', 'hover'], false], 3.0 , ['boolean', ['feature-state', 'detector'], false], 3.0, 1.0], 10.0, ['case', ['boolean', ['feature-state', 'hover'], false], 6.0, ['boolean', ['feature-state', 'detector'], false], 6.0, 4.0], 24.0, ['case', ['boolean', ['feature-state', 'hover'], false], 14.0, ['boolean', ['feature-state', 'detector'], false], 14.0, 12.0]]}},
//	'geo-lines-report': {'id': 'geo-lines-report', 'type': 'line', 'source': 'geo-lines', 'layout':{'line-cap':'round', 'line-join':'round'}, 'paint':{'line-opacity': 1, 'line-color': 'black', 'line-gap-width':['case', ['has', 'bridge'], 3, 0], 'line-width': ['let', 'add', 
//				['case', ['all', ['boolean', ['get', 'include'], false], ['boolean', ['get', 'hover'], false]], 0.0,
//					['boolean', ['get', 'include'], false], 3.0, 
//					['boolean', ['get', 'hover'], false], 3.0, 0.0], ['interpolate', ['exponential', 2], ['zoom'], 0.0, ['+', ['var', 'add'], 1.0], 10.0, ['+', ['var', 'add'], 4.0], 24.0, ['+', ['var', 'add'], 12.0]]]}},
	'geo-lines-report': {'id': 'geo-lines-report', 'type': 'line', 'source': 'geo-lines', 'layout':{'line-cap':'round', 'line-join':'round'}, 'paint':{'line-opacity': 1, 'line-color': ['feature-state', 'color'], 'line-width': ['let', 'add', 
				['case', ['all', ['boolean', ['feature-state', 'include'], false], ['boolean', ['feature-state', 'hover'], false]], 0.0,
					['boolean', ['feature-state', 'include'], false], 3.0, 
					['boolean', ['feature-state', 'hover'], false], 3.0, 0.0], ['interpolate', ['exponential', 2], ['zoom'], 0.0, ['+', ['var', 'add'], 1.0], 10.0, ['+', ['var', 'add'], 4.0], 24.0, ['+', ['var', 'add'], 12.0]]]}},
	'geo-lines-scenario': {'id': 'geo-lines-scenario', 'type': 'line', 'source': 'geo-lines', 'layout':{'line-cap':'round', 'line-join':'round'}, 'paint':{'line-opacity': 1, 'line-color': ['feature-state', 'color'], 'line-width': ['let', 'add', 
				['case', ['boolean', ['feature-state', 'ignore'], false], 0.0, ['all', ['boolean', ['feature-state', 'include'], false], ['boolean', ['feature-state', 'hover'], false]], 0.0,
					['boolean', ['feature-state', 'include'], false], 3.0, 
					['boolean', ['feature-state', 'hover'], false], 3.0, 0.0], ['interpolate', ['exponential', 2], ['zoom'], 0.0, ['+', ['var', 'add'], 1.0], 10.0, ['+', ['var', 'add'], 4.0], 24.0, ['+', ['var', 'add'], 12.0]]]}},
	'geo-lines-sep': {'id': 'geo-lines-sep', 'type': 'line', 'source': 'geo-lines-sep', 'layout':{'line-cap':'round', 'line-join':'round'}, 'paint':{'line-opacity': ['case', ['boolean', ['get', 'hidden']], 0, ['boolean', ['get', 'include'], false], 1, 0.4], 'line-color': ['case', ['boolean', ['feature-state', 'detector'], false], 'blue', 'black'], 'line-gap-width':['case', ['has', 'bridge'], 3, 0], 'line-width': ['interpolate', ['exponential', 2], ['zoom'], 0.0, ['case', ['boolean', ['feature-state', 'hover'], false], 3.0 , ['boolean', ['feature-state', 'detector'], false], 3.0, 1.0], 10.0, ['case', ['boolean', ['feature-state', 'hover'], false], 6.0, ['boolean', ['feature-state', 'detector'], false], 12.0, 4.0], 24.0, ['case', ['boolean', ['feature-state', 'hover'], false], 14.0, ['boolean', ['feature-state', 'detector'], false], 24.0, 12.0]]}},
	'geo-lines-lanes': {'id': 'geo-lines-sep', 'type': 'line', 'source': 'geo-lines-sep', 'layout':{'line-cap':'round', 'line-join':'round'}, 'paint':{'line-opacity': 1, 'line-color': ['case', ['boolean', ['feature-state', 'current'], false], 'green', 'red'], 'line-gap-width':['case', ['has', 'bridge'], 3, 0], 'line-width': ['interpolate', ['exponential', 2], ['zoom'], 0.0, ['case', ['boolean', ['feature-state', 'hover'], false], 3.0 , ['boolean', ['feature-state', 'detector'], false], 3.0, 1.0], 10.0, ['case', ['boolean', ['feature-state', 'hover'], false], 4.0, ['boolean', ['feature-state', 'detector'], false], 4.0, 2.0], 24.0, ['case', ['boolean', ['feature-state', 'hover'], false], 10.0, ['boolean', ['feature-state', 'detector'], false], 10.0, 8.0]]}},
	'geo-points': {'id': 'geo-points', 'type': 'circle', 'source': 'geo-lines', 'paint': {'circle-opacity': ['case', ['boolean', ['get', 'hidden']], 0, ['boolean', ['get', 'include'], false], 1, 0.4], 'circle-radius': ['interpolate', ['exponential', 2], ['zoom'], 0.0, 0.0, 12.0, 2.0, 24.0, 10.0], 'circle-color': ['match', ['get', 'pt'], 'e', ['rgb', 0, 0, 0], 'm', ['rgb', 255, 255, 255], '#F00']}},
	'poly-outline': {'id': 'poly-outline', 'source': 'poly-outline', 'type': 'line', 'layout':{'line-cap':'round', 'line-join':'round'}, 'paint': {'line-color': '#66c2ff', 'line-width': 5, 'line-opacity': 0.5}},
	'poly-bounds': {'id': 'poly-bounds', 'source': 'poly-bounds', 'type': 'fill', 'paint': {'fill-opacity': 0, 'fill-color': '#66c2ff'}},
	'split-points': {'id': 'split-points', 'source': 'split-points', 'type': 'circle', 'paint': {'circle-color': 'black', 'circle-radius': ['interpolate', ['exponential', 2], ['zoom'], 0.0, 0, 12.0, ['case', ['boolean', ['feature-state', 'hover'], false], 8.0, 6.0], 24.0, ['case', ['boolean', ['feature-state', 'hover'], false], 16.0, 14.0]]}},
	'selected-point': {'id': 'selected-point', 'source': 'split-points', 'type': 'circle', 'paint': {'circle-color': 'green', 'circle-radius': ['interpolate', ['exponential', 2], ['zoom'], 0.0, 0, 12.0, 12.0, 24.0, 20.0], 'circle-opacity': ['case', ['boolean', ['get', 'selected'], false], 1, 0]}},
	'satellite': {'id': 'satellite', 'type': 'raster', 'source': 'mapbox://mapbox.satellite', 'layout': {}, 'paint': {}},
	'detectors': {'id': 'detectors', 'type': 'circle', 'source': 'detectors', 'paint': {'circle-radius': ['interpolate', ['exponential', 2], ['zoom'], 0.0, 0, 12.0, ['case', ['boolean', ['feature-state', 'hover'], false], 8.0, 6.0], 24.0, ['case', ['boolean', ['feature-state', 'hover'], false], 16.0, 14.0]], 'circle-color': ['match', ['get', 'roads'], 1, ['rgb', 144, 238, 144], 0, ['rgb', 255, 51, 51], ['rgb', 128, 128, 128]]}},
	'detector-select': {'id': 'detector-select', 'type': 'circle', 'source': 'detectors', 'paint': {'circle-radius': ['interpolate', ['exponential', 2], ['zoom'], 0.0, 0, 12.0, 12.0, 24.0, 20.0], 'circle-color': 'black', 'circle-opacity': ['case', ['boolean', ['get', 'selected'], false], 1, 0]}},
	'detector-pt': {'id': 'detector-pt', 'type': 'circle', 'source': 'detector-pt', 'paint': {'circle-radius': ['interpolate', ['exponential', 2], ['zoom'], 0.0, 0, 12.0, 6.0, 24.0, 14.0], 'circle-color': 'black', 'circle-opacity': ['case', ['boolean', ['get', 'include'], false], 1, 0]}},
	'location-pt': {'id': 'location-pt', 'type': 'circle', 'source': 'location-pt', 'paint': {'circle-radius': ['interpolate', ['exponential', 2], ['zoom'], 0.0, 0, 12.0, 6.0, 24.0, 14.0], 'circle-color': 'red', 'circle-opacity': ['case', ['boolean', ['get', 'include'], false], 1, 0]}},
	'dets': {'id': 'detector-pt', 'type': 'circle', 'source': 'dets', 'paint': {'circle-radius': 4, 'circle-color': 'black', 'circle-opacity': 1}},
	'tmc': {'id': 'tmc', 'type': 'line', 'source': 'tmc', 'layout':{'line-cap':'round', 'line-join':'round'}, 'paint':{'line-opacity': 1, 'line-color': 'red', 'line-width': ['interpolate', ['exponential', 2], ['zoom'], 0.0, ['case', ['boolean', ['feature-state', 'hover'], false], 3.0 , ['boolean', ['feature-state', 'detector'], false], 3.0, 1.0], 10.0, ['case', ['boolean', ['feature-state', 'hover'], false], 6.0, ['boolean', ['feature-state', 'detector'], false], 6.0, 4.0], 24.0, ['case', ['boolean', ['feature-state', 'hover'], false], 14.0, ['boolean', ['feature-state', 'detector'], false], 14.0, 12.0]]}},
	'xd': {'id': 'xd', 'type': 'line', 'source': 'xd', 'layout':{'line-cap':'round', 'line-join':'round'}, 'paint':{'line-opacity': 1, 'line-color': ['case', ['boolean', ['feature-state', 'hover'], false], 'red', 'gray'], 'line-width': ['interpolate', ['exponential', 2], ['zoom'], 0.0, ['case', ['boolean', ['feature-state', 'hover'], false], 5.0 , ['boolean', ['feature-state', 'detector'], false], 5.0, 1.0], 10.0, ['case', ['boolean', ['feature-state', 'hover'], false], 12.0, ['boolean', ['feature-state', 'detector'], false], 12.0, 4.0], 24.0, ['case', ['boolean', ['feature-state', 'hover'], false], 20.0, ['boolean', ['feature-state', 'detector'], false], 20.0, 12.0]]}},
	'elev': {'id': 'elev', 'type': 'circle', 'source': 'elev', 'paint': {'circle-radius': ['let', 'add', ['case', ['boolean', ['feature-state', 'hover'], false], 4.0, 1.0], ['interpolate', ['exponential', 2], ['zoom'], 0.0, ['+', ['var', 'add'], 0.0], 12.0, ['+', ['var', 'add'], 4.0], 24.0, ['+', ['var', 'add'], 16.0]]], 'circle-color': ['case', ['boolean', ['has', 'c'], false], ['get', 'c'], 'blue']}},
	'testlines': {'id': 'testlines', 'type': 'line', 'source': 'testlines', 'layout':{'line-cap':'round', 'line-join':'round'}, 'paint':{'line-opacity': 1, 'line-color': 'gray', 'line-width': ['interpolate', ['exponential', 2], ['zoom'], 0.0, ['case', ['boolean', ['feature-state', 'hover'], false], 3.0 , ['boolean', ['feature-state', 'detector'], false], 3.0, 1.0], 10.0, ['case', ['boolean', ['feature-state', 'hover'], false], 6.0, ['boolean', ['feature-state', 'detector'], false], 6.0, 4.0], 24.0, ['case', ['boolean', ['feature-state', 'hover'], false], 14.0, ['boolean', ['feature-state', 'detector'], false], 14.0, 12.0]]}},
	'adcirc': {'id': 'adcirc', 'type': 'raster', 'source': 'adcirc'},
	'report-location': {'id': 'report-location', 'type': 'line', 'source': 'report-location', 'paint':{'line-opacity': 0.8, 'line-color': 'blue', 'line-width': 5}},
	'network-outlines': {'id': 'network-outlines', 'minzoom':0, 'maxzoom':6, 'type': 'line', 'source': 'network-outlines', 'layout':{'line-cap':'round', 'line-join':'round'}, 'paint':{'line-opacity': 0.8, 'line-color': 'black', 'line-width': 3}}
};
let g_sCheckmarkPng;

function getLineStringBoundingBox(oFeature)
{
	if (oFeature.bbox === undefined)
	{
		let nMinX = Number.MAX_VALUE;
		let nMinY = Number.MAX_VALUE;
		let nMaxX = -Number.MAX_VALUE;
		let nMaxY = -Number.MAX_VALUE;
		
		for (let aCoord of oFeature.geometry.coordinates.values())
		{
			if (aCoord[0] < nMinX)
				nMinX = aCoord[0];

			if (aCoord[0] > nMaxX)
				nMaxX = aCoord[0];

			if (aCoord[1] < nMinY)
				nMinY = aCoord[1];

			if (aCoord[1] > nMaxY)
				nMaxY = aCoord[1];
		}
		
		oFeature.bbox = [[nMinX, nMinY], [nMaxX, nMaxY]];
	}
	
	return oFeature.bbox;
}


function getPolygonBoundingBox(oFeature)
{
	if (oFeature.bbox === undefined)
	{
		let nMinX = Number.MAX_VALUE;
		let nMinY = Number.MAX_VALUE;
		let nMaxX = -Number.MAX_VALUE;
		let nMaxY = -Number.MAX_VALUE;
		
		for (let aCoord of oFeature.geometry.coordinates[0].values())
		{
			if (aCoord[0] < nMinX)
				nMinX = aCoord[0];

			if (aCoord[0] > nMaxX)
				nMaxX = aCoord[0];

			if (aCoord[1] < nMinY)
				nMinY = aCoord[1];

			if (aCoord[1] > nMaxY)
				nMaxY = aCoord[1];
		}
		
		oFeature.bbox = [[nMinX, nMinY], [nMaxX, nMaxY]];
	}
	
	return oFeature.bbox;
}

function removeSource(sSource, oMap)
{
	if (oMap.getSource(sSource) !== undefined)
	{
		for (let oLayer of oMap.getStyle().layers.values())
			if (oLayer.source === sSource)
				oMap.removeLayer(oLayer.id);

		oMap.removeSource(sSource);
		
		return true;
	}
}

function snapPointToWay(oEvent)
{
	let oMap = oEvent.target;
	let aBox = [[oEvent.point.x - 100, oEvent.point.y - 100], [oEvent.point.x + 100, oEvent.point.y + 100]];
	let oFeatures = oMap.queryRenderedFeatures(aBox, {'layers': oEvent.data.layers});
	let oSrc = oMap.getSource(oEvent.data.pointSrc);
	let oData = oSrc._data;
	if (oFeatures.length == 0)
	{
		oData.properties.include = false;
		oSrc.setData(oData);
		return;
	}
	
	let sSnappedWay;
	let dDist = Number.MAX_VALUE;
	let aCoord;
	for (let oFeature of oFeatures.values())
	{
		let oSnapInfo = snapToPolyline(oEvent.lngLat.lng, oEvent.lngLat.lat, oFeature.geometry.coordinates, oEvent.data.snapTol);
		if (oSnapInfo.dist < dDist)
		{
			dDist = oSnapInfo.dist;
			aCoord = oSnapInfo.pt;
			sSnappedWay = oFeature.properties.imrcpid;
		}
	}
	
	if (dDist === Number.MAX_VALUE)
	{
		sSnappedWay = undefined;
		return sSnappedWay;
	}
	oData.properties.include = true;
	oData.geometry.coordinates = aCoord;
	oSrc.setData(oData);
	return sSnappedWay;
}


function snapToPolyline(dX, dY, aCoords, dTol)
{
	let oReturn = {'pt': [NaN, NaN], 'dist': Number.MAX_VALUE};
	let dXmin = dX - dTol;
	let dXmax = dX + dTol;
	let dYmin = dY - dTol;
	let dYmax = dY + dTol;
	for (let nIndex = 0; nIndex < aCoords.length - 1; nIndex++)
	{
		let aPt1 = aCoords[nIndex];
		let aPt2 = aCoords[nIndex + 1];
		let dSegXMin;
		let dSegXMax;
		let dSegYMin;
		let dSegYMax;
		if (aPt1[0] < aPt2[0])
		{
			dSegXMin = aPt1[0];
			dSegXMax = aPt2[0];
		}
		else
		{
			dSegXMin = aPt2[0];
			dSegXMax = aPt1[0];
		}
		if (aPt1[1] < aPt2[1])
		{
			dSegYMin = aPt1[1];
			dSegYMax = aPt2[1];
		}
		else
		{
			dSegYMin = aPt2[1];
			dSegYMax = aPt1[1];
		}
		
		if (dYmax >= dSegYMin && dYmin <= dSegYMax && dXmax >= dSegXMin && dXmin <= dSegXMax)
		{
			let oSnapInfo = snapToLine(dX, dY, aPt1[0], aPt1[1], aPt2[0], aPt2[1]);
			if (Number.isNaN(oSnapInfo.dist))
				continue;
			
			if (oSnapInfo.dist < oReturn.dist)
			{
				oReturn.dist = oSnapInfo.dist;
				oReturn.pt = oSnapInfo.pt;
			}
		}
	}
	
	return oReturn;
}

function snapToLine(dX, dY, dX1, dY1, dX2, dY2)
{
	let oReturn = {'pt': [NaN, NaN], 'dist': NaN};
	let dXd = dX2 - dX1;
	let dYd = dY2 - dY1;
	let dXp = dX - dX1;
	let dYp = dY - dY1;
	
	if (dXd == 0 && dYd == 0) // line segment is a point
		return oReturn

	let dU = dXp * dXd + dYp * dYd;
	let dV = dXd * dXd + dYd * dYd;

	if (dU < 0 || dU > dV) // nearest point is not on the line
	{
		return oReturn
	}

	// find the perpendicular intersection of the point on the line
	oReturn.pt[0] = dX1 + (dU * dXd / dV);
	oReturn.pt[1] = dY1 + (dU * dYd / dV);
	
	dXd = dX - oReturn.pt[0];
	dYd = dY - oReturn.pt[1];
	
	oReturn.dist = Math.sqrt(dXd * dXd + dYd * dYd);
	return oReturn;
}

const removeSourceAndLayers = (map, source) => {

  for (let layer of source.layers)
  {
    if (map.getLayer(layer.id))
      map.removeLayer(layer.id);
  }
  map.removeSource(source.id);
};
const addSourceAndLayers = (map, source, sLastLayer = undefined) => {

  map.addSource(source.id, source.mapboxSource);
  for (let layer of source.layers)
  {
	if (layer.type === 'line') 
	{
		if (map.getLayer('road-number-shield') !== undefined)
			map.addLayer(layer, 'road-number-shield');
		else if (map.getLayer(sLastLayer) !== undefined)
			map.addLayer(layer, sLastLayer);
		else
			map.addLayer(layer);
	}
	else if (layer.type === 'fill')
	{
		if (map.getLayer('country-label-lg') !== undefined)
			map.addLayer(layer, 'country-label-lg');
		else if (map.getLayer(sLastLayer) !== undefined)
			map.addLayer(layer, sLastLayer);
		else
			map.addLayer(layer);
	}
	else
		map.addLayer(layer);
  }
};


const getBoundsForCoordArray = coords => {
  let minLat = 90, maxLat = -90, minLng = 180, maxLng = -180;
  for (let [lng, lat] of coords)
  {
    minLng = Math.min(lng, minLng);
    maxLng = Math.max(lng, maxLng);
    minLat = Math.min(lat, minLat);
    maxLat = Math.max(lat, maxLat);
  }

  return new mapboxgl.LngLatBounds([minLng, minLat], [maxLng, maxLat]);
};

const iconDivFromSprite = ({width, height, x, y}) =>
  `<div width="1" height="1" class="clickable"
    style="width: ${width}px; height: ${height}px;  
    background: url(images/sprite.png) -${x}px -${y}px;">
  </div>`;

function fromIntDeg(nVal)
{
	return nVal / 10000000.0;
}

function toIntDeg(dVal)
{
	return Math.round((dVal + 0.00000005) * 10000000.0);
}


function startDrawPoly()
{
	this.startDraw();
	this.bCanAdd = true;
	let oMap = this.map;
	
	if (document.getElementById('dlgPolyError') === null)
	{
		$(document.body).append('<div id="dlgPolyError"></div>');
		let oDialog = $('#dlgPolyError');
		oDialog.dialog(
				{autoOpen: false,
				position: {my: "center", at: "center", of: "#map-container"},
				draggable: false,
				resizable: false,
				width: 'auto',
				open: function()
				{
					setTimeout(function() 
					{
						oDialog.dialog('close');
					}, 1000);
				}
		});
	}
	
	if (g_sCheckmarkPng === undefined)
	{
		let oCan = document.createElement('canvas');
		oCan.width = 24;
		oCan.height = 24;
		
		let ctx = oCan.getContext('2d');
		ctx.fillStyle = "#179B54";
		ctx.font = "24px FontAwesome";
		ctx.textAlign = "center";
		ctx.textBaseline = "middle";
		ctx.fillText("\uf00c", 12, 12);
		g_sCheckmarkPng = oCan.toDataURL('image/png');
		addStyleRule('.checkcursor', `cursor: url('${g_sCheckmarkPng}') 12 12, auto`);
	}
	removeSource('poly-outline', oMap);
	removeSource('poly-bounds', oMap);
	oMap.addSource('poly-outline', {'type': 'geojson', 'data': {'type': 'Feature', 'geometry': {'type': 'LineString', 'coordinates': []}}});
	oMap.addSource('poly-bounds', {'type': 'geojson', 'data': {'type': 'Feature', 'geometry': {'type': 'Polygon', 'coordinates': [[]]}}});
	oMap.on('click', clickInitPoly.bind(this));
	$(document).on('keyup', cancelDrawPoly.bind(this));
	$(oMap.getCanvas()).addClass('crosshaircursor');
}


function clickInitPoly(oEvent)
{
	let oMap = this.map;
	mapOffBoundFn(oMap, 'click', clickInitPoly.name);
	let oBoundsSource = oMap.getSource('poly-bounds');
	let oOutlineSource = oMap.getSource('poly-outline');
	let lngLat = oEvent.lngLat;
	this.initPoint = lngLat;
	
	let oBoundsData = oBoundsSource._data;
	oBoundsData.geometry.coordinates = [[[lngLat.lng, lngLat.lat], [lngLat.lng, lngLat.lat], [lngLat.lng, lngLat.lat]]];
	oBoundsSource.setData(oBoundsData);
	let oOutlineData = oOutlineSource._data;
	oOutlineData.geometry.coordinates = [[lngLat.lng, lngLat.lat], [lngLat.lng, lngLat.lat]];
	oOutlineSource.setData(oOutlineData);
	oMap.addLayer(g_oLayers['poly-bounds']);
	oMap.addLayer(g_oLayers['poly-outline']);
	
	if (this.initPoly)
		this.initPoly();
	oMap.on('mousemove', moveDrawingPoly.bind(this));
	oMap.on('click', clickDrawingPoly.bind(this));
}


function moveDrawingPoly(oEvent)
{
	let lngLat = oEvent.lngLat;
	let oBoundsSource = this.map.getSource('poly-bounds');
	let oBoundsData = oBoundsSource._data;
	let oOutlineSource = this.map.getSource('poly-outline');
	let oOutlineData = oOutlineSource._data;
	let aQ2 = [lngLat.lng, lngLat.lat];
	let aCoords = oOutlineData.geometry.coordinates;
	
	if (this.newPoint)
	{
		oBoundsData.geometry.coordinates[0].splice(-2, 0, aQ2);
		oOutlineData.geometry.coordinates.splice(-1, 0, aQ2);
		this.newPoint = false;
	}
	else
	{
		oBoundsData.geometry.coordinates[0][oBoundsData.geometry.coordinates[0].length - 2] = aQ2;
		aCoords[aCoords.length - 1] = aQ2;
	}
	let aP2 = aCoords[aCoords.length - 2];
	oBoundsSource.setData(oBoundsData);
	oOutlineSource.setData(oOutlineData);
	
	let bIntersects = false;
	for (let nIndex = 0; nIndex < aCoords.length - 2; nIndex++)
	{
		let aP1 = aCoords[nIndex];
		let aQ1 = aCoords[nIndex + 1];
		if (lineSegmentsIntersect(aP1, aQ1, aP2, aQ2))
		{
			if ((aQ1[0] === aP2[0] && aQ1[1] === aP2[1]) ||
				 (aQ2[0] === aP1[0] && aQ2[1] === aP1[1]))
				 continue;
			
			bIntersects = true;
			break;
			
		}
	}
	
	let oCanvas = $(this.map.getCanvas());
	
	if (bIntersects)
	{
		oCanvas.removeClass('checkcursor crosshaircursor').addClass('notallowedcursor');
		this.bCanAdd = false;
		if (this.movePoly)
			this.movePoly(oEvent);
		return;
	}
	
	this.bCanAdd = true;
	
	let oFirstPoint = this.map.project(this.initPoint);
	if (distanceBetweenPoints(oFirstPoint, oEvent.point) < 10 && aCoords.length > 3)
	{
		oCanvas.removeClass('notallowedcursor crosshaircursor').addClass('checkcursor');
		this.bLastPoint = true;
	}
	else
	{
		oCanvas.removeClass('notallowedcursor checkcursor').addClass('crosshaircursor');
		this.bLastPoint = false;
	}
	
	if (this.movePoly)
		this.movePoly(oEvent);
}


function clickDrawingPoly(oEvent)
{
	if (!this.bCanAdd)
		return;
	
	if (this.bLastPoint)
	{
		finishDrawPoly.bind(this).apply();
		return;
	}
	
	this.newPoint = true;
	
	if (this.addPoint)
		this.addPoint(oEvent);
}


function finishDrawPoly()
{
	let oMap = this.map;
	let oBoundsSource = oMap.getSource('poly-bounds');
	if (oBoundsSource === undefined)
		return;
	let oBoundsData = oBoundsSource._data;
	if (oBoundsData.geometry.coordinates[0].length < 4)
		return;
	
	let oOutlineSource = oMap.getSource('poly-outline');
	let oOutlineData = oOutlineSource._data;
	oOutlineData.geometry.coordinates.splice(-1, 1);
	oOutlineData.geometry.coordinates.push(oOutlineData.geometry.coordinates[0]);
	oBoundsData.geometry.coordinates[0].splice(-2, 1);
	oOutlineSource.setData(oOutlineData);
	oBoundsSource.setData(oBoundsData);
	
	$(oMap.getCanvas()).removeClass('notallowedcursor checkcursor crosshaircursor');
	if (this.finishDraw)
		this.finishDraw(oOutlineData.geometry);

	mapOffBoundFn(oMap, 'click', clickInitPoly.name);
	mapOffBoundFn(oMap, 'mousemove', moveDrawingPoly.name);
	mapOffBoundFn(oMap, 'click', clickDrawingPoly.name);
	jqueryOffBoundFn(document, 'keyup', cancelDrawPoly.name);
}


function cancelDrawPoly(oEvent)
{
	let oMap = this.map;
	if (oEvent.which === 27)
	{
		removeSource('poly-bounds', oMap);
		removeSource('poly-outline', oMap);
		mapOffBoundFn(oMap, 'click', clickInitPoly.name);
		mapOffBoundFn(oMap, 'mousemove', moveDrawingPoly.name);
		mapOffBoundFn(oMap, 'click', clickDrawingPoly.name);
		jqueryOffBoundFn(document, 'keyup', cancelDrawPoly.name);
		$(oMap.getCanvas()).removeClass('notallowedcursor checkcursor crosshaircursor');
		if (this.cancelDraw)
		{
			setTimeout(this.cancelDraw, 10);
		}
	}
}

function mapOffBoundFn(oMap, sType, sName, sLayer)
{
	if (oMap._listeners[sType] !== undefined)
	{
		if (sLayer)
			oMap.off(sType, sLayer, oMap._listeners[sType][oMap._listeners[sType].findIndex(o => o.name === `bound ${sName}`)]);
		else
			oMap.off(sType, oMap._listeners[sType][oMap._listeners[sType].findIndex(o => o.name === `bound ${sName}`)]);
	}
}


function jqueryOffBoundFn(oSel, sType, sName)
{
	let aEvents = $._data($(oSel).get(0), 'events')[sType];
	if (aEvents !== undefined)
		$(oSel).off(sType, aEvents[aEvents.findIndex(o => o.handler.name === `bound ${sName}`)].handler);
}


function clearFeatures(sSrc, oMap)
{
	let oSrc = oMap.getSource(sSrc);
	if (oSrc === undefined)
		return;
	let oData = oSrc._data;
	oData.features = [];
	oSrc.setData(oData);
}


function distanceBetweenPoints(oP1, oP2)
{
	let dX = oP2.x - oP1.x;
	let dY = oP2.y - oP1.y;
	return Math.sqrt(dX * dX + dY * dY);
}


function isFeatureInsidePolygonFeature(oPolygon, oSrc, oFeature)
{
	let oGeo = oFeature.geometry;
	if (oPolygon.holes === undefined)
		calculateHoles(oPolygon, oSrc);
	if (oGeo.type === 'Point')
	{
		if (oPolygon.geometry.coordinates.length === 1)
			return isPointInsidePolygon(oPolygon.geometry.coordinates[0], oGeo.coordinates[0], oGeo.coordinates[1]);
		else
		{
			return isCoordinateInsideFeaturePolygon(oPolygon, oGeo.coordinates);
		}
	}
	
	if (oGeo.type === 'LineString')
	{
		if (oPolygon.geometry.coordinates.length === 1)
		{
			let oPolyCoords = oPolygon.geometry.coordinates[0];
			let oLineCoords = oGeo.coordinates;
			for (let aCoord of oLineCoords.values())
			{
				if (isPointInsidePolygon(oPolyCoords, aCoord[0], aCoord[1]))
					return true;
			}
			
			
			for (let nPolyIndex = 0; nPolyIndex < oPolyCoords.length - 1; nPolyIndex++)
			{
				let oP1 = oPolyCoords[nPolyIndex];
				let oP2 = oPolyCoords[nPolyIndex + 1];
				for (let nLineIndex = 0; nLineIndex < oGeo.coordinates.length - 1; nLineIndex++)
				{
					let oL1 = oLineCoords[nLineIndex];
					let oL2 = oLineCoords[nLineIndex + 1];
					if (lineSegmentsIntersect(oP1, oP2, oL1, oL2))
						return true;
				}
			}
		}
		else
		{
			for (let aCoord of oGeo.coordinates.values())
			{
				if (isCoordinateInsideFeaturePolygon(oPolygon, aCoord))
					return true;
			}
		}
	}
	
	return false;
}


function isCoordinateInsideFeaturePolygon(oPolygon, aCoord)
{
	let bInsideExterior = false;
	for (let nIndex = 0; nIndex < oPolygon.geometry.coordinates.length; nIndex++)
	{
		let aRing = oPolygon.geometry.coordinates[nIndex];
		let bHole = oPolygon.holes[nIndex];
		if (bInsideExterior && !bHole) // if the point is inside an exterior ring and the current ring is not a hole then there cannot be another hole inside the exterior ring the point is inside so early out
			return true;
		let bInside = isPointInsidePolygon(aRing, aCoord[0], aCoord[1]);
		if (!bHole && bInside)
			bInsideExterior = true;

		if (bHole && bInside) // if the point is inside of a hole then it is not "inside" the polygon
			return false;
	}
	
	return bInsideExterior;
}


function isPointInsidePolygon(aRing, dX, dY)
{
	let nCount = 0;
	let nLimit = aRing.length - 1;
	for (let nIndex = 0; nIndex < nLimit; nIndex++)
	{
		let nX1 = aRing[nIndex][0];
		let nY1 = aRing[nIndex][1];
		let nX2 = aRing[nIndex + 1][0];
		let nY2 = aRing[nIndex + 1][1];
		if ((nY1 < dY && nY2 >= dY || nY2 < dY && nY1 >= dY)
		   && (nX1 <= dX || nX2 <= dX)
		   && (nX1 + (dY - nY1) / (nY2 - nY1) * (nX2 - nX1) < dX))
			++nCount;
	}
	return (nCount & 1) !== 0;
}


function calculateHoles(oFeature, oPolySrc)
{
	let oGeo = oFeature.geometry;
	let oHoles = [];
	if (oGeo.coordinates.length === 1)
		oHoles.push(false);
	else
	{
		for (let aRing of oGeo.coordinates.values())
			oHoles.push(isHole(aRing));
	}
	
	let oData = oPolySrc._data;
	oFeature.holes = oHoles;
	oData = oFeature;
	oPolySrc.setData(oData);
}


function isHole(aRing)
{
	let dWinding = 0;
	let nLimit = aRing.length - 1;
	for (let nIndex = 0; nIndex < nLimit; nIndex++)
	{
		let oP1 = aRing[nIndex];
		let oP2 = aRing[nIndex + 1];
		dWinding += (oP2[0] - oP1[0]) * (oP2[1] + oP1[1]);
	}
	return dWinding < 0;
}


function binarySearch(aArr, oKey, fnComp) 
{
	let nLow = 0;
	let nHigh = aArr.length - 1;

	while (nLow <= nHigh) 
	{
		let nMid = (nLow + nHigh) >> 1;
		let oMidVal = aArr[nMid];
		let nCmp = fnComp(oMidVal, oKey);

		if (nCmp < 0)
			nLow = nMid + 1;
		else if (nCmp > 0)
			nHigh = nMid - 1;
		else
			return nMid; // key found
	}
	return -(nLow + 1);  // key not found
}


function onSegment(oP1, oP2, oP3)
{
	return oP2[0] <= Math.max(oP1[0], oP3[0]) && oP2[0] >= Math.min(oP1[0], oP3[0]) &&
		   oP2[1] <= Math.max(oP1[1], oP3[1]) && oP2[1] >= Math.min(oP1[1], oP3[1]);
 
}

function orientation(aP1, aP2, aP3)
{
	let nVal = (aP2[1] - aP1[1]) * (aP3[0] - aP2[0]) - (aP2[0] - aP1[0]) * (aP3[1] - aP2[1]);
	
	if (nVal === 0)
		return 0; // collinear
	
	return (nVal > 0) ? 1 : 2; // 1 -> clockwise; 2 -> counterclockwise	
}


function lineSegmentsIntersect(aP1, aQ1, aP2, aQ2)
{
	// Find the four orientations needed for general and special cases
    let n1 = orientation(aP1, aQ1, aP2);
    let n2 = orientation(aP1, aQ1, aQ2);
    let n3 = orientation(aP2, aQ2, aP1);
    let n4 = orientation(aP2, aQ2, aQ1);
	
	if (n1 !== n2 && n3 !== n4)
		return true;
	
	// Special Cases
	// p1, q1 and p2 are collinear and p2 lies on segment p1q1
	if (n1 === 0 && onSegment(aP1, aP2, aQ1)) 
		return true;

	// p1, q1 and q2 are collinear and q2 lies on segment p1q1
	if (n2 === 0 && onSegment(aP1, aQ2, aQ1)) 
		return true;

	// p2, q2 and p1 are collinear and p1 lies on segment p2q2
	if (n3 === 0 && onSegment(aP2, aP1, aQ2)) 
		return true;

	// p2, q2 and q1 are collinear and q1 lies on segment p2q2
	if (n4 === 0 && onSegment(aP2, aQ1, aQ2)) 
		return true;

	return false; // Doesn't fall in any of the above cases
}


function addStyleRule(sSelector, sRule)
{
	let oSheet = document.styleSheets[document.styleSheets.length - 1];
	oSheet.insertRule(`${sSelector} {${sRule}}`, oSheet.cssRules.length);
}


function polylineMidpoint(aLine)
{
	let dLen = 0.0;
	let dLens = [];
	for (let nIndex = 0; nIndex < aLine.length - 1; nIndex++)
	{
		let oP1 = aLine[nIndex];
		let oP2 = aLine[nIndex + 1];
		let dDeltaX = oP2[0] - oP1[0];
		let dDeltaY = oP2[1] - oP1[1];
		let dSegLen = Math.sqrt(dDeltaX * dDeltaX + dDeltaY * dDeltaY);
		dLen += dSegLen;
		dLens[nIndex] = dSegLen;
	}
	let dMidLen = dLen / 2.0;
	dLen = 0.0;
	let nIndex = 0;
	while (nIndex < dLens.length && dLen < dMidLen)
		dLen += dLens[nIndex++];
	
	dLen -= dLens[--nIndex];
	let dRatio = (dMidLen - dLen) / dLens[nIndex];
	
	let oP1 = aLine[nIndex];
	let oP2 = aLine[nIndex + 1];
	let dDeltaX = (oP2[0] - oP1[0]) * dRatio;
	let dDeltaY = (oP2[1] - oP1[1]) * dRatio;
	return [oP1[0] + dDeltaX, oP1[1] + dDeltaY];
}

export {addSourceAndLayers, removeSourceAndLayers,
  pointToPaddedBounds, featureInSources,
  getBoundsForCoordArray, iconDivFromSprite, g_oLayers, removeSource,
  getPolygonBoundingBox, getLineStringBoundingBox, snapPointToWay, fromIntDeg, 
  toIntDeg, startDrawPoly, mapOffBoundFn, jqueryOffBoundFn, clearFeatures, binarySearch,
  isFeatureInsidePolygonFeature, addStyleRule, polylineMidpoint
  };