package spk.local;

import java.io.*;
import java.util.*;

public final class EngineR32WorldPresentationBusTest {
    public static void main(String[] args)throws Exception{
        testExactParticleSpawn();
        testRemotePetSnapshotAndBarrier();
        System.out.println(
            "V5132_R32_WORLD_PRESENTATION_BUS_PASS "+
            "particleSelector=true nativeChargeSnapshot=true "+
            "nativeChargeRelay=true hitAfterSwing=true "+
            "canonicalHome=true reflection=false");
    }

    static void testExactParticleSpawn()throws Exception{
        NpcRegistry registry=
            new NpcRegistry(new DevAuthorityWorkbench());
        MovementState movement=new MovementState();
        OutboundPacketQueue queue=new OutboundPacketQueue();
        ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{1,2,3,4}));

        int x=movement.x()-1;
        int y=movement.y();

        NpcEntity entity=registry.spawnMirroredNpc(
            5163,x,y,Integer.valueOf(8),movement,writer);

        ByteArrayOutputStream got=new ByteArrayOutputStream();
        queue.drainTo(got,1<<20);
        byte[] framed=got.toByteArray();
        if(framed.length<4)
            throw new AssertionError("missing mirrored add");

        int len=((framed[1]&255)<<8)|(framed[2]&255);
        byte[] body=Arrays.copyOfRange(framed,3,3+len);

        NpcEntity expected=
            new NpcEntity(entity.sceneIndex,5163,x,y);
        Map<Integer,NpcSpawnPresentation> presentation=
            Collections.singletonMap(
                entity.sceneIndex,
                NpcSpawnPresentation.particle(8));

        byte[] exp=NpcSyncEncoder.encode(
            Collections.<NpcSyncEncoder.Update>emptyList(),
            Collections.singletonList(expected),
            movement.x(),movement.y(),presentation);

        if(!Arrays.equals(body,exp))
            throw new AssertionError(
                "particle presentation mismatch");
    }

    static void testRemotePetSnapshotAndBarrier()throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer source=new WorldPlayer();
        WorldPlayer viewer=new WorldPlayer();
        world.registerPlayer(source,"opensrc");
        world.registerPlayer(viewer,"src");

        OutboundPacketQueue sourceQueue=new OutboundPacketQueue();
        OutboundPacketQueue viewerQueue=new OutboundPacketQueue();
        ServerPacketWriter sourceWriter=new ServerPacketWriter(
            sourceQueue,new IsaacCipher(new int[]{11,12,13,14}));
        ServerPacketWriter viewerWriter=new ServerPacketWriter(
            viewerQueue,new IsaacCipher(new int[]{21,22,23,24}));

        Player81WorldSync.register(
            sourceWriter,world,source,new DevAuthorityWorkbench());
        Player81WorldSync.register(
            viewerWriter,world,viewer,new DevAuthorityWorkbench());

        DevAuthorityWorkbench sourceDev=new DevAuthorityWorkbench();
        DevAuthorityWorkbench viewerDev=new DevAuthorityWorkbench();

        MovementState sourceMovement=source.movement();
        MovementState viewerMovement=viewer.movement();

        NpcRegistry sourceNpcs=new NpcRegistry(
            sourceDev,world.petNpcs(),source.id());
        NpcRegistry viewerNpcs=new NpcRegistry(
            viewerDev,world.petNpcs(),viewer.id());

        SharedNpcWorldRelay.register(
            sourceWriter,world,source,sourceNpcs,sourceMovement);
        SharedNpcWorldRelay.register(
            viewerWriter,world,viewer,viewerNpcs,viewerMovement);

        try{
            sourceNpcs.bootstrapHome(
                sourceWriter,sourceMovement,source.petState(),
                new HomeWorldRuntimePlan(world.homeNpcs()));
            viewerNpcs.bootstrapHome(
                viewerWriter,viewerMovement,viewer.petState(),
                new HomeWorldRuntimePlan(world.homeNpcs()));

            sourceWriter.varShort(
                81,BootstrapPackets.player81Idle());
            viewerWriter.varShort(
                81,BootstrapPackets.player81Idle());

            PetDefinitionRepository.Def petDef=
                PetDefinitionRepository.get(24019);
            sourceNpcs.spawnPet(
                petDef,sourceMovement,sourceWriter);
            sourceNpcs.devSetParticleSelector(
                Integer.valueOf(8),sourceMovement,sourceWriter);
            sourceNpcs.setPetNativeState(3,sourceWriter);

            SharedNpcWorldRelay.syncRemotePets(viewerWriter);

            NpcEntity remote=findDef(viewerNpcs,petDef.npcId);
            if(remote==null)
                throw new AssertionError("remote pet absent");

            ByteArrayOutputStream snap=new ByteArrayOutputStream();
            viewerQueue.drainTo(snap,1<<20);
            String raw=new String(
                snap.toByteArray(),"ISO-8859-1");
            if(raw.indexOf("3\n")<0)
                throw new AssertionError(
                    "native charge snapshot missing");

            sourceWriter.varShort(
                81,CombatSync.player81AnimationOnly(15552));
            int before=viewerQueue.queuedBytes();

            sourceNpcs.setPetNativeState(2,sourceWriter);
            if(viewerQueue.queuedBytes()!=before)
                throw new AssertionError(
                    "pet state overtook player event");

            viewerWriter.varShort(
                81,BootstrapPackets.player81Idle());
            if(viewerQueue.queuedBytes()<=before)
                throw new AssertionError(
                    "pet state not released after player event");

            NpcEntity[] shared=
                EngineR31SharedNpcRelayTest.sharedCanonicalHome(
                    sourceNpcs,viewerNpcs);
            NpcEntity sourceHome=shared[0];
            NpcEntity viewerHome=shared[1];

            if(!sourceHome.canonicalId().equals(
                    viewerHome.canonicalId()))
                throw new AssertionError(
                    "HOME canonical identity mismatch");

            sourceWriter.varShort(
                81,
                CombatSync.player81AnimationAndInteraction(
                    15552,sourceHome.sceneIndex));
            int hitBefore=viewerQueue.queuedBytes();

            sourceNpcs.sendMask(
                sourceHome,
                NpcSyncEncoder.Mask.singleHit(100,6,255,255),
                sourceWriter);

            if(viewerQueue.queuedBytes()!=hitBefore)
                throw new AssertionError("hit overtook swing");

            viewerWriter.varShort(
                81,BootstrapPackets.player81Idle());
            if(viewerQueue.queuedBytes()<=hitBefore)
                throw new AssertionError(
                    "hit missing after swing");
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

    static NpcEntity findDef(
        NpcRegistry registry,
        int definitionId
    ){
        for(NpcEntity entity:registry.snapshot())
            if(entity.definitionId==definitionId)
                return entity;
        return null;
    }
}
