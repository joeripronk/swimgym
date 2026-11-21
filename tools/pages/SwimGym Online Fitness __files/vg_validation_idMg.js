var checked_ary = new Array();

checked_ary["bday_day"] = false;
checked_ary["bday_month"] = false;
checked_ary["bday_year"] = false;

checked_ary["country"] = false;
checked_ary["region"] = false;
checked_ary["city"] = false;

/**
 * Validate Number
 */
function validateNum(num, targetdiv, minimum, maximum) {

	var numcheck = /^(([1-9]{1}[0-9]{0,1})?)([0-9]{1})(\.[0-9])?(\,[0-9])?$/;
	var isnum = numcheck.test(num);

	if (empty(num)) {
		$('#'+targetdiv).html(' <img src="/images/validate_cross.png" alt="invalid"' + 'id="' + targetdiv + '_img" /><br />' + ERROR_NO_VALUE);
		return;
	}

	if (isnum) {
		if (num < minimum) {
			$('#'+targetdiv).html(' <img src="/images/validate_cross.png" alt="invalid"' + 'id="' + targetdiv + '_img" /> <br />' + ERROR_HIGHER_NUMBER);
			return;
		}
		else if (num > maximum) {
			$('#'+targetdiv).html(' <img src="/images/validate_cross.png" alt="invalid"' + 'id="' + targetdiv + '_img" /> <br />' + ERROR_LOWER_NUMBER);
			return;
		}

		$('#'+targetdiv).html(' <img src="/images/validate_check.png" alt="valid"' + 'id="' + targetdiv + '_img" /><br />');
		return;
	}
	$('#'+targetdiv).html(' <img src="/images/validate_cross.png" alt="invalid"' + 'id="' + targetdiv + '_img" /><br />' + ERROR_INVALID_VALUE);
}


/**
 * Validate Zipcode
 */
function validateZipcode(targetdiv, input) {
	if (empty(input)) {
		$('#'+targetdiv).html('');
		return;
	}

	var zipcheck;

	if ($('#country').val() == 'NL') {
		zipcheck = /^\W*[1-9]{1}[0-9]{3}\W*[a-zA-Z]{2}\W*$/;
	} else if ($('#country').val() == 'BE') {
		zipcheck = /^[1-9]{1}[0-9]{3}$/;
	} else if ($('#country').val() == '') {
		$('#'+targetdiv).html(' <img src="/images/icon_oknot.gif" alt="invalid"' + 'id="' + targetdiv + '_img" />' + ERROR_CHOOSE_LOCATION);
	}

	var correct = zipcheck.test(input);

	if (correct) {
		$('#'+targetdiv).html(' <img src="/images/icon_message_ok.gif" alt="valid"' + 'id="' + targetdiv + '_img" /> ');
		return;
	}
	else {
		$('#'+targetdiv).html(' <img src="/images/icon_oknot.gif" alt="invalid"' + 'id="' + targetdiv + '_img" />' + ERROR_WRONG_ZIPCODE);
		return;
	}
}

function validateField(targetdiv, url) {
	$('#'+targetdiv).html(' <img src="/images/loaders/virtuagym-loader-small.gif" alt="loading..." />');
	$.ajax({
		url: url
	}).done(function (e) {
		if(e.content != undefined){
			if (typeof startLink == 'function') {
				startLink();
			}
		}else{
			$('#'+targetdiv).html(e);
		}
	});
}

function newValidateEmail(targetInput, targetLabel) {

	var inputElement = $('#' + targetInput);
	var labelElement = $('#' + targetLabel);

	if (empty(inputElement.val())) {

		showErrorMessage(inputElement, labelElement, VAL_SHOULD_NOT_BE_EMPTY);
		return 0;
	}
	else if (/^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,3}$/.test(inputElement.val())) {
		hideErrorMessage(inputElement, labelElement);
		return 1;
	}
	else {
		showErrorMessage(inputElement, labelElement, ENTER_VALID_EMAIL);
		return 0;
	}
}

/**
 * Server Side Validation
 *
 * @param targetInput
 * @param targetLabel
 * @param showLoader
 * @param url
 */
