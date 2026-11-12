# Yama: deviations from the reference

Reference: `RSPSApp/tsps`, `server/plugins/bosses/yama/*.Yama.js`, `docs/yama.md` and its data file
`plugins/bosses/data/yama.json`. Each entry gives the reference file and function, what it does, why
it does not map onto this engine, the closest equivalent the engine has, and the substitute used.
**Reduction** means the substitute is a real loss of behaviour.

## Combat

1. **Stance defence bonuses** (`Fight.Yama.js` `setStance`). Sets `yama.setDefenceBonusOverrides(...)`:
   magic defence -30 against a melee user and +60 against a mage, with phase 3 raising every bonus.
   `Npc` has no per-npc bonus override (its bonuses come from the cache type). Closest: the
   modify-hit hook (`onModifyNpcHit`, passed as `onModifyHit`). Substitute: magic hits are scaled by
   (64-30)/(64+60), about 0.27, while he stands in the magic stance, and every hit by 0.74 in phase 3
   (the reference's ~35 percent higher bonuses). **Reduction**: a damage factor, not a hit-chance
   change; melee and ranged defence are not stance-dependent here.
2. **Stance movement and reach** (`Fight.Yama.js` `YamaCombat.attackDistance`, `setBlockMovement`). After a melee hit he chases; after magic or ranged he stands. His attacks run on the fight timer wherever the challenger stands. Closest: `NpcApproach.ChaseToMelee` (keeps routing while the script casts from range) with `apRangeOverride` 32, which covers the arena so the combat trigger always fires. Substitute: `Normal` movement plus `ChaseToMelee` in the melee stance, `NoMove` plus `StopInRange` when standing and in phase 3. Equivalent in effect.
3. **Combat target call** (`Fight.Yama.js`, the task step 17). The task asks for `opPlayer2`, which only fires when adjacent. Substitute: `apPlayer2`, re-issued every tick in `YamaTargets`.
4. **Attack timing** (`Fight.Yama.js` `fightTick`). The reference runs its own per-tick attack timer.
   Substitute: the boss framework drives one `turn` external at `attackRate` 8, 8 and 7, with
   `suppressAttacks` for the first attack of a phase (5, 4 and 3 ticks). The rotation is the
   reference's (deterministic), so it is an `external`, not `weightedSelectorRandom`.
5. **Phase changes** (`Fight.Yama.js`). The reference scripts the Judge at his next attack after
   1666 and 833 hitpoints. Substitute: scripted `transitionTo` from `YamaFight`, no `entryHp`.
6. **Forced accurate and max hits** (`Flares.Yama.js` `flareHitRoll`, `Judge.Yama.js` `judgeHitRoll`
   and `surgeHitRoll`). The reference sets `roll.forceAccurate` and `forceMaxHit` on flares, the
   Judge and fire surges. The modify-hit hook exposes `hit.damage` and `hit.type` but not the
   attacker's maximum. Closest: that hook. Substitute: a would-be miss lands as 1. **Reduction**: it
   never misses, but is not the max hit.
7. **Standard ranged only** (`Flares.Yama.js` `NOT_STANDARD_RANGED`). Excludes darts, knives, thrown
   axes, chinchompas, blowpipes from the sure-hit rule. The hook has no weapon name. Dropped.
   **Reduction**: every ranged hit never misses on a flare.
8. **Water spell only** (`Judge.Yama.js` `surgeCanAttack`, `castsWater`). Only water spells can
   attack a fire surge. This engine has no can-attack hook and the hit event does not identify the
   spell. Substitute: any magic hit puts a surge out (damage 1 minimum), any other hit does 0.
   **Reduction**.
9. **Demonbane vulnerability** (`Fight.Yama.js`, `Flares.Yama.js` `combat:demonbane-vulnerability`
   1.2 and 2.0). The reference attribute is read by its own demonbane effects. This engine has
   `DemonbaneChecks.isDemonbane` for hits. Substitute: a demonbane hit is scaled by 1.2 on Yama and
   2.0 on flares in the same modify hooks, on top of whatever the engine already does.
10. **Hits through prayer** (`Hits.Yama.js` `strike`). Rolls a styled hit and clamps it to
    `throughPrayer` when the protection prayer is up, bypassing the engine's prayer block. The
    standard player hit modifier blocks the full amount. Substitute: the clamp is applied in
    `YamaHits.strike`, and a prayed hit uses `NoopPlayerHitModifier` so the block is not doubled.
    That skips the standard modifier's event publish for prayed hits.
11. **Poison severity** (`Hits.Yama.js` `poison`, `CombatFactory.poisonEntity(player, hit * 5)`).
    Substitute: `CombatEffects.poison`, if present (see the build notes in the commit), at the
    engine's own severity. **Reduction** if severity is not honoured.
12. **Root without freeze immunity** (`Hits.Yama.js` `root`). Substitute: `CombatEffects.freeze`, which
    may grant freeze immunity afterwards. **Reduction**.
13. **Hit marks** (`Hits.Yama.js` `damage`, `HitMask.RED` and `BLUE`). Typeless damage uses the
    engine's `HitType.Typeless` hitmark group.

## Instance and presentation

14. **11x7 chunk template with a plane-1 mask** (`Domain.Yama.js` `copyDomain`). The engine copies
    whole map squares. The arena crosses squares (22,157) and (23,157), so `copyRegions` copies both.
    Extra terrain and all plane-1 content come along. Harmless, not identical.
15. **Entry cinematic** (`Common.Yama.js` `travel`). Fade out with a message box, minimap off, move at
    tick 2, fade in, busy varbit. Substitute: `withInstanceEnterTransition` and the Judge's
    fall/land use `Cinematic.fadeOverlay`. Timing differs slightly; the busy varbit is not set.
16. **Domain area behaviour** (`Domain.Yama.js` `YamasDomain`: `isMulti`, `allowSummonPet`).
    Substitute: the multiway varbit is set on entry and cleared on leave. Pets are not blocked.
    **Reduction**.
17. **Logout and death recovery** (`Domain.Yama.js` `recoverOnLogin`, `respawnByVoice`,
    `yamaGloats`). Substitute: the instance settings row's exit coordinate is the Voice's respawn
    tile, and `InstancePlayerLeaveEvent` ends the fight and records an escape. Yama's death line and
    soul debt are not implemented. **Reduction**.
18. **Held items and fee** (`Voice.Yama.js` `holdForVoice`, `retrievalFee`, `graveByVoice`). Skipped:
    the task asks only for the entry path.
19. **Dialogue** (`Voice.Yama.js`, `Throne.Yama.js`). The reference's dialogue is data-driven and not in
    the 16 files. The text is the Wiki transcripts. Conditional lines (Icthlarin's Little Helper,
    Ironman) use the neutral variant. The additional-contracts questions and the hiscores record
    are skipped. **Reduction**.
20. **Sounds and jingles** (`Hits.sound`, `sendJingle`, music unlock event). Not sent. Music 280 is
    played on entry. **Reduction**.
21. **Boss HUD** (`Fight.Yama.js` `showHud`, `boss-hud:*` events). The engine's own boss bar plugin
    shows Yama's bar from combat; it is not driven explicitly.
22. **Kill count line and personal best** (`Fight.Yama.js` `count`, `sendBossKillCount`,
    `boss:personal-best`). The kill count is `varp.total_yama_kills`; the line and the personal best
    are chat messages and a persistent attribute, not the kill-count interface or the event.
23. **Stepping stones** (`Judge.Yama.js` `jump`, `stayToJump`). The reference jumps from where the player stands. Substitute: an approach-range loc trigger (range 4) on every action slot, because the stone loc's action slot is not in the repo's dumps, then the engine's `exactMove` with the jump animation. Equivalent in effect.
24. **Fire surge movement** (`Judge.Yama.js` `moveSurges`, `npc.exactMove`). Substitute: teleport plus a
    one-tick `pendingExactMove` glide, as Vardorvis's axes do.
25. **Voice placement** (`docs/yama.md`: the Voice stands on plane 0). `chasm_of_fire.toml` had him on plane 1, where a player on plane 0 cannot reach him. Fixed: the spawn is now on plane 0 at (1439, 10079). The black demons and the other demons the reference also moved are left as they were.
26. **A Kingdom Divided gate** (`Domain.Yama.js` `canEnter`). The quest is not implemented here;
    the gate uses the assumed key `quest_akd` and so passes only under an assume-completed policy.
27. **Flames Pass and Quick Pass** (`Fight.Yama.js` `passFlames`). Fleeing through the flames to
    escape: see the commit that adds it.

## Not ported, as the task says

Contracts, party play, soul debt, achievements, the dossier's rite (its use is a message), and the
Voice's held items.
