# R11 T12: лист звуков (sound sheet)

Статус: инфраструктура и лист готовы в коде, тесты зелёные. **Звук в игре не проверен (UNVERIFIED)**: игру агент не запускал. Файлов из аниме в репозитории нет и не будет;
без файла событие играет прежний ванильный запасной звук (или молчит, если запасного нет).

**Правовое правило.** Звуки и голоса из аниме защищены авторским правом, репозиторий публичный. Файлы кладёт пользователь у себя локально
(`mod/src/main/resources/assets/reiatsu_test/sounds/anime/` или отдельный ресурспак `reiatsu_test_anime_sounds`); обе папки в `.gitignore`.
По умолчанию нужны только эффекты (SFX). Голоса сэйю, имена приёмов, крики и музыка по умолчанию НЕ используются (колонка «риск»).

## Как читать таблицу

- **id**: событие `reiatsu_test:<id>`, ключ в `config/reiatsu_sounds.json`. Файл клипа: `sounds/anime/<группа>/<остальное>_N.ogg`, группа это первый сегмент id, остальное
  склеено через `_` (колонка «файл» даёт вариант `_1`; `_2`, `_3` ... дают случайный выбор, их добавляет ресурспак, см. отчёт `r11_T12.md`).
- **что слышно (ориентир)**: краткое описание своими словами по общим знаниям о сериале; это ориентир для поиска клипа, а не сверка по сериям. Источник сцен пользователь ещё не выбрал
  (старое ТВ-аниме 2004..2012 или Thousand-Year Blood War 2022+), поэтому колонка **сцена** пуста и заполняется пользователем (сезон, серия, время); ничего не выдумано.
- **длина**: нужная длина клипа в секундах. **тип**: `oneshot` (играется один раз) или `loop` (событие повторяется кодом через интервал; настоящих зацикленных звуков в моде пока нет, клип должен быть
  бесшовным или с затуханием).
- **запасной ванильный звук**: что играет без файла. `A + B` это слои одного момента (они звучат вместе, как раньше). `-` значит «молчит без файла».
- **статус**: `нужен файл` (событие подключено в коде, файла пока нет), `резерв` (id зарегистрирован, в коде пока не вызывается; подключается позже), `есть` (пользователь положил файл), `не используется`.
  Статус «есть» ставит пользователь вручную; `tools/sounds_check.py` проверяет сам файл на диске.
- **риск**: `SFX` (эффект, обычно безопасно для локального использования), `голос`, `музыка`, `UI`.

## Общие: меч в ножнах

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `general.draw.start` | DrawFx.onDraw (DRAW_START) | тихий шорох и лёгкий звон меча, который берут из ножен |  | 0.3-0.6 | oneshot | item.armor.equip_iron | нужен файл | SFX | `anime/general/draw_start_1.ogg` |
| `general.draw.release` | DrawFx.onDraw (DRAW_RELEASE) | звонкий скользящий звук клинка, выходящего из сая |  | 0.5-0.9 | oneshot | item.trident.return | нужен файл | SFX | `anime/general/draw_release_1.ogg` |
| `general.sheathe.start` | DrawFx.onDraw (SHEATHE_START) | клинок скользит обратно в ножны |  | 0.4-0.8 | oneshot | item.armor.equip_chain | нужен файл | SFX | `anime/general/sheathe_start_1.ogg` |
| `general.sheathe.end` | DrawFx.onDraw (SHEATHE_END) | короткий щелчок цубы о горловину сая |  | 0.15-0.35 | oneshot | item.armor.equip_iron | нужен файл | SFX | `anime/general/sheathe_end_1.ogg` |

## Общие: удары мечом (резерв)

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `general.sword.swing` | не подключено (удар мечом) | свист клинка при взмахе |  | 0.2-0.5 | oneshot | - | резерв | SFX | `anime/general/sword_swing_1.ogg` |
| `general.sword.hit_flesh` | не подключено (попадание по существу) | глухой рубящий удар по цели |  | 0.2-0.5 | oneshot | - | резерв | SFX | `anime/general/sword_hit_flesh_1.ogg` |
| `general.sword.hit_block` | не подключено (попадание по блоку) | лязг клинка о твёрдое |  | 0.2-0.5 | oneshot | - | резерв | SFX | `anime/general/sword_hit_block_1.ogg` |

## Общие: блок и парирование

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `general.block.hit` | BlockFx.blockWorld; BlockParry.onHit (сервер, запасной путь) | глухой лязг клинка, принявшего удар (блок мечом) |  | 0.3-0.7 | oneshot | item.shield.block + block.anvil.land + entity.iron_golem.damage + block.chain.hit | нужен файл | SFX | `anime/general/block_hit_1.ogg` |
| `general.block.ability` | BlockFx.abilityWorld | приглушённый звон: клинок гасит часть удара способности |  | 0.3-0.6 | oneshot | item.shield.block + block.anvil.land + entity.iron_golem.damage | нужен файл | SFX | `anime/general/block_ability_1.ogg` |
| `general.parry` | BlockFx.parryWorld; BlockParry.onHit (сервер, запасной путь) | резкий высокий звон скрестившихся клинков и искры (парирование) |  | 0.4-0.9 | oneshot | block.amethyst_block.chime x2 + block.bell.use + item.trident.hit + entity.player.attack.crit | нужен файл | SFX | `anime/general/parry_1.ogg` |

## Общие: урон и смерть (резерв)

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `general.hurt` | не подключено (получение урона) | короткий вскрик/удар по телу (голос: избегать) |  | 0.2-0.5 | oneshot | - | резерв | голос | `anime/general/hurt_1.ogg` |
| `general.death` | не подключено (смерть) | падение тела, затухающий выдох (голос: избегать) |  | 0.5-1.5 | oneshot | - | резерв | голос | `anime/general/death_1.ogg` |

