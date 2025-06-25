import {showPageoverlay, timeoutPageoverlay} from './map-common.js';
window.g_oRequirements = {'groups': 'imrcp-admin'};

let oTable;
let sOriginalGroup;
let sOriginalDeactivation;
let nDelayId;

async function initialize()
{
	$(document).prop('title', 'IMRCP Manage Users');
	oTable = $("#usertable").DataTable(
	{
		scrollY:"600px",
		scrollCollapse:true,
		paging:false,
		searching:false,
		ajax:
		{
			'url': 'api/user/table',
			'method': 'POST',
			'dataType': 'json',
			'data': {'token': sessionStorage.token}
		}
	});
	oTable.on('draw.dt', function()
	{
		$('#usertable_info').hide();
		$('#usertable tbody tr').on('dblclick', startEditUser).addClass('clickable');
		oTable.rows().data().each((oEl, nIndex) => {if (oEl[0] === sessionStorage.uname) oTable.row(nIndex).remove().draw();});
	});
	$('#btnAddUser').on('click', startAddUser);
	buildEditDialog();
}


function startAddUser()
{
	let oDialog = $('#dlgEditUser');
	oDialog.dialog('option', 'title', 'Add User');
	$('#inputUserName').val('').prop('disabled', false).removeClass('popup');
	$('#inputPassword').val('');
	$('#pwinfodiv').hide();
	$('#selectUserGroup').val('imrcp-user');
	$('#userenableddiv').hide();
	$('#radioOptEnabled').prop('checked', true);
	sOriginalGroup = sOriginalDeactivation = 'null';
	oDialog.dialog('open');
}


function startEditUser()
{
	let oTableData = oTable.row(this).data();
	let oDialog = $('#dlgEditUser');
	oDialog.dialog('option', 'title', 'Edit User');
	$('#inputUserName').val(oTableData[0]).prop('disabled', true).removeClass('popup');
	$('#inputPassword').val('');
	$('#pwinfodiv').show();
	$('#userenableddiv').show();
	sOriginalGroup = oTableData[1];
	$('#selectUserGroup').val(oTableData[1]);
	if (oTableData[2])
	{
		$('#radioOptDisabled').prop('checked', true);
		sOriginalDeactivation = oTableData[2];
	}
	else
	{
		$('#radioOptEnabled').prop('checked', true);
		sOriginalDeactivation = '';
	}
	oDialog.dialog('open');
}


function saveUser()
{
	let sDeactivation = '';
	let sEnabled = $('input[name|="radioEnabled"]:checked').prop('id');
	if (sOriginalDeactivation.length > 0 && sEnabled !== 'radioOptEnabled') // already deactivated and staying deactivated
		sDeactivation = sOriginalDeactivation; // keep original timestamp
	
	if (sOriginalDeactivation.length === 0 && sEnabled !== 'radioOptEnabled') // enabled user becoming deactivated
		sDeactivation = moment().format('YYYY-MM-DD[T]HH:mm');
	
	showPageoverlay('Saving user...');
	$.ajax(
	{
		'url': 'api/user/save',
		'method': 'POST',
		'dataType': 'json',
		'data': {'token': sessionStorage.token, 'pw': $('#inputPassword').val(), 'name': $('#inputUserName').val(), 'group': $('#selectUserGroup').val(), 'deactivation': sDeactivation}
	}).done(function()
	{
		oTable.ajax.reload();	
	}).fail(function()
	{
		showPageoverlay('Failed to save user. Try again later');
	}).always(function()
	{
		timeoutPageoverlay();
		$('#dlgEditUser').dialog('close');
	});
}


