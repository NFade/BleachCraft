# STATE MACHINE: zanpakuto states and reiatsu (phase 4 contract)

Status: PROPOSAL for the phase 4 coding agent. `design/ADR.md` is binding; where this file disagrees with it, ADR wins (see section 8). Pure Java in `dev.minebleach.reiatsutest.core.state` and `core.reiatsu` (no `net.minecraft` / `net.fabricmc` imports, enforced by `CorePurityTest`), plus thin glue in `server/`, `net/`, `registry/`, `client/hud`. Command ids are those of `design/VOICE_PHRASES.md`; timings come from `ART_BIBLE.md` 2.x. Units: time in **server ticks** (20/s), reiatsu in **tenths of a point** (ADR: `int value (tenths)`), tables show points and seconds. All numbers live in one `BalanceConfig` record (defaults below) so they can be retuned without touching logic.

## 1. Ownership and consistency

| Datum | Owner | Where | Sync |
|---|---|---|---|
| `{character, state, stateSinceTick, shikaiMode, bankaiEndTick}` | **Player attachment** `reiatsu_test:zanpakuto` (authoritative, non-persistent) | server `setAttached` only | all players (auras, swarm, costume) |
| `reiatsu {value, max}` | attachment `reiatsu_test:reiatsu` (persistent) | server | owner only |
| cooldown end ticks | attachment `reiatsu_test:cooldowns` (non-persistent) | server | owner only |
| `release_state` component | **Render mirror** on the main-hand stack, written by the server; never read as truth | stack | with the stack |
| Server-only timers (hand-lost tick, last-ability tick, transition lock, GCD, settle, rate bucket, cast serial) | in-memory `PlayerZanpakutoSession` in the pure model, keyed by player UUID | not synced, dropped on logout | none |

Character is derived from the held item when SEALED; at release it is stored and locked until SEALED. `CharacterId.NONE` while SEALED. Two stacks of the same character count as the same zanpakuto (the state belongs to the player, not the stack).

Consistency events (glue translates Minecraft events into `StateMachine` calls):

| Event | Machine input | Result |
|---|---|---|
| Main hand no longer holds the released character's item (scroll, swap to offhand, other item, container open does not count) | `onHandChanged(heldCharacter)` | start grace of 20 ticks (1.0 s): state kept, abilities and transitions denied (`DENIED_ITEM`). Item back within grace: grace cancelled. Else SEALED (`HAND_LOST`). |
| Stack dropped (Q, death drop, hopper) | `onItemDropped()` | immediate SEALED (`ITEM_DROPPED`); glue also resets the dropped stack's component to SEALED (UNVERIFIED hook, otherwise the 20-tick invariant check fixes it on pickup) |
| Player death | `onDeath()` | SEALED, all cooldowns and the session cleared, scheduled ability effects cancelled, temp blocks rolled back, reiatsu set to 50% on respawn |
| Logout / kick / server stop | `onLogout()` | cancel effects, roll back temp blocks; attachment is non-persistent so join starts SEALED; on JOIN glue resets every released stack (no waiting for the 20-tick check). Reiatsu persists |
| Dimension change, gamemode to spectator | `onDimensionChange()` | immediate SEALED (ADR), effects cancelled, reiatsu kept (ADR R3.2 smoke test) |
| Every 20 ticks | invariant check (ADR) | any inventory stack with component != attachment state is rewritten; main-hand stack gets `state`, all others SEALED |

## 2. Transition table

Graph: `SEALED -> SHIKAI -> BANKAI -> SEALED`, plus `SHIKAI -> SEALED`. No `BANKAI -> SHIKAI`, no `SEALED -> BANKAI`.

