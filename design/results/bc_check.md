# BC1: проверка подключения Better Combat как зависимости (2026-10-11)

Только чтение и веб. Код мода не менялся, jar не скачивался. Всё, что не подтверждено первоисточником, помечено UNVERIFIED.

## 1. Версия и библиотеки

Актуальная версия для 1.21.1 Fabric: **2.4.0+1.21.1-fabric** (Modrinth version id `JJQpBecL`, релиз 2026-07-14). Файл `bettercombat-fabric-2.4.0+1.21.1.jar`, 930 023 байт, SHA-1 `38d1d1f22e9255092baf85abf93a3b9746fa6ed1`. Игровые версии 1.21 и 1.21.1, loader Fabric, client и server обязательны.
- Предыдущие: 2.3.2 (2026-03-07), 2.3.1, 2.3.0, 2.2.5, 2.2.4, 2.2.3 (2025-05-15). Версии 3.x относятся к 1.21.8 и новее, для 1.21.1 их нет.
- Что нового в 2.4.0 (полезно нам): CombatFlags API, которое выключает Better Combat для игрока тегом сущности `/tag @p add bettercombat_disabled` (или через Java). Можно отключать BC в состоянии SEALED или во время блока без компиляционной связи.
- Требования из `fabric.mod.json` ветки 1.21.1 в репозитории (UNVERIFIED для собранного jar 2.4.0): `minecraft >=1.20.0`, `fabricloader >=0.14.0`, `fabric-api *`, `cloth-config >=11.0.0`, `playeranimator >=1.9.0`. Наши loader 0.19.5 и Fabric API 0.116.17+1.21.1 подходят (BC собирался на Fabric API 0.103.0, loader 0.16.5, Yarn 1.21.1+build.3 как у нас).
- Обязательные библиотеки на Modrinth (все required):
  - playerAnimator, slug `playeranimator` (id `gedNE4y2`, MIT). Для 1.21.1 есть 2.0.0, 2.0.1, 2.0.4 (все beta). Новейшая: `2.0.4+1.21.1-fabric` (2025-12-28). BC собран на 2.0.0-alpha1+1.21 (gradle.properties).
  - Cloth Config, slug `cloth-config` (id `9s6osm5g`, LGPL-3.0). Для 1.21.1: `15.0.140+fabric` (последняя), `15.0.130+fabric`.
  - Fabric API (уже есть).
- MixinExtras: README требует его в списке, но на Modrinth он не зависимость. Fabric Loader 0.15+ поставляет MixinExtras сам, поэтому проблем быть не должно (UNVERIFIED, проверим на первом запуске).
- Tiny Config встроен в jar BC (jar-in-jar), отдельно ставить не надо.
- Транзитивные зависимости через Modrinth maven не подтянутся (pom пустой, без `<dependencies>`), поэтому playerAnimator и Cloth Config добавляем вручную.

## 2. Лицензия

Файл `LICENSE` (ветка 1.21.1 и 26.2): `Copyright (c) 2022 Zsolt Molnár`, «All Rights Reserved», дальше стандартная оговорка «AS IS / без гарантий / авторы не отвечают за ущерб». Разрешения на использование, копирование, изменение и распространение в тексте **нет** (вывод из пересказа WebFetch, дословный текст не получен, UNVERIFIED). Modrinth: «Licensed ARR» (`LicenseRef-All-Rights-Reserved`), `fabric.mod.json`: "All Rights Reserved".
- Что это значит для нас (не юридическая консультация): в нашем репозитории и jar не должно быть ни кода, ни ассетов (анимаций, звуков, текстур) Better Combat, jar-in-jar (`include`) запрещаем. Наши JSON-файлы пресетов только ссылаются на `bettercombat:...` по id, ничего не копируют. Игрок сам ставит BC с Modrinth. Это обычная схема «мод-зависимость», и автор сам приглашает к ней: на странице Modrinth сказано, что разработчики могут добавлять совместимость через JSON data-файлы, и перечислены сторонние моды-партнёры (Simply Swords, MedievalWeapons, Spectrum и др.).
- Но явного права в лицензии нет, поэтому открытый пункт из `PARRY_PLAN_BC.md` остаётся: для публичного релиза с обязательной зависимостью написать автору (GitHub issues, Discord по ссылке со страницы). Для приватной разработки риска нет. Опубликованный пример интеграции не заменяет письменного разрешения.
- Dev-зависимость (`modLocalRuntime`/`modImplementation`) на этапе разработки означает только скачивание jar из Modrinth maven на нашей машине, ничего не распространяется.

