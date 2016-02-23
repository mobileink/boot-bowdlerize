(ns polymer)

(def webjar-config
  [{:webjar '[org.webjars.bower/github-com-PolymerElements-paper-button "1.0.11"]
    :runtime 'polymer.paper/button}
   {:webjar '[org.webjars.bower/github-com-PolymerElements-paper-card "1.0.8"]
    :runtime 'polymer.paper/card}
   {:webjar '[org.webjars.bower/github-com-PolymerElements-iron-icon "1.0.7"]
    :runtime 'polymer.iron/icon}
   {:webjar '[org.webjars.bower/github-com-PolymerElements-iron-icons "1.1.3"]
    :runtime 'polymer.iron/icons}])

(def config-map
  [{:polymer "webcomponentsjs"
    :bundles [{:runtime 'polymer.polyfill/heavy
               :uri "bower_components/webcomponentsjs/webcomponents.js"}
              {:runtime 'polymer.polyfill.min/heavy
               :uri "bower_components/webcomponentsjs/webcomponents.min.js"}
              {:runtime 'polymer.polyfill/lite
               :uri "bower_components/webcomponentsjs/webcomponents-lite.js"}
              {:runtime 'polymer.polyfill.min/lite
               :uri "bower_components/webcomponentsjs/webcomponents-lite.min.js"}]}

   {:polymer "PolymerElements/iron-icon" :runtime 'polymer.iron/icon}
   {:polymer "PolymerElements/iron-icons" :runtime 'polymer.iron/icons}
   {:polymer "PolymerElements/iron-input" :runtime 'polymer.iron/input}

   {:polymer "PolymerElements/paper-card" :runtime 'polymer.paper/card}
   {:polymer "PolymerElements/paper-input"
    :bundles [{:runtime 'polymer.paper/input
               :kw :paper-input
               :uri "bower_components/paper-input/paper-input.html"}
              {:runtime 'polymer.paper.input/textarea
               :kw :paper-textarea
               :uri "bower_components/paper-input/paper-textarea.html"}
              {:runtime 'polymer.paper.input/behavior
               :kw :paper-input-behavior
               :uri "bower_components/paper-input/paper-input-behavior.html"}
              {:runtime 'polymer.paper.input/container
               :kw :paper-input-container
               :uri "bower_components/paper-input/paper-input-container.html"}
              {:runtime 'polymer.paper.input/error
               :kw :paper-input-error
               :uri "bower_components/paper-input/paper-input-error.html"}
              {:runtime 'polymer.paper.input/addon-behavior
               :kw :paper-input-addon-behavior
               :uri "bower_components/paper-input/paper-input-addon-behavior.html"}
              {:runtime 'polymer.paper.input/char-counter
               :kw :paper-input-char-counter
               :uri "bower_components/paper-input/paper-input-char-counter.html"}]}])
