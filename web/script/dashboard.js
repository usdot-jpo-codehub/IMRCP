import {initCommonMap, showPageoverlay, timeoutPageoverlay, getNetworksAjax, getProfileAjax, ASSEMBLING,
	WORKINPROGRESS, PUBLISHING, PUBLISHED, isStatus} from './map-common.js';
import {getLineStringBoundingBox, getPolygonBoundingBox, addStyleRule, g_oLayers, mapOffBoundFn, pointToPaddedBounds, removeSource, startDrawPoly, binarySearch} from './map-util.js';
window.g_oRequirements = {'groups': 'imrcp-user;imrcp-admin'};

let g_oMap;
let g_oDataTable;
let g_oTable = [];
let g_oChart;
let g_oObstypes;
let g_oObsLookups;
let g_oTrash = '<i class="fa fa-lg fa-trash pointer"></i>';
let g_oEdit = '<i class="fa fa-lg fa-edit pointer"></i>';
let g_sEditId = undefined;
let g_oImrcpIdLookup = {};
let g_sSelectedNetwork;
let g_sDeleteId = undefined;
let g_nCurrentIndex = -1;
let g_sCurrentId = undefined;
let g_sMomentFormat = 'YYYY-MM-DD HH:mm:ss';
let g_oGeojsonFeatures = {};
let g_oClickData = {};
let g_oStatus = {};
let g_oQueue = [];
let g_oNetworksBoundingBox = [[Number.MAX_VALUE, Number.MAX_VALUE], [Number.MIN_SAFE_INTEGER, Number.MIN_SAFE_INTEGER]];
let g_nNetworks;
let g_oHovers = {};
let g_oProfile;
let g_oGeoLines = {'id': 'geo-lines', 'type': 'line', 'source': 'geo-lines', 
			'layout':{'line-cap':'round', 'line-join':'round'}, 
			'paint':{'line-opacity': 1.0, 
			'line-color': ['feature-state', 'color'], 
			'line-width': ['interpolate', ['exponential', 2], ['zoom'], 0.0, 1.0, 10.0, 4.0, 24.0, 12.0]}};
let g_bPopupExists = false;
let g_sCoordString;
let g_oSegments = {};
let g_oNetworkFeatures = {};
let POLYSVG = '<svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 448 512"><path d="M96 151.4V360.6c9.7 5.6 17.8 13.7 23.4 23.4H328.6c0-.1 .1-.2 .1-.3l-4.5-7.9-32-56 0 0c-1.4 .1-2.8 .1-4.2 .1c-35.3 0-64-28.7-64-64s28.7-64 64-64c1.4 0 2.8 0 4.2 .1l0 0 32-56 4.5-7.9-.1-.3H119.4c-5.6 9.7-13.7 17.8-23.4 23.4zM384.3 352c35.2 .2 63.7 28.7 63.7 64c0 35.3-28.7 64-64 64c-23.7 0-44.4-12.9-55.4-32H119.4c-11.1 19.1-31.7 32-55.4 32c-35.3 0-64-28.7-64-64c0-23.7 12.9-44.4 32-55.4V151.4C12.9 140.4 0 119.7 0 96C0 60.7 28.7 32 64 32c23.7 0 44.4 12.9 55.4 32H328.6c11.1-19.1 31.7-32 55.4-32c35.3 0 64 28.7 64 64c0 35.3-28.5 63.8-63.7 64l-4.5 7.9-32 56-2.3 4c4.2 8.5 6.5 18 6.5 28.1s-2.3 19.6-6.5 28.1l2.3 4 32 56 4.5 7.9z"/></svg>';
let STALE = 4;
let QUEUED = 3;
let PROCESSING = 2;
let FULFILLED = 1;
let POLLINTERVAL = 60000;
let POLLTIMEOUT = undefined;
const MAXLONG = Math.pow(2, 63) - 1;

let MAPHANDLERS = ['scrollZoom', 'boxZoom', 'dragPan', 'keyboard', 'doubleClickZoom'];
let g_oZoomControl;
let WIZARDSTEP = -1;
let WIZARD =
[
	{'ins': 'Follow the prompts to create a new alert for your dashboard', 'enabled': ['#btn-Next', '#btn-Cancel'], 'disabled': ['#alertLabel', '#btn-Prev', '#btn-DrawRegion', '#btn-PickRoads', '#btn-PickAll', '#btn-Save', '.obs1', '.obs2', '.and']},
	{'ins': 'Select a network on the map', 'enabled':['#btn-Cancel'], 'disabled':['#alertLabel', '#btn-Prev', '#btn-Next', '#btn-DrawRegion', '#btn-PickRoads', '#btn-PickAll', '#btn-Save', '.obs1', '.obs2', '.and']},
	{'ins': 'Wait for network to load', 'enabled':[], 'disabled':['#btn-Cancel','#alertLabel', '#btn-Prev', '#btn-Next', '#btn-DrawRegion', '#btn-PickRoads', '#btn-PickAll', '#btn-Save', '.obs1', '.obs2', '.and']},
	{'ins': `Click ${POLYSVG} to select a region, <i class="fa fa-road"></i> to select roads, or <i class="fa fa-code-fork"></i> to select all roads in the network`, 'enabled':['#btn-DrawRegion', '#btn-PickRoads', '#btn-PickAll', '#btn-Prev', '#btn-Cancel'], 'disabled':['#btn-Next', '#alertLabel', '#btn-Save', '.obs1', '.obs2', '.and']},
	{'ins': ``, 'enabled':['#btn-Cancel'], 'disabled':['#btn-DrawRegion', '#btn-PickRoads', '#btn-PickAll', '#btn-Prev', '#alertLabel', '#btn-Next', '#btn-Save', '.obs1', '.obs2'], 'map': true},
	{'ins': `Select the observation type, comparsion, and value for the first rule`, 'enabled':['#btn-Prev', '#btn-Next', '.obs1', '#btn-Cancel'], 'disabled':['#btn-DrawRegion', '#btn-PickRoads', '#btn-PickAll', '#alertLabel', '#btn-Save', '.obs2', '.and']},
	{'ins': `Select "and" or "or" for a second observation type, or "none"`, 'enabled':['#btn-Prev', '#btn-Next', '.and', '#btn-Cancel'], 'disabled':['#btn-DrawRegion', '#alertLabel', '#btn-PickRoads', '#btn-PickAll', '#btn-Save', '.obs1', '.obs2']},
	{'ins': `Select the observation type, comparsion, and value for the second rule`, 'enabled':['#btn-Prev', '#btn-Next', '.obs2', '#btn-Cancel'], 'disabled':['#btn-DrawRegion', '#btn-PickRoads', '#btn-PickAll', '#alertLabel', '#btn-Save', '.and']},
	{'ins': `Enter a label for this alert`, 'enabled':['#btn-Prev', '#btn-Next', '#alertLabel', '#btn-Cancel'], 'disabled':['#btn-DrawRegion', '.obs1', '#btn-PickRoads', '#btn-PickAll', '#btn-Save', '.obs2', '.and']},
	{'ins': `Click <i class="fa fa-save"></i> to finish`, 'enabled':['#btn-Prev', '#alertLabel', '#btn-Cancel', '#btn-Save'], 'disabled':['#btn-DrawRegion', '#btn-Next', '#btn-PickRoads', '#btn-PickAll', '.obs1', '.obs2', '.and']}
];


