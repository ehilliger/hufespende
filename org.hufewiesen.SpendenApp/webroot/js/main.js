

var session = null;

function drawPixels(parent, pixels) {	
	var recompute = false;
	for(i in pixels){
		var p = pixels[i];
		if(!(p instanceof Pixel)) {
			p = new Pixel(p);
			//console.log("drawing pixel: " + JSON.stringify(p));
			if(p.clientId != session && p.state == 'selected') {
				p.state = 'reserved';
			}
			if(p.state == 'selected') {
				recompute = true;
			}
			if(p.visible) {
				p.draw(parent);
			} else {
				p.remove(parent);
			}
		}
	}
	if(recompute) {
		computeSelected();
	}
}

function computeSelected() {
	var sum = $(".pxselected").length * 5;
	$("#donationSum").text("€ " + sum);
}

var eb = null;
function openEbConn() {
	if (!eb) {
		eb = new vertx.EventBus("http://192.168.178.23:8080/hufedb");
		
		eb.onopen = function() {
			console.log("EB connected...");
			eb.registerHandler('hs.client.pxUpdate', function(msg, replyTo) {
				drawPixels($('#boughtPixels'), msg.pixels);
			});
			eb.registerHandler('hs.client.txUpdate', updateTxSum);
			loadPixels();
		};

		eb.onclose = function() {
			console.log("EB disconnected...");
			eb = null;
		};
	}
}

function updateTxSum(reply) {
	$('#totalSum').text('€ ' + reply.count * 5);
}

function loadPixels() {
	eb.send("hs.server.updatePixels", {}, createBatchReplyHandler());
	eb.send("hs.db", {'action':'find', 'collection':'pixels', 'matcher':{}}, createBatchReplyHandler());
	eb.send("hs.server.txSum", {}, updateTxSum);
}

function readBatch(reply) {
	if((reply.status === 'ok') || (reply.status = 'more-exist')) {
		drawPixels($("#boughtPixels"), reply.results);
	} else {
		console.log("error reading pixels: " + reply.status);
	}
}

function createBatchReplyHandler() {
	return function(reply, replier) {
		// Got some results - process them
        readBatch(reply);
        if (reply.status === 'more-exist') {
            // Get next batch
            replier({}, createBatchReplyHandler());
        }
	}
}

function setExpressCheckout() {
	var token = location.search.replace(/.*token=(.*)&.*/, "$1");
	if(token && token != "") {
		console.log("sending token update");
		eb.send(hs.server.setToken, {clientId: session, token: token});
		
	}
}

function spendenFormSubmit() {
	console.log("form submit");
	var form = $('#spendenform');
	var msg = {
			name: form.find('#name').val(),
			email: form.find('#email').val(),
			message: form.find('#message').val(),
			url: form.find('#url').val(),
			pixels: []
	};
	
	var selectedPixels = $('.pxselected');
	if(selectedPixels.length == 0) {
		alert('Sie haben keine Pixel ausgewählt.');
	} else {
		selectedPixels.each(function(idx){		
			msg.pixels.push($(this).data('px'));
		});
		
	}
	// console.log(JSON.stringify(msg));
	eb.send('hs.server.submit', msg, function(reply){
		if(reply.status && reply.status == 'ok') {
			// $('#paypalIFrame').attr('src', reply.checkoutUrl);
			// $("#paypalDiv").dialog("open");
			document.location = reply.checkoutUrl;
		} else {
			alert("error: " + reply.error);
		}
			
	});	
    return false;	
}

function getCookie(name) {
    var nameEQ = name + "=";
    var ca = document.cookie.split(';');
    for(var i=0;i < ca.length;i++) {
        var c = ca[i];
        while (c.charAt(0)==' ') c = c.substring(1,c.length);
        if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length,c.length);
    }
    return null;
}


$(document).ready(function() {
	$("#map").append(""
		+ "<div id='pixelCursor' class='pxcursor'></div>"
		+ "<div id='boughtPixels'></div>"
		+ "<div id='hoverDiv' class='pxhover'>" 
		+	"<h3>"
		+		"<span id='hoverState'>Gespendet durch </span>"
		+		"<span id='hoverName'></span>"
		+ 	"</h3>"
		+ 	"<p id='hoverMsg'></p>"
		+ 	"<p><a id='hoverLink'></a></p>"
		+ "</div>"
	).mouseover(function() {
		$('#pixelCursor').show();
		$("#map").mousemove(function (evt){
			$('#pixelCursor')
				.css("top", (evt.pageY - (evt.pageY % 10)) + "px")
			 	.css("left", (evt.pageX - (evt.pageX % 10)) + "px");

		});
	}).mouseout(function() {
		$('#pixelCursor').hide();
		$("#map").mousemove(null);
	});
	
	$("#pixelCursor")
		.hide()
		.click(function(){
			var parent = $('#boughtPixels');
			var px = new Pixel({
				x: $(this).css("left").replace(/[^-\d\.]/g, '') / 10,
				y: $(this).css("top").replace(/[^-\d\.]/g, '') / 10,
				clientId: session
			});
			var expx = px.findPixel(parent);

			if(expx == null) {
				px.state = 'selected';
				px.visible = true;
				jQuery('#spendenform #submitBtn').next('.help-inline').remove();
                jQuery('#spendenform #submitBtn').parents('.control-group').removeClass('error');
				// console.log("requesting pixel: " + JSON.stringify(px));			
				eb.publish('hs.server.pxUpdate', {pixels: [px]});
			} else {
				if(expx.clientId == px.clientId) {
					// console.log("freeing pixel: " + JSON.stringify(px));
					expx.visible = false;
					eb.publish('hs.server.pxUpdate', {pixels: [expx]});
				}
			}	
		});
	$("#hoverDiv").hide();
	
	
	openEbConn();
	$('#spendenform #name').validate({
		expression: "if(VAL != '') return true; else return false;",
		message: "Bitte einen Namen eingeben"});
	$('#spendenform #email').validate({
		expression: "if(VAL != '') return true; else return false;",
		type: "email",
		message: "Bitte eine Email Adresse angeben"
		});
	$('#spendenform #AGB').validate({
		expression: "return $('#spendenform #AGB').attr('checked') != undefined;",
		message: "Bitte stimmen sie den AGBs zu"
		});
	$('#spendenform #AGB').click(function (evt){
		if($(this).attr('checked')) {
			jQuery('#spendenform #AGB').next('.help-inline').remove();
            jQuery('#spendenform #AGB').parents('.control-group').removeClass('error');
		}
	});
	$('#spendenform #submitBtn').validate({
		expression: "return $('.pxselected').length > 0",
		message: "Sie haben keine Pixel gewählt"
	});
	
	$('#spendenform').validated(spendenFormSubmit);
	
	$("#thankyouDiv").dialog({
        modal: true,
        autoOpen: false,
        height: '300',
        width: '400',
        draggable: true,
        resizeable: true,   
        title: 'Dankeschön'
    });
	
	
	session = getCookie("hsid");
});

