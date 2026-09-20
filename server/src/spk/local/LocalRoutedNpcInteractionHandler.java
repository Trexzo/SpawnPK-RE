package spk.local;

import java.io.IOException;
import spk.content.api.ContentNpcOptionResult;
import spk.content.api.ContentNpcService;

/**
 * Residual definition-routed NPC interaction coordinator.
 *
 * LocalSession retains higher-priority special intercepts (pet Pick-up,
 * Yoshiganger Switch-effect and certified combat-dummy Attack). All remaining
 * decoded NPC actions are routed here through exact-current NPC definitions.
 */
final class LocalRoutedNpcInteractionHandler {
    private final NpcRegistry npcs;
    private final BankState bank;
    private final MovementState movement;
    private final InteractionApproachResolver approach;
    private final ContentRegistry contentRegistry;

    private Integer pendingBankScene;
    private long pendingBankDeadlineMs;

    LocalRoutedNpcInteractionHandler(
        NpcRegistry npcs,
        BankState bank,
        MovementState movement
    ){
        this(
            npcs,
            bank,
            movement,
            null
        );
    }

    LocalRoutedNpcInteractionHandler(
        NpcRegistry npcs,
        BankState bank,
        MovementState movement,
        ContentRegistry contentRegistry
    ){
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.approach=new InteractionApproachResolver(this.movement);
        this.contentRegistry=contentRegistry;
    }

    String handle(
        NpcAction request,
        NpcEntity clicked,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(request==null)return null;

        NpcInteractionRouter.Route route=
            NpcInteractionRouter.resolve(request,clicked);

        NpcInteractionRouter.Service service=
            contentService(
                route,
                clicked
            );

        if(service==NpcInteractionRouter.Service.BANK && clicked!=null){
            if(adjacentTo(clicked.x,clicked.y)){
                pendingBankScene=null;
                return openBank(
                    clicked,
                    request,
                    route,
                    serverPackets,
                    "OPENED_ADJACENT_IMMEDIATE"
                );
            }

            InteractionApproachResolver.Result approachResult=
                approach.queueAdjacent(
                    clicked.x,
                    clicked.y
                );

            if(!approachResult.queued()){
                pendingBankScene=null;
                return "V511_NPC_BANK "+request+
                    " clicked="+clicked+
                    " route="+route+
                    " distance="+chebyshev(
                        movement.x(),movement.y(),clicked.x,clicked.y)+
                    " action=REJECTED_SERVER_APPROACH_"+
                    approachResult.status+
                    " approach="+approachResult;
            }

            pendingBankScene=clicked.sceneIndex;
            pendingBankDeadlineMs=System.currentTimeMillis()+10_000L;
            return "V511_NPC_BANK "+request+
                " clicked="+clicked+
                " route="+route+
                " distance="+chebyshev(
                    movement.x(),movement.y(),clicked.x,clicked.y)+
                " action=DEFERRED_UNTIL_ADJACENT"+
                " serverApproach="+approachResult;
        }

        NpcEntity pet=npcs.pet();
        return "V511_NPC_ACTION "+request+
            " route="+route+
            " result=DECODED_SEMANTIC_"+service+
            " clicked="+clicked+
            " petScene="+(pet==null?-1:pet.sceneIndex);
    }

    String tick(long now,ServerPacketWriter serverPackets)throws IOException{
        Integer scene=pendingBankScene;
        if(scene==null)return null;

        NpcEntity npc=npcs.scene(scene);
        if(npc==null||now>pendingBankDeadlineMs){
            pendingBankScene=null;
            return "V511_NPC_BANK scene="+scene+
                " action=CANCELLED_MISSING_OR_TIMEOUT";
        }

        if(!adjacentTo(npc.x,npc.y)){
            if(movement.queued()==0){
                InteractionApproachResolver.Result reroute=
                    approach.queueAdjacent(
                        npc.x,
                        npc.y
                    );

                if(reroute.queued())
                    return "V511_NPC_BANK scene="+scene+
                        " action=SERVER_REROUTED_MOVING_TARGET"+
                        " approach="+reroute;

                pendingBankScene=null;
                return "V511_NPC_BANK scene="+scene+
                    " action=CANCELLED_PATH_ENDED_NOT_ADJACENT"+
                    " approach="+reroute;
            }
            return null;
        }

        pendingBankScene=null;
        movement.clearQueuedPath();

        // Preserve the current R8.5 deferred-bank reconstruction exactly:
        // the arrival path synthesizes option-3/opcode17 before re-resolving.
        NpcAction synthetic=new NpcAction(17,scene);
        NpcInteractionRouter.Route route=
            NpcInteractionRouter.resolve(synthetic,npc);

        return openBank(
            npc,
            synthetic,
            route,
            serverPackets,
            "OPENED_AFTER_AUTHORITATIVE_ARRIVAL"
        );
    }

    boolean hasPendingBank(){
        return pendingBankScene!=null;
    }

    private NpcInteractionRouter.Service contentService(
        NpcInteractionRouter.Route route,
        NpcEntity clicked
    ){
        if(contentRegistry==null||
           route==null||
           clicked==null||
           route.option<1)
            return route==null
                ?NpcInteractionRouter.Service.NONE
                :route.service;

        ContentNpcOptionResult content=
            contentRegistry.dispatchNpcOption(
                clicked.definitionId,
                route.option,
                clicked.x,
                clicked.y
            );

        if(content==null)
            return route.service;

        ContentNpcService service=
            content.service();

        switch(service){
            case BANK:
                return NpcInteractionRouter.Service.BANK;
            case ATTACK:
                return NpcInteractionRouter.Service.ATTACK;
            case TALK:
                return NpcInteractionRouter.Service.TALK;
            case TRADE:
                return NpcInteractionRouter.Service.TRADE;
            case UNIMPLEMENTED:
                return NpcInteractionRouter.Service.UNIMPLEMENTED;
            case NONE:
            default:
                return NpcInteractionRouter.Service.NONE;
        }
    }

    private String openBank(
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
