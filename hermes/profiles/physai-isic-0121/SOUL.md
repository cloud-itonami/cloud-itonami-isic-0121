# physai-isic-0121 — ぶどう栽培（ISIC 0121）の収穫作業を担うロボットの physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isic-0121`、ISIC Rev.4 0121 ぶどう栽培）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

README の Robotics premise: 施設管理ロボットが畑区画の記録・作業スケジュール・資材の在庫と発注・監査台帳を扱う。物理的な仕事は収穫期に、摘み取りコンテナを斜面の畝で運び上げ、コンテナを収穫ビンに空け、摘んだぶどうを醸造所に届く前に冷やすこと。
その物理的な仕事を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:grape-lugs-up-hillside-row` | transport | 満杯のコンテナを積んだクローラ運搬車が斜面の畝 120 m を枕地のビンまで登る | 1 区間の所要時間 | 130 s（estimate） |
| `:lug-into-harvest-bin` | manipulator | 運搬車のコンテナを持ち上げて収穫ビンの縁越しに空ける | 肩関節ピークトルク | 150 N·m（estimate） |
| `:picked-grapes-cooling` | thermal | コンテナの 15 cm の房の層を 2 °C の冷蔵室で両面から冷やす（層の中央） | 中央温度 | 10 °C（estimate） |

測定の入口: `kbb -M:dev:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:dev:physai-test`（repo 自身の `test/` に加えて `test-physai/vineyardops/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する）。
physics の spec test は `test/` ではなく `test-physai/` に置いてある（repo 自身の runner が `test/` 全体を読むため）。

## 測って分かったこと・限界（成長の第一候補）

1. **斜面の運搬**: 勾配 0〜10° で所要時間 101.8 s のまま（加速度上限 0.5 m/s²）。15° で駆動力が効き 103.24 s、20° で停止（stall）。
   境界は勾配 **16.3°** —— 急斜面の畑ではまず登坂能力が尽きる。
2. **コンテナのアーム**: 肩トルクは 5 kg で 78.2 N·m、10 kg で 114.3 N·m、15 kg で 150.3 N·m。限界 150 N·m に達するコンテナは **14.96 kg**。
3. **房の冷却**: 中央温度は 1 h で 27.5 °C、4 h で 19.1 °C、8 h で 11.3 °C、12 h で 7.08 °C。10 °C に達するのは **9.0 h（32400 s）**。
4. **estimate のままの値**: 区間 130 s、肩トルク上限 150 N·m、破砕前の目標 10 °C（醸造の指針で置き換える）、
   房の層の熱伝導率 0.35・かさ密度 600・比熱 3600、熱伝達率 15、運搬車の駆動力 1200 N・転がり抵抗係数 0.07。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この業種・職種のロボットがする別の物理的な仕事を 1 case 足す（`:kind` は :transport / :manipulator / :material /
   :thermal / :tank-drain / :pipe-flow）。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isic-0121 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:dev:physai-test → kbb -M:dev:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isic-0121 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
