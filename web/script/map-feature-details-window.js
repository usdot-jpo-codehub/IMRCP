import {fromIntDeg} from './map-util.js';

const POINTTOL = 0.0000001;
const CLOSE_BUTTON_HTML = '<button type="button" class="ui-button ui-widget ui-state-default ui-corner-all ui-button-icon-only no-title-form ui-dialog-titlebar-close" role="button" title="Close"><span class="ui-button-icon-primary ui-icon ui-icon-closethick"></span><span class="ui-button-text">Close</span></button>';
const obsTimeFormat = 'MM-DD-YY HH:mm';
const MAXLONG = Math.pow(2, 63) - 1;

const getBoundsForPoint = (lngLat, m, tol) => 
{
	let oP = m.project(lngLat);
	
	return new mapboxgl.LngLatBounds(m.unproject({x: oP.x - tol, y: oP.y + tol}), m.unproject({x: oP.x + tol, y: oP.y - tol}));
};

const getBoundsForClick = lngLat => new mapboxgl.LngLatBounds(lngLat, lngLat);

const featureTypeHandlers = new Map([
  ['symbol', {baseUrl: 'api/points',
      getFeatureBounds: (f, c, m) => getBoundsForPoint(f.geometry.coordinates, m, 2)
    }
  ],
  ['line', {baseUrl: 'api/road',
      getFeatureBounds: (f, c, m) => getBoundsForPoint([fromIntDeg(f.properties.lon), fromIntDeg(f.properties.lat)], m, 0)
    }
  ],
  ['fill', {baseUrl: 'api/area',
      getFeatureBounds: (f, clickLatLng, m) => getBoundsForClick(clickLatLng, m, 0)
    }
  ]
]);

class FeatureDetailsWindow
{
  constructor(
  {mapSelector, sources, selectedTimeStartFunction, selectedTimeEndFunction})
  {
    this.selectedTimeStartFunction = selectedTimeStartFunction;
    this.selectedTimeEndFunction = selectedTimeEndFunction;
    this.sources = sources;
	const dialogDivHtml = `<div id="obs-header" class="obs-detail-row w3-sand"><div class="obs-detail-item" style="width:28px;">&nbsp;</div><div class="obs-detail-item">ObsType</div><div class="obs-detail-item" style="width:75px;">Source</div><div class="obs-detail-item">Start Time</div><div class="obs-detail-item">End Time</div><div class="obs-detail-item">Value</div><div class="obs-detail-item" style="width:50px;">Units</div></div><div id="obs-loading" class="obs-detail-row">Loading data...</div><div id="obs-timeout" class="obs-detail-row">Timeout loading data. Try again.</div><div id="obs-error" class="obs-detail-row">Error loading data.</div><div id="obs-no-data" class="obs-detail-row">No data.</div><div id="obs-detail"></div>`;
    /*const dialogDivHtml = `<div id="obs-header" class="obs-detail-row w3-sand">
					<div class="obs-detail-item" style="width:28px;">&nbsp;</div>
					<div class="obs-detail-item">ObsType</div>
					<div class="obs-detail-item" style="width:75px;">Source</div>
					<div class="obs-detail-item">Start Time</div>
					<div class="obs-detail-item">End Time</div>
					<div class="obs-detail-item">Value</div>
					<div class="obs-detail-item" style="width:50px;">Units</div>
				</div>
				<div id="obs-loading" class="obs-detail-row">Loading data...</div>
				<div id="obs-timeout" class="obs-detail-row">Timeout loading data. Try again.</div>
				<div id="obs-error" class="obs-detail-row">Error loading data.</div>
				<div id="obs-no-data" class="obs-detail-row">No data.</div>
				<div id="obs-detail"></div>`;*/

	if (!document.getElementById('dlgObsDetail'))
	{
		$('body').append('<div id="dlgObsDetail"></div>');
	}
    const dialogDiv = this.dialogDiv = $('#dlgObsDetail').prependTo('body');
	
	
    const dialog = this.dialog = dialogDiv.dialog({
      autoOpen: false,
      resizable: true,
      draggable: true,
      width: 950,
      modal: true,
      position: {my: "center", at: "center center-120", of: mapSelector},
      minHeight: 380,
      maxHeight: 550
    });
	
	dialog.html(dialogDivHtml);
    this.dialogTitleDiv = dialogDiv.parent().find('.ui-dialog-title');
  }

