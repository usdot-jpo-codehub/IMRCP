$.widget("sp.divStack", {
  options: {
    showInactiveHeaders: false
  },
  _create: function ()
  {
    this.element.addClass('divStack ui-widget');
    var layerHeaders = $('<div class="ui-widget-header"><ul class="w3-ul"></ul></div>');
    var layerHeadersList = layerHeaders.find('ul');
    this.layerHeadersList = layerHeadersList;
    var layerDeck = $('<div class="divStackDeck"></div>');
    this.deckLayers = this.element.children('div').detach().appendTo(layerDeck).addClass('ui-widget-content divStackLayer');
    
    $(this.element).append(layerDeck).append(layerHeaders);
    
    this.jqEnabledLayers = this.deckLayers;
   
    var thisWidget = this;
    
    var headerMouseEnter = function(){$(this).addClass("ui-state-hover");};
    var headerMouseLeave = function(){$(this).removeClass("ui-state-hover");};
    var headerMouseClick = function(){

      var jqHeader = $(this);
      if(jqHeader.hasClass("ui-state-active"))
      {
        jqHeader.removeClass("ui-state-active");
        thisWidget.element.removeClass("ui-state-active");
        jqHeader.find('i.fa').removeClass("fa-angle-double-down").addClass("fa-angle-double-up");
       
      }
      else
      {
        layerHeadersList.find("li").removeClass("ui-state-active");
        jqHeader.addClass("ui-state-active");
        thisWidget.element.addClass("ui-state-active");
        jqHeader.find('i.fa').removeClass("fa-angle-double-up").addClass("fa-angle-double-down");
      }
          

      layerDeck.find(".divStackLayer").hide();

      //only if active
      thisWidget.jqEnabledLayers.filter(jqHeader.data("legendDiv")).show();


      var eventData = {previous: thisWidget.selectedHeader, new: jqHeader.get(0)};
      thisWidget.selectedHeader = eventData.new;

      if(eventData.previous !== eventData.new)
        thisWidget._trigger("updateSelected", null, eventData);
    };
    /*
    const openCloseIcon = $('<i class="fa fa-angle-double-up"></i>');
    
    const openCloseHeader = $('<li class="legend-header  ui-state-default"></li>')
      .append(openCloseIcon)
      .mouseenter(headerMouseEnter)
      .mouseleave(headerMouseLeave)
      //.appendTo(layerHeadersList)
      .click(e => {
        if(openCloseIcon.hasClass("fa-angle-double-up"))
        {
          openCloseIcon.removeClass("fa-angle-double-up").addClass("fa-angle-double-down");
          layerDeck.addClass("ui-state-active");
        }
        else
        {
          openCloseIcon.removeClass("fa-angle-double-down").addClass("fa-angle-double-up");
          layerDeck.removeClass("ui-state-active");
        }
      });*/
      
    this.deckLayers.each( function(index, div)
    {
      var jqThis = $(this);
      var header = $('<li class="legend-header"><span class=legend-header-label>' + jqThis.prop('title') + '</span><span class="legend-header-icon"><i class="fa fa-angle-double-up"></i></span></li>');
      header.data("legendDiv", jqThis);
      jqThis.data("legendHeader", header);
      header.addClass("ui-state-default");
      header.mouseenter(headerMouseEnter);
      header.mouseleave(headerMouseLeave);
      header.click(headerMouseClick);
      layerHeadersList.append(header);
    });
    
    this.layerHeadersList = layerHeadersList.find("li");
    
        $(this.element).show();
  },
  activate: function(divSelector)
  {
    this.jqEnabledLayers = this.jqEnabledLayers.add($(divSelector));
    
    this.deckLayers.hide()
            .filter(divSelector)
            .data('legendHeader').click().show();
    
  },
  deactivate: function(divSelector)
  {
    this.jqEnabledLayers = this.jqEnabledLayers.not(divSelector);
    var layer = this.deckLayers.filter(divSelector).hide();
    
   if(!this.options.showInactiveHeaders)
      layer.data('legendHeader').hide();
    
    if(this.jqEnabledLayers.length > 0)
      this.jqEnabledLayers.first().data("legendHeader").click();
    else
      $(this.element).hide();
  },
  _destroy: function ()
  {
    // remove generated elements
    this.uiDialog.remove();
  },
  _setOption: function (key, value)
  {
    this._super(key, value);
  },
  enableLayer: function (layerId)
  {
    
  },
  disableLayer: function (layerId)
  {
    
  }
});

