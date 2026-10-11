# BC5: поза/удар Better Combat под наш мод, очистка T4 (2026-10-11)

Игра не запускалась. Всё про вид в кадре и работу флага в живой сессии: UNVERIFIED (общий прогон). Код и ассеты BC не копировались, BC и playerAnimator подключены как `modCompileOnly` (в jar не попадают) плюс `modLocalRuntime` из BC2.

## Решение 1. Поза и удар BC привязаны к нашему состоянию

Поза: в наших `weapon_attributes/{sode_no_shirayuki,senbonzakura}.json` теперь `{"parent":"bettercombat:katana","attributes":{"pose":""}}`. По байткоду BC (`WeaponAttributesHelper.override`) непустое (не null) поле ребёнка перекрывает родителя, а `AbstractClientPlayerEntityMixin.updateAnimationsOnTick` пропускает пустую `pose` (`isEmpty`), то есть слоёв позы нет вообще: рука держит меч как ванильную вещь (одной рукой), в SEALED ножны на бедре, поза катаны не включается. Для чужих игроков поза идёт через data tracker (`getMainHandIdleAnimation`, на сервере считается тем же `poseForPlayer`), тоже пустая. Удары (`attacks`) наследуются от катаны без изменений.

Удар: `server/BcCombatGate` каждый серверный тик ставит публичный флаг BC `CombatFlags.setAttacksDisabled` (бит API_DISABLED; бит командного тега `bettercombat_disabled` не трогаем). Правило в `core/combat/BcAttackGate` (чистое, тест): флаг включён, если в основной руке zanpakuto и (состояние SEALED или удерживается блок). Флаг лежит в data tracker игрока: клиент BC (`doAttack`, замах) и сервер BC (пакет атаки) читают один и тот же. Побочный плюс: закрыт риск 7 из bc4 (серверные удары BC в обход `AttackEntityCallback` во время блока). Подключение: одна строка `BcCombatGate.init()` в `ZanpakutoManager.init`. Доставание рисует наша анимация (DrawTracker, B3), после неё BC-удар работает.

Заметка: в SEALED ЛКМ при выключенном BC падает на ванильный удар предмета (атрибут +6 урона на мече остаётся). Это поведение до BC, не менял; если нужно запретить совсем, это отдельный `AttackEntityCallback.FAIL` для SEALED.

## Решение 2. Первое лицо при ударе BC

Причина (bc4): `FirstPersonAnimationCompatibility.firstPersonMode()` при `firstPersonAnimations=AUTO` даёт THIRD_PERSON_MODEL, PA отменяет весь `renderHandsWithItems`. Исправление: `client/mixin/BcFirstPersonModeMixin` (`@Pseudo`, HEAD cancellable, `remap=false`) возвращает `FirstPersonMode.NONE`, если у локального игрока в основной руке zanpakuto. Метод вызывается при старте анимации удара. Тогда удар не меняет вид руки от первого лица: наша рука, ножны в левой руке и подъём меча остаются. Конфиг игрока не трогается, для других мечей действует настройка BC. Побочное: в первом лице у нашего меча нет движения удара (BC не вызывает `swingHand`), остаются трейл, звук, попадание; ощущение UNVERIFIED.

## Решение 3. Меч в руке при анимации BC (rightItem)

Находка: числа `rightItem` в анимациях BC в радианах (поза катаны roll 3.64, pitch -5.81 рад), в bc4 они записаны как градусы, это ошибка записи. Они подогнаны под ванильный меч. Посчитано (JOML, `Transformation.apply` проверен javap: translate, `rotationXYZ`, scale): направление лезвия при ванильном `item/handheld` `[0,-90,55]` равно (0, 0.985, -0.174), у нашего дисплея `[45,180,0]` (0, 0.707, 0.707), плоскость лезвия смотрит в -X в обоих. Анимация применяется до display, значит нужен один постоянный поворот D между ними: `Rx(-55 град)` вокруг рукояти (display translation `(0,-2,1.75)` px). Он включается только пока PA-слой реально двигает кость предмета (проба `AnimationStack.get3DTransform("rightItem", ROTATION, ...)` с невозможным значением), с плавным входом/выходом, в покое остаётся настроенный вид.

Код: `client/model/BcItemPose`, вызов из `HeldItemRendererMixin` (push на месте бывшего запястья T4, pop в RETURN). Горячая настройка: `mod/run/pose_override.json`, ключ `bc_item` (все поля необязательны, значения по умолчанию в скобках):
`{"enabled": 1, "rot": [-55, 0, 0], "pos": [0, 0, 0], "pivot": [0, -2, 1.75], "fade_ticks": 3}`
(rot градусы x,y,z; pos и pivot в пикселях модели 1/16; левая рука зеркалит y,z поворота и x сдвигов). Файл читается каждые ~0.15 с в dev.

## Решение 4. Очистка T4

Удалено: `client/mixin/BipedEntityModelMixin.java` (замах рук/торса, был единственным содержимым), `client/model/SwingPose.java` (математика замаха, ключ `swing` в pose_override; отдельного теста не было), блок запястья T4 в `HeldItemRendererMixin`, запись в `reiatsu_test.client.mixins.json`, harness-код `swings`/`extFreezeSwing`/`extTick` в `SpikeHarness`, сценарии T4 и swing-перебор в `tools/spike_tune_gen.py`. Оставлено: доставание (DrawTracker, DrawRig), ножны T3 (`ScabbardFeature`/`ScabbardRenderer`), поза и рука первого лица, `fp_swing` в harness (ванильный взмах первого лица). Ванильный взмах третьего лица (копание) теперь без нашей позы, обычный.

## Тесты
Новые: `BcAttackGateTest` (3), `ClientMixinConfigTest` (2, список клиентских миксинов совпадает с исходниками, T4 нет), `WeaponAttributesPresetTest` +1 (пустая `pose`, `attacks` не переопределены). Полный `gradlew compileClientJava test`: 402 теста, 0 упало.

## Проверить в общем прогоне (UNVERIFIED)
1. Старт клиента: mixin `BcFirstPersonModeMixin` применился (pseudo-цель), нет ошибок про `CombatFlags`.
2. SEALED: рука пустая, ножны на бедре, поза катаны нет, ЛКМ не запускает удар BC (виден только ванильный). После `J`: доставание, затем одна рука держит меч, ЛКМ бьёт BC.
3. Блок ПКМ: удар BC не стартует (и на сервере).
4. Первое лицо: удар BC не прячет руку, ножны в левой руке, подъём меча.
5. Третье лицо, удар BC (свой и чужой): положение меча в руке; подогнать `bc_item.rot/pos/pivot`; нет рывка на входе/выходе.
6. Левша: зеркало `bc_item`.
7. Лог: нет "Pose stack not empty" при ударах из первого и третьего лица.
8. `two_handed: true` остаётся от катаны: BC прячет предмет второй руки при мече в основной. Если мешает, добавить `"two_handed": false` в наши json.
