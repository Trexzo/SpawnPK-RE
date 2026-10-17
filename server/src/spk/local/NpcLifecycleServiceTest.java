package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class NpcLifecycleServiceTest {
    public static void main(String[] args){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        NpcLifecycleService lifecycle=
            new NpcLifecycleService(
                registry
            );

        WorldNpc npc=
            registry.spawn(
                1488,
                3200,
                3200,
                0
            );

        NpcLifecycleService.Snapshot initial=
            lifecycle.register(
                npc,
                100,
                "CUSTOM_LOCALLAB"
            );

        require(
            initial.npcId.equals(
                npc.id
            )&&
            initial.definitionId==1488&&
            initial.hitpoints==100&&
            initial.maxHitpoints==100&&
            initial.alive()&&
            !initial.hasDeathTick(),
            "initial NPC lifecycle"
        );

        NpcLifecycleService.DamageResult nonlethal=
            lifecycle.applyDamage(
                npc.id,
                30,
                10L
            );

        require(
            nonlethal.requestedDamage==30&&
            nonlethal.appliedDamage==30&&
            nonlethal.hitpointsBefore==100&&
            nonlethal.hitpointsAfter==70&&
            !nonlethal.newlyDied&&
            !nonlethal.ignoredDead,
            "nonlethal NPC damage"
        );

        NpcLifecycleService.DamageResult lethal=
            lifecycle.applyDamage(
                npc.id,
                999,
                11L
            );

        NpcLifecycleService.Snapshot dead=
            lifecycle.get(
                npc.id
            );

        require(
            lethal.requestedDamage==999&&
            lethal.appliedDamage==70&&
            lethal.hitpointsBefore==70&&
            lethal.hitpointsAfter==0&&
            lethal.newlyDied&&
            !lethal.ignoredDead&&
            dead!=null&&
            dead.dead()&&
            dead.hitpoints==0&&
            dead.deathTick==11L&&
            dead.hasDeathTick(),
            "lethal NPC transition"
        );

        NpcLifecycleService.DamageResult duplicate=
            lifecycle.applyDamage(
                npc.id,
                5,
                12L
            );

        require(
            duplicate.appliedDamage==0&&
            duplicate.hitpointsBefore==0&&
            duplicate.hitpointsAfter==0&&
            !duplicate.newlyDied&&
            duplicate.ignoredDead&&
            lifecycle.get(
                npc.id
            ).deathTick==11L,
            "already-dead NPC damage duplicated death"
        );

        boolean duplicateRegisterRejected=false;

        try{
            lifecycle.register(
                npc,
                100,
                "CUSTOM_LOCALLAB"
            );
        }catch(
            IllegalStateException expected
        ){
            duplicateRegisterRejected=true;
        }

        require(
            duplicateRegisterRejected,
            "duplicate lifecycle registration accepted"
        );

        WorldNpc unregistered=
            registry.spawn(
                1489,
                3201,
                3200,
                0
            );

        boolean unknownRejected=false;

        try{
            lifecycle.applyDamage(
                unregistered.id,
                1,
                13L
            );
        }catch(
            IllegalArgumentException expected
        ){
            unknownRejected=true;
        }

        require(
            unknownRejected,
            "unregistered NPC damage accepted"
        );

        WorldNpc removed=
            registry.spawn(
                1490,
                3202,
                3200,
                0
            );

        lifecycle.register(
            removed,
            50,
            "CUSTOM_LOCALLAB"
        );

        require(
            registry.remove(
                removed.id
            ),
            "fixture canonical NPC removal"
        );

        boolean ownershipLossRejected=false;

        try{
            lifecycle.applyDamage(
                removed.id,
                1,
                14L
            );
        }catch(
            IllegalStateException expected
        ){
            ownershipLossRejected=true;
        }

        require(
            ownershipLossRejected&&
            lifecycle.get(
                removed.id
            ).hitpoints==50,
            "registry ownership loss did not fail closed"
        );

        EntityId ownerId=
            EntityId.next();

        WorldNpc replaceOld=
            registry.spawnOwned(
                1491,
                3203,
                3200,
                0,
                ownerId,
                1000
            );

        lifecycle.register(
            replaceOld,
            60,
            "CUSTOM_LOCALLAB"
        );

        WorldNpc replaceNew=
            registry.replaceOwned(
                replaceOld,
                1492,
                3204,
                3200,
                0,
                ownerId,
                1001
            );

        boolean replacementLossRejected=false;

        try{
            lifecycle.applyDamage(
                replaceOld.id,
                1,
                15L
            );
        }catch(
            IllegalStateException expected
        ){
            replacementLossRejected=true;
        }

        require(
            replacementLossRejected&&
            lifecycle.get(
                replaceOld.id
            ).hitpoints==60&&
            registry.byId(
                replaceNew.id
            )==replaceNew,
            "registry replacement ownership loss did not fail closed"
        );

        require(
            lifecycle.unregister(
                removed.id
            )&&
            !lifecycle.unregister(
                removed.id
            ),
            "NPC lifecycle unregister not idempotent"
        );

        WorldNpc foreign=
            new WorldNpc(
                EntityId.next(),
                1493,
                3205,
                3200,
                0,
                null,
                -1
            );

        boolean foreignRejected=false;

        try{
            lifecycle.register(
                foreign,
                25,
                "CUSTOM_LOCALLAB"
            );
        }catch(
            IllegalArgumentException expected
        ){
            foreignRejected=true;
        }

        require(
            foreignRejected,
            "non-registry WorldNpc registered"
        );

        WorldNpc sameIdForeign=
            new WorldNpc(
                npc.id,
                npc.definitionId,
                npc.x(),
                npc.y(),
                npc.plane(),
                npc.ownerId,
                npc.sourceItemId
            );

        boolean exactObjectRequired=false;

        try{
            lifecycle.register(
                sameIdForeign,
                25,
                "CUSTOM_LOCALLAB"
            );
        }catch(
            IllegalArgumentException expected
        ){
            exactObjectRequired=true;
        }

        require(
            exactObjectRequired,
            "same-id foreign WorldNpc registered"
        );

        boolean immutableList=false;

        try{
            lifecycle.snapshot()
                .clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutableList=true;
        }

        require(
            immutableList,
            "NPC lifecycle snapshot list mutable"
        );

        assertDomainBoundary();

        require(
            "CUSTOM_LOCALLAB".equals(
                lifecycle.get(
                    npc.id
                ).sourceAuthority
            ),
            "NPC lifecycle authority"
        );

        System.out.println(
            "NPC_LIFECYCLE_SERVICE_PASS "+
            "canonicalWorldNpc=true "+
            "exactObjectOwnership=true "+
            "ownershipLinearized=true "+
            "nonlethalDamage=true "+
            "lethalTransition=true "+
            "damageClamp=true "+
            "deathTick=true "+
            "duplicateDeath=false "+
            "removeOwnershipLossRejected=true "+
            "replaceOwnershipLossRejected=true "+
            "unregisterIdempotent=true "+
            "respawnPolicy=false "+
            "dropMutation=false "+
            "rewardMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void assertDomainBoundary(){
        Class<?>[] types={
            NpcLifecycleService.class,
            NpcLifecycleService.Snapshot.class,
            NpcLifecycleService.DamageResult.class
        };

        for(Class<?> type:types){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("scene")||
                   name.contains("clientindex")||
                   name.contains("widget")||
                   name.contains("reward")||
                   name.contains("drop")||
                   name.contains("respawn"))
                    throw new AssertionError(
                        "unsupported identity/policy leaked into NPC lifecycle "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private NpcLifecycleServiceTest(){}
}
