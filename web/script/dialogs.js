import {g_oMap, g_oDialogs, g_oInstructions, instructions, submitNetwork, cancelNetwork, toggleDialog, turnOffAddRemove, turnOffDelete,
	turnOffMerge, turnOffSplit, startSelectNetwork, cancelReprocess, confirmReprocess, closeNetworkSelect, confirmDeleteNetwork, confirmPublishNetwork, confirmTrainingNetwork, confirmLoadNetwork} from './network.js';
import {toggleDetectorEdit, nextDetector, saveDetector, revertDetector, exitDetectorEdit, detChange, startDetector, selectFile} from './detectors.js';
import {g_oLayers, removeSource} from './map-util.js';

function buildRoadLegendDialog()
{
	let oDialog = $('#dlgRoadLegend');
	$("button[title|='Toggle Road Legend']").click({'dialog': 'roadlegend'}, toggleDialog);
	g_oDialogs['roadlegend'] = '#dlgRoadLegend';
	
	oDialog.dialog({autoOpen: false, position: {my: "right bottom", at: "right-8 bottom-8", of: "#mapid"}, resizable: false, width: 190, draggable: false});
	
	let oTypes = {};
	let oColorList = g_oLayers['geo-lines'].paint['line-color'];
	
	for (let nIndex = 2; nIndex < oColorList.length - 1; nIndex += 2)
	{
		let sType = oColorList[nIndex];
		let sColor = oColorList[nIndex + 1];
		
		let nLinkIndex = sType.indexOf('_link');
		if (nLinkIndex >= 0)
			sType = sType.substring(0, nLinkIndex);
		
		if (oTypes[sType] === undefined)
		{
			oTypes[sType] = sColor;
		}
	}
	
	let sHtml = "<ul class='road-legend'>";
	for (let [sType, sColor] of Object.entries(oTypes))
	{
		sHtml += '<li><span style="background:' + sColor + ';"></span>' + sType + '</li>';
	}
	
	sHtml += '</ul>';
	oDialog.html(sHtml);
}

function buildCancelDialog()
{
	let oDialog = $('#dlgCancel');
	g_oDialogs['cancel'] = '#dlgCancel';
	oDialog.dialog({autoOpen: false, position: {my: 'center', at: 'center', of: '#mapid'}, resizable: false, width: 400, modal: true, draggable: false,
		buttons: [
			{text: 'Confirm cancel', click: function() 
				{
					$('#main-control').show();
					$('#edit-control').hide();
					$('#dlgCancel').dialog('close');
					turnOffAddRemove();
					turnOffMerge();
					turnOffSplit();
					removeSource('geo-lines', g_oMap);
					instructions(g_oInstructions['select'], 'Canceled editing');
					g_oMap.flyTo({center: [-98.585522, 39.8333333], zoom: 4});
					startSelectNetwork();
				}},
			{text: 'Continue Editing', click: function() {$(this).dialog('close');}}
		]});
	oDialog.html('Canceling will lose all unsaved changes. Would you like to cancel or continue editing the network?');
}


