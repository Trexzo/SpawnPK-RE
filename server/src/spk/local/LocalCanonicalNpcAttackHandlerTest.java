package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public final class LocalCanonicalNpcAttackHandlerTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "canonical-npc-click"
            );

        player.movement()
            .restoreAccountState(
                false,
                100,
                3087,
                3495,
                0
            );

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                player.id()
            );

        ByteArrayOutputStream relayBytes=
            new ByteArrayOutputStream();
        ServerPacketWriter relayWriter=
            writer(relayBytes);

        int[] attackDefinitions=
            attackDefinitions();

        int attackDefinition=
            attackDefinitions[0];
        int alternateAttackDefinition=
            attackDefinitions[1];

        LocalCanonicalNpcAttackHandler handler=
            new LocalCanonicalNpcAttackHandler(
                world,
                player,
                ()->generation,
                player.equipment(),
                player.combatStyles(),
                npcs
            );

        try{
            SharedNpcWorldRelay.register(
                relayWriter,
                world,
                player,
                npcs,
                player.movement()
            );

            WorldNpc target=
                canonicalTarget(
                    world,
                    attackDefinition,
                    3088,
                    3495,
                    20
                );

            NpcEntity view=
                project(
                    world,
                    target,
                    relayWriter,
                    npcs
                );

            ByteArrayOutputStream firstBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result
                first=
                    handler.handle(
                        new NpcAction(
                            72,
                            view.sceneIndex
                        ),
                        view,
                        writer(firstBytes)
                    );

            require(
                first!=null&&
                first.status==
                    LocalCanonicalNpcAttackHandler
                        .Status.HIT&&
                first.appliedDamage==10&&
                first.hitpointsAfter==10&&
                first.maxHitpoints==20&&
                !first.newlyDied,
                "first canonical click"
            );

            require(
                world.npcLifecycle()
                    .get(target.id)
                    .hitpoints==10,
                "first click did not mutate canonical HP exactly once"
            );

            require(
                Arrays.equals(
                    firstBytes.toByteArray(),
                    expectedHitPacket(
                        npcs,
                        view,
                        10,
                        10,
                        20
                    )
                ),
                "first hit packet did not use lifecycle result"
            );

            ByteArrayOutputStream lethalBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result
                lethal=
                    handler.handle(
                        new NpcAction(
                            72,
                            view.sceneIndex
                        ),
                        view,
                        writer(lethalBytes)
                    );

            NpcLifecycleService.Snapshot dead=
                world.npcLifecycle()
                    .get(target.id);

            require(
                lethal.status==
                    LocalCanonicalNpcAttackHandler
                        .Status.HIT&&
                lethal.appliedDamage==10&&
                lethal.hitpointsAfter==0&&
                lethal.newlyDied&&
                dead!=null&&
                dead.dead()&&
                dead.hitpoints==0,
                "lethal click did not publish canonical death"
            );

            require(
                Arrays.equals(
                    lethalBytes.toByteArray(),
                    expectedHitPacket(
                        npcs,
                        view,
                        10,
                        0,
                        20
                    )
                ),
                "lethal hit packet did not use lifecycle result"
            );

            ByteArrayOutputStream deadBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result
                deadReplay=
                    handler.handle(
                        new NpcAction(
                            72,
                            view.sceneIndex
                        ),
                        view,
                        writer(deadBytes)
                    );

            require(
                deadReplay.status==
                    LocalCanonicalNpcAttackHandler
                        .Status.TARGET_DEAD&&
                world.npcLifecycle()
                    .get(target.id)
                    .hitpoints==0&&
                deadBytes.size()==0,
                "dead target accepted duplicate damage"
            );

            WorldNpc ranged=
                canonicalTarget(
                    world,
                    attackDefinition,
                    3090,
                    3495,
                    20
                );

            NpcEntity rangedView=
                project(
                    world,
                    ranged,
                    relayWriter,
                    npcs
                );

            ByteArrayOutputStream rangeBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result
                outOfRange=
                    handler.handle(
                        new NpcAction(
                            72,
                            rangedView.sceneIndex
                        ),
                        rangedView,
                        writer(rangeBytes)
                    );

            require(
                outOfRange.status==
                    LocalCanonicalNpcAttackHandler
                        .Status.OUT_OF_RANGE&&
                world.npcLifecycle()
                    .get(ranged.id)
                    .hitpoints==20&&
                rangeBytes.size()==0,
                "out-of-range click mutated HP"
            );

            world.npcs().move(
                ranged.id,
                3088,
                3495,
                0
            );
            SharedNpcWorldRelay.syncRemotePets(
                relayWriter
            );
            rangedView=
                npcs.canonical(
                    ranged.id
                );

            LocalCanonicalNpcAttackHandler
                staleGenerationHandler=
                    new LocalCanonicalNpcAttackHandler(
                        world,
                        player,
                        ()->generation+1L,
                        player.equipment(),
                        player.combatStyles(),
                        npcs
                    );

            ByteArrayOutputStream staleGenerationBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result
                staleGeneration=
                    staleGenerationHandler.handle(
                        new NpcAction(
                            72,
                            rangedView.sceneIndex
                        ),
                        rangedView,
                        writer(
                            staleGenerationBytes
                        )
                    );

            require(
                staleGeneration.status==
                    LocalCanonicalNpcAttackHandler
                        .Status.STALE_PLAYER&&
                world.npcLifecycle()
                    .get(ranged.id)
                    .hitpoints==20&&
                staleGenerationBytes.size()==0,
                "stale player generation mutated HP"
            );

            NpcEntity foreign=
                npcs.spawnMirroredNpc(
                    attackDefinition,
                    3088,
                    3496,
                    null,
                    player.movement(),
                    relayWriter
                );

            ByteArrayOutputStream foreignBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result
                foreignResult=
                    handler.handle(
                        new NpcAction(
                            72,
                            foreign.sceneIndex
                        ),
                        foreign,
                        writer(foreignBytes)
                    );

            require(
                foreignResult.status==
                    LocalCanonicalNpcAttackHandler
                        .Status.NONCANONICAL_SCENE&&
                foreignBytes.size()==0,
                "noncanonical attack scene was accepted"
            );

            WorldNpc mismatchTarget=
                canonicalTarget(
                    world,
                    attackDefinition,
                    3088,
                    3494,
                    20
                );

            NpcEntity mismatchView=
                npcs.spawnMirroredNpc(
                    alternateAttackDefinition,
                    3088,
                    3494,
                    null,
                    player.movement(),
                    relayWriter
                );

            mismatchView.bindCanonicalId(
                mismatchTarget.id
            );

            ByteArrayOutputStream mismatchBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result
                mismatch=
                    handler.handle(
                        new NpcAction(
                            72,
                            mismatchView.sceneIndex
                        ),
                        mismatchView,
                        writer(mismatchBytes)
                    );

            require(
                mismatch.status==
                    LocalCanonicalNpcAttackHandler
                        .Status.DEFINITION_MISMATCH&&
                world.npcLifecycle()
                    .get(mismatchTarget.id)
                    .hitpoints==20&&
                mismatchBytes.size()==0,
                "definition-mismatched projection mutated HP"
            );

            WorldNpc staleTarget=
                canonicalTarget(
                    world,
                    attackDefinition,
                    3087,
                    3496,
                    20
                );

            NpcEntity staleView=
                project(
                    world,
                    staleTarget,
                    relayWriter,
                    npcs
                );

            require(
                world.npcs().remove(
                    staleTarget.id
                ),
                "stale target fixture removal"
            );

            ByteArrayOutputStream staleBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result
                stale=
                    handler.handle(
                        new NpcAction(
                            72,
                            staleView.sceneIndex
                        ),
                        staleView,
                        writer(staleBytes)
                    );

            require(
                stale.status==
                    LocalCanonicalNpcAttackHandler
                        .Status.STALE_CANONICAL&&
                world.npcLifecycle()
                    .get(staleTarget.id)
                    .hitpoints==20&&
                staleBytes.size()==0,
                "stale canonical projection mutated HP"
            );

            NpcEntity dummy=
                new NpcEntity(
                    NpcRegistry.PVM_DUMMY_INDEX,
                    1489,
                    3088,
                    3495
                );

            require(
                LocalPendingRequestDispatcher
                    .isCombatAttackAction(
                        new NpcAction(
                            72,
                            dummy.sceneIndex
                        ),
                        dummy
                    ),
                "combat dummy no longer owned by legacy path"
            );

            System.out.println(
                "CANONICAL_NPC_ATTACK_CLICK_PASS "+
                "canonicalSceneIdentity=true "+
                "exactWorldNpc=true "+
                "exactPlayerGeneration=true "+
                "worldLifecycleAuthority=true "+
                "inRangeDamageOnce=true "+
                "lifecycleHitPresentation=true "+
                "lethalDeath=true "+
                "deadNoDuplicate=true "+
                "outOfRangeNoDamage=true "+
                "staleNoDamage=true "+
                "dummyLegacyOwned=true "+
                "autoRepeat=false "+
                "chaseOwned=false "+
                "rewardsOwned=false"
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                relayWriter
            );
            world.unregisterPlayer(
                player,
                generation
            );
            world.close();
        }
    }

    private static int[] attackDefinitions(){
        int first=-1;
        int second=-1;

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

            if(route.service!=
                    NpcInteractionRouter.Service.ATTACK||
               CombatTargetRepository
                    .isCombatDummy(definition))
                continue;

            if(first<0){
                first=definition;
                continue;
            }

            second=definition;
            break;
        }

        require(
            first>=0&&second>=0,
            "exact action corpus needs two non-dummy option-2 Attack definitions"
        );

        return new int[]{first,second};
    }

    private static WorldNpc canonicalTarget(
        World world,
        int definitionId,
        int x,
        int y,
        int maxHitpoints
    ){
        WorldNpc npc=
            world.npcs().spawn(
                definitionId,
                x,
                y,
                0
            );

        world.npcLifecycle()
            .register(
                npc,
                maxHitpoints,
                "CUSTOM_LOCALLAB_ATTACK_CLICK_TEST"
            );

        return npc;
    }

    private static NpcEntity project(
        World world,
        WorldNpc npc,
        ServerPacketWriter relayWriter,
        NpcRegistry npcs
    )throws Exception{
        SharedNpcWorldRelay.trackCanonicalNpc(
            world,
            npc
        );

        SharedNpcWorldRelay.syncRemotePets(
            relayWriter
        );

        NpcEntity view=
            npcs.canonical(
                npc.id
            );

        require(
            view!=null&&
            npc.id.equals(
                view.canonicalId()
            ),
            "canonical projection missing id="+
            npc.id
        );

        return view;
    }

    private static byte[] expectedHitPacket(
        NpcRegistry npcs,
        NpcEntity target,
        int damage,
        int hitpoints,
        int maxHitpoints
    )throws Exception{
        ArrayList<NpcSyncEncoder.Update> updates=
            new ArrayList<>();

        NpcSyncEncoder.Mask mask=
            NpcSyncEncoder.Mask.singleHit(
                damage,
                1,
                hitpoints,
                maxHitpoints
            );

        for(NpcEntity npc:npcs.snapshot())
            updates.add(
                npc==target
                    ?NpcSyncEncoder.Update.mask(
                        npc,
                        mask
                    )
                    :NpcSyncEncoder.Update.retain(
                        npc
                    )
            );

        byte[] body=
            NpcSyncEncoder.encode(
                updates,
                Collections.emptyList(),
                0,
                0
            );

        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        writer(out).varShort(
            65,
            body
        );

        return out.toByteArray();
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
            throw new AssertionError(
                label
            );
    }

    private LocalCanonicalNpcAttackHandlerTest(){}
}
