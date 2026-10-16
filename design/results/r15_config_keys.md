# R15: ключи конфига

| Ключ | Было (R14) | Стало (R15) |
|---|---|---|
| `rage.shikaiCostTenths` | ключ игнорировался, шикай бесплатный | умолчание **400** (40.0 Rage), читается и пишется в файл. Rage на шкале должно быть не меньше значения (иначе `NOT_ENOUGH_RAGE` и тост), но при активации шикая Rage **не списывается** (события `RageSpent` нет). `/reiatsu dev set rage.shikaiCostTenths 0` снимает требование |
| `rage.bankaiCostTenths` | 800 | без изменений (проверка и списание) |
| `rage.bankaiMinShikaiTicks` | 400 | без изменений |
| `uryu_pose.abilityFp` (dev-файл `run/pose_override.json`, не конфиг) | нет | 1 = от 1-го лица жест способности: лук поднимается левой рукой, правая тянет тетиву и отпускает на событии каста; 0 = как было |

Dev-ключи FxTune (не в файле конфига, `run/fx_override.json`), умолчания R15 для первого лица креста:
`uryu.cross.fpX` 0.0 (было -0.12), `uryu.cross.fpRy` 30 (было 15), `uryu.cross.fpItemScale` 0.6 (было 1.0), `fpItemPitch` 0 (было -60),
`fpItemYaw` -40 (было 0), `fpItemDx` -0.07, `fpItemDy` 0.08, `fpItemDz` 0.10 (было 0 / 0 / 0.14). Третье лицо без изменений.

## Раунд 4
| Ключ | Значение |
|---|---|
| `terrain.uryuWings` (`enabled`, `profile`, `radius`, `cap`) | S, 1.3, cap 10: стрела Wing Barrage, остановленная блоком, царапает мягкие блоки |
| `terrain.uryuRain` | S, 3.5, cap 140: Licht Regen крошит мягкие блоки в квадрате дождя |
| `terrain.uryuStar` (`tunnelRadius`, `craterRadius`) | M, 1.0 / 3.0, cap 450: Sternenstaub пробивает туннель на 6 блоков за блоком остановки и оставляет кратер. M = твёрдость до 2.0 (дерево, камень, земля, стекло), обсидиан не ломается |
| `uryu.cross.tpTilt` (FxTune) | 8 градусов: низ креста чуть к телу от 3-го лица (минус = от тела) |
| `uryu.fp.hideX/hideY/hideZ` (FxTune) | 0.12 / -0.30 / 0.30: куда уходит рука тетивы при полном натяжении |
| `BowFpPose.RIGHT_OFF` | 0.03, -0.05, 0.05 (было 0.45, -0.62, 0): рука тетивы лежит на нокче |
Все ключи `terrain.*` меняются в игре через `/reiatsu dev set terrain.uryuStar.cap 100` (оператор). Вся разрушаемость подчиняется `terrain.enabled`, игровому правилу mobGriefing и режиму игры, как у Бякуи.
