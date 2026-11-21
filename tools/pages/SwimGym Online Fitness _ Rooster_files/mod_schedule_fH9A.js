$(document).ready(function () {

    $('#download_btn').click(function (e) {
        $('#export_form').submit();
        $('#modal-schedule-export').modal('hide');
        e.preventDefault();
    });

});

function set_export_value(file_type) {
	$('#modal-schedule-export').modal('show');
	$('#export_form input[name=export_type]').val(file_type);

	return false;
}

