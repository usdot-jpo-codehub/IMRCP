function setTokenCookie(sToken, nExpires)
{
	let oDate = new Date();
	oDate.setTime(oDate.getTime() + nExpires);
	document.cookie = 'token=' + sToken + ';expires=' + oDate.toUTCString() +';path=/';
}


