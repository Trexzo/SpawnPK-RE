package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Map;

public final class Player81RemoteGenerationFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        WorldPlayer viewer=
            new WorldPlayer();
        WorldPlayer remote=
            new WorldPlayer();

        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "player81-viewer"
            );
        long remoteGenerationA=
            world.registerPlayer(
                remote,
                "player81-remote"
            );

        ServerPacketWriter viewerWriter=
            writer(1);
        ServerPacketWriter remoteWriter=
            writer(5);

        Player81WorldSync.Context viewerContext=
            Player81WorldSync.register(
                viewerWriter,
                world,
                viewer,
                new DevAuthorityWorkbench()
            );
        Player81WorldSync.Context remoteContextA=
            Player81WorldSync.register(
                remoteWriter,
                world,
                remote,
                new DevAuthorityWorkbench()
            );

        ServerPacketWriter remoteWriterB=null;

        try{
            byte[] initial=
                Player81WorldSync.transformForTest(
                    viewerContext,
                    BootstrapPackets.player81Idle()
                );

            Bits bits=
                new Bits(initial);

            eq(0,bits.read(1),"initial viewer idle");
            eq(0,bits.read(8),"initial old remote count");

            int remoteIndex=
                bits.read(11);

            ok(
                remoteIndex>1&&remoteIndex<2047,
                "initial remote index"
            );

            eq(1,bits.read(1),"initial appearance mask");
            eq(1,bits.read(1),"initial clear queue");
            bits.read(5);
            bits.read(5);
            eq(2047,bits.read(11),"initial sentinel");

            long trackGenerationA=
                visibleGeneration(
                    viewerContext,
                    remote.id()
                );

            if(trackGenerationA!=remoteGenerationA)
                throw new AssertionError(
                    "initial track generation expected="+
                    remoteGenerationA+
                    " actual="+
                    trackGenerationA
                );

            Player81WorldSync.transformForTest(
                remoteContextA,
                BootstrapPackets.player81WalkStep(4)
            );

            Player81WorldSync.transformForTest(
                remoteContextA,
                CombatSync.player81AnimationAndGfx(
                    827,
                    1310,
                    0,
                    0
                )
            );

            if(!world.unregisterPlayer(
                    remote,
                    remoteGenerationA
                ))
                throw new AssertionError(
                    "remote generation A unregister failed"
                );

            long remoteGenerationB=
                world.registerPlayer(
                    remote,
                    "player81-remote"
                );

            if(remoteGenerationB==remoteGenerationA)
                throw new AssertionError(
                    "remote generation did not advance"
                );

            byte[] removal=
                Player81WorldSync.transformForTest(
                    viewerContext,
                    BootstrapPackets.player81Idle()
                );

            bits=
                new Bits(removal);

            eq(0,bits.read(1),"removal viewer idle");
            eq(1,bits.read(8),"removal old remote count");
            eq(1,bits.read(1),"removal changed");
            eq(3,bits.read(2),"generation change removal type");
            eq(2047,bits.read(11),"removal sentinel");

            if(Player81WorldSync.clientIndexFor(
                    viewerWriter,
                    remote
                )!=-1)
                throw new AssertionError(
                    "replacement generation reused stale client-index track"
                );

            if(viewerContext.resolveVisible(
                    remoteIndex
                )!=null)
                throw new AssertionError(
                    "replacement generation resolved through stale track"
                );

            if(visibleGeneration(
                    viewerContext,
                    remote.id()
                )!=-1L)
                throw new AssertionError(
                    "old generation track survived removal packet"
                );

            byte[] freshAdd=
                Player81WorldSync.transformForTest(
                    viewerContext,
                    BootstrapPackets.player81Idle()
                );

            bits=
                new Bits(freshAdd);

            eq(0,bits.read(1),"fresh-add viewer idle");
            eq(0,bits.read(8),"fresh-add old remote count");

            int freshIndex=
                bits.read(11);

            ok(
                freshIndex>1&&freshIndex<2047,
                "fresh remote index"
            );

            eq(1,bits.read(1),"fresh appearance mask");
            eq(1,bits.read(1),"fresh clear queue");
            bits.read(5);
            bits.read(5);
            eq(2047,bits.read(11),"fresh-add sentinel");

            long trackGenerationB=
                visibleGeneration(
                    viewerContext,
                    remote.id()
                );

            if(trackGenerationB!=remoteGenerationB)
                throw new AssertionError(
                    "fresh track generation expected="+
                    remoteGenerationB+
                    " actual="+
                    trackGenerationB
                );

            byte[] stable=
                Player81WorldSync.transformForTest(
                    viewerContext,
                    BootstrapPackets.player81Idle()
                );

            bits=
                new Bits(stable);

            eq(0,bits.read(1),"stable viewer idle");
            eq(1,bits.read(8),"stable old remote count");
            eq(
                0,
                bits.read(1),
                "old generation motion/event replayed as replacement"
            );
            eq(2047,bits.read(11),"stable sentinel");

            remoteWriterB=
                writer(9);

            Player81WorldSync.Context remoteContextB=
                Player81WorldSync.register(
                    remoteWriterB,
                    world,
                    remote,
                    new DevAuthorityWorkbench()
                );

            if(remoteContextB.ownerGeneration!=
                    remoteGenerationB)
                throw new AssertionError(
                    "replacement source context generation mismatch"
                );

            System.out.println(
                "PLAYER81_REMOTE_GENERATION_FENCE_PASS "+
                "generationATrackCaptured=true "+
                "replacementRemovedOldTrack=true "+
                "staleLookupRejected=true "+
                "generationBAddedFresh=true "+
                "oldCursorsNotReplayed=true"
            );

            world.unregisterPlayer(
                remote,
                remoteGenerationB
            );
        }finally{
            Player81WorldSync.unregister(
                viewerWriter
            );
            Player81WorldSync.unregister(
                remoteWriter
            );
            if(remoteWriterB!=null)
                Player81WorldSync.unregister(
                    remoteWriterB
                );

            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    viewerGeneration
                );

            if(remote.registered())
                world.unregisterPlayer(
                    remote,
                    remote.generation()
                );

            world.close();
        }
    }

    private static ServerPacketWriter writer(
        int seed
    ){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(
                new int[]{
                    seed,
                    seed+1,
                    seed+2,
                    seed+3
                }
            )
        );
    }

    private static long visibleGeneration(
        Player81WorldSync.Context context,
        EntityId id
    )throws Exception{
        Object track=
            context.visible.get(id);

        if(track==null)
            return -1L;

        Field generation=
            track.getClass()
                .getDeclaredField(
                    "generation"
                );
        generation.setAccessible(true);
        return generation.getLong(track);
    }

    private static void ok(
        boolean value,
        String message
    ){
        if(!value)
            throw new AssertionError(message);
    }

    private static void eq(
        int expected,
        int actual,
        String message
    ){
        if(expected!=actual)
            throw new AssertionError(
                message+
                " expected="+expected+
                " actual="+actual
            );
    }

    private static final class Bits{
        final byte[] bytes;
        int bit;

        Bits(byte[] bytes){
            this.bytes=bytes;
        }

        int read(int count){
            int value=0;
            for(int i=0;i<count;i++){
                value=(value<<1)|
                    ((bytes[bit>>>3]>>
                        (7-(bit&7)))&1);
                bit++;
            }
            return value;
        }
    }

    private Player81RemoteGenerationFenceTest(){}
}