| # | From | To | Trigger | Guards (all must hold) | Side effects | Effect id |
|---|---|---|---|---|---|---|
| T1 | SEALED | SHIKAI | voice `rukia.shikai.release` / `byakuya.shikai.release`; key R | alive; main hand holds a zanpakuto (character = item); `release_lock` expired; transition lock expired; reiatsu strictly greater than 15.0 | cost 15.0; set component SHIKAI; `stateSinceTick = now`; character locked | 1 rukia, 3 byakuya |
| T2 | SHIKAI | BANKAI | voice `*.bankai.release`; key G | held character == locked character; not in hand grace; `reiatsu == max` (100.0); transition lock expired | cost 20.0 (80.0 left); `bankaiEndTick = now + 900`; settle lock 44 ticks (ability use denied while rows rise, ART 2.5); set component BANKAI | 2 rukia, 4 byakuya |
| T3 | SHIKAI or BANKAI | SEALED | voice `common.seal`; key V | held character == locked character; transition lock expired | cost 0; component SEALED; character NONE; cancel effects, roll back temp blocks; `release_lock = 40` ticks (2 s); ability cooldowns are kept | 10 |
| T4 | BANKAI | SEALED | timeout: `now >= bankaiEndTick` (45 s cap) | none | as T3 but `release_lock = 160` (8 s, bankai recovery); extra warning sound | 11 |
| T5 | BANKAI | SEALED | reiatsu reached 0 (only via upkeep drain, see rule R2) | none | as T4 | 11 |
| T6 | SHIKAI | SEALED | reiatsu reached 0 (safety net, unreachable by design) | none | as T3 | 10 |
| T7 | SHIKAI | SEALED | shikai idle timeout: no ability cast for 2400 ticks (120 s) | none | as T3 | 10 |
| T8 | SHIKAI or BANKAI | SEALED | hand grace expired / item dropped | none | as T3, `release_lock = 0` | 10 |
| T9 | any | SEALED | death, logout, dimension change | none | section 1 | none (client resets from attachment) |

Illegal requests never change state or reiatsu, never start a cooldown, and are answered with `action_result`:

| Reject reason (internal) | `action_result` byte (ADR) | Examples | Client feedback |
|---|---|---|---|
| `NOT_IN_STATE` (also duplicate target = current state) | `DENIED_STATE` | bankai from SEALED, shikai release while SHIKAI, ability of the other state | action bar `message.reiatsu_test.denied.state`, low bass note |
| `WRONG_ITEM` (no zanpakuto, other character, hand grace) | `DENIED_ITEM` | voice command with nothing in hand | action bar `...denied.item` |
| `NOT_ENOUGH_REIATSU`, `BANKAI_NOT_FULL` | `DENIED_REIATSU` | bankai at 99.9, ability above available | action bar plus red flash of the bar for 0.4 s, `block.note_block.bass` |
| `ON_COOLDOWN`, `RELEASE_LOCK`, `TRANSITION_LOCK`, `GCD`, `SETTLE_LOCK` | `COOLDOWN` | repeated voice duplicate, scatter at 1.0 s after bankai | HUD cooldown icon shakes, no chat spam |
| `RATE_LIMITED` | `RATE_LIMIT` | more than 10 C2S per second | none (silent) |
| `DEAD_OR_SPECTATOR` | `DENIED_STATE` | | none |

Order of checks (first failure wins, fixed so tests are deterministic): alive, rate limit, duplicate/state, item, locks (transition, GCD, settle, release lock), cooldown, reiatsu. `OK` returns the new snapshot and a list of `StateEvent`s for the glue.

## 3. Reiatsu

`ReiatsuState(int value, int max)`, `max = 1000` tenths (100.0 points). Value always clamped to `[0, max]`. Applied in **batches every 5 ticks** (ADR, at most 4 syncs/s); rates are chosen so one batch is a whole number of tenths.

| State | Gross regen (per s) | Upkeep drain (per s) | Net per s | Per 5-tick batch (tenths) |
|---|---|---|---|---|
| SEALED | +4.0 | 0 | +4.0 (0 to full in 25 s) | +10 |
| SHIKAI | +3.2 | -1.2 | +2.0 | +8 -3 = +5 |
| BANKAI | +0.8 | -2.4 | -1.6 | +2 -6 = -4 |

Formula per batch: `value = clamp(value + regen(state) - drain(state), 0, max)`; all three rates are integers per batch, no rounding.

Rules:
- R1 Shikai release leaves 85.0 from full; idle shikai refills 15.0 in 150 ticks (7.5 s), so bankai needs a deliberate wait. Any ability spent in shikai must be recovered before bankai.
- R2 **Spending may never take the bar to 0**: an action needs `value - cost >= 1` (tenths). Zero is reachable only through upkeep drain (bankai), which then triggers T5. This stops a cast from sealing itself mid-effect.
- R3 Bankai needs `value == max` exactly; entering costs 20.0, so it starts at 80.0. Idle bankai reaches the 45 s cap (T4) with 80.0 - 72.0 = **8.0** left; casting abilities makes zero (T5) come earlier.
- R4 Respawn: 50.0. Creative mode: costs still charged (config `creativeFree=false`).

