import './jquery/jquery.ui.labeledslider.min.js';
import {removeSourceAndLayers, pointToPaddedBounds, featureInSources, iconDivFromSprite, g_oLayers,
	removeSource, getPolygonBoundingBox, startDrawPoly, getLineStringBoundingBox, binarySearch} from './map-util.js';
import {initCommonMap} from './map-common.js';

let g_oMap;
let g_nSelectedScenario;
let g_oScenarios;
let g_oLoadedScenario;
let g_oImrcpIds = {};
let g_oLastHover;
let g_oStartTime;
let g_sLastLegend;
let g_oGeoJsonSrcs = 
[
	{
		'name': 'stpvt', 
		'label': 'Pavement State',
		'datasrc': 'METRO',
		'ranges':
		[
			['Dry', '#960' , 3, 4],
			['Wet', '#00af20', 5, 6],
			['Dew', '#99ffa1', 12, 13],
			['Flooded', '#39f', 30, 31],
			['Frost', '#fdafff', 13, 14],
			['Ice/Snow', '#00faff', 20, 24],
			['Plowed', '#a5cacb', 31, 32]
		],
		'valuemap': {3: 'Dry', 5: 'Wet', 12: 'Dew', 13: 'Frost', 20: 'Ice/Snow', 21: 'Ice/Snow', 22: 'Ice/Snow', 23: 'Ice/Snow', 30: 'Flooded', 31: 'Plowed'},
		'features': [],
		'layer': 
		{'id': 'stpvt', 'type': 'line', 'source': 'stpvt', 'layout':{'line-cap':'round', 'line-join':'round'}, 'paint':{'line-opacity': ['feature-state', 'opacity'], 'line-color': ['feature-state', 'color'], 
		'line-width': ['let', 'add', ['case', ['boolean', ['feature-state', 'hover'], false], 3.0, 0.0], ['interpolate', ['exponential', 2], ['zoom'], 0.0, ['+', ['var', 'add'], 1.0], 10.0, ['+', ['var', 'add'], 4.0], 24.0, ['+', ['var', 'add'], 12.0]]]}}
	},
	{
		'name': 'tpvt', 
		'label': 'Pavement Temperature',
		'datasrc': 'METRO',
		'ranges':
		[
			['Below 0', '#c51b8a' , Number.MIN_SAFE_INTEGER, 0],
			['0 - 20', '#5e4fa2', 0, 20],
			['20 - 30', '#313695', 20, 30],
			['30 - 34', '#3288bd', 30, 34],
			['34 - 45', '#238b45', 34, 45],
			['45 - 56', '#cccc00', 45, 56],
			['56 - 68', '#ff751a', 56, 68],
			['68 - 86', '#f46d43', 68, 86],
			['86 - 104', '#d53e4f', 86, 104],
			['Above 104', '#9e0142', 104, Number.MAX_SAFE_INTEGER]
		],
		'features': [],
		'layer': 
		{'id': 'tpvt', 'type': 'line', 'source': 'tpvt', 'layout':{'line-cap':'round', 'line-join':'round'}, 'paint':{'line-opacity': ['feature-state', 'opacity'], 'line-color': ['feature-state', 'color'], 
		'line-width': ['let', 'add', ['case', ['boolean', ['feature-state', 'hover'], false], 3.0, 0.0], ['interpolate', ['exponential', 2], ['zoom'], 0.0, ['+', ['var', 'add'], 1.0], 10.0, ['+', ['var', 'add'], 4.0], 24.0, ['+', ['var', 'add'], 12.0]]]}}
	},
	{
		'name': 'spdlnk',
		'label': 'Traffic Speed',
		'datasrc': 'MLP',
		'ranges': 
		[
			['0 - 15', "#8c1515", 0, 15],
			['15 - 30', "#f00", 15, 30],
			['30 - 45', "#ff751a", 30, 45],
			['45 - 60', "#ffc700", 45, 60],
			['Above 60', "#00af20", 60, Number.MAX_SAFE_INTEGER]
		],
		features:[],
		'layer': 
		{'id': 'spdlnk', 'type': 'line', 'source': 'spdlnk', 'layout':{'line-cap':'round', 'line-join':'round'}, 'paint':{'line-opacity': ['feature-state', 'opacity'], 'line-color': ['feature-state', 'color'], 
		'line-width': ['let', 'add', ['case', ['boolean', ['feature-state', 'hover'], false], 3.0, 0.0], ['interpolate', ['exponential', 2], ['zoom'], 0.0, ['+', ['var', 'add'], 1.0], 10.0, ['+', ['var', 'add'], 4.0], 24.0, ['+', ['var', 'add'], 12.0]]]}}
	}
];