## Общие: релиз, печать, шунпо, давление реяцу

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `general.seal.collapse` | SealFx.play (id 10: шикай/банкай -> запечатан) | реяцу втягивается обратно в меч, клинок возвращается в простую форму |  | 0.6-1.2 | oneshot | block.beacon.deactivate (+ item.trident.return у Бьякуи) | нужен файл | SFX | `anime/general/seal_collapse_1.ogg` |
| `general.bankai.end` | SealFx.play (id 11: окончание банкая) | банкай рассыпается: тяжёлый затухающий гул, удар сердца |  | 1.0-2.0 | oneshot | block.beacon.deactivate + block.bell.resonate + block.respawn_anchor.deplete | нужен файл | SFX | `anime/general/bankai_end_1.ogg` |
| `general.title_card` | ReleaseFx.play; RukiaBankaiFx.release (только владелец) | звон колокола при выходе названия банкая (музыка/голос: избегать) |  | 0.5-1.2 | oneshot | block.bell.use | нужен файл | музыка/голос | `anime/general/title_card_1.ogg` |
| `general.shunpo.start` | ShunpoFx.play (старт) | резкий свист и хлопок воздуха при шунпо |  | 0.2-0.5 | oneshot | entity.player.attack.sweep | нужен файл | SFX | `anime/general/shunpo_start_1.ogg` |
| `general.shunpo.arrive` | ShunpoFx.play (прибытие) | более короткий свист на финише рывка |  | 0.15-0.4 | oneshot | entity.player.attack.sweep | нужен файл | SFX | `anime/general/shunpo_arrive_1.ogg` |
| `byakuya.shunpo.accent` | ShunpoFx.play (Бьякуя) | шелест лепестков, остающийся после рывка |  | 0.3-0.8 | oneshot | block.cherry_leaves.break | нужен файл | SFX | `anime/byakuya/shunpo_accent_1.ogg` |
| `rukia.shunpo.accent` | ShunpoFx.play (Рукия) | тонкий ледяной перезвон после рывка |  | 0.3-0.8 | oneshot | block.amethyst_block.chime | нужен файл | SFX | `anime/rukia/shunpo_accent_1.ogg` |
| `general.pressure.release` | PressureFx.start (T7: чужой банкай рядом; новый звук, раньше был без звука) | низкий давящий гул реяцу и тяжёлые удары сердца |  | 2.0-4.0 | oneshot | - (без файла тихо) | нужен файл | SFX | `anime/general/pressure_release_1.ogg` |

## Общие: HUD, рейдж, мана, разрушение блоков

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `general.bankai.ready` | HudModel.update (камень банкая загорелся) | сигнал готовности: чистый звон |  | 0.3-0.8 | oneshot | block.amethyst_block.chime | нужен файл | UI | `anime/general/bankai_ready_1.ogg` |
| `general.bankai.timer_tick` | HudModel.update (последние секунды банкая) | тиканье/удар колокольчика |  | 0.1-0.3 | oneshot | block.note_block.bell | нужен файл | UI | `anime/general/bankai_timer_tick_1.ogg` |
| `general.ability.ready` | HudModel.pingSlot (способность снова готова) | короткая нота готовности |  | 0.1-0.4 | oneshot | block.note_block.chime | нужен файл | UI | `anime/general/ability_ready_1.ogg` |
| `general.reiatsu.empty` | HudModel.onResult (не хватает реяцу) | глухой отказ: реяцу не хватает |  | 0.2-0.5 | oneshot | block.note_block.bass | нужен файл | UI | `anime/general/reiatsu_empty_1.ogg` |
| `general.rage.empty` | HudModel.onResult (не хватает рейджа) | глухой отказ: ярости не хватает |  | 0.2-0.5 | oneshot | block.note_block.bass | нужен файл | UI | `anime/general/rage_empty_1.ogg` |
| `general.ability.cooldown` | HudModel.onResult (способность на откате) | сухой щелчок |  | 0.05-0.2 | oneshot | ui.button.click | нужен файл | UI | `anime/general/ability_cooldown_1.ogg` |
| `general.rage.gain` | не подключено (рейдж растёт) | нарастающая дрожь реяцу |  | 0.5-1.5 | oneshot | - | резерв | UI | `anime/general/rage_gain_1.ogg` |
| `general.reiatsu.restored` | не подключено (мана восстановилась) | вдох/затишье, реяцу снова полон |  | 0.5-1.5 | oneshot | - | резерв | UI | `anime/general/reiatsu_restored_1.ogg` |
| `general.terrain.break` | не подключено (T9: разрушение блоков; идёт ванильным мировым событием 2001) | гул и осыпь камня под давлением реяцу |  | 0.5-1.5 | oneshot | (ванильный звук материала блока) | резерв | SFX | `anime/general/terrain_break_1.ogg` |

## Интерфейс (UI, аниме-звук не нужен)

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `ui.voice_toast` | HudModel.updateVoice (голосовая команда распознана) | - |  | 0.05-0.2 | oneshot | ui.toast.in | нужен файл | UI (аниме не нужно) | `anime/ui/voice_toast_1.ogg` |
| `ui.deny.shikai_time` | HudModel.onResult (банкай рано: минимум времени в шикае) | - |  | 0.2-0.5 | oneshot | block.note_block.bass | нужен файл | UI (аниме не нужно) | `anime/ui/deny_shikai_time_1.ogg` |
| `ui.deny.blocked` | HudModel.onResult (заблокировано) | - |  | 0.2-0.5 | oneshot | block.note_block.bass | нужен файл | UI (аниме не нужно) | `anime/ui/deny_blocked_1.ogg` |
| `ui.deny.state` | HudModel.onResult (не то состояние) | - |  | 0.2-0.5 | oneshot | block.note_block.bass | нужен файл | UI (аниме не нужно) | `anime/ui/deny_state_1.ogg` |
| `ui.deny.item` | HudModel.onResult (нет меча в руке) | - |  | 0.2-0.5 | oneshot | block.note_block.bass | нужен файл | UI (аниме не нужно) | `anime/ui/deny_item_1.ogg` |
| `ui.action_denied` | ClientNet.onResult (текстовый отказ) | - |  | 0.2-0.5 | oneshot | block.note_block.bass | нужен файл | UI (аниме не нужно) | `anime/ui/action_denied_1.ogg` |
| `ui.book_page` | SpellbookScreen (перелистывание книги) | - |  | 0.1-0.4 | oneshot | item.book.page_turn | нужен файл | UI (аниме не нужно) | `anime/ui/book_page_1.ogg` |