function buildNetworkDialog()
{
	let oDialog = $('#dlgNetwork');
	g_oDialogs['network'] = '#dlgNetwork';
	oDialog.dialog({autoOpen: false, position: {my: 'center', at: 'center', of: '#mapid'}, resizable: false, width: 600, maxHeight: 600, draggable: false,
			buttons: [
				{text: 'Submit', click: submitNetwork, disabled: true, id: 'btnSubmitNetwork'},
				{text: 'Cancel', click: cancelNetwork, disabled: true, id: 'btnCancelNetwork'}
				]});

	let aHighwayTags = ['motorway', 'connector', 'trunk', 'ramp', 'primary', 'primary_link', 'secondary', 'secondary_link', 'tertiary', 'tertiary_link', 'residential', 'unclassified']
	let aDescs = 
			[
				'A restricted access major divided highway, normally with 2 or more running lanes plus emergency hard shoulder. Equivalent to the Freeway, Autobahn, etc..',
				'The link roads leading to/from a motorway from/to a motorway or trunk.',
				'The most important roads in a country\'s system that aren\'t motorways. (Need not necessarily be a divided highway.)',
				'The link roads leading to/from a motorway or trunk road from/to a lower class highway.',
				'The next most important roads in a country\'s system. (Often link larger towns.)',
				'The link roads (sliproads/ramps) leading to/from a primary road from/to a primary road or lower class highway.',
				'The next most important roads in a country\'s system. (Often link towns.)',
				'The link roads (sliproads/ramps) leading to/from a secondary road from/to a secondary road or lower class highway.',
				'The next most important roads in a country\'s system. (Often link smaller towns and villages)',
				'The link roads (sliproads/ramps) leading to/from a tertiary road from/to a tertiary road or lower class highway.',
				'Roads which serve as an access to housing, without function of connecting settlements. Often lined with housing.',
				'The least important through roads in a country\'s system – i.e. minor roads of a lower classification than tertiary, but which serve a purpose other than access to properties. (Often link villages and hamlets.)'
			];
	let sHtml = '<table class="network-table"><thead><tr><td>Network label</td><td><input type="text" id=network-label></td></tr><tr><td>Road types to include</td><td>Description</td></tr></thead><tbody>';
	for (let nIndex = 0; nIndex < aHighwayTags.length; nIndex++)
	{
		let sName = aHighwayTags[nIndex];
		sHtml += '<tr><td><input type="checkbox" name="' + sName + '">';
		sHtml += '&nbsp;<label for="' + sName + '">' + sName + '</label></td>';
		sHtml += '<td>' + aDescs[nIndex] + '</td></tr>';
	}
	sHtml += '</tbody></table>';
	oDialog.html(sHtml);
	
	let oCancelCkBx = $('<input type="checkbox" id="enable-cancel">Canceling removes all progress.</input>');
	oCancelCkBx.click(function() 
	{
		if ($(this).is(":checked"))
		{
			$('#btnCancelNetwork').button('enable');
			oDialog.parent().find('button[title|="Close"]').button('enable');
		}
		else
		{
			$('#btnCancelNetwork').button('disable');
			oDialog.parent().find('button[title|="Close"]').button('disable');
		}
			
	});
	oCancelCkBx.appendTo($('#dlgNetwork + div.ui-dialog-buttonpane div.ui-dialog-buttonset'));
	
	$('#network-label').on('input', function() 
	{
		if ($(this).val().length == 0)
			$('#btnSubmitNetwork').button('disable');
		else
			$('#btnSubmitNetwork').button('enable');
	});
	
	$('#dlgNetwork input[type="checkbox"]').on('input', function()
	{
		if ($('#network-label').val().length > 0 && $('#dlgNetwork :checked').length > 0)
			$('#btnSubmitNetwork').button('enable');
		else
			$('#btnSubmitNetwork').button('disable');
	});
	
	oDialog.parent().find('button[title|="Close"]').click(cancelNetwork).button('disable');;
}


function buildNetworkLegendDialog()
{
	let oDialog = $('#dlgNetworkLegend');
	$("button[title|='Toggle Network Legend']").click({'dialog': 'networklegend'}, toggleDialog);
	g_oDialogs['networklegend'] = '#dlgNetworkLegend';
	
	oDialog.dialog({autoOpen: false, position: {my: "right bottom", at: "right-8 bottom-8", of: "#mapid"}, resizable: false, width: 220, draggable: false});
	let oTypes = {'Assembling': 'rgba(128, 128, 128, 0.6)', 'Work In Progress': 'rgba(0, 100 ,0 , 0.6)', 'Publishing': 'rgba(147, 112, 219, 0.6)', 'Published': 'rgba(144, 238, 144, 0.6)', 'Training': 'rgba(0, 255, 255, 0.6)', 'Error': 'rgba(255, 51, 51, 0.6)'};
	
	let sHtml = "<ul class='road-legend'>";
	for (let [sType, sColor] of Object.entries(oTypes))
	{
		sHtml += '<li><span style="background:' + sColor + ';"></span><p>' + sType + '</p></li>';
	}
	
	sHtml += '</ul>';
	oDialog.html(sHtml);
	oDialog.dialog('open');
	document.activeElement.blur();
}


