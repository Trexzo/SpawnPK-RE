package spk.local;

import java.io.IOException;
import java.util.Objects;

/** Exact-v308 hit/block-drop popup presentation publisher (S2C255). */
final class CombatPopupPublisher {
    private final ServerPacketWriter packets;

    CombatPopupPublisher(ServerPacketWriter packets){
        this.packets=Objects.requireNonNull(packets,"packets");
    }

    void combatPopup(
        int value,
        long popupEventId,
        int protectionType
    )throws IOException{
        packets.fixed(255,new PacketPayloadWriter()
            .putU16BE(value)
            .putI64BE(popupEventId)
            .putU16BE(protectionType)
            .toByteArray());
    }
}
