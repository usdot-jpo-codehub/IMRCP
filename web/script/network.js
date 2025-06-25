import {buildNetworkDialog, buildRoadLegendDialog, buildCancelDialog, buildNetworkLegendDialog, buildInstructionDialog, buildDetectorDialog,
		buildDetectorEditDialog, buildDetectorStatusDialog, buildUploadConfirmDialog, buildNetworkMetadataDialog, buildReprocessDialog,
		buildDeleteConfirmDialog, buildPublishConfirmDialog, buildTrainingConfirmDialog, buildEditPublishConfirmDialog, buildOverwritePublishConfirmDialog, buildNetworkSelectDialog} from './dialogs.js';
	
import {switchToDetector, leaveDetectors, checkDetectorFile, uploadFile} from './detectors.js';

import {g_oLayers, removeSource, getPolygonBoundingBox, startDrawPoly} from './map-util.js';
import {getNetworksAjax, MapControlIcons, initCommonMap, ASSEMBLING, WORKINPROGRESS, PUBLISHING, PUBLISHED, ERROR, TRAINING, isStatus} from './map-common.js';

let g_oMap;
let g_nHoverId;
let g_oNetworkHover = {};
let g_oClickedNetworks;
let g_nHoverPointId;
let g_sNetworkCoords;
let g_sCurNetwork = null;
let g_oCurBounds;
let g_oHashes = {};
let BUCKET_SPACING = 100000;
let g_sCurHash = null;
let g_oWays = {};
let g_bLoadingHash = false;
let g_oDialogs = {};
let g_oSpotlight = [];
let g_oMerge = [];
let g_nSplitWay;
let g_nSplitNode;
let g_nEdits = 0;
let g_sCursor = '';
let g_nEditMode = -1;
let g_sCurPointHoverLayer;
let g_sCurPointHoverSource;
let g_oPopup;
let g_oReprocess;
let g_oDelete;
let g_sSatelliteUrl = 'mapbox/satellite-tiles.json';
//let g_sSatelliteUrl = 'mapbox://mapbox.satellite';
let g_oInstructions = 
{
	'select' : ['Left-click a polygon to view/edit network. The label is displayed in the popup. If there are overlapping polygons, left-click to get a list of possible networks to select from.', 'Network Overview'],
	'delete' : ['Left-click a polygon to delete that network.<br>Esc: Exit Mode', 'Delete Network'],
	'reprocess' : ['Left-click a polygon to reprocess that network.<br>Esc: Exit Mode', 'Reprocess Network'],
	'edit' : ['Select an editing mode from the right side toolbar.', 'View/Edit Network'],
	'add' : ['Roads not included in network will be loaded on demand when the cursor is close to them<br>Left-click a road to add or remove<br>Esc: Exit mode', 'Add/Remove Road'],
	'merge' : ['Left-click two roads to merge<br>Enter: Commit merge<br>Esc: Cancel', 'Merge Roads'],
	'split' : ['Left-click road to split<br>Esc: Cancel', 'Split Road'],
	'upload' : ['Select "Upload File" to upload a detector file', 'View/Edit Traffic Detectors'],
	'editdet' : ['Left-click the point of detection for this detector on the map.<br>Select the next button or left-click a detector to edit a different detector.', 'Edit Detectors'],
	'det' : ['Left-click a detector or click "Edit Detectors" to open edit dialog.<br>Select "Upload File" to upload a different detector file.', 'View/Edit Traffic Detectors'],
	'detsave' : ['Save or revert changes', 'Edit Detectors']
};

window.g_oRequirements = {'groups': 'imrcp-admin'};

async function initialize()
{
	$(document).prop('title', 'IMRCP Manage Roads');
	g_oMap = initCommonMap('mapid', -98.585522, 39.8333333, 4, 4, 24);
	let oMainControl = new MapControlIcons([{t:'Create Network', i:'drawpoly'}, {t:'Delete Network', i:'delete'}, {t:'Reprocess Network', i:'reload'}, {t:'Toggle Network Legend', i:'toggle'}], 'main-control');
	let oEditControl = new MapControlIcons([{t:'Add/Remove Road', i:'add'}, {t:'Save Changes', i:'save'}, {t:'Publish Network', i:'check'}, {t:'Train Hurricane Traffic Model', fa:'fa fa-train fa-lg'}, {t:'Toggle Road Legend', i:'toggle'}, {t:'Go Back', i:'cancel'}], 'edit-control');
	let oTileControl = new MapControlIcons([{t:'Toggle Satellite Background', i:'satellite'}, {t:'Toggle Instructions', i:'instruction'}, {t:'Toggle Metadata', i:'metadata'}]);
	let oDetectorControl = new MapControlIcons([{t:'Upload File', i:'file'}, {t:'Edit Detectors', i:'edit'}, {t:'Toggle Detector Legend', i:'toggle'}, {t:'Back To Network Edit', i:'cancel'}], 'detector-control');

	g_oMap.addControl(oTileControl, 'top-right');
	g_oMap.addControl(oMainControl, 'top-right');
	g_oMap.addControl(oEditControl, 'top-right');
	g_oMap.addControl(oDetectorControl, 'top-right');
	$('#detector-control').hide();
	$('#edit-control').hide();
	
	$("button[title|='Create Network']").click(startDrawPoly.bind(
	{
		'map': g_oMap,
		'startDraw': startCreateNetwork,
		'initPoly': initCreateNetwork,
		'movePoly': moveCreateNetwork,
		'addPoint': addPointCreateNetwork,
		'finishDraw': finishCreateNetwork,
		'cancelDraw': cancelCreateNetwork
	}));
	$("button[title|='Delete Network']").click(startDeleteNetwork);
	$("button[title|='Reprocess Network']").click(startReprocessNetwork);
	$("button[title|='Save Changes']").click(saveChanges);
	$("button[title|='Add/Remove Road']").click(startAddRemove);
	$("button[title|='Go Back']").click(confirmCancel);
	$("button[title|='Back To Network Edit']").click(leaveDetectors);
	$("button[title|='Toggle Satellite Background']").click(toggleSatellite);
	$("button[title|='Toggle Metadata']").click({'dialog': 'networkmetadata'}, toggleDialog).hide();
	$("button[title|='Upload File']").click(checkDetectorFile);
	$("button[title|='Publish Network']").click(publishNetwork);
	$("button[title|='Train Hurricane Traffic Model']").click(trainHurricane);
	$('#detectorFile').on('change', uploadFile);
	
	g_oMap.on('load', async function() {
		buildNetworkDialog();
		buildRoadLegendDialog();
		buildCancelDialog();
		buildNetworkLegendDialog();
		buildInstructionDialog();
		buildDetectorDialog();
		buildDetectorEditDialog();
		buildDetectorStatusDialog();
		buildUploadConfirmDialog();
		buildNetworkMetadataDialog();
		buildReprocessDialog();
		buildDeleteConfirmDialog();
		buildPublishConfirmDialog();
		buildTrainingConfirmDialog();
		buildEditPublishConfirmDialog();
		buildOverwritePublishConfirmDialog();
		buildNetworkSelectDialog();
		instructions(g_oInstructions['select']);
		startSelectNetwork();
		g_oPopup = new mapboxgl.Popup({closeButton: false, closeOnClick: false, anchor: 'bottom', offset: [0, -25]});
	});
}


function trainHurricane()
{
	$(g_oDialogs['trainingconfirm']).dialog('open');
	document.activeElement.blur();
}


function confirmTrainingNetwork()
{
	$.ajax(
	{
		'url': 'api/generatenetwork/train',
		'method': 'POST',
		'dataType': 'json',
		'data': {'networkid': g_sCurNetwork, 'token': sessionStorage.token}
	}).done(function() 
	{
		g_sCursor = '';
		g_oMap.getCanvas().style.cursor = g_sCursor;
		instructions(g_oInstructions['select'], 'Network successfully queued to train');;
		switchToMain();
	}).fail(function(jqXHR) 
	{
		$('#instructions-error').html(JSON.parse(jqXHR.responseText).msg);
		g_oMap.getCanvas().style.cursor = g_sCursor;
	});
}


function toggleSatellite()
{
	if (g_oMap.getSource('mapbox://mapbox.satellite') === undefined)
	{
		let sBefore = 'road-number-shield';
		if (g_oMap.getLayer('geo-lines') !== undefined)
			sBefore = 'geo-lines';

		g_oMap.addSource('mapbox://mapbox.satellite', {'url': g_sSatelliteUrl, 'type': 'raster', 'tileSize': 256});
		if (g_oMap.getLayer(sBefore) !== undefined)
			g_oMap.addLayer(g_oLayers['satellite'], sBefore);
		else
			g_oMap.addLayer(g_oLayers['satellite']);
	}
	else
	{
		removeSource('mapbox://mapbox.satellite', g_oMap);
	}
}


