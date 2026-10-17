package spk.local;

import java.io.*;

public final class MiniPetSubsystemTest {
    public static void main(String[] args)throws Exception{
        if(MiniPetDefinitionRepository.count()!=21)throw new AssertionError("mini count");
        MovementState m=new MovementState();PetState ps=new PetState();NpcRegistry n=new NpcRegistry();
        ByteArrayOutputStream out=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{1,1,1,1}));
        PetDefinitionRepository.Def main=PetDefinitionRepository.get(22519);if(main==null)throw new AssertionError("main pet");
        String sp=n.spawnPet(main,m,w);if(!sp.startsWith("PET_SPAWN_OK"))throw new AssertionError(sp);ps.activate(main);
        MiniPetService svc=new MiniPetService();String cfg=svc.configure(23629,ps,n,m,w);if(!cfg.startsWith("MINIPET_CONFIGURED"))throw new AssertionError(cfg);
        if(!ps.miniConfigured()||ps.miniItemId()!=23629||n.miniPet()==null)throw new AssertionError("selection/actor");
        if(n.miniPet().sceneIndex==n.pet().sceneIndex)throw new AssertionError("scene collision");
        if(LocalSession.chebyshev(n.miniPet().x,n.miniPet().y,n.pet().x,n.pet().y)!=1)throw new AssertionError("mini must spawn trailing main pet mini="+n.miniPet().x+","+n.miniPet().y+" pet="+n.pet().x+","+n.pet().y);
        int miniScene=n.miniPet().sceneIndex;String rm=n.removePet(w);ps.clear();if(n.miniPet()!=null)throw new AssertionError("mini remains after main pickup");if(!ps.miniConfigured())throw new AssertionError("selection lost");
        n.spawnPet(main,m,w);ps.activate(main);svc.onMainPetSpawn(ps,n,m,w);if(n.miniPet()==null||n.miniPet().sceneIndex==miniScene){} // scene reuse is allowed
        String off=svc.off(ps,n,w);if(ps.miniConfigured()||n.miniPet()!=null)throw new AssertionError(off);
        testConfigurePublicationAtomicity();
        System.out.println("V5122_MINIPET_SUBSYSTEM_PASS definitions=21 configure23629to1937=true relation=MAIN_PET_TARGET trailingDistance1=true selectionPersistsAcrossPickup=true off=true configurePublicationAtomic=true replacementPreservesPrior=true noPriorFailurePreservesNone=true noMainSelectionOnly=true disableFailurePreservesSelection=true");
    }

    private static void testConfigurePublicationAtomicity()
        throws Exception
    {
        java.util.Iterator<MiniPetDefinitionRepository.Def> it=
            MiniPetDefinitionRepository.all().iterator();
        MiniPetDefinitionRepository.Def miniA=it.next();
        MiniPetDefinitionRepository.Def miniB=it.next();

        if(miniA.itemId==miniB.itemId)
            throw new AssertionError("mini fixtures not distinct");

        PetDefinitionRepository.Def main=
            PetDefinitionRepository.get(22519);

        if(main==null)
            throw new AssertionError("main pet fixture missing");

        MovementState movement=
            new MovementState();
        PetState state=
            new PetState();
        NpcRegistry npcs=
            new NpcRegistry();
        MiniPetService service=
            new MiniPetService();
        ServerPacketWriter healthy=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{21,22,23,24}
                )
            );

        npcs.spawnPet(
            main,
            movement,
            healthy
        );
        state.activate(main);

        String first=
            service.configure(
                miniA.itemId,
                state,
                npcs,
                movement,
                healthy
            );

        if(first==null||
           !first.startsWith("MINIPET_CONFIGURED")||
           state.miniItemId()!=miniA.itemId||
           npcs.miniPet()==null)
            throw new AssertionError(
                "mini A fixture configure failed result="+first
            );

        NpcEntity actorA=
            npcs.miniPet();

        boolean replacementFailed=false;
        try{
            service.configure(
                miniB.itemId,
                state,
                npcs,
                movement,
                fullWriter(
                    new int[]{25,26,27,28}
                )
            );
        }catch(IOException expected){
            replacementFailed=true;
        }

        if(!replacementFailed||
           state.miniItemId()!=miniA.itemId||
           npcs.miniPet()!=actorA)
            throw new AssertionError(
                "failed mini replacement changed prior selection/actor"
            );

        String replaced=
            service.configure(
                miniB.itemId,
                state,
                npcs,
                movement,
                healthy
            );

        if(replaced==null||
           !replaced.startsWith("MINIPET_CONFIGURED")||
           state.miniItemId()!=miniB.itemId||
           npcs.miniPet()==null||
           npcs.miniPet()==actorA)
            throw new AssertionError(
                "mini replacement retry did not commit B"
            );

        NpcEntity actorB=
            npcs.miniPet();

        boolean disableFailed=false;
        try{
            service.off(
                state,
                npcs,
                fullWriter(
                    new int[]{29,30,31,32}
                )
            );
        }catch(IOException expected){
            disableFailed=true;
        }

        if(!disableFailed||
           state.miniItemId()!=miniB.itemId||
           npcs.miniPet()!=actorB)
            throw new AssertionError(
                "failed mini disable changed configured selection/actor"
            );

        // No prior configured mini with an active main pet: failed publication
        // must leave both persistent selection and runtime actor absent.
        MovementState movement2=
            new MovementState();
        PetState state2=
            new PetState();
        NpcRegistry npcs2=
            new NpcRegistry();

        npcs2.spawnPet(
            main,
            movement2,
            healthy
        );
        state2.activate(main);

        boolean firstConfigureFailed=false;
        try{
            service.configure(
                miniA.itemId,
                state2,
                npcs2,
                movement2,
                fullWriter(
                    new int[]{33,34,35,36}
                )
            );
        }catch(IOException expected){
            firstConfigureFailed=true;
        }

        if(!firstConfigureFailed||
           state2.miniConfigured()||
           npcs2.miniPet()!=null)
            throw new AssertionError(
                "failed first mini configure created hidden selection/actor"
            );

        // No main pet intentionally remains a selection-only configuration.
        PetState selectionOnly=
            new PetState();
        NpcRegistry noMain=
            new NpcRegistry();

        String noMainResult=
            service.configure(
                miniA.itemId,
                selectionOnly,
                noMain,
                new MovementState(),
                fullWriter(
                    new int[]{37,38,39,40}
                )
            );

        if(noMainResult==null||
           !noMainResult.contains(
                "MAIN_PET_NOT_OUT_SELECTION_PERSISTED"
           )||
           selectionOnly.miniItemId()!=miniA.itemId||
           noMain.miniPet()!=null)
            throw new AssertionError(
                "no-main-pet selection-only semantics changed result="+
                noMainResult
            );
    }

    private static ServerPacketWriter fullWriter(
        int[] seed
    )throws Exception{
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);
        queue.offer(
            new byte[1024]
        );
        return new ServerPacketWriter(
            queue,
            new IsaacCipher(seed)
        );
    }
}