## Бьякуя, шикай (Senbonzakura)

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `byakuya.shikai.pink_hold` | FxEvents.onEffectEvent (id 3: клинок розовеет перед роспуском) | тихий звенящий резонанс клинка, лепестки готовы выйти |  | 0.3-0.6 | oneshot | block.amethyst_block.resonate | нужен файл | SFX | `anime/byakuya/shikai_pink_hold_1.ogg` |
| `byakuya.shikai.release_flash` | ReleaseFx.play (id 3) | вспышка реяцу и протяжный звон при «Senbonzakura» |  | 0.8-1.5 | oneshot | block.beacon.activate + entity.illusioner.cast_spell | нужен файл | SFX | `anime/byakuya/shikai_release_flash_1.ogg` |
| `byakuya.shikai.release_blast` | ReleaseFx.play (id 3) | волна реяцу от груди: короткий низкий хлопок и свист |  | 0.5-1.0 | oneshot | entity.generic.explode + entity.player.attack.sweep | нужен файл | SFX | `anime/byakuya/shikai_release_blast_1.ogg` |
| `byakuya.shikai.swarm_release` | SwarmFx.onRelease | клинок распадается на лепестки: нарастающий шелест |  | 1.0-2.0 | oneshot | block.pink_petals.break | нужен файл | SFX | `anime/byakuya/shikai_swarm_release_1.ogg` |
| `byakuya.shikai.swarm_flutter` | SwarmFx.onRelease | крылатый шорох роя вокруг владельца |  | 0.5-1.2 | oneshot | entity.phantom.flap | нужен файл | SFX | `anime/byakuya/shikai_swarm_flutter_1.ogg` |
| `byakuya.shikai.swarm_hum` | SwarmFx.frame / SwarmFx.tick (рой держится) | ровный шелест лепестков вокруг Бьякуи |  | 2.0-4.0 | loop | block.pink_petals.step | нужен файл | SFX | `anime/byakuya/shikai_swarm_hum_1.ogg` |
| `byakuya.shikai.attack_start` | SwarmFx.onAttack (режим атаки: бросок роя) | рой срывается с места: свист ветра |  | 0.5-1.0 | oneshot | entity.player.attack.sweep + entity.breeze.wind_burst + entity.phantom.swoop | нужен файл | SFX | `anime/byakuya/shikai_attack_start_1.ogg` |
| `byakuya.shikai.attack_hit` | SwarmFx.onAttack (серия попаданий) | серия частых порезов лепестками |  | 0.2-0.5 | oneshot | entity.player.attack.crit | нужен файл | SFX | `anime/byakuya/shikai_attack_hit_1.ogg` |
| `byakuya.shikai.attack_petals` | SwarmFx.onAttack (шлейф лепестков) | осыпающиеся лепестки после атаки |  | 0.3-0.8 | oneshot | block.pink_petals.break / block.pink_petals.step | нужен файл | SFX | `anime/byakuya/shikai_attack_petals_1.ogg` |
| `byakuya.shikai.barrier_start` | SwarmFx.onBarrier (режим барьера) | лепестки смыкаются в стену: мягкий звон и шелест |  | 0.6-1.2 | oneshot | block.beacon.power_select + item.shield.block + block.amethyst_block.resonate | нужен файл | SFX | `anime/byakuya/shikai_barrier_start_1.ogg` |
| `byakuya.shikai.barrier_end` | SwarmFx.onBarrier (барьер распался) | стена разлетается лепестками |  | 0.4-1.0 | oneshot | block.pink_petals.step | нужен файл | SFX | `anime/byakuya/shikai_barrier_end_1.ogg` |
| `byakuya.shikai.barrier_hit` | SwarmFx.barrierHit | удар по стене лепестков: глухой звон |  | 0.2-0.5 | oneshot | item.shield.block + block.pink_petals.break | нужен файл | SFX | `anime/byakuya/shikai_barrier_hit_1.ogg` |
| `byakuya.shikai.cage_cast` | CageFx.play (начало: лепестки летят в область) | лепестки стягиваются в сферу клетки |  | 0.6-1.2 | oneshot | block.pink_petals.break + block.amethyst_block.resonate | нужен файл | SFX | `anime/byakuya/shikai_cage_cast_1.ogg` |
| `byakuya.shikai.cage_telegraph` | CageFx.telegraph | метка центра клетки: короткий кристальный тон |  | 0.2-0.5 | oneshot | block.amethyst_cluster.place | нужен файл | SFX | `anime/byakuya/shikai_cage_telegraph_1.ogg` |
| `byakuya.shikai.cage_warn` | CageFx.warn (предупреждение перед импульсом) | нарастающий звон перед ударом внутри клетки |  | 0.2-0.5 | oneshot | block.amethyst_cluster.hit + block.beacon.power_select | нужен файл | SFX | `anime/byakuya/shikai_cage_warn_1.ogg` |
| `byakuya.shikai.cage_pulse` | CageFx.pulse (импульс внутри клетки) | удар лепестковой сферы: низкий бум и звон |  | 0.5-1.2 | oneshot | entity.generic.explode + entity.firework_rocket.blast + block.amethyst_block.chime + block.pink_petals.break | нужен файл | SFX | `anime/byakuya/shikai_cage_pulse_1.ogg` |
| `byakuya.shikai.cage_end` | CageFx.play (клетка раскрылась) | оболочка раскрывается, лепестки медленно улетают |  | 0.6-1.2 | oneshot | block.pink_petals.break + block.amethyst_block.break | нужен файл | SFX | `anime/byakuya/shikai_cage_end_1.ogg` |