function toggleDialog(oEvent)
{
	let oDialog = $(g_oDialogs[oEvent.data.dialog]);
	if (oDialog.dialog('isOpen'))
		oDialog.dialog('close');
	else
	{
		oDialog.dialog('open');
		document.activeElement.blur();
	}
}


function startSelectNetwork(bLoaded)
{
	g_oClickedNetworks = undefined
	g_oMap.flyTo({center: [-98.585522, 39.8333333], zoom: 4});
	g_oMap.off('click', clickNetwork);
	g_oMap.off('mousemove', mousemoveNetworkPolygons);
	/*
	g_oMap.off('mouseenter', 'network-polygons', mouseenterNetworkPolygons);
	g_oMap.off('mouseleave', 'network-polygons', mouseleaveNetworkPolygons);
	*/
	let oMetadata = $(g_oDialogs['networkmetadata']);
	if (oMetadata.dialog('isOpen'))
		oMetadata.dialog('close');
	$("button[title|='Toggle Metadata']").hide();
	if (bLoaded)
	{
		g_oMap.on('click', clickNetwork);
		g_oMap.on('mousemove', mousemoveNetworkPolygons);
		/*
		g_oMap.on('mouseenter', 'network-polygons', mouseenterNetworkPolygons);
		g_oMap.on('mouseleave', 'network-polygons', mouseleaveNetworkPolygons);
		*/
		g_sCursor = '';
		g_oMap.getCanvas().style.cursor = g_sCursor;
		let oSrc = g_oMap.getSource('network-polygons');
		for (let nIndex = 0; nIndex < oSrc._data.features.length; nIndex++)
			g_oMap.setFeatureState({'id': nIndex, 'source': 'network-polygons'}, {'hover': false, 'hidden': false});
	}
	else
	{
		getNetworksAjax().done(networksSuccess).fail(function() 
		{
			$('#instructions-error').html('Failed to retrieve networks. Try again later.');
		});
	}
}


function networksSuccess(data, textStatus, jqXHR)
{
	let oGeoJson = {};
	oGeoJson.type = 'FeatureCollection';
	oGeoJson.features = [];
	$("button[title|='Train Hurricane Traffic Model']").prop('disabled', true).addClass('ui-button-disabled ui-state-disabled');
	for (let value of data.values())
	{
		let nStatus = value.properties.status;
		let nDisplayStatus = 4;
		if (isStatus(nStatus, ERROR))
			nDisplayStatus = 4;
		else if (isStatus(nStatus, ASSEMBLING))
			nDisplayStatus = 0;
		else if (isStatus(nStatus, WORKINPROGRESS))
			nDisplayStatus = 1;
		else if (isStatus(nStatus, PUBLISHING))
			nDisplayStatus = 2;
		else if (isStatus(nStatus, TRAINING))
			nDisplayStatus = 5;
		else if (isStatus(nStatus, PUBLISHED))
			nDisplayStatus = 3;
	
		value.properties.displaystatus = nDisplayStatus;
		oGeoJson.features.push(value);
		
	}
	
	removeSource('network-polygons', g_oMap);
	g_oMap.addSource('network-polygons', {'type': 'geojson', 'data': oGeoJson, 'generateId': true});
	g_oMap.addLayer(g_oLayers['network-polygons']);
	g_oMap.addLayer(g_oLayers['network-polygons-del']);
	
	if (oGeoJson.features.length === 0)
	{
		$('#instructions-error').html('No networks have been created. Create one by using the "Create Network" button');
		return;
	}
	
	g_oMap.on('click', clickNetwork);
	g_oMap.on('mousemove', mousemoveNetworkPolygons);
	/*
	g_oMap.on('mouseenter', 'network-polygons', mouseenterNetworkPolygons);
	g_oMap.on('mouseleave', 'network-polygons', mouseleaveNetworkPolygons);
	*/
}


function clickNetwork(oEvent)
{
	let oFeatures = g_oMap.queryRenderedFeatures([[oEvent.point.x - 2, oEvent.point.y + 2], [oEvent.point.x + 2, oEvent.point.y - 2]], {'layers': ['network-polygons']});
	if (oFeatures.length === 0)
		return;
	
	g_oClickedNetworks = oFeatures;
	if (oFeatures.length === 1)
	{
		loadNetwork(oFeatures[0].id);
		return;
	}

	let oBB = [[Number.MAX_VALUE, Number.MAX_VALUE], [-Number.MAX_VALUE, -Number.MAX_VALUE]];
	let oDialog = $(g_oDialogs['networkselect']);
	let oList = $('#networkselectlist');
	oList.empty();
	let oFirst = undefined;
	let oSrc = g_oMap.getSource('network-polygons');
	let oData = oSrc._data;
	for (let nIndex = 0; nIndex < oData.features.length; nIndex++)
	{
		g_oMap.setFeatureState({'id': nIndex, 'source': 'network-polygons'}, {'hidden': true});
	}
	for (let oTemp of oFeatures.values())
	{
		let oFeature = oData.features[oTemp.id];
		getPolygonBoundingBox(oFeature);
		if (oFeature.bbox[0][0] < oBB[0][0])
			oBB[0][0] = oFeature.bbox[0][0];
		if (oFeature.bbox[0][1] < oBB[0][1])
			oBB[0][1] = oFeature.bbox[0][1];
		if (oFeature.bbox[1][0] > oBB[1][0])
			oBB[1][0] = oFeature.bbox[1][0];
		if (oFeature.bbox[1][1] > oBB[1][1])
			oBB[1][1] = oFeature.bbox[1][1];
		
		let oLi = $(`<li class="clickable">${oFeature.properties.label}</li>`);
		if (oFirst === undefined)
		{
			oLi.addClass('w3-fhwa-navy');
			g_oMap.setFeatureState(oTemp, {'hover': true, 'hidden': false});
			oFirst = [oLi, oTemp];
		}
		oLi.hover(function() 
		{
			g_oMap.setFeatureState(oTemp, {'hover': true, 'hidden': false});
			$(this).addClass('w3-fhwa-navy');
			if (oTemp.id !== oFirst[1].id)
			{
				g_oMap.setFeatureState(oFirst[1], {'hover': false, 'hidden': true});
				oFirst[0].removeClass('w3-fhwa-navy');
			}
		}, function()
		{
			g_oMap.setFeatureState(oTemp, {'hover': false, 'hidden': true});
			$(this).removeClass('w3-fhwa-navy');
		}).click(function()
		{
			loadNetwork(oTemp.id);
		});
		oList.append(oLi);
	}
	g_oMap.fitBounds(oBB, {'padding': 50});
	g_oMap.off('click', clickNetwork);
	g_oMap.off('mousemove', mousemoveNetworkPolygons);
	g_oMap.off('mousemove', mousemovePopupPos);
	g_oMap.getCanvas().style.cursor = g_sCursor;
	g_oPopup.remove();
	if (!oDialog.dialog('isOpen'))
		oDialog.dialog('open');
}


function closeNetworkSelect()
{
	if (Array.isArray(g_oClickedNetworks))
		startSelectNetwork(true);
}

function loadNetwork(nId)
{
	if (g_oClickedNetworks === undefined)
		return;
	
	let oFeature = null;
	for (let oTemp of g_oClickedNetworks.values())
	{
		if (oTemp.id === nId)
			oFeature = oTemp;
	}
	
	if (oFeature === null)
		return;
	
	g_oClickedNetworks = oFeature;
	if (isStatus(oFeature.properties.status, ASSEMBLING))
	{
		$('#instructions-error').html('Network still assembling.');
		return;
	}
	else if (isStatus(oFeature.properties.status, PUBLISHING))
	{
		$('#instructions-error').html('Network publishing still in progress');
		return;
	}
	else if (isStatus(oFeature.properties.status, TRAINING))
	{
		$('#instructions-error').html('Hurricane traffic model training still in progress');
		//return;
	}
	else if (isStatus(oFeature.properties.status, PUBLISHED))
	{
		$("button[title|='Train Hurricane Traffic Model']").prop('disabled', false).removeClass('ui-button-disabled ui-state-disabled');
		$(g_oDialogs['editpublishconfirm']).dialog('open');
		return;
	}
	else if (isStatus(oFeature.properties.status, ERROR))
	{
		$('#instructions-error').html('Error occured while assembling, publishing, or training network.');
		return;
	}
	
	confirmLoadNetwork();
}


