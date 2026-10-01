package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalCanonicalNpcAttackOwnershipRaceTest {
    public static void main(String[] args)throws Exception{
        staleAttackerRace();
        staleTargetRace();
        lifecycleRace();
        unexpectedFailurePropagates();

        System.out.println(
            "CANONICAL_NPC_ATTACK_OWNERSHIP_RACE_PASS "+
            "resolutionStaleFailClosed=true "+
            "staleAttackerRaceRejected=true "+
            "staleTargetRaceRejected=true "+
            "lifecycleRaceRejected=true "+
            "unexpectedFailurePropagates=true "+
            "packetSideEffect=false "+
            "cadenceSideEffect=false"
        );
    }

    private static void staleAttackerRace()
        throws Exception{
        Fixture fixture=new Fixture();

        try{
            LocalCanonicalNpcAttackHandler handler=
                fixture.handler(
                    (target,generation)->{
                        require(
                            fixture.world.unregisterPlayer(
                                fixture.player,
                                generation
                            ),
                            "stale attacker fixture unregister"
                        );
                    }
                );

            ByteArrayOutputStream bytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result result=
                fixture.attack(
                    handler,
                    bytes
                );

            require(
                result.status==
                    LocalCanonicalNpcAttackHandler
                        .Status.STALE_PLAYER,
                "stale attacker race did not fail closed"
            );
            require(
                fixture.lifecycleHitpoints()==20,
                "stale attacker race mutated HP"
            );
            require(
                bytes.size()==0,
                "stale attacker race emitted packet"
            );
            require(
                handler.nextAllowedAttackTick()==0L,
                "stale attacker race consumed cadence"
            );
        }finally{
            fixture.close();
        }
    }

    private static void staleTargetRace()
        throws Exception{
        Fixture fixture=new Fixture();

        try{
            LocalCanonicalNpcAttackHandler handler=
                fixture.handler(
                    (target,generation)->{
                        require(
                            fixture.world.npcs()
                                .remove(target.id),
                            "stale target fixture removal"
                        );
                    }
                );

            ByteArrayOutputStream bytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result result=
                fixture.attack(
                    handler,
                    bytes
                );

            require(
                result.status==
                    LocalCanonicalNpcAttackHandler
                        .Status.STALE_CANONICAL,
                "stale target race did not fail closed"
            );
            require(
                fixture.lifecycleHitpoints()==20,
                "stale target race mutated HP"
            );
            require(
                bytes.size()==0,
                "stale target race emitted packet"
            );
            require(
                handler.nextAllowedAttackTick()==0L,
                "stale target race consumed cadence"
            );
        }finally{
            fixture.close();
        }
    }

    private static void lifecycleRace()
        throws Exception{
        Fixture fixture=new Fixture();

        try{
            LocalCanonicalNpcAttackHandler handler=
                fixture.handler(
                    (target,generation)->{
                        require(
                            fixture.world.npcLifecycle()
                                .unregisterExact(target),
                            "lifecycle race fixture unregister"
                        );
                    }
                );

            ByteArrayOutputStream bytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result result=
                fixture.attack(
                    handler,
                    bytes
                );

            require(
                result.status==
                    LocalCanonicalNpcAttackHandler
                        .Status.LIFECYCLE_MISSING,
                "lifecycle race did not fail closed"
            );
            require(
                fixture.world.npcs()
                    .byId(fixture.target.id)==
                        fixture.target,
                "lifecycle race changed canonical NPC identity"
            );
            require(
                fixture.world.npcLifecycle()
                    .get(fixture.target.id)==null,
                "lifecycle race fixture still registered"
            );
            require(
                bytes.size()==0,
                "lifecycle race emitted packet"
            );
            require(
                handler.nextAllowedAttackTick()==0L,
                "lifecycle race consumed cadence"
            );
        }finally{
            fixture.close();
        }
    }

    private static void unexpectedFailurePropagates()
        throws Exception{
        Fixture fixture=new Fixture();

        try{
            final IllegalStateException sentinel=
                new IllegalStateException(
                    "UNEXPECTED_RESOLUTION_SENTINEL"
                );

            LocalCanonicalNpcAttackHandler handler=
                fixture.handler(
                    (target,generation)->{
                        throw sentinel;
                    }
                );

            ByteArrayOutputStream bytes=
                new ByteArrayOutputStream();

            boolean propagated=false;

            try{
                fixture.attack(
                    handler,
                    bytes
                );
            }catch(IllegalStateException failure){
                propagated=
                    failure==sentinel;
            }

            require(
                propagated,
                "unexpected invariant failure was swallowed or reclassified"
            );
            require(
                fixture.lifecycleHitpoints()==20,
                "unexpected failure mutated HP"
            );
            require(
                bytes.size()==0,
                "unexpected failure emitted packet"
            );
            require(
                handler.nextAllowedAttackTick()==0L,
                "unexpected failure consumed cadence"
            );
        }finally{
            fixture.close();
        }
    }

    private static final class Fixture
        implements AutoCloseable {
        final World world=
            World.isolatedForTest(600L);
        final WorldPlayer player=
            new WorldPlayer();
        final long generation;
        final WorldNpc target;
        final NpcEntity view;
        final NpcRegistry npcs;

        Fixture(){
            generation=
                world.registerPlayer(
                    player,
                    "canonical-attack-race"
                );

            player.movement()
                .restoreAccountState(
                    false,
                    100,
                    3087,
                    3495,
                    0
                );

            int definition=
                attackDefinition();

            target=
                world.npcs().spawn(
                    definition,
                    3088,
                    3495,
                    0
                );

            world.npcLifecycle()
                .register(
                    target,
                    20,
                    "CUSTOM_LOCALLAB_ATTACK_RACE_TEST"
                );

            npcs=
                new NpcRegistry(
                    new DevAuthorityWorkbench(),
                    world.petNpcs(),
                    player.id()
                );

            view=
                new NpcEntity(
                    100,
                    definition,
                    3088,
                    3495
                );

            view.bindCanonicalId(
                target.id
            );
        }

        LocalCanonicalNpcAttackHandler handler(
            LocalCanonicalNpcAttackHandler
                .BeforeResolutionHook hook
        ){
            return new LocalCanonicalNpcAttackHandler(
                world,
                player,
                ()->generation,
                player.equipment(),
                player.combatStyles(),
                npcs,
                hook
            );
        }

        LocalCanonicalNpcAttackHandler.Result attack(
            LocalCanonicalNpcAttackHandler handler,
            ByteArrayOutputStream bytes
        )throws Exception{
            return handler.handle(
                new NpcAction(
                    72,
                    view.sceneIndex
                ),
                view,
                writer(bytes)
            );
        }

        int lifecycleHitpoints(){
            NpcLifecycleService.Snapshot snapshot=
                world.npcLifecycle()
                    .get(target.id);

            return snapshot==null
                ?-1
                :snapshot.hitpoints;
        }

        @Override public void close(){
            if(world.players().owns(
                    player,
                    generation
                ))
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static int attackDefinition(){
        for(int definition=0;
            definition<16384;
            definition++){
            NpcEntity candidate;

            try{
                candidate=
                    new NpcEntity(
                        100,
                        definition,
                        3088,
                        3495
                    );
            }catch(IllegalArgumentException ignored){
                continue;
            }

            NpcInteractionRouter.Route route=
                NpcInteractionRouter.resolve(
                    new NpcAction(
                        72,
                        candidate.sceneIndex
                    ),
                    candidate
                );

            if(route.service==
                    NpcInteractionRouter.Service.ATTACK&&
               !CombatTargetRepository
                    .isCombatDummy(definition))
                return definition;
        }

        throw new AssertionError(
            "exact action corpus needs one non-dummy option-2 Attack definition"
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream out
    ){
        return new ServerPacketWriter(
            out,
            new IsaacCipher(
                new int[4]
            )
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private LocalCanonicalNpcAttackOwnershipRaceTest(){}
}