## Бьякуя, банкай (Senbonzakura Kageyoshi)

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `byakuya.bankai.aura` | AuraFx.sound (владелец в банкае) | далёкий ровный пульс реяцу |  | 1.0-2.0 | loop | entity.warden.heartbeat | нужен файл | SFX | `anime/byakuya/bankai_aura_1.ogg` |
| `byakuya.bankai.sword_drop` | RowsFx.onRelease; ReleaseFx.play (резервная ветка) | меч выпускается из руки и падает вниз |  | 0.4-0.9 | oneshot | item.trident.throw / item.trident.return | нужен файл | SFX | `anime/byakuya/bankai_sword_drop_1.ogg` |
| `byakuya.bankai.sword_hang` | RowsFx.onRelease (конец висения меча) | тонкий звон перед касанием земли |  | 0.2-0.5 | oneshot | block.amethyst_block.chime | нужен файл | SFX | `anime/byakuya/bankai_sword_hang_1.ogg` |
| `byakuya.bankai.sword_enter` | RowsFx.onRelease (меч входит в землю, как в воду) | клинок уходит в землю, круги расходятся по поверхности |  | 0.6-1.2 | oneshot | item.trident.hit_ground + entity.generic.splash + block.bubble_column.whirlpool_inside | нужен файл | SFX | `anime/byakuya/bankai_sword_enter_1.ogg` |
| `byakuya.bankai.sword_ring` | RowsFx.onRelease (кольцо после касания) | колокольный отзвук после погружения клинка |  | 0.6-1.2 | oneshot | block.bell.use | нужен файл | SFX | `anime/byakuya/bankai_sword_ring_1.ogg` |
| `byakuya.bankai.night` | RowsFx.onRelease (мир темнеет) | всё затихает, низкий гул наползает |  | 1.5-3.0 | oneshot | ambient.cave + block.respawn_anchor.charge | нужен файл | SFX | `anime/byakuya/bankai_night_1.ogg` |
| `byakuya.bankai.heartbeat` | RowsFx.onRelease / RowsFx.frame (удары сердца) | тяжёлые редкие удары сердца в тишине |  | 0.6-1.2 | loop | entity.warden.heartbeat | нужен файл | SFX | `anime/byakuya/bankai_heartbeat_1.ogg` |
| `byakuya.bankai.fault` | RowsFx.onRelease (трещины под землёй) | глубокий гул разломов под ногами |  | 1.0-2.0 | oneshot | entity.warden.emerge | нужен файл | SFX | `anime/byakuya/bankai_fault_1.ogg` |
| `byakuya.bankai.eruption` | RowsFx.onRelease (клинки поднимаются из земли) | масса клинков вырастает из земли: тяжёлый грохот и металл |  | 1.5-3.0 | oneshot | item.mace.smash_ground_heavy + entity.ender_dragon.growl | нужен файл | SFX | `anime/byakuya/bankai_eruption_1.ogg` |
| `byakuya.bankai.rows_done` | RowsFx.onRelease (ряды сомкнулись) | ряды замерли, долгий звенящий отзвук |  | 1.0-2.5 | oneshot | block.beacon.ambient + block.bell.resonate | нужен файл | SFX | `anime/byakuya/bankai_rows_done_1.ogg` |
| `byakuya.bankai.rows_hum` | RowsFx.frame (ряды держатся) | ровный гул рядов клинков |  | 3.0-6.0 | loop | block.beacon.ambient | нужен файл | SFX | `anime/byakuya/bankai_rows_hum_1.ogg` |
| `byakuya.bankai.rows_dust` | RowsFx.burst (осыпь у основания клинков) | тихая каменная крошка у основания рядов |  | 0.1-0.3 | oneshot | block.pointed_dripstone.land | нужен файл | SFX | `anime/byakuya/bankai_rows_dust_1.ogg` |
| `byakuya.bankai.scatter_command` | StormFx.schedule (команда рассеивания) | клинки вибрируют и звенят перед рывком |  | 0.5-1.0 | oneshot | block.amethyst_block.resonate + entity.illusioner.prepare_mirror | нужен файл | SFX | `anime/byakuya/bankai_scatter_command_1.ogg` |
| `byakuya.bankai.scatter_dissolve` | StormFx.schedule (клинки распадаются в лепестки) | клинки рассыпаются в лепестки с шипением |  | 0.5-1.2 | oneshot | entity.phantom.flap + block.amethyst_block.break | нужен файл | SFX | `anime/byakuya/bankai_scatter_dissolve_1.ogg` |
| `byakuya.bankai.scatter_petals` | StormFx.schedule (всплески лепестков) | россыпь лепестков, мелкие шорохи |  | 0.2-0.5 | oneshot | block.pink_petals.break | нужен файл | SFX | `anime/byakuya/bankai_scatter_petals_1.ogg` |
| `byakuya.bankai.scatter_storm` | StormFx.schedule (шторм из лепестков) | ревущий вихрь лепестков |  | 2.0-4.0 | loop | item.elytra.flying + entity.breeze.whirl | нужен файл | SFX | `anime/byakuya/bankai_scatter_storm_1.ogg` |
| `byakuya.bankai.scatter_attack` | StormFx.schedule (поток отрывается к цели) | вихрь рвётся вперёд, свист и удар воздуха |  | 0.6-1.2 | oneshot | entity.breeze.wind_burst + item.trident.riptide_3 + entity.wind_charge.wind_burst | нужен файл | SFX | `anime/byakuya/bankai_scatter_attack_1.ogg` |
| `byakuya.bankai.scatter_envelop` | StormFx.schedule (сфера смыкается: порезы) | частые рубящие шорохи вокруг цели |  | 0.15-0.4 | oneshot | entity.player.attack.sweep | нужен файл | SFX | `anime/byakuya/bankai_scatter_envelop_1.ogg` |
| `byakuya.bankai.scatter_envelop_petals` | StormFx.schedule (лепестки вокруг цели) | лепестки шуршат вокруг цели |  | 0.2-0.5 | oneshot | block.pink_petals.break | нужен файл | SFX | `anime/byakuya/bankai_scatter_envelop_petals_1.ogg` |
| `byakuya.bankai.scatter_return` | StormFx.schedule (возврат лепестков) | лепестки возвращаются, мягкий шелест |  | 0.3-0.8 | oneshot | block.pink_petals.step | нужен файл | SFX | `anime/byakuya/bankai_scatter_return_1.ogg` |
| `byakuya.bankai.scatter_reform` | StormFx.schedule (клинки собираются) | клинки вновь складываются в ряды: звон металла |  | 0.4-0.9 | oneshot | block.amethyst_block.chime | нужен файл | SFX | `anime/byakuya/bankai_scatter_reform_1.ogg` |
| `byakuya.bankai.scatter_reform_end` | StormFx.schedule (ряды готовы) | завершающий звон после сборки |  | 0.4-0.9 | oneshot | block.beacon.power_select | нужен файл | SFX | `anime/byakuya/bankai_scatter_reform_end_1.ogg` |
| `byakuya.bankai.scatter_impact` | StormFx.impact (взрыв рассеивания) | удар вихря в точку: тяжёлый взрыв |  | 1.0-2.0 | oneshot | entity.generic.explode + item.mace.smash_ground_heavy | нужен файл | SFX | `anime/byakuya/bankai_scatter_impact_1.ogg` |
| `byakuya.bankai.scatter_impact_petals` | StormFx.impact (лепестки после взрыва) | россыпь лепестков после удара |  | 0.3-0.8 | oneshot | block.pink_petals.break | нужен файл | SFX | `anime/byakuya/bankai_scatter_impact_petals_1.ogg` |
| `byakuya.bankai.hakuteiken_condense` | HakuteikenFx.schedule (сгущение света в руке) | свет и лепестки стягиваются в ладонь: нарастающий звон |  | 1.0-2.0 | oneshot | block.beacon.activate + entity.illusioner.cast_spell + block.conduit.activate | нужен файл | SFX | `anime/byakuya/bankai_hakuteiken_condense_1.ogg` |
| `byakuya.bankai.hakuteiken_wings` | HakuteikenFx.schedule (крылья и нимб) | раскрытие крыльев: хлопок воздуха и колокол |  | 0.8-1.5 | oneshot | entity.ender_dragon.flap + block.bell.use + block.end_portal_frame.fill | нужен файл | SFX | `anime/byakuya/bankai_hakuteiken_wings_1.ogg` |
| `byakuya.bankai.hakuteiken_blade` | HakuteikenFx.schedule (клинок формируется) | световой клинок вытягивается: чистый нарастающий тон |  | 0.8-1.5 | oneshot | block.amethyst_block.resonate + block.beacon.power_select | нужен файл | SFX | `anime/byakuya/bankai_hakuteiken_blade_1.ogg` |
| `byakuya.bankai.hakuteiken_dissolve` | HakuteikenFx.schedule (крылья рассыпаются) | лёгкий шорох крыльев, рассыпающихся в свет |  | 0.4-1.0 | oneshot | entity.phantom.flap | нужен файл | SFX | `anime/byakuya/bankai_hakuteiken_dissolve_1.ogg` |
| `byakuya.bankai.hakuteiken_reform` | HakuteikenFx.schedule (ряды собираются) | звон возвращающихся клинков |  | 0.4-0.9 | oneshot | block.amethyst_block.chime | нужен файл | SFX | `anime/byakuya/bankai_hakuteiken_reform_1.ogg` |
| `byakuya.bankai.hakuteiken_strike` | HakuteikenFx.strike (выстрел) | мощный световой выстрел: сначала хлопок, потом свист |  | 0.8-1.8 | oneshot | entity.warden.sonic_boom + item.mace.smash_air + item.trident.thunder | нужен файл | SFX | `anime/byakuya/bankai_hakuteiken_strike_1.ogg` |
| `byakuya.bankai.hakuteiken_burst` | HakuteikenFx.burst (взрыв в конце луча) | взрыв света с осколками, затем гулкий отзвук |  | 1.0-2.5 | oneshot | entity.generic.explode + block.glass.break + item.mace.smash_ground_heavy | нужен файл | SFX | `anime/byakuya/bankai_hakuteiken_burst_1.ogg` |
| `byakuya.bankai.senkei` | не подключено (способность выключена) | белый ослепительный разрез/вспышка |  | 0.8-2.0 | oneshot | - | резерв | SFX | `anime/byakuya/bankai_senkei_1.ogg` |

