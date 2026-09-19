package spk.local;

import java.io.IOException;

/**
 * Typed NPC-bank interaction coordinator.
 *
 * Raw NPC action decoding remains in ClientPacketProbe/LocalSession. This class
 * owns only the existing BANK service route and its deferred-arrival state.
 */
final class LocalBankNpcInteractionHandler {
    private final BankState bank;
    private final MovementState movement;
    private final NpcRegistry npcs;

    private Integer pendingScene;
    private long pendingDeadlineMs;

    LocalBankNpcInteractionHandler(
        BankState bank,
        MovementState movement,
        NpcRegistry npcs
    ){
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
    }

    String handle(
        NpcAction request,
        NpcEntity clicked,
        NpcInteractionRouter.Route route,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(request==null||route==null||clicked==null)return null;
        if(route.service!=NpcInteractionRouter.Service.BANK)return null;

        if(adjacentTo(clicked.x,clicked.y)){
            pendingScene=null;
            return openNow(
                clicked,
                request,
                route,
                serverPackets,
                "OPENED_ADJACENT_IMMEDIATE"
            );
        }

        pendingScene=clicked.sceneIndex;
        pendingDeadlineMs=System.currentTimeMillis()+10_000L;
        return "V511_NPC_BANK "+request+
            " clicked="+clicked+
            " route="+route+
            " distance="+chebyshev(
                movement.x(),movement.y(),clicked.x,clicked.y)+
            " action=DEFERRED_UNTIL_ADJACENT";
    }

    String tick(long now,ServerPacketWriter serverPackets)throws IOException{
        Integer scene=pendingScene;
        if(scene==null)return null;

        NpcEntity npc=npcs.scene(scene);
        if(npc==null||now>pendingDeadlineMs){
            pendingScene=null;
            return "V511_NPC_BANK scene="+scene+
                " action=CANCELLED_MISSING_OR_TIMEOUT";
        }

        if(!adjacentTo(npc.x,npc.y)){
            if(movement.queued()==0){
                pendingScene=null;
                return "V511_NPC_BANK scene="+scene+
                    " action=CANCELLED_PATH_ENDED_NOT_ADJACENT";
            }
            return null;
        }

        pendingScene=null;
        movement.clearQueuedPath();

        NpcAction synthetic=new NpcAction(17,scene);
        NpcInteractionRouter.Route route=
            NpcInteractionRouter.resolve(synthetic,npc);

        return openNow(
            npc,
            synthetic,
            route,
            serverPackets,
            "OPENED_AFTER_AUTHORITATIVE_ARRIVAL"
        );
    }

    boolean hasPending(){
        return pendingScene!=null;
    }

    Integer pendingScene(){
        return pendingScene;
    }

    private String openNow(
        NpcEntity npc,
        NpcAction request,
        NpcInteractionRouter.Route route,
        ServerPacketWriter serverPackets,
        String reason
    )throws IOException{
        bank.open(serverPackets);

        return "V511_BANK_OPEN_NPC npc="+npc.definitionId+
            " scene="+npc.sceneIndex+
            " world="+npc.x+","+npc.y+
            " request="+request+
            " route="+route+
            " authorityWorld="+movement.x()+","+movement.y()+
            " distance="+chebyshev(
                movement.x(),movement.y(),npc.x,npc.y)+
            " root="+BankState.BANK_ROOT+
            " action="+reason;
    }

    private boolean adjacentTo(int x,int y){
        return chebyshev(movement.x(),movement.y(),x,y)<=1;
    }

    private static int chebyshev(int x0,int y0,int x1,int y1){
        return Math.max(Math.abs(x1-x0),Math.abs(y1-y0));
    }
}