let g_oVectorSrcs = 
{
	'pccat' :
	{
		'name': 'pccat',
		'label': 'Precip Rate and Type',
		'source': {'type':'vector', 'tiles':[window.location.toString().substring(0, window.location.toString().lastIndexOf('/')) + '/api/mvt/pccat/3/{z}/{x}/{y}'], 'maxzoom':16},
		'layers': 
		[
			{'id':'3_PCCAT1', 'source':'pccat', 'type':'fill', 'metadata':{'imrcp-range':'Rain - Light', 'obstype':'PCCAT'}, 'source-layer':'3_PCCAT1', 'paint':{'fill-color':'#80ff80', 'fill-antialias':false, 'fill-opacity':0.4}},
			{'id':'3_PCCAT2', 'source':'pccat', 'type':'fill', 'metadata':{'imrcp-range':'Rain - Moderate'}, 'source-layer':'3_PCCAT2', 'paint':{'fill-color':'#00af20', 'fill-antialias':false, 'fill-opacity':0.4}},
			{'id':'3_PCCAT3', 'source':'pccat', 'type':'fill', 'metadata':{'imrcp-range':'Rain - Heavy'}, 'source-layer':'3_PCCAT3', 'paint':{'fill-color':'#00561e', 'fill-antialias':false, 'fill-opacity':0.4}},
			{'id':'3_PCCAT4', 'source':'pccat', 'type':'fill', 'metadata':{'imrcp-range':'Frz Rain - Light'}, 'source-layer':'3_PCCAT4', 'paint':{'fill-color':'#d9b3ff', 'fill-antialias':false, 'fill-opacity':0.4}},
			{'id':'3_PCCAT5', 'source':'pccat', 'type':'fill', 'metadata':{'imrcp-range':'Frz Rain - Moderate'}, 'source-layer':'3_PCCAT5', 'paint':{'fill-color':'#8b4baf', 'fill-antialias':false, 'fill-opacity':0.4}},
			{'id':'3_PCCAT6', 'source':'pccat', 'type':'fill', 'metadata':{'imrcp-range':'Frz Rain - Heavy'}, 'source-layer':'3_PCCAT6', 'paint':{'fill-color':'#4a0070', 'fill-antialias':false, 'fill-opacity':0.4}},
			{'id':'3_PCCAT7', 'source':'pccat', 'type':'fill', 'metadata':{'imrcp-range':'Snow - Light'}, 'source-layer':'3_PCCAT7', 'paint':{'fill-color':'#80ffff', 'fill-antialias':false, 'fill-opacity':0.4}},
			{'id':'3_PCCAT8', 'source':'pccat', 'type':'fill', 'metadata':{'imrcp-range':'Snow - Moderate'}, 'source-layer':'3_PCCAT8', 'paint':{'fill-color':'#1a1aff', 'fill-antialias':false, 'fill-opacity':0.4}},
			{'id':'3_PCCAT9', 'source':'pccat', 'type':'fill', 'metadata':{'imrcp-range':'Snow - Heavy'}, 'source-layer':'3_PCCAT9', 'paint':{'fill-color':'#006', 'fill-antialias':false, 'fill-opacity':0.4}},
			{'id':'3_PCCAT10', 'source':'pccat', 'type':'fill', 'metadata':{'imrcp-range':'Ice Pellets - Light'}, 'source-layer':'3_PCCAT10', 'paint':{'fill-color':'#ffc2f1', 'fill-antialias':false, 'fill-opacity':0.4}},
			{'id':'3_PCCAT11', 'source':'pccat', 'type':'fill', 'metadata':{'imrcp-range':'Ice Pellets - Moderate'}, 'source-layer':'3_PCCAT11', 'paint':{'fill-color':'#ff4bf6', 'fill-antialias':false, 'fill-opacity':0.4}},
			{'id':'3_PCCAT12', 'source':'pccat', 'type':'fill', 'metadata':{'imrcp-range':'Ice Pellets - Heavy'}, 'source-layer':'3_PCCAT12', 'paint':{'fill-color':'#830d56', 'fill-antialias':false, 'fill-opacity':0.4}}
		]
	}
};

