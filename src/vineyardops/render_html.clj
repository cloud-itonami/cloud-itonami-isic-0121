(ns vineyardops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5). Drives the REAL actor stack (`vineyardops.operation` ->
  `vineyardops.governor` -> `vineyardops.store`) through a scenario adapted
  from this repo's own `vineyardops.sim` demo driver (combined into one
  seeded store), rendered deterministically -- no invented numbers/ids/ops.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [vineyardops.store :as store]
            [vineyardops.operation :as op]
            [langgraph.graph :as g]))

;; Per-call context (this variant's sim passes :phase per-request, no :role).
(defn- exec! [actor tid request ctx]
  (g/run* actor {:request request :context ctx} {:thread-id tid}))
(defn- approve! [actor tid by]
  (g/run* actor {:approval {:status :approved :by by}} {:thread-id tid :resume? true}))
(defn- reject! [actor tid by]
  (g/run* actor {:approval {:status :rejected :by by}} {:thread-id tid :resume? true}))

(def ^:private ctx #(hash-map :actor-id "vineyard-ops-01" :phase %))

(defn run-demo!
  "Seeds vineyard-001 plus references an unregistered vineyard-ghost, then
  runs every disposition: a crop-health concern (phase-2, always escalates
  -- agronomist approves), a low-risk field-operation schedule (phase-1
  auto-commit), a phase-0 sandbox schedule that phase-gates to escalation
  (operator approves), and a log-vineyard-record against the unregistered
  vineyard-ghost (HARD block). Every id/op/value is from vineyardops.sim /
  vineyardops.governor / vineyardops.store -- no invented values."
  []
  (let [db (store/mem-store
            {:initial-vineyards
             {"vineyard-001" {:id "vineyard-001" :name "Test Vineyard Block" :grape-class "wine-grape"}}})
        actor (op/build db)]
    (exec! actor "t1" {:op :flag-crop-health-concern :vineyard-id "vineyard-001"
                       :concern "powdery-mildew-suspected"} (ctx :phase-2))
    (approve! actor "t1" "agronomist-01")
    (exec! actor "t2" {:op :schedule-field-operation :vineyard-id "vineyard-001"
                       :requested-date "2026-08-01"} (ctx :phase-1))
    (exec! actor "t3" {:op :schedule-field-operation :vineyard-id "vineyard-001"
                       :requested-date "2026-09-01"} (ctx :phase-0))
    (approve! actor "t3" "vineyard-ops-01")
    (exec! actor "t4" {:op :log-vineyard-record :vineyard-id "vineyard-ghost" :count 500} (ctx :phase-3))
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v] (-> (str v) (str/replace "&" "&amp;") (str/replace "<" "&lt;") (str/replace ">" "&gt;")))

(defn- hold-rule [f]
  (or (some-> f :basis first) (some-> f :violations first :rule)))

(defn- last-fact-for [ledger vid] (last (filter #(= (:subject %) vid) ledger)))

(defn- status-cell [ledger vid]
  (let [f (last-fact-for ledger vid)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :approval-rejected (:t f)) "<span class=\"critical\">rejected (hold)</span>"
      (= :governor-hold (:t f))
      (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or (hold-rule f) :unknown))) "</span>")
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ["        <tr><td><code>:flag-crop-health-concern</code></td><td><span class=\"warn\">ALWAYS human approval (crop safety)</span></td></tr>"
   "        <tr><td><code>:schedule-field-operation</code></td><td><span class=\"ok\">auto-commit at phase-1+ when clean + registered; phase-0 always escalates</span></td></tr>"
   "        <tr><td><code>:log-vineyard-record</code></td><td><span class=\"warn\">registered-vineyard required; else HARD block</span></td></tr>"])

(defn render [db]
  (let [ledger (vec (store/ledger db))
        v-ids ["vineyard-001" "vineyard-ghost"]
        vrow (fn [vid]
               (let [v (store/registered-vineyard db vid)]
                 (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
                         (esc vid) (esc (or (:name v) "(unregistered)"))
                         (esc (or (:grape-class v) "—")) (status-cell ledger vid))))
        vineyard-rows (str/join "\n" (map vrow v-ids))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-0121 &middot; vineyard ops</title><style>"
     "body{font:14px/1.5 -apple-system,system-ui,sans-serif;margin:0;color:#1a1a1a;background:#f5f5f5}"
     ".bar{background:#3a0a2a;color:#fff;padding:1.2rem 2rem}.bar h1{margin:0;font-size:1.15rem;font-weight:600}"
     ".badge{display:inline-block;margin-top:.4rem;font-size:.75rem;opacity:.8}"
     "main{max-width:980px;margin:1.5rem auto;padding:0 1rem}"
     ".card{background:#fff;border-radius:8px;padding:1.2rem 1.4rem;margin-bottom:1.2rem;box-shadow:0 1px 3px rgba(0,0,0,.08)}"
     ".card h2{margin-top:0;font-size:1rem}.muted{color:#777;font-size:.82rem}"
     "table{border-collapse:collapse;width:100%;font-size:.85rem}th,td{text-align:left;padding:.42rem .5rem;border-bottom:1px solid #eee}th{font-weight:600;color:#555}"
     ".ok{color:#0a7d33}.warn{color:#9a6700}.critical{color:#b41010;font-weight:600}code{background:#f0f0f0;padding:.1rem .3rem;border-radius:3px;font-size:.8rem}"
     "</style></head><body>\n"
     "<header class=\"bar\">\n  <h1>Vineyard ops (ISIC 0121) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · crop-health always human-approved · phase-0 never auto-commits · unregistered blocks HARD-blocked</span>\n</header>\n"
     "<main>\n  <section class=\"card\">\n    <h2>Scenario vineyard blocks</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>vineyardops.store</code> via <code>vineyardops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly. No invented data.</p>\n"
     "    <table>\n      <thead><tr><th>Vineyard</th><th>Name</th><th>Grape class</th><th>Last op status</th></tr></thead>\n      <tbody>\n"
     vineyard-rows "\n      </tbody>\n    </table>\n  </section>\n"
     "  <section class=\"card\">\n    <h2>Action gate (VineyardOps Governor)</h2>\n"
     "    <p class=\"muted\">HARD blocks cannot be overridden. Unregistered vineyards are rejected before any human; phase-0 (sandbox) never auto-commits.</p>\n"
     "    <table>\n      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n      </tbody>\n    </table>\n  </section>\n"
     "  <section class=\"card\">\n    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n      <tbody>\n"
     ledger-rows "\n      </tbody>\n    </table>\n  </section>\n"
     "</main>\n</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!) out-file (java.io.File. out)]
    (.. out-file getParentFile mkdirs)
    (spit out-file (render db))
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts )")))