## 3. Формат пресетов и как назначить меч стороннего мода

Источник: README ветки 1.21.1 и содержимое `common/src/main/resources/data/bettercombat/weapon_attributes`.

**Способ 1 (рекомендуем): data-файл по id предмета.**
Путь: `data/<namespace предмета>/weapon_attributes/<путь id предмета>.json`. Для нас:
- `mod/src/main/resources/data/reiatsu_test/weapon_attributes/sode_no_shirayuki.json`
- `mod/src/main/resources/data/reiatsu_test/weapon_attributes/senbonzakura.json`
Минимальное содержимое (подтверждено README):
```json
{ "parent": "bettercombat:katana" }
```
Код `WeaponRegistry.loadAttributes` регистрирует файл как оружие, только если id существует в `Registries.ITEM`. Наши предметы: `reiatsu_test:sode_no_shirayuki`, `reiatsu_test:senbonzakura`. `reiatsu_test:spike_item` оставляем без файла (не оружие).
- Свой общий пресет (по коду WeaponRegistry: файл без предмета с таким id остаётся контейнером-родителем; UNVERIFIED на практике): `data/reiatsu_test/weapon_attributes/zanpakuto_katana.json` с `{"parent":"bettercombat:katana","attributes":{...}}`, а в файлах предметов `{"parent":"reiatsu_test:zanpakuto_katana"}`. Потомок переопределяет только указанные поля, цепочка наследования любой длины. Без `parent` нужен полный объект `attributes`.
- Готовые пресеты BC (33 файла): anchor, axe, battlestaff, bow_*, claw, claymore, coral_blade, crossbow_*, cutlass, dagger, double_axe, fist, glaive, halberd, hammer, heavy_axe, **katana**, lance, mace, pickaxe, rapier, scythe, sickle, soul_knife, spear, staff, sword, trident, twin_blade, vanilla_mace, wand.
- Пресет `bettercombat:katana` (получено с GitHub): `range_bonus 0`, `pose: bettercombat:pose_two_handed_katana`, `two_handed: true`, `category: katana`, 4 атаки (горизонталь справа 0.8x, вертикаль слева 1.2x, вертикаль справа 1.2x, горизонталь слева 0.8x), `angle 120`, `upswing 0.5`, анимации `bettercombat:two_handed_slash_*`, звук `bettercombat:katana_slash`. Анимации уже встроены в BC, свои `player_animations` нам не нужны.
- Поля атаки в 1.21.1 пресетах: `hitbox`, `damage_multiplier`, `angle`, `upswing`, `animation`, `swing_sound`. В README примере встречаются `damageMultiplier` и `attack_range` (устаревшая или ошибочная запись): при своих переопределениях ориентироваться на реальные пресеты (`damage_multiplier`, `range_bonus`). UNVERIFIED, какой регистр принимает парсер, проверить логом на BC2. В 2.3.0 добавлены `range_multiplier` и `movement_speed_multiplier` у атак, в 2.3.0 трейлы оружия.

**Способ 2: data component `bettercombat:preset_id`** на конкретный стак (README: `/give @p minecraft:wooden_sword[bettercombat:preset_id="bettercombat:claymore"]`). Порядок поиска в коде: сначала компонент стака, потом реестр по id предмета. Для постоянной привязки через `Item.Settings.component(...)` нужен компиляционный доступ к типу компонента BC (связка кода), поэтому для нас лишнее: берём способ 1. Компонент годится для дебага командой `/give`.

