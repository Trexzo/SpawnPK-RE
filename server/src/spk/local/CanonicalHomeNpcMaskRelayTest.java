package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class CanonicalHomeNpcMaskRelayTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer source=new WorldPlayer();
        WorldPlayer viewer=new WorldPlayer();

        world.registerPlayer(source,"opensrc");
        world.registerPlayer(viewer,"src");

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();
        OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue();

        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(new int[]{1,2,3,4})
            );
        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerQueue,
                new IsaacCipher(new int[]{5,6,7,8})
            );

        Player81WorldSync.register(
            sourceWriter,
            world,
            source,
            new DevAuthorityWorkbench()
        );
        Player81WorldSync.register(
            viewerWriter,
            world,
            viewer,
            new DevAuthorityWorkbench()
        );

        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                source.id()
            );
        NpcRegistry viewerNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                viewer.id()
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
            sourceWriter.varShort(
                81,
                BootstrapPackets.player81Idle()
            );
            viewerWriter.varShort(
                81,
                BootstrapPackets.player81Idle()
            );

            sourceNpcs.bootstrapHome(
                sourceWriter,
                source.movement(),
                source.petState(),
                new HomeWorldRuntimePlan(
                    world.homeNpcs()
                )
            );
            viewerNpcs.bootstrapHome(
                viewerWriter,
                viewer.movement(),
                viewer.petState(),
                new HomeWorldRuntimePlan(
                    world.homeNpcs()
                )
            );

            NpcEntity sourceTarget=null;
            NpcEntity viewerTarget=null;

            for(NpcEntity candidate:
                sourceNpcs.snapshot()){
                if(candidate.canonicalId()==null)
                    continue;

                NpcEntity matching=
                    viewerNpcs.canonical(
                        candidate.canonicalId()
                    );

                if(matching!=null){
                    sourceTarget=candidate;
                    viewerTarget=matching;
                    break;
                }
            }

            if(sourceTarget==null||
               viewerTarget==null)
                throw new AssertionError(
                    "no shared canonical HOME projection found"
                );

            if(!HomeWorldRuntimePlan.isHomeWorldSceneIndex(
                    sourceTarget.sceneIndex)||
               !HomeWorldRuntimePlan.isHomeWorldSceneIndex(
                    viewerTarget.sceneIndex))
                throw new AssertionError(
                    "selected canonical actor is not HOME"
                );

            if(!sourceTarget.canonicalId().equals(
                    viewerTarget.canonicalId()))
                throw new AssertionError(
                    "viewers do not share canonical HOME identity"
                );

            drain(sourceQueue);
            drain(viewerQueue);

            sourceWriter.varShort(
                81,
                CombatSync.player81AnimationAndInteraction(
                    15552,
                    sourceTarget.sceneIndex
                )
            );

            if(viewerQueue.queuedBytes()!=0)
                throw new AssertionError(
                    "source player event bypassed viewer polling barrier"
                );

            sourceNpcs.sendMask(
                sourceTarget,
                NpcSyncEncoder.Mask.forceText(
                    "CANONICAL_HOME"
                ),
                sourceWriter
            );

            if(viewerQueue.queuedBytes()!=0)
                throw new AssertionError(
                    "canonical HOME mask overtook packet81 barrier"
                );

            viewerWriter.varShort(
                81,
                BootstrapPackets.player81Idle()
            );

            ByteArrayOutputStream delivered=
                new ByteArrayOutputStream();
            viewerQueue.drainTo(
                delivered,
                1<<20
            );

            String raw=
                new String(
                    delivered.toByteArray(),
                    StandardCharsets.ISO_8859_1
                );

            if(!raw.contains("CANONICAL_HOME\n"))
                throw new AssertionError(
                    "canonical HOME force-text mask was not delivered"
                );

            if(viewerNpcs.canonical(
                    sourceTarget.canonicalId())!=
               viewerTarget)
                throw new AssertionError(
                    "canonical target lookup changed during delivery"
                );

            System.out.println(
                "CANONICAL_HOME_NPC_MASK_RELAY_PASS "+
                "entity="+sourceTarget.canonicalId()+
                " sourceScene="+sourceTarget.sceneIndex+
                " viewerScene="+viewerTarget.sceneIndex+
                " reflection=false"+
                " packet81Barrier=true"
            );
        }finally{
            SharedNpcWorldRelay.unregister(sourceWriter);
            SharedNpcWorldRelay.unregister(viewerWriter);
            Player81WorldSync.unregister(sourceWriter);
            Player81WorldSync.unregister(viewerWriter);
            world.unregisterPlayer(source);
            world.unregisterPlayer(viewer);
            world.close();
        }
    }

    private static void drain(
        OutboundPacketQueue queue
    )throws IOException{
        ByteArrayOutputStream sink=
            new ByteArrayOutputStream();
        queue.drainTo(sink,1<<20);
    }
}
