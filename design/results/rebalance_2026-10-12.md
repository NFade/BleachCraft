# Damage rebalance for netherite fights (2026-10-12)

Goal (user): a fight in full netherite lasts several minutes and the power grows: sword + kido first, then shikai, then bankai.
UNVERIFIED by play: only unit tests were run, numbers come from a paper estimate.

Paper model: netherite (armor 20, toughness 12) cuts sword hits by about 75 percent. Ability and kido damage uses FREEZE / INDIRECT_MAGIC
which ignore armor, so those numbers hit the 20 HP bar raw. All ability numbers were therefore cut hard; rage (shikai 40, bankai 80) is unchanged
and gives the pacing (passive 1.0 per second in combat plus 1.0 per hp dealt, 1.5 per hp taken).

| Item | Before | After |
|---|---|---|
| Sword attack damage bonus (`ModItems.ATTACK_DAMAGE_BONUS`) | 6 (7 per hit) | 3 (4 per hit, about 1 HP after netherite) |
| Byakurai (kido) damage / cooldown | 6 / 3 s | 2.5 / 5 s |
| Shakkaho (kido) centre damage / cooldown | 10 / 7 s | 4 / 10 s |
| Tsukishiro | 6 | 3 |
| Hakuren | 5 | 2 |
| Shirafune | 8 | 3.5 |
| Ice spike (bankai) | 9 | 4 |
| Byakuya attack mode (landing) / swarm hit in flight | 2 / 1.0 | 1 / 0.5 |
| Petal cage per pulse (6 pulses) | 1.5 | 0.8 |
| Scatter (group, centre / storm) | 16 / 2 | 6 / 1 |
| Hakuteiken (burst / line) | 24 / 14 | 9 / 5 |
| Rukia bankai field tick | 2.0 per 3 s | unchanged (user asked for 1 heart per 3 s) |

Tuning knobs: `BalanceConfig.defaults()`, `KidoConfig.defaults()`, `FlightConfig` default, `ModItems.ATTACK_DAMAGE_BONUS`; the kido and swarm numbers
are also in the json config. Check in play: time to kill a netherite player in each phase; adjust by scaling these tables.
