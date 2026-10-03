package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalRoutedNpcInteractionHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        MovementState movement=player.movement();
        BankState bank=player.bank();
        NpcRegistry npcs=new NpcRegistry();

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        LocalRoutedNpcInteractionHandler h=
            new LocalRoutedNpcInteractionHandler(npcs,bank,movement);

        int[] bankRootPublishes={0};
        h.installBankRootOwner(
            action->{
                bankRootPublishes[0]++;
                return action.open();
            }
        );

        // Native option 3 Bank exercises the generic bank coordinator.
        String spawned=npcs.devSpawnNpc(
            7605,1,0,movement,w);
        if(!spawned.startsWith("DEV_NPC_SPAWN_OK"))
            throw new AssertionError("banker spawn precondition="+spawned);

        NpcEntity banker=null;
        for(NpcEntity n:npcs.snapshot()){
            if(n.definitionId==7605){banker=n;break;}
        }
        if(banker==null)throw new AssertionError("banker not visible");

        int before=wire.size();
        String opened=h.handle(
            new NpcAction(17,banker.sceneIndex),
            banker,
            w
        );
        if(opened==null||
           !opened.contains("V511_BANK_OPEN_NPC npc=7605")||
           !opened.contains("action=OPENED_ADJACENT_IMMEDIATE"))
            throw new AssertionError("adjacent banker route="+opened);
        if(!bank.isOpen())
            throw new AssertionError("bank did not open");
        if(wire.size()<=before)
            throw new AssertionError("banker open emitted no packets");
        if(bankRootPublishes[0]!=1)
            throw new AssertionError(
                "adjacent banker did not publish exactly one owned root count="+
                bankRootPublishes[0]
            );

        // A second banker outside adjacency proves the coordinator owns the
        // deferred scene/deadline state and current path-ended cancellation.
        String spawnedFar=npcs.devSpawnNpc(
            7605,3,0,movement,w);
        if(!spawnedFar.startsWith("DEV_NPC_SPAWN_OK"))
            throw new AssertionError("far banker spawn precondition="+spawnedFar);

        NpcEntity far=null;
        for(NpcEntity n:npcs.snapshot()){
            if(n.definitionId==7605&&n!=banker){far=n;break;}
        }
        if(far==null)throw new AssertionError("far banker not visible");

        String deferred=h.handle(
            new NpcAction(17,far.sceneIndex),
            far,
            w
        );
        if(deferred==null||
           !deferred.contains("action=DEFERRED_UNTIL_ADJACENT"))
            throw new AssertionError("deferred banker route="+deferred);
        if(!h.hasPendingBank())
            throw new AssertionError("deferred bank scene not retained");
        if(bankRootPublishes[0]!=1)
            throw new AssertionError(
                "deferred banker published root before authoritative arrival"
            );

        // Generic RouteFinder ownership now reroutes a still-valid target when
        // the previous path ends. Make that transition explicit, then prove the
        // retained request still cancels deterministically on timeout.
        movement.clearQueuedPath();

        String rerouted=h.tick(System.currentTimeMillis(),w);
        if(rerouted==null||
           !rerouted.contains("SERVER_REROUTED_MOVING_TARGET"))
            throw new AssertionError("deferred reroute="+rerouted);
        if(!h.hasPendingBank())
            throw new AssertionError("rerouted bank scene was cleared");

        String cancelled=h.tick(Long.MAX_VALUE,w);
        if(cancelled==null||
           !cancelled.contains("CANCELLED_MISSING_OR_TIMEOUT"))
            throw new AssertionError("deferred timeout cancellation="+cancelled);
        if(h.hasPendingBank())
            throw new AssertionError("cancelled bank scene still pending");
        if(bankRootPublishes[0]!=1)
            throw new AssertionError(
                "cancelled/deferred banker path published a root"
            );

        NpcEntity unknown=new NpcEntity(
            999,1,movement.x(),movement.y());
        String generic=h.handle(
            new NpcAction(155,999),unknown,w);
        if(generic==null||
           !generic.contains("V511_NPC_ACTION")||
           !generic.contains("result=DECODED_SEMANTIC_"))
            throw new AssertionError("generic route="+generic);
        if(bankRootPublishes[0]!=1)
            throw new AssertionError(
                "non-bank NPC action entered bank root ownership"
            );

        WorldPlayer rejectedPlayer=new WorldPlayer();
        MovementState rejectedMovement=rejectedPlayer.movement();
        BankState rejectedBank=rejectedPlayer.bank();
        NpcRegistry rejectedNpcs=new NpcRegistry();
        ByteArrayOutputStream rejectedWire=
            new ByteArrayOutputStream();
        ServerPacketWriter rejectedWriter=
            new ServerPacketWriter(
                rejectedWire,
                new IsaacCipher(new int[]{5,6,7,8})
            );
        LocalRoutedNpcInteractionHandler rejectedHandler=
            new LocalRoutedNpcInteractionHandler(
                rejectedNpcs,
                rejectedBank,
                rejectedMovement
            );
        int[] rejectedRootAdmissions={0};
        rejectedHandler.installBankRootOwner(
            action->{
                rejectedRootAdmissions[0]++;
                return null;
            }
        );

        String rejectedSpawn=
            rejectedNpcs.devSpawnNpc(
                7605,
                1,
                0,
                rejectedMovement,
                rejectedWriter
            );
        if(!rejectedSpawn.startsWith(
                "DEV_NPC_SPAWN_OK"
            ))
            throw new AssertionError(
                "rejected banker spawn precondition="+
                rejectedSpawn
            );

        NpcEntity rejectedBanker=null;
        for(NpcEntity n:rejectedNpcs.snapshot()){
            if(n.definitionId==7605){
                rejectedBanker=n;
                break;
            }
        }
        if(rejectedBanker==null)
            throw new AssertionError(
                "rejected banker not visible"
            );

        int rejectedBefore=rejectedWire.size();
        String rejectedOpen=
            rejectedHandler.handle(
                new NpcAction(
                    17,
                    rejectedBanker.sceneIndex
                ),
                rejectedBanker,
                rejectedWriter
            );

        if(rejectedOpen!=null||
           rejectedRootAdmissions[0]!=1||
           rejectedBank.isOpen()||
           rejectedWire.size()!=rejectedBefore)
            throw new AssertionError(
                "rejected bank-root ownership mutated/published state result="+
                rejectedOpen+
                " admissions="+
                rejectedRootAdmissions[0]+
                " bankOpen="+
                rejectedBank.isOpen()
            );

        System.out.println(
            "LOCAL_ROUTED_NPC_INTERACTION_HANDLER_PASS "+
            "bankerImmediate=true bankRootOwner=true "+
            "deferredOwnership=true deferredNoEarlyRoot=true "+
            "pathEndReroute=true timeoutCancel=true "+
            "genericFailClosed=true rejectedRootNoMutation=true");
    }
}
