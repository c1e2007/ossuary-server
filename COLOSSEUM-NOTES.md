# Colosseum notes (branch work/colosseum)

## Fixed (commit ef8356c)

- Enemies respawned across waves. Cause: `npcRepo.add(npc, Int.MAX_VALUE)` leaves respawns enabled.
  Fix: `npc.respawns = false` immediately after the add in `spawn()`.
- Enemies lost their target and reverted to wandering. Cause: the engine resets an NPC's mode to the
  default wandering mode whenever an interaction is cancelled, and nothing re-applied a target.
  Fix: `waveTick()` validates every live enemy each tick and re-issues `apPlayer2` if the enemy is
  not in `apPlayer2` mode, is not targeting the player, or the player is no longer a valid target.
- Minimus and the seated Sol wandered. Cause: no pinning. Fix: `npc.moveRestrict = MoveRestrict.NoMove`
  at all three spawn points (lobby spawn, `begin()`, `completeWave`).

## Pinning approach - deviation, needs review

- ToA pins static NPCs through cache config (`defaultMode = "None"`, `moveRestrict = "NoMove"`,
  `wanderRange = 0`). That is the canonical route but requires a cache rebuild.
- `npc.noneMode()` was rejected because the engine calls `defaultMode()` whenever an interaction ends,
  which overwrites it.
- Runtime `npc.moveRestrict = NoMove` was used instead. It matches the ToA config behaviour and
  survives mode resets, but it is a runtime override rather than a cache property.
- Action: decide whether to migrate to cache config on the next cache rebuild.
- No Gauntlet content exists in the repo to compare against.

## Targeting approach - clarification

- `apPlayer2` is already the call that produces real attacks, not just an approach. The boss framework
  runs its combat tick from both `apPlayer2` and `opPlayer2`; the framework's own re-target code and
  ToA's Baba fight use `apPlayer2`, ToA's crocodiles use `opPlayer2`. `apPlayer2` was kept.
- The defect was never the call - it was that nothing re-applied it after a cancellation.

## Outstanding from the test run - NOT STARTED

1. Aggro range is too short. Enemies only aggress within a limited range. Any NPC spawned in a wave
   must aggress the player regardless of distance in the arena.
2. Minimus wanders by the reward chest. He must hold his spot next to the chest without moving.
3. Minimus spawns instantly. He must not spawn for 5 ticks between waves.
4. Rewards do not render in the between-wave loot section or the next-wave loot section.
5. The reward chest on claim is a plain dialogue box, not the actual chest interaction/interface.

## Not verified

- Nothing has been run in the client. `spotlessApply` and `classes -x test` both pass; runtime
  behaviour is confirmed by reading code only.
- Dead-enemy cleanup with respawns off is reasoned from `isSlotAssigned`, not observed.

## Working tree

- `ColosseumData.kt` shows as modified but `git diff --ignore-cr-at-eol` and `git diff -w` are both
  empty: line endings only. Left uncommitted.