function serverSideValidation(targetInput, targetLabel, url, showLoader) {
	var labelElement = $('#'+ targetLabel);
	var inputElement = $('#'+ targetInput);

	inputElement.attr('data-valid','false');

  if (showLoader === true) {
    labelElement.html(' <img src="/images/loaders/virtuagym-loader-small.gif" alt="loading..." />').show();
  }

  $.ajax({
        url: url
    }).success(function (data) {
      var payload = data.content;
      if((typeof payload.callbackFunction !== undefined) && (typeof window[payload.callbackFunction] === "function")){
        window[payload.callbackFunction]();
      } else if(payload.valid === true) {
        hideErrorMessage(inputElement, labelElement);
      } else if(payload.valid === false){
        showErrorMessage(inputElement, labelElement, payload.message);
      }
    });
}

/**
 * Server Side Validation
 *
 * @param targetInput
 * @param targetLabel
 * @param url
 * @param password
 */
function serverSidePasswordValidation(targetInput, targetLabel, url, password) {
	var labelElement = $('#'+ targetLabel);
	var inputElement = $('#'+ targetInput);

	inputElement.attr('data-valid','false');

	$.ajax({
		type: 'POST',
		url: url,
		data: {
			password: password
		}
	}).success(function (data) {
		var payload = data.content;

		if(payload.valid === true) {
			hideErrorMessage(inputElement, labelElement);
		} else if(payload.valid === false){
			showErrorMessage(inputElement, labelElement, payload.message);
		}
	});
}

function newValidateString(targetInput, targetLabel){

	var inputElement = $('#' + targetInput);
	var labelElement = $('#' + targetLabel);
	var elementValue = inputElement.val().trim();

	if (empty(elementValue)) {
		showErrorMessage(inputElement, labelElement, VAL_SHOULD_NOT_BE_EMPTY);
		return 0;
	} else {
		hideErrorMessage(inputElement, labelElement);
		return 1;
	}
}

