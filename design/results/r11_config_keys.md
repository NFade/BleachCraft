# R11: ключи конфигов

Серверный файл `config/reiatsu_test.json` (версия `configVersion` = 2). Клиентский `config/reiatsu_test_client.json` (версия 2, правил миграции пока нет).
Перезагрузка серверных секций без рестарта: `/reiatsu reload` (voice и frost читаются при старте).

| Ключ | Файл | Умолчание | Диапазон | Задача |
|---|---|---|---|---|
| configVersion | server/client | 2 | целое >= 1 | T0 |
| kido.shakkaho.damage | server | 1.0 | 0..100 | T4 |
| kido.shakkaho.fireTicks | server | 15 | 0..600 | T4 |
| mech.meleeHitReiatsu | server | 5.0 | 0..100 | T2 |
| mech.parryReiatsu | server | 10.0 | 0..100 | T2 |
| mech.meleeHitMaxPerSecond | server | 15.0 | 0..200 | T2 |
| mech.meleeHitTargetGapTicks | server | 5 | 0..200 | T2 |
| mech.parryGapTicks | server | 10 | 0..200 | T2 |
| mech.abilityBlockReduction | server | 0.30 | 0..1 | R8 |
| mech.byakuyaAttackTickInterval | server | 4 | 1..200 | R8 |
| mech.barrierPushRadius / Strength / Up / IntervalTicks | server | 3.5 / 0.6 / 0.18 / 2 | 0..16 / 0..4 / 0..1 / 1..40 | R8 |
| rage.damageTenthsPerHp | server | 0 | 0..1000 (десятые очка рейджа за хп урона по тебе) | T3 |
| rage.maxGainPerHitTenths | server | 250 | 0..1000 | T3 |
| rage.dealtTenthsPerHp | server | 2 | 0..1000 | T3 |
| rage.maxDealtGainPerHitTenths | server | 25 | 0..1000 | T3 |
| rage.mobMultiplierPercent | server | 25 | 0..1000 | T3 |
| rage.targetCapPer10sTenths | server | 80 | 0..10000 | T3 |
| rage.parryGainTenths | server | 40 | 0..1000 | T3 |
| rage.parryGapTicks | server | 20 | 0..1200 | T3 |
| rage.abilityHitTenths | server | 20 | 0..1000 | T3 |
| rage.passiveTenths / passiveIntervalTicks / passiveWindowTicks | server | 0 / 10 / 120 | 0..1000 / 1..72000 / 0..72000 | T3 |
| rage.decayDelayTicks / decayIntervalTicks / decayTenths | server | 160 / 20 / 5 | 0..72000 / 1..72000 / 0..1000 | T3 |
| rage.shikaiCostTenths / bankaiCostTenths | server | 400 / 800 | 0..1000 | T3 |
| rage.bankaiMinShikaiTicks | server | 400 | 0..72000 | T3 |
| rukia.passive.enabled | server | true | true/false | T6 |
| rukia.passive.shikai.bonusPerStack / maxStacks / expireTicks | server | 0.05 / 4 / 80 | 0..1 / 0..20 / 1..72000 | T6 |
| rukia.passive.bankai.bonusPerStack / maxStacks / expireTicks | server | 0.08 / 5 / 100 | 0..1 / 0..20 / 1..72000 | T6 |
| rukia.iceWall.enabled | server | true | true/false | T5 |
| rukia.iceWall.width / height / thickness | server | 9 / 5 / 2 | 1..25 / 1..12 / 1..4 (блоков) | T5 |
| rukia.iceWall.lifeTicks / growTicks / fadeTicks | server | 200 / 10 / 10 | 20..6000 / 1..100 / 0..100 (тиков: жизнь 10 с, рост снизу вверх 0.5 с, угасание сверху) | T5 |
| rukia.iceWall.hp | server | 60.0 | 1..100000 (запас прочности всей стены; попадание способности вычитает её урон) | T5 |
| rukia.iceWall.maxBlocks | server | 160 | 8..2000 (блоков стены в журнале на игрока) | T5 |
| rukia.iceWall.searchDown / fillDown | server | 6 / 2 | 0..16 / 0..6 (поиск земли вниз от прицела; углубление колонны в яму) | T5 |
| terrain.enabled | server | true | true/false | T9 |
| terrain.ignoreMobGriefing | server | false | true/false (true: ломать и при `mobGriefing=false`) | T9 |
| terrain.drops | server | false | true/false (дропы разрушенных блоков) | T9 |
| terrain.blocksPerTick | server | 40 | 1..2000 | T9 |
| terrain.effectEveryBlocks | server | 6 | 1..1000 (один звук и частицы на столько блоков) | T9 |
| terrain.profiles.S / M / L .maxHardness | server | 0.8 / 2.0 / 5.0 | 0..100 | T9 |
| terrain.modeAttack.enabled / profile / radius / cap | server | true / S / 3.0 / 60 | bool / S,M,L,NONE / 0..16 / 0..5000 (шикай Бьякуи, режим атаки) | T9 |
| terrain.scatter.enabled / profile / craterRadius / cap | server | true / M / 5.0 / 400 | то же (банкай, Рассеивание) | T9 |
| terrain.hakuteiken.enabled / profile / tunnelRadius / craterRadius / cap | server | true / L / 1.5 / 5.0 / 700 | то же (банкай, Хакутэйкэн: туннель и кратер) | T9 |
| terrain.shakkaho.enabled / profile / radius / cap | server | false / S / 2.5 / 40 | то же (Сякухо, по умолчанию выключено) | T9 |
| hak.lineFade | fx_override.json (dev) | strike + 0.10 = 1.60 | секунды | T8 |
| hak.lineFadeSpan | fx_override.json (dev) | 0.08 | секунды | T8 |
| hak.haloFade | fx_override.json (dev) | 2.20 | секунды | T8 |
| pressure.shake | fx_override.json (dev) / клиент `pressure.shake` | 0.9 | 0..3, градусы на пульсе (тремор 0.15 пропорционален) | T7 |
| pressure.blur | fx_override.json (dev) / клиент `pressure.blur` | 1.0 | 0..3, множитель радиуса размытия (макс 2 px) | T7 |
| pressure.stripes | fx_override.json (dev) / клиент `pressure.stripes` | 1.0 | 0..3, множитель силы полос и разрыва | T7 |
| pressure.scale | fx_override.json (dev) / клиент `pressure.scale` | 1.0 | 0..3, множитель силы всего эффекта | T7 |
| pressure.enabled / vignette / grade | fx_override.json (dev) | 1 / 0.75 / 0.30 | как раньше | T7 |
| tp.swordScale | клиент `config/reiatsu_test_client.json`, секция `"tp"` (пишется при первом старте); dev: `pose_override.json` `tp` | 1.25 | 0.5..2.5 | T10 |
| tp.swordScale.<id> (`senbonzakura`, `sode_no_shirayuki`) | клиент, секция `"tp"` (необязательный, не пишется) | = tp.swordScale | 0.5..2.5 | T10 |
| tp.guard.shift | клиент, секция `"tp"` (необязательный, не пишется) / dev `pose_override.json` `tp` | [1.45, -0.35, 0.55] px (тело: +x влево от персонажа, +y вниз, +z назад) | каждая компонента ±8 | T10 |
| tp.guard.pos (dev, абсолютная точка хвата, перекрывает tsuba-якорь и shift), tp.grip.c_r / grip.c_l / sealed.c_r / saya.c (dev: убрать, иначе не масштабируются) | dev `pose_override.json` `tp` | вычисляются из масштаба | px | T10 |

