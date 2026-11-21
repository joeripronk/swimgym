var Autocomplete = function(id, params, result_id)
{
	this.id = id;
	this.veld = $('#'+id);
	this.resultid = result_id;
	this.result = $('#'+result_id);
	this.options = params ? params : {};
	busy = false;
	jqxhr = undefined;
	manually_aborded = false; //used to not show error message when we manually cancel an ajax request

	if (!this.veld) {
		alert("Autocomplete requires a field id to initialize");
	}
	var optkey, optvalue = {
		phpscript: '',
		method: 'post',
		sel_fields: [],
		delay: 250, //Tijd om te wachten voor results
		minChars: 2, //Hoeveel characters er minimaal moeten zijn.
		action: '',
		pagination: '', //If you want automatically new results when the user is scrolling down enter the pagination id here
		use_window_scrollbar: false, //when true the class will look at the scrollbar of the window instead of the scrollbar of the result div to get the pagination result
		show_loading: true, //when true this will show a loading gif inside the result div when the user is typing or changing an input value
		only_show_first_page: false,
		forceSetPageNum:false, // when true we force setting the page number to 0
		csrf_token:'', //Set $template->assign_var('CSRF_TOKEN', (new \Virtuagym\VGCsrf())->getToken()); on the main handler.php
		onPageFirstLand: false, // When true get results for first time landing on page
    elementSelector: '', // alternate result container id
    minScrollDistance: 1, // min scrollbar distance from bottom of the container
    oncomplete: function(){return false;},
    beforeSend: function(){return false;},
    abortPreviousRequest: false,
	};
	for (optkey in optvalue) {
		if (typeof(this.options[optkey]) != typeof(optvalue[optkey])) {
			this.options[optkey] = optvalue[optkey];
		}
	}
	this.selection = this.options.sel_fields;
  this.elementSelector = this.options.elementSelector ? $(this.options.elementSelector) : null;
	global_autocomplete = this;
	for(var i=0;i<this.selection.length;i++) {
		if($('#'+this.selection[i]).length > 0) {
			if ($('#' + this.selection[i]).get(0).tagName == 'INPUT' && ($('#' + this.selection[i]).attr("type") == 'checkbox' || $('#' + this.selection[i]).attr("type") == 'radio')){
				$('#' + this.selection[i]).on('click', function () {
					global_autocomplete.doUpdate(true);
				});
			} else if (this.options.onPageFirstLand){
				global_autocomplete.doUpdate(true);
				// Get results on dropdown change
				$('#' + this.selection[i]).change(function () {
					global_autocomplete.doUpdate(true);
				});
			} else if ($('#' + this.selection[i]).get(0).tagName == 'SELECT') {
				$('#' + this.selection[i]).change(function () {
					global_autocomplete.doUpdate(true);
				});
			} else {
				$('#' + this.selection[i]).keyup(function () {
					global_autocomplete.doTimer();
				});
				$('#' + this.selection[i]).attr("AutoComplete", "off");
			}
		}
	}

	$(this.veld).keyup(function(e) {global_autocomplete.onKeyUp(e);});
	$(this.veld).attr("AutoComplete","off");
	$(this.veld).bind('cut', function(e){global_autocomplete.onclick(e)});
	$(this.veld).bind('paste', function(e){global_autocomplete.onclick(e)});

	if(this.options.pagination != '') {
		last_result = false;
		current_page = 1;

		if(this.options.use_window_scrollbar) {
			//listener on window scrollbar
			$(window).scroll(function(){
				if ($(window).scrollTop() >= ($(document).height() - $(window).height() - 600)){
					global_autocomplete.doUpdate(false);
				}
			});
		} else {
      const elementSelector = this.elementSelector
      const minScrollDistance = this.options.minScrollDistance;
      const element = elementSelector? $(elementSelector) : $(this.result);
      //listener on the scrollbar
      $(element).scroll(function() {
        if (elementSelector) {
          // stop is there is no scrollbar
          if (!element.scrollTop()) {
            return;
          }
          if (Math.abs(element.get(0).scrollHeight - element.scrollTop() - element.outerHeight()) <= minScrollDistance) {
            global_autocomplete.doUpdate(false);
          }
          return;
        }

				if (element.outerHeight() >= (element.get(0).scrollHeight - element.scrollTop() - 600)){
					global_autocomplete.doUpdate(false);
				}
			});
		}
	}
};