  showFeatureDetails(feature, clickLatLng, mbMap)
  {
    let detailsContent = `${CLOSE_BUTTON_HTML} Loading...`;

    const {dialogTitleDiv, dialog, sources} = this;
    dialogTitleDiv.html(detailsContent);
    const closeDialog = () => dialog.dialog("close");
    dialogTitleDiv.find('.ui-dialog-titlebar-close').click(closeDialog);
	$('#obs-loading').show();
	$('#obs-timeout,#obs-error,#obs-no-data,#obs-detail').hide();
    const startTime = this.selectedTimeStart();
    
	let featureType;
	let featureHandler;
	let bounds;
	if (feature === null)
	{
		featureType = 'fill';
	}
	else
	{
		featureType = sources.get(feature.source).type;
	}
	featureHandler = featureTypeHandlers.get(featureType);
	bounds = featureHandler.getFeatureBounds(feature, clickLatLng, mbMap);

	
	
    const urlBoundarySubPath =
      [bounds.getNorth() + POINTTOL, bounds.getWest() - POINTTOL, bounds.getSouth() - POINTTOL, bounds.getEast() + POINTTOL]
      .map(d => d.toFixed(7))
      .join('/');


	$('#pageoverlay').show();
    const load = $.ajax({
      dataType: "json",
      url: `${featureHandler.baseUrl}/platformObs`
        + `/${startTime}/${this.selectedTimeEnd()}/${urlBoundarySubPath}`,
      timeout: 25000
    });


    load.fail((jqXHR, textStatus) => 
	{
		$('.obs-detail-data').remove();
		if (textStatus === 'timeout')
		{
			$('#obs-timeout').show();
			$('#obs-loading,#obs-error').hide();
			dialogTitleDiv.html(`${CLOSE_BUTTON_HTML} Timeout loading data`);  
		}
		else
		{
			$('#obs-error').show();
			$('#obs-timeout,#obs-loading').hide();
			dialogTitleDiv.html(`${CLOSE_BUTTON_HTML} Error loading data`);
		}
		$('#pageoverlay').hide();
    });

    load.done(additionalDetails => 
	{
		detailsContent = CLOSE_BUTTON_HTML;


		if (additionalDetails.tnm)
			detailsContent += additionalDetails.tnm + '<br />';
		if (additionalDetails.sdet)
			detailsContent += '<div style="max-width:500px; overflow-wrap:break-word;">' + additionalDetails.sdet.replace('   ', '&nbsp;') + '</div>';

		detailsContent += 'Lat, Lon: ';
		if (additionalDetails.lat && additionalDetails.lon)
			detailsContent += additionalDetails.lat + ', ' + additionalDetails.lon + ' ';
		else
		{
			const center = bounds.getCenter();
			detailsContent += `${center.lat.toFixed(6)}, ${center.lng.toFixed(6)} `;
		}

		if (additionalDetails.tel)
			detailsContent += ' Elevation: ' + additionalDetails.tel;
		dialogTitleDiv.html(detailsContent);
		dialogTitleDiv.find('.ui-dialog-titlebar-close').click(closeDialog);
		$('.obs-detail-data').remove();
		$('#obs-loading').hide();

		let obsList = additionalDetails.obs;
		if (!obsList || obsList.length === 0)
		{
			$('#obs-no-data').show();
		}
		else
		{
			let oObsByType = {};
			for (let oObs of obsList)
			{
				if (Object.hasOwn(oObsByType, oObs.oi))
					oObsByType[oObs.oi].push(oObs);
				else
					oObsByType[oObs.oi] = [oObs];
			}
			let oAccordians = [];
			let sHtml = '';
			for (let oObsTypeList of Object.values(oObsByType))
			{
				oObsTypeList.sort((a, b) => a.pref - b.pref);

				for (let nIndex = 0; nIndex < oObsTypeList.length; nIndex++)
				{
					let oObs = oObsTypeList[nIndex];
					let sEnd = '</div>';
					if (nIndex === 0)
					{
						if (oObsTypeList.length > 1)
						{
							sHtml += `<div id="obs-${oObs.oi}" class="obs-detail-data"><h3>`;
							sEnd = '</h3>';
							oAccordians.push(`#obs-${oObs.oi}`);
						}
						else
						{
							sHtml += `<div id="obs-${oObs.oi}" class="obs-detail-row obs-detail-data"><div class="obs-detail-item" style="width:28px;">&nbsp;</div>`;
						}
					}
					else
					{
						if (nIndex === 1)
							sHtml += '<div>';
						if (nIndex < oObsTypeList.length - 1)
							sEnd = '<br><br>';
						else
							sEnd = '</div></div>';
					}

					sHtml += `<div class="obs-detail-item">${oObs.od}</div>`;
					sHtml += `<div class="obs-detail-item" style="width:75px;">${oObs.src}</div>`;
					sHtml += `<div class="obs-detail-item">${moment(oObs.ts1).format(obsTimeFormat)}</div>`;
					sHtml += `<div class="obs-detail-item">${oObs.ts2 !== MAXLONG ? moment(oObs.ts2).format(obsTimeFormat) : ''}</div>`;
					sHtml += `<div class="obs-detail-item">${oObs.url ? '<a href="' + oObs.url + '" target="_blank" style="color: blue;">' + oObs.ev + '</a>' : oObs.ev}</div>`;
					sHtml += `<div class="obs-detail-item" style="width:50px;">${oObs.eu ? oObs.eu : ''}</div>`;

					sHtml += sEnd;
				}
			}
			$('#obs-detail').html(sHtml);
			for (let sAccordian of oAccordians.values())
				$(sAccordian).accordion({collapsible:true, heightStyle: "content", active: false});
			$('#obs-detail').show();
		}
		
		dialog.resize();
//		dialog.dialog("option", "position", "center");
		$('#pageoverlay').hide();
    });

    dialog.dialog("open");
  }

  selectedTimeStart()
  {
    return this.selectedTimeStartFunction();
  }

  selectedTimeEnd()
  {
    return this.selectedTimeEndFunction();
  }
}

export {FeatureDetailsWindow};