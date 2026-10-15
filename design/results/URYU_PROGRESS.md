# Uryū effects: stage progress (the orchestrator updates this after every stage)

Rules (MASTER_PROMPT.md section 3, set again by the user on 2026-10-15): stages run ONE AFTER ANOTHER so the progress is visible; haiku does the mechanical work (results notes, checklists, lang/phrase regeneration, contact sheets, catalogues), sonnet does the implementation, opus only at gates. Parallel only for independent trees (poses live in their own worktree). The game is launched once per batch, by the orchestrator. Context of everything else: URYU_HANDOFF.md.

| # | Stage | Model | Weight | State |
|---|---|---|---|---|
| S0 | Scaffolding (U-FX 0), Blender models (U-FX 1), storyboard, refs | sonnet | 20 | DONE |
| S1 | U-FX 2: bow forming, draw layers, arrow flight/trail/hit, Focus feedback (also repairs the broken build) | sonnet | 10 | DONE (build green, merged with poses) |
| S2 | U-FX 4: Sprenger outline pentagon + bind visuals | sonnet | 8 | DONE (7f337a9, note uryu_ufx4.md) |
| S3 | U-FX 6: Blut Arterie release + vein overlay | sonnet | 8 | DONE (73d6f7e, note uryu_ufx6.md) |
| S4 | U-FX 5: Seele Schneider combo | sonnet | 6 | DONE (3f122a4, note uryu_ufx5.md) |
| S5 | U-FX 11: poses, finish + review (own worktree `uryu-poses`, parallel) | sonnet | 8 | DONE (merged into uryu-fx-wip, build green) |
| S6a | U-FX 7: Vollständig wings (release, bankai state visual, bracers/boots/emblem) | sonnet | 7 | PARTIAL, stopped by the usage limit: uncommitted WIP saved in commit 'WIP S6a' (UryuWingRenderer, UryuWingFx, UryuVollstTiming, UryuWingMath + tests, Phase7Vollst, edits in BankaiEnd, FxEvents, UryuFx, UryuQuincyFeature); may not compile |
| S6b | U-FX 8: wing barrage + Z arrow | sonnet | 5 | TODO |
| S7 | U-FX 9: Licht Regen | sonnet | 6 | TODO |
| S8 | U-FX 10: Sternenstaub | sonnet | 6 | TODO |
| S9 | U-FX 3: shunpo Quincy look, bankai end/seal | sonnet | 3 | TODO |
| S10 | Merge poses, full build, harness run `runPhase7`, review screenshots with the references, fix list | orchestrator + haiku (contact sheets) | 8 | TODO |
| S11 | U-FX 12: polish (tiers, Reduce Motion, sounds, FPS) | sonnet | 5 | TODO |
After every stage: build green, a short haiku-written results note (design/results/uryu_<stage>.md) with the manual checklist, a commit, this table updated, a message to the user with the percentage (sum of the weights of DONE stages).