$.extend(Autocomplete.prototype,
	{
		firstPage: function() {
			current_page = 0;
		},
		prevPage: function() {
			current_page--;
		},
		nextPage: function() {
			current_page++;
		},
		onKeyUp: function(e) {
			if (!e) var e = window.event;
			var key = (e.keyCode ? e.keyCode : e.which);
			var CTRL = 17;

			if(key === $.ui.keyCode.UP || key === $.ui.keyCode.DOWN || key === $.ui.keyCode.LEFT || key === $.ui.keyCode.RIGHT || key === CTRL) {
				return false;
			} else {
				this.getResults($(this.veld).val());
			}
		},
		onclick: function() {
			this.getResults($(this.veld).val());
		},
		getResults: function(text) {
			if(text.length < this.options.minChars && this.options.hasOwnProperty('keepResults') && !this.options.keepResults) {
				this.result.html('');
				this.result.hide();
				return false;
			} else if (text.length > 0 && text.length < this.options.minChars && this.options.hasOwnProperty('keepResults') && this.options.keepResults) {
				return false;
			} else if (text.length === 0) {
				var p = this;
				p.doUpdate(true);
			} else {
				this.result.show();
				var p = this;
				clearTimeout(this.timeout);
				if($(p.veld).val().length >= this.options.minChars) {
					this.doTimer();
				}
			}
		},

		createParams: function(value) {
			var pars = this.id+'='+value+'&';
			if(this.options.action != '') pars += 'action='+this.options.action+'&';

			// add csrf_token
			if(this.options.csrf_token != '') pars += 'csrf_token='+this.options.csrf_token+'&';

			if(this.options.fetch_from_superclub) pars += ' fetch_from_superclub='+this.options.fetch_from_superclub+'&';
			if(this.options.pagination != '') pars += 'page='+(current_page+1)+'&';
			for(i=0;i<this.selection.length;i++) {
				pars += this.selection[i]+'=';
				if ($('#'+this.selection[i]).length > 0 && $('#' + this.selection[i]).get(0).tagName == 'INPUT' && ($('#' + this.selection[i]).attr("type") == 'checkbox' || $('#' + this.selection[i]).attr("type") == 'radio'))
					if ($('#' + this.selection[i]).prop("checked") == true) {
						pars += 'checked';
					} else {
						pars += '';
					}
				else
          pars += encodeURIComponent($('#' + this.selection[i]).val());

				if (i + 1 < this.selection.length)
					pars += '&';

			}
			return pars;
		},

		doUpdate: function(clear_result_div) {
			if(clear_result_div) {
				current_page = 0;
				busy = false;
				last_result = false;
				//scroll the div to the top
				if(!this.options.use_window_scrollbar) {
					$(this.result).scrollTop(0);
				}
				this.show_loading();
			}

			if(this.options.only_show_first_page && current_page > 1) {
				return false;
			}

      if(
        ($(this.veld).val() != '' || this.options.minChars == 0 || this.options.onEmptyExecute)
        && busy == false
        && last_result == false
      ) {
        var pars = this.createParams($(this.veld).val());

        elem = this.result;
        busy = true;

        //show loading image
        if(clear_result_div == false) {
          $(elem).append('<div style="text-align:center; clear:both;"><div id="pagi_loader_' + this.options.action + '" class="css-loader-medium" style="margin: 10px auto;"></div></div>');
        }

        $.ajaxSetup({
          timeout: 30000
        });

        this.options.beforeSend(jqxhr);

        if (jqxhr && this.options.abortPreviousRequest) {
          manually_aborded = true;
          jqxhr.abort();
          jqxhr = undefined;
        }

				//do the ajax call
				jqxhr = $.get(this.options.phpscript, pars, function(data) {
					if(clear_result_div) {
						$(elem).hide();
						$(elem).html(data);
						$(elem).fadeIn("fast");
						if (global_autocomplete.options.forceSetPageNum){
							current_page = 0;
						}
					}
					else {
						$(elem).append(data);
					}

          var div = $(data);
          if (div.hasClass('no_more_results_found') || div.hasClass('no-products-found')) {
              last_result = true;
          }

          if(
            global_autocomplete.options.pagination != ''
            && is_last_result_queue[global_autocomplete.options.pagination]
            && (
              is_last_result_queue[global_autocomplete.options.pagination] == -1
              || is_last_result_queue[global_autocomplete.options.pagination] == 1
            )
          ) {
            last_result = true;
          }

          scanForTooltips();
          global_autocomplete.options.oncomplete();
          busy = false;

          if(clear_result_div == false) {
            $('#pagi_loader_' + global_autocomplete.options.action).remove();
          }

					manually_aborded = false;
					current_page++;
					jqxhr = undefined;
				}).error(function(){
					if(manually_aborded == false) {
						var con_error = '<center>Connection error</center>';
						if(clear_result_div != false)
							$(elem).html(con_error);
						else {
							$('#pagi_loader_'+global_autocomplete.options.action).remove();
							$(elem).append(con_error);
						}
						manually_aborded = false;
						jqxhr = undefined;
						last_result = false;
						busy = false;
					}});
			}
		},
		doTimer: function() {
			this.show_loading();

			var p = this;
			clearTimeout(this.timeout);
			this.timeout = setTimeout( function() {if(jqxhr != undefined){manually_aborded = true; jqxhr.abort(); busy = false;} last_result = false; p.doUpdate(true);}, this.options.delay);
		},
		show_loading: function() {
			if(this.options.show_loading) $(this.result).html('<center><img src="/images/loaders/virtuagym-loader-medium.gif" style="margin-top:50px" /></center>');
		}
	}

);