Bankai duration: hard cap 900 ticks (45 s) from T2; reiatsu zero ends it earlier. Shikai has no duration cap, only T7 (120 s without a cast) and T6/T8. Auto-revert always goes to SEALED, never to SHIKAI.

## 4. Abilities

Keys (new `KeyBinding`s, category `key.categories.reiatsu_test`, rebindable; none clash with vanilla 1.21.1 defaults; phase 4 verifies against `GameOptions`): **R** release (SEALED to SHIKAI), **G** bankai (SHIKAI to BANKAI), **V** seal, **Z / H / B** ability slots 1/2/3. The client resolves slot + synced state + held item to an `AbilityId` and sends `cast_ability`; a stale mapping is rejected by the server (`DENIED_STATE`). Source byte: KEY=0, VOICE=1.

| Ability id (VOICE_PHRASES) | byte | Char / state | Slot | Cost | Cooldown | Server effect (damage as 1 HP = half heart) | Effect id |
|---|---|---|---|---|---|---|---|
| `rukia.shikai.tsukishiro` | 1 | Rukia / SHIKAI | Z | 25.0 | 12 s | circle radius 4 at the caster's feet; t=1.0 s freeze (Slowness IV + freeze ticks 3 s) all targets inside; t=2.0 s shatter: 6 HP freeze-type damage; max 16 targets | 20 |
| `rukia.shikai.hakuren` | 2 | Rukia / SHIKAI | H | 20.0 | 8 s | line wave, range 12, width 4, speed 24 b/s starting t=1.0 s; each target hit once: 5 HP + Slowness II 3 s; frost layer on path (max 64 temp blocks, rollback after 3 s); max 12 targets | 21 |
| `rukia.shikai.shirafune` | 3 | Rukia / SHIKAI | B | 15.0 | 5 s | hitscan along look vector, reach 8, first living target; t=0.6 s: 8 HP + freeze 4 s; no blocks changed | 22 |
| `rukia.bankai.absolute_zero` | 4 | Rukia / BANKAI | Z | 40.0 | 25 s | sphere radius 10 around caster at cast time; t=2.0 s targets immobilised (Slowness VII, no jump) up to 1.3 s; t=3.3 s shatter: 14 HP; up to 64 temp ice blocks, rollback at t=4.0 to 7.0 s; max 32 targets | 23 |
| (passive, no command) | - | Rukia / BANKAI | - | 0 (covered by drain) | - | every 10 ticks: hostile mobs within 3 get Slowness I (15 ticks) and +freeze ticks; no damage; players excluded | none (client draws from state) |
| `byakuya.shikai.mode_attack` | 5 | Byakuya / SHIKAI | Z | 12.0 | 3 s | target point = look ray up to 24 blocks (server-side raycast); hits at t=0.5, 0.6, 0.7 s: 2 HP each, plus 2 HP at t=1.0 s, radius 1.5 around the point, max 8 targets; sets `shikaiMode = ATTACK` until t=1.5 s; also ends an active dome | 30 |
| `byakuya.shikai.mode_barrier` | 6 | Byakuya / SHIKAI | H | 18.0 | 10 s | `shikaiMode = BARRIER` for 100 ticks (5 s); incoming damage to the caster reduced 80% while it lasts, absorption pool 20 HP then it collapses early; cancelled by `mode_attack`, T3 to T9 | 31 |
| `byakuya.bankai.scatter` | 7 | Byakuya / BANKAI | Z | 30.0 | 20 s | t=0.8 to 1.5 s: tornado radius 5 around the caster, 1.5 HP per 10 ticks to targets (caster safe); t=2.5 s impact at the aimed point (ray up to 40): 12 HP radius 5 + knockback 1.0; max 24 targets | 32 |
| `byakuya.bankai.hakuteiken` | 8 | Byakuya / BANKAI | H | 50.0 | 40 s | t=1.5 s line along look vector up to 20 blocks, width 2: 12 HP; t=1.8 s impact burst radius 5 at the line end: 24 HP; max 24 targets | 33 |
| `byakuya.bankai.senkei` | 9 | Byakuya / BANKAI | B | 35.0 | 25 s | OPTIONAL, `enabled=false` by default; 96 hits of 0.5 HP within radius 4 of the target, max 16 targets | 34 |