## Рукия, шикай (Sode no Shirayuki)

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `rukia.shikai.release_flash` | ReleaseFx.play (id 1) | вспышка реяцу и чистый холодный звон при «Mae, Sode no Shirayuki» |  | 0.8-1.5 | oneshot | block.beacon.activate | нужен файл | SFX | `anime/rukia/shikai_release_flash_1.ogg` |
| `rukia.shikai.release_blast` | ReleaseFx.play (id 1) | волна реяцу от груди |  | 0.5-1.0 | oneshot | entity.generic.explode + entity.player.attack.sweep | нужен файл | SFX | `anime/rukia/shikai_release_blast_1.ogg` |
| `rukia.shikai.release_ring` | ReleaseFx.play (id 1, 2: световое кольцо вокруг клинка) | тонкий звенящий перелив вдоль клинка |  | 0.3-0.8 | oneshot | block.amethyst_block.chime | нужен файл | SFX | `anime/rukia/shikai_release_ring_1.ogg` |
| `rukia.shikai.aura` | AuraFx.sound (Рукия в шикае) | лёгкий скрип снега под ногами |  | 0.2-0.6 | loop | block.powder_snow.step | нужен файл | SFX | `anime/rukia/shikai_aura_1.ogg` |
| `rukia.shikai.tsukishiro_windup` | RukiaShikaiFx.tsukishiro (замах, вихрь искр) | быстрый кружащий взмах белого клинка |  | 0.2-0.5 | oneshot | entity.player.attack.sweep | нужен файл | SFX | `anime/rukia/shikai_tsukishiro_windup_1.ogg` |
| `rukia.shikai.tsukishiro_pillar` | RukiaShikaiFx.tsukishiro (столб света) | из круга вырастает ледяной столб света, гром льда |  | 0.8-1.8 | oneshot | block.amethyst_block.chime + item.trident.thunder + block.beacon.power_select | нужен файл | SFX | `anime/rukia/shikai_tsukishiro_pillar_1.ogg` |
| `rukia.shikai.tsukishiro_crust` | RukiaShikaiFx.tsukishiro (ледяная корка по краю круга) | лёд схватывается по кругу: треск и звон |  | 0.6-1.4 | oneshot | entity.player.hurt_freeze + block.amethyst_cluster.place | нужен файл | SFX | `anime/rukia/shikai_tsukishiro_crust_1.ogg` |
| `rukia.shikai.tsukishiro_shatter` | RukiaShikaiFx.tsukishiro (лёд разлетается) | столб льда рассыпается стеклянным дождём |  | 0.8-2.0 | oneshot | block.glass.break | нужен файл | SFX | `anime/rukia/shikai_tsukishiro_shatter_1.ogg` |
| `rukia.shikai.hakuren_start` | RukiaShikaiFx.hakuren (удар в землю под ней) | лёд пробивает землю перед выбросом |  | 0.3-0.8 | oneshot | block.glass.break | нужен файл | SFX | `anime/rukia/shikai_hakuren_start_1.ogg` |
| `rukia.shikai.hakuren_spikes` | RukiaShikaiFx.hakuren (кристаллы поднимаются) | ряд ледяных шипов вырастает из земли |  | 0.2-0.5 | oneshot | block.amethyst_cluster.break | нужен файл | SFX | `anime/rukia/shikai_hakuren_spikes_1.ogg` |
| `rukia.shikai.hakuren_flare` | RukiaShikaiFx.hakuren (вспышка перед волной) | нарастающий светящийся тон |  | 0.3-0.8 | oneshot | block.beacon.power_select | нужен файл | SFX | `anime/rukia/shikai_hakuren_flare_1.ogg` |
| `rukia.shikai.hakuren_trail` | RukiaShikaiFx.hakuren (след кристаллов) | кристаллы вырастают вдоль пути волны |  | 0.15-0.4 | oneshot | block.amethyst_cluster.place | нужен файл | SFX | `anime/rukia/shikai_hakuren_trail_1.ogg` |
| `rukia.shikai.hakuren_wave` | RukiaShikaiFx.hakuren (фронт волны) | ледяная волна идёт по земле: порыв ветра и снега |  | 0.8-1.8 | oneshot | entity.breeze.wind_burst + block.powder_snow.break | нужен файл | SFX | `anime/rukia/shikai_hakuren_wave_1.ogg` |
| `rukia.shikai.hakuren_freeze` | RukiaShikaiFx.hakuren (заморозка цели) | цель сковывает мороз: хруст льда |  | 0.4-0.9 | oneshot | entity.player.hurt_freeze | нужен файл | SFX | `anime/rukia/shikai_hakuren_freeze_1.ogg` |
| `rukia.shikai.hakuren_shatter` | RukiaShikaiFx.hakuren (кристаллы трескаются) | звон разбивающихся кристаллов вдоль пути |  | 0.2-0.5 | oneshot | block.glass.break | нужен файл | SFX | `anime/rukia/shikai_hakuren_shatter_1.ogg` |
| `rukia.shikai.shirafune_cast` | ShirafuneCast.start (замах Ширафунэ) | собранная концентрация, тонкий звон клинка |  | 0.5-1.2 | oneshot | block.amethyst_block.resonate + block.amethyst_cluster.hit | нужен файл | SFX | `anime/rukia/shikai_shirafune_cast_1.ogg` |
| `rukia.shikai.shirafune_charge` | RukiaShikaiFx.shirafune (нарастание) | клинок звенит от холода перед выпадом |  | 0.4-0.9 | oneshot | block.amethyst_block.resonate | нужен файл | SFX | `anime/rukia/shikai_shirafune_charge_1.ogg` |
| `rukia.shikai.shirafune_throw` | RukiaShikaiFx.shirafune (выпад) | быстрый выпад клинка, свист |  | 0.2-0.5 | oneshot | item.trident.throw | нужен файл | SFX | `anime/rukia/shikai_shirafune_throw_1.ogg` |
| `rukia.shikai.shirafune_hit` | RukiaShikaiFx.shirafune (попадание) | укол и треск льда на цели |  | 0.3-0.7 | oneshot | entity.player.attack.crit + block.glass.break | нужен файл | SFX | `anime/rukia/shikai_shirafune_hit_1.ogg` |
| `rukia.shikai.shirafune_chill` | RukiaShikaiFx.shirafune (цель замерзает) | холод расходится по цели |  | 0.4-0.9 | oneshot | entity.player.hurt_freeze | нужен файл | SFX | `anime/rukia/shikai_shirafune_chill_1.ogg` |
| `rukia.shikai.shirafune_retract` | RukiaShikaiFx.shirafune (клинок убирается) | кристалл на острие рассыпается |  | 0.2-0.5 | oneshot | block.glass.break | нужен файл | SFX | `anime/rukia/shikai_shirafune_retract_1.ogg` |

