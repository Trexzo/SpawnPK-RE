package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.*;

public final class RelaySourcePresentationEventCleanupTest {
    public static void main(String[] args)
        throws Exception {

        int relayWorldBaseline=
            mapSize(
                SharedNpcWorldRelay.class,
                "BY_WORLD"
            );
        int relayWriterBaseline=
            mapSize(
                SharedNpcWorldRelay.class,
                "BY_WRITER"
            );

        World world=
            World.isolatedForTest(60_000L);
        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGeneration=
            world.registerPlayer(
                source,
                "relay-source-cleanup"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "relay-viewer-cleanup"
            );

        ServerPacketWriter sourceWriterA=
            writer(1);
        ServerPacketWriter sourceWriterB=
            writer(5);
        ServerPacketWriter viewerWriter=
            writer(9);

        try{
            SharedNpcWorldRelay.register(
                sourceWriterA,
                world,
                source,
                new NpcRegistry(
                    new DevAuthorityWorkbench()
                ),
                source.movement()
            );

            SharedNpcWorldRelay.register(
                viewerWriter,
                world,
                viewer,
                new NpcRegistry(
                    new DevAuthorityWorkbench()
                ),
                viewer.movement()
            );

            long now=
                System.currentTimeMillis();

            boolean queued=
                world.npcPresentationEvents()
                    .enqueue(
                        now,
                        source.id(),
                        WorldNpcPresentationEvents
                            .Target.scene(
                                129,
                                1488
                            ),
                        NpcSyncEncoder.Mask
                            .forceText(
                                "old-source"
                            ),
                        0L,
                        Collections.singleton(
                            viewer.id()
                        )
                    );

            if(!queued)
                throw new AssertionError(
                    "old source event not queued"
                );

            if(world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        now
                    ).size()!=1)
                throw new AssertionError(
                    "old source event missing before replacement"
                );

            SharedNpcWorldRelay.register(
                sourceWriterB,
                world,
                source,
                new NpcRegistry(
                    new DevAuthorityWorkbench()
                ),
                source.movement()
            );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        now+1L
                    ).isEmpty())
                throw new AssertionError(
                    "old source event survived writer replacement"
                );

            if(world.npcPresentationEvents()
                    .size()!=0)
                throw new AssertionError(
                    "old source event remained queued size="+
                    world.npcPresentationEvents()
                        .size()
                );

            if(writerMap(
                    SharedNpcWorldRelay.class
                ).containsKey(
                    sourceWriterA
                ))
                throw new AssertionError(
                    "old source writer retained"
                );

            if(!writerMap(
                    SharedNpcWorldRelay.class
                ).containsKey(
                    sourceWriterB
                ))
                throw new AssertionError(
                    "replacement source writer missing"
                );

            if(!writerMap(
                    SharedNpcWorldRelay.class
                ).containsKey(
                    viewerWriter
                ))
                throw new AssertionError(
                    "viewer context removed by source replacement"
                );

            boolean replacementQueued=
                world.npcPresentationEvents()
                    .enqueue(
                        now+2L,
                        source.id(),
                        WorldNpcPresentationEvents
                            .Target.scene(
                                129,
                                1488
                            ),
                        NpcSyncEncoder.Mask
                            .forceText(
                                "new-source"
                            ),
                        0L,
                        Collections.singleton(
                            viewer.id()
                        )
                    );

            if(!replacementQueued||
               world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        now+2L
                    ).size()!=1)
                throw new AssertionError(
                    "replacement source could not queue new event"
                );

            SharedNpcWorldRelay.unregister(
                sourceWriterB
            );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        now+3L
                    ).isEmpty())
                throw new AssertionError(
                    "source unregister retained replacement event"
                );

            if(!writerMap(
                    SharedNpcWorldRelay.class
                ).containsKey(
                    viewerWriter
                ))
                throw new AssertionError(
                    "viewer removed by source unregister"
                );

            SharedNpcWorldRelay.unregister(
                viewerWriter
            );

            if(mapSize(
                    SharedNpcWorldRelay.class,
                    "BY_WORLD"
                )!=relayWorldBaseline||
               mapSize(
                    SharedNpcWorldRelay.class,
                    "BY_WRITER"
                )!=relayWriterBaseline)
                throw new AssertionError(
                    "relay maps did not return to baseline"
                );

            System.out.println(
                "RELAY_SOURCE_PRESENTATION_EVENT_CLEANUP_PASS "+
                "oldSourceEventPurged=true "+
                "viewerPreserved=true "+
                "replacementSourceUsable=true "+
                "sourceUnregisterPurges=true "+
                "baselineRestored=true"
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                sourceWriterA
            );
            SharedNpcWorldRelay.unregister(
                sourceWriterB
            );
            SharedNpcWorldRelay.unregister(
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

    private static int mapSize(
        Class<?> relay,
        String fieldName
    )throws Exception{
        Field field=
            relay.getDeclaredField(
                fieldName
            );
        field.setAccessible(true);

        synchronized(relay){
            return ((Map<?,?>)
                field.get(null)).size();
        }
    }

    private static Map<?,?> writerMap(
        Class<?> relay
    )throws Exception{
        Field field=
            relay.getDeclaredField(
                "BY_WRITER"
            );
        field.setAccessible(true);

        synchronized(relay){
            @SuppressWarnings("unchecked")
            IdentityHashMap<Object,Object> map=
                (IdentityHashMap<Object,Object>)
                field.get(null);

            return new IdentityHashMap<>(
                map
            );
        }
    }

    private RelaySourcePresentationEventCleanupTest(){}
}
