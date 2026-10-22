package spk.local;

import java.io.IOException;
import spk.content.api.ContentNpcOptionResult;
import spk.content.api.ContentNpcService;
import spk.content.builtin.LocalLabCoreContentModule;

/**
 * Residual definition-routed NPC interaction coordinator.
 *
 * LocalSession retains higher-priority special intercepts (pet Pick-up,
 * Yoshiganger Switch-effect and certified combat-dummy Attack). All remaining
 * decoded NPC actions are routed here through exact-current NPC definitions.
 */
final class LocalRoutedNpcInteractionHandler {
    @FunctionalInterface
    interface BankRootOpenAction {
        String open() throws IOException;
    }

    @FunctionalInterface
    interface BankRootOwner {
        String publish(
            BankRootOpenAction action
        ) throws IOException;
    }

    private final NpcRegistry npcs;
    private final BankState bank;
    private final MovementState movement;
    private final InteractionApproachResolver approach;
    private final ContentRegistry contentRegistry;
    private final LocalMakeoverMageHandler makeoverMage;
    private BankRootOwner bankRootOwner=
        action->action.open();
    private boolean bankRootOwnerInstalled;

    private Integer pendingBankScene;
    private NpcEntity pendingBankNpc;
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
            null,
            null,
            null
        );
    }

    LocalRoutedNpcInteractionHandler(
        NpcRegistry npcs,
        BankState bank,
        MovementState movement,
        ContentRegistry contentRegistry
    ){
        this(
            npcs,
            bank,
            movement,
            contentRegistry,
            null,
            null
        );
    }

    LocalRoutedNpcInteractionHandler(
        NpcRegistry npcs,
        BankState bank,
        MovementState movement,
        ContentRegistry contentRegistry,
        WorldPlayer worldPlayer,
        EquipmentState equipment
    ){
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.approach=new InteractionApproachResolver(this.movement);
        this.contentRegistry=contentRegistry;

        if((worldPlayer==null)!=(equipment==null))
            throw new IllegalArgumentException(
                "worldPlayer/equipment must be supplied together"
            );

        this.makeoverMage=
            worldPlayer==null
                ?null
                :new LocalMakeoverMageHandler(
                    worldPlayer,
                    equipment,
                    movement,
                    npcs,
                    contentRegistry
                );
    }

    void installBankRootOwner(
        BankRootOwner owner
    ){
        BankRootOwner checked=
            java.util.Objects.requireNonNull(
                owner,
                "owner"
            );

        if(bankRootOwnerInstalled)
            throw new IllegalStateException(
                "NPC bank root owner already installed"
            );

        bankRootOwner=checked;
        bankRootOwnerInstalled=true;
    }

    LocalMakeoverMageHandler makeoverMage(){
        return makeoverMage;
    }

    String tickMakeover(
        long now,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        return makeoverMage==null
            ?null
            :makeoverMage.tick(
                now,
                serverPackets,
                tag
            );
    }

    String handle(
        NpcAction request,
        NpcEntity clicked,
        ServerPacketWriter serverPackets
    )throws IOException{
        return handle(
            request,
            clicked,
            serverPackets,
            ""
        );
    }

    String handle(
        NpcAction request,
        NpcEntity clicked,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        if(request==null)return null;

        NpcInteractionRouter.Route route=
            NpcInteractionRouter.resolve(request,clicked);

        ContentNpcOptionResult content=
            contentDecision(
                route,
                clicked
            );

        if(content!=null&&
           content.hasAction())
            return executeContentAction(
                content.actionKey(),
                request,
                clicked,
                serverPackets,
                tag
            );

        NpcInteractionRouter.Service service=
            contentService(
                route,
                content
            );

        if(service==NpcInteractionRouter.Service.BANK && clicked!=null){
            if(adjacentTo(clicked.x,clicked.y)){
                clearPendingBank();
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
                clearPendingBank();
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
            pendingBankNpc=clicked;
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
        if(npc==null||
           npc!=pendingBankNpc||
           now>pendingBankDeadlineMs){
            clearPendingBank();
            movement.clearQueuedPath();
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

                clearPendingBank();
                return "V511_NPC_BANK scene="+scene+
                    " action=CANCELLED_PATH_ENDED_NOT_ADJACENT"+
                    " approach="+reroute;
            }
            return null;
        }

        movement.clearQueuedPath();

        // Preserve the current R8.5 deferred-bank reconstruction exactly:
        // the arrival path synthesizes option-3/opcode17 before re-resolving.
        NpcAction synthetic=new NpcAction(17,scene);
        NpcInteractionRouter.Route route=
            NpcInteractionRouter.resolve(synthetic,npc);

        String result=
            openBank(
                npc,
                synthetic,
                route,
                serverPackets,
                "OPENED_AFTER_AUTHORITATIVE_ARRIVAL"
            );

        /*
         * A failed standalone Bank publication must retain the exact banker
         * identity so the next authoritative tick can retry.
         */
        clearPendingBank();
        return result;
    }

    boolean hasPendingBank(){
        return pendingBankScene!=null;
    }

    NpcEntity pendingBankNpc(){
        return pendingBankNpc;
    }

    private void clearPendingBank(){
        pendingBankScene=null;
        pendingBankNpc=null;
        pendingBankDeadlineMs=0L;
    }

    private ContentNpcOptionResult
        contentDecision(
            NpcInteractionRouter.Route route,
            NpcEntity clicked
        )
    {
        if(contentRegistry==null||
           route==null||
           clicked==null||
           route.option<1)
            return null;

        return contentRegistry.dispatchNpcOption(
            clicked.definitionId,
            route.option,
            clicked.x,
            clicked.y
        );
    }

    private NpcInteractionRouter.Service contentService(
        NpcInteractionRouter.Route route,
        ContentNpcOptionResult content
    ){
        if(route==null)
            return NpcInteractionRouter.Service.NONE;

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

    private String executeContentAction(
        String actionKey,
        NpcAction request,
        NpcEntity clicked,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        if(LocalLabCoreContentModule
                .MAKEOVER_MAGE_ACTION
                .equals(actionKey)){
            if(makeoverMage==null)
                return "CONTENT_NPC_ACTION key="+
                    actionKey+
                    " result=REJECTED_SESSION_ACTION_UNAVAILABLE";

            boolean handled=
                makeoverMage.beginIfSupported(
                    request,
                    clicked,
                    serverPackets,
                    tag
                );

            return handled
                ?null
                :"CONTENT_NPC_ACTION key="+
                    actionKey+
                    " result=REJECTED_TARGET_OR_OPTION_MISMATCH";
        }

        return "CONTENT_NPC_ACTION key="+
            actionKey+
            " result=REJECTED_UNSUPPORTED_CONTENT_ACTION";
    }

    private String openBank(
        NpcEntity npc,
        NpcAction request,
        NpcInteractionRouter.Route route,
        ServerPacketWriter serverPackets,
        String reason
    )throws IOException{
        return bankRootOwner.publish(
            ()->{
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
        );
    }

    private boolean adjacentTo(int x,int y){
        return chebyshev(movement.x(),movement.y(),x,y)<=1;
    }

    private static int chebyshev(int x0,int y0,int x1,int y1){
        return Math.max(Math.abs(x1-x0),Math.abs(y1-y0));
    }
}