function Pixel(json) {
	var pad = function(number, length) {
		var n = '' + number;
		while(n.length<length){n = '0' + n;}
		return n;
	}
	if(json.x && json.y) {
		this.x = json.x
		this.y = json.y
		this._id = pad(this.x, 4) + pad(this.y, 4);
	} else if(json._id) {
		this._id = pad(json._id, 8);
		this.x = parseInt(this._id.replace(/(\d\d\d\d)(\d\d\d\d)/, '$1'));
		this.y = parseInt(this._id.replace(/(\d\d\d\d)(\d\d\d\d)/, '$2'));
	} else {
		throw "invalid argument, neither coordinates nor _id";
	}
	this.visible = json.visible != null ? json.visible : false;
	this.name = json.name != null ? json.name : '';
	this.message = json.message != null ? json.message : '';
	this.url = json.url != null ? json.url : '';
	this.state = json.state != null ? json.state : 'reserved';
	this.clientId = json.clientId != null ? json.clientId : null;
	// methods
	if(!Pixel.prototype.getCssX) {
		Pixel.prototype.getCssX = function() {
			return '' + (this.x * 10) + 'px';
		}
	}
	if(!Pixel.prototype.getCssY) {
		Pixel.prototype.getCssY = function() {
			return '' + (this.y * 10) + 'px';
		}
	}
	if(!Pixel.prototype.draw) {
		Pixel.prototype.draw = function(parent) {
			this.visible = true;
			var pxDiv = $(parent).find('#' + this._id);
			if(!pxDiv || pxDiv.length == 0) {
				pxDiv = $("<div id='" + this._id + "'/>");
				$(parent).append(pxDiv);
			}
			pxDiv.removeClass("pxselected pxbought pxreserved")
				.addClass("px" + this.state)
				.css("left", this.getCssX())
				.css("top", this.getCssY())
				.data("px", this)
				.mouseover(function() {
					var state = $(this).data('px').state;
					var name = $(this).data('px').name;
					if(state == 'reserved' && name && name.length > 0) {
						$("#hoverDiv #hoverState").text("Reserviert durch: ");
					} else if(state === 'bought') {
						$("#hoverDiv #hoverState").text("Gespendet durch: ");
					} else {
						$("#hoverDiv #hoverState").text("Reserviert ");
					}
					
					$("#hoverDiv #hoverName").text($(this).data("px").name);
					if($(this).data("px").message) {
						$("#hoverDiv #hoverMsg").text($(this).data("px").message);
					} else {
						$("#hoverDiv #hoverMsg").text("");
					}
					if($(this).data("px").url) {
						$("#hoverDiv #hoverLink")
							.text($(this).data("px").url)
							.attr("href", $(this).data("px").url);
						
					} else {
						$("#hoverDiv #hoverLink").text("").attr("href", "");
					}
					$("#hoverDiv")
						.css("left", $(this).css("left")).css("left", "+=25")
						.css("top", $(this).css("top")).css("top", "-=25")
						.show();
				})
				.mouseout(function() {
					$("#hoverDiv").hide();
				})
				.click(function() {
					$("#pixelCursor").trigger('click');
				});
				
		}
	}
	if(!Pixel.prototype.remove) {
		Pixel.prototype.remove = function(parent) {
			var pxDiv = $(parent).find('#' + this._id);
			if(pxDiv && pxDiv.length > 0) {
				pxDiv.remove();
				this.visible = false;
			}
		}
	}
	if(!Pixel.prototype.isVisible) {
		Pixel.prototype.isVisible = function(parent) {
			var pxDiv = $(parent).find('#' + this._id);
			if(pxDiv && pxDiv.length > 0) {
				this.visible = true;
			} else {
				this.visible = false;
			}
			return this.visible;

		}
	}
	if(!Pixel.prototype.findPixel) {
		Pixel.prototype.findPixel = function(parent) {
			var pxDiv = $(parent).find('#' + this._id);
			if(pxDiv && pxDiv.length > 0) {
				return $(pxDiv[0]).data("px");
			}
			return null;
		}
	}
};


