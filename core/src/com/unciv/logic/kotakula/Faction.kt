package com.unciv.logic.kotakula

import com.unciv.logic.IsPartOfGameInfoSerialization
import yairm210.purity.annotations.Pure

/**
 * Kotakula Multiverse (KC-01): the side a civilization is on.
 *
 * - [Technocrat] — science, machines and corporations.
 * - [Humanist] — magic, mana and faith.
 * - [None] — no side: city-states (diplomatically), barbarians, spectators and every civilization in
 *   rulesets without factions. Old saves deserialize to [None] because it is the field default.
 *
 * Serialized by name, like every enum in GameInfo.
 */
enum class Faction : IsPartOfGameInfoSerialization {
    Technocrat,
    Humanist,
    None;

    /** The faction this one is at eternal war with, [None] for [None]. */
    @Pure
    fun opposite(): Faction = when (this) {
        Technocrat -> Humanist
        Humanist -> Technocrat
        None -> None
    }

    @Pure
    fun isReal() = this != None

    /** Suffix used for faction-specific translation keys and image names (section 4.1 of the spec). */
    @Pure
    fun keySuffix(): String = if (this == None) "" else "#$name"

    companion object {
        /** The two playable factions, in a fixed order */
        val playable = listOf(Technocrat, Humanist)

        @Pure
        fun safeValueOf(name: String?): Faction? = entries.firstOrNull { it.name == name }
    }
}
