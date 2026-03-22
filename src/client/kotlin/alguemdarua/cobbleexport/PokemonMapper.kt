package alguemdarua.cobbleexport

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import net.minecraft.registry.Registries

object PokemonMapper {

    // Stat display order matching Showdown convention
    private val STAT_ORDER = listOf(
        Stats.HP to "HP",
        Stats.ATTACK to "Atk",
        Stats.DEFENCE to "Def",
        Stats.SPECIAL_ATTACK to "SpA",
        Stats.SPECIAL_DEFENCE to "SpD",
        Stats.SPEED to "Spe"
    )

    /**
     * Converts a Pokemon to Pokémon Showdown text format.
     *
     * Example output:
     *   Mudkip (M) @ Leftovers
     *   Ability: Torrent
     *   Tera Type: Water
     *   Level: 50
     *   EVs: 252 HP / 4 Def / 252 SpD
     *   Calm Nature
     *   IVs: 0 Atk
     *   - Scald
     *   - Toxic
     *   - Recover
     *   - Protect
     */
    fun toShowdown(pokemon: Pokemon): String {
        val lines = mutableListOf<String>()

        // --- LINE 1: Name (Species) (Gender) @ Item ---
        lines.add(buildFirstLine(pokemon))

        // --- Ability ---
        lines.add("Ability: ${formatName(pokemon.ability.name)}")

        // --- Tera Type ---
        lines.add("Tera Type: ${formatName(pokemon.teraType.name.lowercase())}")

        // --- Level (omit if 100, Showdown default) ---
        if (pokemon.level != 100) {
            lines.add("Level: ${pokemon.level}")
        }

        // --- Shiny ---
        if (pokemon.shiny) {
            lines.add("Shiny: Yes")
        }

        // --- EVs (always show all EVs, even if 0, for clarity) ---
        val evParts = STAT_ORDER.map { (stat, label) ->
            val value = pokemon.evs[stat] ?: 0
            "$value $label"
        }
        lines.add("EVs: ${evParts.joinToString(" / ")}")

        // --- Nature (uses effectiveNature = minted nature if minted, otherwise original) ---
        val natureName = pokemon.effectiveNature.name.toString()
            .lowercase()
            .substringAfter(":")
            .replaceFirstChar { it.uppercase() }
        lines.add("$natureName Nature")

        // --- IVs (uses getEffectiveBattleIV = accounts for candies + hyper training) ---
        // Always show all IVs for clarity
        val ivParts = STAT_ORDER.map { (stat, label) ->
            val value = pokemon.ivs.getEffectiveBattleIV(stat)
            "$value $label"
        }
        lines.add("IVs: ${ivParts.joinToString(" / ")}")

        // --- Moves ---
        for (move in pokemon.moveSet.getMoves()) {
            lines.add("- ${formatMoveName(move.name)}")
        }

        return lines.joinToString("\n")
    }

    /**
     * First line: Nickname (Species) (Gender) @ Item
     * - If nickname == species name, just show species
     * - Gender: (M), (F), or omitted for genderless
     * - Item: @ ItemName, or omitted if none
     */
    private fun buildFirstLine(pokemon: Pokemon): String {
        val speciesName = formatName(pokemon.species.name.lowercase())
        val nickname = pokemon.nickname?.string

        val sb = StringBuilder()

        if (nickname != null && nickname != speciesName) {
            sb.append("$nickname ($speciesName)")
        } else {
            sb.append(speciesName)
        }

        // Gender
        when (pokemon.gender.toString()) {
            "MALE" -> sb.append(" (M)")
            "FEMALE" -> sb.append(" (F)")
        }

        // Held item
        val item = getHeldItemName(pokemon)
        if (item != null) {
            sb.append(" @ ${formatName(item)}")
        }

        return sb.toString()
    }

    private fun getHeldItemName(pokemon: Pokemon): String? {
        val stack = pokemon.heldItem()
        if (stack.isEmpty) return null
        return try {
            Registries.ITEM.getId(stack.item).path
        } catch (_: Exception) {
            stack.item.name.string.lowercase().replace(" ", "_")
        }
    }