window.g_oRequirements = {'groups': 'imrcp-user;imrcp-admin'};

const setRefCookie = moment => document.cookie = `rtime=${moment.valueOf()};path=/`;
const setTimeCookie = moment => document.cookie = `ttime=${moment.valueOf()};path=/`;


async function getScenarios()
{
	let pScenarios = $.ajax(
	{
		'url': 'api/scenarios/list',
		'dataType': 'json',
		'method': 'POST',
		'data': {'token': sessionStorage.token, 'processed': true}
	}).promise();
	
	g_oScenarios = await pScenarios;
	for (let oScenario of g_oScenarios.values())
	{
		for (let [sName, oGroup] of Object.entries(oScenario.groups))
		{
			oGroup.segments.sort((a,b)=> a.localeCompare(b));
		}
	}
	setLoadHtml();
}


async function initialize()
{
	$(document).prop('title', 'IMRCP View Scenarios');
	getScenarios();
	setInterval(getScenarios, 60000);
	g_oStartTime = moment().minute(0).second(0).millisecond(0);
	g_oMap = initCommonMap('mapid', -98.585522, 39.8333333, 4, 4, 22);

	
	g_oMap.on('load', async function ()
	{
		g_oMap.addSource('areainsert', {'type': 'geojson', 'data': {'type': 'FeatureCollection', 'features': []}});
		if (g_oMap.getLayer('road-number-shield') !== undefined)
			g_oMap.addLayer({'id': 'areainsert', 'type': 'line', 'source': 'areainsert'}, 'road-number-shield');
		else
			g_oMap.addLayer({'id': 'areainsert', 'type': 'line', 'source': 'areainsert'});
		$('#dlgStatus').dialog({autoOpen: false, position: {my: "center", at: "center", of: "#map-container"}, modal: true, draggable: false, resizable: false, width: 400});
		buildTimeslider();
		buildLoad();
		buildLayers();
		buildLegend();
		buildDataTable();
		setLoadHtml();
		
		$('#loadScenario').on('click', function()
		{
			setLoadHtml();
			$('#dlgLoad').dialog('open');
		});
		$('#dlgLoad').dialog('open');
		g_oMap.on('click', mapClick);
	});
}


function mapMouseMove(oEvent)
{
	let aLayers = [];
	for (let oSrc of g_oGeoJsonSrcs.values())
		aLayers.push(oSrc.layer.id);
	
	let oFeatures = g_oMap.queryRenderedFeatures(pointToPaddedBounds(oEvent.point), {'layers': aLayers});
	if (oFeatures.length > 0)
	{
		$(g_oMap.getCanvas()).addClass('clickable');
		let oTemp;
		for (let oFeature of oFeatures.values())
		{
			if (oFeature.state.opacity === 1)
			{
				oTemp = oFeature;
				break;
			}
		}
		let oFeature = g_oMap.getSource(oTemp.source)._data.features[oTemp.id];
		if (g_oLastHover)
		{
			if (g_oLastHover.properties.imrcpid !== oFeature.properties.imrcpid)
			{
				g_oMap.setFeatureState(g_oLastHover, {'hover': false});
				g_oMap.setFeatureState(oFeature, {'hover': true});
				g_oLastHover = oFeature;
			}
		}
		else
		{
			g_oMap.setFeatureState(oFeature, {'hover': true});
			g_oLastHover = oFeature;
		}
	}
	else
	{
		$(g_oMap.getCanvas()).removeClass('clickable');
		if (g_oLastHover)
		{
			g_oMap.setFeatureState(g_oLastHover, {'hover': false});
			g_oLastHover = undefined;
		}
	}
}


