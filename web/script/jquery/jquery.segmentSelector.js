import {minutesToHHmm, minutesToHH} from '../common.js';
import {addSourceAndLayers, removeSourceAndLayers,
  pointToPaddedBounds, featureInSources, getBoundsForCoordArray, iconDivFromSprite} from '../map-util.js';

const ROAD_MODEL_SOURCE = 'Road Network Model';
const POINT_SELECTION_SOURCES = ['Traffic Detectors'];

const SelectionModes = {AREA: 1, SEGMENTS: 2, POINTS: 3};


$.widget("sp.segmentSelector", {
  options: {
    map: null,
    sources: null
  },
  beginAreaSelection: function (selectedStart)
  {
    this._trigger("beforeSelection");
    this._reportTooltip.html('<ul><li>Click and drag to select an area.</li><li>Click and drag markers to adjust selected area.<li>[Enter] to proceed with selected area.</li><li>[Esc] to cancel process.</l></ul>');
    this._beginSelection(selectedStart);

    this.isSelecting = true;
    this.selectionMode = SelectionModes.AREA;
    var mymap = this.options.map;

    const feature = {
      'type': 'Feature',
      'geometry': {
        'type': 'Polygon',
        'coordinates': [[[0, 0], [1, 1], [2, 2], [0, 0]]]
      }
    };

    mymap.addSource('report-area', {type: 'geojson', data: feature});

    mymap.addLayer({
      "id": "report-area",
      "type": "fill",
      "source": "report-area",
      "paint": {
        "fill-color": "blue", "fill-antialias": false, "fill-opacity": 0.4
      }
    });



    var thisWidget = this;
    var mouseDown = e => {
      const calculateRectCoords = (p1, p2) => [[
            [p1.lng, p1.lat],
            [p2.lng, p1.lat],
            [p2.lng, p2.lat],
            [p1.lng, p2.lat],
            [p1.lng, p1.lat]]];

      var mouseMove = e2 => {
        feature.geometry.coordinates = calculateRectCoords(e.lngLat, e2.lngLat);
        mymap.getSource('report-area').setData(feature);
      };

      mymap.on('mousemove', mouseMove);
      const mouseOut = (outE) => {
        mymap.off('mousemove', mouseMove);
        mymap.off('mousedown', mouseDown);
        mymap.dragPan.enable();


        const [[p1Lng, p1Lat], p2, [p3Lng, p3Lat], p4] = feature.geometry.coordinates[0];
        const neMarker = this.ne = new mapboxgl.Marker({draggable: true})
          .setLngLat([Math.max(p1Lng, p3Lng), Math.max(p1Lat, p3Lat)])
          .addTo(mymap);

        const swMarker = this.sw = new mapboxgl.Marker({draggable: true})
          .setLngLat([Math.min(p1Lng, p3Lng), Math.min(p1Lat, p3Lat)])
          .addTo(mymap);


        this.selectedRectangle = new mapboxgl.LngLatBounds(swMarker.getLngLat(), neMarker.getLngLat());


        var dragEnd = () => {
          const l1 = neMarker.getLngLat();
          const l2 = swMarker.getLngLat();
          feature.geometry.coordinates = calculateRectCoords(l1, l2);

          this.selectedRectangle.setNorthEast([Math.max(l1.lng, l2.lng), Math.min(l1.lat, l2.lat)]);
          this.selectedRectangle.setSouthWest([Math.min(l1.lng, l2.lng), Math.min(l1.lat, l2.lat)]);
          mymap.getSource('report-area').setData(feature);
        };
        document.getSelection().removeAllRanges();

        neMarker.on("dragend", dragEnd);
        swMarker.on("dragend", dragEnd);

        mymap.off('mouseup', mouseOut);
        mymap.off('mouseout', mouseOut);
        thisWidget._trigger("initialAreaSelected");
      };

      mymap.on('mouseup', mouseOut);
      mymap.on('mouseout', mouseOut);

    };

    mymap.on('mousedown', mouseDown);
    mymap.dragPan.disable();

  },
  _beginSelection: function (selectedStart)
  {
    this.selectedStart = selectedStart;
    this._bindReportSelectionMouseEvents();
    $(document).keydown(this._onKeyPress);
  },
  beginSegmentSelection: function (selectedStart)
  {
    this.selectionMode = SelectionModes.SEGMENTS;
    this._reportTooltip.html('<ul><li>Click road segments to select them.</li><li>[Enter] to proceed with selected segments.</li><li>[Esc] to cancel process.</l></ul>');
    this._trigger("beforeSelection");
    this._beginSelection(selectedStart);
    this.isSelecting = true;

    const {map, sources} = this.options;
    addSourceAndLayers(map, sources.get(ROAD_MODEL_SOURCE));
    map.on('click', this._roadMapClickHandler);
  },
  beginPointSelection: function (selectedStart)
  {
    this.selectionMode = SelectionModes.POINTS;
    this._trigger("beforeSelection");
    this._reportTooltip.html('<ul><li>Click dector icons to select them.<li>[Enter] to proceed with selected stations.</li><li>[Esc] to cancel process.</l></ul>');
    this._beginSelection(selectedStart);

    this.isSelecting = true;

    const {map, sources} = this.options;
    POINT_SELECTION_SOURCES.forEach(source => addSourceAndLayers(map, sources.get(source)));
    map.on('click', this._symbolMapClickHandler);
  },
  endSelection: function (cancel)
  {
    if (!this.isSelecting)
      return;

    const {map, sources} = this.options;
    switch (this.selectionMode)
    {
      case SelectionModes.AREA:
        this.isSelecting = false;

        this._trigger("afterSelection");
        this.sw.remove();
        this.ne.remove();
        this.options.map.removeLayer("report-area");
        this.options.map.removeSource("report-area");
        break;
      case SelectionModes.SEGMENTS:
        this.isSelecting = false;

        removeSourceAndLayers(map, sources.get(ROAD_MODEL_SOURCE));
        map.off('click', this._roadMapClickHandler);
        this.selectedElements.clear();
        this._trigger("afterSelection");

        break;
      case SelectionModes.POINTS:
        this.isSelecting = false;

        POINT_SELECTION_SOURCES.forEach(source => removeSourceAndLayers(map, sources.get(source)));
        map.off('click', this._symbolMapClickHandler);

        this.selectedElements.forEach(marker => marker.remove());
        this.selectedElements.clear();

        this._trigger("afterSelection");

        break;

        $(document).off("keydown", this._onKeyPress);
    }
  },
  _resetSelectedItems: function (styler)
  {
    $.each(this.selectedElements, function (i, element)
    {
      element.selected = false;
      styler.styleLayer(element);
    });
    this.selectedElements.clear();
  },
  hasValidSelection: function ()
  {
    switch (this.selectionMode)
    {
      case SelectionModes.AREA:
        return true && this.selectedRectangle;
      case SelectionModes.SEGMENTS:
      case SelectionModes.POINTS:
        return this.selectedElements.size > 0;
    }
    return false;
  },
  mode: function ()
  {
    return this.selectionMode;
  },
  bounds: function ()
  {
    if (!this.hasValidSelection())
      return null;

    switch (this.selectionMode)
    {
      case SelectionModes.AREA:
        return this.selectedRectangle;
      case SelectionModes.POINTS:
        
        return getBoundsForCoordArray([...this.selectedElements.values()].map(m => [m.getLngLat().lng,m.getLngLat().lat]));
        break;
      case SelectionModes.SEGMENTS:
        const allRoadPoints = [];
        for (let roadFeature of this.selectedElements.values())
          allRoadPoints.splice(0, 0, ...roadFeature.geometry.coordinates);
        return getBoundsForCoordArray(allRoadPoints);
    }
  },
  elements: function ()
  {
    return this.selectedElements;
  },
  _create: function ()
  {
    const {map, sources, spriteDef} = this.options;
    var thisWidget = this;

    const selectedElements = this.selectedElements = new Map();

    this._roadMapClickHandler = ({target, point, lngLat}) => {
      const features = target.queryRenderedFeatures(pointToPaddedBounds(point))
        .filter(featureInSources(sources));

      if (features.length === 0)
        return;

      const feature = features[0];
      if (selectedElements.has(feature.id))
      {
        selectedElements.delete(feature.id);
        map.setFeatureState(feature, {clicked: false});
      }
      else
      {
        selectedElements.set(feature.id, feature);
        map.setFeatureState(feature, {clicked: true});
    }
    };


    const markerDivHtml = iconDivFromSprite(spriteDef['detector-hover']);
    this._symbolMapClickHandler = ({target, point}) => {
      const features = target.queryRenderedFeatures(pointToPaddedBounds(point))
        .filter(featureInSources(sources));

      if (features.length === 0)
        return;

      const feature = features[0];
      const {id} = feature;
      if (selectedElements.has(id))
      {
        selectedElements.get(id).remove();
        selectedElements.delete(id);
      }
      else
      {
        selectedElements.set(id, new mapboxgl.Marker($(markerDivHtml).get(0))
          .setLngLat(feature.geometry.coordinates)
          .addTo(map));
    }
    };


    const reportTooltip = $('<div></div>').addClass("tooltip-content over-map").hide()
      .css({position: "absolute"}).appendTo(document.body);

    this._reportTooltip = reportTooltip;

    const orientTooltipOnMouse = (e) => reportTooltip.css({left: e.pageX + 10, top: e.pageY + 10});
    reportTooltip.mousemove(orientTooltipOnMouse);


    this._onKeyPress = (event) => {
      switch (event.which)
      {
        case 13:
          setReportArea();
          this._clearReportSelectionMouseEvents();
          break;
        case 27:
          this.endSelection();
          this._clearReportSelectionMouseEvents();
          break;
      }
    };

    this._bindReportSelectionMouseEvents = selectionToolTip => {
      $("#mapid").mousemove(orientTooltipOnMouse);
      reportTooltip.html(selectionToolTip).show();
    };

    this._clearReportSelectionMouseEvents = () => {
      $("#mapid").off("mousemove", orientTooltipOnMouse).off("mousenter", orientTooltipOnMouse);
      reportTooltip.hide();
    };




    const setReportArea = () =>
    {
      if (!thisWidget.hasValidSelection())
        return;
      $("#divReportDialog").dialog("open");
      $('#txtReportRefTime').datetimepicker('setOptions', {value: new Date(this.selectedStart)});
//      $('#txtReportEnd').datetimepicker('setOptions', {value: new Date(currentMoment.valueOf())});


      const selectedBounds = this.bounds();
      const latLonTol = 0.000001;
      const viewPadding = 0.001;
      
      const paddedBounds = selectedBounds.toArray();
      paddedBounds[0][0] -= viewPadding;
      paddedBounds[0][1] -= viewPadding;
      paddedBounds[1][0] += viewPadding;
      paddedBounds[1][1] += viewPadding;
      
      this.options.map.fitBounds(paddedBounds);
      const selectedNw = selectedBounds.getNorthWest();
      const selectedSe = selectedBounds.getSouthEast();
      $("#txtLat1").val((selectedNw.lat + latLonTol).toFixed(6));
      $("#txtLng1").val((selectedNw.lng - latLonTol).toFixed(6));
      $("#txtLat2").val((selectedSe.lat - latLonTol).toFixed(6));
      $("#txtLng2").val((selectedSe.lng + latLonTol).toFixed(6));
    };




    var updateRangeFn = function (event, ui)
    {
      var offset = ui.values[0];
      var duration = ui.values[1] - offset;
      $('#spnOffset').text(minutesToHHmm(offset));
      $('#spnDuration').text(minutesToHHmm(duration, true));
    };

    var subWindowSlider = $("#divSubOffsetSlider").labeledSlider({
      min: -1440,
      range: true,
      max: 480,
      step: 30,
      values: [0, 30],
      ticks: true,
      labelValues: [-1440, -1200, -960, -720, -480, -240, 0, 240, 480],
      defaultLabelFn: minutesToHH,
      slide: updateRangeFn,
      change: updateRangeFn
    });


    $("#radTypeReport").click(function ()
    {
      $(".SubscriptionOnly").hide();
      var subWindowSlider = $("#divSubOffsetSlider");
      subWindowSlider.labeledSlider('option', 'values', [0, 30]);
      subWindowSlider.labeledSlider('option', 'min', -1440);
      subWindowSlider.labeledSlider('option', 'labelValues', [-1440, -1200, -960, -720, -480, -240, 0, 240, 480]);
      $(".ReportOnly").show();
      $('#spnType').text("Report");
    });
    $("#radTypeSubscription").click(function ()
    {
      $(".ReportOnly").hide();
      var subWindowSlider = $("#divSubOffsetSlider");
      subWindowSlider.labeledSlider('option', 'values', [0, 30]);
      subWindowSlider.labeledSlider('option', 'min', -240);
      subWindowSlider.labeledSlider('option', 'labelValues', [-240, -120, 0, 120, 240, 360, 480]);
      $(".SubscriptionOnly").show();
      $('#spnType').text("Subscription");
    });

    $("#radTypeReport").click();

    $("#lstReportObstypes").change(function ()
    {
      var jqThis = $(this);
      var selectedVal = jqThis.val();
      if (selectedVal.length > 5)
      {
        selectedVal.splice(5, selectedVal.length - 5);
        jqThis.val(selectedVal);
      }

      if (selectedVal.length === 1)
        $('#trMinMax').show();
      else
        $('#trMinMax').hide();
    });

    $("#lstReportObstypes").change();


    $('#divReportDialog').on('dialogclose', function (event)
    {
      thisWidget.endSelection();

      thisWidget._clearReportSelectionMouseEvents();
    });

    $("#btnSubmitReport").click(function ()
    {
      const obstypeList = $('#lstReportObstypes').val();

      const offset = subWindowSlider.labeledSlider('values', 0);
      const duration = subWindowSlider.labeledSlider('values', 1) - offset;

      const elementIds = [...thisWidget.elements().keys()];

      const requestData = {
        geo: JSON.stringify(thisWidget.options.map.getSource('report-location')._data.features),
        format: $('#lstFormat').val(),
        offset: offset,
        duration: duration,
        elementIds: elementIds,
        elementType: elementIds.length === 0 ? null : thisWidget.mode(),
        name: $('#txtName').val(),
        description: $('#txtDescription').val()
      };
      if (obstypeList.length > 0)
      {
        requestData.obsTypeId = obstypeList;
        if (obstypeList.length === 1)
        {
          requestData.maxValue = $('#txtMax').val();
          requestData.minValue = $('#txtMin').val();
        }
      }
      else
      {
        alert('At least one Obstype must be selected.');
        return;
      }

      if ($('#radTypeReport').prop("checked"))
      {
        requestData.reftime = $('#txtReportRefTime').datetimepicker('getValue').getTime();
        requestData.starttime = requestData.reftime + offset * 60000;
        requestData.endtime = requestData.starttime + duration * 60000;
      }
      else
      {
        requestData.cycle = $('input[name="interval"]:checked').val();
      }
	  requestData.token = sessionStorage.token;

      $.ajax({url: 'api/reports/add',
        type: 'post', dataType: 'json',
        data: requestData,
        success: function (data)
        {
          $('#spnName').text(data.name);
          $('#spnDesc').text(data.desc);
          $('#spnUuid').text(data.uuid);

          let url = window.location.href;
          url = url.substring(0, url.lastIndexOf('/')) + '/reports/' + data.uuid + '/files/[filename]';
          $('#spnUrl').text(url);

          $('#divSubInput').hide();
          $('#divSubResult').show();
          $("#divReportDialog").dialog("option", "position", {my: "center", at: "center", of: '#map-container'});
        },
        error: function ()
        {
          $('#divSubInput').hide();
          $('#divSubError').show();
        }
      });


      $("#btnSubmitReport").hide();
      $("#btnClose").val('Close');
    });

    $("#btnClose").click(() => $("#divReportDialog").dialog("close"));


  },
  _destroy: function ()
  {
  },
  _setOption: function (key, value)
  {
    this._super(key, value);
  }
});

export {SelectionModes};