function buildInstructionDialog()
{
	let oDialog = $('#dlgInstructions');
	$("button[title|='Toggle Instructions']").click({'dialog': 'instructions'}, toggleDialog);
	g_oDialogs['instructions'] = '#dlgInstructions';
	
	oDialog.dialog({autoOpen: false, position: {my: "left top", at: "left+8 top+8", of: "#map-container"}, resizable: false, width: 300});
	
	
	let sHtml = '<p id="instructions"></p><p id="instructions-status" style="color: #108010"></p><p id="instructions-error" style="color: #d00010"></p>';
	
	oDialog.html(sHtml);
	oDialog.dialog('open');
	document.activeElement.blur();
}

function buildDetectorDialog()
{
	let oDialog = $('#dlgDetectorLegend');
	$("button[title|='Toggle Detector Legend']").click({'dialog': 'detectorlegend'}, toggleDialog);
	g_oDialogs['detectorlegend'] = '#dlgDetectorLegend';
	
	oDialog.dialog({autoOpen: false, position: {my: "right bottom", at: "right-8 bottom-8", of: "#mapid"}, resizable: false, width: 190, draggable: false});
	let oTypes = {'One-To-One': 'rgb(144, 238, 144)', 'One-To-Many': 'rgb(128, 128, 128)', 'No Mapping': 'rgb(255, 51, 51)'};
	
	let sHtml = "<ul class='road-legend'>";
	for (let [sType, sColor] of Object.entries(oTypes))
	{
		sHtml += '<li><span style="background:' + sColor + ';"></span><p>' + sType + '</p></li>';
	}
	
	sHtml += '</ul>';
	oDialog.html(sHtml);
}


function buildDetectorEditDialog()
{
	let oDialog = $('#dlgDetectorEdit');
	$("button[title|='Edit Detectors']").click({'dialog': 'detectoredit'}, toggleDetectorEdit);
	g_oDialogs['detectoredit'] = '#dlgDetectorEdit';
	
	oDialog.dialog({autoOpen: false, position: {my: "left bottom", at: "left+8 bottom-8", of: "#mapid"}, resizable: false, width: 400, draggable: false,
			buttons: [
				{text: 'Revert', click: function(){}, id: 'detector-revert'},
				{text: 'Next of 00', id: 'detector-next', click: function(){}},
				{text: 'Save', click: function(){}, id: 'detector-save'}
				]});
	
	$('#detector-next').click(nextDetector);
	$('#detector-save').click(saveDetector);
	$('#detector-revert').click(revertDetector);
	oDialog.on('dialogclose', exitDetectorEdit);
	
	let sHtml = '<form id="det-form"><table class="det-edit-table"><tr><td>Label</td><td><input id="det-label" name="label"></td></tr>';
	sHtml += '<tr><td>Collector Id</td><td><input id="det-cid" name="cid"></td></tr>';
	sHtml += '<tr><td>Archive Id</td><td><input id="det-aid" name="aid"></td></tr>';
	sHtml += '<tr><td>Lat</td><td><input disabled id="det-lat" name="lat"></td></tr>';
	sHtml += '<tr><td>Lon</td><td><input disabled id="det-lon" name="lon"></td></tr>';
//	sHtml += '<tr><td>In Service</td><td><input checked type="checkbox" id="det-insvc" name="insvc"></td></tr>';
	sHtml += '<tr><td></td><td id="det-status"></td></tr>';
	
	sHtml += '</table></form>';
	
	
	oDialog.html(sHtml);
	$('#det-cid').on('change', {'field': 'cid'}, detChange);
	$('#det-aid').on('change', {'field': 'aid'}, detChange);
}