function confirmLoadNetwork()
{
	let oFeature = g_oClickedNetworks;
	$('#networkselectlist').empty();
	$(g_oDialogs['networkselect']).dialog('close');

	instructions(g_oInstructions['edit']);
	$.ajax(
	{
		'url': 'api/generatenetwork/geo',
		'dataType': 'json',
		'method': 'POST',
		'networkid': oFeature.properties.networkid,
		'networklabel': oFeature.properties.label,
		'data': {'token': sessionStorage.token, 'networkid': oFeature.properties.networkid, 'published': 'false'}
	}).done(selectSuccess).fail(function() 
	{
		$('#instructions-error').html('Failed to retrieve network.');
		g_oMap.getCanvas().style.cursor = g_sCursor;
	});
	g_sCursor = 'wait';
	
	g_oMap.getCanvas().style.cursor = g_sCursor;
	g_oMap.off('click', clickNetwork);
	g_oMap.off('mousemove', mousemoveNetworkPolygons);
	let oSrc = g_oMap.getSource('network-polygons');
	let oData = oSrc._data;
	for (let nIndex = 0; nIndex < oData.features.length; nIndex++)
	{
		if (nIndex === oFeature.id)
		{
			g_oCurBounds = getPolygonBoundingBox(oFeature);
			g_oMap.fitBounds(g_oCurBounds, {'padding': 50});
			g_oMap.setFeatureState({'id': nIndex, 'source': 'network-polygons'}, {'hidden': false});
		}
		else
			g_oMap.setFeatureState({'id': nIndex, 'source': 'network-polygons'}, {'hidden': true});
	}
	
	
	g_oMap.setFeatureState({'id': oFeature.id, 'source': 'network-polygons'}, {'hidden': false});
}


function selectSuccess(data, textStatus, jqXHR)
{
	g_oWays = {};
	g_oHashes = {};
	g_sCurHash = null;
	
	removeSource('network-polygons', g_oMap);
	removeSource('geo-lines', g_oMap);

	let oLineData = {'type':'FeatureCollection', 'features': []};
	let oHighway = {};
	let nBridges = 0;
	let nRamps = 0;
	let nConnectors = 0;
	let nNotSet = 0;
	for (let oFeature of data.values())
	{
		g_oWays[oFeature.properties.imrcpid] = oFeature;
		if (oFeature.properties.bridge)
			++nBridges;
		if (oHighway[oFeature.properties.type])
			++oHighway[oFeature.properties.type];
		else
			oHighway[oFeature.properties.type] = 1;
		oFeature.properties.include = true;
		oFeature.properties.hidden = false;
		for (let [sKey, sVal] of Object.entries(oFeature.properties.tags))
			oFeature.properties[sKey] = sVal;
		oFeature.properties.tags = undefined;
		if (oFeature.properties.linktype)
		{
			if (oFeature.properties.linktype === 'ramp')
				++nRamps;
			else
				++nConnectors;
		}
		else if (!oFeature.properties.ingress)
			++nNotSet;
			
		oLineData.features.push(oFeature);
	}
	g_oMap.addSource('geo-lines', {'type': 'geojson', 'data': oLineData, 'generateId': true});
	if (g_oMap.getLayer('road-number-shield') !== undefined)
		g_oMap.addLayer(g_oLayers['geo-lines'], 'road-number-shield');
	else
		g_oMap.addLayer(g_oLayers['geo-lines']);
	g_oMap.on('mouseenter', 'geo-lines', hoverHighlight);
	g_oMap.on('mouseleave', 'geo-lines', unHighlight);
	let sOutput = `total segments: ${oLineData.features.length}`;
	let aTypes = [];
	for (let [sKey, sVal] of Object.entries(oHighway))
		aTypes.push([sKey, sVal]);
	
	aTypes.sort((a, b) => a[0].localeCompare(b[0]));
	for (let aType of aTypes.values())
		sOutput += `<br>${aType[0]}: ${aType[1]}`;
	sOutput += `<br>bridges: ${nBridges}`;
	sOutput += `<br>ramps: ${nRamps}`;
	sOutput += `<br>connectors: ${nConnectors}`;
	sOutput += `<br>notset: ${nNotSet}`;
	let oMetadata = $(g_oDialogs['networkmetadata']);
	oMetadata.dialog('option', 'title', this.networklabel);
	oMetadata.html(sOutput);
	oMetadata.dialog('open');
	$("button[title|='Toggle Metadata']").show();
	$(g_oDialogs['networklegend']).dialog('close');
	$(g_oDialogs['roadlegend']).dialog('open');
	document.activeElement.blur();
	g_sCurNetwork = this.networkid;
		
	g_nEdits = 0;
	g_nEditMode = 0;
	$('#main-control').hide();
	$('#edit-control button').prop('disabled', false);
	$('#edit-control').show();
	g_oMap.on('sourcedata', 'geo-lines', geoLinesLoaded);
}


function geoLinesLoaded(oEvent)
{
	if (oEvent.isSourceLoaded)
	{
		g_sCursor = '';
		g_oMap.getCanvas().style.cursor = g_sCursor;
		g_oMap.off('sourcedata', 'geo-lines', geoLinesLoaded);
		g_oPopup.remove();
	}
}

function mousemovePopupPos({target, lngLat, point})
{
	g_oPopup.setLngLat(lngLat);
}

function mousemoveNetworkPolygons(oEvent)
{
	let oTemp = g_oNetworkHover;
	g_oNetworkHover = {};
	let oFeatures = g_oMap.queryRenderedFeatures([[oEvent.point.x - 2, oEvent.point.y + 2], [oEvent.point.x + 2, oEvent.point.y - 2]], {'layers': ['network-polygons']});
	let oTempFeatures = {};
	for (let oFeature of oFeatures.values())
	{
		let sImrcpId = g_oMap.getSource(oFeature.source)._data.features[oFeature.id].properties.networkid;
		if (!Object.hasOwn(oTempFeatures, sImrcpId))
			oTempFeatures[sImrcpId] = oFeature;
	}
	
	oFeatures = Object.values(oTempFeatures);
	if (Object.keys(oTemp).length === 0 && oFeatures.length > 0)
	{
		if (oFeatures.length === 1)
		{
			let oFeature = g_oMap.getSource(oFeatures[0].source)._data.features[oFeatures[0].id];
			let sHtml = `<p>${oFeature.properties.label}</p>`;
			if (oFeature.properties.msg)
			{
				sHtml += '<br>';
				sHtml += oFeature.properties.msg;
			}

			g_oPopup.setLngLat(oEvent.lngLat).setHTML(sHtml).addTo(g_oMap);
		}
		else
			g_oPopup.setLngLat(oEvent.lngLat).setHTML('<p>Left-click<br>to select</p>').addTo(g_oMap);
		g_oMap.getCanvas().style.cursor = 'pointer';
		g_oMap.on('mousemove', mousemovePopupPos);
	}
	else if (oFeatures.length > 0)
	{
		if (oFeatures.length === 1)
		{
			let oFeature = g_oMap.getSource(oFeatures[0].source)._data.features[oFeatures[0].id];
			let sHtml = `<p>${oFeature.properties.label}</p>`;
			if (oFeature.properties.msg)
			{
				sHtml += '<br>';
				sHtml += oFeature.properties.msg;
			}

			g_oPopup.setLngLat(oEvent.lngLat).setHTML(sHtml).addTo(g_oMap);
		}
		else
			g_oPopup.setHTML('<p>Left-click<br>to select</p>');
	}
	
	for (let oFeature of oFeatures.values())
	{
		g_oNetworkHover[oFeature.id] = oFeature;
		if (oTemp[oFeature.id] !== undefined)
		{
			g_oMap.setFeatureState(oFeature, {hover: true});
		}
	}
	
	for (let sId of Object.keys(oTemp))
	{
		if (g_oNetworkHover[sId] === undefined)
		{
			let oFeature = oTemp[sId];
			if (oFeature !== undefined)
				g_oMap.setFeatureState(oFeature, {hover: false});
		}
	}
	if (Object.keys(g_oNetworkHover).length === 0)
	{
		g_oMap.off('mousemove', mousemovePopupPos);
		g_oPopup.remove();
		g_oMap.getCanvas().style.cursor = g_sCursor;
	}
}

