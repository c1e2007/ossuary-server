package org.rsmod.api.player

import net.rsprot.protocol.game.outgoing.sound.MidiSongV2
import org.rsmod.game.entity.Player

/**
 * Plays the song with the raw cache [id]. Revision 242 replaced the old id-taking `midiSong` with
 * gameval-name and [MidiType] overloads; boss specs still carry a song id, so this keeps that path.
 */
public fun Player.midiSongId(
    id: Int,
    fadeOutDelay: Int = 0,
    fadeOutSpeed: Int = 0,
    fadeInDelay: Int = 0,
    fadeInSpeed: Int = 0,
) {
    client.write(MidiSongV2(id, fadeOutDelay, fadeOutSpeed, fadeInDelay, fadeInSpeed))
}
