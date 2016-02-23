(function(document) {
  'use strict';

  var btn = document.querySelector('#btn');

  btn.addEventListener('click', function() {
      alert("Ahhh... Thank you!");
  });

  window.addEventListener('WebComponentsReady', function() {
    // imports are loaded and elements have been registered
      console.log("WebComponentsReady!");
  });
})(document);
