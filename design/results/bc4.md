# BC4: конфликты руки/позы с Better Combat (2026-10-11)

Проверка по коду и байткоду (javap по jar из кэша Gradle, распаковано во временную папку сессии, в репозиторий ничего не попало). Игра не запускалась: всё про поведение в кадре помечено UNVERIFIED и проверяется в общем прогоне runPhase4/5.

## 1. Что миксинят BC 2.4.0 и playerAnimator 2.0.4 (имена Yarn)

Better Combat (`bettercombat.mixins.json`, `bettercombat-fabric.mixins.json`):
- `HeldItemRenderer`, `PlayerEntityRenderer`, `BipedEntityModel`, `ArmorFeatureRenderer`: BC не миксинит.
- `LivingEntityRenderer`: `fabric.mixin.LivingEntityRendererMixin` есть в конфиге, но класс пустой (ни одного инжекта).
- Остальное клиентское: `AbstractClientPlayerEntity` (`<init>` TAIL, ставит слои PA: атака и четыре позы), `ClientPlayerEntity.tickMovement` (INVOKE), `MinecraftClient` (`doAttack`/`handleBlockBreaking`/`doItemUse`/`tick`/`disconnect`), `InGameHud.renderCrosshair`, `ClientPlayerInteractionManager.cancelBlockBreaking`, `ClientPlayNetworkHandler.onParticle`, `ItemStack` tooltip, `DebugRenderer.render` TAIL (отладка коллайдеров).
- Общее: `LivingEntity.getAttributeValue` HEAD, `takeKnockback` HEAD; `PlayerEntity.tick` TAIL, `initDataTracker` TAIL, `getEquippedStack` HEAD, `attack` (STORE и три редиректа), `getEntityInteractionRange` (WrapOperation); `ServerPlayNetworkHandler.onPlayerAction`; `ItemStack`, `Enchantment`, `RangedWeaponItem`, `DataComponentTypes`.

playerAnimator (`playerAnimator-common.mixins.json`):
- `PlayerEntityModel.setAngles`: HEAD (сброс опорных точек) и INVOKE `ModelPart.copyTransform` ordinal 0, то есть ПОСЛЕ `super.setAngles` (`PlayerModelMixin`, priority 2000). Тут PA записывает анимацию в `head/leftArm/rightArm/leftLeg/rightLeg/body`.
- `BipedEntityModel`: `<init>` RETURN, `copyStateTo` RETURN, intrinsic `render`. На `animateArms`/`setAngles` не лезет.
- `HeldItemRenderer`: `renderHandsWithItems` (метод `renderItem(F,...)`) HEAD cancellable; `renderItem(LivingEntity,...)` INVOKE `ItemRenderer.renderStatic` cancellable.
- `HeldItemFeatureRenderer.renderItem`: INVOKE `MatrixStack.multiply` ordinal 0 (только при bend) и INVOKE `HeldItemRenderer.renderItem` (применяет к стеку матриц преобразование кости `rightItem/leftItem` из анимации).
- `PlayerEntityRenderer`: `render` INVOKE `LivingEntityRenderer.render`, `setupRotations` RETURN, `renderArm` INVOKE `PlayerEntityModel.setAngles` (сообщает модели, что это рука от первого лица).
- `LivingEntityRenderer`: `render` FIELD `features` (priority 2000, в проходе первого лица оставляет только `PlayerHeldItemFeatureRenderer`) и bend-вариант (INVOKE `Iterator.next`/`hasNext`, только если включён bend).
- `ArmorFeatureRenderer`: только `<init>` RETURN. Прочее: `CapeFeatureRenderer`, `ElytraFeatureRenderer`, `WorldRenderer.renderLevel` (временный "третий лицо" флаг камеры), `EntityRenderDispatcher.renderShadow`.

## 2. Наши миксины и пересечения

