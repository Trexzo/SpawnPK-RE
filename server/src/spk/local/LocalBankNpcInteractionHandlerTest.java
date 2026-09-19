package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalBankNpcInteractionHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();
        MovementState movement=player.movement();

        DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        NpcRegistry npcs=new NpcRegistry(dev);

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        String spawned=npcs.devSpawnNpc(7605,1,0,movement,w);
        NpcEntity banker=null;
        for(NpcEntity e:npcs.snapshot()){
            if(e.definitionId==7605){banker=e;break;}
        }
        if(banker==null)
            throw new AssertionError("banker spawn failed: "+spawned);

        LocalBankNpcInteractionHandler h=
            new LocalBankNpcInteractionHandler(bank,movement,npcs);

        NpcAction adjacent=new NpcAction(155,banker.sceneIndex);
        NpcInteractionRouter.Route adjacentRoute=
            NpcInteractionRouter.resolve(adjacent,banker);

        if(adjacentRoute.service!=NpcInteractionRouter.Service.BANK)
            throw new AssertionError("banker route="+adjacentRoute);

        int before=wire.size();
        String opened=h.handle(adjacent,banker,adjacentRoute,w);
        if(opened==null||
           !opened.contains("V511_BANK_OPEN_NPC")||
           !opened.contains("action=OPENED_ADJACENT_IMMEDIATE"))
            throw new AssertionError("adjacent open="+opened);
        if(!bank.isOpen())
            throw new AssertionError("bank not open");
        if(wire.size()<=before)
            throw new AssertionError("bank open emitted no packets");
        if(h.hasPending())
            throw new AssertionError("adjacent request must not remain pending");

        String spawnedFar=npcs.devSpawnNpc(7605,3,0,movement,w);
        NpcEntity far=null;
        for(NpcEntity e:npcs.snapshot()){
            if(e.definitionId==7605 && e!=banker){far=e;break;}
        }
        if(far==null)
            throw new AssertionError("far banker spawn failed: "+spawnedFar);

        NpcAction deferredReq=new NpcAction(155,far.sceneIndex);
        NpcInteractionRouter.Route deferredRoute=
            NpcInteractionRouter.resolve(deferredReq,far);

        String deferred=h.handle(deferredReq,far,deferredRoute,w);
        if(deferred==null||
           !deferred.contains("action=DEFERRED_UNTIL_ADJACENT"))
            throw new AssertionError("deferred route="+deferred);
        if(!h.hasPending()||h.pendingScene()==null||
           h.pendingScene().intValue()!=far.sceneIndex)
            throw new AssertionError("pending scene not owned by handler");

        String cancelled=h.tick(System.currentTimeMillis(),w);
        if(cancelled==null||
           !cancelled.contains("action=CANCELLED_PATH_ENDED_NOT_ADJACENT"))
            throw new AssertionError("path-ended cancel="+cancelled);
        if(h.hasPending())
            throw new AssertionError("cancelled request still pending");

        NpcAction nonBank=new NpcAction(72,far.sceneIndex);
        NpcInteractionRouter.Route nonBankRoute=
            new NpcInteractionRouter.Route(
                2,"Attack",NpcInteractionRouter.Service.ATTACK,"TEST");
        if(h.handle(nonBank,far,nonBankRoute,w)!=null)
            throw new AssertionError("non-bank route must remain outside handler");

        System.out.println(
            "LOCAL_BANK_NPC_HANDLER_PASS immediateOpen=true deferredOwnership=true pathEndCancel=true nonBankRejected=true");
    }
}
