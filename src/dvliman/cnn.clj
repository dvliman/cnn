(ns dvliman.cnn
  (:require [clj-http.client :as client]
            [hickory.core :refer [parse as-hiccup as-hickory]]
            [hickory.select :as s]
            [clojure.core.memoize :as memo]
            [cheshire.core :as json]
            [ring.adapter.jetty9 :as jetty]))

(def base-url "https://lite.cnn.io/")

(defn news-url [url-suffix]
  (str base-url url-suffix))

(defn parse-frontpage [doc]
  (->> doc
       (s/select (s/descendant  (s/class "afe4286c")
                                (s/and (s/tag :ul))
                                (s/and (s/tag :li))
                                (s/tag :a)))
       (map #(select-keys % [:content :attrs]))
       (map #(:href (:attrs %)))))

(defn parse-newspage [doc]
  (let [title        (s/select (s/descendant (s/class "afe4286c") (s/tag :h2)) doc)
        subtitle     (s/select (s/descendant (s/class "afe4286c") (s/id "byline")) doc)
        published    (s/select (s/descendant (s/class "afe4286c") (s/id "published datetime")) doc)
        source       (s/select (s/descendant (s/class "afe4286c") (s/id "source")) doc)
        editors-note (s/select (s/descendant (s/class "afe4286c") (s/id "editorsNote")) doc)
        paragraphs   (s/select (s/descendant (s/class "afe4286c") (s/tag :p)) doc)]
    {:title     (-> title first :content first)
     :subtitle  (-> subtitle  first :content first)
     :published (-> published first :content first)
     :source    (-> source first :content first)
     :paragraphs (->> paragraphs
                      (filter #(nil? (:attrs %)))
                      (map :content)
                      (map first))}))

(defn fetch [url]
  (-> (client/get url) :body parse as-hickory))

(defn scrape []
  (->> (fetch base-url)
       parse-frontpage
       (map news-url)
       (map (comp parse-newspage fetch))))

(defn cached-scrape []
  (memo/ttl scrape {} :ttl/threshold (* 3600 1000)))


(defn handler [req]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/encode (scrape))})

(defn -main [& args]
  (run-jetty handler {:port 3000}))