Global rules for abilities:
- Ability requires its own state and character (table above); cooldown starts at accepted cast, not at completion; **GCD** 12 ticks (0.6 s) between any two abilities; settle lock 44 ticks after T2.
- The byte `abilityId` space is shared with `cast_ability` (ADR); transitions use `request_transition(targetState)`.
- Damage is attributed to the caster (`DamageSource` with attacker): Rukia freeze-type, Byakuya magic-type (exact `DamageTypes` keys UNVERIFIED, phase 4 checks). Never targets the caster, tamed pets of the caster, spectators, entities tagged `#reiatsu_test:immune` (default: villager, wandering_trader), or other players unless config `affectPlayers=true` and server PvP is on. Area queries run once per phase on loaded chunks only, centred on the server-side position and look vector (client aim is never trusted).
- Scheduled phases run through a `cast serial`: every scheduled action of a cast is dropped when the caster leaves the required state (seal, T4 to T9) or the serial changes. Seal rolls temp blocks back immediately.
- **No permanent world change.** Only temporary ice/snow (allowlist `ice`, `snow` layer 1) placed on air, snow layers or source water, never on block entities, never replacing solid blocks; per player at most 128 temp blocks alive. Every placed position is stored with its previous state in a `TempBlockJournal` (pure model `core`), persisted via a `PersistentState` and replayed on `SERVER_STARTED`, so a crash cannot leave ice behind. Rolled back on timeout, seal, death, logout, dimension change, server stop. Blade rows, petals, crystals and shells are client visuals only.

## 5. Pure-Java API sketch

Package `core.state` unless noted. Everything is deterministic given `Clock`.

```java
public enum CharacterId { NONE(0), RUKIA(1), BYAKUYA(2); public final byte code; }
public enum ZanpakutoState { SEALED, SHIKAI, BANKAI }
public enum RequestSource { KEY, VOICE }                       // wire bytes 0, 1
public enum ShikaiMode { IDLE, ATTACK, BARRIER }
public enum Trigger { VOICE, KEY, BANKAI_CAP, SHIKAI_IDLE, REIATSU_ZERO,
                      HAND_LOST, ITEM_DROPPED, DEATH, LOGOUT, DIMENSION_CHANGE }
public enum RejectReason { NOT_IN_STATE, WRONG_ITEM, NOT_ENOUGH_REIATSU, BANKAI_NOT_FULL,
    ON_COOLDOWN, RELEASE_LOCK, TRANSITION_LOCK, GCD, SETTLE_LOCK, RATE_LIMITED, DEAD_OR_SPECTATOR;
    ResultCode toWire(); }                                     // ADR action_result byte
public enum ResultCode { OK, DENIED_STATE, DENIED_ITEM, DENIED_REIATSU, COOLDOWN, RATE_LIMIT }

public enum AbilityId { TSUKISHIRO(1,"rukia.shikai.tsukishiro",RUKIA,SHIKAI), /* ... section 4 ... */;
    byte code; String commandId; CharacterId character; ZanpakutoState requiredState; }

public record AbilitySpec(AbilityId id, int costTenths, int cooldownTicks, int effectId, boolean enabled) {}
public record BalanceConfig(int maxTenths, int regenBatchTicks, EnumMap<ZanpakutoState,Rate> rates,
    int shikaiReleaseCost, int bankaiCost, int bankaiCapTicks, int shikaiIdleTicks, int handGraceTicks,
    int transitionLockTicks, int gcdTicks, int settleTicks, int sealLockTicks, int recoveryLockTicks,
    Map<AbilityId,AbilitySpec> abilities) { static BalanceConfig defaults(); }

public interface Clock { long nowTick(); }                      // glue: MinecraftServer#getTicks (UNVERIFIED, NOT world time: /time set must not matter)
public final class FakeClock implements Clock { void advance(long ticks); void set(long t); }  // test source set

public record TransitionRequest(ZanpakutoState target, RequestSource source, int clientSeq, CharacterId held) {}
public record AbilityRequest(AbilityId ability, RequestSource source, int clientSeq, CharacterId held) {}
public record TransitionResult(ResultCode code, RejectReason reason, ZanpakutoState from, ZanpakutoState to,
    List<StateEvent> events) { boolean ok(); }                  // ability casts reuse it with from == to

public sealed interface StateEvent permits StateChanged, ReiatsuSpent, CooldownStarted, BroadcastEffect,
    CancelEffects, RollbackTempBlocks, MirrorComponent, ScheduledPhase {}
// BroadcastEffect(effectId, seed), ScheduledPhase(offsetTicks, phaseId) are executed by server glue.

public record ZanpakutoSnapshot(CharacterId character, ZanpakutoState state, long stateSinceTick,
    ShikaiMode shikaiMode, long bankaiEndTick) {}               // 1:1 with the attachment record

public final class StateMachine {                               // one per player, owned by the server glue
    StateMachine(Clock clock, BalanceConfig cfg, ReiatsuState initial);
    TransitionResult request(TransitionRequest r);
    TransitionResult cast(AbilityRequest r);
    List<StateEvent> tick(int dtTicks);                         // after clock.advance(dt); walks tick by tick (cap 100) so catch-up is exact
    List<StateEvent> onHandChanged(CharacterId held);
    List<StateEvent> onItemDropped(); onDeath(); onLogout(); onDimensionChange();
    ZanpakutoSnapshot snapshot(); ReiatsuState reiatsu(); int cooldownRemaining(AbilityId id);
}
public final class RateLimiter { RateLimiter(Clock c, int perSecond); boolean tryAcquire(); }  // 10/s, token bucket
```

