package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RemotePetMirrorMaskRetryTest {
    public static void main(String[] args)throws Exception{
        assertPostAddMaskFailureDoesNotDuplicateMirror();
        assertDeferredMasksPreserveFifoOrder();

        System.out.println(
            "REMOTE_PET_MIRROR_MASK_RETRY_PASS "+
            "addCommittedOnce=true "+
            "failedMaskDeferred=true "+
            "stableSceneOnRetry=true "+
            "noDuplicateMirror=true "+
            "deferredMaskRetried=true "+
            "laterMaskQueuedBehindFailure=true "+
            "deferredMaskFifo=true"
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
                    "post-add mask failure did not leave exactly one authoritative mirror size="+
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

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "mask retry spawned duplicate mirror size="+
                    viewerNpcs.snapshot().size()
                );

            NpcEntity after=
                viewerNpcs.snapshot().get(0);

            if(after.sceneIndex!=scene)
                throw new AssertionError(
                    "mask retry changed mirror scene old="+
                    scene+
                    " new="+
                    after.sceneIndex
                );

            if(viewerOut.successfulWrites()!=2)
                throw new AssertionError(
                    "deferred interaction mask was not retried exactly once successfulWrites="+
                    viewerOut.successfulWrites()+
                    " attempts="+
                    viewerOut.attempts()
                );

            int attemptsAfterRetry=
                viewerOut.attempts();

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "steady-state sync duplicated mirror"
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


    private static void assertDeferredMasksPreserveFifoOrder()
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
                "mirror-mask-fifo-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "mirror-mask-fifo-viewer"
            );

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();

        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{711,712,713,714}
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
                    new int[]{715,716,717,718}
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
            PetDefinitionRepository.Def base=
                PetDefinitionRepository.get(
                    24019
                );

            if(base==null)
                throw new AssertionError(
                    "missing pet 24019 template"
                );

            PetDefinitionRepository.Def nativePet=
                new PetDefinitionRepository.Def(
                    base.itemId,
                    6650,
                    base.itemName,
                    "FIFO native-state fixture",
                    base.standAnim,
                    base.walkAnim,
                    base.turn180Anim,
                    base.turn90CWAnim,
                    base.turn90CCWAnim,
                    base.size,
                    base.models,
                    "TEST_NATIVE_STATE_FIFO"
                );

            String spawn=
                sourceNpcs.spawnPet(
                    nativePet,
                    source.movement(),
                    sourceWriter
                );

            if(spawn==null||
               !spawn.startsWith(
                    "PET_SPAWN_OK"
               ))
                throw new AssertionError(
                    "native-state source pet setup failed: "+
                    spawn
                );

            String nativeState=
                sourceNpcs.setPetNativeState(
                    2,
                    sourceWriter
                );

            if(nativeState==null||
               !nativeState.startsWith(
                    "PET_NATIVE_STATE_OK"
               ))
                throw new AssertionError(
                    "native-state fixture setup failed: "+
                    nativeState
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
                    "FIFO viewer did not establish source visibility"
                );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            /*
             * Attempt 1: mirrored add succeeds.
             * Attempt 2: interaction-target mask fails and is deferred.
             * The later native-state mask must queue behind it rather than
             * becoming attempt 3 in this same sync.
             */
            if(viewerOut.attempts()!=2)
                throw new AssertionError(
                    "later mirror mask overtook deferred FIFO attempts="+
                    viewerOut.attempts()
                );

            if(viewerOut.successfulWrites()!=1)
                throw new AssertionError(
                    "failing FIFO sync emitted a later mask successes="+
                    viewerOut.successfulWrites()
                );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "FIFO failure did not preserve exactly one mirror size="+
                    viewerNpcs.snapshot().size()
                );

            int scene=
                viewerNpcs.snapshot().get(0)
                    .sceneIndex;

            viewerOut.disableFailure();

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerOut.attempts()!=4||
               viewerOut.successfulWrites()!=3)
                throw new AssertionError(
                    "retry did not drain both deferred masks in FIFO attempts="+
                    viewerOut.attempts()+
                    " successes="+
                    viewerOut.successfulWrites()
                );

            if(viewerNpcs.snapshot().size()!=1||
               viewerNpcs.snapshot().get(0)
                    .sceneIndex!=scene)
                throw new AssertionError(
                    "FIFO retry changed authoritative mirror scene"
                );

            int attemptsAfterDrain=
                viewerOut.attempts();

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerOut.attempts()!=
                    attemptsAfterDrain)
                throw new AssertionError(
                    "drained FIFO emitted duplicate mask work before="+
                    attemptsAfterDrain+
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
