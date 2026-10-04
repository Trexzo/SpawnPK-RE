package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PetChargeIncrementCommitFenceTest {
    private static final int QUEUE_CAPACITY=1<<20;

    private static final class Fixture {
        final MovementState movement=new MovementState();
        final PetState petState=new PetState();
        final PetEffectState effects=new PetEffectState();
        final DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        final NpcRegistry npcs=new NpcRegistry(dev);
        final OutboundPacketQueue queue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        final ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{981,982,983,984}
                )
            );
        final LocalPetRuntimeCommandHandler handler=
            new LocalPetRuntimeCommandHandler(
                petState,
                effects,
                npcs,
                movement
            );
        final PetDefinitionRepository.Def def;

        Fixture()throws Exception{
            npcs.bootstrap(
                writer,
                movement,
                petState
            );
            drain(queue);

            def=
                PetDefinitionRepository.get(
                    24019
                );

            if(def==null||
               !PetPresentationProfile.isChargePet(
                    def.itemId,
                    def.npcId))
                throw new AssertionError(
                    "charge-pet fixture missing"
                );

            String spawn=
                npcs.spawnPet(
                    def,
                    movement,
                    writer
                );

            if(spawn==null||
               !spawn.startsWith(
                    "PET_SPAWN_OK"))
                throw new AssertionError(
                    "charge-pet spawn failed result="+
                    spawn
                );

            petState.activate(def);
            effects.onPetChanged(
                def.itemId,
                def.npcId
            );

            drain(queue);
        }
    }

    public static void main(String[] args)throws Exception{
        successfulSettlement();
        outerSourceFailureRetires();
        nativeSettlementFailureRetires();
        standaloneCompatibility();

        System.out.println(
            "PET_CHARGE_INCREMENT_COMMIT_FENCE_PASS "+
            "sourceDamagePreserved=true "+
            "outerAbortPolicyExplicit=true "+
            "nativeStateAfterBytes=true "+
            "effectStateAfterBytes=true "+
            "standaloneCompatibility=true"
        );
    }

    private static void successfulSettlement()
        throws Exception{
        Fixture f=new Fixture();

        f.writer.beginBatch();
        f.writer.fixed(
            134,
            BootstrapPackets.skill134(
                PlayerState.HITPOINTS,
                0,
                1
            )
        );

        String prepared=
            f.handler.applyDamage(
                75,
                10_000L,
                f.writer,
                "COMBAT_M2"
            );

        if(!prepared.contains(
                "presentation=DEFERRED_UNTIL_SOURCE_COMMIT")||
           !f.handler.preparedCombatDamagePending()||
           f.effects.charge()!=0||
           f.effects.accumulatedDamage()!=0||
           f.effects.lastDamageAtMs()!=0L||
           f.npcs.petNativeState()!=0)
            throw new AssertionError(
                "combat charge mutated before source commit result="+
                prepared
            );

        f.writer.endBatch();

        int sourceBytes=
            f.queue.queuedBytes();

        if(sourceBytes<=0)
            throw new AssertionError(
                "source transport did not commit"
            );

        String settled=
            f.handler
                .settlePreparedCombatDamageAfterSourceCommit(
                    f.writer
                );

        if(settled==null||
           !settled.contains(
                "chargeChanged=true")||
           f.handler.preparedCombatDamagePending()||
           f.effects.charge()!=1||
           f.effects.accumulatedDamage()!=75||
           f.effects.lastDamageAtMs()!=10_000L||
           f.npcs.petNativeState()!=1||
           f.queue.queuedBytes()<=sourceBytes)
            throw new AssertionError(
                "post-source combat charge did not settle once result="+
                settled
            );
    }

    private static void outerSourceFailureRetires()
        throws Exception{
        Fixture f=new Fixture();

        f.writer.beginBatch();
        f.writer.fixed(
            134,
            BootstrapPackets.skill134(
                PlayerState.HITPOINTS,
                0,
                1
            )
        );

        f.handler.applyDamage(
            75,
            20_000L,
            f.writer,
            "COMBAT_M2"
        );

        OutboundPacketQueue.BatchReservation pressure=
            OutboundPacketQueue.reserveBatch(
                f.queue,
                QUEUE_CAPACITY
            );

        boolean failed=false;
        try{
            try{
                LocalSession.endWorldTickBatch(
                    f.writer
                );
            }catch(IOException expected){
                failed=true;
            }

            if(!failed)
                throw new AssertionError(
                    "forced source admission failure did not escape"
                );

            if(!f.handler
                    .abortPreparedCombatDamageAfterSourceFailure(
                        f.writer
                    ))
                throw new AssertionError(
                    "prepared charge source-failure token missing"
                );
        }finally{
            pressure.release();
        }

        if(!f.writer.terminal()||
           f.handler.preparedCombatDamagePending()||
           f.effects.charge()!=0||
           f.effects.accumulatedDamage()!=0||
           f.effects.lastDamageAtMs()!=0L||
           f.npcs.petNativeState()!=0||
           f.queue.queuedBytes()!=0)
            throw new AssertionError(
                "source failure did not retire with exact pet preimage"
            );
    }

    private static void nativeSettlementFailureRetires()
        throws Exception{
        Fixture f=new Fixture();

        f.writer.beginBatch();
        f.writer.fixed(
            134,
            BootstrapPackets.skill134(
                PlayerState.HITPOINTS,
                0,
                1
            )
        );

        f.handler.applyDamage(
            75,
            30_000L,
            f.writer,
            "COMBAT_M2"
        );

        f.writer.endBatch();
        drain(f.queue);

        OutboundPacketQueue.BatchReservation pressure=
            OutboundPacketQueue.reserveBatch(
                f.queue,
                QUEUE_CAPACITY
            );

        boolean failed=false;
        try{
            f.handler
                .settlePreparedCombatDamageAfterSourceCommit(
                    f.writer
                );
        }catch(IOException expected){
            failed=true;
        }finally{
            pressure.release();
        }

        if(!failed||
           !f.writer.terminal()||
           f.handler.preparedCombatDamagePending()||
           f.effects.charge()!=0||
           f.effects.accumulatedDamage()!=0||
           f.effects.lastDamageAtMs()!=0L||
           f.npcs.petNativeState()!=0||
           f.queue.queuedBytes()!=0)
            throw new AssertionError(
                "native settlement failure changed semantic preimage or stayed live"
            );
    }

    private static void standaloneCompatibility()
        throws Exception{
        Fixture f=new Fixture();

        String result=
            f.handler.applyDamage(
                75,
                40_000L,
                f.writer,
                "MANUAL_BEHEMOTH_HIT"
            );

        if(!result.contains(
                "chargeChanged=true")||
           f.handler.preparedCombatDamagePending()||
           f.effects.charge()!=1||
           f.effects.accumulatedDamage()!=75||
           f.effects.lastDamageAtMs()!=40_000L||
           f.npcs.petNativeState()!=1||
           f.queue.queuedBytes()<=0)
            throw new AssertionError(
                "standalone damage compatibility changed result="+
                result
            );
    }

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();
        queue.drainTo(
            out,
            QUEUE_CAPACITY
        );
    }

    private PetChargeIncrementCommitFenceTest(){}
}
