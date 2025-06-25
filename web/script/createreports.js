import './jquery/jquery.datetimepicker.full.js';
import './jquery/jquery.ui.labeledslider.min.js';

import {g_oLayers, removeSource, getPolygonBoundingBox, startDrawPoly, getLineStringBoundingBox, binarySearch, 
	isFeatureInsidePolygonFeature, pointToPaddedBounds, mapOffBoundFn, addStyleRule} from './map-util.js';
import {minutesToHHmm, minutesToHH} from './common.js';
import {loadSettings} from './map-settings.js';
import {getNetworksAjax, getProfileAjax, initCommonMap, ASSEMBLING, WORKINPROGRESS, PUBLISHING, PUBLISHED, ERROR, isStatus} from './map-common.js';

window.g_oRequirements = {'groups': 'imrcp-user;imrcp-admin'};
let g_oMap;
let g_oHovers = {};
let g_oElements = {};
let g_oNetworksBoundingBox = [[Number.MAX_VALUE, Number.MAX_VALUE], [Number.MIN_SAFE_INTEGER, Number.MIN_SAFE_INTEGER]];
let g_sLoadedNetwork;
let g_bClearSelection = true;
let g_nNetworks;


async function initialize()
{
	$('#pageoverlay').html(`<p class="centered-element">Initializing...</p>`).css({'opacity': 0.5, 'font-size': 'x-large'}).show();
	$(document).prop('title', 'IMRCP Create Report');
	let pNetworks = getNetworksAjax().promise();
	
	let pProfile = getProfileAjax().promise();
	let pObstypes = $.getJSON('obstypes.json').promise();
	g_oMap = initCommonMap('mapid', -98.585522, 39.8333333, 4, 4, 24);
	
	g_oMap.on('load', async function() 
	{
		buildInstructionDialog();	
		buildReportDialogs(await pObstypes);
		let oAllNetworks = await pNetworks;
		let oProfile = await pProfile;
		let oNetworks = {'type': 'geojson', 'maxzoom': 9, 'data': {'type': 'FeatureCollection', 'features': []}, 'generateId': true};
		for (let oNetwork of oAllNetworks.values())
		{
			let nStatus = oNetwork.properties.status;
			let nDisplayStatus = 4;
			if (isStatus(nStatus, ASSEMBLING))
				nDisplayStatus = 0;
			else if (isStatus(nStatus, WORKINPROGRESS))
				nDisplayStatus = 1;
			else if (isStatus(nStatus, PUBLISHING))
				nDisplayStatus = 2;
			else if (isStatus(nStatus, PUBLISHED))
				nDisplayStatus = 3;

			oNetwork.properties.displaystatus = nDisplayStatus;
			for (let oProfileNetwork of oProfile.networks.values())
			{
				if (oProfileNetwork.id === oNetwork.properties.networkid)
				{
					oNetworks.data.features.push(oNetwork);
					for (let aCoord of oNetwork.geometry.coordinates[0].values())
					{
						if (aCoord[0] < g_oNetworksBoundingBox[0][0])
							g_oNetworksBoundingBox[0][0] = aCoord[0];
						
						if (aCoord[1] < g_oNetworksBoundingBox[0][1])
							g_oNetworksBoundingBox[0][1] = aCoord[1];
						
						if (aCoord[0] > g_oNetworksBoundingBox[1][0])
							g_oNetworksBoundingBox[1][0] = aCoord[0];
						
						if (aCoord[1] > g_oNetworksBoundingBox[1][1])
							g_oNetworksBoundingBox[1][1] = aCoord[1];
					}
				}
			}
		}
		
		g_nNetworks = oNetworks.data.features.length;
		g_oMap.addSource('network-polygons', oNetworks);
		g_oMap.addLayer(g_oLayers['network-polygons-report']);
		
		let oCan = document.createElement('canvas');
		oCan.width = 24;
		oCan.height = 24;
		
		let ctx = oCan.getContext('2d');
		ctx.fillStyle = '#179b54';
		ctx.font = '24px FontAwesome';
		ctx.textAlign = 'center';
		ctx.textBaseline = 'middle';
		ctx.fillText('\uf067', 12, 12);
		addStyleRule('.pluscursor', `cursor: url('${oCan.toDataURL('image/png')}') 12 12, auto`);
		ctx.clearRect(0, 0, oCan.width, oCan.height);
		ctx.fillStyle = '#b2000c';
		ctx.fillText('\uf068', 12, 12);
		addStyleRule('.minuscursor', `cursor: url('${oCan.toDataURL('image/png')}') 12 12, auto`);
		$('#pageoverlay').hide();
		selectANetwork();
	});
}