## Рукия, банкай (Hakka no Togame)

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `rukia.bankai.release_flash` | ReleaseFx.play (id 2) | мощная вспышка белого холода при «Bankai: Hakka no Togame» |  | 0.8-1.5 | oneshot | block.beacon.activate | нужен файл | SFX | `anime/rukia/bankai_release_flash_1.ogg` |
| `rukia.bankai.release_blast` | ReleaseFx.play (id 2) | волна реяцу от груди, сильнее шикая |  | 0.6-1.2 | oneshot | entity.generic.explode + entity.player.attack.sweep | нужен файл | SFX | `anime/rukia/bankai_release_blast_1.ogg` |
| `rukia.bankai.release_gather` | RukiaBankaiFx.release (реяцу собирается) | холодный нарастающий тон |  | 0.6-1.2 | oneshot | block.beacon.power_select + block.amethyst_block.resonate | нужен файл | SFX | `anime/rukia/bankai_release_gather_1.ogg` |
| `rukia.bankai.release_burst` | RukiaBankaiFx.release (вспышка ледяного цветка) | белый удар света и стеклянный треск |  | 0.8-1.8 | oneshot | block.end_portal.spawn + entity.warden.sonic_boom + block.glass.break | нужен файл | SFX | `anime/rukia/bankai_release_burst_1.ogg` |
| `rukia.bankai.release_flower` | RukiaBankaiFx.release (снежный вихрь, цветок раскрывается) | порыв снега и шелест тонкого льда |  | 0.8-1.8 | oneshot | block.powder_snow.break + item.elytra.flying | нужен файл | SFX | `anime/rukia/bankai_release_flower_1.ogg` |
| `rukia.bankai.release_freeze` | RukiaBankaiFx.release (земля замерзает) | лёд растекается по земле, кристаллы вырастают |  | 1.0-2.5 | oneshot | block.amethyst_block.chime + block.amethyst_cluster.place | нужен файл | SFX | `anime/rukia/bankai_release_freeze_1.ogg` |
| `rukia.bankai.release_wings` | RukiaBankaiFx.release (рукава-ленты расправляются) | лёгкий шорох ткани и ледяной ветер |  | 0.5-1.2 | oneshot | item.armor.equip_elytra + entity.phantom.flap | нужен файл | SFX | `anime/rukia/bankai_release_wings_1.ogg` |
| `rukia.bankai.release_ambient` | RukiaBankaiFx.release (звон над территорией) | тихий ровный гул холода |  | 1.0-2.0 | oneshot | block.beacon.ambient | нужен файл | SFX | `anime/rukia/bankai_release_ambient_1.ogg` |
| `rukia.bankai.release_settle` | RukiaBankaiFx.release (всё успокаивается) | затихающий кристальный тон |  | 0.8-1.8 | oneshot | block.amethyst_block.resonate | нужен файл | SFX | `anime/rukia/bankai_release_settle_1.ogg` |
| `rukia.bankai.aura` | AuraFx.sound (владелец в банкае) | холодный гул и редкие шаги по снегу |  | 1.0-3.0 | loop | block.amethyst_block.resonate / block.powder_snow.step | нужен файл | SFX | `anime/rukia/bankai_aura_1.ogg` |
| `rukia.bankai.hum` | RukiaBankaiClient.tick (гул холода каждые 4 с) | ровный холодный гул вокруг Рукии |  | 2.0-4.0 | loop | block.amethyst_block.resonate | нужен файл | SFX | `anime/rukia/bankai_hum_1.ogg` |
| `rukia.bankai.snow_step` | RukiaBankaiClient.tick (снежные шаги) | мягкий шаг по снегу |  | 0.15-0.4 | oneshot | block.powder_snow.step | нужен файл | SFX | `anime/rukia/bankai_snow_step_1.ogg` |
| `rukia.bankai.glint` | RukiaBankaiClient.glints (вспышка на кристалле) | тонкий звон кристалла |  | 0.2-0.5 | oneshot | block.amethyst_block.chime | нужен файл | SFX | `anime/rukia/bankai_glint_1.ogg` |
| `rukia.bankai.mob_frost` | RukiaBankaiClient.mobs (моб на льду) | треск мороза у ног существа |  | 0.3-0.7 | oneshot | entity.player.hurt_freeze | нужен файл | SFX | `anime/rukia/bankai_mob_frost_1.ogg` |
| `rukia.bankai.field_front` | FrostFieldFx.field (фронт заморозки) | кольцо мороза бежит по земле: треск и холод |  | 1.0-2.0 | oneshot | block.glass.break + block.amethyst_block.resonate + block.powder_snow.break | нужен файл | SFX | `anime/rukia/bankai_field_front_1.ogg` |
| `rukia.bankai.field_chime` | FrostFieldFx.field (конец фронта) | светлый ледяной звон |  | 0.4-0.9 | oneshot | block.amethyst_block.chime | нужен файл | SFX | `anime/rukia/bankai_field_chime_1.ogg` |
| `rukia.bankai.field_crackle` | FrostFieldFx.outward (потрескивание льда) | тихое потрескивание схватывающегося льда |  | 0.1-0.3 | oneshot | block.glass.* / block.powder_snow.* (CRACKS) | нужен файл | SFX | `anime/rukia/bankai_field_crackle_1.ogg` |
| `rukia.bankai.field_crystal` | FrostFieldFx.rareCrystal | одинокий кристалл вырастает |  | 0.2-0.5 | oneshot | block.amethyst_cluster.place | нужен файл | SFX | `anime/rukia/bankai_field_crystal_1.ogg` |
| `rukia.bankai.field_thaw` | FrostFieldFx.thaw (лёд тает) | лёд ломается и отпускает землю |  | 0.6-1.5 | oneshot | block.amethyst_block.resonate + block.glass.break | нужен файл | SFX | `anime/rukia/bankai_field_thaw_1.ogg` |
| `rukia.bankai.field_thaw_crackle` | FrostFieldFx.inward (оттаивание вглубь) | трещины льда и осыпь снега |  | 0.1-0.3 | oneshot | block.powder_snow.break / block.glass.break | нужен файл | SFX | `anime/rukia/bankai_field_thaw_crackle_1.ogg` |
| `rukia.bankai.encase` | RukiaEntityFx.onEntityFx (цель в ледяной оболочке) | цель мгновенно скована льдом |  | 0.3-0.7 | oneshot | block.amethyst_cluster.place | нужен файл | SFX | `anime/rukia/bankai_encase_1.ogg` |
| `rukia.bankai.encase_break` | RukiaEntityFx.hit (оболочка разбита) | лёд раскалывается и осыпается |  | 0.4-0.9 | oneshot | block.glass.break | нужен файл | SFX | `anime/rukia/bankai_encase_break_1.ogg` |
| `rukia.bankai.ice_wall_gather` | IceWallFx.play (подготовка стены) | реяцу холодно сжимается перед взмахом |  | 0.4-0.9 | oneshot | block.beacon.power_select | нужен файл | SFX | `anime/rukia/bankai_ice_wall_gather_1.ogg` |
| `rukia.bankai.ice_wall_release` | IceWallFx.play (взмах) | быстрый взмах клинка, свист |  | 0.2-0.5 | oneshot | entity.player.attack.sweep | нужен файл | SFX | `anime/rukia/bankai_ice_wall_release_1.ogg` |
| `rukia.bankai.ice_wall_rise` | IceWallFx.play (иней там, где встаёт стена) | иней вспыхивает кристаллами у основания |  | 0.2-0.5 | oneshot | block.amethyst_cluster.place | нужен файл | SFX | `anime/rukia/bankai_ice_wall_rise_1.ogg` |
| `rukia.bankai.ice_wall_start` | IceWall.cast (сервер, начало роста) | поднимается ледяная стена: низкий звон и рост льда |  | 0.8-1.6 | oneshot | block.amethyst_block.chime (громкость 1.2, тон 0.6) | нужен файл | SFX | `anime/rukia/bankai_ice_wall_start_1.ogg` |
| `rukia.bankai.ice_wall_layer` | IceWall.placeLayer (сервер, каждый слой) | очередной слой льда схватывается: тихий кристальный стук |  | 0.15-0.4 | oneshot | block.amethyst_cluster.place (0.7, тон 0.8 + 0.12 на слой) | нужен файл | SFX | `anime/rukia/bankai_ice_wall_layer_1.ogg` |
| `rukia.bankai.ice_wall_hit` | IceWall.absorbNear (сервер, стена приняла удар) | удар по льду: глухой звонкий стук |  | 0.2-0.5 | oneshot | block.glass.hit | нужен файл | SFX | `anime/rukia/bankai_ice_wall_hit_1.ogg` |
| `rukia.bankai.ice_wall_break` | IceWall.release (сервер, стена рухнула) | ледяная стена рассыпается стеклянным обвалом |  | 0.8-2.0 | oneshot | block.glass.break (1.4, тон 0.7) | нужен файл | SFX | `anime/rukia/bankai_ice_wall_break_1.ogg` |