Клиентские (`reiatsu_test.client.mixins.json`): `HeldItemRendererMixin`, `BipedEntityModelMixin`, `GameRendererMixin` (`tiltViewWhenHurt` HEAD, `getFov` RETURN), `InGameHudMixin` (`render` HEAD), два аксессора. Серверный: `LivingEntityMixin.blockedByShield` HEAD. Рендер-код без миксина: `ScabbardFeature` (через Fabric `LivingEntityFeatureRendererRegistrationCallback`), `FirstPersonHand`, `ScabbardRenderer`.

| Цель | Наше | BC / PA | Итог |
|---|---|---|---|
| `HeldItemRenderer.renderItem(F,...)` | INVOKE `Immediate.draw()` (ножны в левой руке) | PA HEAD cancel | Точки разные, инжекты сосуществуют. Поведение: пока PA отменяет проход, наших ножен и руки в первом лице нет (см. раздел 3). |
| `HeldItemRenderer.renderItem(LivingEntity,...)` | HEAD (рука, подъём меча, запись кадра руки, запястье T4) и RETURN (pop и `DrawTracker.endRender`) | PA INVOKE `renderStatic`, cancellable | Реальное пересечение. Если PA отменяет посреди метода, а наш RETURN не видит этот return, то матрица (push подъёма или запястья) не снимается и `endRender` пропускается. Зависит от порядка применения миксинов. ПРАВКА (ниже). |
| `HeldItemRenderer.updateHeldItems` | HEAD | нет | нет |
| `BipedEntityModel.animateArms` | HEAD cancel | нет | нет (PA не трогает этот метод). Порядок результата: наш замах пишется внутри `super.setAngles`, PA перезаписывает после. |
| `LivingEntity` | `blockedByShield` HEAD | BC `getAttributeValue`, `takeKnockback` | разные методы, нет |
| `GameRenderer`, `InGameHud` | `tiltViewWhenHurt`, `getFov`, `render` | BC `renderCrosshair`; PA трогает `WorldRenderer` | нет |
| `PlayerEntityRenderer`/`LivingEntityRenderer`/`ArmorFeatureRenderer` | миксинов нет | PA/BC, см. выше | нет; `ScabbardFeature` вырезается PA в проходе первого лица (фильтр `features`), это нормально |

Итог по инжектам: прямых совпадений метод+точка нет; одно условное (порядок RETURN после cancel PA).

## 3. Что BC/PA перезапишет, что сохранится

Третье лицо (чужие игроки, свой вид сзади и спереди):
- Поза BC `pose_two_handed_katana` (в пресете `bettercombat:katana`, наследуется нашими мечами): слой PA с каналами `rightArm`, `leftArm` (углы и сдвиги) и `rightItem` (поворот меча в руке: x 1.17, y -3.83, z 0.81 пикселей, pitch -5.8°, roll 3.6°). Включается ВСЕГДА, пока меч в основной руке (поза берётся из `WeaponAttributes.pose` стака), и не зависит от нашего состояния SEALED/ножны. Снимается при `handSwinging`, `isUsingItem` (наш блок ПКМ, значит рука блока остаётся ванильной), полёте на элитрах и т.п.
- Атака BC (`two_handed_slash_*`, `one_handed_slash_*`): каналы head, torso, rightArm, leftArm, rightLeg, leftLeg, rightItem. Перезаписывает руки, корпус, голову, ноги.
- Наш замах T4 (`BipedEntityModelMixin` + запястье в `HeldItemRendererMixin`): BC атака НЕ вызывает `swingHand` (в байткоде нет вызовов, `doAttack` отменён), поэтому `handSwingProgress` от удара BC равен 0 и `SwingPose.active` не срабатывает: T4 не включается, конфликта нет. T4 остаётся только для ванильных замахов (копание, взаимодействие с блоком); тогда BC убирает свою позу (`handSwinging`), и замах виден как раньше. Если BC атака и ванильный замах совпали, углы рук/торса наших `animateArms` перезапишет PA (он идёт позже), матричный наклон запястья останется поверх.
- Ножны на бедре T3 (`ScabbardFeature`/`renderHip`): сохраняются. Привязаны к `model.body.rotate(m)`, а PA пишет анимацию торса в ту же часть до отрисовки слоёв, значит ножны следуют за корпусом. Нет изгиба (bend PA применяет только к слоям с флагом upper part; у нас слой без флага), для катан эффект мал.
- Кадр руки (`captureHand` в HEAD `renderItem`): запись идёт ПОСЛЕ применения PA кости `rightItem`, поэтому кадр руки включает поворот позы BC; переход бедро-рука (доставание) заканчивается ровно там, где рисует предмет. Запасной `handFrame` (`setArmAngle`) PA кость предмета не знает, но он нужен только если захват устарел.
- Поворот меча по дисплейным трансформам модели (THIRD_PERSON_RIGHT_HAND) теперь получает ещё и `rightItem` позы BC, поэтому вид меча в руке отличается от настроенного под ванильную позу (UNVERIFIED, возможна подстройка).