function buildDetectorStatusDialog()
{
	let oDialog = $('#dlgDetectorStatus');
	g_oDialogs['detectorstatus'] = '#dlgDetectorStatus';
	
	oDialog.dialog({autoOpen: false, position: {my: "center", at: "center", of: "#mapid"}, resizable: false, width: 400, draggable: false,
				close: function(oEvent, oUi)
				{
					$('#status-total').html('');
					$('#status-onetoone').html('');
					$('#status-nomapping').html('');
					$('#status-onetomany').html('');
				}});
			
	oDialog.on('dialogclose', startDetector);
	let sHtml = '<table class="det-status-table"><tr><td>Total Detectors</td><td id="status-total"></td></tr>';
	sHtml += '<tr><td># of One-To-One Mappings</td><td id="status-onetoone"></td></tr>';
	sHtml += '<tr><td># of One-To-Many Mappings</td><td id="status-onetomany"></td></tr>';
	sHtml += '<tr><td># of No Mappings</td><td id="status-nomapping"></td></tr>';
	sHtml += '</table>';
	oDialog.html(sHtml);
}


function buildUploadConfirmDialog()
{
	let oDialog = $('#dlgUploadConfirm');
	g_oDialogs['uploadconfirm'] = '#dlgUploadConfirm';
	oDialog.dialog({autoOpen: false, position: {my: "center", at: "center", of: "#mapid"}, resizable: false, width: 400, draggable: false,
				buttons: [{text: 'Cancel', click: function(){$(this).dialog('close');}},
						  {text: 'Upload', click: function(){$(this).dialog('close'); selectFile();}}]});
	
	
	oDialog.html('There is already a detector file associated with this network. That file and any changes will be lost if another file is uploaded. Do you want to upload a new file?');
}


function buildNetworkMetadataDialog()
{
	let oDialog = $('#dlgNetworkMetadata');
	g_oDialogs['networkmetadata'] = '#dlgNetworkMetadata';
	oDialog.dialog({autoOpen: false, position: {my: "left bottom", at: "left+8 bottom-8", of: "#map-container"}, resizable: false, width: 300});
}

function buildReprocessDialog()
{
	let oDialog = $('#dlgReprocess');
	g_oDialogs['reprocess'] = '#dlgReprocess';
	oDialog.dialog({autoOpen: false, position: {my: "center", at: "center", of: "#mapid"}, resizable: false, width: 400, draggable: false, model: true,
				buttons: [{text: 'Cancel', click: cancelReprocess},
						  {text: 'Reprocess', click: confirmReprocess}
						]});
	
	oDialog.html('Reprocessing a network allows you to choose different road classifications but will cause all manual changes to be lost. Would you like to reprocess this network?');
	oDialog.parent().find('button[title|="Close"]').on('click', cancelReprocess);
}


function buildDeleteConfirmDialog()
{
	let oDialog = $('#dlgDeleteConfirm');
	g_oDialogs['deleteconfirm'] = '#dlgDeleteConfirm';
	oDialog.dialog({autoOpen: false, position: {my: "center", at: "center", of: "#mapid"}, resizable: false, modal: true, width: 400, draggable: false,
				buttons: [{text: 'Cancel', click: function(){$(this).dialog('close'); turnOffDelete();}},
						  {text: 'Delete', click: function(){$(this).dialog('close'); confirmDeleteNetwork();}}]});
	
	
	oDialog.html('Deleting a network is permanent. Do you want to delete this network?');
}


function buildPublishConfirmDialog() 
{
	let oDialog = $('#dlgPublishConfirm');
	g_oDialogs['publishconfirm'] = '#dlgPublishConfirm';
	oDialog.dialog({autoOpen: false, position: {my: "center", at: "center", of: "#mapid"}, resizable: false, modal:true, width: 400, draggable: false,
				buttons: [{text: 'Cancel', click: function(){$(this).dialog('close');}},
						  {text: 'Publish', click: function(){$(this).dialog('close'); confirmPublishNetwork();}}]});
	
	let sHtml = '<strong id="publishreplace">WARNING: You are about to replace an existing published road network. This is not recommended.<br><br></strong>';
	sHtml += 'Choose which features to enable for this road network:<br><br>';
	sHtml += '<input type="checkbox" checked id="chkTrafficModel">&nbsp;<label for="chkTrafficModel">Enable Traffic Model</label><br>';
	sHtml += '<input type="checkbox" checked id="chkRoadWeatherModel">&nbsp;<label for="chkRoadWeatherModel">Enable Road Weather Model</label><br>';
	sHtml += '<input type="checkbox" id="chkExternalSharing">&nbsp;<label for="chkExternalSharing">Enable External Sharing of Network</label><br><br>';
	sHtml += 'Changes to the road network model after it is published are not recommended. Do you want to publish this network?';
	oDialog.html(sHtml);
}