function mapClick(oEvent)
{
	if (!g_oLastHover)
		return;
	
	$('#datatable tbody > tr').remove();
	let nTimeIndex = $('#timeslider').labeledSlider('value');
	let lScenarioTime = g_oLoadedScenario.starttime;
	let sStart = moment(lScenarioTime).add(nTimeIndex, 'hour').format('MM-DD HH:mm');
	let sEnd = moment(lScenarioTime).add(nTimeIndex + 1, 'hour').format('MM-DD HH:mm');
	let sRowHtml = '';
	let nFeatureIndex = g_oImrcpIds[g_oLastHover.properties.imrcpid];
	for (let oSrc of g_oGeoJsonSrcs.values())
	{
		sRowHtml += '<tr>';
		sRowHtml += `<td class="obsType">${oSrc.name.toUpperCase()}</td>`;
		sRowHtml += `<td class="obsType">${oSrc.datasrc.toUpperCase()}</td>`;
		sRowHtml += `<td class="timestamp">${sStart}</td>`;
		sRowHtml += `<td class="timestamp">${sEnd}</td>`;
		if (oSrc.features[nFeatureIndex].properties.values === undefined)
			sRowHtml += `<td class="td-value">-999.0</td>`;
		else
			sRowHtml += `<td class="td-value">${oSrc.valuemap ? oSrc.valuemap[oSrc.features[nFeatureIndex].properties.values[nTimeIndex]] : oSrc.features[nFeatureIndex].properties.values[nTimeIndex].toFixed(1)}</td>`;
		sRowHtml += '</tr>';
	}
	$('#datatable tbody').append(sRowHtml);
	
	$('#actiontable tbody > tr').remove();
	sRowHtml = '';
	for (let [sName, oGroup] of Object.entries(g_oLoadedScenario.groups))
	{
		if (binarySearch(oGroup.segments, g_oLastHover.properties.imrcpid, (a, b) => a.localeCompare(b)) >= 0)
		{
			for (let [sKey, aValues] of Object.entries(oGroup))
			{
				if (sKey === 'segments')
					continue;
				
				sRowHtml += '<tr>';
				sRowHtml += `<td class="obsType">${sKey}</td>`;
				sRowHtml += `<td class="obsType">${sName}</td>`;
				sRowHtml += `<td class="timestamp">${sStart}</td>`;
				sRowHtml += `<td class="timestamp">${sEnd}</td>`;
				if (sKey === 'label')
					sRowHtml += `<td class="td-value">${aValues}</td>`;
				else
					sRowHtml += `<td class="td-value">${aValues[nTimeIndex]}</td>`;
				
				if (sKey === 'lanes')
					sRowHtml += `<td class="obsType" style="text-align: center;">${g_oLastHover.properties.lanecount}</td>`;
				else if (sKey === 'vsl')
					sRowHtml += `<td class="obsType" style="text-align: center;">${g_oLastHover.properties.spdlimit}</td>`;
				else
					sRowHtml += `<td class="obsType"></td>`;
				sRowHtml += '</tr>';
			}
		}
	}
	$('#actiontable tbody').append(sRowHtml);
	$('#tabtables').tabs('option', 'active', 0);
	$('#dlgDataTable').dialog('open');
	g_oMap.setFeatureState(g_oLastHover, {'hover': false});
	g_oLastHover = undefined;
}