/*
function mouseenterNetworkPolygons(oEvent)
{
	let nHover = g_nHoverId;
	let nFeatureId = getFeatureId(oEvent);
	let oFeature = g_oMap.getSource('network-polygons')._data.features[nFeatureId];
	g_oPopup.setLngLat(oEvent.lngLat).setHTML(`<p>${oFeature.properties.label}<br>${oFeature.properties.segments}</p>`).addTo(g_oMap);
	g_oMap.on('mousemove', mousemovePopupPos);
	
	if (nHover >= 0)
		g_oMap.setFeatureState({source: 'network-polygons', id: nHover}, {hover: false});
	if (nFeatureId >= 0)
	{
		g_nHoverId = nFeatureId;
		g_oMap.setFeatureState({source: 'network-polygons', id: nFeatureId}, {hover: true});
	}
	g_oMap.on('mousemove', 'network-polygons', mousemoveNetworkPolygons);
}

function mousemoveNetworkPolygons(oEvent)
{
	let nHover = g_nHoverId;
	if (oEvent.features.length > 0)
		g_oMap.getCanvas().style.cursor = 'pointer';
	
	let nFeatureId = getFeatureId(oEvent);
	
	if (nFeatureId != nHover)
	{
		let oFeature = g_oMap.getSource('network-polygons')._data.features[nFeatureId];
		g_oPopup.setHTML(`<p>${oFeature.properties.label}<br>${oFeature.properties.segments}</p>`);
		if (nHover >= 0)
			g_oMap.setFeatureState({source: 'network-polygons', id: nHover}, {hover: false});
		if (nFeatureId >= 0)
		{
			g_nHoverId = nFeatureId;
			g_oMap.setFeatureState({source: 'network-polygons', id: nFeatureId}, {hover: true});
		}
	}
}


function mouseleaveNetworkPolygons(oEvent)
{
	let nHover = g_nHoverId;
	g_nHoverId = undefined;
	oEvent.target.getCanvas().style.cursor = g_sCursor;
	if (nHover >= 0)
		oEvent.target.setFeatureState({source: 'network-polygons', id: nHover}, {hover: false});
	
	oEvent.target.off('mousemove', 'network-polygons', mousemoveNetworkPolygons);
	oEvent.target.off('mousemove', mousemovePopupPos);
	g_oPopup.remove();
}
*/

function startDeleteNetwork()
{
	g_oMap.off('click', clickNetwork);
	/*
	g_oMap.off('mouseenter', 'network-polygons', mouseenterNetworkPolygons);
	g_oMap.off('mouseleave', 'network-polygons', mouseleaveNetworkPolygons);
	*/
	g_oMap.on('click', 'network-polygons', deleteNetwork);
	g_oMap.on('mouseenter', 'network-polygons', mouseenterNetworkPolygonsDel);
	g_oMap.on('mouseleave', 'network-polygons', mouseleaveNetworkPolygonsDel);
	$(document).on('keyup', turnOffDelete);
	instructions(g_oInstructions['delete']);
}

function mouseenterNetworkPolygonsReprocess(oEvent)
{
	let nHover = g_nHoverId;
	let nFeatureId = getFeatureId(oEvent);
		
	if (nHover >= 0)
		g_oMap.setFeatureState({source: 'network-polygons', id: nHover}, {'hover': false});
	if (nFeatureId >= 0)
	{
		g_nHoverId = nFeatureId;
		g_oMap.setFeatureState({source: 'network-polygons', id: nFeatureId}, {'hover': true});
	}
	g_oMap.on('mousemove', 'network-polygons', mousemoveNetworkPolygonsReprocess);
}

function mousemoveNetworkPolygonsReprocess(oEvent)
{
	let nHover = g_nHoverId;
	if (oEvent.features.length > 0)
		g_oMap.getCanvas().style.cursor = 'pointer';
	
	let nFeatureId = getFeatureId(oEvent);
	
	if (nFeatureId != nHover)
	{
		if (nHover >= 0)
			g_oMap.setFeatureState({source: 'network-polygons', id: nHover}, {'hover': false});
		if (nFeatureId >= 0)
		{
			g_nHoverId = nFeatureId;
			g_oMap.setFeatureState({source: 'network-polygons', id: nFeatureId}, {'hover': true});
		}
	}
}


function mouseleaveNetworkPolygonsReprocess(oEvent)
{
	let nHover = g_nHoverId;
	oEvent.target.getCanvas().style.cursor = g_sCursor;
	if (nHover >= 0)
		oEvent.target.setFeatureState({source: 'network-polygons', id: nHover}, {'hover': false});
	
	g_nHoverId = undefined;
	oEvent.target.off('mousemove', 'network-polygons', mousemoveNetworkPolygonsReprocess);
}


function startReprocessNetwork()
{
	g_oMap.off('click', clickNetwork);
	g_oMap.on('click', 'network-polygons', reprocessNetwork);
	g_oMap.on('mouseenter', 'network-polygons', mouseenterNetworkPolygonsReprocess);
	g_oMap.on('mouseleave', 'network-polygons', mouseleaveNetworkPolygonsReprocess);
	$(document).on('keyup', turnOffReprocess);
	instructions(g_oInstructions['reprocess']);
}


function turnOffReprocess(oEvent)
{
	if (oEvent.which == 27) // esc
	{
		cancelReprocess();
	}
}


function cancelReprocess()
{
	$(g_oDialogs['reprocess']).dialog('close');
	$(g_oDialogs['network']).dialog('close');
	g_oMap.off('click', 'network-polygons', reprocessNetwork);
	g_oMap.off('mouseenter', 'network-polygons', mouseenterNetworkPolygonsReprocess);
	g_oMap.off('mouseleave', 'network-polygons', mouseleaveNetworkPolygonsReprocess);
	g_oMap.off('mousemove', 'network-polygons', mousemoveNetworkPolygonsReprocess);
	$(document).off('keyup', turnOffReprocess);
	instructions(g_oInstructions['select'], 'Exited reprocess mode');
	g_oReprocess = undefined;
	switchToMain();
}


function confirmReprocess()
{
	let sData = '';
	for (let nIndex = 0; nIndex < g_oReprocess.geometry.coordinates[0].length - 1; nIndex++)
	{
		let aCoord = g_oReprocess.geometry.coordinates[0][nIndex];
		sData += aCoord[0].toFixed(7) + ',' + aCoord[1].toFixed(7) + ',';
	}
	
	$('#network-label').val(g_oReprocess.properties.label);
	let oDialog = $(g_oDialogs['network']);
	g_sNetworkCoords = sData.substring(0, sData.length - 1);
	$(g_oDialogs['reprocess']).dialog('close');
	g_oMap.off('click', 'network-polygons', reprocessNetwork);
	$(document).off('keyup', turnOffReprocess);
	oDialog.dialog('open');
}


function reprocessNetwork(oEvent)
{
	let nHover = g_nHoverId;
	let oFeatures = oEvent.features;
	if (oFeatures.length === 0)
		return;
	
	let oFeature = null;
	for (let nIndex = 0; nIndex < oFeatures.length; nIndex++)
	{
		if (oFeatures[nIndex].id == nHover)
			oFeature = oFeatures[nIndex];
	}
	
	if (oFeature == null)
		return;
	g_oReprocess = g_oMap.getSource('network-polygons')._data.features[nHover];
	$(g_oDialogs['reprocess']).dialog('open');
}


function turnOffDelete(oEvent)
{
	let nHover = g_nHoverId;
	if (oEvent === undefined || oEvent.which == 27) // esc
	{
		g_oMap.off('click', 'network-polygons', deleteNetwork);
		g_oMap.off('mouseenter', 'network-polygons', mouseenterNetworkPolygonsDel);
		g_oMap.off('mousemove', 'network-polygons', mousemoveNetworkPolygonsDel);
		g_oMap.off('mouseleave', 'network-polygons', mouseleaveNetworkPolygonsDel);
		if (nHover >= 0)
		{
			g_oMap.setFeatureState({source: 'network-polygons', id: nHover}, {'delete': false});
		}
		$(document).off('keyup', turnOffDelete);
		instructions(g_oInstructions['select'], 'Exited delete mode');
		startSelectNetwork(true);
	}
}


function confirmDeleteNetwork()
{
	$.ajax(
	{
		'url': 'api/generatenetwork/delete',
		'method': 'POST',
		'networkid': g_oDelete.properties.networkid,
		'data': {'token': sessionStorage.token, 'networkid': g_oDelete.properties.networkid}
	}).done(function() 
	{
		g_sCursor = '';
		g_oMap.getCanvas().style.cursor = g_sCursor;
		instructions(g_oInstructions['select'], 'Delete Success');;
		switchToMain();
	}).fail(function() 
	{
		$('#instructions-error').html('Failed to delete network.');
		g_oMap.getCanvas().style.cursor = g_sCursor;
	});
	instructions('', 'Deleting Network');
	g_sCursor = 'wait';
	g_oMap.getCanvas().style.cursor = g_sCursor;
	g_oMap.off('click', 'network-polygons', deleteNetwork);
	g_oMap.off('mouseenter', 'network-polygons', mouseenterNetworkPolygonsDel);
	g_oMap.off('mousemove', 'network-polygons', mousemoveNetworkPolygonsDel);
	g_oMap.off('mouseleave', 'network-polygons', mouseleaveNetworkPolygonsDel);
	$(document).off('keyup', turnOffDelete);
	g_nHoverId = undefined;
}


