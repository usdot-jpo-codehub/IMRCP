import {g_oMap, g_sCurNetwork, g_oCurBounds, g_oDialogs, g_sCursor, g_oInstructions, instructions, unHighlightPoint,
		hoverHighlightPoint, hoverHighlight, unHighlight, updateHighlight, updateHighlightPoint, turnOffAddRemove,
		turnOffMerge, turnOffSplit, setCursor, toggleDialog, startSelectNetwork} from './network.js';
	
import {g_oLayers, removeSource, snapPointToWay} from './map-util.js';

let g_sSnappedWay;
let g_oRevertDet;
let g_bHasFile = false;
let g_nTotal = 0;
let g_nCurDet = -1;
let g_dSnapTol = 0.001;
let g_sSaveWay;

function startDetector()
{
	let oMap = g_oMap;
	oMap.on('mouseleave', 'detectors', unHighlightPoint);
	oMap.on('mouseenter', 'detectors', hoverHighlightPoint);
	oMap.on('click', 'detectors', editDetector);
	instructions(g_oInstructions['det']);
}

function toggleDetectorEdit(oEvent)
{
	let oDialog = $(g_oDialogs['detectoredit']);
	let oMap = g_oMap;
	if (!oEvent.data)
		oEvent.data = {'dialog' : 'detectoredit'};

	if (!oDialog.dialog('isOpen'))
	{
		instructions(g_oInstructions['editdet']);
		oMap.addSource('detector-pt', {'type': 'geojson', 'data': {'type': 'Feature', 'properties': {'include': false}, 'geometry': {'type' : 'Point', 'coordinates': [0,0]}}});
		oMap.addLayer(g_oLayers['detector-pt']);
		if (!oEvent.features)
			nextDetector(oEvent);
		oMap.on('mouseenter', 'geo-lines', hoverHighlight);
		oMap.on('mouseleave', 'geo-lines', unHighlight);
		oMap.on('mouseenter', 'detectors', turnOffSet);
		oMap.on('mouseleave', 'detectors', turnOnSet);
		oMap.on('mousemove', snapPoint);
		oMap.on('click', setDetector);
	}
	else
	{
		exitDetectorEdit(oEvent);
	}
	toggleDialog(oEvent);
}


function exitDetectorEdit()
{
	let oMap = g_oMap;
	oMap.off('mouseenter', 'geo-lines', hoverHighlight);
	oMap.off('mousemove', 'geo-lines', updateHighlight);
	oMap.off('mouseleave', 'geo-lines', unHighlight);
	oMap.off('mouseenter', 'detectors', turnOffSet);
	oMap.off('mouseleave', 'detectors', turnOnSet);
	oMap.off('mousemove', snapPointToWay);
	oMap.off('click', setDetector);
	g_nCurDet = -1;
	let oDetSrc = oMap.getSource('detectors');
	let oDetData = oDetSrc._data;
	let nCid = $('#det-cid').val();
	let nMapDetId = oDetData.features.findIndex(function(oEl){return oEl.properties.cid === nCid});
	oDetData.features[nMapDetId].properties.selected = false;
	oDetSrc.setData(oDetData);
	removeSource('detector-pt', oMap);
	instructions(g_oInstructions['det']);
}

function turnOnSet(oEvent)
{
	oEvent.target.on('click', setDetector);
	oEvent.target.addLayer(g_oLayers['detector-pt']);
}

function turnOffSet(oEvent)
{
	oEvent.target.off('click', setDetector);
	oEvent.target.removeLayer('detector-pt');
}


function snapPoint(oEvent)
{
	if (oEvent.data === undefined)
		oEvent.data = {};
	oEvent.data.layers = ['geo-lines'];
	oEvent.data.pointSrc = 'detector-pt';
	oEvent.data.snapTol = g_dSnapTol;
	g_sSnappedWay = snapPointToWay(oEvent);
}


