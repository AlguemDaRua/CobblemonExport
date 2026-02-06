package alguemdarua.cobbleexport

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.api.pokemon.stats.Stats

object PokemonMapper {

    fun toMap(pokemon: Pokemon): Map<String, Any?> {
        return mapOf(
            "uuid" to pokemon.uuid.toString(),
            "species" to pokemon.species.name.lowercase(),
            "nickname" to (pokemon.nickname ?: pokemon.species.name),
            "level" to pokemon.level,
            "shiny" to pokemon.shiny,
            "gender" to pokemon.gender.toString(),
            "ability" to pokemon.ability.name,
            "nature" to pokemon.nature.name.path,
            "friendship" to pokemon.friendship,
            "caughtBall" to pokemon.caughtBall.name.path,

            // FIXED: 'hp' is deprecated, use 'currentHealth'
            "current_hp" to pokemon.currentHealth,
            "max_hp" to pokemon.maxHealth,

            // Stats
            "stats" to getStatsMap(pokemon),

            // IVs
            "ivs" to mapOf(
                "hp" to (pokemon.ivs[Stats.HP] ?: 0),
                "atk" to (pokemon.ivs[Stats.ATTACK] ?: 0),
                "def" to (pokemon.ivs[Stats.DEFENCE] ?: 0),
                "spa" to (pokemon.ivs[Stats.SPECIAL_ATTACK] ?: 0),
                "spd" to (pokemon.ivs[Stats.SPECIAL_DEFENCE] ?: 0),
                "spe" to (pokemon.ivs[Stats.SPEED] ?: 0)
            ),

            // EVs
            "evs" to mapOf(
                "hp" to (pokemon.evs[Stats.HP] ?: 0),
                "atk" to (pokemon.evs[Stats.ATTACK] ?: 0),
                "def" to (pokemon.evs[Stats.DEFENCE] ?: 0),
                "spa" to (pokemon.evs[Stats.SPECIAL_ATTACK] ?: 0),
                "spd" to (pokemon.evs[Stats.SPECIAL_DEFENCE] ?: 0),
                "spe" to (pokemon.evs[Stats.SPEED] ?: 0)
            ),

            "moves" to pokemon.moveSet.getMoves().map { it.name }
        )
    }

    // Helper to reduce duplicate code
    private fun getStatsMap(pokemon: Pokemon): Map<String, Int> {
        return mapOf(
            "hp" to pokemon.getStat(Stats.HP),
            "atk" to pokemon.getStat(Stats.ATTACK),
            "def" to pokemon.getStat(Stats.DEFENCE),
            "spa" to pokemon.getStat(Stats.SPECIAL_ATTACK),
            "spd" to pokemon.getStat(Stats.SPECIAL_DEFENCE),
            "spe" to pokemon.getStat(Stats.SPEED)
        )
    }
}