async function init()
{
	showPageoverlay('Initializing...');
	$('#btn-DrawRegion').prepend(POLYSVG);
	$('#btn-Prev').click(prevState);
	$('#btn-Next').click(nextState);
	$('#btn-Cancel').click(cancelEdits);
	$('#btn-Save').click(saveEdits);
	buildConfirmDelete();
	
	getStatusAjax().promise().done(doneStatus);
	let pObstypes = $.getJSON('obstypes.json').promise();
	let pLookups = $.ajax(
	{
		'url': 'api/dashboard/lookup',
		'dataType': 'json',
		'method': 'POST',
		'data': {'token': sessionStorage.token}
	}).promise();
	let pNetworks = getNetworksAjax().promise();
	let pProfile = getProfileAjax().promise();
	g_oMap = initCommonMap('mapid', -98.585522, 39.8333333, 4, 4, 24);

	$('#btn-DrawRegion').click(startRegion);
	$('#btn-PickRoads').click(startPickRoads);
	$('#btn-PickAll').click(pickAll);
	g_oDataTable = new DataTable('#tableid',
	{
		autoWidth: false,
		paging:false,
		searching:false,
		columnDefs: [{targets: [8], visible:false}, {targets: 1, className:'dt-body-left dt-head-center', sortable:false}, {targets: [0, 2, 3, 4, 5, 6, 7], className:'dt-body-center dt-head-center', sortable:false}],
		columns: [{title: `<button id="btn-AddNew" class="w3-button w3-green clickable dashboardtooltip" tip="Add New Alert"><i class="fa fa-plus"></i></button>`, width:'4%'}, {title: 'Alerts', width:'60%'}, {title: 'Until Next', width:'6%'}, {title: 'Affected Segments', width:'7%'}, {title: 'Total Segments', width:'7%'}, {title: 'Affected Linear Miles', width:'6%'}, {title: '', width:'5%'}, {title: '', width:'5%'}, {title: ''}],
		data: g_oTable
	});
	$('#tableid_info').hide();
	$('#btn-AddNew').on('click', function() 
	{
		if ($('#editctrls').css('display') === 'block')
			return;
		if (g_nCurrentIndex >= 0)
		{
			$(g_oDataTable.row(g_nCurrentIndex).node()).removeClass('w3-fhwa-navy');
			if (g_oMap.getLayer('geo-lines') !== undefined)
				g_oMap.removeLayer('geo-lines');
			g_nCurrentIndex = -1;
			g_sCurrentId = undefined;
			$('#chartid').css('display', 'none');
		}
		resetEdits();
		resetMap();
		showEditControls();
		WIZARDSTEP = -1;
		nextState();
	});
	$('.sorting_disabled').removeClass('sorting_asc');
	g_oDataTable.on('click', 'tbody tr', async function()
	{
		if ($('#editctrls').css('display') === 'block' || $('#dlgConfirmDelete').dialog('isOpen'))
			return;
		let nIndex = g_oDataTable.row(this).index();
		let sAlertId = g_oDataTable.data()[nIndex][8];
		
		g_oChart.data.datasets = [];
		g_oChart.options.scales.yAxes = [];
		g_oChart.options.title.text = 'No Chart';
		if (g_nCurrentIndex >= 0)
		{
			$(g_oDataTable.row(g_nCurrentIndex).node()).removeClass('w3-fhwa-navy');
			if (g_nCurrentIndex == nIndex)
			{
				if (g_oMap.getLayer('geo-lines') !== undefined)
					g_oMap.removeLayer('geo-lines');
				if (g_oMap.getLayer('poly-outline') !== undefined)
					g_oMap.removeLayer('poly-outline');
				g_nCurrentIndex = -1;
				g_sCurrentId = undefined;
				g_oChart.update();
				$('#chartid').css('display', 'none');
				return;
			}
		}
		let oStatus = g_oStatus[sAlertId];
		if (oStatus.sortstatus !== FULFILLED)
		{
			if (g_oMap.getLayer('geo-lines') !== undefined)
				g_oMap.removeLayer('geo-lines');
			g_nCurrentIndex = -1;
			g_sCurrentId = undefined;
			$('#chartid').css('display', 'none');
			return;
		}
			
		$(this).addClass('w3-fhwa-navy');
		g_nCurrentIndex = nIndex;
		g_sCurrentId = sAlertId;
		let oDataObject = g_oClickData[sAlertId];
		
		let pData = null;
		let bFailed = false;
		if (oDataObject === undefined)
		{
			showPageoverlay('Loading data...');
			pData = $.ajax(
			{
				'url': 'api/dashboard/data',
				'dataType': 'json',
				'method': 'POST',
				'data': {'token': sessionStorage.token, 'id': sAlertId}
			}).promise().fail(() => bFailed = true);
		}
		let oGeojson = g_oGeojsonFeatures[sAlertId];
		let pGeojson = null;
		if (oGeojson === undefined)
		{
			showPageoverlay('Loading data...');
			pGeojson = $.ajax(
			{
				'url': 'api/dashboard/geojson',
				'dataType': 'json',
				'method': 'POST',
				'data': {'token': sessionStorage.token, 'id': sAlertId}
			}).promise().fail(() => bFailed = true);
		}
		
		if (pData !== null)
		{
			oDataObject = await pData;
			g_oClickData[sAlertId] = oDataObject;
		}
		if (pGeojson !== null)
		{
			oGeojson = await pGeojson;
			g_oGeojsonFeatures[sAlertId] = oGeojson;
		}
		
		if (bFailed)
		{
			showPageoverlay('Failed to load data');
			timeoutPageoverlay();
			return;
		}
		
		let oSrc = g_oMap.getSource('geo-lines');
		oSrc._data.features = [];
		g_oImrcpIdLookup = {};
		let oFeatures = oGeojson.features[0]; // the features key points to an array of arrays. The first array is the segments, the second array, if it exists is the bounding polygon of the region
		for (let nIndex = 0; nIndex < oFeatures.length; nIndex++)
		{
			let oFeature = oFeatures[nIndex];
			getLineStringBoundingBox(oFeature);
			oFeature.id = nIndex;
			oFeature.source = 'geo-lines';
			g_oImrcpIdLookup[oFeature.properties.imrcpid] = nIndex;
			oSrc._data.features.push(oFeature);
		}
		oSrc.setData(oSrc._data);
		let oState = {'color': '#000'};
		for (let oFeature of oSrc._data.features.values())
			g_oMap.setFeatureState(oFeature, oState);
		
		if (g_oMap.getLayer('geo-lines') === undefined)
			addBefore(g_oGeoLines, 'road-number-shield');
		
		let nMinLon = Number.MAX_SAFE_INTEGER;
		let nMinLat = Number.MAX_SAFE_INTEGER;
		let nMaxLon = Number.MIN_SAFE_INTEGER;
		let nMaxLat = Number.MIN_SAFE_INTEGER;
		oState.color = oStatus.color;
		if (oDataObject.ids !== undefined)
		{
			let oSrcData = oSrc._data;
			if (oDataObject.ids.length === 0)
			{
				for (let oFeature of oSrcData.features.values())
				{
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
			}
			else
			{
				for (let sId of oDataObject.ids.values())
				{
					let oFeature = oSrcData.features[g_oImrcpIdLookup[sId]];
					g_oMap.setFeatureState(oFeature, oState);

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
			}
		}
		if (oSrc._data.features.length === 1)
		{
			let oFeature = oSrc._data.features[0];
			g_oMap.setFeatureState(oFeature, oState);
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
		
		removeSource('poly-outline', g_oMap);
		if (oGeojson.features.length > 1)
		{
			g_oMap.addSource('poly-outline', {'type': 'geojson', 'data': {'type': 'Feature', 'geometry': {'type': 'LineString', 'coordinates': oGeojson.features[1][0].geometry.coordinates}}});
			g_oMap.addLayer(g_oLayers['poly-outline']);
			for (let oCoord of oGeojson.features[1][0].geometry.coordinates.values())
			{
				if (oCoord[0] < nMinLon)
					nMinLon = oCoord[0];
				if (oCoord[1] < nMinLat)
					nMinLat = oCoord[1];
				if (oCoord[0] > nMaxLon)
					nMaxLon = oCoord[0];
				if (oCoord[1] > nMaxLat)
					nMaxLat = oCoord[1];
			}
		}
		
		if (nMinLon != Number.MAX_SAFE_INTEGER)
		{
			g_oMap.fitBounds([[nMinLon, nMinLat], [nMaxLon, nMaxLat]], {'padding': 0, 'linear': false, 'duration': 0});
			if (oDataObject.ids === undefined)
				g_oMap.setZoom(g_oMap.getZoom() - 1.5);
			else
				g_oMap.setZoom(g_oMap.getZoom() - 0.5);
		}
		updateChart(oDataObject, oStatus);
		timeoutPageoverlay();
	});
	g_oDataTable.on('click', 'tbody tr .pointer', async function()
	{
		if (this.classList.contains('fa-trash'))
		{
			let oRow = g_oDataTable.row($(this).parent());
			let sId = g_oDataTable.data()[oRow.index()][8];
			g_sDeleteId = sId;
			$('#dlgConfirmDelete').dialog('open');
			return false;
		}
		else if (this.classList.contains('fa-edit'))
		{
			let oRow = g_oDataTable.row($(this).parent());
			let nIndex = oRow.index();
			let sAlertId = g_oDataTable.data()[nIndex][8];
			
			let bUpdateMap = true;
			if (g_nCurrentIndex >= 0)
			{
				$(g_oDataTable.row(g_nCurrentIndex).node()).removeClass('w3-fhwa-navy');
				bUpdateMap = g_nCurrentIndex !== nIndex;
			}
			g_nCurrentIndex = nIndex;
			$(g_oDataTable.row(nIndex).node()).addClass('w3-fhwa-navy');
			
			showPageoverlay('Loading data...');
			if (bUpdateMap)
			{
				let oGeojson = g_oGeojsonFeatures[sAlertId];
				let pGeojson = null;
				if (oGeojson === undefined)
				{
					pGeojson = $.ajax(
					{
						'url': 'api/dashboard/geojson',
						'dataType': 'json',
						'method': 'POST',
						'data': {'token': sessionStorage.token, 'id': sAlertId}
					}).promise().fail(() => bFailed = true);
				}
				
				if (pGeojson !== null)
				{
					oGeojson = await pGeojson;
					g_oGeojsonFeatures[sAlertId] = oGeojson;
				}
				
				let nMinLon = Number.MAX_SAFE_INTEGER;
				let nMinLat = Number.MAX_SAFE_INTEGER;
				let nMaxLon = Number.MIN_SAFE_INTEGER;
				let nMaxLat = Number.MIN_SAFE_INTEGER;
				let oSrc = g_oMap.getSource('geo-lines');
				oSrc._data.features = [];
				g_oImrcpIdLookup = {};
				let oFeatures = oGeojson.features[0]; // the features key points to an array of arrays. The first array is the segments, the second array, if it exists is the bounding polygon of the region
				for (let nIndex = 0; nIndex < oFeatures.length; nIndex++)
				{
					let oFeature = oFeatures[nIndex];
					getLineStringBoundingBox(oFeature);
					oFeature.id = nIndex;
					oFeature.source = 'geo-lines';
					g_oImrcpIdLookup[oFeature.properties.imrcpid] = nIndex;
					oSrc._data.features.push(oFeature);
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
				oSrc.setData(oSrc._data);
				let oState = {'color': '#000'};
				for (let oFeature of oSrc._data.features.values())
					g_oMap.setFeatureState(oFeature, oState);

				if (g_oMap.getLayer('geo-lines') === undefined)
					addBefore(g_oGeoLines, 'road-number-shield');
				
				if (oGeojson.features.length > 1)
				{
					removeSource('poly-outline', g_oMap);
					g_oMap.addSource('poly-outline', {'type': 'geojson', 'data': {'type': 'Feature', 'geometry': {'type': 'LineString', 'coordinates': oGeojson.features[1][0].geometry.coordinates}}});
					g_oMap.addLayer(g_oLayers['poly-outline']);
				}

				if (nMinLon != Number.MAX_SAFE_INTEGER)
				{
					g_oMap.fitBounds([[nMinLon, nMinLat], [nMaxLon, nMaxLat]], {'padding': 0, 'linear': false, 'duration': 0});
				}
			}
			let bFail = false;
			let pAlert = $.ajax(
			{
				'url': 'api/dashboard/parameters',
				'dataType': 'json',
				'method': 'POST',
				'data': {'token': sessionStorage.token, 'id': sAlertId}
			}).fail(() => bFail = true);
						
			resetEdits();
			let oAlert = (await pAlert).alert;
			if (bFail)
			{
				showPageoverlay('Failed loading data...');
				$(g_oDataTable.row(nIndex).node()).addClass('w3-fhwa-navy');
				resetMap();
				g_nCurrentIndex = -1;
				return false;
			}
			
			enableEdits(oAlert);
			showEditControls();
			timeoutPageoverlay();
		}
		return false;
	});
	g_oObsLookups = await pLookups;
	g_oObstypes = await pObstypes;
	
	let sOptions = `<option value ='-1'>Select an obstype...</option>`;
	for (let oObs of Object.entries(g_oObstypes))
		sOptions += `<option value='${oObs[0]}'>${oObs[1].name}${oObs[1].unit ? ' (' + oObs[1].unit + ')' : ''}, ${oObs[1].desc}</option>`;
	
	$('.obstypes').on('change', updateSelects).append(sOptions);
	
	sOptions = `<option value ='-1'>Select a comparsion...</option>`;
	for (let oComp of [['lt', '&lt;'], ['eq', '='], ['gt', '&gt;']].values())
		sOptions += `<option value='${oComp[0]}'>${oComp[1]}</option>`;

	$('.comps').append(sOptions);	

	let oCanvas = $('#chartid')[0];
	let oCtx = oCanvas.getContext('2d');

	g_oChart = new Chart(oCtx,
	{
		type: 'line',
		data: {datasets: []},
		options:
		{
			maintainAspectRatio: false,
			tooltips: {callbacks: {label: function(tooltipItem, data)
					{
						let oCur = g_oClickData[g_sCurrentId];
						if (oCur === undefined)
							return '';
						let sObsType = g_oClickData[g_sCurrentId].obstype[tooltipItem.datasetIndex].toUpperCase();
						let sGraphVal = data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index].y;
						if (g_oObsLookups[sObsType])
						{
							let aLookup = g_oObsLookups[sObsType];
							let sVal = 'unknown';
							for (let nIndex = 0; nIndex < aLookup.length; nIndex++)
							{
								if (aLookup[nIndex][0] == sGraphVal)
								{
									sVal = aLookup[nIndex][1];
									break;
								}
							}
							return `${data.datasets[tooltipItem.datasetIndex].label}: ${data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index].y} (${sVal})`;
						}
						return `${data.datasets[tooltipItem.datasetIndex].label}: ${data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index].y}`;
					}}},
			title: {display: true, text: 'Title'},
			legend: {display: false, position: 'right', align: 'end'},
			scales:{
				xAxes:
					[
						{
							scaleLabel: {display: false, labelString: 'time'},
							type: 'time',
							time: {unit: 'hour', tooltipFormat: 'MM-DD hh:mm a', displayFormats: {hour: 'MM-DD hh:mm a'}},
							ticks: {source: 'auto', maxRotation: 0, autoSkip:true}
						}
					]
			}
		}
	});
	
	let oAllNetworks = await pNetworks;
	g_oProfile = await pProfile;
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
		
		if (nDisplayStatus !== 3)
			continue;
		oNetwork.properties.displaystatus = nDisplayStatus;
		for (let oProfileNetwork of g_oProfile.networks.values())
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
	ctx.clearRect(0, 0, oCan.width, oCan.height);
	ctx.fillStyle = '#fff';
	ctx.arc(12, 12, 10, 0, 2 * Math.PI);
	ctx.fill();
	ctx.fillStyle = '#b2000c';
	ctx.fillText('\uf05e', 12, 12);
	addStyleRule('.bancursor', `cursor: url('${oCan.toDataURL('image/png')}') 12 12, auto`);

	g_oMap.on('load', async function() 
	{
		g_oMap.addSource('network-polygons', oNetworks);
		g_oMap.addSource('geo-lines', {'type': 'geojson', 'data': {'type': 'FeatureCollection', 'features': []}, 'generateId': true});
		timeoutPageoverlay(0);
	});
	
}


function enableEdits(oAlert)
{
	$('#instructiontxt').html(`Click <i class="fa fa-save"></i> save changes`);
	$('#btn-Next,#btn-Prev,#btn-DrawRegion,#btn-PickRoads,#btn-PickAll').prop('disabled', true);
	$('#alertLabel,.obs1,.obs2,.and,#btn-Save,#btn-Cancel').prop('disabled', false);
	$('.and').on('change', updateObsTwo);
	$('#btn-Cancel').on('click', disableEdits);
	g_sEditId = oAlert.id;
	for (let nIndex = 0; nIndex < oAlert.obstypes.length; nIndex++)
	{
		let sClass = `.obs${nIndex + 1}`;
		$(sClass + '.obstypes').val(oAlert.obstypes[nIndex]);
		$(sClass + '.comps').val(oAlert.comps[nIndex]);
		let sObstype = oAlert.obstypes[nIndex].toString(36).toUpperCase();
		if (g_oObsLookups[sObstype])
		{
			let sOptions = `<option value='-1'>Select a value...</option>`;
			if (g_oObsLookups[sObstype])
			{
				for (let aLookup of g_oObsLookups[sObstype])
					sOptions += `<option value='${aLookup[0]}'>${aLookup[1]}</option>`;
			}
			$(sClass + '.values').val('').css('display', 'none');
			$(sClass + '.lookups').append(sOptions).val(oAlert.vals[nIndex]).css('display', 'block');
			
		}
		else
		{
			$(sClass + '.values').val(oAlert.vals[nIndex]).css('display', 'block');
			$(sClass + '.lookups').val(-1).css('display', 'none');
		}
	}
	
	if (oAlert.obstypes.length === 1)
	{
		$('input[name="and"]').val(['none']);
		$('.obs2.obstypes').children().first().html('');
		$('.obs2.comps').children().first().html('');
		$('.obs2.values').prop('placeholder', '');
		$('.obs2').prop('disabled', true);
	}
	else
	{
		if (oAlert.and)
			$('input[name="and"]').val(['and']);
		else
			$('input[name="and"]').val(['or']);
	}
		
	$('.obs1').removeClass('redhighlight');
	$('.obs2').removeClass('redhighlight');
	$('#alertLabel').removeClass('redhighlight').val(oAlert.label);
}


function disableEdits()
{
	$('.and').off('change', updateObsTwo);
	$('#btn-Cancel').off('click', disableEdits);
	g_sEditId = undefined;
}


function validateAll(sPopupTarget)
{
	return validateObsOne(sPopupTarget) && validateObsTwo(sPopupTarget) && validateLabel(sPopupTarget);
}


function updateObsTwo()
{
	if ($('input[name="and"]:checked').val() === 'none')
	{
		$('.obs2.obstypes').val(-1).children().first().html('');
		$('.obs2.comps').val(-1).children().first().html('');
		$('.obs2.values').val('').css('display', 'block').prop('placeholder', '');
		$('.obs2.lookups').val(-1).css('display', 'none').children().first().html('');
		$('.obs2').removeClass('redhighlight');
		$('.obs2').prop('disabled', true);
	}
	else
	{
		$('.obs2.obstypes').children().first().html('Select an obstype...');
		$('.obs2.comps').children().first().html('Select a comparsion...');
		$('.obs2.values').prop('placeholder', 'Value to compare');
		$('.obs2').prop('disabled', false);
	}
}

function doDelete()
{
	for (let nIndex = 0; nIndex < g_oTable.length; nIndex++)
	{
		if (g_oTable[0][8] === g_sDeleteId)
		{
			g_oTable.splice(nIndex, 1);
			break;
		}
	}
	if (g_sDeleteId === g_sCurrentId)
	{
		if (g_oMap.getLayer('geo-lines') !== undefined)
			g_oMap.removeLayer('geo-lines');
		$('#chartid').css('display', 'none');
	}
	
	delete g_oStatus[g_sDeleteId];
	delete g_oClickData[g_sDeleteId];
	$.ajax(
	{
		'url': 'api/dashboard/delete',
		'dataType': 'json',
		'method': 'POST',
		'data': {'token': sessionStorage.token, 'id': g_sDeleteId}
	}).done(doneDelete);
	g_sDeleteId = undefined;
}

function addBefore(oLayer, sBeforeId)
{
	if (g_oMap.getLayer(sBeforeId) !== undefined)
		g_oMap.addLayer(oLayer, sBeforeId);
	else
		g_oMap.addLayer(oLayer);
}


function pickAll()
{
	let oGeometry;
	for (let oNetwork of g_oMap.getSource('network-polygons')._data.features.values())
	{
		if (oNetwork.properties.networkid === g_sSelectedNetwork)
		{
			oGeometry = oNetwork.geometry.coordinates[0]; // get outer ring of network polygon
		}
	}
	let sData = '';
	for (let nIndex = 0; nIndex < oGeometry.length - 1; nIndex++)
	{
		let aCoord = oGeometry[nIndex];
		sData += aCoord[0].toFixed(7) + ',' + aCoord[1].toFixed(7) + ',';
	}
	
	removeSource('poly-outline', g_oMap);
	g_oMap.addSource('poly-outline', {'type': 'geojson', 'data': {'type': 'Feature', 'geometry': {'type': 'LineString', 'coordinates': oGeometry}}});
	g_oMap.addLayer(g_oLayers['poly-outline']);
	g_sCoordString = sData.substring(0, sData.length - 1);
	nextState();
	nextState();
}

function startPolygon()
{
	nextState();
	$('#instructiontxt').html('Left-click: Initial point. Esc: Cancel');
}

function initPolygon()
{
	$('#instructiontxt').html('Left-click: Add point. Esc: Cancel');
}


function movePolygon(oEvent)
{
	if (!this.bCanAdd)
		addPopup($('#instructiontxt'), 'Polygon cannot self intersect');
}


function addPointPolygon(oEvent)
{
	if (this.map.getSource('poly-outline')._data.geometry.coordinates.length  > 2)
		$('#instructiontxt').html('Left-click: Add point. Left-click initial point: Finish polygon. Esc: Cancel');
}


function finishPolygon(oGeometry)
{
	let sData = '';
	for (let nIndex = 0; nIndex < oGeometry.coordinates.length - 1; nIndex++)
	{
		let aCoord = oGeometry.coordinates[nIndex];
		sData += aCoord[0].toFixed(7) + ',' + aCoord[1].toFixed(7) + ',';
	}

	g_sCoordString = sData.substring(0, sData.length - 1);
	nextState();
}


function cancelPolygon()
{
	g_sCoordString = undefined;
	prevState();
}


function startRegion()
{
	g_oSegments = {};
	let oSrc = g_oMap.getSource('geo-lines');
	for (let oFeature of oSrc._data.features.values())
		g_oMap.setFeatureState(oFeature, {'include': false, 'color': '#000'});
	$(g_oMap.getCanvas()).removeClass('clickable');
	startDrawPoly.bind(
	{
		'map': g_oMap,
		'startDraw': startPolygon,
		'initPoly': initPolygon,
		'movePoly': movePolygon,
		'addPoint': addPointPolygon,
		'finishDraw': finishPolygon,
		'cancelDraw': cancelPolygon
	}).call();
}


function startPickRoads()
{
	g_sCoordString = undefined;
	nextState();
	$('#btn-Next').prop('disabled', false);
	$('#instructiontxt').html('Left-click roads to add/remove from selection. Click <i class="fa fa-arrow-circle-right"></i> to continue');
	g_oMap.on('click', toggleInclude);
	g_oMap.on('mouseenter', 'geo-lines', hoverHighlight);
}


function doneStatus(oData, sTextStatus, oJqXHR)
{
	let oStatus = oData;
	let oFulfilled = [];
	for (let [sId, oAlertStatus] of Object.entries(oStatus))
	{
		if (g_oStatus[sId] === undefined)
		{
			oAlertStatus.color = '#AAA';
			oAlertStatus.in = MAXLONG;
			oAlertStatus.count = 0;
			oAlertStatus.id = sId;
			oAlertStatus.sortstatus = oAlertStatus.status;
			oAlertStatus.miles = 0;
			g_oStatus[sId] = oAlertStatus;
			if (oAlertStatus.status === FULFILLED)
			{
				oFulfilled.push(oAlertStatus);
			}
			else if (oAlertStatus.status === STALE)
			{
				g_oQueue.push(oAlertStatus);
			}
		}
		else
		{
			let oCur = g_oStatus[sId];
			oCur.name = oAlertStatus.name; // label can change by edit so make sure we have the updated label
			if (oCur.updated !== oAlertStatus.updated)
			{
				oCur.updated = oAlertStatus.updated;
				oCur.status = oAlertStatus.status;
				if (oAlertStatus.status === FULFILLED)
					oFulfilled.push(oCur);
				else if (oAlertStatus.status === STALE)
				{
					g_oQueue.push(oCur);
				}
			}
		}
	}

	redrawTable();
	for (let oAlert of oFulfilled.values())
	{
		fulfillAlert(oAlert.id);
	}
	while (g_oQueue.length > 0)
	{
		let oToQueue = g_oQueue.shift();
		if (oToQueue.status === STALE)
		{
			queueAlert(oToQueue.id);
		}
	}

	POLLTIMEOUT = setTimeout(function()
	{
		getStatusAjax().done(doneStatus);
	}, POLLINTERVAL);

}


function fulfillAlert(sId)
{
	$.ajax(
	{
		'url': 'api/dashboard/fulfill',
		'dataType': 'json',
		'method': 'POST',
		'alertid': sId,
		'data': {'token': sessionStorage.token, 'id': sId}
	}).done(doneFulfill);
}


function doneFulfill(oData, sTextStatus, oJqXHR)
{
	let oObject = g_oStatus[this.alertid];
	oObject.color = oData.color;
	oObject.in = oData.in;
	oObject.count = oData.count;
	oObject.sortstatus = FULFILLED;
	oObject.miles = oData.miles;
	g_oClickData[this.alertid] = undefined;
	redrawTable();
	if (g_sCurrentId === this.alertid)
	{
		updateCurrent(this.alertid);
	}
}


async function updateCurrent(sAlertId)
{
	let bFailed = false;
	let pData = $.ajax(
	{
		'url': 'api/dashboard/data',
		'dataType': 'json',
		'method': 'POST',
		'data': {'token': sessionStorage.token, 'id': sAlertId}
	}).promise().fail(() => bFailed = true);

	let oDataObject = await pData;
	if (bFailed)
	{
		$(g_oDataTable.row(g_nCurrentIndex).node()).removeClass('w3-fhwa-navy');
		if (g_oMap.getLayer('geo-lines') !== undefined)
			g_oMap.removeLayer('geo-lines');
		if (g_oMap.getLayer('poly-outline') !== undefined)
			g_oMap.removeLayer('poly-outline');
		g_nCurrentIndex = -1;
		g_sCurrentId = undefined;
		$('#chartid').css('display', 'none');
		return;
	}
	g_oClickData[sAlertId] = oDataObject;
	let oStatus = g_oStatus[sAlertId];
	let oState = {'color': '#000'};
	let oSrc = g_oMap.getSource('geo-lines');
	for (let oFeature of oSrc._data.features.values())
		g_oMap.setFeatureState(oFeature, oState);

	if (g_oMap.getLayer('geo-lines') === undefined)
		addBefore(g_oGeoLines, 'road-number-shield');

	oState.color = oStatus.color;
	if (oDataObject.ids !== undefined)
	{
		let oSrcData = oSrc._data;
		if (oDataObject.ids.length > 0)
		{
			for (let sId of oDataObject.ids.values())
			{
				let oFeature = oSrcData.features[g_oImrcpIdLookup[sId]];
				g_oMap.setFeatureState(oFeature, oState);
			}
		}
	}
	if (oSrc._data.features.length === 1)
	{
		let oFeature = oSrc._data.features[0];
		g_oMap.setFeatureState(oFeature, oState);
	}
	removeSource('poly-outline', g_oMap);
	if (oDataObject.geometry !== undefined)
	{
		let oFirst = oDataObject.geometry[0];
		let oLast = oDataObject.geometry[oDataObject.geometry.length - 1];
		if (oFirst[0] !== oLast[0] || oFirst[1] !== oLast[1])
			oDataObject.geometry.push(oFirst);
		g_oMap.addSource('poly-outline', {'type': 'geojson', 'data': {'type': 'Feature', 'geometry': {'type': 'LineString', 'coordinates': oDataObject.geometry}}});
		g_oMap.addLayer(g_oLayers['poly-outline']);
	}
	updateChart(oDataObject, oStatus);
}


function getInString(lTs, nCount)
{
	if (lTs === MAXLONG || nCount === 0)
		return '';
	let oNow = moment();
	let oTs = moment(lTs);
	if (oTs.diff(oNow) < 0)
		return '0m';
	let oDiffHrs = oTs.diff(oNow, 'hours', true);
	if (oDiffHrs > 24)
		return `${oTs.diff(oNow, 'days')}d`;
	
	if (oDiffHrs > 1)
		return `${oTs.diff(oNow, 'hours')}h`;
	
	return `${Math.round(oTs.diff(oNow, 'minutes', true))}m`;
}


function doneDelete(oData, sTextStatus, oJqXHR)
{
	
}


function queueAlert(sId)
{
	$.ajax(
	{
		'url': 'api/dashboard/queue',
		'dataType': 'json',
		'method': 'POST',
		'alertid': sId,
		'data': {'token': sessionStorage.token, 'id': sId}
	}).done(doneQueue);
}

function doneQueue(oData, sTextStatus, oJqXHR)
{
	
}


function doneAdd(oData, sTextStatus, oJqXHR)
{
	if (POLLTIMEOUT !== undefined)
	{
		clearTimeout(POLLTIMEOUT);
		POLLTIMEOUT = undefined;
		getStatusAjax().done(doneStatus);
	}
	showPageoverlay(oData.msg);
	timeoutPageoverlay(1000);
	cancelEdits();
}


function doneGeojson(oData, sTextStatus, oJqXHR)
{
	let oFeatures = oData.features;
	let sId = oData.id;
	g_oGeojsonFeatures[sId] = oFeatures;
	
}


function switchGeojson(sId)
{
	if (g_oGeojsonFeatures[sId] === undefined)
	{
		$.ajax(
		{
			'url': 'api/dashboard/geojson',
			'dataType': 'json',
			'method': 'POST',
			'data': {'token': sessionStorage.token, 'id': sId}
		}).done(doneGeojson);
		return;
	}
	let oSrc = g_oMap.getSource('geo-lines');
	let oData = oSrc._data;
	oData.features = [];
	g_oImrcpIdLookup = {};
	let oFeatures = g_oGeojsonFeatures[sId];
	for (let nIndex = 0; nIndex < oFeatures.length; nIndex++)
	{
		let oFeature = oFeatures[nIndex];
		getLineStringBoundingBox(oFeature);
		oFeature.id = nIndex;
		oFeature.source = 'geo-lines';
		g_oImrcpIdLookup[oFeature.properties.imrcpid] = nIndex;
		oData.features.push(oFeature);
	}
	oSrc.setData(oData);
	for (let oFeature of oSrc._data.features.values())
		g_oMap.setFeatureState(oFeature, g_oDefaultState);
}

function showEditControls()
{
	$('#chartid').css('display', 'none');
	$('#editctrls').css('display', 'block');
	$('#tableoverlay').css('display', 'block');
}


function nextState()
{
	if (validateInput())
	{
		++WIZARDSTEP;
		updateState();
	}
}

//[
//	{'ins': 'Follow the prompts to create a new alert for your dashboard', 'enabled': ['#btn-Next', '#btn-Cancel'], 'disabled': ['#alertLabel', '#btn-Prev', '#btn-DrawRegion', '#btn-PickRoads', '#btn-Save', '.obs1', '.obs2']},
//	{'ins': 'Select a network on the map', 'enabled':['#btn-Cancel'], 'disabled':['#alertLabel', '#btn-Prev', '#btn-Next', '#btn-DrawRegion', '#btn-PickRoads', '#btn-Save', '.obs1', '.obs2']},
//	{'ins': 'Wait for network to load', 'enabled':[], 'disabled':['#btn-Cancel','#alertLabel', '#btn-Prev', '#btn-Next', '#btn-DrawRegion', '#btn-PickRoads', '#btn-Save', '.obs1', '.obs2']},
//	{'ins': `Click ${POLYSVG} to select a region or <i class="fa fa-road"></i> to select roads`, 'enabled':['#btn-DrawRegion', '#btn-PickRoads', '#btn-Prev', '#btn-Cancel'], 'disabled':['#btn-Next', '#alertLabel', '#btn-Save', '.obs1', '.obs2']},
//	{'ins': ``, 'enabled':['#btn-Cancel'], 'disabled':['#btn-DrawRegion', '#btn-PickRoads', '#btn-Prev', '#alertLabel', '#btn-Next', '#btn-Save', '.obs1', '.obs2'], 'map': true},
//	{'ins': `Select the obstype, comparsion, and value for the first rule`, 'enabled':['#btn-Prev', '#btn-Next', '.obs1', '#btn-Cancel'], 'disabled':['#btn-DrawRegion', '#btn-PickRoads', '#alertLabel', '#btn-Save', '.obs2']},
//	{'ins': `Enter a label for this alert`, 'enabled':['#btn-Prev', '#btn-Next', '#alertLabel', '#btn-Cancel'], 'disabled':['#btn-DrawRegion', '.obs1', '#btn-PickRoads', '#btn-Save', '.obs2']},
//	{'ins': `Click <i class="fa fa-save"></i> to finish, or <i class="fa fa-arrow-circle-right"></i> to configure another rule`, 'enabled':['#btn-Prev', '#alertLabel', '#btn-Next', '#btn-Cancel', '#btn-Save'], 'disabled':['#btn-DrawRegion', '#btn-PickRoads', '.obs1', '.obs2']},
//	{'ins': `Select the logical operator, obstype, comparsion, and value for the second rule`, 'enabled':['#btn-Prev', '#btn-Next', '.obs2', '#btn-Cancel', '#alertLabel'], 'disabled':['#btn-DrawRegion', '#btn-PickRoads', '#btn-Save', '.obs1']},
//	{'ins': `Click <i class="fa fa-save"></i> to finish`, 'enabled':['#btn-Prev', '#alertLabel', '#btn-Cancel', '#btn-Save'], 'disabled':['#btn-DrawRegion', '#btn-Next', '#btn-PickRoads', '.obs1', '.obs2']}
//];
function prevState()
{
	if (WIZARDSTEP === 3 || WIZARDSTEP === 5 || (WIZARDSTEP == 8 && $('input[name="and"]:checked').val() === 'none'))
		--WIZARDSTEP;
	--WIZARDSTEP;
	updateState();
}

function updateState()
{
	let oState = WIZARD[WIZARDSTEP];
	$('#instructiontxt').html(oState.ins);
	if (oState.enabled)
	{
		for (let sEle of oState.enabled.values())
			$(sEle).prop('disabled', false);
	}

	if (oState.disabled)
	{
		for (let sEle of oState.disabled.values())
			$(sEle).prop('disabled', true);
	}

	g_oMap.off('mouseenter', 'geo-lines', hoverHighlight);
	g_oMap.off('click', 'geo-lines', toggleInclude);
	g_oMap.off('mouseenter', 'network-polygons-report', hoverHighlight);
	g_oMap.off('click', 'network-polygons-report', loadANetwork);
	switch (WIZARDSTEP)
	{
		case 1: // select network
		{
			selectANetwork();
			break;
		}
		case 3: // select region or road
		{
			if (g_nNetworks === 1)
				$('#btn-Prev').prop('disabled', true);
			removeSource('poly-outline', g_oMap);
			removeSource('poly-bounds', g_oMap);
			break;
		}
		case 7: // second obstype
		{
			if ($('input[name="and"]:checked').val() === 'none') // skip if no second obstype
			{
				$('.obs2.obstypes').children().first().html('');
				$('.obs2.comps').children().first().html('');
				$('.obs2.values').prop('placeholder', '');
				nextState();
			}
			else
			{
				$('.obs2.obstypes').children().first().html('Select an obstype...');
				$('.obs2.comps').children().first().html('Select a comparsion...');
				$('.obs2.values').prop('placeholder', 'Value to compare');
			}
			break;
		}
	}
}


function validateInput()
{
	switch (WIZARDSTEP)
	{
		case 4: // check if at least one segment is selected
		{
			if (g_sCoordString === undefined && Object.keys(g_oSegments).length === 0)
			{
				addPopup($('#btn-Next'), 'Must select at least 1 road');
				return false;
			}
			break;
		}
		case 5:
		{			
			if (!validateObsOne())
				return false;
			
			break;
		}
		
		case 7:
		{			
			if (!validateObsTwo())
				return false;
			break;
		}
		case 8:
		{
			if (!validateLabel())
				return false;
			break;
		}
	}
	
	return true;
}


function validateObsOne(sPopupTarget = '#btn-Next')
{
	if ($('.obs1.obstypes').val() == -1)
	{
		addPopup($(sPopupTarget), 'Must select an obstype');
		$('.obs1.obstypes').addClass('redhighlight');
		return false;
	}

	$('.obs1').removeClass('redhighlight');
	if ($('.obs1.comps').val() == -1)
	{
		addPopup($(sPopupTarget), 'Must select a comparison');
		$('.obs1.comps').addClass('redhighlight');
		return false;
	}

	$('.obs1').removeClass('redhighlight');
	if ($('.obs1.values').css('display') == 'block' && !validNumber($('.obs1.values').val()))
	{
		addPopup($(sPopupTarget), 'Must enter a valid number');
		$('.obs1.values').addClass('redhighlight');
		return false;
	}

	$('.obs1').removeClass('redhighlight');
	if ($('.obs1.lookups').css('display') == 'block' && $('.obs1.lookups').val() == -1)
	{
		addPopup($(sPopupTarget), 'Must select a value');
		$('.obs1.lookups').addClass('redhighlight');
		return false;
	}
	
	return true;
}


function validateObsTwo(sPopupTarget = '#btn-Next')
{
	if ($('input[name="and"]:checked').val() === 'none')
		return true;

	if ($('.obs2.obstypes').val() == -1)
	{
		addPopup($(sPopupTarget), 'Must select an obstype');
		$('.obs2.obstypes').addClass('redhighlight');
		return false;
	}

	$('.obs2').removeClass('redhighlight');
	if ($('.obs2.comps').val() == -1)
	{
		addPopup($(sPopupTarget), 'Must select a comparison');
		$('.obs2.comps').addClass('redhighlight');
		return false;
	}

	$('.obs2').removeClass('redhighlight');
	if ($('.obs2.values').css('display') == 'block' && !validNumber($('.obs2.values').val()))
	{
		addPopup($(sPopupTarget), 'Must enter a valid number');
		$('.obs2.values').addClass('redhighlight');
		return false;
	}

	$('.obs2').removeClass('redhighlight');
	if ($('.obs2.lookups').css('display') == 'block' && $('.obs2.lookups').val() == -1)
	{
		addPopup($(sPopupTarget), 'Must select a value');
		$('.obs2.lookups').addClass('redhighlight');
		return false;
	}
	
	return true;
}

function validateLabel(sPopupTarget = '#btn-Next')
{
	if (!validText($('#alertLabel').val()))
	{
		addPopup($(sPopupTarget), 'Invalid input');
		$('#alertLabel').addClass('redhighlight');
		return false;
	}
	$('#alertLabel').removeClass('redhighlight');
	
	return true;
}


function validText(sTxt)
{
	return sTxt.length > 0 && /^[ a-zA-Z0-9\-_@\.]+$/.exec(sTxt) != null;
}

function validNumber(sTxt)
{
	return /^[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)$/.exec(sTxt) != null;
}

function timeoutMapoverlay(nMillis = 1500)
{
	window.setTimeout(function()
	{
		$('#mapoverlay p').html('');
		$('#mapoverlay').hide();
	}, nMillis);
}


function showMapoverlay(sContents)
{
	$('#mapoverlay p').html(sContents);
	$('#mapoverlay').css({'opacity': 0.5, 'font-size': 'x-large'}).show();
}

function selectANetwork()
{
	g_sSelectedNetwork = undefined;
	if (g_oMap.getLayer('network-polygons-report') === undefined)
		g_oMap.addLayer(g_oLayers['network-polygons-report']);
	
	if (g_oMap.getLayer('geo-lines') !== undefined)
		g_oMap.removeLayer('geo-lines');
	g_oMap.fitBounds(g_oNetworksBoundingBox, {'padding': 50});
	if (g_nNetworks === 1)
	{
		loadANetwork(g_oMap.getSource('network-polygons')._data);
	}
	else
	{
		g_oMap.on('mouseenter', 'network-polygons-report', hoverHighlight);
		g_oMap.on('click', 'network-polygons-report', loadANetwork);
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
	
	g_sSelectedNetwork = oNetwork.properties.networkid;
	if (g_oNetworkFeatures[oNetwork.properties.networkid] !== undefined)
	{
		let oFeatures = g_oNetworkFeatures[oNetwork.properties.networkid];
		g_oImrcpIdLookup = {};
		for (let oFeature of oFeatures.values())
			g_oImrcpIdLookup[oFeature.properties.imrcpid] = oFeature.id;
		
		let oSrc = g_oMap.getSource('geo-lines');
		oSrc._data.features = oFeatures;
		oSrc.setData(oSrc._data);
		if (g_oMap.getLayer('network-polygons-report') !== undefined)
			g_oMap.removeLayer('network-polygons-report');
		if (g_oMap.getLayer('geo-lines') === undefined)
			addBefore(g_oGeoLines, 'road-number-shield');
		finishedStyling();
	}
	else
	{
		showMapoverlay(`Loading network: ${oNetwork.properties.label}`);
		$.ajax(
		{
			'url': 'api/generatenetwork/geo',
			'dataType': 'json',
			'method': 'POST',
			'networkid': oNetwork.properties.networkid,
			'data': {'token': sessionStorage.token, 'networkid': oNetwork.properties.networkid, 'published': 'true'}
		}).done(geoSuccess).fail(function() 
		{
			showMapoverlay(`Failed to retrieve network: "${oNetwork.properties.label}"<br>Try again later.`);
			if (g_nNetworks > 1)
				selectANetwork();
		});
	}
	nextState();
}


function finishedStyling(oEvent)
{
	let oFeatures = g_oMap.getSource('geo-lines')._data.features;
	for (let nIndex = 0; nIndex < oFeatures.length; nIndex++) 
		g_oMap.setFeatureState({'id': nIndex, 'source': 'geo-lines'}, {'preview': false, 'include': false, 'color': '#000'});
	timeoutMapoverlay(1000);

	if (g_oMap.getLayer('network-polygons-report') !== undefined)
		g_oMap.removeLayer('network-polygons-report');

	g_oMap.off('sourcedata', finishedStyling);
	nextState();
}

function geoSuccess(oData, sStatus, oJqXHR)
{
	g_oImrcpIdLookup = {};
	let oLineData = g_oMap.getSource('geo-lines')._data;
	let oFeatures = [];
	let nCount = 0;
	for (let oFeature of oData.values())
	{
		getLineStringBoundingBox(oFeature);
		oFeatures.push(oFeature);
		oFeature.id = nCount++;
		g_oImrcpIdLookup[oFeature.properties.imrcpid] = oFeature.id;
		oFeature.source = 'geo-lines';
	}
	oLineData.features = oFeatures;
	g_oMap.getSource('geo-lines').setData(oLineData);
	g_oMap.on('sourcedata', finishedStyling);
	if (g_oMap.getLayer('geo-lines') === undefined)
		addBefore(g_oGeoLines, 'road-number-shield');
	$(g_oMap.getCanvas()).removeClass('clickable');
	g_oNetworkFeatures[this.networkid] = oFeatures;
}

function hoverHighlight(oEvent)
{
	if (oEvent.features.length > 0)
	{
		let oFeature = oEvent.features[0];
		let bInclude = g_oMap.getFeatureState(oFeature).include;

		if (bInclude === undefined || oFeature.layer.id === 'network-polygons-report')
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
		let bBanned = false;
		if (oHover !== undefined)
		{
			g_oMap.setFeatureState(oHover, {'hover': false});
			$(g_oMap.getCanvas()).removeClass('clickable minuscursor pluscursor bancursor');
		}
		let bInclude = g_oMap.getFeatureState(oFeature).include;
		if (bInclude === undefined || oFeature.layer.id === 'network-polygons-report')
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
	$(g_oMap.getCanvas()).removeClass('clickable minuscursor pluscursor bancursor');
}


function toggleInclude(oEvent)
{
	let oFeatures = g_oMap.queryRenderedFeatures(pointToPaddedBounds(oEvent.point), {'layers': ['geo-lines']});
	if (oFeatures.length > 0)
	{
		let oTemp;
		for (let oFeature of oFeatures.values())
		{
			if (oFeature.id === g_oHovers['geo-lines'].id)
			{
				oTemp = oFeature;
				break;
			}
		}

		let sColor = '#000';
		if (g_oMap.getFeatureState(oTemp).include)
		{
			$(g_oMap.getCanvas()).removeClass('minuscursor').addClass('pluscursor');
			delete g_oSegments[oTemp.properties.imrcpid];

		}
		else
		{
			$(g_oMap.getCanvas()).removeClass('pluscursor').addClass('minuscursor');
			g_oSegments[oTemp.properties.imrcpid] = oTemp;
			sColor = '#0C0';

		}
		g_oMap.setFeatureState(oTemp, {'include': !g_oMap.getFeatureState(oTemp).include, 'color': sColor});
	}
}

function addPopup(oJqEl, sMsg)
{
	if (g_bPopupExists)
		return;
	g_bPopupExists = true;
	let oPopuptext = $(`<span class="show">${sMsg}</span>`);
	if (oJqEl[0].getBoundingClientRect().top < 70)
		oPopuptext.addClass('popuptextbelow');
	else
		oPopuptext.addClass('popuptextabove');

	oJqEl.addClass('popup').append(oPopuptext);

	setTimeout(function()
	{
		oJqEl.removeClass('popup');
		oPopuptext.remove();
		g_bPopupExists = false;
	}, 1500);
}

function cancelEdits()
{
	$('#editctrls').css('display', 'none');
	$('#tableoverlay').css('display', 'none');
	resetMap();
}


function resetMap()
{
	removeSource('poly-outline', g_oMap);
	removeSource('poly-bounds', g_oMap);
	if (g_oMap.getLayer('geo-lines') !== undefined)
		g_oMap.removeLayer('geo-lines');
	if (g_oMap.getLayer('network-polygons-report') !== undefined)
		g_oMap.removeLayer('network-polygons-report');
	g_oImrcpIdLookup = {};
	g_oSegments = {};

}


function resetEdits()
{
	$('#alertLabel').removeClass('redhighlight').val('');
	$('.obstypes').val(-1);
	$('.comps').val(-1);
	$('.values').val('').css('display', 'block');
	$('.lookups').val(-1).css('display', 'none');
	$('input[name="and"]').val(['and']);
	$('.obs2.obstypes').children().first().html('');
	$('.obs2.comps').children().first().html('');
	$('.obs2.values').prop('placeholder', '');
	$('.obs1').removeClass('redhighlight');
	$('.obs2').removeClass('redhighlight');
}


function updateSelects(e)
{
	let sObstype = parseInt(e.target.options[e.target.selectedIndex].value).toString(36).toUpperCase();
	let oSelect = $(e.target).siblings('div').find('select');
	let oInput = $(e.target).siblings('div').find('input');
	let oComp = $(e.target).siblings('.comps');
	let sOptions = `<option value='-1'>Select a value...</option>`;
	oSelect.children().remove();
	if (g_oObsLookups[sObstype])
	{
		for (let aLookup of g_oObsLookups[sObstype])
			sOptions += `<option value='${aLookup[0]}'>${aLookup[1]}</option>`;
		oSelect.css('display', 'block');
		oInput.css('display', 'none').val('');
		oComp.val('eq').prop('disabled', true);;
	}
	else
	{
		oSelect.css('display', 'none').children().remove();
		oInput.css('display', 'block');
		oComp.prop('disabled', false);
	}
	oSelect.append(sOptions);
}

function saveEdits()
{
	let oData = {'token': sessionStorage.token};
	oData.label = $('#alertLabel').val();
	if (g_sCoordString === undefined)
		oData.coords='';
	else
		oData.coords = g_sCoordString;
	
	let sIds = '';
	for (let sId of Object.keys(g_oSegments).values())
		sIds += `${sId},`;
	if (sIds.length > 0)
		sIds = sIds.substring(0, sIds.length - 1);
	oData.ids = sIds;
	
	let oObstypes = $('.obstypes').map((nIndex, oEl) => parseInt($(oEl).val())).get();
	let oComps = $('.comps').map((nIndex, oEl) => $(oEl).val()).get();
	let oLookups = $('.lookups').map((nIndex, oEl) => parseInt($(oEl).val())).get();
	let oValues = $('.values').map((nIndex, oEl) => $(oEl).val()).get();
	let sAnd = $('input[name="and"]:checked').val();
	oData.logic = sAnd;
	oData.obstypes = oObstypes[0].toString();
	oData.comps = oComps[0];
	if (g_oObsLookups[oObstypes[0].toString(36).toUpperCase()])
		oData.vals = oLookups[0].toString();
	else
		oData.vals = oValues[0];

	if (oObstypes[1] > 0)
	{
		oData.obstypes += `,${oObstypes[1].toString()}`;
		oData.comps += `,${oComps[1]}`;
		if (g_oObsLookups[oObstypes[1].toString(36).toUpperCase()])
			oData.vals += `,${oLookups[1].toString()}`;
		else
			oData.vals += `,${oValues[1]}`;
	}
	
	let sUrl = 'api/dashboard/addAlert';
	if (g_sEditId !== undefined)
	{
		if (!validateAll('#btn-Save'))
			return;
		oData.currentid = g_sEditId;
		sUrl = 'api/dashboard/editAlert';
	}

	showPageoverlay('Saving alert...');
	$.ajax(
	{
		'url': sUrl,
		'dataType': 'json',
		'method': 'POST',
		'data': oData
	}).done(doneAdd).fail(() => 
	{
		showPageoverlay('Failed to save alert.');
		timeoutPageoverlay();
		cancelEdits();
	});
	
	if (g_sEditId !== undefined)
	{
		for (let nIndex = 0; nIndex < g_oTable.length; nIndex++)
		{
			if (g_oTable[0][8] === g_sEditId)
			{
				g_oTable.splice(nIndex, 1);
				break;
			}
		}

		delete g_oStatus[g_sEditId];
		delete g_oClickData[g_sEditId];
		disableEdits();
	}
}


function getStatusAjax()
{
	return $.ajax(
	{
		'url': 'api/dashboard/status',
		'dataType': 'json',
		'method': 'POST',
		'data': {'token': sessionStorage.token}
	});
}


function cmpAlert(o1, o2)
{
	let nReturn = o1.sortstatus - o2.sortstatus;
	if (nReturn === 0)
	{
		nReturn = o1.in - o2.in;
		if (nReturn === 0)
		{
			nReturn = o2.count - o1.count;
			if (nReturn === 0)
				nReturn = o1.name.localeCompare(o2.name);
		}
	}
	
	return nReturn;
}


function redrawTable()
{
	if ($('#dlgConfirmDelete').dialog('isOpen'))
	{
		return;
	}
	let oDataTable = Object.values(g_oStatus);
	oDataTable.sort(cmpAlert);
	g_oTable = [];
	for (let oObject of oDataTable.values())
	{
		g_oTable.push([`<i class="fa fa-lg fa-circle" style="color:${oObject.color}"></i>`, oObject.name, getInString(oObject.in, oObject.count), oObject.count, oObject.total, oObject.miles.toFixed(1), g_oEdit, g_oTrash, oObject.id]);
	}
	g_oDataTable.clear();
	let nNewIndex = -1;
	for (let nIndex = 0; nIndex < g_oTable.length; nIndex++)
	{
		let oRow = g_oTable[nIndex];
		g_oDataTable.row.add(oRow);
		if (g_sCurrentId === oRow[8])
			nNewIndex = nIndex;
	}
	g_oDataTable.draw();
	$('th').removeClass('sorting_asc');
	if (g_sCurrentId !== undefined)
	{
		g_nCurrentIndex = nNewIndex;
		$(g_oDataTable.row(g_nCurrentIndex).node()).addClass('w3-fhwa-navy');
	}
}


function updateChart(oDataObject, oStatus)
{
	if (oDataObject.data !== undefined)
	{
		g_oChart.data.datasets = [];
		g_oChart.options.scales.yAxes = [];
		let oNow = moment().minutes(0);
		let oFirst = moment(oDataObject.data[0][0], g_sMomentFormat);
		if (oDataObject.data.length > 1)
		{
			let oSecond = moment(oDataObject.data[1][0], g_sMomentFormat);
			if (oSecond.isBefore(oFirst))
				oFirst = oSecond;
		}
		let nDiff = oNow.diff(oFirst);
		for (let nPos = 0; nPos < oDataObject.data.length; nPos++)
		{
			let oCutoff = moment(oFirst).add(nDiff, 'milliseconds');
			let sPos;
			let aBorderDash = [];
			if (nPos === 0)
			{
				sPos = 'left';
			}
			else
			{
				sPos = 'right';
				aBorderDash = [12, 6];
			}
			let sObstype = oDataObject.obstype[nPos];
			let nMax = 0;
			let nMin = 0;

			let oVals = oDataObject.data[nPos];
			let oGraphVals = [];
			for (let i = 0; i < oVals.length; ++i)
			{
				let dObsVal = oVals[i][1];
				if (dObsVal > nMax)
					nMax = dObsVal;
				if (dObsVal < nMin)
					nMin = dObsVal;
				let oTime = moment(oVals[i][0], g_sMomentFormat).add(nDiff, 'milliseconds');
				if (oTime.isSameOrAfter(oCutoff))
				{
					oGraphVals.push({x: oTime.format(g_sMomentFormat), y: oVals[i][1]});
					oCutoff.add(1, 'hour');
				}
			}

			let nStep = 10;
			if (nMax !== 0) // if max is not 0, then it is above 0
			{
				if (nMax < nStep)
					nStep = 1;
				nMax += (nStep - nMax % nStep);
			}

			if (nMin !== 0) // if min is not 0, then it is less than 0
				nMin -= (nStep + nMin % nStep);

			g_oChart.data.datasets.push({
							label: oDataObject.label[nPos],
							yAxisID: sObstype,
							data: oGraphVals,
							borderColor: "rgba(0,0,0,0.6)",
							backgroundColor: "rgba(0,0,0,0)",
							borderDash: aBorderDash,
							cubicInterpolationMode: 'monotone'});

			g_oChart.options.scales.yAxes.push(
							{
								id: sObstype,
								position: sPos,
								type: 'linear',
								scaleLabel: {display: true, labelString: oDataObject.label[nPos]},
								ticks: {
								min: nMin,
								max: nMax,
								stepSize: nStep
								}
							});

		}
//			g_oChart.options.legend.display = g_oChart.options.scales.yAxes.length > 1;
		g_oChart.options.legend.display = false;
		g_oChart.options.title.text = oStatus.name;
		$('#chartid').css('display', 'block');
	}
	else
	{
		$('#chartid').css('display', 'none');
	}
	g_oChart.update();		
}


function buildConfirmDelete()
{
	let oDialog = $('#dlgConfirmDelete');
	oDialog.dialog({autoOpen: false, position: {my: "center", at: "center", of: "body"}, modal: true, draggable: false, resizable: false, width: 400,
		buttons: [
			{id: 'btn-ConfirmDelete', text: 'Delete', click: function() 
			{
				doDelete();
				oDialog.dialog('close');
			}},
			{id: 'btn-ConfirmCancel', text: 'Cancel', click: function() 
			{
				$(this).dialog('close');
			}}
		],
		open:function() 
		{
			oDialog.dialog('option', 'position', {my: "center", at: "center", of: "body"});
		},
		close:function() 
		{
			redrawTable();
		}});
	$(window).resize(function()
	{
		oDialog.dialog('option', 'position', {my: "center", at: "center", of: "body"});
	});
	oDialog.dialog('option', 'title', 'Confirm Alert Delete');
	oDialog.siblings().children('.ui-dialog-titlebar-close').remove();
	oDialog.html('Deleting an alert cannot be undone. Would you like to delete the alert?');
}

$(document).on('initPage', init);