function selectANetwork()
{
	if (g_oMap.getLayer('network-polygons-report') === undefined)
		g_oMap.addLayer(g_oLayers['network-polygons-report']);
	
	if (g_oMap.getLayer('geo-lines-report') !== undefined)
		g_oMap.removeLayer('geo-lines-report');
	g_oMap.fitBounds(g_oNetworksBoundingBox, {'padding': 50});
	if (g_nNetworks === 1)
	{
		loadANetwork(g_oMap.getSource('network-polygons')._data);
	}
	else
	{
		g_oMap.on('mouseenter', 'network-polygons-report', hoverHighlight);
		g_oMap.on('click', 'network-polygons-report', loadANetwork);
		$('#instructions').html('Left-click network to create a report');
		$('#dlgInstructions').dialog('option', 'title', 'Select Network');
	}
}


function loadANetwork(oEvent)
{
	let oNetwork = oEvent.features[0];
	if (g_nNetworks === 1)
		g_oMap.fitBounds(getPolygonBoundingBox(oNetwork), {'padding': 50});
	else
		g_oMap.fitBounds(getPolygonBoundingBox(g_oMap.getSource('network-polygons')._data.features[oNetwork.id]), {'padding': 50});
	g_oMap.off('mouseenter', 'network-polygons-report', hoverHighlight);
	mapOffBoundFn(g_oMap, 'mousemove', unHighlight.name);
	g_oMap.off('click', 'network-polygons-report', loadANetwork);
	if (g_oHovers['network-polygons-report'] !== undefined)
	{
		g_oMap.setFeatureState(g_oHovers['network-polygons-report'], {'hover': false});
		g_oHovers['network-polygons-report'] = undefined;
	}
	if (oNetwork.properties.networkid === g_sLoadedNetwork)
	{
		if (g_oMap.getLayer('network-polygons-report') !== undefined)
			g_oMap.removeLayer('network-polygons-report');
		g_oMap.addLayer(g_oLayers['geo-lines-report']);
		startReport();
	}
	else
	{
		$('#pageoverlay').html(`<p class="centered-element">Loading network: ${oNetwork.properties.label}</p>`).css({'opacity': 0.5, 'font-size': 'x-large'}).show();
		$.ajax(
		{
			'url': 'api/generatenetwork/geo',
			'dataType': 'json',
			'method': 'POST',
			'networkid': oNetwork.properties.networkid,
			'data': {'token': sessionStorage.token, 'networkid': oNetwork.properties.networkid, 'published': 'true'}
		}).done(geoSuccess).fail(function() 
		{
			$('#pageoverlay').html(`<p class="centered-element">Failed to retrieve network: "${oNetwork.properties.label}"<br>Try again later.</p>`);
			if (g_nNetworks > 1)
				selectANetwork();
		});
	}
	
}

