package spk.local;

import java.util.*;

/** Offline data/provenance contract for WORLD-R1. No client JAR is required. */
public final class HomeWorldParityDataTest {
    public static void main(String[] args){
        eq(HomeObjectOverlayRepository.count(),53,"overlay count");
        eq(HomeObjectOverlayRepository.removeCount(),27,"remove count");
        eq(HomeObjectOverlayRepository.addCount(),26,"add count");
        eq(HomeNpcSpawnRepository.count(),48,"canonical NPC count");
        eq(HomeLandmarkRepository.count(),9,"landmark count");

        int stat=0,wander=0,defaults=0;
        for(HomeNpcSpawnRepository.Spawn s:HomeNpcSpawnRepository.all()){
            if(s.staticObserved())stat++;
            if(s.localWanderObserved())wander++;
            if(s.defaultReplay)defaults++;
        }
        eq(stat,33,"static NPC count"); eq(wander,15,"wander NPC count"); eq(defaults,47,"default replay NPC count");

        npc(1799,3079,3492); // Blood fountain
        npc(1489,3107,3504);
        npc(1488,3107,3506); npc(1488,3107,3508);
        npc(1488,3095,3513); npc(1488,3097,3513); npc(1488,3099,3513);

        HomeNpcSpawnRepository.Spawn warning=only(HomeNpcSpawnRepository.byDefinition(8168));
        eq(warning.anchorX,3084,"warning x");eq(warning.anchorY,3495,"warning y");
        eq(warning.duplicateObservedActors,21,"warning duplicate actors");

        HomeNpcSpawnRepository.Spawn event=only(HomeNpcSpawnRepository.byDefinition(8193));
        if(event.defaultReplay)fail("event NPC must not be default replay");
        if(!"EVENT_SCOPED_OBSERVED".equals(event.lifecycleScope))fail("event lifecycle="+event.lifecycleScope);

        wander(315,3094,3096,3502,3503);   // Emblem trader
        wander(1050,3089,3091,3498,3500); // Collection shop
        wander(3280,3078,3080,3498,3500); // Repair dwarf

        objAdd(15477,15477,3084,3483,10,0,3174);   // Portal
        objAdd(6552,6552,3095,3506,10,2,-1);      // Spell-book altar
        objAdd(61,61,3098,3506,10,2,-1);          // Chaos altar
        objAdd(4483,4483,3091,3510,10,1,-1);      // Bank chest
        objAdd(43484,10716,3090,3489,10,1,3022);  // Loot chest alias
        objAdd(43484,10716,3094,3507,10,2,3022);
        objAdd(13291,13291,3082,3495,10,1,3117);  // Magic chest
        if(!HomeObjectOverlayRepository.hasRemoveOnly(3091,3508))fail("missing remove-only 3091,3508");
        landmark("BANK_CHEST_NORTH_B",4483,4483,3091,3511);
        landmark("MAGIC_CHEST_ENCHANTMENT_CANDIDATE",13291,13291,3082,3495);

        if(!HomeWorldManifest.EXACT_PACKET_101_151_SERIALIZER_CERTIFIED)fail("WORLD-R2 must certify exact scene serializer");
        HomeWorldManifest.requireSceneSerializerCertification();

        // Signed-5 packet65 range helper: every returned spawn must fit exact current encoder range.
        List<HomeNpcSpawnRepository.Spawn> near=HomeWorldManifest.nearbyDefaultReplayNpcs(3084,3497);
        if(near.isEmpty())fail("nearby list unexpectedly empty");
        for(HomeNpcSpawnRepository.Spawn s:near) if(!s.withinSigned5SpawnRange(3084,3497))fail("out-of-range nearby "+s);

        System.out.println("WORLD_R2_HOME_PARITY_DATA_PASS overlay=53 remove=27 add=26 canonicalNpcs=48 static=33 wander=15 defaultReplay=47 warning8168Collapsed=21 serializerCertified=true");
    }

    private static void npc(int def,int x,int y){
        for(HomeNpcSpawnRepository.Spawn s:HomeNpcSpawnRepository.byDefinition(def))
            if(s.anchorX==x&&s.anchorY==y)return;
        fail("missing npc "+def+" @"+x+","+y);
    }
    private static HomeNpcSpawnRepository.Spawn only(List<HomeNpcSpawnRepository.Spawn> x){
        if(x.size()!=1)fail("expected one NPC row, got "+x.size()); return x.get(0);
    }
    private static void wander(int def,int minx,int maxx,int miny,int maxy){
        for(HomeNpcSpawnRepository.Spawn s:HomeNpcSpawnRepository.byDefinition(def))
            if(s.minX==minx&&s.maxX==maxx&&s.minY==miny&&s.maxY==maxy&&s.localWanderObserved())return;
        fail("wander bounds mismatch def="+def);
    }
    private static void objAdd(int wire,int scene,int x,int y,int shape,int rot,int anim){
        for(HomeObjectOverlayRepository.Mutation m:HomeObjectOverlayRepository.at(x,y))
            if(m.isAdd()&&m.wireObjectId==wire&&m.sceneObjectId==scene&&m.shape==shape&&m.rotation==rot&&m.definitionAnimationId==anim)return;
        fail("missing object add wire="+wire+" scene="+scene+" @"+x+","+y);
    }

    private static void landmark(String key,int wire,int scene,int x,int y){
        HomeLandmarkRepository.Landmark l=HomeLandmarkRepository.get(key);
        if(l==null||l.wireObjectId!=wire||l.sceneObjectId!=scene||l.worldX!=x||l.worldY!=y)
            fail("landmark mismatch "+key);
    }

    private static void eq(int a,int b,String what){if(a!=b)fail(what+" "+a+" != "+b);}
    private static void fail(String s){throw new AssertionError(s);}
}
