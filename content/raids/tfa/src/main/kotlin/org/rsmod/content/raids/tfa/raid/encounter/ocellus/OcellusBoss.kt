package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.output.spotanimSpecific
import org.rsmod.api.script.onNpcQueue
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.BOSS_SPOT_SLOT
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.BROKEN
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.CENTRE
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.CORNER_CYCLE
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.NEXT
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.PHASE_THREE
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.PHASE_TWO
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.QUADRANTS
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.QUADRANT_THRESHOLDS
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.SPREAD_IMPACT
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.THRESHOLDS
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.UNTETHER_IMPACT
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.UNTETHER_PERSONAL
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.VOLLEY_END
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.VOLLEY_END_SEQ
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.VOLLEY_START
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.WALK
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.WALKING
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.THRESHOLD
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.plugin.scripts.ScriptContext

class OcellusBoss
@Inject
constructor(
    deps: BossDeps,
    private val encounter: OcellusEncounter,
    private val hpBar: BossHpBarScript,
    npcList: NpcList,
) : BossPluginScript(deps) {
    private val spin = OcellusSpin(deps)
    private val curses = OcellusCurses(deps, npcList)
    private val p1 = OcellusP1Abilities()
    private val p2 = OcellusP2Abilities()
    private val p3 = OcellusP3Abilities(encounter)
    private val attacks = OcellusAttacks(deps, spin)
    private val finalPhase = OcellusFinalAbilities()

    override fun ScriptContext.startup() {
        spin.register()
        curses.register(this)
        OcellusTendrils(deps, encounter.meter).register()
        OcellusBarrage(deps, encounter.meter).register()
        OcellusSlash.register(deps)
        deps.extensionRegistry.register(UNTETHER_PERSONAL) {
            it.target.spotanimSpecific(UNTETHER_PERSONAL_SPOT, slot = UNTETHER_PERSONAL_SLOT)
        }
        deps.extensionRegistry.register(UNTETHER_IMPACT) {
            it.target.spotanim(SPREAD_IMPACT, delay = UNTETHER_IMPACT_DELAY)
        }
        val finalForm = ServerCacheManager.getNpc("npc.tfa_ocellus_p3".asRSCM(RSCMType.NPC))!!
        onNpcQueue(finalForm, "queue.death") { death() }
        BossCombat.register(
            this,
            spec,
            deps,
            onModifyHit = { encounter.of(npc)?.phaseTwo?.modifyHit(this) },
            onHit = {
                curses.reflect(this)
                for (player in OcellusArena.players(npc, deps.playerList)) hpBar.onUpdate(player, npc)
                if (npc.hitpoints <= 0) encounter.of(npc)?.let { deathFx(npc, it) }
            },
        )
    }

    private suspend fun StandardNpcAccess.death() {
        val fight = encounter.of(npc)
        deps.encounterRegistry.of(npc).interrupt(deps.mapClock.cycle)
        fight?.complete(npc)
        noneMode()
        hideAllOps()
        anim(DEATH_SEQ)
        delay(DEATH_REMOVE_DELAY)
        fight?.phaseTwo?.clear()
        deps.npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun deathFx(npc: Npc, fight: OcellusFight) {
        if (fight.dying) return
        fight.dying = true
        npc.infoProtocol.setSpotanim(-1, 0, 0, BOSS_SPOT_SLOT)
        npc.spotanim(DEATH_SKIRT_SPOT, delay = DEATH_FX_DELAY)
        npc.spotanim(DEATH_SPOT, delay = DEATH_FX_DELAY, slot = DEATH_SPOT_SLOT)
        npc.spotanim(DEATH_CRACK_SPOT, delay = DEATH_FX_DELAY, slot = DEATH_CRACK_SLOT)
    }

    override val spec: BossSpec =
        boss("npc.tfa_ocellus_intro", "npc.tfa_ocellus_p1", "npc.tfa_ocellus_p3") {
            phase(WALKING, scriptedMovement = true) { every(VOLLEY_GAP, run(VOLLEY_SLOT)) }
            phase(CORNER_CYCLE, scriptedMovement = true) {}
            phase(CENTRE, scriptedMovement = true, lockMovement = true) {}
            phase(BROKEN, scriptedMovement = true, lockMovement = true) {}
            phase(PHASE_TWO, scriptedMovement = true, lockMovement = true) {}
            QUADRANTS.forEachIndexed { quadrant, name ->
                phase(name, lockMovement = true) {
                    if (quadrant == QUADRANTS.lastIndex) {
                        weightedSelectorRandom { +random(CURSES, weight = 1) }
                    } else {
                        weightedSelectorRandom { +random(ATTACK_CYCLE, weight = 1) }
                        forceWhen(
                            varnAtLeast(OcellusVarns.SPECIAL_CYCLES, CYCLES_PER_SPECIAL),
                            special(quadrant),
                        )
                    }
                    if (quadrant > 0) every(1, p3.riftWave(quadrant))
                }
            }

            ability(START, p1.start())
            ability(WALK, p1.walk())
            ability(VOLLEY_SLOT, p1.volleySlot())
            ability(VOLLEY_START, p1.volley(VOLLEY_START_SEQ))
            ability(VOLLEY_END, p1.volley(VOLLEY_END_SEQ))
            ability(CORNER_CYCLE, p1.cornerCycle())
            ability(CENTRE, p1.centre())
            ability(NEXT, p1.next())
            ability(BREAK, p1.shieldBreak())
            ability(PHASE_TWO, p2.phaseTwo())
            ability(PHASE_THREE, p3.phaseThree())
            ability(ATTACK_CYCLE, attacks.attackCycle(), attackDelay = ATTACK_CYCLE_CAP)
            ability(CURSES, finalPhase.curses(), attackDelay = ATTACK_CYCLE_CAP)
            for (quadrant in 0 until QUADRANTS.lastIndex) {
                ability(special(quadrant), attacks.special(quadrant), attackDelay = ATTACK_CYCLE_CAP)
                ability(destroy(quadrant), p3.destroyQuadrant(quadrant + 1))
            }
            ability(SKIP_TO_THREE, p2.skipToPhaseThree())

            triggers {
                val skipping = varnIs(THRESHOLD, OcellusVarns.SKIP_TO_PHASE_THREE)
                val shielded =
                    QUADRANTS.fold(!Condition.InPhase(PHASE_TWO)) { acc, quadrant ->
                        acc and !Condition.InPhase(quadrant)
                    }
                on(skipping and shielded) runs
                    sequence(
                        interrupt(),
                        p1.dropProtection(),
                        external(OcellusPhaseTwo.ENTER),
                        run(SKIP_TO_THREE),
                    )
                on(skipping and Condition.InPhase(PHASE_TWO)) runs
                    sequence(interrupt(), run(SKIP_TO_THREE))
                on(Condition.InPhase(WALKING) and Condition.Not(skipping)) runs run(START)
                on(Condition.HpExact(1) and shielded) runs run(BREAK)
                QUADRANT_THRESHOLDS.forEachIndexed { quadrant, fraction ->
                    val crossed =
                        Condition.InPhase(QUADRANTS[quadrant]) and
                            Condition.HpBelow(fraction, inclusive = true)
                    on(crossed) runs
                        sequence(
                            interrupt(),
                            transitionTo(QUADRANTS[quadrant + 1]),
                            run(destroy(quadrant)),
                        )
                }
                THRESHOLDS.forEachIndexed { stage, fraction ->
                    val crossedWhileWalking =
                        varnIs(THRESHOLD, stage) and
                            Condition.HpBelow(fraction, inclusive = true) and
                            Condition.InPhase(WALKING)
                    on(crossedWhileWalking) runs sequence(interrupt(), p1.enterCentre(stage + 1))
                }
            }
        }

    private companion object {
        const val START = "start"
        const val VOLLEY_SLOT = "volley_slot"
        const val BREAK = "shield_break"
        const val ATTACK_CYCLE = "attack_cycle"
        const val ATTACK_CYCLE_CAP = 20
        fun special(quadrant: Int) = "special_${quadrant + 1}"
        const val CYCLES_PER_SPECIAL = 2
        const val UNTETHER_PERSONAL_SPOT = "spotanim.vfx_ocellus_magic_projanim02"
        const val UNTETHER_PERSONAL_SLOT = 2
        const val UNTETHER_IMPACT_DELAY = 120
        fun destroy(quadrant: Int) = "destroy_quadrant_${quadrant + 1}"
        const val SKIP_TO_THREE = "skip_to_three"
        const val VOLLEY_START_SEQ = "seq.npc_ocellus_phase1_basic_projectile_start"
        const val CURSES = "curses"
        const val DEATH_SEQ = "seq.npc_ocellus_phase3_death"
        const val DEATH_REMOVE_DELAY = 16
        const val DEATH_FX_DELAY = 1
        const val DEATH_SPOT_SLOT = 5
        const val DEATH_CRACK_SLOT = 3
        const val DEATH_SKIRT_SPOT = "spotanim.vfx_ocellus_death_skirt_spotanim01"
        const val DEATH_SPOT = "spotanim.vfx_ocellus_death"
        const val DEATH_CRACK_SPOT = "spotanim.vfx_ocellus_death_crack_spotanim01"
        const val VOLLEY_GAP = 5
    }
}
