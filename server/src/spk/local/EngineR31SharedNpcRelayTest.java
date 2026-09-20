package spk.local;

import java.io.*;

public final class EngineR31SharedNpcRelayTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer p1=new WorldPlayer();
        WorldPlayer p2=new WorldPlayer();
        world.registerPlayer(p1,"opensrc");
        world.registerPlayer(p2,"src");

        OutboundPacketQueue q1=new OutboundPacketQueue();
        OutboundPacketQueue q2=new OutboundPacketQueue();
        ServerPacketWriter sw1=new ServerPacketWriter(
            q1,new IsaacCipher(new int[]{1,2,3,4}));
        ServerPacketWriter sw2=new ServerPacketWriter(
            q2,new IsaacCipher(new int[]{5,6,7,8}));

        Player81WorldSync.register(
            sw1,world,p1,new DevAuthorityWorkbench());
        Player81WorldSync.register(
            sw2,world,p2,new DevAuthorityWorkbench());

        MovementState m1=p1.movement();
        MovementState m2=p2.movement();

        NpcRegistry n1=new NpcRegistry(
            new DevAuthorityWorkbench(),
            world.petNpcs(),
            p1.id());
        NpcRegistry n2=new NpcRegistry(
            new DevAuthorityWorkbench(),
            world.petNpcs(),
            p2.id());

        SharedNpcWorldRelay.register(sw1,world,p1,n1,m1);
        SharedNpcWorldRelay.register(sw2,world,p2,n2,m2);

        try{
            n1.bootstrapHome(
                sw1,m1,p1.petState(),
                new HomeWorldRuntimePlan(world.homeNpcs()));
            n2.bootstrapHome(
                sw2,m2,p2.petState(),
                new HomeWorldRuntimePlan(world.homeNpcs()));

            sw1.varShort(81,BootstrapPackets.player81Idle());
            sw2.varShort(81,BootstrapPackets.player81Idle());

            PetDefinitionRepository.Def petDef=
                PetDefinitionRepository.get(24019);
            if(petDef==null)
                throw new AssertionError("pet 24019 unmapped");
            n1.spawnPet(petDef,m1,sw1);

            MiniPetDefinitionRepository.Def miniDef=
                MiniPetDefinitionRepository.get(23988);
            if(miniDef==null)
                throw new AssertionError("mini 23988 unmapped");
            n1.spawnOrReplaceMiniPet(miniDef,m1,sw1);

            SharedNpcWorldRelay.syncRemotePets(sw2);

            boolean remotePet=false;
            boolean remoteMini=false;
            for(NpcEntity e:n2.snapshot()){
                if(e.definitionId==petDef.npcId)remotePet=true;
                if(e.definitionId==miniDef.npcId)remoteMini=true;
            }
            if(!remotePet||!remoteMini)
                throw new AssertionError(
                    "remote pet clone missing pet="+remotePet+
                    " mini="+remoteMini);

            NpcEntity[] shared=sharedCanonicalHome(n1,n2);
            NpcEntity sourceHome=shared[0];
            NpcEntity viewerHome=shared[1];

            if(!sourceHome.canonicalId().equals(
                    viewerHome.canonicalId()))
                throw new AssertionError(
                    "HOME projections do not share canonical identity");

            sw1.varShort(
                81,
                CombatSync.player81AnimationAndInteraction(
                    15552,sourceHome.sceneIndex));

            int before=q2.queuedBytes();
            n1.sendMask(
                sourceHome,
                NpcSyncEncoder.Mask.singleHit(100,6,255,255),
                sw1);

            if(q2.queuedBytes()!=before)
                throw new AssertionError(
                    "remote hit overtook source player presentation");

            sw2.varShort(81,BootstrapPackets.player81Idle());

            if(q2.queuedBytes()<=before)
                throw new AssertionError(
                    "canonical HOME mask not delivered after player81 barrier");

            System.out.println(
                "V5132_R31_SHARED_NPC_RELAY_PASS "+
                "remotePet=true remoteMini=true "+
                "dummyHitBarrier=true canonicalHome=true reflection=false");
        }finally{
            SharedNpcWorldRelay.unregister(sw1);
            SharedNpcWorldRelay.unregister(sw2);
            Player81WorldSync.unregister(sw1);
            Player81WorldSync.unregister(sw2);
            world.unregisterPlayer(p1);
            world.unregisterPlayer(p2);
            world.close();
        }
    }

    static NpcEntity[] sharedCanonicalHome(
        NpcRegistry source,
        NpcRegistry viewer
    ){
        NpcEntity[] fallback=null;

        for(NpcEntity candidate:source.snapshot()){
            if(candidate.canonicalId()==null||
               !HomeWorldRuntimePlan.isHomeWorldSceneIndex(
                   candidate.sceneIndex))
                continue;

            NpcEntity matching=
                viewer.canonical(candidate.canonicalId());
            if(matching==null)continue;

            if(candidate.definitionId==1488)
                return new NpcEntity[]{candidate,matching};

            if(fallback==null)
                fallback=new NpcEntity[]{candidate,matching};
        }

        if(fallback!=null)return fallback;

        throw new AssertionError(
            "no shared canonical HOME NPC visible to both viewers");
    }
}