function geoSuccess(oData, sStatus, oJqXHR)
{
	removeSource('geo-lines', g_oMap);
	g_oMap.addSource('geo-lines', {'type': 'geojson', 'data': {'type': 'FeatureCollection', 'features': []}, 'generateId': true});
	let oLineData = g_oMap.getSource('geo-lines')._data;
	oLineData.features = [];
	let nCount = 0;
	for (let oFeature of oData.values())
	{
		getLineStringBoundingBox(oFeature);
		oFeature.id = nCount++;
		oFeature.source = 'geo-lines';
		oLineData.features.push(oFeature);
	}
	
	g_oMap.getSource('geo-lines').setData(oLineData);
	for (let oFeature of oLineData.features.values())
		g_oMap.setFeatureState(oFeature, {'color': '#000', 'include': false, 'hover': false});
	g_oMap.on('sourcedata', finishedStyling);
	if (g_oMap.getLayer('road-number-shield') !== undefined)
		g_oMap.addLayer(g_oLayers['geo-lines-report'], 'road-number-shield');
	else
		g_oMap.addLayer(g_oLayers['geo-lines-report']);
	$(g_oMap.getCanvas()).removeClass('clickable');
	g_sLoadedNetwork = this.networkid;
	startReport();
}


function startReport()
{
	g_oElements = {};
	g_oMap.on('mouseenter', 'geo-lines-report', hoverHighlight);
	g_oMap.on('click', toggleInclude);
	$(document).on('keyup', handleKeyPress);
	let sInstruc = 'Left-click to select a segment for a report';
	if (g_nNetworks > 1)
		sInstruc += '<br>Press Esc to return to network selection';
	
	$('#instructions').html(sInstruc);
	$('#dlgInstructions').dialog('option', 'title', 'Select Segments');
}


function hoverHighlight(oEvent)
{
	if (oEvent.features.length > 0)
	{
		let oFeature = oEvent.features[0];
		let bInclude = g_oMap.getFeatureState(oFeature).include;
		if (bInclude === undefined)
			$(g_oMap.getCanvas()).addClass('clickable');
		else if (bInclude)
			$(g_oMap.getCanvas()).addClass('minuscursor');
		else
			$(g_oMap.getCanvas()).addClass('pluscursor');
		let oHover = g_oHovers[oFeature.layer.id];
		if (oHover !== undefined)
		{
			g_oMap.setFeatureState(oHover, {'hover': false});
		}
		g_oHovers[oFeature.layer.id] = oFeature;
		g_oMap.on('mousemove', oFeature.layer.id, updateHighlight);
		g_oMap.on('mousemove', unHighlight.bind({'layer': oFeature.layer.id}));
		g_oMap.setFeatureState(oFeature, {'hover': true});
	}
}


function updateHighlight(oEvent)
{
	if (oEvent.features.length > 0)
	{
		let oFeature = oEvent.features[0];
		let oHover = g_oHovers[oFeature.layer.id];
				
		if (oHover !== undefined)
		{
			g_oMap.setFeatureState(oHover, {'hover': false});
			$(g_oMap.getCanvas()).removeClass('clickable minuscursor pluscursor');
		}
		let bInclude = g_oMap.getFeatureState(oFeature).include;
		if (bInclude === undefined)
			$(g_oMap.getCanvas()).addClass('clickable');
		else if (bInclude)
			$(g_oMap.getCanvas()).addClass('minuscursor');
		else
			$(g_oMap.getCanvas()).addClass('pluscursor');
		g_oHovers[oFeature.layer.id] = oFeature;
		g_oMap.setFeatureState(oFeature, {'hover': true});
	}
}


function unHighlight(oEvent)
{
	if (g_oMap.queryRenderedFeatures(pointToPaddedBounds(oEvent.point), {'layers': [this.layer]}).length > 0)
		return;
	let oHover = g_oHovers[this.layer];
	g_oHovers[this.layer] = undefined;
	if (oHover !== undefined)
	{
		g_oMap.setFeatureState(oHover, {'hover': false});
	}
	
	g_oMap.off('mousemove', this.layer, updateHighlight);
	mapOffBoundFn(g_oMap, 'mousemove', unHighlight.name);
	$(g_oMap.getCanvas()).removeClass('clickable minuscursor pluscursor');
}