function validateUserName(targetInput, targetLabel) {
  var inputElement = $('#' + targetInput);
  var labelElement = $('#' + targetLabel);
  var elementValue = inputElement.val().trim();

  if (empty(elementValue)) {
    showErrorMessage(inputElement, labelElement, VAL_SHOULD_NOT_BE_EMPTY);
    return 0;
  }

  var pattern = /^([\.\,\~\!\@\#\$\%\^\&\*\(\)\_\+\=\-\[\]\{\}\;\:\"\'\`\\\/\<\>\?\|\u20A0-\u20AF\u20B0-\u20BF\u00A2\u00A3\u00A5])/;
  if (pattern.test(elementValue)) {
    showErrorMessage(inputElement, labelElement, VAL_SHOULD_START_WITH_ALPHANUM_CHAR);
    return 0;
  }

  hideErrorMessage(inputElement, labelElement);
  return 1;
}


function newFieldChecked(targetLabel, targetInput, value){

	var inputElement = $('#'+ targetInput);
	var labelElement = $('#'+ targetLabel);
	String(targetLabel);

	if (targetInput === 'gender') {
		if (value === '' || value === null) {
			showErrorMessage(inputElement, labelElement, VAL_SHOULD_NOT_BE_EMPTY);
			return;
		}
	}
	else if (targetInput === 'bday_day' || targetInput === 'bday_month' || targetInput === 'bday_year') {
		if (checked_ary['bday_day'] === true && checked_ary['bday_month'] === true && checked_ary['bday_year'] === true) {
			return;
		}
		else {
			showErrorMessage(inputElement, labelElement, VAL_SHOULD_NOT_BE_EMPTY);
			return;
		}

	}
	hideErrorMessage(inputElement, labelElement);
}

function validateCheckboxes(targetInput, targetLabel, errorMessage){
    var inputElements = $("input[name='" + targetInput + "']");
	var labelElement = $('#' + targetLabel);
	
	var all_unchecked = true;
    inputElements.each( function () {
    	if ($(this).is(':checked')) {
			all_unchecked = false;
            return false;
		}
    });

    if (all_unchecked) {
        showErrorMessage(undefined, labelElement, errorMessage);
        return 0;
	} else {
        hideErrorMessage(undefined, labelElement);
        return 1;
	}
}

function newValidateNum(targetInput, targetLabel, minimum, maximum) {

	var numCheck = /^(([1-9]{1}[0-9]{0,1})?)([0-9]{1})(\.[0-9])?(\,[0-9])?$/;

	var inputElement = $('#'+ targetInput);
	var labelElement = $('#'+ targetLabel);

	var num = inputElement.val();
	var isNum = numCheck.test(num);
	if (empty(num)) {
		showErrorMessage(inputElement, labelElement, VAL_SHOULD_NOT_BE_EMPTY);
		return 0;
	}

	if (isNum) {
		if (num < minimum) {
			showErrorMessage(inputElement, labelElement, ERROR_VALUE_TOOLOW);
			return 0;
		}
		else if (num > maximum) {
			showErrorMessage(inputElement, labelElement, ERROR_VALUE_TOOHIGH);
			return 0;
		}else {
			hideErrorMessage(inputElement, labelElement);
			return 1;
		}
	}
	showErrorMessage(inputElement, labelElement, INVALIDWEIGHT);
}

function validateNumberLimit(targetInput, targetLabel, limitNumber, errorMessage) {
    var inputElement = $('#' + targetInput);
    var labelElement = $('#' + targetLabel);

    if (inputElement.val() > limitNumber) {
        showErrorMessage(inputElement, labelElement, errorMessage);
        return 0;
    } else {
        hideErrorMessage(inputElement, labelElement);
        return 1;
    }
}

function validateDateLimit(targetInput, targetLabel, limitDate, errorMessage) {
    var inputElement = $('#' + targetInput);
    var labelElement = $('#' + targetLabel);

    var dateValues = inputElement.val().split('-');
    var inputDate = new Date(dateValues[2], dateValues[1] - 1, dateValues[0]);

    if (inputDate > limitDate) {
        showErrorMessage(inputElement, labelElement, errorMessage);
        return 0;
    } else {
        hideErrorMessage(inputElement, labelElement);
        return 1;
    }
}

//simple date validation to see if it fits within the limit of plus or minus 50 years
function validateDate(date){
	var allowedYearDifference = 50;
	var msToYears = 31536000000;

	var dateValues = date.split('-');
	if (!dateValues[1]) {
		var dateValues = date.split('/');
		var inputDate = new Date(dateValues[2], dateValues[0] - 1 , dateValues[1]);
	} else {
		var inputDate = new Date(dateValues[2], dateValues[1] - 1, dateValues[0]);
	}

	if(inputDate <= new Date('1970-01-01')){
		return false;
	}

	var todayDate = new Date();

	//millisecond difference
	var diff = Math.abs(inputDate - todayDate);
	//convert ms to years
	diff = diff / msToYears;

	if(diff >= allowedYearDifference){
		return false;
	}
	return true;
}

function showErrorMessage(inputElement, labelElement, errorMessage){
	labelElement.show();
	labelElement.html(errorMessage);

	if (typeof inputElement != "undefined") {
        inputElement.attr('data-valid','false');
        inputElement.removeClass('signup-input');
        inputElement.addClass('vg-input-error');
	}
}

function hideErrorMessage(inputElement, labelElement){
	labelElement.hide();

    if (typeof inputElement != "undefined") {
		inputElement.attr('data-valid','true');
		inputElement.removeClass('vg-input-error');
		inputElement.addClass('signup-input');
    }
}

//==============================
// Helper Functions
//==============================

/**
 * Check if value is empty
 *
 * @param str
 * @returns boolean
 */
function empty(str) {
	return (!str || 0 === (str.trim()).length);
}

/**
 * Check if value is in array
 *
 * @returns boolean
 * @param value
 */
function inArray(value) {
	for (var i = 0; i < checked_ary.length; i++) {
		if (this[i] === value) {
			return true;
		}
	}
	return false;
}

function is_prorata(){
	if($('#startonfirst').is(":checked") && $('#ms_price_term').val() !== "monthly" && $('#ms_price_term').val() !== "total") {
		return false;
	}
	return true;
}

function alertIfVarNotUndefined(varName, alertText){
	if (typeof window[varName] != 'undefined') {
		alert(alertText);
	}
}