function deleteNetwork(oEvent)
{
	let nHover = g_nHoverId;
	let oFeatures = oEvent.features;
	if (oFeatures.length === 0)
		return;
	
	let oFeature = null;
	for (let nIndex = 0; nIndex < oFeatures.length; nIndex++)
	{
		if (oFeatures[nIndex].id == nHover)
			oFeature = oFeatures[nIndex];
	}
	
	if (oFeature == null)
		return;
	
	g_oDelete = oFeature;
	$(g_oDialogs['deleteconfirm']).dialog('open');
}


function mouseenterNetworkPolygonsDel(oEvent)
{
	let nHover = g_nHoverId;
	let nFeatureId = getFeatureId(oEvent);
		
	if (nHover >= 0)
		g_oMap.setFeatureState({source: 'network-polygons', id: nHover}, {'delete': false});
	if (nFeatureId >= 0)
	{
		g_nHoverId = nFeatureId;
		g_oMap.setFeatureState({source: 'network-polygons', id: nFeatureId}, {'delete': true});
	}
	g_oMap.on('mousemove', 'network-polygons', mousemoveNetworkPolygonsDel);
}

function mousemoveNetworkPolygonsDel(oEvent)
{
	let nHover = g_nHoverId;
	if (oEvent.features.length > 0)
		g_oMap.getCanvas().style.cursor = 'pointer';
	
	let nFeatureId = getFeatureId(oEvent);
	
	if (nFeatureId != nHover)
	{
		if (nHover >= 0)
			g_oMap.setFeatureState({source: 'network-polygons', id: nHover}, {'delete': false});
		if (nFeatureId >= 0)
		{
			g_nHoverId = nFeatureId;
			g_oMap.setFeatureState({source: 'network-polygons', id: nFeatureId}, {'delete': true});
		}
	}
}


function mouseleaveNetworkPolygonsDel(oEvent)
{
	let nHover = g_nHoverId;
	oEvent.target.getCanvas().style.cursor = g_sCursor;
	if (nHover >= 0)
		oEvent.target.setFeatureState({source: 'network-polygons', id: nHover}, {'delete': false});
	
	g_nHoverId = undefined;
	oEvent.target.off('mousemove', 'network-polygons', mousemoveNetworkPolygonsDel);
}


function startCreateNetwork()
{
	g_oMap.off('click', clickNetwork);
	g_oMap.off('mousemove', mousemoveNetworkPolygons);
	let oSrc = g_oMap.getSource('network-polygons');
	let oData = oSrc._data;
	for (let nIndex = 0; nIndex < oData.features.length; nIndex++)
	{
		g_oMap.setFeatureState({'id': nIndex, 'source': 'network-polygons'}, {'hidden': true});
	}
	/*
	g_oMap.off('mouseenter', 'network-polygons', mouseenterNetworkPolygons);
	g_oMap.off('mouseleave', 'network-polygons', mouseleaveNetworkPolygons);
	g_oMap.off('mousemove', 'network-polygons', mousemoveNetworkPolygons);
	 */
	instructions(['Left-click: Initial point<br>Esc: Cancel', 'Create Network']);
}


function submitNetwork()
{
	let sOptions = '';
	let oDialog = $(g_oDialogs['network']);
	if (g_oReprocess === undefined)
	{
		$('#dlgNetwork :checked').each(function(){sOptions += this.name + ','});
		$.ajax(
		{
			'url': 'api/generatenetwork/create', 
			'method': 'POST',
			'data': {'token': sessionStorage.token, 'coords': g_sNetworkCoords, 'options': sOptions.substring(0, sOptions.length - 1), 'label': $('#network-label').val()}
		}).done(function() 
		{
			removeSource('poly-bounds', g_oMap);
			removeSource('poly-outline', g_oMap);
			g_sCursor = '';
			g_oMap.getCanvas().style.cursor = g_sCursor;
			instructions(g_oInstructions['select'], 'Successful network creation request.');
			startSelectNetwork();
		}).fail(function() 
		{
			$('#instructions-error').html('Request failed. Try again later');
		});

		g_sCursor = 'wait';
		g_oMap.getCanvas().style.cursor = g_sCursor;
		$('#network-label').val('');
		$('#dlgNetwork :checked').each(function(){$(this).prop('checked', false);});
		oDialog.dialog('close');
	}
	else
	{
		$('#dlgNetwork :checked').each(function(){sOptions += this.name + ',';});
		$.ajax(
		{
			'url': 'api/generatenetwork/reprocess', 
			'method': 'POST',
			'data': {'token': sessionStorage.token, 'coords': g_sNetworkCoords, 'networkid': g_oReprocess.properties.networkid, 'filter': sOptions.substring(0, sOptions.length - 1), 'label': $('#network-label').val()}
		}).done(function() 
		{
			g_sCursor = '';
			g_oMap.getCanvas().style.cursor = g_sCursor;
			instructions(g_oInstructions['select'], 'Successful network reprocess request.');
			startSelectNetwork();
		}).fail(function() 
		{
			$('#instructions-error').html('Request failed. Try again later');
		});

		g_oReprocess = undefined;
		g_sCursor = 'wait';
		g_oMap.getCanvas().style.cursor = g_sCursor;
		$('#network-label').val('');
		$('#dlgNetwork :checked').each(function(){$(this).prop('checked', false);});
		oDialog.dialog('close');
	}
}


function cancelNetwork()
{
	if (g_oReprocess === undefined)
	{
		$('#network-label').val('');
		$('#btnCancelNetwork').button('disable');
		$('#enable-cancel').prop('checked', false);
		$('#dlgNetwork :checked').each(function(){$(this).prop('checked', false);});
		let oDialog = $(g_oDialogs['network']);
		oDialog.dialog('close');
		instructions(g_oInstructions['select'], 'Network creation canceled');
		removeSource('poly-bounds', g_oMap);
		removeSource('poly-outline', g_oMap);
		startSelectNetwork(true);
	}
	else
	{
		g_oReprocess = undefined;
		$('#network-label').val('');
		$('#btnCancelNetwork').button('disable');
		$('#enable-cancel').prop('checked', false);
		$('#dlgNetwork :checked').each(function(){$(this).prop('checked', false);});
		let oDialog = $(g_oDialogs['network']);
		oDialog.dialog('close');
		instructions(g_oInstructions['select'], 'Network reprocess canceled');
		startSelectNetwork(true);
	}
}


function initCreateNetwork()
{
	instructions(['Left-click: Add point<br>Esc: Cancel', 'Create Network']);
}


function moveCreateNetwork(oEvent)
{
	if (!this.bCanAdd)
		$('#instructions-error').html('Polygon cannot self intersect');
	else
		$('#instructions-error').html('');
}


function addPointCreateNetwork(oEvent)
{
	if (this.map.getSource('poly-outline')._data.geometry.coordinates.length  > 2)
		instructions(['Left-click: Add point<br>Left-click initial point: Finish polygon<br>Esc: Cancel', 'Create Network']);
}


function finishCreateNetwork(oGeometry)
{
	let sData = '';
	for (let nIndex = 0; nIndex < oGeometry.coordinates.length - 1; nIndex++)
	{
		let aCoord = oGeometry.coordinates[nIndex];
		sData += aCoord[0].toFixed(7) + ',' + aCoord[1].toFixed(7) + ',';
	}

	let oDialog = $(g_oDialogs['network']);
	g_sNetworkCoords = sData.substring(0, sData.length - 1);
	oDialog.dialog("open");
}


function cancelCreateNetwork()
{
	instructions(g_oInstructions['select'], 'Network creation canceled');
	startSelectNetwork(true);
}

function switchToMain()
{
	$('#main-control').show();
	$('#edit-control').hide();
	let oCancel = $(g_oDialogs['cancel']);
	if (oCancel.dialog('isOpen'))
		oCancel.dialog('close');
	let oRoadLegend = $(g_oDialogs['roadlegend']);
	if (oRoadLegend.dialog('isOpen'))
		oRoadLegend.dialog('close');
	$(g_oDialogs['networklegend']).dialog('open');
	document.activeElement.blur();
	turnOffAddRemove();
	turnOffMerge();
	turnOffSplit();
	
	g_oMap.off('mouseenter', 'geo-lines', hoverHighlight);
	g_oMap.off('mousemove', 'geo-lines', updateHighlight);
	g_oMap.off('mouseleave', 'geo-lines', unHighlight);
	removeSource('geo-lines', g_oMap);
	instructions(g_oInstructions['select']);
	startSelectNetwork();
}


function confirmCancel()
{
	if (g_nEdits > 0)
	{
		$(g_oDialogs['cancel']).dialog('open');
		document.activeElement.blur();
	}
	else
		switchToMain();
}

