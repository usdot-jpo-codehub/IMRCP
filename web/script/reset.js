import {swapMsg, enterKey} from './login.js';

let aAllMsgs = [];

const validPword = (sPw) =>
{
	if (!sPw.match(/[a-zA-Z0-9\!\@#\$%\^&\*\(\)\-_]{8,}/)) // length and valid characters
		return false;
	
	if (!sPw.match(/(?=.*[a-z])/)) //  at least one lower case
		return false;
	
	if (!sPw.match(/(?=.*[A-Z])/)) //  at least one upper case
		return false;
	
	if (!sPw.match(/(?=.*[0-9])/)) //  at least one digit
		return false;
	
	if (!sPw.match(/(?=.*[\!\@#\$%\^&\*\(\)\-_])/)) //  at least symbol
		return false;
		
	return true;
};


function submitReset(oEvent)
{
	oEvent.preventDefault();
	swapMsg('', aAllMsgs);
	
	let sPw1 = $('#pword1').val();
	let sPw2 = $('#pword2').val();
	
	if (sPw1 !== sPw2)
	{
		swapMsg('pwordmsg', aAllMsgs);
		return;
	}
	
	if (!validPword(sPw1))
	{
		swapMsg('blankmsg', aAllMsgs);
		return;
	}
	
	let sKey = new URLSearchParams(window.location.search).get('key');
	if (sKey === null || !sKey.match(/^[0-9a-f]{64}$/))
	{
		swapMsg('keymsg', aAllMsgs);
		return;
	}
	
	$.ajax(
	{
		'url': 'api/auth/update',
		'method': 'POST',
		'dataType': 'json',
		'data': {'key': sKey, 'pword': sPw1}
	}).done(function (oData)
	{
		if (oData.status === 'success')
		{
			swapMsg('successmsg', aAllMsgs);
			setTimeout(function() 
			{
				document.location = '/';
			}, 3000);
		}
	});
	
}


function init()
{
	aAllMsgs = ['successmsg', 'keymsg', 'pwordmsg', 'blankmsg'];
	$('#btnSubmit').on('click', submitReset);
	$('#pword1').focus(); // set focus to pword1 field
	$('#pword2').on('keydown', {'sel': '#btnSubmit'}, enterKey);
}

$(document).ready(init);
