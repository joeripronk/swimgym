$(document).ready(function () {
    $("body").tooltip({selector: '[data-toggle="tooltip"]'}); // enabling the tooltip(s)
});

var Template = function (template, pattern) {
    var default_pattern = /(^|.|\r|\n)(#\{(.*?)\})/;
    this.template = template.toString();
    this.pattern = pattern || default_pattern;
}

jQuery.extend(Template.prototype, {
    evaluate: function (object) {
        var myRegexp = /\[\[(.*?)\]\]/;
        match = myRegexp.exec(this.template);
        while (match != null) {
            this.template = this.template.replace(match[0], object[match[1]]);
            match = myRegexp.exec(this.template);
        }
        return this.template;
    }
});

let onlyborder_validation = false;
let Validation = function () {
    this.onlyborder_validation=false;
    $.each(this, function (index, element) {

        if($(element).attr('data-validate-initialized')){
            return;
        }

        $(element).attr('data-validate-initialized', true);

        if ($(element).attr('data-validate') == 'number') {
            if($(element).attr('data-min') != undefined && $(element).attr('data-min') < 0 ){
                if ($(element).attr('data-max') != undefined) $(element).attr('maxlength', $(element).attr('data-max').length+1);
            }else{
                if ($(element).attr('data-max') != undefined) $(element).attr('maxlength', $(element).attr('data-max').length);
            }
        }

        if ($(element).attr('data-validate') == 'string') {
            if ($(element).attr('data-max') != undefined) $(element).attr('maxlength', $(element).attr('data-max'));
        }

        if ($(element).attr('data-validate') == 'time') {
            $(element).attr('maxlength', 5);
        }

        if ($(window).width() <= 767) {
            if ($(element).attr('data-validate') == 'number') $(element).attr('type', 'number');
            if ($(element).attr('data-validate') == 'email') $(element).attr('type', 'email');
            if ($(element).attr('data-validate') == 'time') $(element).attr('type', 'time');
        }
        if(this.onlyborder_validation === false){
	        let validation_div = $('<div class="validation-icon" data-toggle="tooltip" data-placement="auto" data-html="true"></div>');
	        validation_div.attr('id', $(element).attr('id') + '_validate');
	        $('body').append(validation_div);
	        validation_div.css('position', 'absolute');
	        validation_div.css('display', 'none');

	        if ($(element).attr('data-validate-location') == 'inside') {
		        validation_div.css('top', $(element).offset().top + 10);
		        validation_div.css('left', $(element).offset().left + $(element).outerWidth() - 32);
	        } else {
		        validation_div.css('top', $(element).offset().top + 4);
		        validation_div.css('left', $(element).offset().left + $(element).outerWidth() + 10);
	        }
        }

	    $(element).on('change', function (e) {
            let element = $(e.currentTarget);
            let validation_div = $('#' + element.attr('id') + '_validate');
            validation_div.tooltip('hide');

		    data = element.val();

		    if (element.attr('data-validate-group')) {
			    let val = element.attr('data-validate-group')
                data = "{";
			    $("input[data-validate-group='" + val + "']").each(function () {
			        if (( $(this).attr('type') == 'radio' || $(this).attr('type') == 'checkbox' ) ) {
			            if ( ($(this).is(":checked"))) {
				            data += "\"" + $(this).attr('name') + "\":\"" + $($(this)).val() + "\",";
			            } // else ignore
                    } else {
				        data += "\"" + $(this).attr('name') + "\":\"" + $($(this)).val() + "\",";
			        }


			    })
                data = data.substr(0, data.length-1);
			    data += "}";
		    }

            if ($(element).attr('data-validate-location') == 'inside') {
                validation_div.css('top', $(element).offset().top + 10);
                validation_div.css('left', $(element).offset().left + $(element).outerWidth() - 32);
            } else {
                validation_div.css('top', $(element).offset().top + 4);
                validation_div.css('left', $(element).offset().left + $(element).outerWidth() + 10);
            }


            $.ajax({
                url: '/validate',
                type: 'post',
                data: {
                    type: element.attr('data-validate'),
                    value: data,
                    min: element.attr('data-min'),
                    max: element.attr('data-max'),
                    against: $('#' + element.attr('data-validate-against')).val()
                }
            }).done(function (result) {
                element.removeClass('error');
                element.removeClass('ok');
                if (result.status_code != 200 || !result.content.result) {
                    element.addClass('error');
                    if(element.attr('data-validate-border') == undefined) {
                        $('#' + element.attr('id') + '_validate').fadeOut(250, function (e) {
                            $('#' + element.attr('id') + '_validate').removeClass('validation-ok').addClass('validation-error').fadeIn(250, function (e) {
                                $(this).tooltip('show');
                            }).attr('data-original-title', result.content.messages);
                        });
                    }else{
                        element.tooltip('show').attr('data-original-title', result.content.messages);
                    }
                } else {
                    if(element.attr('data-validate-border')){
                        element.addClass('ok');
                        element.tooltip('hide');
                    }else{
                        $('#' + element.attr('id') + '_validate').fadeOut(250, function (e) {
                            $('#' + element.attr('id') + '_validate').removeClass('validation-error').addClass('validation-ok').fadeIn().attr('data-original-title', '').tooltip('hide');
                        });
                    }
                }
            });
        });
    });
};
Validation.prototype.init = function () {

};
$.fn.validation = Validation;

if($.fn.modal) {
    $.fn.modal.Constructor.prototype.adjustDialog = function () {
    };
    $.fn.modal.Constructor.prototype.checkScrollbar = function () {
    };
}

$.fn.stopTyping = function(event_handler) {

    let timeout = null;

    $(this).keyup(function (e) {

        clearTimeout(timeout);

        let that = this;

        timeout = setTimeout(function () {
            if ($.isFunction(event_handler)) {
                event_handler(that);
            }
        }, 1000);
    });
};

/**
 * Executes a function after the ENTER key is pressed
 * @param callback The function to be executed
 * @param preventDefaultBehavior Whether the default behavior should be prevented or not
 */
$.fn.onEnterPressed = function (callback, preventDefaultBehavior) {
    $(this).keyup(function(event) {
        if (event.keyCode == $.ui.keyCode.ENTER) {
            callback();

            if (preventDefaultBehavior) {
                event.preventDefault();
                return false;
            }
        }
    });
};

$(function () {
    $('[data-toggle="tooltip"]').attr('data-html', 'true').attr('data-html', 'true').tooltip();
    if($('[data-validate]').length) {
        $('[data-validate]').validation();
    }

    $(document).delegate('*[data-validate]', 'click', function (event) {
        $('[data-validate]').validation();
    });


    $(window).resize(positionValidation);

    $('.dropdown').on('show.bs.dropdown', function (e) {
        $(this).find('.dropdown-menu').first().stop(true, true).slideDown(150);
    });

    $('.dropdown').on('hide.bs.dropdown', function (e) {
        $(this).find('.dropdown-menu').first().stop(true, true).slideUp(150);
    });

    $(document).delegate('*[data-toggle="lightbox"]', 'click', function (event) {
        event.preventDefault();
        $(this).ekkoLightbox({
            'left_arrow_class': '', // todo
            'right_arrow_class': '' // todo
        });
    });

    $('input[data-type="color"]').each(function () {
        let color_input = '#' + $(this).attr('id');
        let color_picker = $(this).attr('id') + '_colorpicker';
        $('<input id="' + color_picker + '" class="" type="text" value="' + $(color_input).val() + '">').insertAfter(this);

        color_picker = '#' + color_picker;

        $(color_picker).spectrum({
            color: $(color_input).val(),
            showInput: true,
            showButtons: true,
            preferredFormat: "hex",
            containerClassName: 'ui-colorpicker',
            cancelText: typeof COLORPICKER_CANCEL !== 'undefined' ? COLORPICKER_CANCEL : 'Cancel',
            chooseText: typeof COLORPICKER_PICK !== 'undefined' ? COLORPICKER_PICK : 'Choose',
            change: function (color) {
                handleColorChange(color_input, color_picker, color.toHexString());
            }
        });

        $(color_picker).on('change', function () {
            handleColorChange(color_input, color_picker);
        });

        $(color_input).on('change', function () {
            handleColorChange(color_input, color_picker);
        });

        $(color_input).on('click', function (event) {
            event.preventDefault();
            event.stopPropagation();
            $(color_picker).spectrum('show');
        });

        handleColorChange(color_input, color_picker);
    });

    if ($('input[data-type="color"]').length) {
        $('.sp-replacer').find('.sp-dd').addClass('has-ui-icon').html($('#color-picker-dropdown-dummy').html());
    }

    $('input[type="file"]').on('change', document, function (event) {
		if (window.File && window.FileReader && window.FileList && window.Blob) {
            if($(this).get(0).files[0] != undefined) {
                let fsize = $(this).get(0).files[0].size;

                if (fsize > (1048576 * 8)) {
                    bootbox.alert(TOO_LARGE_FILE);
                    $(this).fileinput('clear');
                }
            }
        }
    });
});




function showSmallLoading() {
    $('#bottom_loader').slideDown();
}

function hideSmallLoading() {
    $('#bottom_loader').slideUp(1000);
}

function showFullScreenLoading() {
    $('#fullscreen_loader').show();
}

function hideFullScreenLoading() {
    $('#fullscreen_loader').hide();
}

function showSmallSaver() {
    $('#bottom_saver span').text(SAVING);
    $('#bottom_saver .loading').addClass('css-loader-medium');
    if(!$('#bottom_saver').is(':animated')) $('#bottom_saver').slideDown();
}

function hideSmallSaver() {
    $('#bottom_saver span').text(CHANGES_SAVED);
    $('#bottom_saver .loading').removeClass('css-loader-medium');
    $('#bottom_saver').slideUp(1000);
}

function showFullScreenSaver() {
    $('#fullscreen_saver span').text(SAVING);
    $('#fullscreen_saver .loading').addClass('css-loader');
    $('#fullscreen_saver').show();
}

function hideFullScreenSaver() {
    $('#fullscreen_saver span').text(CHANGES_SAVED);
    $('#fullscreen_saver .loading').removeClass('css-loader');
    $('#fullscreen_saver').hide();
}

function ajaxSuccess(data, hide_in_milliseconds) {
    if (data) {
        if (data.reload) {
            window.location.reload();
        }

        if (data.content.error) {
            show_notification('error', data.content.error, hide_in_milliseconds);
        }

        if (data.content.info) {
            show_notification('', data.content.info, hide_in_milliseconds);
        }

        if (data.content.success) {
            show_notification('success', data.content.success, hide_in_milliseconds);
        }

        if (data.content.redirect) {
            window.location = data.content.redirect;
        }
    }
}

function getAjaxErrorMessage(xhr, fallback_message) {
    var server_error = xhr && xhr.responseJSON && xhr.responseJSON.content && xhr.responseJSON.content.error;

    return server_error || fallback_message || '';
}

function ajaxError(xhr, desc, err) {
    console.log(xhr);
    console.log("Details: " + desc + "\nError:" + err);

    // Surface a backend-provided error message (e.g. FORM_INVALID_CSRF_TOKEN on 403)
    // so failed AJAX calls don't appear to be silently ignored by the UI.
    var error_message = getAjaxErrorMessage(xhr);
    if (error_message && typeof show_notification === 'function') {
        show_notification('error', error_message);
    }
}

function formHandler(formid, fieldvalue) {
    if (fieldvalue != '') {
        document.getElementById(formid).deleteid.value = fieldvalue;
    }
    document.getElementById(formid).submit();
}

var Ajax = function (url, json_data, success, error) {
    $.ajax({
        url: url,
        type: 'post',
        dataType: 'json',
        data: json_data,
        success: success || ajaxSuccess,
        error: error || ajaxError
    });
};

var AjaxImage = function (url, form_id, success, error) {
    $('#' + form_id + '_progress').remove();

    let form_id_element = $('form[id="' + form_id + '"]');
    form_id_element.append('<progress id="' + form_id + '_progress"></progress>');

    //Workaround to fix problem on safari with empty file input type
    //https://stackoverflow.com/questions/49614091/safari-11-1-ajax-xhr-form-submission-fails-when-inputtype-file-is-empty?rq=1

    //base on https://stackoverflow.com/a/50009200 it has been fixed.
/*	form_id_element.find("input[type='file']").each(function(){
	    if ($(this).get(0).files.length === 0) {$(this).remove();}
	});*/

    let formData = new FormData(form_id_element[0]);

    $.ajax({
        url: url,
        type: 'POST',
        data: formData,
        xhr: function () {  // Custom XMLHttpRequest
            let custom_xhr = $.ajaxSettings.xhr();
            if (custom_xhr.upload) { // Check if upload property exists
                custom_xhr.upload.addEventListener('progress', function (e) {
                    if (e.lengthComputable) {
                        //$('#progress_values').html(e.loaded + '/' + e.total);
                        $('#' + form_id + '_progress').attr({value: e.loaded, max: e.total});
                    }
                    if (e.loaded == e.total) {
                        $('#' + form_id + '_progress').remove();
                    }
                }, false); // For handling the progress of the upload
            }
            return custom_xhr;
        },
        cache: false,
        contentType: false,
        processData: false,
        success: success || ajaxSuccess,
        error: error || ajaxError
    });
};

function close_notifications(){
    $('.notification div.btn-close:visible').each(function(){
       close_notification(this);
    });
}

function close_notification(element) {
    $('.validation-icon').tooltip('hide');
    let height = $(element).parent().outerHeight(true);

    $(element).parent().animate({
        'opacity': 0,
        'height': 0,
        'margin': 0,
        'min-height': 0,
        'padding': '0px 10px 0px 65px'
    }, {
        duration: 400,
        progress: function () {
            positionValidation();
        },
        complete: function () {
            $(this).remove();
        }
    });
}

function show_notification(type, text, hide_in_milliseconds, closable) {
    $('.validation-icon').tooltip('hide');

    if (closable == undefined) closable = true;

    let element_selector = '#notification' + (type ? '_' + type : '');
    let element = $(element_selector).clone();

    if (!closable) {
        element.find('.btn-close').remove();
    }

    element.removeAttr('id').prependTo('#main-container .page-container').css("display", "flex").hide().fadeIn(0, function () {
        $(this).find('span').html(text);

        $('html, body').animate({scrollTop: 0},{duration: 300});

	    let close_btn = $(this).find('.btn-close');

        $(this).animate({
            'opacity': 1,
            'height': 'auto',
            'margin': '0px 0px 8px 0px',
            'min-height': '50px',
            'padding': '16px'
        }, {
            duration: 400,
            progress: function () {
                positionValidation();
            },
            done: function () {
                $(this).css('height', 'auto');
                positionValidation();
            },
            complete: function () {
                let ms = parseInt(hide_in_milliseconds);
                if(!isNaN(ms)){
                    setTimeout(function () {
                        close_btn.trigger("click");
                    }, ms);
                }
            }
        });
    });
}

function scrollTo(element){
    if(element == 0){
        return;
    }

    $('html, body').animate({scrollTop: element.offset().top},{duration: 300});
}

function positionValidation() {
    $('.validation-icon').each(function () {
        $(this).tooltip('hide');
        if($(this).attr('id').replace('_validate', '') != 'undefined') {
            let parent = $('#' + $(this).attr('id').replace('_validate', ''));
            if ($(parent)) {
                if ($(parent).attr('data-validate-location') == 'inside') {
                    $(this).css('top', $(parent).offset().top + 10);
                    $(this).css('left', $(parent).offset().left + $(parent).outerWidth() - 32);
                } else {
                    $(this).css('top', $(parent).offset().top + 4);
                    $(this).css('left', $(parent).offset().left + $(parent).outerWidth() + 10);
                }
            }
        }
    });
}

function getTimezoneName() {
    tmSummer = new Date(Date.UTC(2005, 6, 30, 0, 0, 0, 0));
    so = -1 * tmSummer.getTimezoneOffset();
    tmWinter = new Date(Date.UTC(2005, 12, 30, 0, 0, 0, 0));
    wo = -1 * tmWinter.getTimezoneOffset();

    if (-660 == so && -660 == wo) return 'Pacific/Midway';
    if (-600 == so && -600 == wo) return 'Pacific/Tahiti';
    if (-570 == so && -570 == wo) return 'Pacific/Marquesas';
    if (-540 == so && -600 == wo) return 'America/Adak';
    if (-540 == so && -540 == wo) return 'Pacific/Gambier';
    if (-480 == so && -540 == wo) return 'America/Anchorage';
    if (-480 == so && -480 == wo) return 'Pacific/Pitcairn';
    if (-420 == so && -480 == wo) return 'America/Los_Angeles';
    if (-420 == so && -420 == wo) return 'America/Phoenix';
    if (-360 == so && -420 == wo) return 'America/Denver';
    if (-360 == so && -360 == wo) return 'America/Guatemala';
    if (-360 == so && -300 == wo) return 'Pacific/Easter';
    if (-300 == so && -360 == wo) return 'America/Chicago';
    if (-300 == so && -300 == wo) return 'America/Bogota';
    if (-240 == so && -300 == wo) return 'America/New_York';
    if (-240 == so && -240 == wo) return 'America/Caracas';
    if (-240 == so && -180 == wo) return 'America/Santiago';
    if (-180 == so && -240 == wo) return 'America/Halifax';
    if (-180 == so && -180 == wo) return 'America/Montevideo';
    if (-180 == so && -120 == wo) return 'America/Sao_Paulo';
    if (-150 == so && -210 == wo) return 'America/St_Johns';
    if (-120 == so && -180 == wo) return 'America/Godthab';
    if (-120 == so && -120 == wo) return 'America/Noronha';
    if (-60 == so && -60 == wo) return 'Atlantic/Cape_Verde';
    if (0 == so && -60 == wo) return 'Atlantic/Azores';
    if (0 == so && 0 == wo) return 'Africa/Casablanca';
    if (60 == so && 0 == wo) return 'Europe/London';
    if (60 == so && 60 == wo) return 'Africa/Algiers';
    if (60 == so && 120 == wo) return 'Africa/Windhoek';
    if (120 == so && 60 == wo) return 'Europe/Amsterdam';
    if (120 == so && 120 == wo) return 'Africa/Harare';
    if (180 == so && 120 == wo) return 'Europe/Athens';
    if (180 == so && 180 == wo) return 'Africa/Nairobi';
    if (240 == so && 180 == wo) return 'Europe/Moscow';
    if (240 == so && 240 == wo) return 'Asia/Dubai';
    if (270 == so && 210 == wo) return 'Asia/Tehran';
    if (270 == so && 270 == wo) return 'Asia/Kabul';
    if (300 == so && 240 == wo) return 'Asia/Baku';
    if (300 == so && 300 == wo) return 'Asia/Karachi';
    if (330 == so && 330 == wo) return 'Asia/Kolkata';
    if (345 == so && 345 == wo) return 'Asia/Kathmandu';
    if (360 == so && 300 == wo) return 'Asia/Yekaterinburg';
    if (360 == so && 360 == wo) return 'Asia/Colombo';
    if (390 == so && 390 == wo) return 'Asia/Yangon';
    if (420 == so && 360 == wo) return 'Asia/Almaty';
    if (420 == so && 420 == wo) return 'Asia/Bangkok';
    if (480 == so && 420 == wo) return 'Asia/Krasnoyarsk';
    if (480 == so && 480 == wo) return 'Australia/Perth';
    if (540 == so && 480 == wo) return 'Asia/Irkutsk';
    if (540 == so && 540 == wo) return 'Asia/Tokyo';
    if (570 == so && 570 == wo) return 'Australia/Darwin';
    if (570 == so && 630 == wo) return 'Australia/Adelaide';
    if (600 == so && 540 == wo) return 'Asia/Yakutsk';
    if (600 == so && 600 == wo) return 'Australia/Brisbane';
    if (600 == so && 660 == wo) return 'Australia/Sydney';
    if (630 == so && 660 == wo) return 'Australia/Lord_Howe';
    if (660 == so && 600 == wo) return 'Asia/Vladivostok';
    if (660 == so && 660 == wo) return 'Pacific/Guadalcanal';
    if (690 == so && 690 == wo) return 'Pacific/Norfolk';
    if (720 == so && 660 == wo) return 'Asia/Magadan';
    if (720 == so && 720 == wo) return 'Pacific/Fiji';
    if (720 == so && 780 == wo) return 'Pacific/Auckland';
    if (765 == so && 825 == wo) return 'Pacific/Chatham';
    if (780 == so && 780 == wo) return 'Pacific/Enderbury';
    if (840 == so && 840 == wo) return 'Pacific/Kiritimati';
    return 'Europe/Amsterdam';
}

function getUserIP(target) {
    $.ajax({
        url: '//api.ipify.org/?format=json'
    }).done(function (e) {
        $('#' + target).val(e.ip);
    });
}

// Creates a table from an Ajax response data
function createTable(table_selector, data) {
    if (!data) return;
    if (!(data.content)) return;

    clearTable(table_selector);
    createTableHead(table_selector, data.content.head);
    createTableBody(table_selector, data.content.rows);
}

function clearTable(table_selector) {
    $(table_selector).html('');
}

function createTableHead(table_selector, columns_array) {
    let head = '';
    for (var i = 0; i < columns_array.length; i++) {
        let column = columns_array[i];
        head += ('<th>' + column + '</th>');
    }

    $(table_selector).prepend('<thead><tr>' + head + '</tr></thead>');
}

function createTableBody(table_selector, rows_array) {
    let table_rows = '';

    for (var i = 0; i < rows_array.length; i++) {
        let row = rows_array[i];
        table_rows += createTableRow(row);
    }

    $(table_selector).append('<tbody>' + table_rows + '</tbody>');
}

function createTableRow(row) {
    let table_row = '';
    for (var i = 0; i < row.length; i++) {
        let column = row[i];
        table_row += ('<td>' + column + '</td>');
    }

    return '<tr>' + table_row + '</tr>';
}

var reauth_process;

function validate_reauth() {
    $.ajax({
        url: '/ajax',
        type: 'POST',
        data: {
            mode: 'reauthenticate',
            action: 'reauth',
            reauth_password: $('#reauth_password').val()
        }
    }).success(function (d) {
        if(d.content.success){
            reauth_process.resolve();
            $('#modal-reauth').modal('hide');
        }else{
            bootbox.alert(d.content.error);
        }
    });
}

var reauth = function (custom_auth_method, custom_content, formData) {
    if (reauth_process != undefined) reauth_process.fail();
    if ($('#modal-reauth').size() != 0) $('#modal-reauth').remove();

    reauth_process = $.Deferred();

    let data = cloneObject(formData);
    if (data === null) {
        data = {};
    }

    data.mode = 'reauthenticate';

    $.ajax({
        url: '/ajax',
        type: 'POST',
        data
    }).success(function (d) {
        if (d.content == undefined) {

            $('body').append(d);

            if ($.isFunction(custom_auth_method)) {

                $('body').find('#button_continue_auth').attr('onclick', '')
                $('body').find('#button_continue_auth').bind('click', custom_auth_method)

            }

            if (custom_content) {
                $('body').find('#modal-reauth #reauth_text').text((custom_content))
            }

            $('#modal-reauth').modal('show');
        } else {
            reauth_process.resolve();
        }
    });

    return $.Deferred(function (def) {
        $.when(reauth_process).done(function () {
            def.resolve();
        });
    });
};

/**
 * Clones an object using structuredClone if available, otherwise falls back to JSON.stringify.
 *
 * @param value
 * @returns {Object|null}
 */
function cloneObject(value) {
  if ((typeof value !== 'object') || (value === null)) {
    return null;
  }

  if (typeof structuredClone === 'function') {
    return structuredClone(value);
  }

  // Fallback for environments without structuredClone
  try {
    return JSON.parse(JSON.stringify(value));
  } catch (e) {
    console.log('structuredClone is not supported and value cannot be cloned via JSON method.', value)
  }

  return null
}

function getContrastYIQ(hexcolor) {
    hexcolor = hexcolor.replace("#", "");
    let r = parseInt(hexcolor.substr(0, 2), 16);
    let g = parseInt(hexcolor.substr(2, 2), 16);
    let b = parseInt(hexcolor.substr(4, 2), 16);
    let yiq = ((r * 299) + (g * 587) + (b * 114)) / 1000;
    return (yiq >= 128) ? 'black' : 'white';
}

function handleColorChange(element, target, color) {
    color = (color) ? color : $(element).val();

    if (color === 'transparent' || color === '' || color === null) {
        clearColorPicker(element);
        return;
    }

    if (color.substr(0, 1) !== '#') {
        color = '#' + color;
    }
    if (color.length >= 7) {
        color = color.substr(0, 7);
    }

    let element_dom = $(element);
    let font_color = getContrastYIQ(color.replace('#', ''));
    $(target).spectrum('set', color);
    element_dom.val(color);
    element_dom.css('background-color', color);
    element_dom.css('color', font_color);

    let color_picker_selector = element_dom.closest('div').find('.sp-preview-inner');
    if (color_picker_selector.length) {
        if (font_color === 'black') {
            color_picker_selector.addClass('light');
        } else {
            color_picker_selector.removeClass('light');
        }
    }

    const callbackName = element_dom.attr('data-spectrum-callback');
    if (callbackName && typeof window[callbackName] === 'function') {
        window[callbackName](element_dom, color);
    }
}

function clearColorPicker(element) {
    $(element).val('');
    $(element).css('background-color', 'transparent');
    $(element).css('color', 'transparent');
}

function stopUnreadMsgAnimation(){
    $('.drop-down-icon').addClass('off');
}
function startUnreadMsgAnimation(){
    $('.drop-down-icon').removeClass('off');
}
//function called on change of the duration filter for bookings
function getBookings(member_id, in_client_app, in_coach_app, in_my_services, view, filter_selector, doneCallback) {
    //get the selected duration from filter
    //need to specify '' else takes '-1 month'
    let bookings_duration_filter;

    if (filter_selector === undefined) {
        filter_selector = '#duration_filter';
    }

    if($(filter_selector).val() == '') {
        bookings_duration_filter = '';
    } else if(!$(filter_selector).val()) {
        bookings_duration_filter = '-1 month';
    } else {
        bookings_duration_filter = $(filter_selector).val();
    }

    let send_data =  {
        action: 'get_member_events',
        member_id: member_id,
        in_client_app: in_client_app,
        in_coach_app: in_coach_app,
        in_my_services: in_my_services,
        view: (view !== undefined) ? view : 0,
        bookings_duration_filter: bookings_duration_filter
    }

    if ($('select#user_club').length) {
        send_data.user_club = $('select#user_club').val();
    }

    //ajax to fetch the bookings based on the duration
    $.get('/club_portal/ajax', send_data, function (data) {
        $('#bookings').html(data);
        $('.bookings-result').html(data);
    }).done(function() {
        if ($.isFunction(doneCallback)) {
            doneCallback();
        }
    });
}

/**
 * display and focus on the input based on the language selected from the multi language dropwdown
 * used in custom-homescreen
 */
function displayInputForTranslation(e) {
    //get currently chosen language
    let selected_lang = $(e).val();
    $('.validation-icon').hide();
    $('.tooltip').hide();
    if(selected_lang != "default-lang") {
        $('input[data-lang][data-lang!="' + $(e).val() + '"]').hide();
        //check if input field exists, false - create new input field
        $('input[data-lang="' + $(e).val() + '"]').show().first().focus();

        $('div[data-lang][data-lang!="' + $(e).val() + '"]').hide();
        //check if edit-icon exists, false - create new edit-icon
        $('div[data-lang="' + $(e).val() + '"]').show().first().focus();
    }
}

function isChrome() {
    return (!!window.chrome && !!window.chrome.webstore);
}

/**
 * makes checkbox selectable with shift
 *
 * @param checkbox_selector  selector for checkboxes to be selectable
 * @param connectedAmountContainer selector of node to keep amount of selected checkboxes
 */
function shiftAutoSelect(checkbox_selector, connectedAmountContainer) {

    $('body').on('click', checkbox_selector , function(e) {

		if (e.shiftKey) {
            let list = $(checkbox_selector);

            let that = this;
            let pos = 0;
            let done = false;

            list.each(function(i) {
				if ($(this).is(that)) {
                    let ch = ($(this).is(':checked'))

                    pos = i;

                    for (var j = i; j < list.length; j++) {

                        if ($(list.eq(j)).attr('data-last-click') == '1') {

                            for (var f = j; f > i; f--) {
                                $(list.eq(f).prop('checked', ch))
                            }

                            done = true;
                            break;
                        }
                    }

                    if (!done)
                        for (var j = i; j >= 0; j--) {

                            if ($(list.eq(j)).attr('data-last-click') == '1') {

                                for (var f = j; f <= i; f++) {
                                    $(list.eq(f).prop('checked', ch))
                                }

                                break;
                            }
                        }
                }
            });

            list = null;
        }
        $(checkbox_selector).removeAttr('data-last-click');//clear last clear attrib.

        $(this).attr('data-last-click', "1");

        let amount = $(checkbox_selector + ':checked').length;

        if (connectedAmountContainer !== undefined) {
            $(connectedAmountContainer).text(amount);
        }

    })
}

function hideAndShowLoading(element) {
    $(element).after( '<img id="loading_btn_replacement" src="https://static.virtuagym.com/v29524207/images/loaders/virtuagym-loader-medium.gif" />');
    $(element).hide();
}

function nlToBr(inputSelector, setValue)
{
    let text = $(inputSelector).val();

    let regex = /<br\s*[\/]?>/gi;

    let result = text.replace(regex, '\n');

    if (setValue === undefined || setValue > 0) {
        $(inputSelector).val(result);
    }

    return result;
}

function createFancybox(selector, type, closebutton, modal, content, onclosed, remove_bg, locked, wrapCSS){
    if(!locked) locked = false;
    if(!type) type = 'ajax';
    selector = (selector) ? $(selector) : $;
    selector.fancybox({
        type: type,
        scrolling: 'auto',
        closeBtn: closebutton,
        modal: modal,
        content: content,
        title: null,
        helpers : {
            overlay : {
                locked : locked
            }
        },
        wrapCSS: wrapCSS,
        'afterClose': onclosed,
        'beforeShow': function(e){
            if(remove_bg) {
                $('.fancybox-skin').css('background-color', 'transparent');
            }
        }
    });
}

function formSubmit (form_id, result_div, show_loader, show_loader_div, loader_type, oncomplete, oncomplete_data = false, return_data = false) {
    if (show_loader == true) {
        let loader_location = result_div;
        if (show_loader_div) {
            loader_location = show_loader_div;
        }
        if (loader_type == undefined || loader_type == 0) {
            $(loader_location).html('<img src="https://static.virtuagym.com/v29524207/images/loaders/virtuagym-loader-medium.gif" alt="loading..." style="width: 32px; height: 32px; margin: 5px auto; display: block;" />');
        } else if (loader_type == 1) {
            $(loader_location).html('<img src="https://static.virtuagym.com/v29524207/images/loaders/virtuagym-loader-small.gif" alt="loading..." style="width: 16px; height: 16px; margin: 0px auto; display: block;" />');
        } else {
            $(loader_location).html('<img src="https://static.virtuagym.com/v29524207/images/loaders/virtuagym-loader-medium.gif" alt="loading..." style="width: 128px; height: 15px; margin: 5px auto; display: block;" />');
        }
    }

    $.ajax({
        url: $('#' + form_id).attr('action'),
        type: 'POST',
        data: $('#' + form_id).serialize()
    }).done(function (data) {
        if (return_data == true) return data;
        if (result_div != undefined) $('#' + result_div).html(data);
        if (oncomplete != undefined) {
            if(oncomplete_data == true){
                oncomplete(data);
            }else{
                oncomplete();
            }
        }
    });
}

function trigger_vg_message(msg, slide_down, message_id, url, show_close) {
	trigger_msg_type(0, msg, slide_down, message_id, url, 0, show_close)
}

function trigger_vg_error(msg, slide_down, message_id, url, show_close) {
	trigger_msg_type(1, msg, slide_down, message_id, url, 0, show_close)
}

function trigger_vg_neutral_message(msg, slide_down, message_id, url, show_close) {
	trigger_msg_type(2, msg, slide_down, message_id, url, 0, show_close)
}

// DO NOT USE THIS FUNCTION! USE THE 3 FUNCTIONS ABOVE INSTEAD!
function trigger_msg_type(type, msg, slide_down, message_id, url, slidedelay, show_close) {
	jQuery(document).ready(function () {
		trigger_msg_type_delayed(type, msg, slide_down, message_id, url, slidedelay, show_close)
	});
}
//DO NOT USE THIS FUNCTION! USE THE 3 FUNCTIONS ABOVE INSTEAD!
function trigger_msg_type_delayed(type, msg, slide_down, message_id, url, slidedelay, show_close) {
    if (message_id == undefined) message_id = 0;
    if (show_close == undefined) show_close = true;
    if (slidedelay == undefined) slidedelay = 500;
    let ajax_method = "";
    if (message_id > 0) ajax_method = "$.ajax({url: '" + url + "', data: {message_id : " + message_id + ", action: 'notification_viewed'},type: 'GET'});";

    let new_message;

    switch (parseInt(type)){
        case 0:
            new_message = $('#notification_success').clone().removeAttr('id');
            break;

        case 1:
            new_message = $('#notification_error').clone().removeAttr('id');
            break;

        case 2:
        default:
            new_message = $('#notification').clone().removeAttr('id');
            break;
    }

    new_message.find('span').html(msg);

    if(ajax_method){
        new_message.find('.btn-close').attr('onclick', ajax_method + new_message.find('.btn-close').attr('onclick'));
    }

    if (slide_down == undefined) slide_down = 1;
    $('#vgmessages').show();
    $('#vgmessages').append(new_message);
    if (slide_down > 0) {
        let notification_height = new_message.height();

        new_message.hide().css('height', 0).css('display', 'flex').delay(slidedelay).animate({
            opacity: 1,
            height: notification_height
        }, 800, function () {
            $(this).css('display', 'flex');
            $(this).css('height', 'auto');
        });
    } else {
        new_message.css('display', 'flex').css('opacity', 1);
    }
}

function formatDate(date, separator, order_array) //ex [d,m,y]  [y,m,d]
{
    var yyyy = date.getFullYear().toString();
    var mm = (date.getMonth() + 1).toString(); // getMonth() is zero-based
    var dd = date.getDate().toString();

    var date_data = new Array();
    var di = 0;
    var yi = 2;
    var mi = 1;

    if (order_array !== undefined) {

        di = order_array.indexOf('d');
        yi = order_array.indexOf('y');
        mi = order_array.indexOf('m');
    }

    date_data[di] = (dd[1] ? dd : "0" + dd[0]);
    date_data[yi] = yyyy;
    date_data[mi] = (mm[1] ? mm : "0" + mm[0]);

    separator = (separator !== undefined) ? separator : '-';

    return date_data.join(separator)
}

/**
 * Create and manage a dropdown pagination.
 * @param translations Translation labels: PAGE, PREVIOUS, NEXT.
 * @param on_page_change_callback Function to be executed after the page is changed by setPage. Usually it's a
 * function to load the data based on the selected page.
 * @example
 *     // initializing pagination...
 *     var pagination = new DropdownPagination({ PAGE:... }, getData);
 *     pagination.setContainerElement($('#pagination-container'));
 *     pagination.setPage(1);
 *
 *     // changing page...
 *     pagination.next(); // go to next page
 *     pagination.previous(); // go to previous page
 *     pagination.setPage(7); // go to a specific page
 */
var DropdownPagination = function(translations, on_page_change_callback) {
    // set default translations which are used only if the real translations are not assigned
    translations = $.extend({ PAGE: 'Page',  PREVIOUS: 'Previous',  NEXT: 'Next', }, translations);

    var _this = this;
    var container_element = $(null);
    var current_page = 1;
    var total_pages = null;

    var pagination_controllers = (
        '<div class="pagination-container">' +
            '<span class="pagination-button pagination-previous-page">' + translations.PREVIOUS + '</span>' +
            '<select class="pagination-dropdown form-control new-select"></select>' +
            '<span class="pagination-button pagination-next-page">' + translations.NEXT + '</span>' +
        '</div>'
    );

    this.setContainerElement = function(element) {
        container_element = element;
        container_element.html(pagination_controllers);
        container_element.find('.pagination-previous-page').click(_this.previous);
        container_element.find('.pagination-next-page').click(_this.next);
        container_element.find('.pagination-dropdown').change(function() {
            _this.setPage(this.value);
        });
    };

    this.reset = function() {
        total_pages = null;
        _this.setPage(1);
    };

    this.setPage = function(new_page) {
        new_page = parseInt((new_page || 0), 10);

        if (isNaN(new_page) || (new_page < 1) || (total_pages !== null && new_page > total_pages)) {
            return;
        }

        current_page = new_page;

        if (typeof on_page_change_callback == 'function') {
            on_page_change_callback(current_page);
        }
    };

    this.getPage = function() {
        return current_page;
    };

    this.setTotalPages = function(total) {
        total_pages = total;
        _this.render();
    };

    this.previous = function() {
        _this.setPage(current_page - 1);
    };

    this.next = function() {
        _this.setPage(current_page + 1);
    };

    this.render = function() {
        var options = '';
        for (var x = 1; x <= total_pages; x++) {
            var selected = current_page == x ? 'selected' : '';
            options += '<option value="' + x + '" ' + selected + '>' + translations.PAGE + ' ' + x + '/' + total_pages + '</option>';
        }
        container_element.find('.pagination-dropdown').html(options);
        container_element.find('.pagination-dropdown').prop('disabled', (total_pages <= 0));

        container_element.find('.pagination-previous-page, .pagination-next-page').removeClass('disabled');
        if (current_page <= 1) {
            container_element.find('.pagination-previous-page').addClass('disabled');
        }
        if (current_page >= total_pages) {
            container_element.find('.pagination-next-page').addClass('disabled');
        }
    };
};

/**
 * @param name
 * @returns {string|null}
 */
function getBrowserCookie(name) {
    var cookieName = name + "=";
    var cookies = document.cookie.split(';');

    for (var i = 0; i < cookies.length; i++) {
        var cookie = cookies[i];

        while (cookie.charAt(0) === ' ') {
            cookie = cookie.substring(1, cookie.length);
        }

        if (cookie.indexOf(cookieName) === 0) {
            return cookie.substring(cookieName.length, cookie.length);
        }
    }

    return null;
}

function scanForTooltips() {
    $("[tooltip]").each(
        function (e) {
            $(this).on('mouseenter', show_tooltip);
            $(this).on('mouseleave', function () {$('#tooltip_holder').hide();});
        }
    );
}

function show_tooltip() {
    console.log("Old tooltip called");
}

function cancelClickEvent(e) {
    if (!e) var e = window.event;
    if(e != undefined) {
        e.cancelBubble = true;
        if (e.stopPropagation) e.stopPropagation();
    }
}

var FormSubmit = function (form_id, result_div, show_loader, show_loader_div, loader_type, oncomplete, oncomplete_data, return_data, onEmptyData) {
    if (show_loader == true) {
        var loader_location = result_div;
        if (show_loader_div) {
            loader_location = show_loader_div;
        }
        if (loader_type == undefined || loader_type == 0) {
            $(loader_location).html('<img src="https://static.virtuagym.com/v29524207/images/loaders/virtuagym-loader-medium.gif" alt="loading..." style="width: 32px; height: 32px; margin: 5px auto; display: block;" />');
        } else if (loader_type == 1) {
            $(loader_location).html('<img src="https://static.virtuagym.com/v29524207/images/loaders/virtuagym-loader-small.gif" alt="loading..." style="width: 16px; height: 16px; margin: 0px auto; display: block;" />');
        } else {
            $(loader_location).html('<img src="https://static.virtuagym.com/v29524207/images/loaders/virtuagym-loader-medium.gif" alt="loading..." style="width: 128px; height: 15px; margin: 5px auto; display: block;" />');
        }
    }

    $.ajax({
        url: $('#' + form_id).attr('action'),
        type: 'POST',
        data: $('#' + form_id).serialize()
    }).done(function (data) {
        if (!data && onEmptyData !== undefined) {
            onEmptyData();
        }
        if (return_data != undefined) return data;
        if (result_div != undefined) $('#' + result_div).html(data);
        if (oncomplete != undefined) {
            if(oncomplete_data != undefined){
                oncomplete(data);
            }else{
                oncomplete();
            }
        }
    }).fail(function (data) {
        if (result_div != undefined) $('#' + result_div).html(data.responseText);
    });
}

function like_and_number_up(update_id, like_amount, like_type) {
    var likes_element = $('#' + update_id + '_' + like_type).find('.ui-like-count');

    let element_1 = '.ui-like-icon.likeable';
    let element_2 = '.ui-like-icon.liked';

    if (like_amount < 1) {
        element_1 = '.ui-like-icon.liked';
        element_2 = '.ui-like-icon.likeable';
    }

    $('#' + update_id + '_' + like_type).find(element_1).fadeOut('fast', function () {
        $('#' + update_id + '_' + like_type).find(element_2).fadeIn();

        likes_element.animate({
            fontSize: '16px',
            lineHeight: '20px',
        }, 100);

        if (like_amount > 0) {
            likes_element.html(parseInt(likes_element.html()) + 1);
        } else {
            likes_element.html(parseInt(likes_element.html()) - 1);
        }
        likes_element.animate({
            fontSize: '14px',
            lineHeight: '20px',
        }, 50);
    });


    like(update_id, like_type, like_amount, function (data) {
        likes_element.html(data);
    });
}

function like(subject_id, subject_type, like, oncomplete) {
    if (oncomplete == undefined) oncomplete = function (data) {
        $('#likes_' + subject_id).html(data);
    };
    $.get('/user/ajax', {subject_id: subject_id, subject_type: subject_type, action: "like", like: like}, oncomplete);
}

busy_like = '';
out_timer = '';
get_timer = '';

function get_likers(subject_id, type, name, is_unique, hover_element) {
    clearTimeout(out_timer);

    return;

    result_elem = '#like_result_' + subject_id;

    get_timer = setTimeout(function () {
        if (busy_like == subject_id) {
            return;
        }

        $.get('/ajax', {subject_id: subject_id, subject_type: type, mode: "get_likers_initial"}, function (data) {
            busy_like = subject_id;
            $('.' + name + '_results').html('');

            $(result_elem).html('');

            $(result_elem).append(data);
            $('#likers_tooltip').css('top', $(result_elem).parent().position().top - $('#likers_tooltip').height() + 8 + 'px');
            if (is_unique == false) $('#likers_tooltip').css('left', $(hover_element).position().left - 26 + 'px');
            $('#likers_tooltip').show();
            $('#likers_tooltip').mouseover(function () {
                clearTimeout(out_timer);
            });
        });
    }, 50);

    $(result_elem).parent().unbind("mouseout");

    $(result_elem).parent().mouseout(function () {
        clearTimeout(out_timer);
        clearTimeout(get_timer);

        out_timer = setTimeout(function () {
            remove_likers(subject_id + '_' + type, name)
        }, 200);
    });
}

function remove_likers(subject_id, name) {
    busy_like = '';
    out_timer = '';

    $('.' + name + '_results').html('');
    $('#' + subject_id + '_' + name + '_result').fadeIn("fast");
}

function prepareTags(tags, target){
    $('#'+target+'_holder').html();
    $.each(tags.split(';'), function (k,v){
        if(v) {
            $('#' + target + '_holder').append('<div class="tag" data-value="' + v + '" onclick="selectTag(this, \'' + target + '\');">' + v + '</div>');
        }
    })
    $('#'+target+'_holder').append('<div style="clear: both;"></div>');
    $('#'+target+'_holder').append('<div id="'+target+'_count" style="display: inline-block;"></div>');
    $('#'+target+'_holder').append('<div id="'+target+'_list" style="display: none; clear: both; height: 200px; width: 100%; line-height: normal; overflow-y: scroll;"></div>');
    calculateReach(target);
}

function selectTag(elm, target){
    if($(elm).hasClass('tag_active')){
        $(elm).removeClass('tag_active');
        $('#'+target).val($('#'+target).val().replace($(elm).attr('data-value')+';', ''));
    }else{
        $(elm).addClass('tag_active');
        $('#'+target).val($('#'+target).val()+$(elm).attr('data-value')+';');
    }
    calculateReach(target);
}

function calculateReach(target){
    $('#' + target + '_count').html(LOADING);
    $.ajax({
        url: '/ajax?mode=get_tag_count&tags='+$('#'+target).val()
    }).done(function(e){
        $('#' + target + '_count').html(REACH + ': <strong>' + e + '</strong> ' + CLUBS + ' <a href="#" onclick="$(\'#' + target + '_list\').toggle(); return false;">' + SEE_REACH + '</a>');
        seeReach(target);
    });
}

function seeReach(target){
    $.ajax({
        url: '/ajax?mode=get_tag_count_list&tags='+$('#'+target).val()
    }).done(function(e){
        $('#'+target+'_list').html(''+e+'');
    });
}

function toggleDarkMode(value) {
    if (!value) {
        $('#toggleLightMode').addClass('animate');
        $('#toggleLightMode').find('.ui-loader').show();
    } else {
        $('#toggleDarkMode').addClass('animate');
        $('#toggleDarkMode').find('.ui-loader').show();
    }

    $.ajax({
        url: '/ajax?mode=toggle_dark_mode&value=' + value
    }).done(function (e) {
        location.reload();
    });
}

//to prevent an IE bug use this function instead of setTimeout
function setSafeTimeout(func, delay) {
    var target = +new Date + delay;
    return setTimeout(function () {
        var now = +new Date;
        if (now < target) {
            setSafeTimeout(func, target - now);
        } else {
            func();
        }
    }, delay);
}
