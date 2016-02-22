(ns themes)

(alter-meta! *ns* (fn [m] (assoc m :config true :resource-type :html-import)))

(def app {:uri "themes/app-theme.html"})
