package spk.local;

import java.io.*;

/** Session-only player presentation controls backed by exact packet-81 appearance publication. */
final class PlayerPresentationService {
    private final World world;
    private final DevAuthorityWorkbench dev;

    PlayerPresentationService(DevAuthorityWorkbench dev){
        this(World.shared(),dev);
    }

    PlayerPresentationService(World world,DevAuthorityWorkbench dev){
        if(world==null) throw new NullPointerException("world");
        if(dev==null) throw new NullPointerException("dev");
        this.world=world;
        this.dev=dev;
    }

    void refresh(String username,EquipmentState equipment,PlayerState player,ServerPacketWriter packets) throws IOException {
        publishAppearance(
            username,
            equipment,
            player,
            dev.playerNpcTransformId(),
            packets
        );
    }

    String morph(int npcId,String username,EquipmentState equipment,PlayerState player,ServerPacketWriter packets) throws IOException {
        if(npcId<0||npcId>16383)
            throw new IllegalArgumentException(
                "npc transform 0..16383"
            );

        publishAppearance(
            username,
            equipment,
            player,
            Integer.valueOf(npcId),
            packets
        );
        dev.setPlayerNpcTransformId(npcId);
        dev.trace().record("PLAYER_NPC_MORPH",
            "REQUEST=C2S103_DEV -> route=PlayerPresentationService -> state=npcTransformId:"+npcId+
            " -> publish=S2C81_APPEARANCE(slot0=0xFFFF,npc="+npcId+") -> result=CLIENT_PLAYER_RENDERS_NPC",
            "EXACT_CURRENT_CLIENT");
        return "DEV_PLAYER_MORPH_OK npc="+npcId+" entityFamily=PLAYER persisted=false authority=EXACT_CURRENT_CLIENT";
    }

    String clear(String username,EquipmentState equipment,PlayerState player,ServerPacketWriter packets) throws IOException {
        Integer old=dev.playerNpcTransformId();

        publishAppearance(
            username,
            equipment,
            player,
            null,
            packets
        );
        dev.setPlayerNpcTransformId(null);
        dev.trace().record("PLAYER_NPC_MORPH_CLEAR",
            "REQUEST=C2S103_DEV -> route=PlayerPresentationService -> state=npcTransformId:AUTO -> publish=S2C81_APPEARANCE(normalEquipment) -> result=NORMAL_PLAYER_MODEL",
            "EXACT_CURRENT_CLIENT");
        return "DEV_PLAYER_MORPH_CLEAR old="+(old==null?"NONE":old)+" persisted=false";
    }

    private void publishAppearance(
        String username,
        EquipmentState equipment,
        PlayerState player,
        Integer npcTransformId,
        ServerPacketWriter packets
    )throws IOException{
        packets.varShort(
            81,
            BootstrapPackets.player81AppearanceOnly(
                username,
                equipment.appearanceItems(),
                player,
                npcTransformId,
                world.appearanceRoleFor(
                    username,
                    player
                )
            )
        );
    }

    String info(){
        return "DEV_PLAYER_PRESENTATION morph="+(dev.playerNpcTransformId()==null?"NORMAL":dev.playerNpcTransformId())+
            " entityFamily=PLAYER authority="+(dev.playerNpcTransformId()==null?"NATIVE_NORMAL":"EXACT_CURRENT_CLIENT")+
            " persisted=false";
    }
}