function buildEditDialog()
{
	let oDialog = $('#dlgEditUser');
	oDialog.dialog({autoOpen: false, position: {my: "center", at: "center", of: "#main-content"}, modal: true, draggable: false, resizable: false, width: 500,
		buttons: [
			{text: 'Cancel', click: function() 
				{
					oDialog.dialog('close');
				}},
			{text: 'Save', id: 'btnSaveUser', disabled: true, click: saveUser}
		]});
	
	oDialog.siblings().children('.ui-dialog-titlebar-close').remove();
	$(window).resize(function()
	{
		oDialog.dialog('option', 'position', {my: "center", at: "center", of: "#main-content"});
	});
	let sHtml = `<div class="flexbox marginbottom12"><label class="flex1">User</label><div class="flex3"><input style="width:100%;" placeholder="Enter email address" id="inputUserName" type="text"></div></div>
	<div id="pwinfodiv" class="flexbox marginbottom12"><div class="flex1"></div><div class="flex3" style="font-size:80%">leave password field blank for no change</div></div> 
	<div class="flexbox marginbottom12"><label class="flex1">Password</label><div class="flex3"><input style="width:100%;" id="inputPassword" type="text"></div></div>
<div id="usergroupdiv" class="flexbox marginbottom12" style="height:35px;"><label class="flex1">User Group</label><div class="flex3"><select style="width:100%;" id="selectUserGroup"><option value="imrcp-user">imrcp-user</option><option value="imrcp-admin">imrcp-admin</option></select></div></div>
<div id="userenableddiv" class="flexbox marginbottom 12" style="height:35px;"><input class="flex1" style="width:auto;" type="radio" id="radioOptEnabled" name="radioEnabled"><label for="radioOptEnabled">Enabled</label><input class="flex1" style="width:auto;" type="radio" id="radioOptDisabled" name="radioEnabled"><label for="radioOptDisabled">Disabled</label><div>`;
	oDialog.html(sHtml);
	$('#inputUserName').on('keyup', delayCheck);
	$('#inputUserName').on('focusout', checkUserName);
	$('#selectUserGroup, input[name|="radioEnabled"]').on('change', checkDirty);
	$('#inputPassword').on('keyup', checkDirty);
}


function checkDirty()
{
	let sEnabled = $('input[name|="radioEnabled"]:checked').prop('id');
	if ($('#inputPassword').val().length === 0 && ((sOriginalDeactivation.length === 0 && sEnabled === 'radioOptEnabled') || (sOriginalDeactivation.length > 0 && sEnabled === 'radioOptDisabled')) && sOriginalGroup === $('#selectUserGroup').val())
	{
		$('#btnSaveUser').prop('disabled', true);
		$('#btnSaveUser').addClass('ui-button-disabled ui-state-disabled');
	}
	else
	{
		$('#btnSaveUser').prop('disabled', false);
		$('#btnSaveUser').removeClass('ui-button-disabled ui-state-disabled');
	}
}


function delayCheck()
{
	if (nDelayId)
		clearTimeout(nDelayId);
	$('#btnSaveUser').prop('disabled', true);
	$('#btnSaveUser').addClass('ui-button-disabled ui-state-disabled');
	nDelayId = setTimeout(function()
	{
		$('#inputUserName').blur().focus();
	}, 500);
}


function checkUserName(oEvent)
{
	let oUname = $('#inputUserName');
	if (!oUname.val().match(/^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@(?:[a-z0-9][a-z0-9-]*[a-z0-9])+(?:\.[a-z0-9][a-z0-9-]*[a-z0-9])*(?:\.[a-z]{2,4})$/))
	{
		$('#btnSaveUser').prop('disabled', true);
		$('#btnSaveUser').addClass('ui-button-disabled ui-state-disabled');
		let oPopuptext = $('<span id="user_popup" class="show">Invalid Email Address</span>');
		if (oUname.parent()[0].getBoundingClientRect().top < 70)
			oPopuptext.addClass('popuptextbelow');
		else
			oPopuptext.addClass('popuptextabove');

		oUname.parent().addClass('popup').append(oPopuptext);
		
		
		setTimeout(function()
		{
			oUname.removeClass('popup');
			oPopuptext.remove();
		}, 2500);
		oEvent.preventDefault();
		setTimeout(function() {oUname.focus();}, 100);
		return false;
	}
	else
	{
		oUname.removeClass('popup');
		$('#user_popup').remove();
		$('#btnSaveUser').prop('disabled', false);
		$('#btnSaveUser').removeClass('ui-button-disabled ui-state-disabled');
	}
}

$(document).on('initPage', initialize);