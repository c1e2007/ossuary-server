package org.rsmod.api.bosses.runtime

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class BossExtensionRegistryTest {
    private val npc =
        Npc(NpcServerType(id = 1, name = "Boss", size = 1, hitpoints = 100), CoordGrid(3200, 3200))
    private val player = Player()
    private val tile = CoordGrid(3205, 3205)

    @Test
    fun `context handlers receive the resolved tile`() {
        val registry = BossExtensionRegistry()
        var received: BossExtensionContext? = null
        registry.register("place_rock") { ctx -> received = ctx }
        registry.invoke("place_rock", null, npc, player, params = "rock", tile = tile)
        val ctx = checkNotNull(received)
        assertSame(npc, ctx.npc)
        assertSame(player, ctx.target)
        assertEquals("rock", ctx.params)
        assertEquals(tile, ctx.tile)
    }

    @Test
    fun `classic handlers still receive npc, target and params`() {
        val registry = BossExtensionRegistry()
        var params: Any? = null
        registry.register("classic") { _, n, t, p ->
            assertSame(npc, n)
            assertSame(player, t)
            params = p
        }
        registry.invoke("classic", null, npc, player, params = 3, tile = tile)
        assertEquals(3, params)
    }

    @Test
    fun `a bound npc resolves its own scope before the shared handler`() {
        val registry = BossExtensionRegistry()
        val other =
            Npc(NpcServerType(id = 1, name = "Boss", size = 1, hitpoints = 100), CoordGrid(3200, 3200))
        val calls = mutableListOf<String>()
        registry.register("shared") { calls += "global" }
        registry.register("only_global") { calls += "only_global" }
        val scope = BossExtensionRegistry()
        scope.register("shared") { calls += "scoped" }
        registry.bind(npc, scope)

        registry.invoke("shared", null, npc, player, null)
        registry.invoke("shared", null, other, player, null)
        registry.invoke("only_global", null, npc, player, null)
        registry.unbind(npc)
        registry.invoke("shared", null, npc, player, null)

        assertEquals(listOf("scoped", "global", "only_global", "global"), calls)
    }

    @Test
    fun `unknown handlers fail loudly`() {
        assertThrows<IllegalStateException> {
            BossExtensionRegistry().invoke("missing", null, npc, player, null)
        }
    }
}
