package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalCanonicalNpcAttackSpatialAtomicityTest {
    public static void main(String[] args)throws Exception{
        playerMoveRaceRejected();
        npcMoveRaceRejected();
        unchangedInRangeDamagesOnce();

        System.out.println(
            "CANONICAL_NPC_ATTACK_SPATIAL_ATOMICITY_PASS "+
            "spatialMutationAtomic=true "+
            "playerMoveRaceRejected=true "+
            "npcMoveRaceRejected=true "+
            "unchangedInRangeDamagesOnce=true "+
            "packetOnRejected=false "+
            "cadenceOnRejected=false"
        );
    }

    private static void playerMoveRaceRejected()
        throws Exception{
        Fixture fixture=new Fixture();

        try{
            LocalCanonicalNpcAttackHandler handler=
                fixture.handler(
                    (target,generation)->
                        fixture.movePlayer(
                            generation,
                            3080,
                            3495
                        )
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
                        .Status.OUT_OF_RANGE,
                "player move race was not rejected"
            );
            require(
                fixture.hitpoints()==20,
                "player move race mutated NPC HP"
            );
            require(
                bytes.size()==0,
                "player move race emitted hit packet"
            );
            require(
                handler.nextAllowedAttackTick()==0L,
                "player move race consumed cadence"
            );
        }finally{
            fixture.close();
        }
    }

    private static void npcMoveRaceRejected()
        throws Exception{
        Fixture fixture=new Fixture();

        try{
            LocalCanonicalNpcAttackHandler handler=
                fixture.handler(
                    (target,generation)->{
                        WorldNpc moved=
                            fixture.world.npcs().move(
                                target.id,
                                3090,
                                3495,
                                0
                            );

                        require(
                            moved==target,
                            "NPC move race fixture lost exact target"
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
                        .Status.OUT_OF_RANGE,
                "NPC move race was not rejected"
            );
            require(
                fixture.hitpoints()==20,
                "NPC move race mutated HP"
            );
            require(
                bytes.size()==0,
                "NPC move race emitted hit packet"
            );
            require(
                handler.nextAllowedAttackTick()==0L,
                "NPC move race consumed cadence"
            );
        }finally{
            fixture.close();
        }
    }

    private static void unchangedInRangeDamagesOnce()
        throws Exception{
        Fixture fixture=new Fixture();

        try{
            LocalCanonicalNpcAttackHandler handler=
                fixture.handler(
                    (target,generation)->{}
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
                        .Status.HIT&&
                result.appliedDamage==10&&
                result.hitpointsAfter==10,
                "unchanged in-range click did not hit exactly once"
            );
            require(
                fixture.hitpoints()==10,
                "unchanged in-range click did not mutate canonical HP once"
            );
            require(
                bytes.size()>0,
                "unchanged in-range click emitted no hit packet"
            );
            require(
                handler.nextAllowedAttackTick()>
                    fixture.world.clock().tick(),
                "successful in-range click did not consume cadence"
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
        final NpcRegistry npcs;
        final NpcEntity view;

        Fixture()throws Exception{
            generation=
                world.registerPlayer(
                    player,
                    "canonical-spatial-race"
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
                    "CUSTOM_LOCALLAB_SPATIAL_ATOMICITY_TEST"
                );

            npcs=
                new NpcRegistry(
                    new DevAuthorityWorkbench(),
                    world.petNpcs(),
                    player.id()
                );

            view=
                npcs.spawnMirroredNpc(
                    definition,
                    3088,
                    3495,
                    null,
                    player.movement(),
                    writer(
                        new ByteArrayOutputStream()
                    )
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

        void movePlayer(
            long expectedGeneration,
            int x,
            int y
        ){
            try{
                boolean current=
                    world.withOpenPlayerMutationOwnershipIfCurrent(
                        player,
                        expectedGeneration,
                        ()->
                            player.movement()
                                .restoreAccountState(
                                    false,
                                    100,
                                    x,
                                    y,
                                    0
                                )
                    );

                require(
                    current,
                    "player move race fixture lost generation"
                );
            }catch(RuntimeException failure){
                throw failure;
            }catch(Error failure){
                throw failure;
            }catch(Exception failure){
                throw new IllegalStateException(
                    "player move race fixture failed",
                    failure
                );
            }
        }

        int hitpoints(){
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

    private LocalCanonicalNpcAttackSpatialAtomicityTest(){}
}
