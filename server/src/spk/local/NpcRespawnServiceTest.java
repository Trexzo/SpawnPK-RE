package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class NpcRespawnServiceTest {
    public static void main(String[] args){
        ordinaryRespawnLifecycle();
        aliveAndOwnedRejected();
        cancellation();
        zeroDelay();
        deterministicOrder();
        registrationFailureRollsBack();
        protocolBoundary();

        System.out.println(
            "NPC_RESPAWN_SERVICE_PASS "+
            "deadOnly=true "+
            "ownedNpcRejected=true "+
            "canonicalRetirement=true "+
            "noEarlyRespawn=true "+
            "exactDueTick=true "+
            "zeroDelay=true "+
            "freshEntityId=true "+
            "definitionTileMaxHpPreserved=true "+
            "freshLifecycleAlive=true "+
            "cancelledNoRespawn=true "+
            "monotonicTick=true "+
            "deterministicDueOrder=true "+
            "registrationFailureRollback=true "+
            "combatOutcomeMutation=false "+
            "dropMutation=false "+
            "rewardMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void ordinaryRespawnLifecycle(){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        NpcLifecycleService lifecycle=
            new NpcLifecycleService(registry);
        NpcRespawnService respawns=
            new NpcRespawnService(
                registry,
                lifecycle
            );

        WorldNpc npc=
            registry.spawn(
                1488,
                3200,
                3201,
                0
            );

        lifecycle.register(
            npc,
            100,
            "CUSTOM_LOCALLAB_HP"
        );
        lifecycle.applyDamage(
            npc.id,
            100,
            10L
        );

        NpcRespawnService.TicketSnapshot ticket=
            respawns.scheduleDead(
                npc,
                3L,
                "CUSTOM_LOCALLAB_RESPAWN"
            );

        require(
            ticket.oldNpcId.equals(npc.id)&&
            ticket.deathTick==10L&&
            ticket.dueTick==13L&&
            ticket.maxHitpoints==100&&
            ticket.definitionId==1488&&
            ticket.x==3200&&
            ticket.y==3201&&
            ticket.plane==0&&
            ticket.state==
                NpcRespawnService.State.SCHEDULED,
            "scheduled respawn template"
        );

        require(
            registry.byId(npc.id)==null&&
            lifecycle.get(npc.id)==null,
            "dead canonical state not retired"
        );

        NpcRespawnService.TickResult early=
            respawns.tick(12L);

        require(
            early.respawned.isEmpty()&&
            registry.size()==0,
            "NPC respawned early"
        );

        NpcRespawnService.TickResult due=
            respawns.tick(13L);

        require(
            due.respawned.size()==1&&
            due.failedTickets.isEmpty(),
            "due respawn missing"
        );

        NpcRespawnService.RespawnFact fact=
            due.respawned.get(0);

        require(
            fact.oldNpcId.equals(npc.id)&&
            !fact.newNpcId.equals(npc.id)&&
            fact.definitionId==1488&&
            fact.tile.x==3200&&
            fact.tile.y==3201&&
            fact.tile.plane==0&&
            fact.worldTick==13L&&
            "CUSTOM_LOCALLAB_RESPAWN".equals(
                fact.respawnPolicyAuthority
            ),
            "respawn fact"
        );

        WorldNpc fresh=
            registry.byId(
                fact.newNpcId
            );
        NpcLifecycleService.Snapshot freshLife=
            lifecycle.get(
                fact.newNpcId
            );

        require(
            fresh!=null&&
            fresh.definitionId==1488&&
            freshLife!=null&&
            freshLife.alive()&&
            freshLife.hitpoints==100&&
            freshLife.maxHitpoints==100&&
            "CUSTOM_LOCALLAB_HP".equals(
                freshLife.sourceAuthority
            ),
            "fresh respawn lifecycle"
        );

        expect(
            IllegalArgumentException.class,
            ()->respawns.tick(12L),
            "world tick backwards"
        );

        expect(
            IllegalStateException.class,
            ()->respawns.cancel(
                ticket.ticketId
            ),
            "cancel respawned ticket"
        );
    }

    private static void aliveAndOwnedRejected(){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        NpcLifecycleService lifecycle=
            new NpcLifecycleService(registry);
        NpcRespawnService respawns=
            new NpcRespawnService(
                registry,
                lifecycle
            );

        WorldNpc alive=
            registry.spawn(
                1500,
                1,
                2,
                0
            );
        lifecycle.register(
            alive,
            10,
            "CUSTOM_LOCALLAB"
        );

        expect(
            IllegalStateException.class,
            ()->respawns.scheduleDead(
                alive,
                5L,
                "CUSTOM_LOCALLAB"
            ),
            "alive NPC scheduled"
        );

        require(
            registry.byId(alive.id)==alive&&
            lifecycle.get(alive.id).alive(),
            "alive rejection mutated state"
        );

        WorldNpc owned=
            registry.spawnOwned(
                1501,
                2,
                2,
                0,
                EntityId.next(),
                1000
            );
        lifecycle.register(
            owned,
            10,
            "CUSTOM_LOCALLAB"
        );
        lifecycle.applyDamage(
            owned.id,
            10,
            5L
        );

        expect(
            IllegalArgumentException.class,
            ()->respawns.scheduleDead(
                owned,
                5L,
                "CUSTOM_LOCALLAB"
            ),
            "owned NPC scheduled"
        );

        require(
            registry.byId(owned.id)==owned&&
            lifecycle.get(owned.id).dead(),
            "owned rejection mutated state"
        );
    }

    private static void cancellation(){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        NpcLifecycleService lifecycle=
            new NpcLifecycleService(registry);
        NpcRespawnService respawns=
            new NpcRespawnService(
                registry,
                lifecycle
            );

        WorldNpc npc=dead(
            registry,
            lifecycle,
            1600,
            20L,
            25
        );

        NpcRespawnService.TicketSnapshot ticket=
            respawns.scheduleDead(
                npc,
                2L,
                "CUSTOM_LOCALLAB"
            );

        NpcRespawnService.TicketSnapshot cancelled=
            respawns.cancel(
                ticket.ticketId
            );

        require(
            cancelled.state==
                NpcRespawnService.State.CANCELLED&&
            respawns.cancel(
                ticket.ticketId
            ).state==
                NpcRespawnService.State.CANCELLED,
            "cancel idempotency"
        );

        require(
            respawns.tick(22L)
                .respawned.isEmpty()&&
            registry.size()==0,
            "cancelled NPC respawned"
        );
    }

    private static void zeroDelay(){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        NpcLifecycleService lifecycle=
            new NpcLifecycleService(registry);
        NpcRespawnService respawns=
            new NpcRespawnService(
                registry,
                lifecycle
            );

        WorldNpc npc=dead(
            registry,
            lifecycle,
            1700,
            30L,
            5
        );

        NpcRespawnService.TicketSnapshot ticket=
            respawns.scheduleDead(
                npc,
                0L,
                "CUSTOM_LOCALLAB"
            );

        require(
            ticket.dueTick==30L&&
            respawns.tick(30L)
                .respawned.size()==1,
            "zero-delay respawn"
        );
    }

    private static void deterministicOrder(){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        NpcLifecycleService lifecycle=
            new NpcLifecycleService(registry);
        NpcRespawnService respawns=
            new NpcRespawnService(
                registry,
                lifecycle
            );

        WorldNpc first=dead(
            registry,
            lifecycle,
            1800,
            40L,
            10
        );
        WorldNpc second=dead(
            registry,
            lifecycle,
            1801,
            40L,
            10
        );

        NpcRespawnService.TicketSnapshot a=
            respawns.scheduleDead(
                first,
                5L,
                "CUSTOM_LOCALLAB"
            );
        NpcRespawnService.TicketSnapshot b=
            respawns.scheduleDead(
                second,
                5L,
                "CUSTOM_LOCALLAB"
            );

        NpcRespawnService.TickResult result=
            respawns.tick(45L);

        require(
            result.respawned.size()==2&&
            result.respawned.get(0)
                .ticketId.equals(a.ticketId)&&
            result.respawned.get(1)
                .ticketId.equals(b.ticketId)&&
            result.respawned.get(0)
                .newNpcId.value<
            result.respawned.get(1)
                .newNpcId.value,
            "respawn due order"
        );
    }

    private static void registrationFailureRollsBack(){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        NpcLifecycleService lifecycle=
            new NpcLifecycleService(registry);

        NpcRespawnService respawns=
            new NpcRespawnService(
                registry,
                lifecycle,
                (npc,maxHp,authority)->{
                    throw new IllegalStateException(
                        "fixture registration failure"
                    );
                }
            );

        WorldNpc npc=dead(
            registry,
            lifecycle,
            1900,
            50L,
            10
        );

        NpcRespawnService.TicketSnapshot ticket=
            respawns.scheduleDead(
                npc,
                1L,
                "CUSTOM_LOCALLAB"
            );

        require(
            registry.size()==0,
            "old NPC not retired before failure fixture"
        );

        NpcRespawnService.TickResult failed=
            respawns.tick(51L);

        require(
            failed.respawned.isEmpty()&&
            failed.failedTickets.equals(
                Collections.singletonList(
                    ticket.ticketId
                )
            )&&
            registry.size()==0&&
            respawns.get(
                ticket.ticketId
            ).state==
                NpcRespawnService.State.SCHEDULED,
            "registration failure rollback"
        );

        NpcRespawnService.TickResult failedAgain=
            respawns.tick(52L);

        require(
            failedAgain.failedTickets.size()==1&&
            registry.size()==0,
            "failed respawn retry leaked NPC"
        );
    }

    private static WorldNpc dead(
        WorldNpcRegistry registry,
        NpcLifecycleService lifecycle,
        int definitionId,
        long deathTick,
        int maxHp
    ){
        WorldNpc npc=
            registry.spawn(
                definitionId,
                definitionId,
                definitionId+1,
                0
            );

        lifecycle.register(
            npc,
            maxHp,
            "CUSTOM_LOCALLAB_HP"
        );

        lifecycle.applyDamage(
            npc.id,
            maxHp,
            deathTick
        );

        return npc;
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                NpcRespawnService.class,
                NpcRespawnService.TicketSnapshot.class,
                NpcRespawnService.RespawnFact.class
        }){
            for(Field field:type.getDeclaredFields()){
                String name=field.getName()
                    .toLowerCase(Locale.ROOT);

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("scene")||
                   name.contains("widget")||
                   name.contains("reward")||
                   name.contains("drop"))
                    throw new AssertionError(
                        "presentation/economy identity leaked "+
                        type.getSimpleName()+
                        "."+field.getName()
                    );
            }
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;
            throw new AssertionError(
                label+" wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private NpcRespawnServiceTest(){}
}
