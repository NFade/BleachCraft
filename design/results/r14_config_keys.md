# R14: ключи конфига и дев-команды

## Команды (оператор, уровень 2)

| Команда | Что делает |
|---|---|
| `/reiatsu dev get` | список секций и сколько ключей в каждой |
| `/reiatsu dev get <префикс>` | ключи с префиксом и их текущие значения (до 40 строк), например `balance.tsukishiro`, `uryu.bow`, `balance.regen` |
| `/reiatsu dev set <ключ> <значение>` | пишет ОДИН ключ в `config/reiatsu_test.json` и сразу перечитывает (как `/reiatsu reload`); ключ дополняется по Tab, текущее значение подсказывается во втором аргументе. Неизвестный ключ и нечисло отвергаются, число вне диапазона зажимается (в ответе видно `[asked X: clamped or rounded]`) |
| `/reiatsu dev unset <ключ>` | только `balance.*`: убирает ключ из файла, значение возвращается к умолчанию кода |

Секции, которые можно менять на ходу: `balance`, `rage`, `mech`, `uryu`, `kido`, `terrain`, `rukia`, `bladeFlight`, `doubleJump`. `voice` и `frost` требуют перезапуска. Для клиентских ключей `uryu.bow.*` (анимация натяжения) в одиночной игре достаточно одного файла.

## Новая секция `balance` (разреженная: в файле только то, что менял игрок)

Единицы как их читает человек, не машина. `<способность>` это имя в нижнем регистре: `tsukishiro hakuren shirafune ice_wall mode_attack mode_barrier cage scatter hakuteiken senkei ikorose hyapponzashi snake_hook butou_renjin korose hakufuku heilig_pfeil sprenger seele_schneider wing_barrage licht_regen sternenstaub`.

| Ключ | Умолчание R14 | Что |
|---|---|---|
| `<способность>.cost` | см. `/reiatsu dev get balance.<имя>` | реацу, очки |
| `<способность>.cooldown` | | откат, секунды |
| `<способность>.enabled` | 1 | 0 выключает способность |
| `<способность>.<параметр>` | | радиус, длина, урон и т.д. (`radius length halfWidth damage damage2 knockback slowAmp statusTicks maxTargets aimRange ...`), какие есть у способности, показывает `get` |
| `regen.<sealed/base/shikai/bankai>.perBatchTenths` | 10 / 10 / **10** / **8** | десятые реацу за порцию (порция 5 тиков, 4 в секунду: 10 = +4.0/с) |
| `regen.<состояние>.upkeepTenths` | 0 / 0 / **2** / 0 | расход содержания за порцию |
| `shikaiReleaseCost` | 15 | реацу на вход в шикай |
| `bankaiCapSeconds` | 45 | длительность банкая |
| `shikaiIdleSeconds`, `gcdTicks`, `barrierSeconds`, `barrierReductionPercent`, `bankaiReentrySeconds` | 120, 12, 5, 80, 60 | прочее |
| `shunpo.cost` / `shunpo.cooldown` / `shunpo.distance` / `shunpo.minDistance` | 5 / 2.5 / 9 / 1.5 | рывок, очки, секунды, блоки |
| `shunpo.uryuDistanceBonus` | 2 | блоки сверх базового у Исиды |
| `shunpo.uryuCooldownBonus` | **1.0** с (было 0) | откат Исиды сверх базового: 2.5 + 1.0 = 3.5 с |

Что не настраивается: фазы (тайминги) способностей, размер шкалы, лимит запросов.

## Изменённые умолчания и ключи

| Что | Было | Стало |
|---|---|---|
| `rage.shikaiCostTenths` | 400, платился и проверялся | **удалён из работы**: шикай ничего не стоит и не требует Rage, ключ в файле игнорируется и больше не пишется. Гейт банкая (`bankaiCostTenths` 800, `bankaiMinShikaiTicks` 400) без изменений |
| реген реацу в шикае | +10 −3 за порцию = +2.0/с | +10 −2 = **+3.2/с** |
| реген реацу в банкае | +5 = +2.0/с | **+3.2/с** |
| откат рывка Исиды | 2.5 с | **3.5 с** |

## Клиентские dev-ключи (`run/fx_override.json`, вне git; через `-Pfx=ключ=значение` в harness)

| Ключ | Умолчание | Что |
|---|---|---|
| `aura.shikaiOnlyOnCast` | 1 | 0: вернуть постоянный «пар» (клубы) шикая Рукии и Бьякуи; остальные эффекты ауры не затрагиваются |
| `aura.releasePulse` / `aura.castPulse` | 4 / 3 | секунд ауры после активации шикая / после каста |
| `uryu.cross.arm` | 1 | 0: рука с запечатанным крестом не поднимается (3-е лицо) |
| `uryu.cross.armPitch` / `armYaw` / `armRoll` | -35 / -5 / 22 | поза руки, градусы |
| `uryu.cross.itemTurn` | 1 | множитель поворота креста обратно, чтобы цепь висела вниз |
| `uryu.cross.fp`, `fpX/fpY/fpZ`, `fpRx/fpRy/fpRz` | 1, -0.12/0.45/0, 0/15/0 | рука с крестом от 1-го лица |
| `uryu.cross.fpItem*` и `tpItem*` (`Pitch Roll Yaw Dx Dy Dz Scale`) | fp Pitch -60, остальное 0/1 | поворот и сдвиг самого креста в руке |
| `uryu.abilityRight` (в `pose_override.json`, секция `uryu_pose`, ключ `abilityRight`) | 1 | 0: правша без анимаций каста слоя Исиды |


