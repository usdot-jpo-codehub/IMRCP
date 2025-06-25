import './jquery/jquery.segmentSelector.js';
import './jquery/jquery.datetimepicker.full.js';
import './jquery/jquery.ui.labeledslider.min.js';
import './jquery/jquery.divStack.js';
import './jquery/jquery.notifications.dialog.js';

import {minutesToHHmm} from './common.js';
import {setupObstypeLegendDivs, buildPaneSourceInputs} from './map-legends.js'
import {FeatureDetailsWindow} from './map-feature-details-window.js';
import {saveSettings, loadSettings} from './map-settings.js';
import {ReportSelectionManager} from './map-report-window.js';
import {addSourceAndLayers, removeSourceAndLayers, pointToPaddedBounds, featureInSources, iconDivFromSprite, g_oLayers} from './map-util.js';
import {getNetworksAjax, getProfileAjax, initCommonMap, getLastLayer} from './map-common.js';

window.g_oRequirements = {'groups': 'imrcp-user;imrcp-admin'};
let bRefreshMap = false;
let nLastSliderVal;
let nImrcpReqs = 0;
let sLastMapboxLayer;
let OFFSET = 30;

const mapMouseMoveHandler = (sources, spriteDef) => 
{
	let lastHoverFeature;
	const hoverablePointLayers = new Set(["Flood Sensors", "Weather Sensors", "Mobile Sensors"]);
	return ({target:map, point}) => 
	{
		let oQuery = map.queryRenderedFeatures(pointToPaddedBounds(point));
		const features = oQuery.filter(featureInSources(sources)).filter((oF)=>oF.layer.type !== 'fill');
		const currentSelectedFeature = features.length === 0 ? null : features[0];
		if (currentSelectedFeature)
			$(map.getCanvas()).addClass("clickable");
		else
			$(map.getCanvas()).removeClass("clickable");

		if (lastHoverFeature && (!currentSelectedFeature || lastHoverFeature.id !== currentSelectedFeature.id))
		{
		  // if (!newHoverIds.has(f.id)) -- this doesn't work while we are only highlighting the first layer
			let sType = sources.get(lastHoverFeature.source).type;
			if (sType === 'symbol')
			{
				if (lastHoverFeature.marker)
					lastHoverFeature.marker.remove();
				else
					map.setFeatureState(lastHoverFeature, {hover: false});
			}
			else if (map.getSource(lastHoverFeature.source) && sType === 'line') // don't change feature state if feature's source has been removed
				map.setFeatureState(lastHoverFeature, {hover: false});
		}

		if (currentSelectedFeature && (!lastHoverFeature || lastHoverFeature.id !== currentSelectedFeature.id))
		{
			const featureSource = sources.get(currentSelectedFeature.source);
			if (featureSource.type === 'symbol')
			{
				if (hoverablePointLayers.has(currentSelectedFeature.source))
				{
					let sImageName = currentSelectedFeature.layer.layout["icon-image"];
//					if (sImageName.indexOf("-sdf") >= 0)
//					{
						map.setFeatureState(currentSelectedFeature, {hover: true});
//					}
//					else
//					{
//						sImageName += "-hover"; // append hover suffix
//						currentSelectedFeature.marker = new mapboxgl.Marker($(iconDivFromSprite(spriteDef[sImageName])).get(0)).setLngLat(currentSelectedFeature.geometry.coordinates).addTo(map);
//					}
				}
			}
			else if (featureSource.type === 'line')
				map.setFeatureState(currentSelectedFeature, {hover: true});

			lastHoverFeature = currentSelectedFeature;
		}
		else if (!currentSelectedFeature)
			lastHoverFeature = null;
	};
};

