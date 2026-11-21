$(document).ready(function() {
    $('.user-menu-data').click(function() {
        $('.user-menu').toggleClass('open');
        $('.user-menu-dropdown').slideToggle('fast');
    });

    $(document).on('click', function(evt) {
        var clickedInsideUserMenu = !!$(evt.target).closest('.user-menu').length;
        if (!clickedInsideUserMenu) {
            $('.user-menu').removeClass('open');
            $('.user-menu-dropdown').slideUp('fast');
        }
    });
});