function setDetector(oEvent)
{
	$('#det-status').html('');
	let oMap = oEvent.target;
	if (!g_sSnappedWay)
		return;
	$('#detector-revert').button('option', 'disabled', false);
	$('#detector-save').button('option', 'disabled', false);
	instructions(g_oInstructions['detsave']);
	$('#detector-next').button('option', 'disabled', true);
	let sWay = g_sSnappedWay;
	g_sSaveWay = sWay;
	let aCoord = oMap.getSource('detector-pt')._data.geometry.coordinates;
	
	$('#det-lon').val(aCoord[0].toFixed(7));
	$('#det-lat').val(aCoord[1].toFixed(7));
	let oDetSrc = oMap.getSource('detectors');
	let oDetData = oDetSrc._data;
	let nCid = $('#det-cid').val();
	let nMapDetId = oDetData.features.findIndex(function(oEl){return oEl.properties.cid === nCid});
	let oFeature = oDetData.features[nMapDetId];
	
	g_oRevertDet['dirty'] = true;
	oFeature.properties.mappedto = [sWay];
	oFeature.properties.roads = 1;
	oFeature.geometry.coordinates = aCoord;
	oDetSrc.setData(oDetData);
}


function nextDetector(oEvent)
{
	$('#det-status').html('');
	$('#detector-revert').button('option', 'disabled', true);
	$('#detector-save').button('option', 'disabled', true);
	$('#detector-next').button('option', 'disabled', false);
	let oMap = g_oMap;
	let nCid;
	
	if (oEvent.features)
	{
		nCid = oEvent.features[0].properties.cid;
		g_nCurDet = -1;
	}
	let oSrc = oMap.getSource('detectors');
	let oData = oSrc._data;
	let nMapped = 1;
	let bFound = false;
	g_nTotal = 0;
	let nOn = 0;
	for (let nIndex = 0; nIndex < oData.features.length; nIndex++)
	{
		let oFeature = oData.features[nIndex];
		oFeature.properties.selected = false;
		nMapped = oFeature.properties.mappedto.length;
		if (nMapped != 1)
			++g_nTotal
		
		if (!bFound && g_nCurDet < nIndex && (nMapped != 1 || nCid === oFeature.properties.cid))
		{
			if (nCid !== undefined)
			{
				if (oFeature.properties.cid != nCid)
					continue;
				
				if (nMapped == 1)
				{
					nOn = 0;
					g_nCurDet = -1;
				}
				else
				{
					nOn = g_nTotal;
					g_nCurDet = nIndex;
				}
				bFound = true;
			}
			else
			{
				nOn = g_nTotal;
				bFound = true;
				g_nCurDet = nIndex;
			}
			
			oFeature.properties.selected = true;
			$('#det-cid').val(oFeature.properties.cid);
			$('#det-aid').val(oFeature.properties.aid);
			$('#det-lat').val(oFeature.geometry.coordinates[1].toFixed(7));
			$('#det-lon').val(oFeature.geometry.coordinates[0].toFixed(7));
			$('#det-label').val(oFeature.properties.label);
			$('#det-insvc').prop('checked', true);
			let aPrevMappedTo = [];
			for (let sMapped of oFeature.properties.mappedto.values())
				aPrevMappedTo.push(sMapped);
			g_oRevertDet = {'cid': oFeature.properties.cid, 'aid': oFeature.properties.aid, 
						'coord': [oFeature.geometry.coordinates[0], oFeature.geometry.coordinates[1]], 
						'label': oFeature.properties.label, 'dirty' : false,
						'mappedto': aPrevMappedTo};

			oMap.flyTo({center: oFeature.geometry.coordinates, zoom: 16});
		}
	}
	oSrc.setData(oData);
	$('#detector-next').html('Next of ' + g_nTotal);
	if (nOn == g_nTotal) // reset counter at end of list
		g_nCurDet = -1;
	
	$('#detector-next').button('option', 'disabled', g_nTotal == 0);
	if (g_nTotal == 0)
	{
		$('#detector-next').button('option', 'disabled', true);
		$('#detector-next').html('Next');
		instructions(['All detectors have a one to one mapping. Select a specific detector that needs changes.', 'Edit Traffic Detector']);
	}
}


function detChange(oEvent)
{
	let oMap = g_oMap;
	let sField = oEvent.data.field;
	let oDetSrc = oMap.getSource('detectors');
	let oDetData = oDetSrc._data;
	let sVal = $('#det-' + sField).val();
	let nMapDetId = oDetData.features.findIndex(function(oEl){return oEl.properties[sField] === sVal});
	if (nMapDetId >= 0)
	{
		$('#det-status').html('Id already used');
		$('#det-' + sField).val(g_oRevertDet[sField]);
	}
	else
	{
		g_oRevertDet.dirty = true;
		nMapDetId = oDetData.features.findIndex(function(oEl){return oEl.properties[sField] === g_oRevertDet[sField]});
		oDetData.features[nMapDetId].properties[sField] = sVal;
		oDetSrc.setData(oDetData);
		$('#detector-revert').button('option', 'disabled', false);
		$('#detector-save').button('option', 'disabled', false);
		instructions(g_oInstructions['detsave']);
		$('#detector-next').button('option', 'disabled', true);
		$('#det-status').html('');
	}
}