const buildSourceMap = (sourceData, spriteDef) =>
{
	const sources = new Map();
	Object.entries(sourceData).forEach(([sourceId, {group, source, layers, type}]) => 
	{
		if (sourceId[0] === '#')
			return;
		const legendElements = [];

		const fullLayerDefinitions = [];
		const defaultLayer = layers[0];		
		for (let layer of layers)
		{
			
			const fullLayer = $.extend(true, {type, source: sourceId}, defaultLayer, layer);
			fullLayerDefinitions.push(fullLayer);

			const label = layer.metadata['imrcp-range'];
			switch (type)
			{
				case 'fill':
				case 'line':
					legendElements.push({'label': label, 'color': layer.paint[ type + '-color'], 'opacity': fullLayer.paint[type + '-opacity'] ? fullLayer.paint[type + '-opacity'] : 1.0});
					break;
				case 'symbol':
					const img = layer.layout['icon-image'];
					if (img)
						legendElements.push({label, style: img});
					break;
				case 'raster':
					legendElements.push({'label': label, 'color': '#006fc6', 'opacity': 1.0});
					break;
			}

			if (type === 'line')
				fullLayer.paint['line-width'] = ["let", "extra",
				["case", ["boolean", ["feature-state", "hover"], false], 2, ["boolean", ["feature-state", "clicked"], false], 2, 0],
				["interpolate", ["exponential", 2], ["zoom"], 10.0, ["+", ["var", "extra"], 2.0], 16.0, ["+", ["var", "extra"], 8.0], 22.0, ["+", ["var", "extra"], 12.0]]];
		}

		const fullSource = {group, type, legendElements, layers: fullLayerDefinitions, id: sourceId, mapboxSource: Object.assign({}, source, {tiles: source.tiles})};

		sources.set(sourceId, fullSource);
	});
	return sources;
};


const refreshMap = (map, sources) => 
{
	sources.forEach(source => 
	{
		if (map.getSource(source.id))
		{
			removeSourceAndLayers(map, source);
			addSourceAndLayers(map, source, sLastMapboxLayer);
		}
	});
};

const scheduleOffsetMinuteTimeout = (nOffset, callback) => 
{
  //set the refresh method to run at the start of the next minute, and
  //then set it to run every minute after that.
	let now = moment();
	let currentMillis = now.valueOf();
	let nAdd = nOffset;
	if (now.seconds() >= nOffset)
		nAdd += 60;
	now.seconds(0).milliseconds(0).add(nAdd, 'seconds');
	setTimeout(function ()
	{
		callback();
		setInterval(callback, 60000);

	}, now.valueOf() - currentMillis);
};

const layerCheckHandler = (map, sources) => ({target}) => 
{
    const jqEl = $(target);
    const sourceId = jqEl.val();
    const source = sources.get(sourceId);

	if (jqEl.prop('checked'))
		addSourceAndLayers(map, source, sLastMapboxLayer);
	else
		removeSourceAndLayers(map, source);

};

function setObsGroupView()
{
	$('#legendDeck').find('.obstype-pane-list li input:checked').each(function(idx, el) 
	{
		$(el).trigger('click');
	});
	$(this).siblings().find('li').each(function(idx, el) 
	{
		let sName = $(el).text().trim();
		let sSelector = `input[value="${sName}"]`;
		$(sSelector).trigger('click');
	});
		
}


const displayZoom = ({target}) => $('#txtLocationLookup').text(('' + target.getZoom()).substring(0, 3));
function updateDateSlider(oMoment)
{
	$('#dateslider > .ui-slider-handle').text(oMoment.format('MM/DD'));
}

function updateTimeSlider(oMoment)
{
	$('#timeslider > .ui-slider-handle').text(oMoment.format('HH:mm'));
}


function setTimeStep()
{
	let oSlider = $('#dateslider');
	let nVal = oSlider.labeledSlider('value');
	let nStep = 1;
	if (nVal > 0)
		nStep = 60;

	$('#timeslider').labeledSlider('option', 'step', nStep);
	return nStep;
}


function getNowTime()
{
	let oNow = moment().milliseconds(0);
	if (oNow.seconds() < OFFSET)
		oNow.add(-1, 'minute');
	
	return oNow.seconds(0);
}

const setRefCookie = moment => document.cookie = `rtime=${moment.valueOf()};path=/`;
const setTimeCookie = moment => document.cookie = `ttime=${moment.valueOf()};path=/`;

