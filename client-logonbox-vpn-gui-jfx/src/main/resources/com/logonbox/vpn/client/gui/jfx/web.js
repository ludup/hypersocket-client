(function($) {
	$.fn.buttonLoader = function(action) {
		var self = $(this);
		//start loading animation
		if (action == 'start') {
			if ($(self).attr("disabled") == "disabled") {
				e.preventDefault();
			}
			//disable buttons when loading state
			$('.has-spinner').attr("disabled", "disabled");
			var txt = $(self).text();
			$(self).attr('data-btn-text', txt);
			//binding spinner element to button and changing button text
			$(self).html('<span class="spinner"><i class="fa fa-spinner fa-spin"></i></span>' + txt);
			$(self).addClass('active');
		}
		//stop loading animation
		if (action == 'stop') {
			$(self).html($(self).attr('data-btn-text'));
			$(self).removeClass('active');
			//enable buttons after finish loading
			$('.has-spinner').removeAttr("disabled");
		}
	}
})(jQuery);