function saveDetector()
{
	let oMap = g_oMap;
	let oData = {};
	$('#det-form').find('input').each(function() {oData[this.name] = $(this).val();});
	oData.token = sessionStorage.token;
	oData.networkid = g_sCurNetwork;
	oData.prevcid = g_oRevertDet.cid;
	oData.way = g_sSaveWay;
	g_sSaveWay = undefined;
	$.ajax(
	{
		'url': 'api/generatenetwork/detsave',
//		'dataType': 'json',
		'method': 'POST',
		'data': oData
	}).done(saveDetectorSuccess).fail(saveDetectorFail); 
}

function revertDetector()
{
	let oMap = g_oMap;
	let nCid = $('#det-cid').val();
	let oDetSrc = oMap.getSource('detectors');
	let oDetData = oDetSrc._data;
	let nMapDetId = oDetData.features.findIndex(function(oEl){return oEl.properties.cid === nCid});
	let oFeature = oDetData.features[nMapDetId];
	$('#det-status').html('');
	$('#detector-revert').button('option', 'disabled', true);
	$('#detector-save').button('option', 'disabled', true);
	instructions(g_oInstructions['editdet']);
	$('#detector-next').button('option', 'disabled', false);
	$('#det-cid').val(g_oRevertDet.cid);
	$('#det-aid').val(g_oRevertDet.aid);
	$('#det-lat').val(g_oRevertDet.coord[1].toFixed(7));
	$('#det-lon').val(g_oRevertDet.coord[0].toFixed(7));
	$('#det-label').val(g_oRevertDet.label);
	g_oRevertDet.dirty = false;
	
	oFeature.properties.mappedto = [];
	for (let sMapping of g_oRevertDet.mappedto.values())
		oFeature.properties.mappedto.push(sMapping);
	oFeature.properties.roads = oFeature.properties.mappedto.length;
	oFeature.geometry.coordinates = [g_oRevertDet.coord[0], g_oRevertDet.coord[1]];
	oDetSrc.setData(oDetData);
}


function saveDetectorSuccess(data, status, jqXHR)
{
	$('#detector-revert').button('option', 'disabled', true);
	$('#detector-save').button('option', 'disabled', true);
	--g_nTotal;
	$('#detector-next').html('Next of ' + g_nTotal);
	$('#detector-next').button('option', 'disabled', g_nTotal == 0);
	if (g_nTotal == 0)
	{
		$('#detector-next').button('option', 'disabled', true);
		$('#detector-next').html('Next');
		instructions(['All detectors have a one to one mapping. Select a specific detector that needs changes.', 'Edit Traffic Detector']);
	}
	else
		instructions(g_oInstructions['editdet']);
	$('#instructions-status').html('Detector save succeeded');
	
	$('#det-status').html('Save succeeded');
}


function saveDetectorFail()
{
	$('#instructions-error').html('Detector save failed');
	$('#det-status').html('Save failed');
}

function editDetector(oEvent)
{
	if (!$(g_oDialogs['detectoredit']).dialog('isOpen'))
		toggleDetectorEdit({});
	nextDetector(oEvent)
}


function switchToDetector(oEvent)
{
	$('#edit-control').hide();
	$('#detector-control').show();
	let oCancel = $('#dlgCancel');
	if (oCancel.dialog('isOpen'))
		oCancel.dialog('close');
	let oRoadLegend = $(g_oDialogs['roadlegend']);
	if (oRoadLegend.dialog('isOpen'))
		oRoadLegend.dialog('close');
	$(g_oDialogs['detectorlegend']).dialog('open');
	document.activeElement.blur();
	turnOffAddRemove();
	turnOffMerge();
	turnOffSplit();
	let oMap = g_oMap;
	oMap.off('mouseenter', 'geo-lines', hoverHighlight);
	oMap.off('mousemove', 'geo-lines', updateHighlight);
	oMap.off('mouseleave', 'geo-lines', unHighlight);
	setCursor('wait');
	oMap.getCanvas().style.cursor = g_sCursor;
	loadDetectors();
}