function exportOsm()
{
	$('#pageoverlay').show();
	$.ajax(
	{
		'url': 'api/generatenetwork/osm',
		'method': 'POST',
		'data': {'networkid': g_sCurNetwork, 'token': sessionStorage.token, 'label': $(g_oDialogs['networkmetadata']).dialog('option', 'title')}
	}).done(doneExportOsm).fail(function(){alert('Failed to export OSM. Try again later.');}).always(function(){$('#pageoverlay').hide();});
}

function doneExportOsm(data)
{
	window.location = data;
}


function publishNetwork()
{
	if (isStatus(g_oClickedNetworks.properties.status, PUBLISHED))
		$('#publishreplace').show();
	else
		$('#publishreplace').hide();
	$(g_oDialogs['publishconfirm']).dialog('open');
	document.activeElement.blur();
}


function confirmPublishNetwork()
{
	$.ajax(
	{
		'url': 'api/generatenetwork/publish',
		'method': 'POST',
		'data': {'networkid': g_sCurNetwork, 'token': sessionStorage.token, 'trafficmodel': $('#chkTrafficModel').is(':checked'), 'externalpublish': $('#chkExternalSharing').is(':checked'), 'roadwxmodel': $('#chkRoadWeatherModel').is(':checked') }
	}).done(function() 
	{
		g_sCursor = '';
		g_oMap.getCanvas().style.cursor = g_sCursor;
		instructions(g_oInstructions['select'], 'Network successfully queued to publish');;
		switchToMain();
	}).fail(function() 
	{
		$('#instructions-error').html('Failed to queue network to publish.');
		g_oMap.getCanvas().style.cursor = g_sCursor;
	});
}


function keyupExitAddRemove(oEvent)
{
	if (oEvent.which == 27) // esc
	{
		instructions(g_oInstructions['edit'], 'Exited add/remove mode.');
		g_nEditMode = 0;
		turnOffAddRemove();
	}
}

function startAddRemove()
{
	if (g_nEditMode == 0)
		instructions(g_oInstructions['add']);
	else if (g_nEditMode == 1)
	{
		instructions(g_oInstructions['edit'], 'Exited add/remove mode');
		turnOffAddRemove();
		g_nEditMode = 0;
		return;
	}
	else if (g_nEditMode == 2)
	{
		instructions(g_oInstructions['add'], 'Switched from merge to add/remove');
		turnOffMerge();
	}
	else if (g_nEditMode == 3)
	{
		instructions(g_oInstructions['add'], 'Switched from split to add/remove');
		turnOffSplit();
	}
	g_nEditMode = 1;
	instructions(g_oInstructions['add']);
	$(document).on('keyup', keyupExitAddRemove);
	g_oMap.on('mousemove', getHash);
	g_oMap.on('click', 'geo-lines', toggleInclude);
	g_oMap.on('mousemove', spotlight);
}

function turnOffAddRemove()
{
	g_oMap.off('mousemove', getHash);
	g_oMap.off('mousemove', spotlight);
	g_oMap.off('click', 'geo-lines', toggleInclude);
	let oLineSrc = g_oMap.getSource('geo-lines');
	if (oLineSrc !== undefined)
	{
		let oData = oLineSrc._data;
		while (g_oSpotlight.length > 0)
		{
			let nId = g_oSpotlight.pop();
			if (!oData.features[nId].properties.include)
				oData.features[nId].properties.hidden = true;
		}
		oLineSrc.setData(oData);
	}
	$(document).off('keyup', keyupExitAddRemove);
}


function startSplit()
{
	if (g_nEditMode == 0)
		instructions(g_oInstructions['split']);
	else if (g_nEditMode == 1)
	{
		turnOffAddRemove();
		instructions(g_oInstructions['split'], 'Switched from add/remove to split');
	}
	else if (g_nEditMode == 2)
	{
		turnOffMerge();
		instructions(g_oInstructions['split'], 'Switched from merge to split');
	}
	else if (g_nEditMode == 3)
	{
		instructions(g_oInstructions['edit'], 'Exited split mode');
		turnOffSplit();
		g_nEditMode = 0;
		return;
	}
	g_nEditMode = 3;

	g_oMap.on('click', 'geo-lines', addToSplit);
	g_oMap.on('click', 'split-points', selectSplitPoint);
	g_oMap.on('mouseenter', 'split-points', hoverHighlightPoint);
	g_oMap.on('mouseleave', 'split-points', unHighlightPoint);
	$(document).on('keyup', null, {'map': g_oMap}, splitFinish);
	g_oMap.addSource('split-points', {'type': 'geojson', 'data': {'type': 'FeatureCollection', 'features': []}});
	g_oMap.addLayer(g_oLayers['selected-point']);
	g_oMap.addLayer(g_oLayers['split-points']);
}


function addToSplit(oEvent)
{
	if (oEvent.features.length > 0)
	{
		let oSrc = g_oMap.getSource('split-points');
		let oData = oSrc._data;
		if (g_nSplitWay >= 0)
		{
			g_oMap.setFeatureState({source: 'geo-lines', id: g_nSplitWay}, {hover: false});
			while (oData.features.length > 0)
				oData.features.pop();
		}
		g_nSplitWay = oEvent.features[0].id;
		g_oMap.setFeatureState({source: 'geo-lines', id: g_nSplitWay}, {hover: true});
		let oLineSrc = g_oMap.getSource('geo-lines');
		let oLineData = oLineSrc._data;
		let nCount = 0;
		for (let oCoord of oLineData.features[g_nSplitWay].geometry.coordinates.values())
		{
			oData.features.push({'type': 'Feature', 'id': nCount++, 'properties': {'imrcpid': oLineData.features[g_nSplitWay].properties.imrcpid}, 'geometry': {'type': 'Point', 'coordinates': oCoord}});
		}
		oSrc.setData(oData);
		instructions(['Left-click node to split road<br>Esc: Cancel', 'Split Road'], '', '');
	}
}


function selectSplitPoint(oEvent)
{
	if (oEvent.features.length > 0)
	{
		let oSrc = g_oMap.getSource('split-points');
		let oData = oSrc._data;
		if (g_nSplitNode >= 0)
		{
			oData.features[g_nSplitNode].properties.selected = false;
		}
		g_nSplitNode = oEvent.features[0].id;
		oData.features[g_nSplitNode].properties.selected = true;
		oSrc.setData(oData);
		instructions(['Left-click: Select a different road or node<br>Enter: Commit split<br>Esc: Cancel', 'Split Road']);
	}
}


function turnOffSplit()
{
	g_oMap.off('click', 'geo-lines', addToSplit);
	g_oMap.off('click', 'split-points', selectSplitPoint);
	g_oMap.off('mouseenter', 'split-points', hoverHighlightPoint);
	g_oMap.off('mouseleave', 'split-points', unHighlightPoint);
	g_oMap.off('mousemove', 'split-points', updateHighlightPoint);
	$(document).off('keyup', splitFinish);
	removeSource('split-points', g_oMap);
	if (g_nSplitWay >= 0)
		g_oMap.setFeatureState({source: 'geo-lines', id: g_nSplitWay}, {hover: false});
	g_nSplitWay = undefined;
	g_nSplitNode = undefined;
}

function splitFinish(oEvent)
{
	if (oEvent.which == 27) // esc
	{
		instructions(g_oInstructions['edit'], 'Canceled split');
		g_nEditMode = 0;
		turnOffSplit(oEvent);
	}
	if (oEvent.which == 13) // enter
	{
		if (g_nSplitWay === undefined || g_nSplitNode === undefined)
		{
			return;
		}
		
		let sWayImrcp = g_oMap.getSource('geo-lines')._data.features[g_nSplitWay].properties.imrcpid;
		let sNodeImrcp = g_oMap.getSource('split-points')._data.features[g_nSplitNode].properties.imrcpid;
		$.ajax(
		{
			'url': 'api/generatenetwork/split',
			'method': 'POST',
			'node': g_nSplitNode,
			'way': g_nSplitWay,
			'data': {'networkid': g_sCurNetwork, 'token': sessionStorage.token, 'way': sWayImrcp, 'node': g_nSplitNode.toString()}
		}).done(splitSuccess).fail(function()
		{
			instructions(g_oInstructions['edit'], '', 'Split failed');;
		});
		
		g_nEditMode = 0;
		turnOffSplit();
	}
}


function splitSuccess(data, status, jqXHR)
{
	g_nEdits++;
	let oSrc = g_oMap.getSource('geo-lines');
	let oData = oSrc._data;
	for (let oWay of data.values())
	{
		g_oWays[oWay.properties.imrcpid] = oWay;
		oWay.properties.include = true;
		oWay.properties.hidden = false;
		oData.features.push(oWay);
	}

	oData.features[this.way].geometry.coordinates = [];
	oSrc.setData(oData);
	instructions(g_oInstructions['edit'], 'Split succeeded');
}