`core.reiatsu`:

```java
public record ReiatsuState(int value, int max) {                // compact ctor clamps; invariant 0 <= value <= max
    ReiatsuState plus(int d); boolean isFull(); boolean canSpend(int cost);   // value - cost >= 1
}
public record Rate(int regenPerBatch, int drainPerBatch) {}
public final class ReiatsuMath { static ReiatsuState applyBatch(ReiatsuState s, Rate r);
    static int ticksToFull(ReiatsuState s, Rate r); }
```

Tick order inside one tick (fixed): (1) expire cooldowns and locks; (2) hand grace check; (3) if `now % 5 == 0` apply a reiatsu batch; (4) reiatsu 0 check (T5/T6); (5) bankai cap check (T4); (6) shikai idle check (T7). Requests arriving in the same server tick are processed before `tick` in arrival order.

## 6. Unit tests (JUnit 5, `src/test/java/.../core/state` and `core/reiatsu`)

Legal transitions (L): L1 T1 Rukia and L2 T1 Byakuya (cost 15.0, effect 1/3, component mirror); L3 T2 both characters at exactly 100.0 (cost 20.0 -> 80.0, `bankaiEndTick = now+900`); L4 T3 from SHIKAI; L5 T3 from BANKAI (cooldowns kept, `release_lock 40`); L6 T4 cap; L7 T5 zero; L8 T6; L9 T7 idle; L10 T8 grace expiry; L11 key and voice give identical results; L12 full cycle SEALED-SHIKAI-BANKAI-SEALED re-enterable after lock.

Illegal transitions (I): I1 SEALED to BANKAI; I2 BANKAI to SHIKAI; I3 SHIKAI to SHIKAI and BANKAI to BANKAI (duplicate, no cost); I4 bankai at 99.9; I5 release at exactly 15.0 (strict rule) and 15.1; I6 no item / other character's item (both directions); I7 character mismatch in SHIKAI; I8 during hand grace; I9 dead player; I10 each rejection leaves reiatsu, cooldowns and snapshot unchanged (property test over all state x request pairs: 3 states x 3 targets x 3 held values); I11 reject reason to wire byte mapping is total; I12 check order (state beats item beats lock beats cooldown beats reiatsu).

Reiatsu (R): R1 batch values per state (+10 / +5 / -4); R2 25 s sealed regen 0 to max, 150 ticks shikai refill from 85.0 to 100.0; R3 clamp at max and at 0 (never negative, never above max, including huge dt); R4 non-batch ticks change nothing; R5 R2 rule: spend to 0 impossible, spend to 0.1 possible; R6 idle bankai after 900 ticks has 80 tenths; R7 `tick(dt)` with dt 1 x 100 equals dt 100 (catch-up equals stepping); R8 `ReiatsuState` rejects invalid construction by clamping; R9 `ticksToFull`.

Cooldowns and abilities (C): C1 each ability charges its cost and starts its cooldown at cast; C2 cooldown expires exactly at `cdTicks` (denied at tick-1, accepted at tick); C3 GCD 12 ticks across different abilities; C4 settle lock 44 ticks after T2 (scatter at 43 denied `COOLDOWN`, at 44 accepted); C5 ability in wrong state/character denied; C6 cooldowns survive seal and are cleared by death; C7 insufficient reiatsu denied without a cooldown; C8 `mode_attack` ends an active barrier; C9 barrier shikaiMode returns to IDLE at 100 ticks; C10 disabled `senkei` rejected; C11 events list contains the expected `ScheduledPhase` offsets (tsukishiro 20 and 40 ticks, absolute zero 66).

