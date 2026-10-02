package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RemotePetMirrorMaskRetryTest {
    public static void main(String[] args)throws Exception{
        assertPostAddMaskFailureDoesNotDuplicateMirror();

        System.out.println(
            "REMOTE_PET_MIRROR_MASK_RETRY_PASS "+
            "addCommittedOnce=true "+
            "failedMaskDeferred=true "+
            "stableSceneOnRetry=true "+
            "noDuplicateMirror=true "+
            "deferredMaskRetried=true "+
            "deferredMaskBlocksLaterProjection=true "+
            "fifoDrainsBeforeMiniProjection=true "+
            "nativeMaskOrderedAfterDeferredInteraction=true"
        );
    }

    private static void assertPostAddMaskFailureDoesNotDuplicateMirror()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                600L
            );

        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGeneration=
            world.registerPlayer(
                source,
                "mirror-mask-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "mirror-mask-viewer"
            );

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();

        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{701,702,703,704}
                )
            );

        FailNthWriteOutputStream viewerOut=
            new FailNthWriteOutputStream(
                2
            );

        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerOut,
                new IsaacCipher(
                    new int[]{705,706,707,708}
                )
            );

        Player81WorldSync.register(
            sourceWriter,
            world,
            source,
            new DevAuthorityWorkbench()
        );

        Player81WorldSync.Context viewerSync=
            Player81WorldSync.register(
                viewerWriter,
                world,
                viewer,
                new DevAuthorityWorkbench()
            );

        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        NpcRegistry viewerNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        SharedNpcWorldRelay.register(
            sourceWriter,
            world,
            source,
            sourceNpcs,
            source.movement()
        );

        SharedNpcWorldRelay.register(
            viewerWriter,
            world,
            viewer,
            viewerNpcs,
            viewer.movement()
        );

        try{
            PetDefinitionRepository.Def pet=
                PetDefinitionRepository.get(
                    24019
                );

            if(pet==null)
                throw new AssertionError(
                    "missing pet 24019"
                );

            String spawn=
                sourceNpcs.spawnPet(
                    pet,
                    source.movement(),
                    sourceWriter
                );

            if(spawn==null||
               !spawn.startsWith(
                    "PET_SPAWN_OK"
               ))
                throw new AssertionError(
                    "source pet setup failed: "+
                    spawn
                );

            String nativeState=
                sourceNpcs.setPetNativeState(
                    1,
                    sourceWriter
                );

            if(nativeState==null||
               !nativeState.startsWith(
                    "PET_NATIVE_STATE_OK"
               ))
                throw new AssertionError(
                    "source native-state setup failed: "+
                    nativeState
                );

            MiniPetDefinitionRepository.Def mini=
                MiniPetDefinitionRepository.get(
                    22088
                );

            if(mini==null)
                throw new AssertionError(
                    "missing mini pet 22088"
                );

            String miniSpawn=
                sourceNpcs.spawnOrReplaceMiniPet(
                    mini,
                    source.movement(),
                    sourceWriter
                );

            if(miniSpawn==null||
               !miniSpawn.startsWith(
                    "MINIPET_SPAWN_OK"
               ))
                throw new AssertionError(
                    "source mini-pet setup failed: "+
                    miniSpawn
                );

            Player81WorldSync.transformForTest(
                viewerSync,
                BootstrapPackets.player81Idle()
            );

            if(Player81WorldSync.clientIndexFor(
                    viewerWriter,
                    source
                )<0)
                throw new AssertionError(
                    "viewer did not establish source visibility"
                );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerOut.attempts()!=2)
                throw new AssertionError(
                    "fixture did not fail on required post-add mask attempts="+
                    viewerOut.attempts()
                );

            if(viewerOut.successfulWrites()!=1)
                throw new AssertionError(
                    "expected only mirror add write to succeed before mask failure successes="+
                    viewerOut.successfulWrites()
                );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "deferred main mask allowed later mini/canonical projection size="+
                    viewerNpcs.snapshot().size()
                );

            NpcEntity first=
                viewerNpcs.snapshot().get(0);
            int scene=
                first.sceneIndex;

            viewerOut.disableFailure();

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=2)
                throw new AssertionError(
                    "retry did not drain deferred main mask before resuming mini projection size="+
                    viewerNpcs.snapshot().size()
                );

            NpcEntity after=
                null;

            for(NpcEntity npc:
                    viewerNpcs.snapshot())
                if(npc.sceneIndex==scene){
                    after=npc;
                    break;
                }

            if(after==null)
                throw new AssertionError(
                    "mask retry changed or lost stable main mirror scene="+
                    scene
                );

            if(viewerOut.successfulWrites()!=6)
                throw new AssertionError(
                    "retry packet order/count mismatch expected queued-main-mask/queued-native-mask/mini-add/mini-masks after initial add successes="+
                    viewerOut.successfulWrites()+
                    " attempts="+
                    viewerOut.attempts()
                );

            if(viewerOut.attempts()!=7)
                throw new AssertionError(
                    "retry did not consume exactly one failed write plus ordered follow-up packets attempts="+
                    viewerOut.attempts()
                );

            int attemptsAfterRetry=
                viewerOut.attempts();

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=2)
                throw new AssertionError(
                    "steady-state sync changed main+mini mirror cardinality"
                );

            if(viewerOut.attempts()!=
                    attemptsAfterRetry)
                throw new AssertionError(
                    "completed deferred mask was emitted again attemptsBefore="+
                    attemptsAfterRetry+
                    " after="+
                    viewerOut.attempts()
                );
        }finally{
            SharedNpcWorldRelay.unregister(
                sourceWriter
            );
            SharedNpcWorldRelay.unregister(
                viewerWriter
            );

            Player81WorldSync.unregister(
                sourceWriter
            );
            Player81WorldSync.unregister(
                viewerWriter
            );

            if(source.registered())
                world.unregisterPlayer(
                    source,
                    sourceGeneration
                );

            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    viewerGeneration
                );

            world.close();
        }
    }

    private static final class FailNthWriteOutputStream
        extends OutputStream {

        private final ByteArrayOutputStream delegate=
            new ByteArrayOutputStream();
        private final int failAt;
        private int attempts;
        private int successfulWrites;
        private final AtomicBoolean failureEnabled=
            new AtomicBoolean(true);

        FailNthWriteOutputStream(
            int failAt
        ){
            this.failAt=failAt;
        }

        synchronized int attempts(){
            return attempts;
        }

        synchronized int successfulWrites(){
            return successfulWrites;
        }

        void disableFailure(){
            failureEnabled.set(false);
        }

        @Override public void write(
            int value
        )throws IOException{
            byte[] one=
                new byte[]{(byte)value};
            write(
                one,
                0,
                1
            );
        }

        @Override public synchronized void write(
            byte[] bytes,
            int offset,
            int length
        )throws IOException{
            attempts++;

            if(failureEnabled.get()&&
               attempts==failAt)
                throw new IOException(
                    "EXPECTED_POST_ADD_MASK_FAILURE"
                );

            delegate.write(
                bytes,
                offset,
                length
            );
            successfulWrites++;
        }
    }

    private RemotePetMirrorMaskRetryTest(){}
}