function buildTimeslider()
{
	let nMin = 0;
	let nMax = 24;
	let nLabelStep = 2;
	let nTickStep = 1;
	let aLabels = [];
	
	
//	for (let nOffset = nLabelStep; nOffset < nMax; nOffset += nLabelStep)
//		aLabels.push(nOffset);
	
	let aTicks = [];	
	for (let nOffset = nTickStep; nOffset < nMax; nOffset += nTickStep)
		aTicks.push(nOffset);
	
	$('#timeslider').labeledSlider(
	{
		slide: function(event, ui) 
		{
			let oUpdateTime = moment(g_oStartTime).add(ui.value, 'hour');
			updateTimeSlider(oUpdateTime);
		},
		labelPosition: 'on',
		min: nMin,
		max: nMax,
		step: 1,
		value: 0,
		ticks: true,
		labelValues: aLabels,
		tickValues: aTicks,
		stop: function (event, ui)
		{
			setTimeCookie(moment(g_oStartTime).add(ui.value, 'hour'));
			updateVectors();
			updateColors();
		}
	});
	$('.timeslider .ui-slider-handle').css('width', '7.5em');
	updateTimeSlider(moment(g_oStartTime));
}


function updateTimeSlider(oMoment)
{
	$('#timeslider > .ui-slider-handle').text(oMoment.format('MM/DD HH:mm'));
}

function buildLoad()
{
	let oDialog = $('#dlgLoad');
	oDialog.dialog({autoOpen: false, position: {my: "center", at: "center", of: "#map-container"}, modal: true, draggable: false, resizable: false, width: 450,
		'title': "Select Scenario to Load",
		buttons: [
			{text: 'Cancel', click: function() 
				{
					oDialog.dialog('close');
				}},
			{text: 'Load Scenario', id: 'btnLoad', click: loadScenario}
		]});
	oDialog.html('');
}


function buildLayers()
{
	let oDialog = $('#dlgLayers');
	oDialog.dialog({autoOpen: false, position: {my: "left bottom", at: "left+8 bottom-8", of: "#map-container"}, modal: false, draggable: false, resizable: false, width: 300,
	'title': "Map Layers",
	'closeOnEscape': false,
	buttons: [
		{text: 'Load Scenario', id: 'btnOpenLoad', click: function()
		{
			$('#dlgLoad').dialog('open');
		}}
	]});
	oDialog.siblings().children('.ui-dialog-titlebar-close').remove();
	
	let oHtml = $(`<ul class="w3-ul ul-no-border obstype-pane-list"></ul>`);
	for (let [sKey, oSrc] of Object.entries(g_oVectorSrcs))
	{
		let oLi = $(`<li><label><input id="${sKey}" type="checkbox" value="${oSrc.label}"/>${oSrc.label}</label></li>`);
		let oLegendOpen = $(`<i class="w3-right	fa fa-window-restore clickable" aria-hidden="true"></i>`);
		oLi.append(oLegendOpen);
		oLegendOpen.on('click', {'obstype': sKey}, setLegend);
		oHtml.append(oLi);
	}
	
	let nCount = 0;
	for (let oSrc of g_oGeoJsonSrcs.values())
	{
		let oLi = $(`<li><label><input id="${oSrc.name}" name="roadradio" type="radio" value="${oSrc.label}"/>${oSrc.label}</label></li>`);
		let oLegendOpen = $(`<i class="w3-right fa fa-window-restore clickable" aria-hidden="true"></i>`);
		oLegendOpen.on('click', {'index': nCount++}, setLegend);
		oLi.append(oLegendOpen);
		oHtml.append(oLi);
	}
	
	oDialog.html('');
	oDialog.append($('<div></div>').append(oHtml));
	$('#dlgLayers input[type=checkbox').on('click', function()
	{
		if ($(this).prop('checked'))
			addVectorSrc($(this).prop('id'));
		else
			removeVectorSrc($(this).prop('id'));
	});
	$('#dlgLayers input[name=roadradio]')[0].click();
	$('#dlgLayers input[name=roadradio]').on('change', function() 
	{
		updateOpacity();
	});
	oDialog.dialog('open');
}


