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


var boughtPixels = [
	[1, 10, 10, "Eckart"],
	[2, 11, 10, "Laura"],
	[3, 20, 10, "Eckart"],
	[4, 30, 10, "Someone"],
	[5, 30, 11, "Eckart"],
	[6, 30, 12, "Laura"]
];

function drawBought(parent, boughtPixels) {	
	// for(i in boughtPixels){
	var taken = [];
	for(var i=0; i<10000; i++) {
		var x = 1 + Math.floor(Math.random() * 100);
		var y = 1 + Math.floor(Math.random() * 80);
		if(taken[x] == undefined || taken[x][y] == undefined)	{
			var px = [i, x, y, "someone"];
			if(taken[x] == undefined) {
				taken[x] = [];
			}
			taken[x][y] = px;
			// var px = boughtPixels[i];
			// console.log("drawing bought pixel: " + px + ", appending to: " + parent);
			var pxDiv = $("<div id='" + px[0] + "' class='pxbought'></div>");
			parent.append(pxDiv);
			pxDiv.css("left", px[1] * 10);
			pxDiv.css("top", px[2] * 10);
			pxDiv.data("donator", px[3]);
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
}

function computeSelected() {
	var sum = $(".pxselected").length * 12;
	$("#donationSum").text("€" + sum);
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
		});
	$("#hoverDiv").hide();

	var bpParent = $("#boughtPixels");
	drawBought(bpParent, boughtPixels);
});

