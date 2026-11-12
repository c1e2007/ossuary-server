package org.rsmod.content.raids.tfa.raid.encounter.dreadnoth

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec

class JellyBoss @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    override val spec: BossSpec =
        boss("npc.tfa_jelly_minion") {
            stats(attackRate = ATTACK_RATE)

            val strike =
                ability(
                    "strike",
                    sequence(
                        anim("seq.jelly_attack"),
                        onEach(
                            CurrentTarget,
                            hit {
                                damage(0..MAX_HIT).roll()
                                type(Melee)
                                onHit(statDrain("stat.prayer", amount = PRAYER_DRAIN))
                            },
                        ),
                    ),
                )

            phase("alive") {
                weightedSelectorRandom { +random(strike, weight = 1, requires = WithinMeleeRange) }
            }
        }

    private companion object {
        const val ATTACK_RATE = 3
        const val MAX_HIT = 17
        const val PRAYER_DRAIN = 1
    }
}
