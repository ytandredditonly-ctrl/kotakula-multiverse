package com.unciv.dev.kotakula

import java.io.File

/** Reads a Unciv `.properties` translation file (key = value) */
fun readTranslationFile(file: File): LinkedHashMap<String, String> {
    val result = LinkedHashMap<String, String>()
    for (line in file.readLines(Charsets.UTF_8)) {
        if (line.isBlank() || line.startsWith('#') || !line.contains(" = ")) continue
        val key = line.substringBefore(" = ").replace("\\n", "\n")
        val value = line.substringAfter(" = ").replace("\\n", "\n")
        if (value.isNotEmpty()) result[key] = value
    }
    return result
}

/** Russian texts of the engine changes (KC-xx) and of the new uniques (section 1.4) */
fun GenContext.addEngineTranslations() {
    val t = translations
    addLayerEngineTranslations()
    t.section("Engine: factions (KC-01..KC-06)")
    t.add("Technocrats", "Технари")
    t.add("Humanists", "Гуманитарии")
    t.add("Common", "Общее")
    t.add("Technocrat", "Технари")
    t.add("Humanist", "Гуманитарии")
    t.add("Choose your side", "Выберите сторону")
    t.add("[civName] belongs to the other faction. The eternal war has begun!", "[civName] — из другой фракции. Вечная война началась!")
    t.add("Eternal war: peace with the other faction is impossible", "Вечная война: мир с другой фракцией невозможен")
    t.add("You attacked a civilization of our own faction. Traitor!", "Предатель фракции: вы напали на цивилизацию нашей фракции!")
    t.add("You liberated cities from the other faction. Hero of the faction!", "Герой фракции: вы отбиваете города у другой фракции!")
    t.add("Truce: [amount] turns left", "Перемирие: осталось [amount] ходов")
    val truceEnd = spec.sectionText("### 2.4.").joinToString(" ")
    t.add("The truce is ending. Time to get out the calculators and the staffs",
        Regex("приходит уведомление: «([^»]+)»").find(truceEnd)!!.groupValues[1])
    t.add("Nobody in this multiverse wanted to be a Technocrat. The faction victory is cancelled for lack of an enemy. You may fight each other like in the good old days",
        "В этой мультивселенной никто не захотел быть технарём. Фракционная победа отменена за отсутствием врага. Можете воевать друг с другом, как в старые добрые времена")
    t.add("Nobody in this multiverse wanted to be a Humanist. The faction victory is cancelled for lack of an enemy. You may fight each other like in the good old days",
        "В этой мультивселенной никто не захотел быть гуманитарием. Фракционная победа отменена за отсутствием врага. Можете воевать друг с другом, как в старые добрые времена")
    t.add("Belongs to another faction", "Принадлежит другой фракции")
    t.add("Other faction", "Другая фракция")
    t.add("[modName] mod version: [version]", "Версия мода [modName]: [version]")
    t.section("Engine: gods and startups (gods-and-startups.md)")
    t.add("Thunder Grandpa is displeased with [unitName]", "Громовой Дед недоволен: [unitName]")
    t.add("Lightning struck our [unitName]", "Молния ударила в наш юнит [unitName]")
    t.add("Chaos gave you [amount] [stat]. Or took it away - it is not sure itself", "Хаос подарил вам [amount] [stat]. Или отнял — он сам не уверен")
    t.add("We are no longer [oldPantheon]. Now we are [newPantheon]. The investors did not notice", "Мы больше не [oldPantheon]. Теперь мы [newPantheon]. Инвесторы ничего не заметили")
    t.add("Pivot ([amount] Faith)", "Пивот ([amount] веры)")
    t.add("Replace your startup by a free one. It can be done only once", "Замените свой стартап на любой свободный. Это можно сделать только один раз")

    t.section("Engine: capture, espionage, summoning (KC-13, KC-14, KC-21)")
    t.add("[cityName] has been liberated from the Technocrats. Found [amount] broken computers. Recycled into [science] Science",
        "[cityName] освобождён от технарей. Найдены [amount] сломанных компьютеров. Переработаны в [science] науки")
    t.add("[cityName] has been liberated from the Humanists. Found [amount] broken staffs. Recycled into [science] Science",
        "[cityName] освобождён от гуманитариев. Найдены [amount] сломанных посохов. Переработаны в [science] науки")
    t.add("The summoned [unit] has returned to its own dimension", "Призванный юнит [unit] вернулся в своё измерение")
    t.add("[unit] has been summoned in [cityName]!", "[unit] призван в городе [cityName]!")

    t.section("Engine: faith actions (KC-12)")
    t.add("[action] has started!", "Запущено: [action]!")
    t.add("[action] has ended", "Закончилось: [action]")
    t.add("You received [amount] [stat] from [action]", "Получено [amount] [stat] от действия [action]")
    t.add("[cityState] became a subsidiary. It does not know yet", "[cityState] стал дочерней компанией. Он пока не знает")
    t.add("Found a pantheon first", "Сначала создайте пантеон")
    t.add("Already active", "Уже действует")
    t.add("On cooldown", "Перезарядка")
    t.add("No free slot", "Нет свободного слота")
    t.add("Not enough Faith", "Не хватает веры")
    t.add("No known city-state", "Нет знакомых городов-государств")
    t.add("Base cost: [amount]", "Базовая цена: [amount]")
    t.add("Instant action", "Мгновенное действие")
    t.add("Duration: [amount] turns", "Длительность: [amount] ходов")
    t.add("Cooldown: [amount] turns", "Перезарядка: [amount] ходов")
    t.add("Active actions", "Активные действия")
    t.add("Discount", "Скидка")
    t.add("Active", "Действует")
    t.add("Start [action]", "Запустить: [action]")
    t.add("Not your turn", "Сейчас не ваш ход")
    t.add("Choose a city-state", "Выберите город-государство")

    t.section("Engine: religion and victory (KC-11, KC-15)")
    val hints = spec.sectionText("### 9.2.").joinToString(" ")
    val hintMatches = Regex("«([^»]+)»").findAll(hints).map { it.groupValues[1] }.toList()
    t.add("Technocrats do not believe in your gods. They believe in the quarterly report", hintMatches[0])
    t.add("Humanists do not buy your shares. They cannot read reports", hintMatches[1])
    t.add("Holy city", "Святой город")
    t.add("[faction] have won a [victoryType] Victory!", "[faction] одержали победу: [victoryType]!")
    t.add("Winners", "Победители")
    t.add("KM: Destroy all civilizations of the opposing faction", "Уничтожить все крупные цивилизации другой фракции")
    t.add("Unciv: Kotakula Edition", "Unciv: Kotakula Edition")

    t.section("New uniques (section 1.4)")
    t.add("for [faction] faction", "для фракции [faction]")
    t.add("KM: Reveals resources of the opposing faction", "Видны ресурсы чужой фракции (использовать их нельзя)")
    t.add("KM: Reveals [positiveAmount]% of opposing faction military units", "Шпионаж: каждый ход раскрываются [positiveAmount]% боевых юнитов чужой фракции")
    t.add("KM: Reveals all opposing faction units within [positiveAmount] tiles of your cities", "Шпионаж: юниты чужой фракции в радиусе [positiveAmount] клеток от ваших городов видны всегда")
    t.add("KM: Reveals [relativeAmount]% opposing faction military units", "Шпионаж: [relativeAmount] п. п. к доле раскрываемых юнитов чужой фракции")
    t.add("KM: [relativeAmount]% Strength vs opposing faction", "[relativeAmount]% к силе против чужой фракции")
    t.add("KM: [relativeAmount]% Strength vs same faction", "[relativeAmount]% к силе против своей фракции")
    t.add("KM: Gain [amount] [civWideStat] when a unit of the opposing faction is killed", "[amount] [civWideStat] за каждого убитого юнита чужой фракции")
    t.add("KM: Gain [amount] [civWideStat] when conquering a city of the opposing faction", "[amount] [civWideStat] за каждый захваченный город чужой фракции")
    t.add("KM: Trophy: gain [positiveAmount]% of building cost as [civWideStat]", "Трофей: [positiveAmount]% стоимости здания в виде [civWideStat]")
    t.add("KM: Summons best [unitType] unit every [positiveAmount] turns", "Раз в [positiveAmount] ходов призывает лучшего доступного юнита типа [unitType]")
    t.add("KM: Summoned", "Призванный: без содержания, нельзя улучшить и подарить")
    t.add("KM: Expires after [positiveAmount] turns", "исчезает через [positiveAmount] ходов")
    t.add("KM: Faith actions cost [relativeAmount]%", "[relativeAmount]% к стоимости действий веры")
    t.add("KM: [relativeAmount] Faith action slots", "[relativeAmount] слот действий веры")
    t.add("KM: Counts as [positiveAmount] parts for faction victory project", "Считается за [positiveAmount] частей проекта победы")
    t.add("KM: Pillaging yields [positiveAmount]x", "Добыча при разграблении ×[positiveAmount]")
    t.add("KM: Set faction [faction]", "Фракция: [faction]")
    t.add("KM: Gain a free [beliefType] belief", "Основатель религии получает бесплатное верование: [beliefType]")
    t.add("KM: Holy city bonus", "Святой город: +10% производства и +10% золота")
    t.add("Gain [positiveAmount]% of action cost as [civWideStat]", "[positiveAmount]% стоимости действия возвращается в виде [civWideStat]")
    t.add("KM: [amount] Influence with chosen city-state", "[amount] влияния у выбранного города-государства")
    t.add("[amount] City Strength [cityFilter]", "[amount] к силе города [cityFilter]")
    t.add("KM: All units fully heal", "Все юниты полностью излечиваются")

    t.section("Ancient ruins - rewritten texts (Ruins.json)")
    t.add("We have discovered cultural artifacts in the ruins! (+20 culture)", "В руинах найдены культурные артефакты: старые мемы студии Kotakula! (+20 культуры)")
    t.add("A [unit] has joined us!", "К нам присоединился [unit]! Говорит, что стажёр и работает за печеньки")
    t.add("An ancient tribe trained us in their ways of combat!", "Древнее племя провело для нас тренинг по боевому тимбилдингу!")
    t.add("We have found survivors in the ruins! Population added to [cityName].", "В руинах нашлись выжившие стажёры! Население города [cityName] выросло.")
    t.add("We have discovered the lost technology of [techName] in the ruins!", "В руинах найдена потерянная документация: [techName]! Её никто не читал, но теперь это наше")
    t.add("Our unit finds advanced weaponry hidden in the ruins!", "Наш юнит нашёл в руинах оружие из следующего патча!")
    t.add("You find evidence of Barbarian activity. Nearby Barbarian camps are revealed!", "Найдены следы варваров: пустые коробки из-под пиццы. Ближайшие лагеря раскрыты!")
    t.add("We have found a crudely-drawn map in the ruins!", "В руинах найдена карта, нарисованная на салфетке! Масштаб примерный")
    t.add("We have found holy symbols in the ruins, giving us a deeper understanding of religion! (+[faithAmount] Faith)",
        "В руинах найдены святые символы — похоже, старые логотипы студии. Понимание религии углубилось (+[faithAmount] веры)")
    t.add("We have found an ancient prophecy in the ruins, greatly increasing our spiritual connection! (+[faithAmount] Faith)",
        "В руинах найдено древнее пророчество: «Релиз будет в пятницу». Духовная связь окрепла (+[faithAmount] веры)")
}

/**
 * Section 4.2: faction variants of every line of the vanilla Russian translation plus the mod's own lines.
 * Explicit variants written above win; the check of section 14.1 runs on the result.
 */
fun GenContext.addFactionVariants(vanillaRussian: Map<String, String>) {
    val mod = translations.all()
    val all = LinkedHashMap<String, String>(vanillaRussian)
    all.putAll(mod)
    translations.section("Faction variants (section 4.2, generated)")
    for ((key, value) in all) {
        if (key.contains('#')) continue
        val humanist = FactionWording.forHumanist(value)
        if (humanist != value && !translations.contains("$key#Humanist")) translations.add("$key#Humanist", humanist)
        val technocrat = FactionWording.forTechnocrat(value)
        if (technocrat != value && !translations.contains("$key#Technocrat")) translations.add("$key#Technocrat", technocrat)
    }
}
