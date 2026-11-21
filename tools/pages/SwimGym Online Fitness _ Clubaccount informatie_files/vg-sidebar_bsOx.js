function saveSidebarState() {
    var $sidebar = $('.sidebar');

    var collapsed = $sidebar.hasClass('sidebar-collapsed');
    document.cookie = 'sidebar_is_collapsed=' + (collapsed ? 1 : 0) + ';path=/';

    var openMenu = $sidebar.find('.has-submenu.open').attr('data-menu') || $sidebar.find('.has-submenu.previously-open-menu').attr('data-menu');
    document.cookie = 'sidebar_previously_open_menu=' + (openMenu || '') + ';path=/';
}

$(function () {
    var Accordion = function (el, multiple) {
        this.el = el || {};
        this.multiple = multiple || false;
        var links = this.el.find('.sidebar-link');
        links.on('click', {el: this.el, multiple: this.multiple}, this.dropdown);
    };

    Accordion.prototype.dropdown = function (event) {
        var $this = $(this);
        var $submenu = $this.next('.submenu');
        var $menu = $submenu.closest('li');

        if (!$this.closest('li').hasClass('has-submenu')) {
            return;
        }

        var menuIsOpen = $menu.hasClass('open') || $menu.hasClass('previously-open-menu');

        if (menuIsOpen) {
            $submenu.slideUp(function () {
                $menu.removeClass('previously-open-menu');
                saveSidebarState();
            });
            $menu.removeClass('open');
        } else {
            $submenu.slideDown();
            $menu.addClass('open');
        }

        if (!event.data.multiple && $submenu) {
            var $sidebarMenu = $(event.data.el[0]);
            $sidebarMenu
                .find('.has-submenu.open, .previously-open-menu').not($menu).find('.submenu')
                .slideUp(function () {
                    $(this).closest('.previously-open-menu').removeClass('previously-open-menu');
                    saveSidebarState();
                })
                .closest('.open').removeClass('open');
        }

        saveSidebarState();
    };

    new Accordion($('.sidebar-menu'), false);
    if ($('.sidebar-footer').find('.has-submenu').length) {
        new Accordion($('.sidebar-footer'), false);
    }
});

$(document).ready(function() {
    $('.btn-sidebar-collapse').on('click', function () {
        var sidebar = $('.sidebar').toggleClass('sidebar-collapsed');

        if (sidebar.hasClass('sidebar-collapsed')) {
            $('body').addClass('sidebar-is-collapsed');
        } else {
            $('body').removeClass('sidebar-is-collapsed');
        }

        saveSidebarState();
    });

    // tooltip
    $('.sidebar li:not(.sidebar-controller) .sidebar-link').on('mouseenter', function () {
        if (!$('.sidebar').hasClass('sidebar-collapsed')) {
            return;
        }

        $('.sidebar-tooltip')
            .text($(this).text().trim())
            .css({
                marginLeft: $('.sidebar').width() + 15,
                marginTop: this.getBoundingClientRect().top + 7
            })
            .show();
    });

    $('.sidebar li:not(.sidebar-controller) .sidebar-link').on('mouseleave', function () {
        $('.sidebar-tooltip').hide().text('');
    });

    // club list
    $('.current-club-container').on('click', function () {
        $('.sidebar-club-list').fadeIn(200);
    });

    $(document).on('click', function (evt) {
        var isCurrentClubContainer = !!$(evt.target).closest('.current-club-container').length;

        if (!isCurrentClubContainer) {
            $('.sidebar-club-list').fadeOut(200);
        }
    });

    // there is no fixed amount of items inside '.sidebar-footer', so the bottom position of '.sidebar-menu' should be
    // adjusted so it can use all the available space and the scrollbar can work properly
    $('.sidebar-menu').css('bottom', $('.sidebar-footer').height());
});