function loadDetectors()
{
	$.ajax(
	{
		'url': 'api/generatenetwork/detectors',
		'dataType': 'json',
		'method': 'POST',
		'networkid': g_sCurNetwork,
		'data': {'token': sessionStorage.token, 'networkid': g_sCurNetwork}
	}).done(detectorSuccess).fail(detectorFail); 
}


function detectorSuccess(data, status, jqXHR)
{
	g_bHasFile = true;
	let oMap = g_oMap;
	setCursor('');
	removeSource('detectors', oMap);
	let oData = {'type': 'FeatureCollection', 'features': []};
	let nOneToOne = 0;
	let nOneToMany = 0;
	let nNoMapping = 0;
	for (let oFeature of data.values())
	{
		let nRoads = oFeature.properties.mappedto.length
		oFeature.properties.roads = nRoads;
		if (nRoads == 0)
			++nNoMapping;
		else if (nRoads == 1)
			++nOneToOne;
		else
			++nOneToMany;
		oData.features.push(oFeature);
	}
	
	$('#status-total').html(nNoMapping + nOneToOne + nOneToMany);
	$('#status-onetoone').html(nOneToOne);
	$('#status-nomapping').html(nNoMapping);
	$('#status-onetomany').html(nOneToMany);
	$(g_oDialogs['detectorstatus']).dialog('open');
	document.activeElement.blur();
	oMap.addSource('detectors', {'type': 'geojson', 'data': oData, 'generateId': true});
	oMap.addLayer(g_oLayers['detector-select']);
	oMap.addLayer(g_oLayers['detectors']);
	oMap.getCanvas().style.cursor = g_sCursor;	
}


function detectorFail(jqXHR, textStatus, errorThrown)
{
	instructions(g_oInstructions['upload'], '', 'No detector file uploaded for network');
	setCursor('');
	g_oMap.getCanvas().style.cursor = g_sCursor;	
}


function leaveDetectors()
{
	let oMap = g_oMap;
	g_bHasFile = false;
	oMap.on('mouseenter', 'geo-lines', hoverHighlight);
	oMap.on('mouseleave', 'geo-lines', unHighlight);
	oMap.off('mouseenter', 'detectors', hoverHighlightPoint);
	oMap.off('mousemove', 'detectors', updateHighlightPoint);
	oMap.off('mouseleave', 'detectors', unHighlightPoint);
	oMap.off('click', 'detectors', editDetector);
	$('#detector-control').hide();
	$('#edit-control').show();
	$(g_oDialogs['roadlegend']).dialog('open');
	document.activeElement.blur();
	let oDetectorLegend = $(g_oDialogs['detectorlegend']);
	if (oDetectorLegend.dialog('isOpen'))
		oDetectorLegend.dialog('close');
	if ($(g_oDialogs['detectoredit']).dialog('isOpen'))
		toggleDetectorEdit({});
	removeSource('detectors', oMap);
	oMap.fitBounds(g_oCurBounds, {'padding': 50});
	startSelectNetwork(true);
	instructions(g_oInstructions['edit']);
}

function checkDetectorFile()
{
	if (g_bHasFile)
	{
		$(g_oDialogs['uploadconfirm']).dialog('open');
		document.activeElement.blur();
	}
	else
		selectFile();
}

function selectFile()
{
	$('#detectorFile').trigger('click');
}

function uploadFile(oEvent)
{
	let oFile = $('#detectorFile')[0].files[0]
	let sContents = '';
	let nRecv = 0;
	var oReader = oFile.stream().getReader();
	oReader.read().then(function processText({done, value}) 
	{
		// Result objects contain two properties:
		// done  - true if the stream has already given you all its data.
		// value - some data. Always undefined when done is true.
		if (done) 
		{
			$.ajax('/api/generatenetwork/upload', 
			{
				type: 'POST',
				data: {'token': sessionStorage.token, 'networkid': g_sCurNetwork, 'file': sContents}
			}).done(function()
			{
				loadDetectors(oEvent);
			}).fail(function() 
			{
				alert('fail');
			});
			return;
		}

		// value for fetch streams is a Uint8Array
		nRecv += value.length;
		let chunk = value;

		sContents += new TextDecoder("utf-8").decode(chunk);

		// Read some more, and call this function again
		return oReader.read().then(processText);
	});
}

export {switchToDetector, leaveDetectors, checkDetectorFile, uploadFile, toggleDetectorEdit, nextDetector,
		saveDetector, revertDetector, exitDetectorEdit, detChange, startDetector, selectFile};