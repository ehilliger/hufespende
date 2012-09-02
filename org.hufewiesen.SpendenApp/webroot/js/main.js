$("#map").mouseover(function() {
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


function drawPixels(parent, pixels) {	
	for(i in pixels){
		var p = pixels[i];
		if(!(p instanceof Pixel)) {
			p = new Pixel(p);
		}
		p.draw(parent);
	}
}

function computeSelected() {
	var sum = $(".pxselected").length * 12;
	$("#donationSum").text("€ " + sum);
}

var eb = null;
function openEbConn() {
	if (!eb) {
		eb = new vertx.EventBus("http://192.168.178.23:8080/hufedb");

		eb.onopen = function() {
			console.log("EB connected...");
			eb.registerHandler('hs.client.pxreserved', function(msg, replyTo) {
				// console.log('pxreserved: ' + JSON.stringify(msg));
				drawPixels($('#boughtPixels'), msg.pixels);
			});
			loadPixels();
		};

		eb.onclose = function() {
			console.log("EB disconnected...");
			eb = null;
		};
	}
}

function loadPixels() {
	eb.send("hs.db", {action:"find", collection: "pixels", matcher: {}}, function(reply) {
		console.log("got db result");
		if(reply.status === 'ok') {
			// console.log("reply:\n" + JSON.stringify(reply.results));
			drawPixels($("#boughtPixels"), reply.results);
		} else {
			console.log("error getting DB entries")
		}
	});
}

function spendenFormSubmit() {
	var form = $('#spendenform');
	var msg = {
			name: form.find('#name').val(),
			email: form.find('#email').val(),
			message: form.find('#message').val(),
			url: form.find('#url').val(),
			pixels: []
	};
	$('.pxselected').each(function(idx){		
		console.log('px: ' + JSON.stringify($(this).data('px')));
		msg.pixels.push($(this).data('px'));
	});
	console.log(JSON.stringify(msg));
	eb.publish('hs.server.submit', msg);
}



$(document).ready(function() {
	$("#map").append(""
		+ "<div id='pixelCursor' class='pxcursor'></div>"
		+ "<div id='boughtPixels'></div>"
		+ "<div id='hoverDiv' class='pxhover'><h3 id='hoverState'>Gespendet durch</h3><p id='hoverName'></p></div>"
	);
	$("#pixelCursor")
		.hide()
		.click(function(){
			var px = new Pixel({
				x: $(this).css("left").replace(/[^-\d\.]/g, '') / 10,
				y: $(this).css("top").replace(/[^-\d\.]/g, '') / 10,
				state: 'selected'
			});
			px.draw($('#boughtPixels'));
			computeSelected();
			eb.publish('hs.client.pxreserved', {pixels: [px]});
		});
	$("#hoverDiv").hide();
	
	
	openEbConn();
	
	$('#spendenform #submitBtn').click(spendenFormSubmit);

	// var bpParent = $("#boughtPixels");
	// drawBought(bpParent, boughtPixels);
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
	this.name = json.name != null ? json.name : '';
	this.message = json.message != null ? json.message : '';
	this.url = json.url != null ? json.url : '';
	this.state = json.state != null ? json.state : 'reserved';
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
			var pxDiv = $(parent).find('#' + this._id);
			if(!pxDiv || pxDiv.length == 0) {
				pxDiv = $("<div id='" + this._id + "'/>");
				$(parent).append(pxDiv);
			}
			pxDiv.removeClass("pxselected pxbought pxreserverd")
				.addClass("px" + this.state)
				.css("left", this.getCssX())
				.css("top", this.getCssY())
				.data("px", this)
				.mouseover(function() {
					var state = $(this).data('px').state;
					if(state == 'reserved') {
						$("#hoverDiv h3#hoverState").text("Reserviert durch:");
					} else if(state === 'bought') {
						$("#hoverDiv h3#hoverState").text("Gespendet durch:");
					} else {
						$("#hoverDiv h3#hoverState").text("Reserviert");
					}
					
					$("#hoverDiv p#hoverName").text($(this).data("px").name);
					$("#hoverDiv")
						.css("left", $(this).css("left")).css("left", "+=25")
						.css("top", $(this).css("top")).css("top", "-=25")
						.show();
				})
				.mouseout(function() {
					$("#hoverDiv").hide();
				})
				.click(function(evt) {
					evt.stopPropagation();
				});
		}
	}
};