Auto-revert (A): A1 bankai cap at 900 ticks exactly (tick 899 BANKAI, 900 SEALED) with 8.0 left; A2 earlier zero with abilities spent (`CancelEffects` + `RollbackTempBlocks` emitted); A3 recovery lock 160 vs seal lock 40: release denied at lock-1, accepted at lock; A4 shikai idle 2400 ticks reset by each cast; A5 hand grace 20 ticks: back at 19 keeps state, at 20 seals; A6 drop is immediate; A7 death/logout/dimension reset everything and emit `CancelEffects`; A8 respawn reiatsu 50.0.

Debounce interplay (D): D1 duplicate `request(SHIKAI)` 1 tick apart: first OK, second `DENIED_STATE`, reiatsu charged once; D2 release then seal within 10 ticks -> `TRANSITION_LOCK`; D3 voice "Bankai, Senbonzakura Kageyoshi" then "Chire" 4 ticks later -> scatter denied (`SETTLE_LOCK`/`TRANSITION_LOCK`); D4 same `clientSeq` replayed or lower seq ignored; D5 rate limiter: 10 accepted per 20 ticks, 11th `RATE_LIMIT`, bucket refills with the fake clock; D6 a rejected request does not consume a rate token twice or start any cooldown; D7 identical results whether the same request arrives via KEY or VOICE.

Meta: M1 `CorePurityTest` (no `net.minecraft`/`net.fabricmc` imports in `core/`); M2 `BalanceConfig.defaults()` invariants (all rates divisible so a batch is integral, costs < max, bankai cap < ticks-to-zero from 80.0 idle, every `AbilityId` has a spec, ability codes unique); M3 `AbilityId.commandId` set equals the `state`-gated ability ids in `voice/phrases.json`.

## 7. Server glue duties (not unit-tested here)

Per server tick call `session.tick`; write attachments only when the snapshot or reiatsu changed (reiatsu at most every 5 ticks); execute `StateEvent`s (broadcast `effect_event` to the caster and `PlayerLookup.tracking`, `entity_fx` for frozen mobs, component mirror, temp block journal); send `action_result` to the caster for every request. Hand check each tick via the main-hand stack's item class. Keep the 20-tick invariant check from ADR.

## 8. Conflicts and open points

Conflicts (ADR followed):
1. VOICE_PHRASES 5.4 says interim triggers are off by default (>= 0.97, 150 ms apart); ADR 4 fires interim at >= 0.93 stable over 2 interims. **ADR wins.** The server-side locks here (transition lock 10 ticks, settle 44 ticks) make premature or duplicate interim fires harmless: they are rejected with no cost.
2. VOICE_PHRASES 5.5 proposes a 600 ms global block after state change; ADR has a 300 ms global gap and 1500 ms per-command debounce. **ADR wins** for the matcher; the machine adds its own 500 ms transition lock independent of it.
3. MASTER_PROMPT phase 4 allows "four items or one per character"; ADR section 1 fixes one item per character + `release_state`. Followed.
4. ADR attachment record has no shikai-idle, hand-grace or lock fields; they are server-only session fields (not synced). The synced snapshot is exactly the ADR record.
5. ADR `action_result` enum has no separate lock/rate-limit-by-lock reasons; all lock kinds map to `COOLDOWN`. Fine, but the HUD cannot tell them apart (acceptable for a prototype).
6. ADR registry layout lists no blocks; this file uses vanilla `ice`/`snow` only, with a persisted journal instead of a custom self-reverting block.

Open points: (a) `MinecraftServer#getTicks`, drop-time hook, `DamageTypes` keys, `GameOptions` key list are UNVERIFIED names: verify in phase 4. (b) Attachment survival across dimension change (ADR R3.2) decides whether reiatsu needs the fallback `state_sync`. (c) Relog resets all cooldowns (cooldowns are non-persistent per ADR): an exploit accepted for the prototype, or persist them. (d) Dropped item entities show SHIKAI until picked up (cosmetic). (e) Senkei, `affectPlayers`, creative cost, idle timeout 120 s and all balance numbers are tuning candidates after the first play test. (f) `byakuya.bankai.scatter` and `hakuteiken` have no required prior step; a combo requirement (blades must be up) is intentionally absent.
