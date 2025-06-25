import {minutesToHHmm} from './common.js';
import {timeoutPageoverlay, showPageoverlay} from './map-common.js';
{
	let sDeleteId;
	let sDeleteName;
  window.g_oRequirements = {'groups': 'imrcp-user;imrcp-admin'};
  $(document).on('initPage', function ()
  {
	$(document).prop('title', 'IMRCP View Reports');
	$('#dlgDelete').dialog({autoOpen: false, position: {my: 'center', at: 'center', of: 'body'}, width: 400, modal: true, resizable: false, draggable: false, buttons:
			[
				{text: 'Go Back', click: function() {sDeleteId = undefined; $(this).dialog('close');}},
				{text: 'Confirm Delete', click: deleteSub}
			]});
	$('#dlgDelete').html('Are you sure you want to delete this report/subscription? This cannot be undone and all associated data files will not be available for download.');
    var dateFormat = "MMM D HH:mm [UTC]";
    var ulFiles = $('#ulFiles');

    $.ajax({
      url: 'api/reports/list',
      type: 'get',
      dataType: 'json',
      success: function (subs, status, xhr)
      {
        var userSubs = $('#divUserSubs');
        var userReports = $('#divUserReports');
        userSubs.text('');
        userReports.text('');
        for (var i = 0; i < subs.length; ++i)
        {
          var sub = subs[i];
          var newSub =
                  ' <br /><div class="w3-card w3-white">' +
                  '  <header class="w3-container w3-sand"><h4 class="w3-left">';


          var subName = sub.name;
          if (!subName || subName.length === 0)
            subName = "(no name provided)";
          if (sub.fulfilled)
          {
            newSub += '<a href="';
            if (sub.isReport)
              newSub += 'api/reports/download/' + sub.uuid + '/latest';
            else
              newSub += '#';
            newSub += '">' + subName + '</a>';
          }
          else
            newSub += subName + ' (pending fulfillment)';

          newSub += '</h4>';
			newSub += `<h4 id="del_${sub.uuid}" name="${sub.name}" class="w3-right clickable" style="margin-left:10px"><i class="fa fa-trash"></i></h4>`;
          if (sub.lastAccessed)
            newSub += '         <h4 class="w3-right">Downloaded: ' + moment(sub.lastAccessed).utc().format(dateFormat) + '</h4> ';
		
          newSub +=
                  '     </header>' +
                  '     <div class="w3-container">' +
                  '    <p class="w3-left sub-date"><span class="sub-date">Created: ' + moment(sub.created).utc().format(dateFormat) + '</span>';
          if (sub.isSubscription)
          {
            newSub += '<br/>Interval: ' + sub.cycle + ' minutes' +
                    '<br />Offset: ' + minutesToHHmm(sub.offset, false) +
                    '<br/>Duration: ' + minutesToHHmm(sub.duration, true) + '</p>';
          }
          else
          {
              newSub += '<br/>Start: ' + moment(sub.startMillis).utc().format(dateFormat) +
                    '<br />End: ' + moment(sub.endMillis).utc().format(dateFormat) + '</p>';
            //include report start/end?
          }
          newSub += '</p><p class="w3-right">';

          if(sub.elementType)
          {
            newSub +='Elements: ' +  sub.elementCount + (sub.elementType === 2 ? ' segment' : ' detector') ;
            if(sub.elementCount > 1)
              newSub += 's';
          }
          else
            newSub += 'Area: ' + sub.lat1 + ', ' + sub.lon1 + ', ' + sub.lat2 + ', ' + sub.lon2;

          if (sub.obstypes)
          {
            if (sub.obstypes.length === 1)
            {
              var obstype = sub.obstypes[0];
              newSub += '<br/>Obs: <span class="sub-obs">' + obstype.name + '</span>';
              if (obstype.min)
                newSub += '<br />Min: ' + obstype.min;
              if (obstype.max)
                newSub += '<br/>Max: ' + obstype.max + '</p>';
            }
            else
            {
              var obsListCount = sub.obstypes.length === 5 ? 5 : Math.min(4, sub.obstypes.length);
              newSub += '<br/>Obs: ';
              for (var index = 0; index < obsListCount; ++index)
              {
                if (index > 0)
                  newSub += ', ';
                newSub += '<span class="sub-obs">' + sub.obstypes[index].name + '</span>';
              }

              if (obsListCount < sub.obstypes.length)
                newSub += '....(' + (sub.obstypes.length - obsListCount) + ' more)';
            }
          }

          newSub +=
                  '  </div>' +
                  '</div>';

          newSub = $(newSub);

          if (sub.isSubscription)
          {
            var link = newSub.find('a');
            link.data('uuid', sub.uuid);

            link.click(function ()
            {
              $('#subFilesHeader').text($(this).text());
              $('#divSubFilesError').hide();

              var uuid = $(this).data('uuid');
              var fileUrlBase = 'api/reports/files/' + uuid;
              $.ajax({
                url: fileUrlBase,
                type: 'get',
                dataType: 'json',
                success: function (files, status, xhr)
                {
                  var items = '';

                  for (var i = 0; i < files.length; ++i)
                  {
                    var file = files[i];
                    items += '<li class="w3-' + (i % 2 === 0 ? 'white' : 'sand') + '"><p class="name"><a href="api/reports/download/' + uuid + '/' + file + '">' + file + '</a></p></li>';
                  }
                  //could re-use existing elements and update text instead of always dumping and recreating
                  ulFiles.empty();
                  $(items).appendTo(ulFiles);
                  ulFiles.show();
                  //figure out some paginating widget
//                  var monkeyList = new List('divFiles', {
//                    valueNames: ['name'],
//                    page: 3,
//                    pagination: true
//                  });
//reIndex()

//                  $('#ulFiles').easyPaginate({
//                    paginateElement: 'li',
//                    elementsPerPage: 3,
//                    prevButton: false,
//                    nextButton: false,
//                    effect: 'climb'
//                  });

                },
                error: function (xhr, status, error)
                {
                  $('#divSubFilesError').show();
                }
              });
            });
          }

          newSub.appendTo(sub.isReport ? userReports : userSubs);
        }
		$('h4.clickable').on('click', confirmDelete);
      },
      error: function (xhr, status, error)
      {
        $('#divUserSubs').text('Error loading subscriptions.');
        $('#divUserReports').text('Error loading reports.');
      }
    });
  });
  
  function confirmDelete()
  {
	  sDeleteId = $(this).prop('id');
	  sDeleteName = $(this).attr('name');
	  $('#dlgDelete').dialog('option', 'title', 'Delete ' + sDeleteName);
	  $('#dlgDelete').dialog('open');
  }
  
  function deleteSub()
  {
	  showPageoverlay(`Deleting ${sDeleteName}`);
	$.ajax(
	{
		'url': 'api/reports/delete/',
		'method': 'POST',
		'dataType': 'text',
		'data': {'token': sessionStorage.token, 'id': sDeleteId.substring(4)}
	}).done(function() 
	{
		showPageoverlay('Success!');
		$('#' + sDeleteId).parents('.w3-card').remove();
	}).fail(function()
	{
		showPageoverlay('Delete failed. Try again later');
	}).always(function()
	{
		$('#ulFiles').empty().hide();
		$('#subFilesHeader').text('');
		$('#divSubFilesError').hide();
		sDeleteId = sDeleteName = undefined;
		$('#dlgDelete').dialog('close');
		timeoutPageoverlay(2000);
	});
	  
	  
  }
}
