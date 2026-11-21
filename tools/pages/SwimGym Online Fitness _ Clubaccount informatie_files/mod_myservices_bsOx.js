const CONTENT_TAB_PREFIX = 'content_tab_';
const CONTENT_TABS = {
	'aboutme': 'client_data',
	'products': 'products',
	'bookings': 'bookings',
	'lastvisits': 'visits',
	'payments': 'payments',
}

var timer;

$(function () {

	$('body').on('click', '#edit_personal', function () {
		$('#edit_personal').hide();
		$('.tooltip').hide();
		showForm('#edit_personal_data');
	});

	$('body').on('click', '#edit_address', function () {
		$('#edit_address').hide();
		$('.tooltip').hide();
		showForm('#edit_address_data');
	});

	$('body').on('click', 'button.save-form', function (e) {
		e.preventDefault();
		var form = $(this).parents('form');
		var that = this;

		var currvals = getCurrentData('#' + form.attr('id'));
		var changes = updateSpanValues('#' + form.attr('id'));

		if (changes != currvals) {
			$.ajax({
				url: form.attr('action'),
				data: form.serialize(),
				success: function (data) {
					showMessage($(that).data('refers'), data.content.message);
					hideMessage($(that).data('refers'));
					hideForm('#' + form.attr('id'));
				}
			})
		}
	});


	$('body').on('click', '.section-menu-container li', function (e) {

		var section_id = $(this).find('span').data('href');

		openMobileContent(section_id)
	});

	$('body').on('change', '#user_club', function () {
		$('#switch_clubs').submit();
	});

	$('body').on('click', '.section-clubs-container li', function (e) {
		var clubid = $(this).data('id');
		changeclub(clubid);
	});

	function changeclub(clubid) {
		$('#user_club').val(clubid);
		$('#switch_clubs').submit();
	}

	$('body').on('click', '.delete_payment', function () {

		var result = confirm(CONFIRM_DELETE_PAYMENT_METHOD);

		var data = {
			'action': 'delete_direct_debit',
			'delete': $(this).data('delete'),
			'p_method_id': $(this).data('pid'),
			'p_method_name': $(this).data('pname')
		};

		var t = this;

		if (result) {
			$.ajax({
				url: $(this).data('url'),
				data: data,
				success: function (response) {
					$(t).parents('tr').slideUp('slow');
					$('#payment_methods').append('<p style="color:green">' + response.content.message + '</p>');
				}
			})
		}
	})
});

function toggleMobileClubSwitcher() {
  const clubSelector = $('#mobileClubSelectorContainer')
  const content = $('#myServicesContainer')

  if (clubSelector.is(":visible")) {
    clubSelector.slideToggle()
    content.show()
  } else {
    content.hide()
    clubSelector.slideToggle()
  }
}

function onClickCheckinHistoryPage(page) {
  var data = {
    'action': 'user_visits',
    'page': parseInt(page),
    'user_club': $('#user_club').val()
  };

  const selector = '#checkinHistory tbody'
  $(selector).fadeOut();

  $.ajax({
    url: $('#user_url').val(),
    data: data,
    success: function (response) {

      $('.content_tab_visits').html(response);
      $(selector).fadeIn();
    }
  })

  return false
}

function openMobileContent(section_id) {
	$('.section-menu-container li').slideUp();
	$('.section-menu-container li.active').removeClass('active');

	$('#mobile-sections-container div.section').removeClass('active').hide();
	$('#mobile-sections-container div[data-id="' + section_id + '"]').slideDown().addClass('active');
}

function hideForm(id) {
	$(id + ' td > span').show();
	$(id + ' td > span').each(function (k, v) {
		$('#' + $(this).attr('data-id')).hide();
	});

	$(id + ' .save-form').hide();
}

function updateSpanValues(id) {
	var changes = '';
	var changing_allowed = parseInt($('#changes_allowed').val());

	$(id + ' td > span').each(function (k, v) {

		var data_id = $(this).attr('data-id');
		var val = $('#' + data_id).val();
		var _change = '';

		if (data_id == 'country' || data_id == 'member_lang' || data_id == 'gender') {

			if ($('select#country').val() != '-1')
				_change = $('#' + data_id).find('option:selected').text();
			else
				_change = '';

		} else if (data_id == 'birthday_selects') {

			if ($('#bday_day').val() != '0' && $('#bday_month').val() != '0' && $('#bday_year').val() != '0')
				_change = ($('#bday_day').val() + ' ' + $('#bday_month option:selected').text() + ' ' + $('#bday_year option:selected').text());
			else {
				_change = $(this).text();
			}
		}
		else {
			_change = val;
		}

		if (changing_allowed) {
			$(this).text(_change.trim())
		}

		changes = changes + _change.trim();
	});

	return changes;
}

function showForm(id) {
	$(id + ' td > span').hide();
	$(id + ' td > span').each(function (k, v) {
		$('#' + $(this).attr('data-id')).show();
	});
	$(id + ' .save-form').show();
}

function showMessage(id, message) {
	$('#' + id).css('color', '#1EA21E').text(message).slideDown()
}

function hideMessage(id) {
	setTimeout(function () {
		$('#' + id).slideUp()
	}, 2000);
}

function getCurrentData(id) {
	var current = ''

	$(id + ' td > span').each(function (k, v) {

		current = current + ($(this).text().trim());
	})

	return current;
}

/** Helper function to change the displayed content tab
 * @param String tab_id CONTENT_TABS
 */
function displayContentTab(tab_id) {
	if (!CONTENT_TABS[tab_id]) {
		return;
	}
	var tab_href = CONTENT_TAB_PREFIX + CONTENT_TABS[tab_id]
	$('.nav-tabs a[href="#' + tab_href + '"]').tab('show');
	openMobileContent(tab_href)
}

/**
 * Save Webshop Member Settings
 * @param setting_id
 * @param setting_value
 * @param show_saving_loader
 */
function saveWebshopMemberSetting(setting_id, setting_value, show_saving_loader) {
	if (show_saving_loader) {
		showSmallSaver();
	}

	new Ajax('/webshop/ajax', {
		action: 'save-webshop-member-setting',
		setting_id: setting_id,
		value: setting_value,
	}, function (d) {
		if (show_saving_loader) {
			hideSmallSaver();
		}

		if (d.content.error) {
			show_notification('error', d.content.error);
		}
	});
}