function handleKeyPress(oEvent)
{
	if (oEvent.which === 27) // esc
	{
		if (g_nNetworks > 1 && Object.keys(g_oElements).length === 0)
		{
			g_oMap.off('mouseenter', 'geo-lines-report', hoverHighlight);
			g_oMap.off('click', toggleInclude);
			$(document).off('keyup', handleKeyPress);
			selectANetwork();
			return;
		}
		
		if (g_bClearSelection)
		{
			if (Object.keys(g_oElements).length > 0)
			{
				g_oElements = {};
				let oSrc = g_oMap.getSource('geo-lines');
				let oData = oSrc._data;
				for (let oFeature of oData.features.values())
				{
					g_oMap.setFeatureState(oFeature, {'include': false, 'color': '#000'});
				}
				oSrc.setData(oData);
			}
			let sInstruc = 'Left-click to select a segment for a report';
			if (g_nNetworks > 1)
				sInstruc += '<br>Press Esc to return to network selection';

			$('#instructions').html(sInstruc);
		}
		else
			g_bClearSelection = true;
	}
	
	if (oEvent.which === 13)
	{
		if (Object.entries(g_oElements).length === 0)
			return;
		$('#txtReportRefTime').datetimepicker('setOptions', {value: new Date(this.selectedStart)});
		$('#dlgReport').dialog('open');
	}
}


function toggleInclude(oEvent)
{
	let oFeatures = g_oMap.queryRenderedFeatures(pointToPaddedBounds(oEvent.point), {'layers': ['geo-lines-report']});
	if (oFeatures.length > 0)
	{
		let oTemp;
		for (let oFeature of oFeatures.values())
		{
			if (oFeature.id === g_oHovers['geo-lines-report'].id)
			{
				oTemp = oFeature;
				break;
			}
		}
		if (oTemp === undefined)
			return;
//		$('#pageoverlay').html('').css('opacity', 0.1).show();
//		g_oMap.on('sourcedata', finishedStyling);
		let oSrc = oEvent.target.getSource('geo-lines');
		let oData = oSrc._data;
		let oFeature = oData.features[oTemp.id];
		let bInclude = g_oMap.getFeatureState(oFeature).include;
		let sColor = '#000';
		if (bInclude)
		{
			$(g_oMap.getCanvas()).removeClass('minuscursor').addClass('pluscursor');
			delete g_oElements[oFeature.properties.imrcpid];
		}
		else
		{
			$(g_oMap.getCanvas()).removeClass('pluscursor').addClass('minuscursor');
			sColor = '#0c0';
			g_oElements[oFeature.properties.imrcpid] = oFeature;
		}
		g_oMap.setFeatureState(oFeature, {'include': !bInclude, 'color': sColor});
		
		if (Object.keys(g_oElements).length === 0)
		{
			let sInstruc = 'Left-click to select a segment for a report';
			if (g_nNetworks > 1)
				sInstruc += '<br>Press Esc to return to network selection';

			$('#instructions').html(sInstruc);
		}
		else
		{
			let sInstruc = 'Left-click to add/remove segments to/from selection<br>Press Enter to finish selection and continue<br>Press Esc to clear selection<br><br>Thicker lines are included in the selection';
			$('#instructions').html(sInstruc);
		}
	}
}


function finishedStyling(oEvent)
{
	$('#pageoverlay').hide();

	if (g_oMap.getLayer('network-polygons-report') !== undefined)
		g_oMap.removeLayer('network-polygons-report');

	g_oMap.off('sourcedata', finishedStyling);
}


function buildInstructionDialog()
{
	let oDialog = $('#dlgInstructions');
	oDialog.dialog({autoOpen: false, position: {my: "left top", at: "left+8 top+8", of: "#map-container"}, draggable: false, resizable: false, width: 'auto'});
	
	oDialog.dialog('option', 'title', 'Instructions');
	oDialog.siblings().children('.ui-dialog-titlebar-close').remove();
	let sHtml = '<p id="instructions"></p><p id="instructions-status" style="color: #108010"></p><p id="instructions-error" style="color: #d00010"></p>';
	
	oDialog.html(sHtml);
	oDialog.dialog('open');
	document.activeElement.blur();
}


