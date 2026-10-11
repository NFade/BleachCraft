# BC3: пресеты Better Combat и урон мечей (2026-10-11)

Игра не запускалась. Ассеты и код BC не копировались. Проверка удара в игре: UNVERIFIED (общий прогон позже).

## Что сделано
- `mod/src/main/resources/data/reiatsu_test/weapon_attributes/sode_no_shirayuki.json` и `senbonzakura.json`: `{"parent": "bettercombat:katana"}`.
  - Путь и имя подтверждены по jar BC 2.4.0 (javap `WeaponRegistry`: префикс `weapon_attributes/`, суффикс `.json`; в самом jar лежат `data/minecraft/weapon_attributes/diamond_sword.json` вида `{"parent":"bettercombat:sword"}` и `data/bettercombat/weapon_attributes/katana.json`). Имя файла = путь id предмета, пространство имён = `reiatsu_test`.
  - Мечей мода два (зарегистрированы через `ModItems.register(name, CharacterId)`). `spike_item` не меч (тестовый ассет spike 2b, `new Item`, не `ZanpakutoItem`), файла нет, как и решено в bc_check.
  - Поза `pose_two_handed_katana` и анимации остаются от пресета BC (двуручная катана); одноручный стиль (`bettercombat:sword`) это дизайн-вопрос BC4/BC5.
- `ModItems.java`: оба меча получили `Item.Settings.attributeModifiers(...)`: `GENERIC_ATTACK_DAMAGE +6.0` (Item.BASE_ATTACK_DAMAGE_MODIFIER_ID, ADD_VALUE, MAINHAND) и `GENERIC_ATTACK_SPEED -2.4` (BASE_ATTACK_SPEED_MODIFIER_ID). Константы `ATTACK_DAMAGE_BONUS`, `ATTACK_SPEED_BONUS`.

## Числа урона
- Алмазный меч в 1.21.1: `SwordItem.createAttributeModifiers(material, 3, -2.4)` = 3 + 3 (алмаз) = +6, скорость -2.4 (javap). С базой игрока 1 это 7 урона на полный заряд, скорость атаки 1.6 (как у ванильного меча; скорость катан отдельный шаг).
- Урон удара BC = атрибут игрока × `damage_multiplier` атаки: в пресете katana серия 0.8 / 1.2 / 1.2 / 0.8, то есть ~5.6 / 8.4 / 8.4 / 5.6 при полном заряде. Средний за цикл 7.0.
- В `PLAN_2026-10-11.md` раздел C чисел урона нет, взяты ориентир задачи (алмазный меч).

## Как мод считает урон
- `ZanpakutoManager.allowDamage` и `BlockParry.onHit` работают только когда ПОЛУЧАТЕЛЬ — `ServerPlayerEntity`; не меняют урон исходящего. `BlockParry.kindOf` считает MELEE по типам `PLAYER_ATTACK`/`MOB_ATTACK`/`MOB_ATTACK_NO_AGGRO` с живым атакующим. BC на сервере вызывает `ServerPlayerEntity.attack` (javap `ServerNetwork`, `method_7324`), значит удар игрока по игроку идёт как `PLAYER_ATTACK` и -50% вне окна парирования применяется как обычно (по коду; в игре UNVERIFIED).

## Блок и BC (javap `MinecraftClientInject`)
- `startUpswing` (клиент) делает ранний `return`, если `player.isUsingItem()` (`method_6115`), то же при идущем замахе, при `attackCooldown>0` и при недозаряженном откате. Блок на ПКМ (UseAction.BLOCK, `setCurrentHand`) значит замах не начинается: «блок запрещает атаку» сохраняется на клиенте. Когда замах стартует, BC вызывает `stopUsingItem()` (`method_6075`).
- Серверная сторона: обработчик `C2S_AttackRequest` в BC проверяет только `CombatFlags.isAttackDisabled`, `isUsingItem` не проверяет и вызывает `attack` напрямую, минуя Fabric `AttackEntityCallback`. Наш серверный предохранитель в `BlockParry.init` (AttackEntityCallback.FAIL при блоке) на удары BC НЕ действует. Для честного клиента это не важно (замах не стартует), модифицированный клиент мог бы бить из блока. Если нужна серверная гарантия: отключать BC флагом `CombatFlags` (2.4.0) на время блока или проверять в ServerPlayNetworkHandler-хуке. Не правилось.

## Риски (только записаны)
- BC `pre_doItemUse` отменяет ПКМ во время замаха и пока откат не дошёл до порога: блок нельзя начать посреди замаха, влияет на ощущение парирования.
- Урон стоит на мече даже в ножнах (SEALED): атрибут есть всегда, пока меч в руке.
- Поза/анимации BC против `HeldItemRendererMixin` и `BipedEntityModelMixin`: задачи BC4/BC5.
- Лицензия ARR: открытый вопрос из bc_check, без изменений.

## Тест
`mod/src/test/java/dev/minebleach/reiatsutest/data/WeaponAttributesPresetTest.java` (2 теста, без Minecraft): набор имён файлов в `weapon_attributes` равен набору `register("...", CharacterId...)` из `ModItems.java` (по файлу исходника); каждый файл парсится Gson, `parent` задан, это `bettercombat:katana`. Оба теста проходят. Общий `gradlew test`: 374 запущено, 3 упали, все три в голосовом наборе (`MatcherFixtureTest`, `MustNotFireTest`: фраза `senbonzakura` матчится как `byakuya.shikai.release`), это текущая правка `phrases.json` параллельным голосовым агентом, к BC3 отношения не имеет.
