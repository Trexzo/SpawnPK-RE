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
    static final int R7_MAX_HP=25;
    static final long R7_RESPAWN_TICKS=5L;
    static final int LOOT_ITEM=995;
    static final int LOOT_AMOUNT=1;

    static boolean enabled(){
        return Boolean.getBoolean("spk.local.r6MortalFixture");
    }

    static boolean lifecycleEnabled(){
        return enabled() &&
            Boolean.getBoolean("spk.local.r7LifecycleFixture");
    }

    static int maxHp(){
        return lifecycleEnabled()?R7_MAX_HP:MAX_HP;
    }

    /**
     * R48: reject a predictable lethal fixture conflict *before* any rune
     * debit, NPC65 hit, HP mutation or ground-item packet. This is a
     * read-only World-owned check, not a crash-safe transaction guarantee.
     *
     * Only the canonical R6 marked actor qualifies. Nonlethal hits do not
     * need a ground-loot reservation; lethal hits require a real registered
     * owner, dev-owned scene removal authority, a free owner loot position,
     * and a schedulable R7 respawn when that test mode is active.
     */
    static String preflightConfirmedDamage(
        int damage,
        NpcEntity npc,
        NpcRegistry npcs,
        World world,
        WorldPlayer player,
        long worldTick
    ){
        if(!enabled()||npc==null||!npc.localLabMortalFixture()||
           npc.definitionId!=NPC_DEFINITION||damage<=0)
            return "R48_REJECTED_NOT_MORTAL_DAMAGE";
        if(npcs==null||world==null||player==null||
           !player.registered()||
           npcs.scene(npc.sceneIndex)!=npc||
           npc.localLabMortalHp()<=0)
            return "R48_REJECTED_STALE_OR_UNREGISTERED";
        if(worldTick<0L)
            return "R48_REJECTED_NEGATIVE_WORLD_TICK";
        if(damage<npc.localLabMortalHp())
            return null;
        String owner=player.username();
        if(owner==null||owner.trim().isEmpty())
            return "R48_REJECTED_NO_LOOT_OWNER";
        if(!npcs.canRemoveDevOwnedNpc(npc))
            return "R48_REJECTED_SCENE_NOT_DEV_OWNED";
        if(world.groundItems().findOwned(
                LOOT_ITEM,npc.x,npc.y,0,owner)!=null)
            return "R48_REJECTED_OWNER_LOOT_TILE_OCCUPIED";
        if(!npcs.canScheduleLocalLabMortalDeath(worldTick))
            return "R48_REJECTED_R7_RESPAWN_UNSCHEDULABLE";
        return null;
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
        // Recheck at the canonical damage boundary. This protects all callers,
        // not just the R48 Wind Strike adapter, from leaving an HP=0 actor
        // or debited inventory after a predictable blocked loot publication.
        String conflict=preflightConfirmedDamage(
            damage,npc,npcs,world,player,worldTick);
        if(conflict!=null)
            throw new IllegalStateException(conflict);

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
        if(lifecycleEnabled())
            npcs.scheduleLocalLabMortalRespawn(npc,worldTick);

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