Миграции (`core/config/ConfigMigrations`, версия 2): `kido.shakkaho.damage` {2.0, 4.0, 5.0} -> 1.0; `kido.shakkaho.fireTicks` 60 -> 15.
Значение 5.0 и 60 были в dev-конфигах дуэли; изменённые пользователем иные значения остаются. Копия файла: `reiatsu_test.json.bak_v1`.

Секции rage (T3) и rukia.passive (T6) пишутся в файл при первом старте с умолчаниями, раньше их там не было, миграции не нужны. Клиентский файл `config/reiatsu_test_client.json` получает секцию `"pressure": {"shake":0.9,"blur":1.0,"stripes":1.0,"scale":1.0}` (добавляется, если её нет); ключи `pressure.*` в `fx_override.json` перекрывают её. Предпросмотр: `/reiatsu_fx pressure [k] [byakuya|rukia]`.

Секции `terrain` (T9) и `rukia.iceWall` (T5) пишутся в файл при первом старте с умолчаниями (`rukia.iceWall` в тот же объект `rukia`, где лежит `passive`; чужие ключи не трогаются), миграции не нужны; перечитываются `/reiatsu reload`. Стоимость (18), откат (14 с), подготовка (14 тиков) и дальность (10 блоков) ледяной стены живут в `BalanceConfig` (код), в файле их нет. Остальные ключи раунда (tp.*, звуки) добавляются вместе с задачами T10, T12.


Ключи `tp.*` (T10) готовы: `tp.swordScale` множит размер меча в руке, блоке и держании и ножен (на бедре и в запечатанном блоке), только вид от третьего лица; клиентский файл перечитывается раз в секунду, перезапуск не нужен. Остальные ключи раунда (rukia.iceWall, terrain.*, звуки) добавляются вместе с задачами T5, T9, T12.

Ключи `uryu.*` клиентского файла (эффекты Урю, по уровням качества, U-FX 0): см. `design/results/uryu_ufx0.md`, раздел Config keys (`uryu.maxFlechettes`, `maxStardust`, `starFlakes`, `wingOverglow`, `bands`, `veinOverlay`, `rainSplashShare`, `afterimages`, `wingScale`, `fpGuard`, `shunpoOld`, `glow`).