const sourceLayersPromise = $.getJSON('mapbox/sourcelayers.json').promise();
const spriteDefinitionPromise = $.getJSON('images/sprite.json').promise();
const obstypesPromise = $.getJSON('obstypes.json').promise();

async function initialize()
{
	$(document).prop('title', 'IMRCP View Map');
	
	const settings = await loadSettings;
	const map = initCommonMap('mapid', settings.map.center.lng, settings.map.center.lat, settings.map.zoom, 4, 22);
	const sources = buildSourceMap(await sourceLayersPromise, await spriteDefinitionPromise);
	let oLonLat = new mapboxgl.Popup({closeButton: false, closeOnClick: false, anchor: 'bottom', offset: [0, -25], maxwidth: 'none'});
	function updateLonLatPos(oEvent)
	{
		oLonLat.setLngLat(oEvent.lngLat);
		oLonLat.setHTML(`${oEvent.lngLat.lng.toFixed(7)}, ${oEvent.lngLat.lat.toFixed(7)}<br>Right-click to copy`);
	}
	
	map.on('load', async function ()
	{
		sLastMapboxLayer = getLastLayer(map);
		const legendDeckContainer = $('#legendDeck');
		buildPaneSourceInputs(map, sources, settings.layers, addSourceAndLayers, sLastMapboxLayer);
		if (sessionStorage.uname === 'cherneya')
		{
			let oLatLon = $('<li><div id="mapLngLat" style="display:inline-block" class="w3-navitem">0,0</div></li>');
			oLatLon.click(() => map.showTileBoundaries = !map.showTileBoundaries);
			$('#navbar').append(oLatLon);
			map.on('click', function(e) {
				$('#mapLngLat').html(e.lngLat.lng.toFixed(7) + ' ' + e.lngLat.lat.toFixed(7) + ' ' + map.getZoom());
			});
		}
		legendDeckContainer.find('.obstype-pane-list li input').change(layerCheckHandler(map, sources));
		let oObsGroupBtns = $('.obs-group-btn');
		oObsGroupBtns.on('click', setObsGroupView);
		var legendDivStack = legendDeckContainer.divStack({});
		setupObstypeLegendDivs(sources);
	});

	const refreshMyMap = () => refreshMap(map, sources);

	map.on('zoomend', displayZoom);
	map.on('zoom', displayZoom);
	displayZoom({target: map});

	map.on("mousemove", mapMouseMoveHandler(sources, await spriteDefinitionPromise));
	map.on('mousemove', updateLonLatPos);
	map.on('contextmenu', function(oEvent)
	{
		if (oLonLat.isOpen())
			navigator.clipboard.writeText(`${oEvent.lngLat.lng.toFixed(7)}, ${oEvent.lngLat.lat.toFixed(7)}`);
	});

	let oQueryTime = getNowTime();
	let oRefTime = moment(oQueryTime);
	let oTimeForLabel = moment().hours(0).minutes(0).seconds(0).milliseconds(0);
	
	let nMin = -5;
	let nMax = 5;
	let nLabelDaysBack = 4;
	let nLabelStep = 1;
	let nTickStep = nLabelStep;
	let aLabels = [];
	
	
	for (let nOffset = 0; nOffset > nMin; nOffset -= nLabelStep)
		aLabels.push(nOffset);
	aLabels = aLabels.reverse()
	
	for (let nOffset = nLabelStep; nOffset < nMax; nOffset += nLabelStep)
		aLabels.push(nOffset);
	
	let aTicks = [];
	for (let nOffset = 0; nOffset > nMin; nOffset -= nTickStep)
		aTicks.push(nOffset);
	aTicks = aTicks.reverse();
	
	for (let nOffset = nTickStep; nOffset < nMax; nOffset += nTickStep)
		aTicks.push(nOffset);
	
	let mapDateSlider = $('#dateslider').labeledSlider(
	{
		slide: function(event, ui) 
		{
			let oUpdateTime = moment(oRefTime).add(ui.value, 'days');
			updateDateSlider(oUpdateTime);
		},
		labelPosition: 'on',
		min: nMin,
		max: nMax,
		step: 1,
		value: 0,
		ticks: true,
		defaultLabelFn: function(nOffset)
		{
			return moment(oRefTime).add(nOffset, 'days').format("MM/DD");
		},
		labelValues: aLabels,
		tickValues: aTicks,
		stop: function (event, ui)
		{
			let nStep = setTimeStep();
			let oTimeslider = $('#timeslider');
			oTimeslider.labeledSlider('option', 'step', nStep);
			let nTimeVal = $('#timeslider').labeledSlider('value');
			$('#timeslider').labeledSlider('value', Math.floor(nTimeVal / nStep) * nStep);
			let oSelectedTime = moment(oRefTime).add(ui.value, 'days').minutes(0).hours(0).add($('#timeslider').labeledSlider('value'), 'minutes');
			updateQueryTime(oSelectedTime);
			
			refreshMyMap();
		}
	});
	
	nMin = 0;
	nMax = 1440;
	nLabelStep = 60;
	nTickStep = nLabelStep / 2;
	aLabels = [];
	
	for (let nOffset = 0; nOffset > nMin; nOffset -= nLabelStep)
		aLabels.push(nOffset);
	aLabels = aLabels.reverse();
	
	for (let nOffset = nLabelStep; nOffset < nMax; nOffset += nLabelStep)
		aLabels.push(nOffset);
	
	aTicks = [];
	for (let nOffset = 0; nOffset > nMin; nOffset -= nTickStep)
		aTicks.push(nOffset);
	aTicks = aTicks.reverse();
	
	for (let nOffset = nTickStep; nOffset < nMax; nOffset += nTickStep)
		aTicks.push(nOffset);

	let mapTimeSlider = $("#timeslider").labeledSlider(
	{
		slide: function(event, ui) 
		{
			let oUpdateTime = moment(oRefTime).add(ui.value, 'minutes').subtract(oRefTime.hour(), 'hours').subtract(oRefTime.minute(), 'minutes');
			updateTimeSlider(oUpdateTime);
		},
		labelPosition: 'on',
		min: nMin,
		max: nMax,
		step: 1,
		value: oQueryTime.hours() * 60 + oQueryTime.minutes(),
		ticks: true,
		defaultLabelFn: function(nOffset)
		{
			return moment(oTimeForLabel).add(nOffset, 'minutes').format("HH:mm");
		},
		labelValues: aLabels,
		tickValues: aTicks,
		stop: function (event, ui)
		{
			let oSelectedTime = moment(oQueryTime).minutes(0).hours(0).add(ui.value, 'minutes');
			let nLast = ui.value;
			if (event.originalEvent.type === 'keyup' && ui.value === 0 && nLastSliderVal === 0 && mapDateSlider.labeledSlider('value') !== mapDateSlider.labeledSlider('option', 'min'))
			{
				mapDateSlider.labeledSlider('value', mapDateSlider.labeledSlider('value') - 1);
				setTimeStep();
				oSelectedTime.subtract(mapTimeSlider.labeledSlider('option', 'step'), 'minutes');
				mapTimeSlider.labeledSlider('value', mapTimeSlider.labeledSlider('option', 'max') - mapTimeSlider.labeledSlider('option', 'step'));
				updateDateSlider(oSelectedTime);
				
			}
			if (ui.value === nMax)
			{
				if (mapDateSlider.labeledSlider('value') === mapDateSlider.labeledSlider('option', 'max'))
				{
					oSelectedTime.subtract(mapTimeSlider.labeledSlider('option', 'step'), 'minutes');
					mapTimeSlider.labeledSlider('value', mapTimeSlider.labeledSlider('value') - mapTimeSlider.labeledSlider('option', 'step'));
				}
				else
				{
					mapTimeSlider.labeledSlider('value', 0);
					nLast = 0;
					mapDateSlider.labeledSlider('value', mapDateSlider.labeledSlider('value') + 1);
					setTimeStep();
					updateDateSlider(oSelectedTime);
				}
			}

			updateQueryTime(oSelectedTime);
			refreshMyMap();
			nLastSliderVal = nLast;
		}
	});
	nLastSliderVal = mapTimeSlider.labeledSlider('value');

	const mapDialog = new FeatureDetailsWindow(
	{
		mapSelector: '#mapid', sources,
		selectedTimeStartFunction: () => oRefTime.valueOf(), //+ selectedValue * 60 * 1000;
		selectedTimeEndFunction: () => oQueryTime.valueOf()
	});

	const mapClickHandler = ({target, point, lngLat}) => 
	{
		const features = target.queryRenderedFeatures(pointToPaddedBounds(point)).filter(featureInSources(sources));

		if (features.length > 0)
			mapDialog.showFeatureDetails(features[0], lngLat, map);
		else
			mapDialog.showFeatureDetails(null, lngLat, map);
	};

	map.on('click', mapClickHandler);
	
	
	function updateRefTime(oNewTime)
	{
		setRefCookie(oNewTime);
		oRefTime = oNewTime;
		$('#dateslider .label-on').each(function(index, el) {
			$(el).html($('#dateslider').labeledSlider('option').defaultLabelFn(index - nLabelDaysBack));
		});
		$('#btnTime').val(oNewTime.format("YYYY/MM/DD HH:mm"));
		
		if (document.visibilityState === 'visible')
		{
			bRefreshMap = false;
			refreshMyMap();
		}
		else
		{
			bRefreshMap = true;
		}
	}
	
	
	function updateQueryTime(oNewTime)
	{
		setTimeCookie(oNewTime);
		oQueryTime = oNewTime;
		mapTimeSlider.labeledSlider('value', oQueryTime.hours() * 60 + oQueryTime.minutes());
		let nDayDiff = oQueryTime.dayOfYear() - oRefTime.dayOfYear();
		if (nDayDiff > 5) //
		{
			nDayDiff -= oQueryTime.isLeapYear() ? 366 : 365;
		}
		else if (nDayDiff < -5)
		{
			nDayDiff += oQueryTime.isLeapYear() ? 366 : 365;
		}
			
		mapDateSlider.labeledSlider('value', nDayDiff);
		updateTimeSlider(oQueryTime);
		updateDateSlider(oQueryTime);
		setTimeStep();

		if (document.visibilityState === 'visible')
		{
			bRefreshMap = false;
			refreshMyMap();
		}
		else
		{
			bRefreshMap = true;
		}
	}
	
	function updateAreaOpacity()
	{
		let dOpacity = Number($('#selOpacity').val()) / 100.0;
		sources.forEach(source => 
		{
			if (source.group === 'Area')
			{
				for (let oLegendEl of source.legendElements.values())
				{
					oLegendEl.opacity = dOpacity;
				}
				
				for (let oLayer of source.layers.values())
				{
					if (oLayer.paint === undefined)
						continue;
					oLayer.paint['fill-opacity'] = dOpacity;
					if (map.getLayer(oLayer.id) !== undefined)
						map.setPaintProperty(oLayer.id, 'fill-opacity', dOpacity);
				}
			}
		});
	}

	const autoRefresh = () => 
	{
		let nInterval = Number($('#selAutoRefresh').val());
		if (nInterval > 0)
		{
			let oNow = moment().seconds(0).milliseconds(0);
			let nStep = mapTimeSlider.labeledSlider('option', 'step');
			if (oNow.minutes() % nInterval === 0)
			{
				let oNewTime = moment(oQueryTime).hours(0).minutes(0).add(oQueryTime.hours() * 60 + (Math.floor(oQueryTime.minutes() / nInterval) * nInterval) + Math.max(nInterval, nStep), 'minutes');
				if (Math.abs(oNow.diff(oRefTime, 'minutes')) <= nInterval)
					updateRefTime(getNowTime());
				
					if (oNow.minutes() % nStep === 0)
						updateQueryTime(oNewTime);
				
			}
		}
	};

	scheduleOffsetMinuteTimeout(OFFSET, autoRefresh);
	document.addEventListener('visibilitychange', () => 
	{
		if (document.visibilityState === 'visible' && bRefreshMap)
		{
			bRefreshMap = false;
			refreshMyMap();
		}
	});
	var minuteInterval = 10;
	var startDate = new Date();

	startDate.setSeconds(0);
	startDate.setMilliseconds(0);
	startDate.setMinutes(0);

	//set max time to the next interval + 6 hours
	var maxTime = new Date(startDate.getTime() + (1000 * 60 * 60 * 6) + (1000 * 60 * minuteInterval));
	maxTime.setMinutes(maxTime.getMinutes() - maxTime.getMinutes() % minuteInterval);

	$('#txtTime').datetimepicker(
	{
		step: minuteInterval,
		value: startDate,
		formatTime: 'H:i',
		format: 'Y/m/d H:i',
		yearStart: 2017,
		yearEnd: maxTime.getUTCFullYear(),
		maxTime: maxTime,
		maxDate: maxTime,
		closeOnWithoutClick: false,
		onSelectTime: function()
		{
			let newTime = moment(this.getValue()).seconds(0).milliseconds(0);
			updateRefTime(newTime);
			updateQueryTime(newTime);
			//If the newly selected time is within a minute of the current time, re-enable auto-refresh
			$('#chkAutoRefresh').prop('checked', Math.abs(newTime.valueOf() - new Date().getTime()) < 60000);
			mapTimeSlider.labeledSlider('option', 'step', 1);
		}
	});

	$('.xdsoft_today_button').click(function() 
	{
		let oTime = getNowTime();
		let oRefTime = moment(oTime);
		
		
		$('#txtTime').datetimepicker({value: oRefTime});
		$('#txtTime').datetimepicker('hide');
		updateRefTime(oRefTime);
		updateQueryTime(oTime);
		mapTimeSlider.labeledSlider('option', 'step', 1);
	});

	updateQueryTime(oQueryTime);
	updateRefTime(oRefTime);

	let autoCenteredDialogs = $("#divReportDialog, #dialog-form, #divNotificationDialog");
	$(window).resize(function ()
	{
	  const position = {my: "center", at: "center", of: '#map-container'};
	  autoCenteredDialogs.each(function (index)
	  {
		  if ($(this).hasClass("ui-dialog-content")) // Test if the dialog has been initialized
			  $(this).dialog("option", "position", position);
	  });
	  $("#divNotificationDialog").notificationDialog("option", "position", position);
	});

	const  dialogDrag = () => 
	{
		autoCenteredDialogs = autoCenteredDialogs.not('#dialog-form');
		$('#dialog-form').off('dialogdragstart', dialogDrag);
	};

	$('#dialog-form').on('dialogdragstart', dialogDrag);
	function toggleLonLat()
	{
		if ($('#chkLonLat').prop('checked'))
			oLonLat.addTo(map);
		else
			oLonLat.remove();
	}
	
	$('#chkLonLat').on('change', toggleLonLat);
	$('#chkLonLat').prop('checked', settings.lonlat);
	toggleLonLat();
	$('#selAutoRefresh').val(settings.refresh);
	let sLastVal = settings.refresh;
	$('#selAutoRefresh').on('change', function(oEvent)
	{
		let sNewVal = $('#selAutoRefresh').val();
		if (Number(sNewVal) > 0)
		{
			if (Number(sLastVal) === 0)
				$('.xdsoft_today_button').trigger('click');
		}
		sLastVal = sNewVal;
	});
	$('#selOpacity').on('change', updateAreaOpacity);
	
	$('#selOpacity').val(settings.opacity);
	$('#selOpacity').trigger('change');
	if (settings.page !== undefined)
		$('#selPage').val(settings.page);

	const notificationsDialog = $('#divNotificationDialog').notificationDialog(
	{
		selectNotification: (e, notification) => map.fitBounds([[notification.lat1, notification.lon1], [notification.lat2, notification.lon2]]),

		displayOnNewAlerts: () => $('#chkNotifications').prop('checked')
	});


	$("#save-user-view").click(saveSettings(map));
	$('#btnTime').click(() =>
	{
		$('#txtTime').datetimepicker('toggle');
	});
}


$(document).on('initPage', initialize);
