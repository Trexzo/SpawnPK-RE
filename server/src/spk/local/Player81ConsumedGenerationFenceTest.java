package spk.local;

import java.io.ByteArrayOutputStream;

public final class Player81ConsumedGenerationFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGenerationA=
            world.registerPlayer(
                source,
                "consumed-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "consumed-viewer"
            );

        ServerPacketWriter sourceWriterA=
            writer(1);
        ServerPacketWriter viewerWriter=
            writer(5);

        Player81WorldSync.Context sourceA=
            Player81WorldSync.register(
                sourceWriterA,
                world,
                source,
                new DevAuthorityWorkbench()
            );
        Player81WorldSync.Context viewerContext=
            Player81WorldSync.register(
                viewerWriter,
                world,
                viewer,
                new DevAuthorityWorkbench()
            );

        ServerPacketWriter sourceWriterB=null;

        try{
            Player81WorldSync.transformForTest(
                viewerContext,
                BootstrapPackets.player81Idle()
            );

            Player81WorldSync.transformForTest(
                sourceA,
                CombatSync.player81AnimationOnly(
                    827
                )
            );

            Player81WorldSync.transformForTest(
                viewerContext,
                BootstrapPackets.player81Idle()
            );

            long identityA=
                Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id()
                    );

            long exactA=
                Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id(),
                        sourceGenerationA
                    );

            if(identityA<=0L||
               exactA!=identityA)
                throw new AssertionError(
                    "generation A cursor mismatch identity="+
                    identityA+
                    " exact="+exactA
                );

            if(!world.unregisterPlayer(
                    source,
                    sourceGenerationA
                ))
                throw new AssertionError(
                    "source generation A unregister failed"
                );

            long sourceGenerationB=
                world.registerPlayer(
                    source,
                    "consumed-source"
                );

            if(sourceGenerationB==
                    sourceGenerationA)
                throw new AssertionError(
                    "source generation did not advance"
                );

            long identityDuringReplacement=
                Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id()
                    );

            if(identityDuringReplacement!=
                    identityA)
                throw new AssertionError(
                    "compatibility identity cursor unexpectedly changed"
                );

            if(Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id(),
                        sourceGenerationA
                    )!=-1L)
                throw new AssertionError(
                    "exact generation A cursor survived World replacement"
                );

            if(Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id(),
                        sourceGenerationB
                    )!=-1L)
                throw new AssertionError(
                    "exact generation B cursor accepted before fresh source sync context"
                );

            sourceWriterB=
                writer(9);

            Player81WorldSync.Context sourceB=
                Player81WorldSync.register(
                    sourceWriterB,
                    world,
                    source,
                    new DevAuthorityWorkbench()
                );

            if(sourceB.ownerGeneration!=
                    sourceGenerationB)
                throw new AssertionError(
                    "fresh source B context generation mismatch"
                );

            Player81WorldSync.transformForTest(
                viewerContext,
                BootstrapPackets.player81Idle()
            );
            Player81WorldSync.transformForTest(
                viewerContext,
                BootstrapPackets.player81Idle()
            );

            if(Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id(),
                        sourceGenerationB
                    )!=0L)
                throw new AssertionError(
                    "fresh B track should begin with zero consumed event cursor"
                );

            Player81WorldSync.transformForTest(
                sourceB,
                CombatSync.player81AnimationOnly(
                    828
                )
            );

            Player81WorldSync.transformForTest(
                viewerContext,
                BootstrapPackets.player81Idle()
            );

            long exactB=
                Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id(),
                        sourceGenerationB
                    );

            if(exactB<=0L)
                throw new AssertionError(
                    "fresh B cursor did not advance"
                );

            if(Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id(),
                        sourceGenerationA
                    )!=-1L)
                throw new AssertionError(
                    "generation A cursor became visible again"
                );

            System.out.println(
                "PLAYER81_CONSUMED_GENERATION_FENCE_PASS "+
                "generationAWorks=true "+
                "replacementExactRejected=true "+
                "freshBContextRequired=true "+
                "generationBWorks=true"
            );

            world.unregisterPlayer(
                source,
                sourceGenerationB
            );
            world.unregisterPlayer(
                viewer,
                viewerGeneration
            );
        }finally{
            Player81WorldSync.unregister(
                sourceWriterA
            );

            if(sourceWriterB!=null)
                Player81WorldSync.unregister(
                    sourceWriterB
                );

            Player81WorldSync.unregister(
                viewerWriter
            );

            if(source.registered())
                world.unregisterPlayer(
                    source,
                    source.generation()
                );

            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    viewer.generation()
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

    private Player81ConsumedGenerationFenceTest(){}
}
