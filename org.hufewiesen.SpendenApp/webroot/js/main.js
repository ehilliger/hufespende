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




function drawPixels(parent, boughtPixels) {	
	for(i in boughtPixels){
		var px = boughtPixels[i];
		// console.log("drawing bought pixel: " + px + ", appending to: " + parent);
		
		var pxDiv = $("<div class='px" + px.state + "'></div>");
		parent.append(pxDiv);
		pxDiv.css("left", px.x * 10);
		pxDiv.css("top", px.y * 10);
		pxDiv.data("donator", px.name);
		pxDiv.mouseover(function() {
			$("#hoverDiv p#hoverName").text($(this).data("donator"));
			$("#hoverDiv")
				.css("left", $(this).css("left")).css("left", "+=25")
				.css("top", $(this).css("top")).css("top", "-=25")
				.show();
		});
		pxDiv.mouseout(function() {
			$("#hoverDiv").hide();
		});
		pxDiv.click(function(evt) {
			evt.stopPropagation();
		});
	}
}

function pixelsReserved(pixels) {
	for(var i=0; i<pixels.length; i++) {
		pixels[i].name="unknown";
		pixels[i].state="reserved";
	}
	drawPixels($("#boughtPixels"), pixels);
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
			eb.registerHandler('client.pxreserved', function(msg, replyTo) {
				console.log('pxreserved: ' + JSON.stringify(msg));
				pixelsReserved(msg.pixels);
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
			console.log("reply" + reply);
			var bpParent = $("#boughtPixels");
			drawPixels(bpParent, reply.results);
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
		var px = {
			x: $(this).css("left").replace(/[^-\d\.]/g, '') / 10, 
			y: $(this).css("top").replace(/[^-\d\.]/g, '') / 10
		};
		console.log('px: ' + JSON.stringify(px));
		msg.pixels.push(px);
	});
	console.log(JSON.stringify(msg));
}



$(document).ready(function() {
	$("#map").append(""
		+ "<div id='pixelCursor' class='pxcursor'></div>"
		+ "<div id='boughtPixels'></div>"
		+ "<div id='hoverDiv' class='pxhover'><h3>Gespendet durch</h3><p id='hoverName'></p></div>"
	);
	$("#pixelCursor")
		.hide()
		.click(function(){
			// select pixel
			var selectedPx = $(this).clone();
			selectedPx.removeClass("pxcursor").addClass("pxselected");			
			selectedPx.click(function(){
				$(this).remove();
				computeSelected();
			});
			$("#boughtPixels").append(selectedPx);
			computeSelected();
			eb.publish('client.pxreserved', {pixels: [{
				x: $(this).css("left").replace(/[^-\d\.]/g, '') / 10,
				y: $(this).css("top").replace(/[^-\d\.]/g, '') / 10
			}]});
		});
	$("#hoverDiv").hide();
	
	
	openEbConn();
	
	$('#spendenform #submitBtn').click(spendenFormSubmit);

	// var bpParent = $("#boughtPixels");
	// drawBought(bpParent, boughtPixels);
});

