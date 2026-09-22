package spk.local;

import java.io.ByteArrayOutputStream;

public final class Player81InteractionTargetGenerationFenceTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer source=new WorldPlayer();
        WorldPlayer target=new WorldPlayer();
        WorldPlayer viewer=new WorldPlayer();

        long sourceGeneration=world.registerPlayer(source,"player81-source");
        long targetGenerationA=world.registerPlayer(target,"player81-target");
        long viewerGeneration=world.registerPlayer(viewer,"player81-viewer");

        ServerPacketWriter sourceWriter=writer(1);
        ServerPacketWriter targetWriter=writer(5);
        ServerPacketWriter viewerWriter=writer(9);

        Player81WorldSync.Context sourceContext=
            Player81WorldSync.register(sourceWriter,world,source,new DevAuthorityWorkbench());
        Player81WorldSync.Context targetContextA=
            Player81WorldSync.register(targetWriter,world,target,new DevAuthorityWorkbench());
        Player81WorldSync.Context viewerContext=
            Player81WorldSync.register(viewerWriter,world,viewer,new DevAuthorityWorkbench());

        ServerPacketWriter targetWriterB=null;

        try{
            Player81WorldSync.transformForTest(sourceContext,BootstrapPackets.player81Idle());
            Player81WorldSync.transformForTest(targetContextA,BootstrapPackets.player81Idle());
            Player81WorldSync.transformForTest(viewerContext,BootstrapPackets.player81Idle());

            int sourceTargetIndex=sourceContext.clientIndexFor(target);
            if(sourceTargetIndex<0)
                throw new AssertionError("source target index missing");

            Player81WorldSync.transformForTest(
                sourceContext,
                CombatSync.player81InteractionOnly(32768+sourceTargetIndex)
            );

            byte[] oldEvent=
                Player81WorldSync.latestEventForViewerForTest(
                    viewerContext,
                    source.id()
                );

            int oldTranslated=interactionValue(oldEvent);
            int expectedA=viewerContext.interactionTargetFor(target);
            if(oldTranslated!=expectedA)
                throw new AssertionError(
                    "generation A translation mismatch expected="+
                    expectedA+" actual="+oldTranslated
                );

            if(!world.unregisterPlayer(target,targetGenerationA))
                throw new AssertionError("target generation A unregister failed");

            long targetGenerationB=
                world.registerPlayer(target,"player81-target");

            if(targetGenerationB==targetGenerationA)
                throw new AssertionError("target generation did not advance");

            Player81WorldSync.transformForTest(
                viewerContext,
                BootstrapPackets.player81Idle()
            );
            Player81WorldSync.transformForTest(
                viewerContext,
                BootstrapPackets.player81Idle()
            );

            int viewerTargetB=
                viewerContext.interactionTargetFor(target);
            if(viewerTargetB<0)
                throw new AssertionError("viewer did not establish fresh generation B track");

            byte[] delayedOld=
                Player81WorldSync.latestEventForViewerForTest(
                    viewerContext,
                    source.id()
                );

            if(interactionValue(delayedOld)!=65535)
                throw new AssertionError(
                    "delayed generation A interaction retargeted generation B"
                );

            Player81WorldSync.transformForTest(
                sourceContext,
                BootstrapPackets.player81Idle()
            );
            Player81WorldSync.transformForTest(
                sourceContext,
                BootstrapPackets.player81Idle()
            );

            int sourceTargetB=
                sourceContext.clientIndexFor(target);
            if(sourceTargetB<0)
                throw new AssertionError("source did not establish fresh generation B track");

            Player81WorldSync.transformForTest(
                sourceContext,
                CombatSync.player81InteractionOnly(32768+sourceTargetB)
            );

            byte[] freshEvent=
                Player81WorldSync.latestEventForViewerForTest(
                    viewerContext,
                    source.id()
                );

            if(interactionValue(freshEvent)!=viewerTargetB)
                throw new AssertionError(
                    "fresh generation B interaction did not translate normally"
                );

            targetWriterB=writer(13);
            Player81WorldSync.Context targetContextB=
                Player81WorldSync.register(
                    targetWriterB,
                    world,
                    target,
                    new DevAuthorityWorkbench()
                );

            if(targetContextB.ownerGeneration!=targetGenerationB)
                throw new AssertionError("replacement target context generation mismatch");

            System.out.println(
                "PLAYER81_INTERACTION_TARGET_GENERATION_FENCE_PASS "+
                "generationATranslates=true "+
                "delayedARejectedAfterB=true "+
                "freshBTranslates=true"
            );

            world.unregisterPlayer(target,targetGenerationB);
        }finally{
            Player81WorldSync.unregister(sourceWriter);
            Player81WorldSync.unregister(targetWriter);
            Player81WorldSync.unregister(viewerWriter);
            if(targetWriterB!=null)Player81WorldSync.unregister(targetWriterB);

            if(source.registered())
                world.unregisterPlayer(source,sourceGeneration);
            if(target.registered())
                world.unregisterPlayer(target,target.generation());
            if(viewer.registered())
                world.unregisterPlayer(viewer,viewerGeneration);

            world.close();
        }
    }

    private static int interactionValue(byte[] tail){
        if(tail==null||tail.length<3)
            throw new AssertionError("interaction event tail missing");
        if((tail[0]&0x1)==0)
            throw new AssertionError("interaction mask bit missing");
        return (tail[1]&255)|((tail[2]&255)<<8);
    }

    private static ServerPacketWriter writer(int seed){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{seed,seed+1,seed+2,seed+3})
        );
    }

    private Player81InteractionTargetGenerationFenceTest(){}
}
