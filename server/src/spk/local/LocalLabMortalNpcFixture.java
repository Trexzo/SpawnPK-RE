package spk.local;

import java.io.IOException;

/**
 * R6 explicitly CUSTOM_LOCALLAB gameplay fixture, not a recovered SpawnPK NPC.
 *
 * Enabled only when the dedicated test JVM has
 * -Dspk.local.r6MortalFixture=true. Canonical HOME spawns, max-hit dummies,
 * original drop tables, and the default bootstrap remain unchanged.
 *
 * The existing combat engine resolves damage, timing and hit publication. This
 * class owns the separate fixture-only lethal HP transition and owner-only loot.
 */
final class LocalLabMortalNpcFixture {
    static final int NPC_DEFINITION=1;
    static final int MAX_HP=10;
    static final int LOOT_ITEM=995;
    static final int LOOT_AMOUNT=1;

    static boolean enabled(){
        return Boolean.getBoolean("spk.local.r6MortalFixture");
    }

    static String applyPublishedDamage(
        int damage,
        NpcEntity npc,
        NpcRegistry npcs,
        World world,
        WorldPlayer player,
        SceneUpdatePublisher scene,
        ServerPacketWriter writer,
        long worldTick
    )throws IOException{
        if(!enabled()||
           npc==null||
           !npc.localLabMortalFixture())
            return null;

        if(damage<=0)
            return null;

        if(npc.definitionId!=NPC_DEFINITION)
            throw new IllegalStateException(
                "mortal fixture definition mismatch");

        final int previousHp=npc.localLabMortalHp();
        if(previousHp<=0)
            throw new IllegalStateException(
                "duplicate lethal fixture hit scene="+npc.sceneIndex);

        final int remainingHp=
            npc.applyLocalLabMortalDamage(damage);
        if(remainingHp>0)
            return "R6_MORTAL_NPC_HIT scene="+npc.sceneIndex+
                " hpBefore="+previousHp+
                " damage="+damage+
                " hpAfter="+remainingHp+
                " authority=CUSTOM_LOCALLAB_FLAT_10_V1";

        String username=player.username();
        if(username==null||username.trim().isEmpty())
            throw new IllegalStateException(
                "mortal fixture owner has no username");

        Tile location=new Tile(npc.x,npc.y,0);
        if(world.groundItems().findOwned(
                LOOT_ITEM,location.x,location.y,
                location.plane,username)!=null)
            throw new IllegalStateException(
                "fixture loot location already occupied");

        String removed=npcs.devRemoveNpc(npc.sceneIndex,writer);
        if(!removed.startsWith("DEV_NPC_REMOVE_OK"))
            throw new IllegalStateException(
                "mortal fixture not removed: "+removed);

        GroundItem drop=world.groundItems().add(
            LOOT_ITEM,
            LOOT_AMOUNT,
            location,
            username,
            worldTick,
            true
        );
        scene.groundSpawn(drop);

        return "R6_MORTAL_NPC_DEATH scene="+npc.sceneIndex+
            " def="+npc.definitionId+
            " hpBefore="+previousHp+
            " damage="+damage+
            " hpAfter=0"+
            " dropItem="+LOOT_ITEM+
            " amount="+LOOT_AMOUNT+
            " world="+location.x+","+location.y+
            " owner="+username+
            " registryId="+drop.id+
            " authority=CUSTOM_LOCALLAB_OPT_IN_FIXTURE"+
            " originalSpawnpkDropTable=false";
    }

    private LocalLabMortalNpcFixture(){}
}
