async function generateSymbolLegendContent(legendEntries)
{
  let legendEntryHtml = '';
  for (let nIndex = 0; nIndex < legendEntries.layers.length; nIndex++)
  {
		let sIcon = legendEntries.layers[nIndex].layout['icon-image'];
		let sLabel = legendEntries.legendElements[nIndex].label;
		let oImage;
		const pImageLoad = new Promise(resolve =>
		{
		  oImage = new Image();
		  oImage.onload = resolve;
		  oImage.src = `images/icons/${sIcon}.png`;
		});
		await pImageLoad;
		legendEntryHtml += `<li>
		<div style="width: ${oImage.width}px; height: ${oImage.height}px; background: url(images/icons/${sIcon}.png); display:inline-block;vertical-align:middle"></div>
		<span style="padding-left:5px;">${sLabel}</span>
		</li>`;
	  
  }
  return legendEntryHtml;
};

const generateFillLegendContent = legendEntries =>
  {
    let legendEntryHtml = '';
    for (let legendEntry of legendEntries)
    {
      if (legendEntry.color === "#ffffff")// if the color is white, then use a black background with a white rounded square inside it
        legendEntryHtml += '<li><i class="fa fa-square-o" style="color: #000"></i> ';
      else
        legendEntryHtml += `<li><i class="fa fa-stop " style="color: ${legendEntry.color}; opacity: 1.0"></i>`;

      legendEntryHtml += legendEntry.label + '</li>';
    }
    return legendEntryHtml;
  };


const fillLegendDiv = (sources, legendContainer) => async (e) => {
    const legend = legendContainer.find('.legend-content');
    const legendHeader = legendContainer.find('.legend-heading .heading-title');
    const dialogIcon = $(e.target);
    const selectedObstypeSourceId = dialogIcon.data('source-id');
	
	if (legendHeader.text() === selectedObstypeSourceId && legend.parent().is(":visible"))
	{
		legend.parent().hide();
		$('.legend-header').show();
		return;
	}
	$('.legend-header').hide();
    legendHeader.text(dialogIcon.data('obstype-label'));

    let newLegendContent = '<ul class="layer-legend w3-ul ul-no-border">';
    const source = sources.get(selectedObstypeSourceId);
    switch (source.type)
    {
      case 'fill' :
      case 'line' :
        newLegendContent += generateFillLegendContent(source.legendElements);
        break;
      case 'symbol':
        newLegendContent += await generateSymbolLegendContent(source);
        break;
    }

    newLegendContent += '</ul>';
	legend.html(newLegendContent);
    legend.parent().show();
	if (source.legendElements.length > 15)
	{
		let dHeight = legendContainer.outerHeight();
		let dLegendHeight = legendContainer.siblings('.divStackLayer').filter((n, e)=>$(e).css('display') === 'block').outerHeight();
		if (dHeight > dLegendHeight)
		{
			legendContainer.css({'position': 'relative', 'bottom': dHeight - dLegendHeight});
		}
	}
	else
	{
		legendContainer.css({'bottom': 0});
	}
    
  };


const setupObstypeLegendDivs = sources =>
{
  $('#legendDeck .divStackDeck .divStackLayer').each((idx, div) => {

    const legendContainer = $('<div style="display: none; float:left;" class="ui-widget-content"></div>')
      .insertAfter(div);

    const legendHeader = $('<span class="w3-sand w3-round legend-heading clickable"><span class="heading-title"></span></span>')
      .appendTo(legendContainer);
    const legendIcon = $('<i style="padding-right:3px; padding-left:6px; padding-top:3px" class="fa fa-times clickable fa-lg w3-right"></i>')
      .appendTo(legendHeader)
      .click(e => {legendContainer.hide(); $('.legend-header').show();});

    const legendContent = $('<div class="legend-content" style="margin-top:5px;margin-bottom:5px"></div>').appendTo(legendContainer);

    $(div).find('i')
      .each((idx, icon) => {
        const obstypeListItem = $(icon).parents(('li'));
        let sourceId = obstypeListItem.find('input').val();
        let source = sources.get(sourceId);
		if (source === undefined)
		{
			sourceId = obstypeListItem[0].textContent;
			source = sources.get(sourceId);
		}

        if (source && source.legendElements.length > 0)
        {
          $(icon).data('source-id', sourceId);
          $(icon).data('obstype-label', sourceId);
        }
      });

    $(div).find('i')
      .click(fillLegendDiv(sources, legendContainer));

  });
};


const buildPaneSourceInputs = (map, sources, initialLayers,addSourceAndLayersFn, sLastLayer) => {
  const initialSources = new Set(initialLayers);
  const groupMap = new Map();
  const settingsDiv = $('#settingsPane');
  const viewPane = $('#viewsPane');
  groupMap.set('Groups', viewPane);
  groupMap.set('Settings', settingsDiv);

  sources.forEach(source => {

    const {group, mapboxSource, id} = source;

    if (!groupMap.has(group))
      groupMap.set(group, $('<div title="' + group + '"><ul class="w3-ul ul-no-border obstype-pane-list"></ul></div>').insertBefore(viewPane));

    const groupDiv = groupMap.get(group);
    const groupUl = groupDiv.find('ul.obstype-pane-list');
    const sourceLi = $(`<li><label><input id="${source.layers[0].metadata.obstype}" type="checkbox" value="${id}"/>${id}</label><i class="w3-right	fa fa-window-restore clickable" aria-hidden="true"></i></li>`).appendTo(groupUl);

    if (initialSources.has(id))
    {
      sourceLi.find('input').prop('checked', true);
      addSourceAndLayersFn(map, source, sLastLayer);
    }
  });
};

export {setupObstypeLegendDivs, buildPaneSourceInputs};