function buildLegend()
{
	let oDialog = $('#dlgLegend');
	oDialog.dialog({autoOpen: false, position: {my: "left bottom", at: "right bottom", of: "#dlgLayers"}, modal: false, draggable: false, resizable: false, width: 'auto',
	'dialogClass' :'no-title-form',
	'closeOnEscape': false
	});
	
	
}


function setLegend(oEvent)
{
	let oDialog = $('#dlgLegend');
	if (oDialog.dialog('isOpen'))
	{
		oDialog.dialog('close');
		if ((g_sLastLegend || g_sLastLegend >= 0) && g_sLastLegend == oEvent.data.index || g_sLastLegend == oEvent.data.obstype)
		{
			g_sLastLegend = undefined;
			return;
		}
	}
	oDialog.html('');
	let sHtml = `<ul id="legendlist">`;
	let oSrc;
	if (oEvent.data.index || oEvent.data.index >= 0)
	{
		oSrc = g_oGeoJsonSrcs[oEvent.data.index];
		for (let aArr of oSrc.ranges.values())
			sHtml += `<li><i class="fa fa-stop " style="color: ${aArr[1]}"></i>&nbsp;${aArr[0]}</li>`;
		g_sLastLegend = oEvent.data.index;
	}
	else
	{
		oSrc = g_oVectorSrcs[oEvent.data.obstype];
		for (let oLayer of oSrc.layers.values())
			sHtml += `<li><i class="fa fa-stop " style="color: ${oLayer.paint['fill-color']}"></i>&nbsp;${oLayer.metadata['imrcp-range']}</li>`;
		g_sLastLegend = oEvent.data.obstype;
	}
	sHtml += '</ul>';
	let oX = $(`<i style="padding-right:3px; padding-left:6px; padding-top:3px" class="fa fa-times clickable fa-lg w3-right"></i>`);
	oX.click(function() 
	{
		oDialog.dialog('close');
	});
	let oTitle = $(`<span class="w3-sand w3-round legend-heading "><span style="padding-left: 2px;" class="heading-title">${oSrc.label}</span></span>`);
	oTitle.append(oX);
	
	oDialog.append(oTitle).append(sHtml);;
	oDialog.dialog('option', 'position', {my: "left bottom", at: "right bottom", of: "#dlgLayers"});
	oDialog.dialog('open');
}


function setLoadHtml()
{
	$('#selectScenarios').remove();
	g_nSelectedScenario = -1;
	g_oScenarios.sort((a, b) => a.name.localeCompare(b.name));
	let sHtml = `<ul id="selectScenarios">`;
	for (let nIndex = 0; nIndex < g_oScenarios.length; nIndex++)
	{
		let oScenario = g_oScenarios[nIndex];
		let sPending = '';
		if (!oScenario.processed)
			sPending += '<div class="flex1"><i class="flex1 fa fa-spinner fa-spin"></i></div>';
		sHtml += `<li id="scenario${nIndex}" class="clickable flexbox"><div class="flex10">${oScenario.name}</div>${sPending}</li>`;
	}
	sHtml += `</ul><div>Select a scenario to load, then click 'Load'. Scenarios that have a <i class="fa fa-spinner fa-spin"></i> have not been processed yet.`;
	
	$('#dlgLoad').html(sHtml);
	$('#selectScenarios > li').on('click', function() 
	{
		if (g_nSelectedScenario >= 0)
			$('#scenario' + g_nSelectedScenario).removeClass('w3-fhwa-navy');
		$(this).removeClass('hoverScenario');
		let sId = $(this).prop('id');
		
		g_nSelectedScenario = sId.substring('scenario'.length);
	}).on('mouseenter', function()
	{
		let nNum = $(this).prop('id').substring('scenario'.length);
		if (nNum != g_nSelectedScenario)
			$(this).addClass('w3-fhwa-navy hoverScenario');
	}).on('mouseleave', function()
	{
		let nNum = $(this).prop('id').substring('scenario'.length);
		if (nNum == g_nSelectedScenario)
			$(this).removeClass('hoverScenario');
		else
			$(this).removeClass('w3-fhwa-navy hoverScenario');
	});
}