function buildTrainingConfirmDialog() 
{
	let oDialog = $('#dlgTrainingConfirm');
	g_oDialogs['trainingconfirm'] = '#dlgTrainingConfirm';
	oDialog.dialog({autoOpen: false, position: {my: "center", at: "center", of: "#mapid"}, resizable: false, modal:true, width: 400, draggable: false,
				buttons: [{text: 'Cancel', click: function(){$(this).dialog('close');}},
						  {text: 'Train', click: function(){$(this).dialog('close'); confirmTrainingNetwork();}}]});
	
	let sHtml = 'Training the Hurricane Traffic Model requires historic National Hurricane Center forecast files and historic speed data in the IMRCP speed data adapter file format. Ensure these data sets exist before attempting to train the model.';
	oDialog.html(sHtml);
}


function buildEditPublishConfirmDialog()
{
	let oDialog = $('#dlgEditPublishConfirm');
	g_oDialogs['editpublishconfirm'] = '#dlgEditPublishConfirm';
	oDialog.dialog({autoOpen: false, position: {my: "center", at: "center", of: "#mapid"}, resizable: false, modal:true, width: 400, draggable: false,
				buttons: [{text: 'Cancel', click: function(){$(this).dialog('close');}},
						  {text: 'Continue', click: function(){$(this).dialog('close'); confirmLoadNetwork();}}]});

	oDialog.html('You are about to edit a published road network model. This is not recommended. Do you want to continue?');
}


function buildOverwritePublishConfirmDialog()
{
	let oDialog = $('#dlgOverwritePublishConfirm');
	g_oDialogs['overwritepublishconfirm'] = '#dlgOverwritePublishConfirm';
	oDialog.dialog({autoOpen: false, position: {my: "center", at: "center", of: "#mapid"}, resizable: false, modal:true, width: 400, draggable: false,
				buttons: [{text: 'Cancel', click: function(){$(this).dialog('close');}},
						  {text: 'Overwrite', click: function(){$(this).dialog('close'); confirmPublishNetwork();}}]});

	oDialog.html('You are about to overwrite a published network. This is not recommended or fully supported. Do you want to continue?');
}


function buildNetworkSelectDialog()
{
	let oDialog = $('#dlgNetworkSelect');
	g_oDialogs['networkselect'] = '#dlgNetworkSelect';
	oDialog.dialog({autoOpen: false, position: {my: "center", at: "center", of: "#map-container"}, resizable: false, width: 300, maxHeight:200, draggable: false,
	close: closeNetworkSelect});
	$(window).resize(function()
	{
		oDialog.dialog('option', 'position', {my: "center", at: "center", of: "#map-container"});
	});
	oDialog.dialog('option', 'title', 'Select A Network');
	oDialog.html('<ul style="padding: 0px 0px 0px 10px; list-style: none;" id="networkselectlist"</ul>');

}

export {buildNetworkDialog,
		buildRoadLegendDialog,
		buildCancelDialog,
		buildNetworkLegendDialog,
		buildInstructionDialog,
		buildDetectorDialog,
		buildDetectorEditDialog,
		buildDetectorStatusDialog,
		buildUploadConfirmDialog,
		buildNetworkMetadataDialog,
		buildReprocessDialog,
		buildDeleteConfirmDialog,
		buildPublishConfirmDialog,
		buildTrainingConfirmDialog,
		buildEditPublishConfirmDialog,
		buildOverwritePublishConfirmDialog,
		buildNetworkSelectDialog};
