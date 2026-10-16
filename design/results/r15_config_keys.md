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
