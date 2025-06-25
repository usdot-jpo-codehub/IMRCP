let aAllMsgs = [];

function enterKey(oEvent)
{
	if (oEvent.which === 13) // keycode for enter key
	{
		$(oEvent.data.sel).click(); // implicit click
		return false;
	}
	return true;
}


function isEmpty(sVal)
{
	return (sVal === undefined || sVal.length === 0);
}


function disable(bState)
{
	$('#uname').prop("disabled", bState); // lock controls
	$('#pword').prop("disabled", bState);
	$('#btnLogin').prop("disabled", bState);
}


function errorConn(oJqXHR, sStatus, sError)
{
	swapMsg('connmsg', aAllMsgs); // show message leave fields alone
	disable(false); // re-enable controls
}


function loginRep(oData, oStatus, oJqXHR)
{
	let sStatus = oData.status;
	if (isEmpty(sStatus) || sStatus === "failure")
	{
		swapMsg('usermsg', aAllMsgs);
		disable(false); // re-enable controls
		$('#pword').val("").focus(); // reset password and focus there
	}
	else
	{
		sessionStorage.token = oData.token; // store session token
		sessionStorage.groups = oData.groups;
		document.cookie = 'token=' + oData.token + ';path=/';
		if (oData.page !== undefined)
			document.location = oData.page;
		else
			document.location = "map.html";
	}
}


function login()
{
	swapMsg('', aAllMsgs);
	
	disable(true); // temporarily disable controls
	sessionStorage.uname = $('#uname').val(); // save username

	$.ajax("api/auth/login",
	{
		method: "POST",
		dataType: 'json',
		data: {"uname": sessionStorage.uname, "pword": $('#pword').val()},
		error: errorConn,
		success: loginRep,
		timeout: 10000
	});
}


function requestReset(oEvent)
{
	oEvent.preventDefault();
	let sUname = $('#uname').val();
	if (isEmpty(sUname)) // || !sUname.match(/^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@(?:[a-z0-9][a-z0-9-]*[a-z0-9])+(?:\.[a-z0-9][a-z0-9-]*[a-z0-9])*(?:\.[a-z]{2,4})$/))
	{
		$('#uname').focus();
		swapMsg('unamemsg', aAllMsgs);
		return false;
	}
	
	swapMsg('', aAllMsgs);
	
	$.ajax(
	{
		'url': 'api/auth/reset',
		'method': 'POST',
		'dataType': 'json',
		'data': {'uname': sUname}
	}).done(function(oData)
	{
		if (oData.status === 'lockout')
			swapMsg('toomanymsg', aAllMsgs);
		
		if (oData.status === 'success')
			swapMsg('resetmsg', aAllMsgs);
	});
}


function swapMsg(sName, aMsgs)
{
	for (let sMsg of aMsgs.values())
	{
		if (sName === sMsg)
			$(`#${sName}`).show();
		else
			$(`#${sMsg}`).hide();
	}
}


function init()
{
	let sToken = sessionStorage.token;
	if (sToken != undefined && sToken.length > 0)
	{
		$.post("api/auth/logout",
		{
			"token": sToken
		},
		function(sData, oStatus)
		{
			sessionStorage.clear();
		});
	}
	aAllMsgs = ['usermsg', 'connmsg', 'unamemsg', 'toomanymsg', 'resetmsg'];
	
	document.cookie = 'token=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;'; 
	$('#uname').focus(); // set focus to username field
	$('#btnLogin').click(login);
	$('#pword').on('keydown', {'sel': '#btnLogin'}, enterKey);
	$('#btnReset').on('click', requestReset);
}

$(document).ready(init);

export {swapMsg, enterKey, isEmpty};