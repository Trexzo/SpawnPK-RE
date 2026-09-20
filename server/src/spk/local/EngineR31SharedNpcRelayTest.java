package spk.local;

import java.io.*;

public final class EngineR31SharedNpcRelayTest {
    public static void main(String[] args)throws Exception{
        World w=World.isolatedForTest(600L);
        WorldPlayer p1=new WorldPlayer(),p2=new WorldPlayer();
        w.registerPlayer(p1,"opensrc");
        w.registerPlayer(p2,"src");

        OutboundPacketQueue q1=new OutboundPacketQueue(),q2=new OutboundPacketQueue();
        ServerPacketWriter sw1=new ServerPacketWriter(q1,new IsaacCipher(new int[]{1,2,3,4}));
        ServerPacketWriter sw2=new ServerPacketWriter(q2,new IsaacCipher(new int[]{5,6,7,8}));

        Player81WorldSync.Context c1=
            Player81WorldSync.register(sw1,w,p1,new DevAuthorityWorkbench());
        Player81WorldSync.Context c2=
            Player81WorldSync.register(sw2,w,p2,new DevAuthorityWorkbench());

        NpcRegistry n1=new NpcRegistry(new DevAuthorityWorkbench());
        NpcRegistry n2=new NpcRegistry(new DevAuthorityWorkbench());
        MovementState m1=new MovementState(),m2=new MovementState();

        SharedNpcWorldRelay.register(sw1,w,p1,n1,m1);
        SharedNpcWorldRelay.register(sw2,w,p2,n2,m2);

        try{
            NpcEntity[] sharedHome=
                bootstrapSharedHome(
                    w,
                    n1,
                    n2,
                    m1,
                    m2,
                    sw1,
                    sw2
                );
            NpcEntity d1=sharedHome[0];
            NpcEntity d2=sharedHome[1];

            if(d1.canonicalId()==null||
               !d1.canonicalId().equals(d2.canonicalId()))
                throw new AssertionError(
                    "HOME projections do not share canonical identity"
                );

            sw1.varShort(81,BootstrapPackets.player81Idle());
            sw2.varShort(81,BootstrapPackets.player81Idle());

            PetDefinitionRepository.Def pd=
                PetDefinitionRepository.get(24019);
            if(pd==null)
                throw new AssertionError("pet 24019 unmapped");

            n1.spawnPet(pd,m1,sw1);

            MiniPetDefinitionRepository.Def md=
                MiniPetDefinitionRepository.get(23988);
            if(md==null)
                throw new AssertionError("mini 23988 unmapped");

            n1.spawnOrReplaceMiniPet(md,m1,sw1);
            SharedNpcWorldRelay.syncRemotePets(sw2);

            boolean pet=false,mini=false;
            for(NpcEntity e:n2.snapshot()){
                if(e.definitionId==pd.npcId)pet=true;
                if(e.definitionId==md.npcId)mini=true;
            }
            if(!pet||!mini)
                throw new AssertionError(
                    "remote pet clone missing pet="+pet+
                    " mini="+mini
                );

            sw1.varShort(
                81,
                CombatSync.player81AnimationAndInteraction(
                    15552,
                    d1.sceneIndex
                )
            );

            int before=q2.queuedBytes();

            n1.sendMask(
                d1,
                NpcSyncEncoder.Mask.singleHit(
                    100,
                    6,
                    255,
                    255
                ),
                sw1
            );

            if(q2.queuedBytes()!=before)
                throw new AssertionError(
                    "remote hit overtook source player presentation"
                );

            sw2.varShort(
                81,
                BootstrapPackets.player81Idle()
            );

            if(q2.queuedBytes()<=before)
                throw new AssertionError(
                    "canonical HOME mask not delivered after player81 barrier"
                );

            System.out.println(
                "V5132_R31_SHARED_NPC_RELAY_PASS "+
                "remotePet=true remoteMini=true "+
                "canonicalHomeHitBarrier=true "+
                "canonicalId="+d1.canonicalId()
            );
        }finally{
            SharedNpcWorldRelay.unregister(sw1);
            SharedNpcWorldRelay.unregister(sw2);
            Player81WorldSync.unregister(sw1);
            Player81WorldSync.unregister(sw2);
            w.unregisterPlayer(p1);
            w.unregisterPlayer(p2);
            w.close();
        }
    }

    private static NpcEntity[] bootstrapSharedHome(
        World world,
        NpcRegistry first,
        NpcRegistry second,
        MovementState firstMovement,
        MovementState secondMovement,
        ServerPacketWriter firstWriter,
        ServerPacketWriter secondWriter
    )throws Exception{
        HomeWorldRuntimePlan firstHome=
            new HomeWorldRuntimePlan(world.homeNpcs());
        HomeWorldRuntimePlan secondHome=
            new HomeWorldRuntimePlan(world.homeNpcs());

        first.bootstrapHome(
            firstWriter,
            firstMovement,
            new PetState(),
            firstHome
        );
        second.bootstrapHome(
            secondWriter,
            secondMovement,
            new PetState(),
            secondHome
        );

        for(NpcEntity candidate:first.snapshot()){
            EntityId id=candidate.canonicalId();
            if(id==null)continue;
            NpcEntity other=second.canonical(id);
            if(other!=null&&
               other.definitionId==candidate.definitionId)
                return new NpcEntity[]{candidate,other};
        }

        throw new AssertionError(
            "no shared canonical HOME NPC visible to both viewers"
        );
    }
}
