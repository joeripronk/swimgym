// This wrapper fixes wrong placement of picker when container has margins.
var originaldatepicker = $.fn.datepicker;

$.fn.datepicker = function () {
    var result = originaldatepicker.apply(this, arguments);

    this.on('show', function (e) {
        var $target = $(this),
            $picker = $target.data('datepicker').picker,
            top;

        if ($picker.hasClass('datepicker-orient-top')) {
            top = $target.offset().top - $picker.outerHeight() - parseInt($picker.css('marginTop'));
        } else {
            top = $target.offset().top + $target.outerHeight() + parseInt($picker.css('marginTop'));
        }

        $picker.offset({top: top});
    });

    return result;
}
