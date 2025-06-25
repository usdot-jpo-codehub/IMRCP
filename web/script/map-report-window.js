import {SelectionModes} from './jquery/jquery.segmentSelector.js';
import{fromIntDeg, startDrawPoly, getLineStringBoundingBox, removeSource, clearFeatures} from './map-util.js';

const resetSubscriptionFields = () => {
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
  $('#divSubResult,#divSubError').hide();
  $('#btnSubmitReport, #divSubInput').show();
  $('#btnClose').val('Cancel');
  $('#divReportDialog').dialog('option', 'position', {my: 'center', at: 'center', of: '#map-container'});
};


class ReportSelectionManager
{
  constructor( 
  {
  map: mymap,
    obstypes,
    refTimeMax,
    refTimeInterval,
    refTimeInitial,
    sources,
    spriteDef,
    beforeSelection,
    afterSelection
  })
  {

    var reportObstypeOptions = Object.entries(obstypes)
      .sort((a, b) => a[1].name.localeCompare(b[1].name))
      .reduce((accumulatedOptions, [id, {name, desc, unit}]) => {//id, desc, eng units
        accumulatedOptions += `<option value ='${id}'>${name}, ${desc}`;

        if (unit)
          accumulatedOptions += `, ${unit}`;

        return accumulatedOptions + '</option>';
      }, '');



    const reportTypeDialogHtml = 
			`<div id='start-report-dialog' style='display:none;' title='Choose Report Location'>
			<ul class='w3-ul ul-no-border'>
				<li><input type='text' style='width: 100%;' id='location-autocomplete' name='location' placeholder='Type name of city, county, or state...'></li>
				<li><input type='button' value='Select Custom Area' id='location-custom'></li>
			</ul>
			<input type='button' style='float:right;' value='Next' id='report-type-button'>
			</div>`;
//      `<div id='start-report-dialog' style='display: none;' title='Choose Report'>
//      <ul class='w3-ul ul-no-border'>
//        <li><label><input type='radio' value='3' name='reportType' id='optDetectorReport' />Stations</label></li>
//        <li><label><input type='radio' value='2' name='reportType' id='optSegmentReport' />Segments</label></li> 
//        <li><label><input type='radio' value='1' name='reportType' id='optAreaReport' />Area</label></li>
//      </ul>
//      <input type='button' value='Next' id='report-type-button' />
//      </div>`;


    const reportDialogHtml =
      `<div id='divReportDialog' style='display:none'>
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
      <div id='divSubResult' style='display:none;'>
    	<div>
			<h3><span id='spnType'>Report</span> Info</h3>
			<div> 
        <b>Name: </b><span id='spnName'></span><br/>
        <b>Description: </b><span id='spnDesc'></span><br/>
        <b>Subscription Identifier: </b><span id='spnUuid'></span><br/>
        <b>Direct URL: </b><span id='spnUrl'></span>
			</div>
		</div>
        <br />
			You can view your current reports and subscriptions and their available files
      on the <a href='reports.jsp'>Reports</a> page.
	    </div>
      <div id='divSubError' style='display:none;'>An error occurred creating your report or subscription. Please try again later.</div>
      <input type='button' id='btnSubmitReport' value='Submit'/><input type='button' id='btnClose' value='Cancel' />
    </div>`;

    const reportDialog = this.reportDialog = $(reportDialogHtml).prependTo('body').dialog({
	autoOpen: false,
	resizable: false,
	draggable: false,
	width: 'auto',
	modal: true,
	dialogClass: 'no-title-form',
	position: {my: 'center', at: 'center', of: '#map-container'},
	close: function() 
	{
		resetSubscriptionFields();
		cancelReport();
	},
	open: function()
	{
		  let oObstypes = $('#lstReportObstypes');
		  $('#legendDeck input:checked').each(function(index, el) 
		  {
			  if(oObstypes.val().length < 5)
			  {
				  let sVal = parseInt($(el).prop('id'), 36);
				  oObstypes.children('option[value="' + sVal + '"]').prop('selected', true);
			  }
		  });
	}});
	  

    $('#txtReportRefTime').datetimepicker({
      step: refTimeInterval,
      value: refTimeInitial,
      formatTime: 'h:i a',
      format: 'Y/m/d h:i a',
      yearStart: 2017,
      yearEnd: refTimeMax.getUTCFullYear(),
      maxTime: refTimeMax,
      maxDate: refTimeMax
    });



    const reportTypeDialog = this.reportTypeDialog = $(reportTypeDialogHtml).prependTo('body').dialog({
      autoOpen: false,
      resizable: true,
      draggable: true,
      width: 500,
      modal: false,
	  closeOnEscape: false,
      //  dialogClass: 'no-title-form',
      position: {my: 'left top', at: 'left+10 top+10', of: '#map-container'}
    });
	
	reportTypeDialog.parent().find('button[title|="Close"]').on('click', cancelReport);
	$('#report-type-button').prop('disabled', true);
	
	function cancelReport()
	{
		reportTypeDialog.dialog('close');
		reportDialog.dialog('close');
		document.dispatchEvent(new KeyboardEvent('keyup', 
		{
			key: 'Escape',
			keyCode: 27,
			code: 'Escape',
			which: 27
		}));
		clearFeatures('report-location', mymap);
		$('#location-autocomplete').val('');
		mymap.getCanvas().style.cursor = '';
		$('#report-type-button').prop('disabled', true);
	}
	
	let oLocCache = {};
	$('#location-autocomplete').autocomplete(
	{
		minLength: 3,
		source: function(oReq, oResponse)
		{
			let sTerm = oReq.term;
			if (sTerm in oLocCache)
			{
				oResponse(oLocCache[sTerm]);
				return;
			}
			$.ajax(
			{
				'url': 'api/location/lookup',
				'method': 'POST',
				'dataType': 'json',
				'data': {'lookup': sTerm, 'token': sessionStorage.token}
			}).done(function(oData, sStatus, oXhr) 
			{
				oLocCache[sTerm] = oData;
				oResponse(oData);
			}).fail(function()
			{
				alert('Location request failed');
				oResponse([]);
			});
		},
		select: function(oEvent, oUi)
		{
			$.ajax(
			{
				'url': 'api/location/geo',
				'method': 'POST',
				'dataType': 'json',
				'data': {'place': oUi.item.value, 'token': sessionStorage.token}
			}).done(function(oPolygon, sStatus, oXhr)
			{
				let dMinX = Number.MAX_VALUE;
				let dMaxX = -Number.MAX_VALUE;
				let dMinY = Number.MAX_VALUE;
				let dMaxY = -Number.MAX_VALUE;
//				let aNewRings = [];
				let oSrc = mymap.getSource('report-location');
				let oData = oSrc._data;
				oData.features = [];
				for (let nRingIndex = 0; nRingIndex < oPolygon.length; nRingIndex++)
				{
					let aRing = oPolygon[nRingIndex];
					let aNewRing = [];
					aNewRing.push([fromIntDeg(aRing[0][0]), fromIntDeg(aRing[0][1])]);
					for (let nIndex = 1; nIndex < aRing.length; nIndex++)
					{
						aRing[nIndex][0] += aRing[nIndex - 1][0];
						aRing[nIndex][1] += aRing[nIndex - 1][1];
						let dX = fromIntDeg(aRing[nIndex][0]);
						let dY = fromIntDeg(aRing[nIndex][1]);
						aNewRing.push([dX, dY]);
						if (dX > dMaxX)
							dMaxX = dX;
						if (dX < dMinX)
							dMinX = dX;
						if (dY > dMaxY)
							dMaxY = dY;
						if (dY < dMinY)
							dMinY = dY;
					}
					if (aNewRing[0][0] !== aNewRing[aNewRing.length - 1][0] || aNewRing[0][1] !== aNewRing[aNewRing.length - 1][1]) // first and last coordinate need to be the same
						aNewRing.push([aNewRing[0][0], aNewRing[0][1]]);
					oData.features.push({'type': 'Feature', 'geometry': {'type': 'LineString', 'coordinates': aNewRing}});
				}
				
				oSrc.setData(oData);
				mymap.fitBounds([[dMinX, dMinY], [dMaxX, dMaxY]], {'padding': 50});
				$('#report-type-button').prop('disabled', false);
			}).fail(function() 
			{
				alert('Failed to load jurisdiction geometry');
			});
		}
	});


    const segmentSelector = this.segmentSelector = $('#mapid').segmentSelector({map: mymap, spriteDef, sources, beforeSelection, afterSelection});
	$('#location-custom').click(function() 
	{
		document.dispatchEvent(new KeyboardEvent('keyup', // fire an escape to cancel a possible existing drawing event
		{
			key: 'Escape',
			keyCode: 27,
			code: 'Escape',
			which: 27
		}));
	
		startDrawPoly.bind(
		{
			'map': mymap,
			'startDraw': function() 
			{
				$('#start-report-dialog > ul').append('<li>Left-click: Initial point<br>Esc: Cancel</li>');
				$('#report-type-button').prop('disabled', true);
				$('#location-autocomplete').val('');
				clearFeatures('report-location', mymap);
			},
			'initPoly': function()
			{
				$('#start-report-dialog > ul li:last-child').html('Left-click: Add point<br>Enter: Finish polygon<br>Esc: Cancel');
			},
			'finishDraw': function()
			{
				$('#start-report-dialog > ul li:last-child').remove();


				let oSrc = mymap.getSource('report-location');
				let oData = oSrc._data;
				oData.features = [];

				let oPoly = mymap.getSource('poly-outline');
				let oFeature = oPoly._data;
				oFeature.geometry.coordinates.push(oFeature.geometry.coordinates);
				oData.features.push(oFeature);
				oSrc.setData(oData);
				removeSource('poly-outline', mymap);
				removeSource('poly-bounds', mymap);
				mymap.fitBounds(getLineStringBoundingBox(oFeature), {'padding': 50});
				mymap.getCanvas().style.cursor = '';
				$('#report-type-button').prop('disabled', false);
			},
			'cancelDraw': function()
			{
				$('#start-report-dialog > ul li:last-child').remove();
				removeSource('poly-outline', mymap);
				removeSource('poly-bounds', mymap);
				mymap.getCanvas().style.cursor = '';
			}}).apply();
	});
	
	
    $('#btnReport').click(() => reportTypeDialog.dialog('open'));
	$('#report-type-button').click(function () 
	{
		reportTypeDialog.dialog('close');
		reportDialog.dialog('open');
	});
//    $('#report-type-button').click(() => {
//      switch (1 * $('input[name="reportType"]:checked').val())
//      {
//        case SelectionModes.AREA:
//          return this.beginAreaReport();
//        case SelectionModes.POINTS:
//          return this.beginDetectorReport();
//        case SelectionModes.SEGMENTS:
//          return this.beginSegmentReport();
//        default:
//          alert('Please select a report type to continue.');
//      }
//    });
  }

  beginReport(selectionMethod)
  {
    this.reportTypeDialog.dialog('close');
    this.segmentSelector.segmentSelector(selectionMethod);
  }

  beginSegmentReport()
  {
    this.beginReport('beginSegmentSelection');
  }
  beginAreaReport()
  {
    this.beginReport('beginAreaSelection');
  }
  beginDetectorReport()
  {
    this.beginReport('beginPointSelection');
  }
}


export {ReportSelectionManager};