function loadScenario()
{
	if (g_nSelectedScenario < 0)
		return;
	$('#dlgLoad').dialog('close');
	let oScenario = g_oScenarios[g_nSelectedScenario];
	if (!oScenario.processed)
	{
		status('Scenario Not Processed', 'This scenario has not been processed yet. Try again later');
		return;
	}
	$('#pageoverlay').html(`<p class="centered-element">Loading scenario ${g_oScenarios[g_nSelectedScenario].name}</p>`).css({'opacity': 0.5, 'font-size': 'x-large'}).show();
	$.ajax(
	{
		'url': 'api/scenarios/data',
		'dataType': 'json',
		'method': 'POST',
		'data': {'token': sessionStorage.token, 'id': g_oScenarios[g_nSelectedScenario].id}
	}).done(function(oData)
	{
		$('#dlgLayers').dialog('option', 'title', `Map Layers - ${oScenario.name}`);
		g_oLoadedScenario = oScenario;
		g_oMap.off('mousemove', mapMouseMove);
		g_oStartTime = moment(oScenario.starttime);
		setRefCookie(g_oStartTime);
		setTimeCookie(g_oStartTime);
		updateTimeSlider(moment(g_oStartTime));
		$('#timeslider').labeledSlider('value', 0);
		
		for (let oSrc of g_oGeoJsonSrcs.values())
		{
			removeSource(oSrc.name, g_oMap);
			oSrc.features = [];
		}
		
		g_oImrcpIds = {};
		let nCount = 0;
		let nMinLon = Number.MAX_SAFE_INTEGER;
		let nMinLat = Number.MAX_SAFE_INTEGER;
		let nMaxLon = Number.MIN_SAFE_INTEGER;
		let nMaxLat = Number.MIN_SAFE_INTEGER;
		for (let oFeature of oData.values())
		{
			getLineStringBoundingBox(oFeature);
			for (let oSrc of g_oGeoJsonSrcs.values())
				oSrc.features.push({'type': 'Feature', 'source': oSrc.name, 'bbox': oFeature.bbox, 'id': nCount, 'properties': {'imrcpid': oFeature.properties.imrcpid, 'values': oFeature.properties[oSrc.name], 'lanecount': oFeature.properties.lanecount, 'spdlimit': oFeature.properties.spdlimit}, 'geometry': {'type': 'LineString', 'coordinates': oFeature.geometry.coordinates}});
			
			g_oImrcpIds[oFeature.properties.imrcpid] = nCount++;
			
			
			let aMins = oFeature.bbox[0];
			let aMaxes = oFeature.bbox[1];
			if (aMins[0] < nMinLon)
				nMinLon = aMins[0];
			if (aMins[1] < nMinLat)
				nMinLat = aMins[1];
			if (aMaxes[0] > nMaxLon)
				nMaxLon = aMaxes[0];
			if (aMaxes[1] > nMaxLat)
				nMaxLat = aMaxes[1];
		}
		
		for (let oSrc of g_oGeoJsonSrcs.values())
		{
			g_oMap.addSource(oSrc.name, {'type': 'geojson', 'data': {'type': 'FeatureCollection', 'features': oSrc.features}});
			for (let oFeature of g_oMap.getSource(oSrc.name)._data.features.values())
				g_oMap.setFeatureState(oFeature, {'opacity': 0, 'color': 'black'});
			
			if (g_oMap.getLayer('road-number-shield') !== undefined)
				g_oMap.addLayer(oSrc.layer, 'road-number-shield');
			else
				g_oMap.addLayer(oSrc.layer);
		}

		updateColors();
		updateOpacity();
		g_oMap.fitBounds([[nMinLon, nMinLat], [nMaxLon, nMaxLat]], {'padding': 50, 'linear': false, 'duration': 0});
		g_oMap.on('mousemove', mapMouseMove);
	}).fail(function(oRes) 
	{
		status(oRes.status, oRes.msg);
		g_oLoadedScenario = undefined;
	}).always(function()
	{
		$('#pageoverlay').hide();
	});
}

