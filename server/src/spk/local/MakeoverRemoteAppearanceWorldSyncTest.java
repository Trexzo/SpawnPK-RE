package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Focused Make-over regression for the existing Player81WorldSync relay path.
 *
 * Proves that the same packet-81 appearance refresh emitted after C2S101 is:
 * 1) published as a world-visible source event, and
 * 2) consumed by a nearby viewer with the exact changed appearance block
 *    present in the viewer's packet-81 wire payload.
 */
public final class MakeoverRemoteAppearanceWorldSyncTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);

        WorldPlayer source=new WorldPlayer();
        WorldPlayer viewer=new WorldPlayer();

        ByteArrayOutputStream sourceWire=
            new ByteArrayOutputStream();
        ByteArrayOutputStream viewerWire=
            new ByteArrayOutputStream();

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();
        OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue();

        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );
        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerQueue,
                new IsaacCipher(
                    new int[]{5,6,7,8}
                )
            );

        DevAuthorityWorkbench sourceDev=
            new DevAuthorityWorkbench();
        DevAuthorityWorkbench viewerDev=
            new DevAuthorityWorkbench();

        try{
            world.registerPlayer(
                source,
                "makeover_source"
            );
            world.registerPlayer(
                viewer,
                "makeover_viewer"
            );

            Player81WorldSync.register(
                sourceWriter,
                world,
                source,
                sourceDev
            );
            Player81WorldSync.register(
                viewerWriter,
                world,
                viewer,
                viewerDev
            );

            PlayerPresentationService sourcePresentation=
                new PlayerPresentationService(
                    sourceDev
                );
            PlayerPresentationService viewerPresentation=
                new PlayerPresentationService(
                    viewerDev
                );

            // Publish the source's initial/default appearance.
            sourcePresentation.refresh(
                source.username(),
                source.equipment(),
                source.playerState(),
                sourceWriter
            );
            drain(
                sourceQueue,
                sourceWire
            );
            drain(
                sourceQueue,
                sourceWire
            );

            long initialSourceEvent=
                Player81WorldSync
                    .latestPublishedEventSequence(
                        sourceWriter
                    );

            if(initialSourceEvent<=0L)
                throw new AssertionError(
                    "source default appearance was not published"
                );

            // Prime the viewer's remote track for the source.
            viewerPresentation.refresh(
                viewer.username(),
                viewer.equipment(),
                viewer.playerState(),
                viewerWriter
            );
            drain(
                viewerQueue,
                viewerWire
            );
            drain(
                viewerQueue,
                viewerWire
            );

            long initiallyConsumed=
                Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id()
                    );

            if(initiallyConsumed!=initialSourceEvent)
                throw new AssertionError(
                    "viewer did not prime source event consumed="+
                    initiallyConsumed+
                    " expected="+
                    initialSourceEvent
                );

            int[] kits={
                45,-1,56,61,67,70,79
            };
            int[] colours={
                11,15,14,5,23
            };

            if(!source.playerState()
                    .setCharacterAppearance(
                        CharacterDesignProfile.FEMALE,
                        kits,
                        colours
                    ))
                throw new AssertionError(
                    "female Make-over state rejected"
                );

            int viewerBytesBefore=
                viewerWire.size();

            // This is the same presentation path used by LocalSession's
            // Make-over accept bridge after CharacterDesignRequest is applied.
            sourcePresentation.refresh(
                source.username(),
                source.equipment(),
                source.playerState(),
                sourceWriter
            );

            long makeoverSourceEvent=
                Player81WorldSync
                    .latestPublishedEventSequence(
                        sourceWriter
                    );

            if(makeoverSourceEvent<=initialSourceEvent)
                throw new AssertionError(
                    "Make-over appearance did not publish a new world event old="+
                    initialSourceEvent+
                    " new="+
                    makeoverSourceEvent
                );

            // Any subsequent viewer packet-81 sync must consume the source event.
            viewerPresentation.refresh(
                viewer.username(),
                viewer.equipment(),
                viewer.playerState(),
                viewerWriter
            );

            long makeoverConsumed=
                Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id()
                    );

            if(makeoverConsumed!=makeoverSourceEvent)
                throw new AssertionError(
                    "viewer did not consume Make-over event consumed="+
                    makeoverConsumed+
                    " expected="+
                    makeoverSourceEvent
                );

            byte[] allViewerWire=
                viewerWire.toByteArray();
            byte[] viewerDelta=
                Arrays.copyOfRange(
                    allViewerWire,
                    viewerBytesBefore,
                    allViewerWire.length
                );

            byte[] expectedRemoteAppearance=
                BootstrapPackets.appearanceBlock(
                    source.username(),
                    source.equipment()
                        .appearanceItems(),
                    source.playerState(),
                    null
                );

            if(!contains(
                    viewerDelta,
                    expectedRemoteAppearance))
                throw new AssertionError(
                    "viewer packet-81 delta does not contain exact changed source appearance"
                );

            System.out.println(
                "MAKEOVER_REMOTE_APPEARANCE_WORLD_SYNC_PASS "+
                "sourceEvent="+makeoverSourceEvent+
                " viewerConsumed="+makeoverConsumed+
                " gender=FEMALE "+
                "kits="+Arrays.toString(kits)+
                " colours="+Arrays.toString(colours)+
                " remoteAppearanceBytes="+
                expectedRemoteAppearance.length+
                " viewerDeltaBytes="+
                viewerDelta.length
            );
        }finally{
            Player81WorldSync.unregister(
                viewerWriter
            );
            Player81WorldSync.unregister(
                sourceWriter
            );
            world.unregisterPlayer(
                viewer
            );
            world.unregisterPlayer(
                source
            );
            world.close();
        }
    }

    private static void drain(
        OutboundPacketQueue queue,
        ByteArrayOutputStream wire
    )throws Exception{
        while(queue.queuedBytes()>0)
            queue.drainTo(
                wire,
                1<<20
            );
    }

    private static boolean contains(
        byte[] haystack,
        byte[] needle
    ){
        if(haystack==null||
           needle==null||
           needle.length==0||
           needle.length>haystack.length)
            return false;

        outer:
        for(int i=0;
            i<=haystack.length-needle.length;
            i++){
            for(int j=0;
                j<needle.length;
                j++)
                if(haystack[i+j]!=needle[j])
                    continue outer;
            return true;
        }

        return false;
    }
}