## Gin, шикай (Shinso)

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `gin.shikai.release` | GinFx.release | тонкий звон Синсо при «Shoot to Kill» |  | 0.5-1.0 | oneshot | item.trident.return | нужен файл | SFX | `anime/gin/shikai_release_1.ogg` |
| `gin.shikai.ikorose_cast` | GinFx.cast (id 62) | быстрый выпад клинка |  | 0.2-0.5 | oneshot | item.trident.throw | нужен файл | SFX | `anime/gin/shikai_ikorose_cast_1.ogg` |
| `gin.shikai.hyapponzashi_windup` | GinFx.cast (id 63, замах 1 с) | клинок звенит перед серией выпадов |  | 0.6-1.2 | oneshot | block.beacon.activate | нужен файл | SFX | `anime/gin/shikai_hyapponzashi_windup_1.ogg` |
| `gin.shikai.snake_hook_cast` | GinFx.cast (id 64) | рывок клинка вперёд |  | 0.2-0.5 | oneshot | item.trident.throw | нужен файл | SFX | `anime/gin/shikai_snake_hook_cast_1.ogg` |
| `gin.shikai.snake_hook_throw` | GinFx.hook (бросок) | клинок со свистом выстреливает на цепи |  | 0.2-0.5 | oneshot | entity.fishing_bobber.throw | нужен файл | SFX | `anime/gin/shikai_snake_hook_throw_1.ogg` |
| `gin.shikai.snake_hook_hit` | GinFx.hook (зацеп) | металлический стук в точке зацепа |  | 0.2-0.5 | oneshot | block.chain.hit | нужен файл | SFX | `anime/gin/shikai_snake_hook_hit_1.ogg` |
| `gin.shikai.strike` | GinFx.strike (выстрел клинка) | резкий щелчок удлиняющегося клинка |  | 0.15-0.4 | oneshot | entity.player.attack.sweep | нужен файл | SFX | `anime/gin/shikai_strike_1.ogg` |
| `gin.shikai.strike_hit` | GinFx.strike (попадание) | укол на цели |  | 0.15-0.4 | oneshot | entity.player.attack.crit | нужен файл | SFX | `anime/gin/shikai_strike_hit_1.ogg` |