Первое лицо:
- BC не миксинит `HeldItemRenderer`, ванильная рука/меч/ножны в покое наши (BC слои PA с `FirstPersonMode.NONE` для поз). PA для позы вызывает `prepForFirstPersonRender`, анимация к руке `renderRightArm` не применяется, наша `FirstPersonHand` рисует ванильную руку.
- При ударе BC (первый лицо, конфиг `firstPersonAnimations=AUTO`, нет модов firstperson/realcamera) режим `THIRD_PERSON_MODEL`: PA отменяет весь `renderHandsWithItems`. На время удара пропадают наши рука, ножны в левой руке и подъём меча; вместо них рисуется модель игрока от третьего лица (с мечом через `PlayerHeldItemFeatureRenderer`, `ScabbardFeature` отфильтрован). После удара всё возвращается. Это ожидаемое поведение BC, не баг, но визуальный скачок (UNVERIFIED).
- Эффекты по `getPerspective().isFirstPerson()` (`AuraFx`, `DrawFx`, `PetalSwarmRenderer`, `ReleaseHold`) во время такого прохода считают, что вид первый: UNVERIFIED, влияние на положение якорей FX.

## 4. Что изменено

Один файл: `mod/src/main/resources/reiatsu_test.client.mixins.json`, добавлено `"priority": 1100`. Наши клиентские миксины применяются позже PA/BC (по умолчанию 1000), поэтому RETURN-инжект `reiatsu$endRender` гарантированно видит return, который PA вставляет при cancel посреди `renderItem`; push/pop матриц и `DrawTracker` остаются парными независимо от порядка загрузки. Больше правок нет: прямых пересечений инжектов нет, замах T4 не трогали (BC5).

## 5. Риски для общего прогона runPhase4/5 (всё UNVERIFIED)

1. Запуск с BC+PA+Cloth: mixin apply без ошибок (`defaultRequire 1` у обоих), нет предупреждений про `HeldItemRenderer` и `PlayerEntityModel`.
2. Меч в SEALED (в ножнах на бедре, рука пуста): BC всё равно показывает позу двуручной катаны (руки подняты) и разрешает удар BC. Решение за дизайном: (a) в наших `weapon_attributes` задать пустую `pose` (потеряем позу у выхваченного меча), (b) свой клиентский миксин на выбор позы по состоянию, (c) `CombatFlags`/тег `bettercombat_disabled` на SEALED. Нужно решить в BC5/общем прогоне.
3. Первое лицо: проверить смену вид-кадр при ударе BC (исчезновение руки/ножен), и совпадение положения меча в руке при `THIRD_PERSON_MODEL`.
4. Третье лицо: положение меча в руке (поворот `rightItem` позы) относительно настроенного display, доставание и вкладывание (blend бедро-рука), ножны при ходьбе/приседании с позой BC.
5. Ванильный замах (копание блока) с мечом: оба слоя (наш замах и поза BC) сняты `handSwinging`, T4 должен работать как раньше.
6. Блок ПКМ: поза BC снимается (`isUsingItem`), рука блока ванильная; проверить, что fade поза-блок не дёргается.
7. Сервер: удары BC обходят `AttackEntityCallback` (см. bc3.md), блок-предохранитель на сервере для них не действует.
8. Стек матриц: в логе не должно быть "Pose stack not empty" при ударах BC от первого лица.
