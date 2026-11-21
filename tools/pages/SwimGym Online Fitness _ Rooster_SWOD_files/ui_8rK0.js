;(function ($, window, document, undefined) {

    'use strict';

    let $ripple = $('.js-ripple');

    $ripple.on('click.ui.ripple', function (e) {

        let $this = $(this);
        let $offset = $this.parent().offset();
        let $circle = $this.find('.c-ripple__circle');

        let x = e.pageX - $offset.left;
        let y = e.pageY - $offset.top;

        $circle.css({
            top: y + 'px',
            left: x + 'px'
        });

        $this.addClass('is-active');

    });

    $ripple.on('animationend webkitAnimationEnd oanimationend MSAnimationEnd', function (e) {
        $(this).removeClass('is-active');
    });

})(jQuery, window, document);

function ui_update_progress(id, percentage) {
    if (percentage > 100) {
        percentage = 100;
    }

    if (percentage < 0) {
        percentage = 0;
    }

    let progress_element = $('#' + id);
    progress_element.attr('aria-valuenow', percentage);
    progress_element.find('.value').html(percentage + '%');

    let calc = (progress_element.find('.bar').attr('stroke-dasharray') * (1 - percentage / 100));
    progress_element.find('.bar').attr('stroke-dashoffset', calc);
}

let previousFocusDrawer = null;

function openUIDrawer() {
    previousFocusDrawer = document.activeElement;
    $('.ui-drawer-overlay, .ui-drawer').addClass('is-active');
    $('.ui-drawer').removeAttr('inert').attr('aria-hidden', 'false');
}

function closeUIDrawer() {
    if ($('.ui-drawer').has(document.activeElement).length) {
        document.activeElement.blur();
    }

    $('.ui-drawer-overlay, .ui-drawer').removeClass('is-active');
    $('.ui-drawer').attr('inert', '').attr('aria-hidden', 'true');
    $('body').css('overflow', '');

    if (previousFocusDrawer?.isConnected) {
        previousFocusDrawer.focus();
    }
}

$(function () {
    $('.js-close-drawer').on('click', closeUIDrawer);

    $(document).on('keyup', function (e) {
        if (e.key === 'Escape' && $('.ui-drawer.is-active').length) closeUIDrawer();
    });
});