function startMerge()
{
	if (g_nEditMode == 0)
		instructions(g_oInstructions['merge']);
	else if (g_nEditMode == 1)
	{
		turnOffAddRemove();
		instructions(g_oInstructions['merge'], 'Switched from add/remove to merge');
	}
	else if (g_nEditMode == 2)
	{
		instructions(g_oInstructions['edit'], 'Exited merge mode');
		turnOffMerge();
		g_nEditMode = 0;
		return;
	}
	else if (g_nEditMode == 3)
	{
		instructions(g_oInstructions['merge'], 'Switched from split to merge');
		turnOffSplit();
	}
	g_nEditMode = 2;
	g_oMerge = [];
	g_oMap.on('click', 'geo-lines', addToMerge);
	$(document).on('keyup', mergeFinish);
}


function addToMerge(oEvent)
{
	if (oEvent.features.length > 0)
	{
		if (oEvent.features[0].properties.hidden)
			return;
		let nNewId = oEvent.features[0].id;
		for (let nIndex = 0; nIndex < g_oMerge.length; nIndex++)
		{
			if (g_oMerge[nIndex] == nNewId)
			{
				g_oMap.setFeatureState({source: 'geo-lines', id: nNewId}, {hover: false});
				g_oMerge.splice(nIndex, 1);
				return;
			}
		}
		g_oMerge.push(nNewId);
		g_oMap.setFeatureState({source: 'geo-lines', id: nNewId}, {hover: true});
		while (g_oMerge.length > 2)
		{
			g_oMap.setFeatureState({source: 'geo-lines', id: g_oMerge[0]}, {hover: false});
			g_oMerge.splice(0, 1);
		}
	}
}


function turnOffMerge()
{
	g_oMap.off('click', 'geo-lines', addToMerge);
	while (g_oMerge.length > 0)
	{
		let nId = g_oMerge.pop();
		g_oMap.setFeatureState({source: 'geo-lines', id: nId}, {hover: false});
	}
	$(document).off('keyup', mergeFinish);
}

function mergeFinish(oEvent)
{
	if (oEvent.which == 27) // esc or enter
	{
		instructions(g_oInstructions['edit'], 'Canceled merge');
		g_nEditMode = 0;
		turnOffMerge();
	}
	if (oEvent.which == 13) // enter
	{
		if (g_oMerge.length != 2)
		{
			alert('Must select 2 roads to merge');
			return;
		}
		
		let oLineSrc = g_oMap.getSource('geo-lines');
		let oData = oLineSrc._data;
		let aImrcpIds = [];
		for (let nId of g_oMerge.values())
		{
			aImrcpIds.push(oData.features[nId].properties.imrcpid);
		}
		$.ajax(
		{
			'url': 'api/generatenetwork/merge',
			'method': 'POST',
			'ids': [g_oMerge[0], g_oMerge[1]],
			'data': {'networkid': g_sCurNetwork, 'token': sessionStorage.token, 'id1': aImrcpIds[0], 'id2': aImrcpIds[1]}
		}).done(mergeSuccess).fail(function()
		{
			instructions(g_oInstructions['edit'], '', 'Merge failed');
		});
		g_nEditMode = 0;
		turnOffMerge();
	}
}


function mergeSuccess(data, status, jqXHR)
{
	g_nEdits++;
	g_oWays[data.properties.imrcpid] = data;
	data.properties.include = true;
	data.properties.hidden = false;
	let oSrc = g_oMap.getSource('geo-lines');
	let oData = oSrc._data;
	oData.features.push(data);
	oData.features[this.ids[0]].geometry.coordinates = [];
	oData.features[this.ids[1]].geometry.coordinates = [];
	oSrc.setData(oData);
	instructions(g_oInstructions['edit'], 'Merge succeeded');
}


function toggleInclude(oEvent)
{
	if (oEvent.features.length > 0)
	{
		let oSrc = oEvent.target.getSource('geo-lines');
		let oData = oSrc._data;
		let oFeature = oData.features[oEvent.features[0].id];
		
		if (oFeature.properties.include === undefined)
		{
			$.ajax(
			{
				'method': 'POST',
				'url': 'api/generatenetwork/add',
				'data': {'networkid': g_sCurNetwork, 'id': oFeature.properties.imrcpid, 'token': sessionStorage.token}
			}).done(function() 
			{
				oFeature.properties.include = true;
				oSrc.setData(oData);
				g_nEdits++;
				instructions(g_oInstructions['add'], 'Add succeeded.');
			}).fail(function()
			{
				instructions(g_oInstructions['add'], '', 'Add failed.');
			});
		}
		else
		{
			let sMode = oFeature.properties.include ? 'remove' : 'add';
			$.ajax(
			{
				'method': 'POST',
				'url': 'api/generatenetwork/' + sMode,
				'data': {'networkid': g_sCurNetwork, 'id': oFeature.properties.imrcpid, 'token': sessionStorage.token}
			}).done(function() 
			{
				oFeature.properties.include = !oFeature.properties.include;
				oSrc.setData(oData);
				g_nEdits++;
				instructions(g_oInstructions['add'], sMode.charAt(0).toUpperCase() + sMode.slice(1) + ' succeeded.');
			}).fail(function()
			{
				instructions(g_oInstructions['add'], '', sMode.charAt(0).toUpperCase() + sMode.slice(1) + ' failed.');
			});
		}
	}
}


function saveChanges()
{
	if (g_nEdits > 0)
	{
		if (g_nEditMode == 1)
			turnOffAddRemove();
		else if (g_nEditMode == 2)
			turnOffMerge();
		else if (g_nEditMode == 3)
			turnOffSplit();
		g_nEditMode = 0;
		$('#edit-control button').prop('disabled', true);
		instructions(['', 'Save Changes'], 'Saving...');
		$.ajax(
		{
			'url': 'api/generatenetwork/save',
			'method': 'POST',
			'data': {'networkid': g_sCurNetwork, 'token': sessionStorage.token}
		}).done(function(data, textStatus, jqXHR)
		{
			instructions(g_oInstructions['edit'], 'Saved changes');
			$.ajax(
			{
				'url': 'api/generatenetwork/geo',
				'dataType': 'json',
				'method': 'POST',
				'networkid': g_sCurNetwork,
				'data': {'token': sessionStorage.token, 'networkid': g_sCurNetwork, 'published': 'false'}
			}).done(selectSuccess).fail(function() 
			{
				instructions(g_oInstructions['select'], '', 'Failed to retrieve network')
				g_oMap.getCanvas().style.cursor = g_sCursor;
				$('#edit-control button').prop('disabled', false);
			});
		}).fail(function()
		{
			instructions(g_oInstructions['edit'], '', 'Failed to save changes');
		});
	}
}


function hoverHighlight(oEvent)
{
	let nHover = g_nHoverId;
	if (oEvent.features.length > 0)
	{
		if (oEvent.features[0].properties.hidden)
			return;
		g_oMap.getCanvas().style.cursor = 'pointer';
		g_oPopup.setLngLat(oEvent.lngLat);
		if (nHover >= 0)
		{
			let bMerge = false;
			for (let nId of g_oMerge.values())
			{
				if (nId == nHover)
				{
					bMerge = true;
					break;
				}
			}
			let bSplit = g_nSplitWay != null && g_nSplitWay == nHover;
			if (!bMerge && !bSplit)
				g_oMap.setFeatureState({source: 'geo-lines', id: nHover}, {hover: false});
		}
		g_nHoverId = oEvent.features[0].id;
		g_oMap.on('mousemove', 'geo-lines', updateHighlight);
		g_oMap.setFeatureState({source: 'geo-lines', id: oEvent.features[0].id}, {hover: true});
		if (g_nEditMode === 0)
		{
			let sPopup = '';
			if (oEvent.features[0].properties.linktype)
				sPopup += oEvent.features[0].properties.linktype;
			else if (oEvent.features[0].properties.ingress)
				sPopup += `ingress: ${oEvent.features[0].properties.ingress}<br>egress: ${oEvent.features[0].properties.egress}`;
			else
				sPopup += 'ramp not set';
			g_oPopup.setHTML(sPopup).addTo(g_oMap);
			g_oMap.on('mousemove', mousemovePopupPos);
		}
	}
}


