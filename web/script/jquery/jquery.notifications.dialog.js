$.widget("sp.notificationDialog", {
  options: {
    position: null,
    dateFormatFutureDay: "ddd, h:mm a",
    dateFormatToday: "h:mm a",
    displayUtcTimes: false,
    displayOnNewAlerts: true
  },
  _getDisplayOnNewAlerts: function()
  {
    if($.isFunction(this.options.displayOnNewAlerts))
      return this.options.displayOnNewAlerts();
    else return this.options.displayOnNewAlerts;
  },
  processNotifications: function (notifications)
  {
    var thisDialog = this;
    var foundIds = {};
    var newNotifications = []; // build list of notifications in response that we have not receieved
    var oldNotifications = []; // build list of notifications in response that we have previously received and that are still active

    $.each(notifications, function (index, notification)
    {
      if (notification.cleared > 0)
        notification.cleared = true;
      else
        notification.cleared = false;

      foundIds[notification.id] = true;
      if (thisDialog.notifications[notification.id])
      {
        var currentNotification = thisDialog.notifications[notification.id];
        if (!currentNotification.cleared && notification.cleared)
        {
          currentNotification.element.remove();
          currentNotification.cleared = true;
        }
        else
          oldNotifications.push(notification);
      }
      else if (notification.cleared)
        return;
      else
      {
        newNotifications.push(notification);
      }
    });

    var count = 0;
    $.each(thisDialog.notifications, function (index, notification)
    {
      if (!foundIds[notification.id])
      {
        notification.element.remove();
        delete thisDialog.notifications[notification.id];
      }
      else if (!notification.cleared)
        ++count;
    });

    $.each(oldNotifications, function (index, notification)
    {
      thisDialog._updateNotification(notification);
    });


    count += newNotifications.length;
    this.spnCount.text(count + ' ' + (count === 1 ? 'Notification' : 'Notifications'));
    if (newNotifications.length > 0)
    {
      if (thisDialog._getDisplayOnNewAlerts())
      {
        if (!thisDialog.uiDialog.dialog("isOpen"))
          thisDialog.open();
      }

      $.each(newNotifications, function (index, notification)
      {
        thisDialog._addNotification(notification);
      });
    }

    if (newNotifications.length > 0 && thisDialog.uiDialog.dialog("isOpen"))
      thisDialog.uiDialog.parent('.ui-dialog').effect("shake");
  },
  open: function ()
  {
    this.uiDialog.dialog("open");
  },
  _addNotification: function (notification)
  {

    this.notifications[notification.id] = notification;

    var divNotification = $(
            '<div class="sp-notification alert-' + notification.typeId + '"><div class="sp-notification-predicted">P</div><div class="alert-icon"></div><div class="sp-notification-content-left">' +
            '<div class="sp-notification-content-left-top"><span class="sp-notification-type"></span>' +
            '<span class="sp-notification-time-started"></span>' +
            '</div>' +
            '<span class="sp-notification-location"></span>' +
            '</div><div class="sp-notification-content-right">' +
            '<span class="sp-notification-duration"></span>' +
            '<br />' +
            '<span class="sp-notification-time-issued-cleared"></span></div></div>');

    notification.element = divNotification;


    var spnType = divNotification.find('.sp-notification-type');
    var spnLocation = divNotification.find('.sp-notification-location');

    spnLocation.html(notification.description);
    spnType.text(notification.typeName);



    var thisDialog = this;
    divNotification.prependTo(this.uiDialog).click(function ()
    {
      thisDialog._trigger("selectNotification", null, notification);
    });
    this._updateNotification(notification);

  },
  _updateNotification: function (notification)
  {

    var divNotification = this.notifications[notification.id].element;
    var duration = notification.end - notification.start;
    duration /= 1000 * 60;

    if (notification.start.valueOf() - new Date().getTime() > 1000 * 60 * 15)
      divNotification.find('.sp-notification-predicted').show();

    var spnDuration = divNotification.find('.sp-notification-duration');
    var spnStartTime = divNotification.find('.sp-notification-time-started');
    var spnIssueClearTime = divNotification.find('.sp-notification-time-issued-cleared');
    spnDuration.text('Duration: ' + duration + ' minutes');
    spnStartTime.text('Estimated Start Time: ' + this._formatTime(notification.start));

    spnIssueClearTime.text('Issued: ' + this._formatTime(notification.issued));

  },
  _formatTime: function (time)
  {
    time = moment(time);
    if (this.options.displayUtcTimes)
      time = time.utc();
    var now = moment();

    var dateFormat;
    if (now.dayOfYear() === time.dayOfYear() && now.year() === time.year())
      dateFormat = this.options.dateFormatToday;
    else
      dateFormat = this.options.dateFormatFutureDay;

    return time.format(dateFormat);
  },
  _create: function ()
  {
    this.notifications = {};
    var dialogOptions = {
      autoOpen: false,
      resizable: false,
      draggable: false,
      //width: 'auto',
      modal: true,
      // dialogClass: "no-title-form",
      //minHeight: "380",
      height: "400",
      minWidth: "700"
    };
    if (this.options.position !== null)
      dialogOptions.position = this.options.position;

    this.uiDialog = $('<div title="0 Notifications"><!--<button>Add</button>--></div>')
            .appendTo(this.element)
            .dialog(dialogOptions);

    var thisWidget = this;
    this.uiDialog.find('button').click(function ()
    {

      var testContainer = $('<div style="width: 100%; height:0em; border:1px solid black;"><div style="position: relative;width: 100%; height:100%; left:1000px;position:absolute; border:1px solid black; z-index:10000;">Content</div></div>').prependTo(thisWidget.uiDialog)
              .effect("size", {
                to: {height: '3em'}
              }, 1000).find('div').animate({left: '0px'}, {queue: false, duration: 1500});

    });

    var dialogParent = this.uiDialog.parent('.ui-dialog');
    dialogParent.addClass('sp-notification-dialog');
    var titleBar = dialogParent.find('.ui-dialog-titlebar');

    this.spnCount = titleBar.find('.ui-dialog-title');
    //  var uiclearedCountSpan = uiTitleCountSpan.find('.count-cleared');
    // uiclearedCountSpan.attr('style', 'margin-left:1em;');
  },
  _destroy: function ()
  {
    // remove generated elements
    this.uiDialog.remove();
  },
  _setOption: function (key, value)
  {
    if (key === "position")
    {
      this.uiDialog.dialog("option", "position", value);
    }
    this._super(key, value);
  }
});