function status(sTitle, sHtml)
{
	let oStatus = $('#dlgStatus');
	oStatus.dialog('option', 'title', sTitle);
	oStatus.html(sHtml);
	oStatus.dialog('open');
}


function updateColors()
{
	let nTimeIndex = $('#timeslider').labeledSlider('value');
	for (let oSrc of g_oGeoJsonSrcs.values())
	{
		for (let oFeature of oSrc.features.values())
		{
			let oValues = oFeature.properties.values;
			let dVal = oValues === undefined ? -999.0 : oFeature.properties.values[nTimeIndex];
			let sColor = oSrc.ranges[0][1];
			for (let nIndex = 0; nIndex < oSrc.ranges.length; nIndex++)
			{
				let aRange = oSrc.ranges[nIndex];
				if (dVal >= aRange[2] && dVal < aRange[3])
				{
					sColor = aRange[1];
					break;
				}
			}
			
			g_oMap.setFeatureState(oFeature, {'color': sColor});
		}
	}
}

function updateOpacity()
{
	let sChecked = $('#dlgLayers input[name=roadradio]:checked').prop('id');
	for (let oSrc of g_oGeoJsonSrcs.values())
	{
		let nOpacity = oSrc.name === sChecked ? 1.0 : 0;
		for (let oFeature of oSrc.features.values())
			g_oMap.setFeatureState(oFeature, {'opacity': nOpacity});
	}
}


function removeVectorSrc(sSrc)
{
	let oSrc = g_oVectorSrcs[sSrc];
	for (let oLayer of oSrc.layers.values())
	{
		if (g_oMap.getLayer(oLayer.id))
			g_oMap.removeLayer(oLayer.id);
	}
	g_oMap.removeSource(oSrc.name);
}


function addVectorSrc(sSrc)
{
	let oSrc = g_oVectorSrcs[sSrc];
	g_oMap.addSource(oSrc.name, oSrc.source);
	for (let oLayer of oSrc.layers.values())
		g_oMap.addLayer(oLayer, 'areainsert');
}


function updateVectors()
{
	$('#dlgLayers input[type=checkbox]').each(function()
	{
		if ($(this).prop('checked'))
		{
			let sId = $(this).prop('id');
			removeVectorSrc(sId);
			addVectorSrc(sId);
		}
	});
}


function buildDataTable()
{
	let oDialog = $('#dlgDataTable');
	let sHtml = `<div id="tabtables">
		<ul>
			<li><a href="#datatable">Data</a></li>
			<li><a href="#actiontable">Actions</a></li>
		</ul>`;
	sHtml += `<div id="datatable"><table id="obs-data" class = "pure-table pure-table-bordered">
		<thead class="obs-table">   
			<tr class="w3-sand">     
				<td>ObsType</td>
				<td>Source</td>
				<td>Start Time</td>
				<td>End Time</td>
				<td>Value</td> 
			</tr>
		</thead>
    <tbody class="obs-table pure-table-striped"></tbody></table> </div>`;
	sHtml += `<div id="actiontable"><table id="obs-data" class = "pure-table pure-table-bordered">
		<thead class="obs-table">   
			<tr class="w3-sand">     
				<td>Action</td>
				<td>Group</td>
				<td>Start Time</td>
				<td>End Time</td>
				<td>Value</td>
				<td>Normal Value</td>
			</tr>
		</thead>
	<tbody class="obs-table pure-table-striped"></tbody></table></div>`;
	sHtml += `</div>`;
	oDialog.dialog({autoOpen: false, title: 'Segment Detail', resizable: true, draggable: true, width: 'auto', modal: true, position: {my: "center", at: "center", of: '#map-container'}, minHeight: 380, maxHeight: 600});
	oDialog.html(sHtml);
	$('#tabtables').tabs();
}

$(document).on('initPage', initialize);