**Что нужно ZanpakutoItem:**
- Ничего менять в классе не нужно. Но урон: BC использует атрибут атаки игрока, а у наших предметов нет `attribute_modifiers` (в `ModItems.register` только `maxCount(1)` и компонент). Без модификатора урон будет как кулаком (UNVERIFIED, но по устройству ванильной боёвки так), умножаемый на `damage_multiplier`. Нужен шаг 5 из `PARRY_PLAN_BC.md`: `Item.Settings.attributeModifiers(...)` с `attack_damage` и `attack_speed`.
- `pose: two_handed_katana` заставит держать меч двумя руками в третьем лице (пустая вторая рука); если нужен одноручный стиль, взять `bettercombat:sword` как parent. Это вопрос дизайна.

## 4. Maven и строки для build.gradle

Репозиторий Modrinth (в блок `repositories {}` у нас сейчас пустой):
```groovy
repositories {
	exclusiveContent {
		forRepository {
			maven { name = "Modrinth"; url = "https://api.modrinth.com/maven" }
		}
		filter { includeGroup "maven.modrinth" }
	}
}
```
Зависимости (в `dependencies {}`, после fabric-api):
```groovy
	// Better Combat (All Rights Reserved): только dev-зависимость, не встраивать (без include), jar не копировать в репозиторий.
	modLocalRuntime "maven.modrinth:better-combat:2.4.0+1.21.1-fabric"
	modLocalRuntime "maven.modrinth:playeranimator:2.0.4+1.21.1-fabric"
	modLocalRuntime "maven.modrinth:cloth-config:15.0.140+fabric"
```
Вариант по тексту задачи (если нужна компиляция против API Better Combat, например CombatFlags или TrailAppearanceOverride): заменить `modLocalRuntime` на `modImplementation` у `better-combat`. Для сквозных data-файлов достаточно `modLocalRuntime`: код BC не попадает на компиляционный classpath (меньше связки, нет случайных импортов). Координаты проверены: pom по адресам `https://api.modrinth.com/maven/maven/modrinth/better-combat/2.4.0+1.21.1-fabric/...pom` и аналогичным для playeranimator и cloth-config отвечают 200 (pom пустые, транзитивных зависимостей нет). Версия в maven = `version_number` Modrinth (README: `VERSION-fabric`). UNVERIFIED: что `modLocalRuntime` корректно ремапит эти jar в нашей версии Loom (`net.fabricmc.fabric-loom-remap` 1.17.21); Gradle-сборку не запускал. Если не сработает, `modRuntimeOnly`, затем `modImplementation`.
Запуски `runClient`, `runPhase4..6` берут runtime-classpath, значит BC загрузится во всех харнесс-запусках. Для чистых тестов без BC убрать/закомментировать строки.

## 5. fabric.mod.json

Пока разрешение автора не получено, зависимость в метаданных **мягкая** (мод работает без BC, JSON-файлы просто игнорируются):
```json
	"suggests": {
		"bettercombat": ">=2.4.0"
	}
```
После подтверждения автора и перехода на «BC обязателен» (дописать в существующий блок `depends`):
```json
		"bettercombat": ">=2.4.0",
		"playeranimator": ">=2.0.0",
		"cloth-config": ">=15.0.130"
```
`playeranimator` и `cloth-config` у BC уже указаны в его depends, так что Loader сам потребует их при установке BC; дублировать у нас нужно только если используем их напрямую. Id мода BC: `bettercombat` (подтверждено по его `fabric.mod.json`).

## 6. Совместимость с HeldItemRendererMixin и остальным (по описанию и исходникам BC, код наш проверить на BC4)

- Список mixin-ов BC (1.21.1): общие `DataComponentTypesMixin, EnchantmentMixin, ItemStackMixin, RangedWeaponItemMixin, ServerPlayNetworkHandlerMixin, player.LivingEntity(Accessor/Mixin), player.PlayerEntity(Accessor/Mixin/RangeMixin)`; клиентские `AbstractClientPlayerEntityMixin, ClientPlayerEntityMixin, ClientPlayerInteractionManagerMixin, ClientPlayNetworkHandlerMixin, ColliderDebugRenderer, InGameHudInject, ItemStackTooltipMixin, MinecraftClientAccessor, MinecraftClientInject`.
  - `HeldItemRenderer` среди них **нет**, прямого пересечения с нашим `HeldItemRendererMixin` (`updateHeldItems`, `renderItem`) нет. На странице Modrinth и README про отрисовку от первого лица ничего не сказано. Риск косвенный: playerAnimator сам правит рендер игрока и руки (в том числе от первого лица), как именно и затрагивает ли `HeldItemRenderer`, не проверено (UNVERIFIED).
  - `BipedEntityModelMixin` (наш `animateArms` на HEAD с cancel) может конфликтовать с позой и анимацией, которые BC/playerAnimator выставляют в `setAngles`/`animateArms` (UNVERIFIED). README прямо говорит, что BC несовместим с модами, меняющими «player model modifications», «custom item wielding appearance», «player animation changes» и просит снять такую логику перед интеграцией: то есть наш замах (T4) и поза руки при ударе придётся отключить.
  - `LivingEntityMixin.blockedByShield` у нас и `player.LivingEntityMixin` у BC наложены на один класс, но на разные методы (UNVERIFIED, проверить логом mixin).
