(ns styles)

(alter-meta! *ns* (fn [m] (assoc m :config true :resource-type :css)))

(def main {:uri "styles/main.css"
            :media "(min-width: 700px) and (orientation: landscape)"})

(def hello {:uri "styles/hello.css"})

;; indirection - the var you use in your code need not match the name of the underlying asset
(def world {:uri "styles/foo.css"})