## Gin, банкай (Kamishini no Yari)

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `gin.bankai.release` | GinFx.release (банкай) | низкий звон «Kamishini no Yari» |  | 0.8-1.8 | oneshot | item.trident.return | нужен файл | SFX | `anime/gin/bankai_release_1.ogg` |
| `gin.bankai.butou_renjin_cast` | GinFx.cast (id 65) | клинок наливается светом перед взмахом |  | 0.5-1.0 | oneshot | block.amethyst_block.resonate | нужен файл | SFX | `anime/gin/bankai_butou_renjin_cast_1.ogg` |
| `gin.bankai.butou_renjin_sweep` | GinFx.sweep | длинный свист рассекающего клинка |  | 0.3-0.8 | oneshot | entity.player.attack.sweep | нужен файл | SFX | `anime/gin/bankai_butou_renjin_sweep_1.ogg` |
| `gin.bankai.korose_cast` | GinFx.cast (id 66) | короткий звон, клинок нацелен |  | 0.2-0.5 | oneshot | item.trident.throw | нужен файл | SFX | `anime/gin/bankai_korose_cast_1.ogg` |
| `gin.bankai.korose_channel` | GinFx.channel (канал, кольцо пыли) | нарастающий напряжённый тон |  | 0.8-2.0 | oneshot | block.beacon.power_select | нужен файл | SFX | `anime/gin/bankai_korose_channel_1.ogg` |
| `gin.bankai.korose_dissolve` | GinFx.dissolve (распад) | тихий рассыпающийся хруст и шипение |  | 0.5-1.2 | oneshot | block.amethyst_cluster.break + entity.generic.extinguish_fire | нужен файл | SFX | `anime/gin/bankai_korose_dissolve_1.ogg` |
| `gin.bankai.korose_interrupt` | GinFx.interrupt | канал сорван: стеклянный звон |  | 0.2-0.5 | oneshot | block.glass.break | нужен файл | SFX | `anime/gin/bankai_korose_interrupt_1.ogg` |
| `gin.bankai.hakufuku` | GinFx.hakufuku (скрытие) | бледное мерцание, звук исчезновения |  | 0.3-0.8 | oneshot | block.amethyst_block.chime | нужен файл | SFX | `anime/gin/bankai_hakufuku_1.ogg` |

## Uryu, шикай (резерв: UryuFx пока без звуков)

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `uryu.shikai.release` | не подключено (UryuFx; шикай) | тихий гул Квинси: собирается свет лука |  | 0.5-1.2 | oneshot | - | резерв | SFX | `anime/uryu/shikai_release_1.ogg` |
| `uryu.shikai.heilig_pfeil` | не подключено (Heilig Pfeil) | тонкий звон тетивы и свист стрелы света |  | 0.3-0.8 | oneshot | - | резерв | SFX | `anime/uryu/shikai_heilig_pfeil_1.ogg` |
| `uryu.shikai.sprenger` | не подключено (Sprenger) | стрела-мина: щелчок, затем взрыв |  | 0.5-1.2 | oneshot | - | резерв | SFX | `anime/uryu/shikai_sprenger_1.ogg` |
| `uryu.shikai.hirenkyaku` | не подключено (Hirenkyaku) | быстрый рывок Квинси |  | 0.2-0.5 | oneshot | - | резерв | SFX | `anime/uryu/shikai_hirenkyaku_1.ogg` |

## Uryu, банкай (резерв: UryuFx пока без звуков)

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `uryu.bankai.release` | не подключено (UryuFx; банкай) | мощный подъём духовной силы |  | 0.8-1.8 | oneshot | - | резерв | SFX | `anime/uryu/bankai_release_1.ogg` |
| `uryu.bankai.licht_regen` | не подключено (Licht Regen) | дождь световых стрел |  | 1.0-2.5 | oneshot | - | резерв | SFX | `anime/uryu/bankai_licht_regen_1.ogg` |
| `uryu.bankai.homing_volley` | не подключено (залп самонаводящихся стрел) | множество стрел со свистом поворачивают за целью |  | 0.8-1.8 | oneshot | - | резерв | SFX | `anime/uryu/bankai_homing_volley_1.ogg` |
| `uryu.bankai.sternenstaub` | не подключено (Sternenstaub) | звёздная пыль, тонкий перелив |  | 1.0-2.5 | oneshot | - | резерв | SFX | `anime/uryu/bankai_sternenstaub_1.ogg` |

## Кидо

| id | где вызывается | что слышно в аниме (ориентир) | сцена | длина, с | тип | запасной ванильный звук | статус | риск | файл (вариант 1) |
|---|---|---|---|---|---|---|---|---|---|
| `kido.byakurai.cast` | KidoFx.byakurai (каст) | треск разряда белой молнии, громовой раскат |  | 0.3-0.8 | oneshot | item.trident.thunder (тон/громкость kido.byakurai.*) | нужен файл | SFX | `anime/kido/byakurai_cast_1.ogg` |
| `kido.byakurai.hit` | KidoFx.byakurai (попадание) | хлопок на цели |  | 0.2-0.6 | oneshot | entity.firework_rocket.blast | нужен файл | SFX | `anime/kido/byakurai_hit_1.ogg` |
| `kido.shakkaho.cast` | KidoFx.shakkaho (каст: красный шар заряжается) | рокот заряда и треск огня |  | 0.5-1.2 | oneshot | entity.blaze.shoot (kido.shakkaho.castPitch/Volume) | нужен файл | SFX | `anime/kido/shakkaho_cast_1.ogg` |
| `kido.shakkaho.flight` | KidoFx.shakkaho (шар летит) | потрескивание огня в полёте |  | 0.3-1.0 | oneshot | block.fire.ambient | нужен файл | SFX | `anime/kido/shakkaho_flight_1.ogg` |
| `kido.shakkaho.burst` | KidoFx.explode (взрыв шара) | громкий огненный взрыв |  | 0.8-2.0 | oneshot | entity.generic.explode (kido.shakkaho.burstPitch/Volume) | нужен файл | SFX | `anime/kido/shakkaho_burst_1.ogg` |
| `kido.sai.cast` | KidoFx.sai (каст бакудо) | электрический шипящий звон бакудо |  | 0.3-0.8 | oneshot | block.amethyst_block.resonate (kido.sai.pitch/volume) | нужен файл | SFX | `anime/kido/sai_cast_1.ogg` |
| `kido.sai.hit` | KidoFx.sai (цель скована) | щелчок смыкающихся оков |  | 0.2-0.5 | oneshot | entity.iron_golem.repair | нужен файл | SFX | `anime/kido/sai_hit_1.ogg` |

## Итого

Событий: 166 (подключено в коде: 149, резерв: 17). Источник сцен: ждёт выбора пользователя (вопрос 9 плана R11).

## Ориентиры по сценам (непроверенные, только чтобы знать, где искать)

Найдено обычным поиском по вики и новостным сайтам 2026-10-14, тексты не копировались. Источник сцен пользователь не выбрал, поэтому в таблицах колонка «сцена» пуста.
- Бьякуя, банкай Senbonzakura Kageyoshi: первое появление в старом ТВ-аниме, серия 52 («Renji, Oath of the Soul! Death Match with Byakuya», дуэль с Рэндзи; арка Soul Society). Источник: [страница серии](https://www.animeoshi.com/anime/bleach/episode/52). Время внутри серии не искалось.
- Рукия, банкай Hakka no Togame: первое появление в Thousand-Year Blood War (бой с Sternritter «F» As Nodt, часть «The Separation»). Номер серии источники называют по-разному (в одном «6-я серия 2-го кура»), ПРОВЕРИТЬ по каталогу серий перед использованием.
- Остальное (шикай Рукии, Gin, Uryu, кидо, шунпо, обнажение меча) не искалось: эти звуки есть во многих сериях, выбирает пользователь.