function updateHighlight(oEvent)
{
	let nHover = g_nHoverId;
	if (oEvent.features.length > 0)
	{
		if (oEvent.features[0].properties.hidden)
			return;
		g_oMap.getCanvas().style.cursor = 'pointer';
		if (nHover >= 0)
		{
			let bMerge = false;
			for (let nId of g_oMerge.values())
			{
				if (nId == nHover)
				{
					bMerge = true;
					break;
				}
			}
			let bSplit = g_nSplitWay != null && g_nSplitWay == nHover;
			if (!bMerge && !bSplit)
				g_oMap.setFeatureState({source: 'geo-lines', id: nHover}, {hover: false});
		}
		g_nHoverId = oEvent.features[0].id;
		g_oMap.setFeatureState({source: 'geo-lines', id: oEvent.features[0].id}, {hover: true});
		if (g_nEditMode === 0)
		{
			let sPopup = '';
			if (oEvent.features[0].properties.linktype)
				sPopup += oEvent.features[0].properties.linktype;
			else if (oEvent.features[0].properties.ingress)
				sPopup += `ingress: ${oEvent.features[0].properties.ingress}<br>egress: ${oEvent.features[0].properties.egress}`;
			else
				sPopup += 'ramp not set';
			g_oPopup.setHTML(sPopup);
		}
	}
}


function unHighlight(oEvent)
{
	let nHover = g_nHoverId;
	g_nHoverId = undefined;
	oEvent.target.off('mousemove', 'geo-lines', updateHighlight);
	if (nHover >= 0)
	{
		let bMerge = false;
		for (let nId of g_oMerge.values())
		{
			if (nId == nHover)
			{
				bMerge = true;
				break;
			}
		}
		let bSplit = g_nSplitWay != null && g_nSplitWay == nHover;
		if (!bMerge && !bSplit)
			oEvent.target.setFeatureState({source: 'geo-lines', id: nHover}, {hover: false});
	}
	g_oPopup.remove();
	oEvent.target.off('mousemove', mousemovePopupPos);
	oEvent.target.getCanvas().style.cursor = g_sCursor;
}


function hoverHighlightPoint(oEvent)
{
	let nHover = g_nHoverPointId;
	if (oEvent.features.length > 0)
	{
		let sLayer = oEvent.features[0].layer.id;
		let sSrc = oEvent.features[0].layer.source;
		g_sCurPointHoverLayer = sLayer;
		g_sCurPointHoverSource = sSrc;
		g_nHoverPointId = oEvent.features[0].id;
		g_oMap.on('mousemove', sLayer, updateHighlightPoint);
		g_oMap.getCanvas().style.cursor = 'pointer';
		if (nHover >= 0)
		{
			g_oMap.setFeatureState({source: sSrc, id: nHover}, {hover: false});
		}
		g_oMap.setFeatureState({source: sSrc, id: oEvent.features[0].id}, {hover: true});
	}
}


function updateHighlightPoint(oEvent)
{
	let nHover = g_nHoverPointId;
	if (oEvent.features.length > 0)
	{
		g_nHoverPointId = oEvent.features[0].id;
		let sSrc = oEvent.features[0].layer.source;
		g_sCurPointHoverLayer = oEvent.features[0].layer.id;
		g_sCurPointHoverSource = sSrc;
		g_oMap.getCanvas().style.cursor = 'pointer';
		if (nHover >= 0)
		{
			g_oMap.setFeatureState({source: sSrc, id: nHover}, {hover: false});
		}
		g_oMap.setFeatureState({source: sSrc, id: oEvent.features[0].id}, {hover: true});
	}
}


function unHighlightPoint(oEvent)
{
	let nHover = g_nHoverPointId;
	g_nHoverPointId = undefined;
	oEvent.target.off('mousemove', g_sCurPointHoverLayer, updateHighlightPoint);
	if (nHover >= 0)
	{
		oEvent.target.setFeatureState({source: g_sCurPointHoverSource, id: nHover}, {hover: false});
	}
	
	oEvent.target.getCanvas().style.cursor = g_sCursor;
}


function getHash({target, lngLat, point})
{
	if (target.getZoom() < 12 || g_bLoadingHash)
		return;
	let sHash = hashLonLat(lngLat.lng, lngLat.lat);
	if (sHash !== g_sCurHash)
	{
		g_sCurHash = sHash;
		if (g_oHashes[sHash] === undefined)
		{
			g_bLoadingHash = true;
			g_oHashes[sHash] = {};
			g_sCursor = 'progress';
			target.getCanvas().style.cursor = g_sCursor;
			$.ajax('api/generatenetwork/hash',
			{
				'dataType': 'json',
				'method': 'POST',
				'map': target,
				'hash': sHash,
				'data': {'token': sessionStorage.token, 'networkid': g_sCurNetwork, 'lon': lngLat.lng.toString(), 'lat': lngLat.lat.toString(), 'hash': sHash}
			}).done(hashSuccess).fail(function() {g_bLoadingHash = false; g_sCursor = ''; target.getCanvas().style.cursor = g_sCursor; g_oHashes[sHash] = undefined;});
		}
	}
}


function hashSuccess(data, textStatus, jqXHR)
{
	g_oHashes[this.hash] = 'loaded';
	
	if (data.lines !== undefined)
	{
		let oLineSrc = g_oMap.getSource('geo-lines');
		let oData = oLineSrc._data;
		for (let oFeature of data.lines.values())
		{
			if (g_oWays[oFeature.properties.imrcpid] === undefined)
			{
				g_oWays[oFeature.properties.imrcpid] = oFeature;
				oFeature.properties.include = false;
				oFeature.properties.hidden = true;
				oData.features.push(oFeature);
			}
		}
		oLineSrc.setData(oData)
	}
	
	g_sCursor = '';
	g_oMap.getCanvas().style.cursor = g_sCursor;
	g_bLoadingHash = false;
}

function spotlight(oEvent)
{
	let oLineSrc = g_oMap.getSource('geo-lines');
	let oData = oLineSrc._data;
	while (g_oSpotlight.length > 0)
	{
		let nId = g_oSpotlight.pop();
		if (!oData.features[nId].properties.include)
			oData.features[nId].properties.hidden = true;
	}
	
	if (g_oMap.getZoom() < 12)
	{
		oLineSrc.setData(oData);
		return;
	}
	let oFeatures = g_oMap.queryRenderedFeatures([[oEvent.point.x - 50, oEvent.point.y + 50], [oEvent.point.x + 50, oEvent.point.y - 50]], {'layers': ['geo-lines']});
	
	for (let oFeature of oFeatures.values())
	{
		if (oFeature.properties.include)
			continue;
		
		oData.features[oFeature.id].properties.hidden = false;
		g_oSpotlight.push(oFeature.id);
	}
	
	oLineSrc.setData(oData);
}


function toIntDeg(dVal)
{
	return Math.round((dVal + 0.00000005) * 10000000.0);
}

function hashLonLat(dLon, dLat)
{
	return (getBucket(toIntDeg(dLon)) << 16) | (getBucket(toIntDeg(dLat)) & 0xffff); // 16-bit hashLonLat index by lat/lon
}

function getBucket(nVal)
{
	return Math.floor(Math.floor(nVal, BUCKET_SPACING) / BUCKET_SPACING);
}

function getFeatureId(oEvent)
{
	let nFeatureId = -1;
	if (oEvent.features.length > 0)
	{
		if (oEvent.features.length == 1)
		{
			nFeatureId = oEvent.features[0].id;
		}
		else
		{
			let nMinArea = Number.MAX_VALUE;
			let oData = g_oMap.getSource('network-polygons')._data;
			
			for (let nIndex = 0; nIndex < oEvent.features.length; nIndex++)
			{
				let aBbox = getPolygonBoundingBox(oData.features[oEvent.features[nIndex].id]);
				
				let nArea = (aBbox[1][0] - aBbox[0][0]) * (aBbox[1][1] - aBbox[0][1]);
				if (nArea < nMinArea)
				{
					nMinArea = nArea;
					nFeatureId = oEvent.features[nIndex].id;
				}
			}
			g_oMap.getSource('network-polygons').setData(oData);
		}
	}
	
	return nFeatureId;
}


function instructions(aContent, sStatus, sError)
{
	$('#instructions').html(aContent[0]);
	$(g_oDialogs['instructions']).dialog('option', 'title', aContent[1]);
	if (sStatus)
		$('#instructions-status').html(sStatus);
	else
		$('#instructions-status').html('');
	if (sError)
		$('#instructions-error').html(sError);
	else
		$('#instructions-error').html('');
}

function setCursor(sCursor)
{
	g_sCursor = sCursor;
}


$(document).on('initPage', initialize);

export {g_oMap, g_sCurNetwork, g_oCurBounds, g_oDialogs, g_sCursor, g_oInstructions, instructions, unHighlightPoint,
		hoverHighlightPoint, hoverHighlight, unHighlight, updateHighlight, updateHighlightPoint, submitNetwork, cancelNetwork, toggleDialog,
		turnOffAddRemove, turnOffMerge, turnOffSplit, startSelectNetwork, setCursor, cancelReprocess, confirmReprocess, closeNetworkSelect,
		confirmDeleteNetwork, confirmPublishNetwork, confirmTrainingNetwork, confirmLoadNetwork, turnOffDelete};