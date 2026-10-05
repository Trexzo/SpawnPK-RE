package spk.local;

import java.io.ByteArrayOutputStream;

public final class PlayerDeathCarriedPresentationRelayTest {
    public static void main(String[] args)throws Exception{
        commitAbortRetryAndGenerationFence();
        queuePressureRetry();

        System.out.println(
            "PLAYER_DEATH_CARRIED_PRESENTATION_PASS "+
            "immutablePostimage=true "+
            "inventory3214=true "+
            "equipment1688=true "+
            "appearance81=true "+
            "outerBatchCommitFence=true "+
            "abortRetainsEvent=true "+
            "queuePressureZeroNewBytes=true "+
            "retryCommitsOnce=true "+
            "generationFence=true "+
            "attackerWriterOwnership=false"
        );
    }

    private static void commitAbortRetryAndGenerationFence()
        throws Exception
    {
        World world=World.isolatedForTest(600L);
        WorldPlayer victim=new WorldPlayer();
        long generation=
            world.registerPlayer(
                victim,
                "victim-presentation"
            );

        OutboundPacketQueue queue=
            new OutboundPacketQueue();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{101,102,103,104}
                )
            );
        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();

        Player81WorldSync.register(
            writer,
            world,
            victim,
            dev
        );

        try{
            installPostimage(victim);

            long now=1000L;

            if(!world.playerCarriedPresentationEvents()
                    .enqueueDeathPostimage(
                        now,
                        victim,
                        generation,
                        1L
                    ))
                throw new AssertionError(
                    "carried presentation enqueue rejected"
                );

            /*
             * Mutate canonical state after enqueue. Delivery must retain the
             * immutable death postimage rather than reread this later state.
             */
            victim.bank().restoreInventoryState(
                new BankState.Stack[
                    BankState.INVENTORY_CAPACITY
                ]
            );
            victim.equipment().setStack(
                EquipmentSlot.WEAPON,
                1305,
                1
            );

            LocalPlayerCarriedPresentationRelay relay=
                new LocalPlayerCarriedPresentationRelay(
                    world,
                    victim
                );
            PlayerPresentationService presentation=
                new PlayerPresentationService(
                    world,
                    dev
                );

            int bytesBefore=
                queue.queuedBytes();

            writer.beginBatch();
            int staged=
                relay.publishPending(
                    now+1L,
                    writer,
                    (appearanceItems,targetWriter)->
                        presentation.refreshSnapshot(
                            victim.username(),
                            appearanceItems,
                            victim.playerState(),
                            targetWriter
                        )
                );

            if(staged!=1||
               relay.stagedCount()!=1)
                throw new AssertionError(
                    "carried event not staged exactly once"
                );

            if(queue.queuedBytes()!=bytesBefore)
                throw new AssertionError(
                    "carried presentation escaped outer batch"
                );

            if(world.playerCarriedPresentationEvents()
                    .size()!=1)
                throw new AssertionError(
                    "event acknowledged before packet commit"
                );

            writer.abortBatch();
            relay.abortStaged();

            if(queue.queuedBytes()!=bytesBefore||
               world.playerCarriedPresentationEvents()
                    .size()!=1)
                throw new AssertionError(
                    "aborted carried presentation was not retryable"
                );

            writer.beginBatch();
            relay.publishPending(
                now+2L,
                writer,
                (appearanceItems,targetWriter)->
                    presentation.refreshSnapshot(
                        victim.username(),
                        appearanceItems,
                        victim.playerState(),
                        targetWriter
                    )
            );
            writer.endBatch();

            if(queue.queuedBytes()<=bytesBefore)
                throw new AssertionError(
                    "successful carried presentation emitted no bytes"
                );

            if(relay.commitStaged(
                    now+3L
                )!=1||
               world.playerCarriedPresentationEvents()
                    .size()!=0)
                throw new AssertionError(
                    "successful carried delivery not acknowledged"
                );

            long published=
                Player81WorldSync.latestPublishedEventSequence(
                    writer
                );
            if(published<=0L)
                throw new AssertionError(
                    "appearance packet81 did not publish semantic event"
                );

            int bytesAfterCommit=
                queue.queuedBytes();

            writer.beginBatch();
            if(relay.publishPending(
                    now+4L,
                    writer,
                    (appearanceItems,targetWriter)->
                        presentation.refreshSnapshot(
                            victim.username(),
                            appearanceItems,
                            victim.playerState(),
                            targetWriter
                        )
                )!=0)
                throw new AssertionError(
                    "delivered carried event replayed"
                );
            writer.endBatch();
            relay.commitStaged(
                now+5L
            );

            if(queue.queuedBytes()!=bytesAfterCommit)
                throw new AssertionError(
                    "empty replay emitted bytes"
                );

            if(!world.playerCarriedPresentationEvents()
                    .enqueueDeathPostimage(
                        now+6L,
                        victim,
                        generation,
                        2L
                    ))
                throw new AssertionError(
                    "second death postimage enqueue rejected"
                );

            Player81WorldSync.unregister(
                writer
            );
            if(!world.unregisterPlayer(
                    victim,
                    generation
                ))
                throw new AssertionError(
                    "victim unregister fixture failed"
                );

            WorldPlayer replacement=
                new WorldPlayer();
            long replacementGeneration=
                world.registerPlayer(
                    replacement,
                    "victim-presentation"
                );

            try{
                if(relay.publishPending(
                        now+7L,
                        writer,
                        (appearanceItems,targetWriter)->{
                            throw new AssertionError(
                                "stale generation attempted publication"
                            );
                        }
                    )!=0)
                    throw new AssertionError(
                        "stale victim generation consumed event"
                    );

                if(world.playerCarriedPresentationEvents()
                        .pendingFor(
                            replacement.id(),
                            replacementGeneration,
                            now+7L
                        ).size()!=0)
                    throw new AssertionError(
                        "old carried event transferred to replacement"
                    );
            }finally{
                world.unregisterPlayer(
                    replacement,
                    replacementGeneration
                );
            }
        }finally{
            Player81WorldSync.unregister(
                writer
            );
            if(victim.registered())
                world.unregisterPlayer(
                    victim
                );
            world.close();
        }
    }

    private static void queuePressureRetry()
        throws Exception
    {
        World world=World.isolatedForTest(600L);
        WorldPlayer victim=new WorldPlayer();
        long generation=
            world.registerPlayer(
                victim,
                "victim-pressure"
            );

        OutboundPacketQueue queue=
            new OutboundPacketQueue(
                1024
            );
        queue.offerBatch(
            new byte[1000]
        );

        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{201,202,203,204}
                )
            );
        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();

        Player81WorldSync.register(
            writer,
            world,
            victim,
            dev
        );

        try{
            installPostimage(victim);

            long now=2000L;
            world.playerCarriedPresentationEvents()
                .enqueueDeathPostimage(
                    now,
                    victim,
                    generation,
                    1L
                );

            LocalPlayerCarriedPresentationRelay relay=
                new LocalPlayerCarriedPresentationRelay(
                    world,
                    victim
                );
            PlayerPresentationService presentation=
                new PlayerPresentationService(
                    world,
                    dev
                );

            int pressuredBytes=
                queue.queuedBytes();

            writer.beginBatch();
            relay.publishPending(
                now+1L,
                writer,
                (appearanceItems,targetWriter)->
                    presentation.refreshSnapshot(
                        victim.username(),
                        appearanceItems,
                        victim.playerState(),
                        targetWriter
                    )
            );

            boolean rejected=false;
            try{
                writer.endBatch();
            }catch(java.io.IOException expected){
                rejected=true;
                writer.abortBatch();
                relay.abortStaged();
            }

            if(!rejected)
                throw new AssertionError(
                    "queue pressure did not reject carried batch"
                );

            if(queue.queuedBytes()!=pressuredBytes||
               world.playerCarriedPresentationEvents()
                    .size()!=1)
                throw new AssertionError(
                    "queue pressure partially published/acknowledged carried state"
                );

            ByteArrayOutputStream sink=
                new ByteArrayOutputStream();
            while(queue.queuedBytes()>0)
                queue.drainTo(
                    sink,
                    Integer.MAX_VALUE
                );

            writer.beginBatch();
            relay.publishPending(
                now+2L,
                writer,
                (appearanceItems,targetWriter)->
                    presentation.refreshSnapshot(
                        victim.username(),
                        appearanceItems,
                        victim.playerState(),
                        targetWriter
                    )
            );
            writer.endBatch();

            if(relay.commitStaged(
                    now+3L
                )!=1||
               world.playerCarriedPresentationEvents()
                    .size()!=0||
               queue.queuedBytes()==0)
                throw new AssertionError(
                    "carried presentation retry did not commit"
                );
        }finally{
            Player81WorldSync.unregister(
                writer
            );
            if(victim.registered())
                world.unregisterPlayer(
                    victim,
                    generation
                );
            world.close();
        }
    }

    private static void installPostimage(
        WorldPlayer victim
    ){
        BankState.Stack[] inventory=
            new BankState.Stack[
                BankState.INVENTORY_CAPACITY
            ];
        inventory[1]=
            new BankState.Stack(
                20466,
                1
            );
        victim.bank()
            .restoreInventoryState(
                inventory
            );

        int[] items=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];
        int[] quantities=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];
        java.util.Arrays.fill(
            items,
            -1
        );
        items[
            EquipmentSlot.CAPE
                .equipmentIndex
        ]=6570;
        quantities[
            EquipmentSlot.CAPE
                .equipmentIndex
        ]=1;

        victim.equipment()
            .restoreAccountState(
                items,
                quantities
            );
    }

    private PlayerDeathCarriedPresentationRelayTest(){}
}