- Блок на ПКМ (наш `use`/`UseAction.BLOCK`): по коду `MinecraftClientInject` BC в `startUpswing` отказывается начинать замах, пока `player.isUsingItem()`, то есть пока держится блок, атаки нет. Это совпадает с принятым дефолтом «блок запрещает атаку». Обратное: `doItemUse` у BC отменяет ПКМ во время замаха (upswing) или пока откат атаки не дошёл до порога, значит блок нельзя начать посреди замаха (влияет на ощущение парри).
  - Когда замах стартует, BC вызывает `stopUsingItem()` (прервёт использование).
- Урон: BC отменяет ванильный `doAttack` и бьёт сам по пакету; на сервере обработчик `ServerPlayNetworkHandlerMixin` вызывает обычную атаку игрока (UNVERIFIED). Проверить, что наш `allowDamage` (-50% вне окна парирования, ZanpakutoManager) видит такие удары как melee-урон.

## 7. Риски и открытые вопросы

1. Лицензия ARR: нет явного права на зависимость. Приватно можно, публично нужен ответ автора. Не вшивать jar (include), не копировать ресурсы.
2. Мод BC обязателен для работы боёвки при релизе: без него у катан нет ударов на ЛКМ (ванильный удар остаётся).
3. playerAnimator для 1.21.1 только в статусе beta (2.0.x), без релиза. Совместимость с BC 2.4.0 UNVERIFIED, проверить на нашем запуске.
4. Конфликт с T4 (наш замах, ветка tp3b) и `BipedEntityModelMixin`: нужно либо отключать наш замах при наличии BC, либо удалять. Результат T4 тогда выбрасывается.
5. Позы и ощущения: пресет katana требует двух рук (`two_handed`), наш рендер руки в `HeldItemRendererMixin` рисует руку перед предметом в первом лице; как BC рисует руки/оружие в первом лице, неизвестно (UNVERIFIED), проверка на BC4.
6. Урон без `attribute_modifiers` (см. п.3), скорость катан отдельный шаг 5.
7. MixinExtras: см. п.1, ожидаем встроенный в Fabric Loader.
8. BC не совместим с модами, меняющими те же механики (двойное оружие, дальность атаки, таймер и откат атаки, обработчики клавиш атаки/добычи, модель игрока). Наш мод в этих областях: `HeldItemRendererMixin`, `BipedEntityModelMixin`, `GameRendererMixin` (UNVERIFIED насчёт последнего).
9. `README` показывает устаревшие имена полей (`attack_range`, `damageMultiplier`); сверять с реальными пресетами.
10. Сборка Gradle с новыми зависимостями не запускалась по условию задачи (BC2 проверит).

## Источники
- Modrinth API: https://api.modrinth.com/v2/project/better-combat/version (2.4.0 id JJQpBecL), проект https://modrinth.com/mod/better-combat
- Репозиторий: https://github.com/ZsoltMolnarrr/BetterCombat (ветка `1.21.1`: README.md, LICENSE, CHANGELOG.md, gradle.properties, fabric/src/main/resources/fabric.mod.json, common/src/main/resources/bettercombat.mixins.json, `data/bettercombat/weapon_attributes/katana.json`, `sword.json`, `logic/WeaponRegistry.java`, `mixin/client/MinecraftClientInject.java`)
- Modrinth maven: https://api.modrinth.com/maven (проверены pom better-combat, playeranimator, cloth-config)