function buildReportDialogs(oObstypes)
{
	let oDialog = $('#dlgReport');
	oDialog.dialog(
	{
		autoOpen: false,
		position: {my: 'center', at: 'center', of: '#map-container'},
		draggable: false,
		resizable: false,
		width: 'auto',
		modal: true,
		dialogClass: 'no-title-form',
		close: function()
		{
			resetSubscriptionFields();
		}
	});
	let reportObstypeOptions = Object.entries(oObstypes)
      .sort((a, b) => a[1].name.localeCompare(b[1].name))
      .reduce((accumulatedOptions, [id, {name, desc, unit}]) => {//id, desc, eng units
        accumulatedOptions += `<option value ='${id}'>${name}, ${desc}`;

        if (unit)
          accumulatedOptions += `, ${unit}`;

        return accumulatedOptions + '</option>';
      }, '');
	oDialog.html(`
      <div id='divSubInput'>
        <table>
          <tr><td>Name</td><td colspan='3'><input type='text' id='txtName' /></td></tr>
          <tr><td class='reportObstypeLabel'><label for='lstReportObstypes'>Obstype (Up to 5)</label></td><td colspan='3'><select multiple='multiple' style='width:100%; height:90px;' id='lstReportObstypes'>${reportObstypeOptions}</select></td></tr>
          <tr id='trMinMax'><td><label for='txtMin'>Min</label></td><td><input size='10' maxlength='9' id='txtMin' type='text' /></td><td><label for='txtMax'>Max</label></td><td><input size='10' maxlength='9' id='txtMax' type='text' /></td></tr>
          <tr><td><label for='lstFormat' >Format</label></td><td colspan='3'><select id='lstFormat' name='format' style='width: 100%;'>
          <option value='CSV' selected='selected'>CSV</option>
        </select></td></tr>
          <tr><td colspan='4'><span style='float:left;'><input id='radTypeReport' type='radio' name='TYPE' checked='checked' /><label for='radTypeReport'>Run Report</label></span><span style='float:right'><input id='radTypeSubscription' type='radio' name='TYPE' /><label for='radTypeSubscription'>Create Subscription</label></span></td></tr>
          <tr class='ReportOnly'><td colspan='4'>Ref Time&nbsp;&nbsp;<input style='width:200px' type='text' value='' id='txtReportRefTime'/></td></tr>
          <tr class='SubscriptionOnly'><td><label for='radInterval15'>Interval</label></td><td colspan='3'>
              <input value='15' id='radInterval15'  checked='checked' type='radio' name='interval' /><label for='radInterval15'>15 min</label>
              <input value='30' id='radInterval30' type='radio' name='interval' /><label for='radInterval30'>30 min</label>
              <input value ='60' id='radInterval60' type='radio' name='interval' /><label for='radInterval60'>1 hour</label>
            </td></tr>
          <tr><td colspan='4'><div id='divSubOffsetSlider'></div></td></tr>
          <tr><td><label>Offset</label></td><td><span id='spnOffset'></span></td><td><label>Duration</label></td><td><span id='spnDuration'></span></td></tr>
          
        </table>
      </div>

      <input type='button' id='btnSubmitReport' value='Submit'/><input type='button' id='btnClose' value='Cancel' />
    </div>`);
	let nInterval = 60;
	let oStartDate = new Date();

	oStartDate.setSeconds(0);
	oStartDate.setMilliseconds(0);
	oStartDate.setMinutes(oStartDate.getMinutes() - oStartDate.getMinutes() % nInterval);
	
	//set max time to the next interval + 168 hours (7 days)
	let oMaxTime = new Date(oStartDate.getTime() + (1000 * 60 * 60 * 168) + (1000 * 60 * nInterval));
	oMaxTime.setMinutes(oMaxTime.getMinutes() - oMaxTime.getMinutes() % nInterval);
	$('#txtReportRefTime').datetimepicker({
		step: nInterval,
		value: oStartDate,
		yearStart: 2017,
		yearEnd: oMaxTime.getUTCFullYear(),
		maxTime: oMaxTime,
		maxDate: oMaxTime,
		formatTime: 'h:i a',
		format: 'Y/m/d h:i a'
    });
	
	let updateRangeFn = function (event, ui)
	{
		let offset = ui.values[0];
		let duration = ui.values[1] - offset;
		$('#spnOffset').text(minutesToHHmm(offset));
		$('#spnDuration').text(minutesToHHmm(duration, true));
	};

	$("#divSubOffsetSlider").labeledSlider({
		min: -1440,
		range: true,
		max: 480,
		step: 30,
		values: [0, 30],
		ticks: true,
		labelValues: [-1440, -1200, -960, -720, -480, -240, 0, 240, 480],
		defaultLabelFn: minutesToHH,
		slide: updateRangeFn,
		change: updateRangeFn
	});
	let subWindowSlider = $("#divSubOffsetSlider");

	$("#radTypeReport").click(function ()
	{
		$(".SubscriptionOnly").hide();
		subWindowSlider.labeledSlider('option', 'values', [0, 30]);
		subWindowSlider.labeledSlider('option', 'min', -1440);
		subWindowSlider.labeledSlider('option', 'labelValues', [-1440, -1200, -960, -720, -480, -240, 0, 240, 480]);
		$(".ReportOnly").show();
		$('#spnType').text("Report");
	});

	$("#radTypeSubscription").click(function ()
	{
		$(".ReportOnly").hide();
		
		subWindowSlider.labeledSlider('option', 'values', [0, 30]);
		subWindowSlider.labeledSlider('option', 'min', -240);
		subWindowSlider.labeledSlider('option', 'labelValues', [-240, -120, 0, 120, 240, 360, 480]);
		$(".SubscriptionOnly").show();
		$('#spnType').text("Subscription");
	});

	$("#radTypeReport").click();

	$("#lstReportObstypes").change(function ()
	{
		let jqThis = $(this);
		let selectedVal = jqThis.val();
		if (selectedVal.length > 5)
		{
			selectedVal.splice(5, selectedVal.length - 5);
			jqThis.val(selectedVal);
		}

		if (selectedVal.length === 1)
			$('#trMinMax').show();
		else
			$('#trMinMax').hide();
	});

	$("#lstReportObstypes").change();
	$("#btnClose").click(() => 
	{
		$('#dlgReport').dialog("close");
		g_bClearSelection = false;
		handleKeyPress({'which': 27});
	});
	$("#btnSubmitReport").click(function ()
	{
		let obstypeList = $('#lstReportObstypes').val();
		let offset = subWindowSlider.labeledSlider('values', 0);
		let duration = subWindowSlider.labeledSlider('values', 1) - offset;
		let dLatMax = Number.MIN_SAFE_INTEGER;
		let dLonMax = Number.MIN_SAFE_INTEGER;
		let dLatMin = Number.MAX_SAFE_INTEGER;
		let dLonMin = Number.MAX_SAFE_INTEGER;
		let aElementIds = [];
		for (let [sId, oFeature] of Object.entries(g_oElements))
		{
			aElementIds.push(sId);
			for (let aCoord of oFeature.geometry.coordinates.values())
			{
				if (aCoord[0] < dLonMin)
					dLonMin = aCoord[0];
				if (aCoord[1] < dLatMin)
					dLatMin = aCoord[1];
				if (aCoord[0] > dLonMax)
					dLonMax = aCoord[0];
				if (aCoord[1] > dLatMax)
					dLatMax = aCoord[1];
			}
		}


		let requestData = {
//			geo: JSON.stringify(g_oMap.getSource('report-location')._data.features),
			minLon: dLonMin,
			minLat: dLatMin,
			maxLon: dLonMax,
			maxLat: dLatMax,
			format: $('#lstFormat').val(),
			offset: offset,
			duration: duration,
			elementIds: aElementIds,
			name: $('#txtName').val(),
			description: $('#txtDescription').val()
		};
		if (obstypeList.length > 0)
		{
			requestData.obsTypeId = obstypeList;
			if (obstypeList.length === 1)
			{
				requestData.maxValue = $('#txtMax').val();
				requestData.minValue = $('#txtMin').val();
			}
		}
		else
		{
			alert('At least one Obstype must be selected.');
			return;
		}
		
		if ($('#txtName').val().length === 0)
		{
			alert('Please enter a name.');
			$('#txtName').focus();
			return;
		}

		if ($('#radTypeReport').prop("checked"))
		{
			requestData.reftime = $('#txtReportRefTime').datetimepicker('getValue').getTime();
			requestData.starttime = requestData.reftime + offset * 60000;
			requestData.endtime = requestData.starttime + duration * 60000;
		}
		else
		{
			requestData.cycle = $('input[name="interval"]:checked').val();
		}
		requestData.token = sessionStorage.token;
		
		$.ajax({url: 'api/reports/add',
		  type: 'post', dataType: 'json',
		  data: requestData,
		  success: function (data)
		  {
			$('#spnName').text(data.name);
			$('#spnUuid').text(data.uuid);

			let url = window.location.href;
			url = url.substring(0, url.lastIndexOf('/')) + '/api/reports/download/' + data.uuid + '/[filename]';
			$('#spnUrl').text(url);
			$('#dlgReport').dialog('close');
			$('#dlgReportResult').dialog('open');

			$('#divSubResult').show();
			$('#divSubError').hide();
			$('#dlgReportResult').dialog("option", "position", {my: "center", at: "center", of: '#map-container'});
		  },
		  error: function ()
		  {
			$('#dlgReportResult').dialog('open');
			$('#divSubResult').hide();
			$('#divSubError').show();
		  }
		});
	});
	
	oDialog = $('#dlgReportResult');
	
	oDialog.dialog(
	{
		autoOpen: false,
		position: {my: 'center', at: 'center', of: '#map-container'},
		draggable: false,
		resizable: false,
		width: 'auto',
		modal: true,
		title: 'Report Results',
		close: function()
		{
			handleKeyPress({'which': 27});
		}
	});
	oDialog.html(
	      `<div id='divSubResult' style='display:none;'>
    	<div>
			<h3><span id='spnType'>Report</span> Info</h3>
			<div> 
        <b>Name: </b><span id='spnName'></span><br/>
        <b>Subscription Identifier: </b><span id='spnUuid'></span><br/>
        <b>Direct URL: </b><span id='spnUrl'></span>
			</div>
		</div>
        <br />
			You can view your current reports and subscriptions and their available files
      on the <a href='reports.html'>Reports</a> page.
	    </div>
      <div id='divSubError' style='display:none;'>An error occurred creating your report or subscription. Please try again later.</div>`);
}


function resetSubscriptionFields()
{
	$('#txtName').val('');
	$('#txtDescription').val('');
	$('#txtMax').val('');
	$('#txtMin').val('');
	$('#lstReportObstypes option:selected').prop('selected', false);
	$('#lstReportObstypes').change();
	$('#lstOffset').prop('selectedIndex', '0');
	$('#lstDuration').prop('selectedIndex', '0');
	$('#divSubOffsetSlider').labeledSlider('values', 0, 0);
	$('#divSubOffsetSlider').labeledSlider('values', 1, 15);
	$('#dlgReport').dialog('option', 'position', {my: 'center', at: 'center', of: '#map-container'});
}


$(document).on('initPage', initialize);