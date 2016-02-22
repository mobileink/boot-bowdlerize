(ns polymer)

(def config-map
  {"PolymerElements/iron-icon" 'miraj.iron/icon
   "PolymerElements/paper-button" 'miraj.paper/button
   "PolymerElements/paper-card" {:ns 'miraj.paper :name 'card}
   "PolymerElements/paper-input" [{:ns 'miraj.paper
                                   :name 'input
                                   :uri "bower_components/paper-input/paper-input.html"}
                                  {:ns 'miraj.paper.input
                                   :name 'textarea
                                   :uri "bower_components/paper-input/paper-textarea.html"}
                                  {:ns 'miraj.paper.input
                                   :name 'behavior
                                   :uri "bower_components/paper-input/paper-input-behavior.html"}
                                  {:ns 'miraj.paper.input
                                   :name 'container
                                   :uri "bower_components/paper-input/paper-input-container.html"}
                                  {:ns 'miraj.paper.input
                                   :name 'error
                                   :uri "bower_components/paper-input/paper-input-error.html"}
                                  {:ns 'miraj.paper.input
                                   :name 'addon-behavior
                                   :uri "bower_components/paper-input/paper-input-addon-behavior.html"}
                                  {:ns 'miraj.paper.input
                                   :name 'char-counter
                                   :uri "bower_components/paper-input/paper-input-char-counter.html"}]})