    /**
     * Converts internal names like "special_attack" or "leftovers" to "Special Attack" or "Leftovers".
     */
    private fun formatName(name: String): String {
        return name.split("_", "-").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Converts internal move names to proper display names.
     * Handles: "rockthrow" → "Rock Throw", "thunderpunch" → "Thunder Punch"
     *
     * Uses a known compound words list + fallback camelCase-style splitting.
     */
    private fun formatMoveName(internalName: String): String {
        // Check override map first for tricky names
        MOVE_NAME_OVERRIDES[internalName.lowercase()]?.let { return it }

        // Try splitting known compound words
        val name = internalName.lowercase()
        for (word in KNOWN_SECOND_WORDS) {
            if (name.endsWith(word) && name.length > word.length) {
                val first = name.substringBefore(word)
                return "${first.replaceFirstChar { it.uppercase() }} ${word.replaceFirstChar { it.uppercase() }}"
            }
        }

        // Fallback: just capitalize the whole thing as-is
        return name.replaceFirstChar { it.uppercase() }
    }

    // Common second-words in Pokémon move names for splitting compound words
    private val KNOWN_SECOND_WORDS = listOf(
        "throw", "smash", "gun", "sight", "claw", "fang", "punch", "kick", "beam",
        "bolt", "blast", "ball", "wave", "pulse", "dance", "song", "tail", "whip",
        "seed", "leaf", "storm", "wind", "rain", "snow", "hail", "quake", "slide",
        "fall", "dive", "fly", "rush", "charge", "force", "power", "guard", "shield",
        "edge", "stone", "rock", "slam", "strike", "attack", "defense", "speed",
        "trick", "swap", "room", "terrain", "field", "trap", "web", "spin", "turn",
        "screech", "cry", "roar", "growl", "bite", "breath", "fire", "flame",
        "thunder", "shock", "spark", "freeze", "chill", "ice", "snow", "mist",
        "fog", "cloud", "shadow", "light", "flash", "glow", "ray", "burst",
        "bomb", "shot", "arrow", "blade", "sword", "cut", "slash", "chop",
        "split", "break", "crush", "press", "squeeze", "grip", "hold", "lock",
        "block", "wall", "screen", "coat", "veil", "cloak", "wrap", "bind",
        "dew", "focus", "first", "rage", "noise", "sound"
    ).sortedByDescending { it.length }

    // Override map for move names that can't be split by simple rules
    private val MOVE_NAME_OVERRIDES = mapOf(
        "rockthrow" to "Rock Throw",
        "rocksmash" to "Rock Smash",
        "watergun" to "Water Gun",
        "foresight" to "Foresight",
        "thunderbolt" to "Thunderbolt",
        "thunderpunch" to "Thunder Punch",
        "thundershock" to "Thunder Shock",
        "thunderwave" to "Thunder Wave",
        "firepunch" to "Fire Punch",
        "firespin" to "Fire Spin",
        "fireblast" to "Fire Blast",
        "flamethrower" to "Flamethrower",
        "icebeam" to "Ice Beam",
        "icepunch" to "Ice Punch",
        "iceshard" to "Ice Shard",
        "solarbeam" to "Solar Beam",
        "solarblade" to "Solar Blade",
        "shadowball" to "Shadow Ball",
        "shadowclaw" to "Shadow Claw",
        "shadowpunch" to "Shadow Punch",
        "shadowsneak" to "Shadow Sneak",
        "darkpulse" to "Dark Pulse",
        "aurasphere" to "Aura Sphere",
        "focusblast" to "Focus Blast",
        "focuspunch" to "Focus Punch",
        "energyball" to "Energy Ball",
        "seedbomb" to "Seed Bomb",
        "leafblade" to "Leaf Blade",
        "leafstorm" to "Leaf Storm",
        "moonblast" to "Moonblast",
        "moonlight" to "Moonlight",
        "earthquake" to "Earthquake",
        "earthpower" to "Earth Power",
        "stoneedge" to "Stone Edge",
        "rockslide" to "Rock Slide",
        "rocktomb" to "Rock Tomb",
        "stealthrock" to "Stealth Rock",
        "ironhead" to "Iron Head",
        "irontail" to "Iron Tail",
        "irondefense" to "Iron Defense",
        "flashcannon" to "Flash Cannon",
        "steelwing" to "Steel Wing",
        "metalclaw" to "Metal Claw",
        "closecombat" to "Close Combat",
        "crosschop" to "Cross Chop",
        "crosspoison" to "Cross Poison",
        "dragonpulse" to "Dragon Pulse",
        "dragonclaw" to "Dragon Claw",
        "dragondance" to "Dragon Dance",
        "dragonbreath" to "Dragon Breath",
        "dragonrush" to "Dragon Rush",
        "dragontail" to "Dragon Tail",
        "outrage" to "Outrage",
        "dracometeor" to "Draco Meteor",
        "sludgebomb" to "Sludge Bomb",
        "sludgewave" to "Sludge Wave",
        "poisonjab" to "Poison Jab",
        "psychic" to "Psychic",
        "psyshock" to "Psyshock",
        "psywave" to "Psywave",
        "zenheadbutt" to "Zen Headbutt",
        "futuresight" to "Future Sight",
        "calmmind" to "Calm Mind",
        "nastyplot" to "Nasty Plot",
        "swordsdance" to "Swords Dance",
        "bulkup" to "Bulk Up",
        "shellsmash" to "Shell Smash",
        "quiverdance" to "Quiver Dance",
        "dragondance" to "Dragon Dance",
        "willowisp" to "Will-O-Wisp",
        "toxicspikes" to "Toxic Spikes",
        "stickyweb" to "Sticky Web",
        "rapidspin" to "Rapid Spin",
        "uturn" to "U-turn",
        "voltswitch" to "Volt Switch",
        "flipturn" to "Flip Turn",
        "trickroom" to "Trick Room",
        "tailwind" to "Tailwind",
        "bravebird" to "Brave Bird",
        "flareblitz" to "Flare Blitz",
        "wildcharge" to "Wild Charge",
        "headsmash" to "Head Smash",
        "doubleedge" to "Double-Edge",
        "bodyslam" to "Body Slam",
        "bodypress" to "Body Press",
        "heavyslam" to "Heavy Slam",
        "heatwave" to "Heat Wave",
        "airslash" to "Air Slash",
        "bugbuzz" to "Bug Buzz",
        "signalbeam" to "Signal Beam",
        "megahorn" to "Megahorn",
        "xscissor" to "X-Scissor",
        "nightslash" to "Night Slash",
        "nightshade" to "Night Shade",
        "suckerpunch" to "Sucker Punch",
        "knockoff" to "Knock Off",
        "foulplay" to "Foul Play",
        "scald" to "Scald",
        "surf" to "Surf",
        "hydropump" to "Hydro Pump",
        "aquajet" to "Aqua Jet",
        "aquatail" to "Aqua Tail",
        "waterfall" to "Waterfall",
        "liquidation" to "Liquidation",
        "muddywater" to "Muddy Water",
        "highhorsepower" to "High Horsepower",
        "superpower" to "Superpower",
        "drainpunch" to "Drain Punch",
        "machpunch" to "Mach Punch",
        "bulletpunch" to "Bullet Punch",
        "vacuumwave" to "Vacuum Wave",
        "extremespeed" to "Extreme Speed",
        "quickattack" to "Quick Attack",
        "protect" to "Protect",
        "detect" to "Detect",
        "substitute" to "Substitute",
        "recover" to "Recover",
        "roost" to "Roost",
        "synthesis" to "Synthesis",
        "toxic" to "Toxic",
        "spore" to "Spore",
        "sleeppowder" to "Sleep Powder",
        "stunspore" to "Stun Spore",
        "leechseed" to "Leech Seed",
        "gigadrain" to "Giga Drain",
        "megadrain" to "Mega Drain",
        "grassknot" to "Grass Knot",
        "powerwhip" to "Power Whip",
        "woodhammer" to "Wood Hammer",
        "hornleech" to "Horn Leech",
        "playrough" to "Play Rough",
        "dazzlinggleam" to "Dazzling Gleam",
        "drainingkiss" to "Draining Kiss",
        "mysticalfire" to "Mystical Fire",
        "fierydance" to "Fiery Dance",
        "lavaplume" to "Lava Plume",
        "overheat" to "Overheat",
        "blueflare" to "Blue Flare",
        "sacredfire" to "Sacred Fire",
        "vcreate" to "V-create",
        "surf" to "Surf",
        "thunderclap" to "Thunderclap",
        "voltabsorb" to "Volt Absorb",
        "waterpulse" to "Water Pulse",
        "darkestlariat" to "Darkest Lariat",
        "spiritshackle" to "Spirit Shackle",
        "poltergeist" to "Poltergeist",
        "phantomforce" to "Phantom Force",
        "hex" to "Hex",
        "hypnosis" to "Hypnosis",
        "dreameater" to "Dream Eater",
        "trickorttreat" to "Trick-or-Treat",
        "stoneaxe" to "Stone Axe",
        "headlongrush" to "Headlong Rush",
        "wavecrash" to "Wave Crash",
        "bitterblade" to "Bitter Blade",
        "ragingbolt" to "Raging Bolt",
        "psyblade" to "Psyblade",
        "makeitrain" to "Make It Rain",
        "bloodmoon" to "Blood Moon",
        "ivycudgel" to "Ivy Cudgel",
        "matchagotcha" to "Matcha Gotcha",
        "syrupbomb" to "Syrup Bomb",
        "electrodrift" to "Electro Drift",
        "icespinner" to "Ice Spinner",
        "trailblaze" to "Trailblaze",
        "ragefist" to "Rage Fist",
        "lastrespects" to "Last Respects",
        "populationbomb" to "Population Bomb",
        "tidalwave" to "Tidal Wave",
        "lifedew" to "Life Dew",
        "laserfocus" to "Laser Focus",
        "mefirst" to "Me First"
    )
}