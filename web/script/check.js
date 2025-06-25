import {startSubmitBug, createMenu} from './map-common.js';

function check()
{
	let sToken = sessionStorage.token;
	if (sToken === undefined || sToken.length === 0)
	{
		document.location="/"; // redirect to login
		return;
	}
	else
	{
		let bValidGroup = false;
		for (let sGroup of sessionStorage.groups.split(';').values())
		{
			if (g_oRequirements.groups.indexOf(sGroup) >= 0)
			{
				bValidGroup = true;
				break;
			}
		}
		if (!bValidGroup)
		{
			document.location="/"; // redirect to login
			return;
		}

		$.post("api/auth/check",
		{
			"token": sToken
		},
		function(oData, oStatus)
		{
			sToken = oData.token;
			if (sToken === undefined || sToken.length === 0)
			{
				document.location="/"; // redirect to login
				return;
			}
		});
	}

	$("#uname").text(sessionStorage.uname); // set menu user name
	createMenu();
	$(document).trigger("initPage");
}

$(document).ready(check);
