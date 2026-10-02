package spk.local;

import java.io.*;
import java.util.*;

/**
 * Shared-world two-player trade state machine for the exact current 317 client UI.
 * Offers are reservations until the second-stage mutual accept, so cancel/disconnect
 * never needs to roll inventory state back.
 */
final class TradeService {
    static final int INVENTORY_OVERLAY=3321, INVENTORY_GRID=3322, TRADE_ROOT=3323;
    static final int OWN_OFFER=3415, OTHER_OFFER=3416, PARTNER_TEXT=3417, STATUS_TEXT=3431;
    static final int FIRST_ACCEPT=3420, FIRST_DECLINE=3422, CONFIRM_ROOT=3443, CONFIRM_STATUS=3535, GIVE_LIST=3538, RECEIVE_LIST=3539, FINAL_ACCEPT=3546, FINAL_DECLINE=3548;
    private enum Stage { OFFERING, CONFIRMING, COMMITTED, CANCELLED }
    private enum XKind { OFFER, REMOVE }

    @FunctionalInterface
    interface RootPublication {
        void publish() throws IOException;
    }

    @FunctionalInterface
    interface RootOwner {
        void publish(RootPublication action) throws IOException;
    }

    @FunctionalInterface
    interface CompetingRootPublication {
        boolean publish() throws Exception;
    }

    private static final IdentityHashMap<World,State> STATES=new IdentityHashMap<>();
    private TradeService(){}

    static synchronized void register(World world,WorldPlayer player,BankState bank,ServerPacketWriter writer,Runnable save){
        register(
            world,
            player,
            player==null?0L:player.generation(),
            bank,
            writer,
            save
        );
    }

    static synchronized void register(
        World world,
        WorldPlayer player,
        long expectedGeneration,
        BankState bank,
        ServerPacketWriter writer,
        Runnable save
    ){
        register(
            world,
            player,
            expectedGeneration,
            bank,
            writer,
            save,
            action->action.publish()
        );
    }

    static synchronized void register(
        World world,
        WorldPlayer player,
        long expectedGeneration,
        BankState bank,
        ServerPacketWriter writer,
        Runnable save,
        RootOwner rootOwner
    ){
        Objects.requireNonNull(world,"world");
        Objects.requireNonNull(player,"player");
        Objects.requireNonNull(bank,"bank");
        Objects.requireNonNull(writer,"writer");
        Objects.requireNonNull(rootOwner,"rootOwner");

        if(world.closed())
            throw new IllegalStateException(
                "cannot register TradeService on closed World"
            );

        removePlayerContexts(
            player,
            null,
            "CONTEXT_REPLACED",
            true
        );

        state(world).contexts.put(
            player.id(),
            new Context(
                world,
                player,
                expectedGeneration,
                bank,
                writer,
                save,
                rootOwner
            )
        );
    }
    static synchronized void unregister(WorldPlayer player){
        if(player==null)return;
        removePlayerContexts(
            player,
            null,
            "DISCONNECT",
            true
        );
    }

    static synchronized void unregister(
        WorldPlayer player,
        ServerPacketWriter writer
    ){
        if(player==null||writer==null)return;

        removePlayerContexts(
            player,
            writer,
            "DISCONNECT",
            true
        );
    }

    enum BrokenWriterPeerClose {
        NONE,
        COMMITTED,
        RETRACTED_RETRYABLE,
        TERMINAL
    }

    static final class BrokenWriterRetirement {
        final BrokenWriterPeerClose peerClose;
        final WorldPlayer peerOwner;
        final ServerPacketWriter peerWriter;
        final IOException peerFailure;

        BrokenWriterRetirement(
            BrokenWriterPeerClose peerClose,
            WorldPlayer peerOwner,
            ServerPacketWriter peerWriter,
            IOException peerFailure
        ){
            this.peerClose=peerClose;
            this.peerOwner=peerOwner;
            this.peerWriter=peerWriter;
            this.peerFailure=peerFailure;
        }

        boolean terminalPeer(){
            return peerClose==
                BrokenWriterPeerClose.TERMINAL&&
                peerOwner!=null&&
                peerWriter!=null;
        }
    }

    /**
     * Retires runtime Trade authority after the supplied writer has already
     * been classified terminal/non-retractable. Never publishes to that writer
     * again. A distinct peer close is attempted through the recoverable writer
     * transaction so queue-backed rejection remains retractable; only a
     * non-retractable peer failure is returned as a second terminal writer.
     */
    static synchronized BrokenWriterRetirement retireBrokenWriter(
        WorldPlayer player,
        ServerPacketWriter writer
    ){
        if(player==null||writer==null)
            return new BrokenWriterRetirement(
                BrokenWriterPeerClose.NONE,
                null,
                null,
                null
            );

        BrokenWriterPeerClose peerClose=
            BrokenWriterPeerClose.NONE;
        WorldPlayer peerOwner=null;
        ServerPacketWriter peerWriter=null;
        IOException peerFailure=null;

        for(
            Iterator<Map.Entry<World,State>> it=
                STATES.entrySet().iterator();
            it.hasNext();
        ){
            State s=it.next().getValue();
            Context c=s.contexts.get(
                player.id()
            );

            if(c==null||
               c.player!=player||
               c.writer!=writer)
                continue;

            Trade trade=c.trade;

            if(trade!=null&&
               trade.stage!=Stage.CANCELLED&&
               trade.stage!=Stage.COMMITTED){
                Context peer=trade.other(c);

                trade.stage=Stage.CANCELLED;
                detach(s,trade);

                if(peer!=null&&
                   peer.writer!=writer){
                    /*
                     * The peer generation can change independently of the
                     * TradeService monitor. Fence the recoverable close under
                     * the peer PlayerRegistry monitor and revalidate exact Context
                     * identity + generation at the publication boundary.
                     *
                     * Do not acquire World.lifecycleLock here: runtime binding
                     * registration already orders World lifecycle ->
                     * TradeService, so reversing that order would deadlock.
                     */
                    synchronized(peer.world.players()){
                        State peerState=
                            STATES.get(
                                peer.world
                            );

                        if(!peer.world.closed()&&
                           peerState!=null&&
                           peerState.contexts.get(
                                peer.player.id()
                           )==peer&&
                           peer.ownerCurrent()){
                            peerOwner=peer.player;
                            peerWriter=peer.writer;

                            try{
                                ServerPacketWriter.RecoverablePacketResult result=
                                    peer.writer
                                        .publishRecoverablePacketIfIdle(
                                            ()->peer.writer.fixed(
                                                219,
                                                new byte[0]
                                            )
                                        );

                                peerClose=
                                    result==
                                        ServerPacketWriter
                                            .RecoverablePacketResult
                                            .COMMITTED
                                        ?BrokenWriterPeerClose.COMMITTED
                                        :BrokenWriterPeerClose
                                            .RETRACTED_RETRYABLE;
                            }catch(IOException failure){
                                peerClose=
                                    BrokenWriterPeerClose.TERMINAL;
                                peerFailure=failure;
                            }
                        }
                    }
                }
            }else{
                c.trade=null;
                c.pendingX=null;
            }

            if(s.contexts.get(
                    player.id()
                )==c)
                s.contexts.remove(
                    player.id()
                );

            if(s.contexts.isEmpty()&&
               s.trades.isEmpty())
                it.remove();
        }

        return new BrokenWriterRetirement(
            peerClose,
            peerOwner,
            peerWriter,
            peerFailure
        );
    }
    static synchronized void closeWorld(
        World world
    ){
        if(world==null)
            return;

        State state=
            STATES.remove(
                world
            );

        if(state==null)
            return;

        LinkedHashSet<Trade> active=
            new LinkedHashSet<>(
                state.trades.values()
            );

        for(Trade trade:active){
            if(trade==null)
                continue;

            trade.stage=
                Stage.CANCELLED;
            trade.a.trade=null;
            trade.b.trade=null;
            trade.a.pendingX=null;
            trade.b.pendingX=null;
        }

        for(Context context:
                state.contexts.values()){
            context.trade=null;
            context.pendingX=null;
        }

        state.trades.clear();
        state.contexts.clear();
    }

    static String start(
        World world,
        WorldPlayer a,
        WorldPlayer b
    )throws IOException{
        if(world==null||a==null||b==null)
            return "TRADE_UI_REJECTED_CONTEXT_MISSING";

        if(world.closed())
            return "TRADE_UI_REJECTED_WORLD_CLOSED";

        final Context ca;
        final Context cb;

        synchronized(TradeService.class){
            State s=STATES.get(world);
            if(s==null)
                return "TRADE_UI_REJECTED_CONTEXT_MISSING";

            ca=s.contexts.get(a.id());
            cb=s.contexts.get(b.id());

            if(ca==null||cb==null)
                return "TRADE_UI_REJECTED_CONTEXT_MISSING";

            if(ca.player!=a||cb.player!=b)
                return "TRADE_UI_REJECTED_STALE_CONTEXT";
        }

        final boolean[] opened={false};

        try{
            boolean admitted=
                world.withOpenTwoPlayerOwnershipIfCurrent(
                    a,
                    ca.ownerGeneration,
                    b,
                    cb.ownerGeneration,
                    ()->{
                        synchronized(TradeService.class){
                            State s=STATES.get(world);

                            if(s==null||
                               s.contexts.get(a.id())!=ca||
                               s.contexts.get(b.id())!=cb||
                               !ca.ownerCurrent()||
                               !cb.ownerCurrent())
                                return;

                            Trade priorA=
                                liveTrade(ca);
                            Trade priorB=
                                liveTrade(cb);

                            Trade t=new Trade(ca,cb);
                            boolean[] rootPublicationEntered={
                                false,
                                false
                            };
                            Context[] packetPublicationFailure={
                                null
                            };

                            try{
                                ca.rootOwner.publish(
                                    ()->{
                                        rootPublicationEntered[0]=true;
                                        try{
                                            publishFirstFor(t,ca);
                                            ca.writer.varShort(
                                                126,
                                                BootstrapPackets.widgetText126(
                                                    STATUS_TEXT,
                                                    ""
                                                )
                                            );
                                        }catch(IOException failure){
                                            packetPublicationFailure[0]=ca;
                                            throw failure;
                                        }
                                    }
                                );

                                cb.rootOwner.publish(
                                    ()->{
                                        rootPublicationEntered[1]=true;
                                        try{
                                            publishFirstFor(t,cb);
                                            cb.writer.varShort(
                                                126,
                                                BootstrapPackets.widgetText126(
                                                    STATUS_TEXT,
                                                    ""
                                                )
                                            );
                                        }catch(IOException failure){
                                            packetPublicationFailure[0]=cb;
                                            throw failure;
                                        }
                                    }
                                );
                            }catch(IOException failure){
                                if(packetPublicationFailure[0]!=null)
                                    retireTerminalStartRootPublicationFailure(
                                        packetPublicationFailure[0],
                                        ca,
                                        cb,
                                        priorA,
                                        priorB,
                                        rootPublicationEntered,
                                        failure
                                    );
                                else{
                                    /*
                                     * RootOwner itself may fail after the
                                     * packet action completed. That does not
                                     * prove either transport terminal, so keep
                                     * the established reversible rollback.
                                     */
                                    restoreOrCloseEnteredTradeRootAfterStartFailure(
                                        ca,
                                        rootPublicationEntered[0],
                                        priorA,
                                        failure
                                    );
                                    restoreOrCloseEnteredTradeRootAfterStartFailure(
                                        cb,
                                        rootPublicationEntered[1],
                                        priorB,
                                        failure
                                    );
                                }
                                throw failure;
                            }catch(RuntimeException failure){
                                restoreOrCloseEnteredTradeRootAfterStartFailure(
                                    ca,
                                    rootPublicationEntered[0],
                                    priorA,
                                    failure
                                );
                                restoreOrCloseEnteredTradeRootAfterStartFailure(
                                    cb,
                                    rootPublicationEntered[1],
                                    priorB,
                                    failure
                                );
                                throw failure;
                            }catch(Error failure){
                                restoreOrCloseEnteredTradeRootAfterStartFailure(
                                    ca,
                                    rootPublicationEntered[0],
                                    priorA,
                                    failure
                                );
                                restoreOrCloseEnteredTradeRootAfterStartFailure(
                                    cb,
                                    rootPublicationEntered[1],
                                    priorB,
                                    failure
                                );
                                throw failure;
                            }

                            retirePriorTradeForReplacement(
                                s,
                                priorA,
                                ca,
                                cb
                            );
                            if(priorB!=priorA)
                                retirePriorTradeForReplacement(
                                    s,
                                    priorB,
                                    ca,
                                    cb
                                );

                            s.trades.put(a.id(),t);
                            s.trades.put(b.id(),t);
                            ca.trade=t;
                            cb.trade=t;
                            opened[0]=true;
                        }
                    }
                );

            if(!admitted||!opened[0])
                return "TRADE_UI_REJECTED_STALE_CONTEXT";
        }catch(IOException failure){
            throw failure;
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IOException(
                "trade root publication failed",
                failure
            );
        }

        return "TRADE_UI_OPEN root=3323 overlay=3321 own=3415 other=3416 firstAccept=3420 confirmRoot=3443 finalAccept=3546";
    }

    static synchronized String handleItemAction(WorldPlayer player,ItemContainerAction a)throws IOException{
        Context c=context(player);Trade t=liveTrade(c);if(t==null)return null;
        if(t.stage!=Stage.OFFERING)return "TRADE_ITEM_REJECTED_STAGE_"+t.stage;
        if(a.widgetId!=INVENTORY_GRID&&a.widgetId!=OWN_OFFER)return null;
        int amount=amountFor(a.opcode);
        if(a.widgetId==INVENTORY_GRID){
            if(a.itemId<0)return "TRADE_OFFER_REJECTED_ITEM";
            BankState.Stack slot=c.bank.inventoryAt(a.slot);
            if(slot==null||slot.itemId!=a.itemId)return "TRADE_OFFER_REJECTED_SLOT_MISMATCH slot="+a.slot+" item="+a.itemId;
            if(ItemPolicyRepository.explicitlyUntradeable(a.itemId))return "TRADE_OFFER_REJECTED_EXPLICIT_UNTRADEABLE item="+a.itemId;
            if(amount<0){
                try{
                    c.writer.fixed(
                        27,
                        new byte[0]
                    );
                }catch(IOException failure){
                    retireTerminalParticipantPublicationFailure(
                        t,
                        c,
                        failure
                    );
                    throw failure;
                }
                c.pendingX=
                    new PendingX(
                        XKind.OFFER,
                        a.itemId
                    );
                return "TRADE_OFFER_X_PROMPT item="+a.itemId;
            }
            int available=Math.max(0,c.bank.inventoryCount(a.itemId)-t.offer(c).getOrDefault(a.itemId,0));
            int add=amount==Integer.MAX_VALUE?available:Math.min(amount,available);if(add<=0)return "TRADE_OFFER_REJECTED_NO_AVAILABLE item="+a.itemId;
            String presentationFailure=changeOffer(t,c,a.itemId,add);
            if(presentationFailure!=null)return presentationFailure;
            return "TRADE_OFFER_OK item="+a.itemId+" qty="+add+" explicitTradeable="+ItemPolicyRepository.explicitTradeable(a.itemId);
        }else{
            int item=offerItemAt(t,c,a.slot);if(item<0||item!=a.itemId)return "TRADE_REMOVE_REJECTED_SLOT_MISMATCH slot="+a.slot+" item="+a.itemId+" resolved="+item;
            if(amount<0){
                try{
                    c.writer.fixed(
                        27,
                        new byte[0]
                    );
                }catch(IOException failure){
                    retireTerminalParticipantPublicationFailure(
                        t,
                        c,
                        failure
                    );
                    throw failure;
                }
                c.pendingX=
                    new PendingX(
                        XKind.REMOVE,
                        item
                    );
                return "TRADE_REMOVE_X_PROMPT item="+item;
            }
            int have=t.offer(c).getOrDefault(item,0);int rem=amount==Integer.MAX_VALUE?have:Math.min(amount,have);if(rem<=0)return "TRADE_REMOVE_REJECTED_EMPTY item="+item;
            String presentationFailure=changeOffer(t,c,item,-rem);
            if(presentationFailure!=null)return presentationFailure;
            return "TRADE_REMOVE_OK item="+item+" qty="+rem;
        }
    }

    static synchronized String handleAmount(WorldPlayer player,int amount)throws IOException{
        Context c=context(player);
        Trade t=liveTrade(c);
        if(t==null||c.pendingX==null)return null;

        PendingX p=c.pendingX;

        if(amount<=0){
            c.pendingX=null;
            return "TRADE_X_REJECTED_AMOUNT amount="+amount;
        }

        if(t.stage!=Stage.OFFERING){
            c.pendingX=null;
            return "TRADE_X_REJECTED_STAGE_"+t.stage;
        }

        if(p.kind==XKind.OFFER){
            if(ItemPolicyRepository.explicitlyUntradeable(p.item)){
                c.pendingX=null;
                return "TRADE_X_REJECTED_EXPLICIT_UNTRADEABLE item="+p.item;
            }

            int available=Math.max(
                0,
                c.bank.inventoryCount(p.item)-
                    t.offer(c).getOrDefault(p.item,0)
            );
            int add=Math.min(amount,available);

            if(add<=0){
                c.pendingX=null;
                return "TRADE_X_REJECTED_NO_AVAILABLE";
            }

            String presentationFailure=
                changeOffer(
                    t,
                    c,
                    p.item,
                    add
                );

            if(presentationFailure!=null)
                return presentationFailure;

            return "TRADE_OFFER_X_OK item="+p.item+" qty="+add;
        }

        int have=
            t.offer(c).getOrDefault(
                p.item,
                0
            );
        int rem=Math.min(
            amount,
            have
        );

        if(rem<=0){
            c.pendingX=null;
            return "TRADE_X_REMOVE_EMPTY";
        }

        String presentationFailure=
            changeOffer(
                t,
                c,
                p.item,
                -rem
            );

        if(presentationFailure!=null)
            return presentationFailure;

        return "TRADE_REMOVE_X_OK item="+p.item+" qty="+rem;
    }

    static String handleWidget(WorldPlayer player,int widget)throws IOException{
        Runnable[] postCommitSaves={
            null,
            null
        };
        String result;

        synchronized(TradeService.class){
            result=
                handleWidgetLocked(
                    player,
                    widget,
                    postCommitSaves
                );
        }

        if(postCommitSaves[0]!=null)
            postCommitSaves[0].run();
        if(postCommitSaves[1]!=null)
            postCommitSaves[1].run();

        return result;
    }

    private static String handleWidgetLocked(
        WorldPlayer player,
        int widget,
        Runnable[] postCommitSaves
    )throws IOException{
        Context c=context(player);Trade t=liveTrade(c);if(t==null)return null;
        if(widget==FIRST_DECLINE || widget==FINAL_DECLINE){
            cancel0(state(c.world),c,widget==FIRST_DECLINE?"FIRST_STAGE_DECLINE":"FINAL_STAGE_DECLINE",true);
            return "TRADE_CANCELLED_DECLINE widget="+widget;
        }
        if(widget==FIRST_ACCEPT){
            if(t.stage!=Stage.OFFERING)return "TRADE_FIRST_ACCEPT_REJECTED_STAGE_"+t.stage;

            boolean otherAccepted=
                c==t.a
                    ?t.firstAcceptedB
                    :t.firstAcceptedA;

            if(!otherAccepted){
                String pairFailure=
                    publishOneSidedAcceptStatus(
                        t,
                        c,
                        false
                    );

                if(pairFailure!=null)
                    return pairFailure;

                t.setFirstAccepted(
                    c,
                    true
                );
                return "TRADE_FIRST_ACCEPT_WAITING_OTHER";
            }

            t.setFirstAccepted(c,true);
            t.a.pendingX=t.b.pendingX=null;
            try{
                publishConfirm(t);
            }catch(IOException failure){
                cancel0(
                    state(c.world),
                    c,
                    "CONFIRM_ROOT_PUBLICATION_FAILED",
                    true
                );
                throw failure;
            }catch(RuntimeException failure){
                cancel0(
                    state(c.world),
                    c,
                    "CONFIRM_ROOT_PUBLICATION_FAILED",
                    true
                );
                throw failure;
            }catch(Error failure){
                cancel0(
                    state(c.world),
                    c,
                    "CONFIRM_ROOT_PUBLICATION_FAILED",
                    true
                );
                throw failure;
            }
            t.stage=Stage.CONFIRMING;
            return "TRADE_FIRST_ACCEPT_BOTH_CONFIRM_OPEN root=3443";
        }
        if(widget==FINAL_ACCEPT){
            if(t.stage!=Stage.CONFIRMING)return "TRADE_FINAL_ACCEPT_REJECTED_STAGE_"+t.stage;

            boolean otherAccepted=
                c==t.a
                    ?t.finalAcceptedB
                    :t.finalAcceptedA;

            if(!otherAccepted){
                String pairFailure=
                    publishOneSidedAcceptStatus(
                        t,
                        c,
                        true
                    );

                if(pairFailure!=null)
                    return pairFailure;

                t.setFinalAccepted(
                    c,
                    true
                );
                return "TRADE_FINAL_ACCEPT_WAITING_OTHER";
            }

            t.setFinalAccepted(c,true);
            return commit(
                t,
                postCommitSaves
            );
        }
        return null;
    }

    static synchronized boolean cancelIfActive(WorldPlayer player,String reason)throws IOException{
        Context c=context(player);Trade t=liveTrade(c);if(t==null)return false;cancel0(state(c.world),c,reason,true);return true;
    }

    static synchronized boolean retireForCompetingRoot(
        WorldPlayer player,
        String reason
    )throws IOException{
        Context c=context(player);
        Trade t=liveTrade(c);

        if(t==null)
            return false;

        retireForCompetingRoot0(
            c,
            t
        );
        return true;
    }

    static synchronized boolean publishCompetingRoot(
        WorldPlayer player,
        CompetingRootPublication publication
    )throws Exception{
        Objects.requireNonNull(
            player,
            "player"
        );
        CompetingRootPublication checked=
            Objects.requireNonNull(
                publication,
                "publication"
            );

        Context c=context(player);
        Trade t=liveTrade(c);

        boolean published=
            checked.publish();

        if(!published)
            return false;

        if(t!=null&&
           c!=null&&
           c.trade==t&&
           tradeCurrent(t))
            retireForCompetingRoot0(
                c,
                t
            );

        return true;
    }

    private static void retireForCompetingRoot0(
        Context current,
        Trade trade
    )throws IOException{
        Context peer=trade.other(current);
        trade.stage=Stage.CANCELLED;
        detach(state(current.world),trade);

        IOException terminalFailure=null;

        /*
         * The replacement root is already committed, but the stored peer
         * Context may become stale before its close is published. Fence the
         * exact Context + generation under the peer PlayerRegistry monitor. Avoid
         * World.lifecycleLock here because runtime binding registration already
         * orders World lifecycle -> TradeService.
         */
        synchronized(peer.world.players()){
            State peerState=
                STATES.get(
                    peer.world
                );

            if(peer.world.closed()||
               peerState==null||
               peerState.contexts.get(
                    peer.player.id()
               )!=peer||
               !peer.ownerCurrent())
                return;

            try{
                peer.writer.fixed(
                    219,
                    new byte[0]
                );
            }catch(IOException failure){
                terminalFailure=failure;
            }
        }

        if(terminalFailure==null)
            return;

        /*
         * The initiating competing root already committed and this Trade is
         * already detached. Retire the terminal peer only after releasing the
         * PlayerRegistry monitor; never report the committed replacement root as failed.
         */
        LocalSessionRuntimeBindings
            .retireTerminalRuntimeBundle(
                peer.player,
                peer.writer,
                true,
                terminalFailure
            );

        System.err.println(
            "[ENGINE-R4] terminal Trade competing-root peer close failed; "+
            "peer runtime retired: "+
            terminalFailure
        );
    }

    static synchronized boolean active(WorldPlayer p){
        Context c=context(p);
        return liveTrade(c)!=null;
    }

    private static String commit(
        Trade t,
        Runnable[] postCommitSaves
    )throws IOException{
        Context a=t.a,b=t.b;

        /*
         * handleWidgetLocked(...) already owns TradeService.class. Do not acquire
         * player mutation locks from here: decoded session work owns those
         * locks before entering TradeService, so TradeService -> mutation
         * would invert the normal order. Both participants belong to this
         * Trade's single World; its PlayerRegistry monitor fences generation
         * changes for both while preserving the #1691 lock order.
         */
        synchronized(a.world.players()){
            if(a.world!=b.world)
                throw new IllegalStateException(
                    "cross-world Trade commit"
                );

                if(!a.ownerCurrent()||
                   !b.ownerCurrent()){
                    cancel0(
                        state(a.world),
                        a,
                        "FINAL_VALIDATION_STALE_OWNER",
                        false
                    );
                    return "TRADE_COMMIT_REJECTED_STALE_OWNER";
                }

                if(!offersAvailable(
                        a,
                        t.offerA
                    )||
                   !offersAvailable(
                        b,
                        t.offerB
                    )){
                    cancel0(
                        state(a.world),
                        a,
                        "FINAL_VALIDATION_MISSING_OFFER",
                        true
                    );
                    return "TRADE_COMMIT_REJECTED_OFFER_CHANGED";
                }

                int[][] postA=
                    projectedExchangeInventory(
                        a.bank,
                        t.offerA,
                        t.offerB
                    );
                int[][] postB=
                    projectedExchangeInventory(
                        b.bank,
                        t.offerB,
                        t.offerA
                    );

                if(postA==null||
                   postB==null){
                    cancel0(
                        state(a.world),
                        a,
                        "FINAL_VALIDATION_INVENTORY_SPACE",
                        true
                    );
                    return "TRADE_COMMIT_REJECTED_INVENTORY_SPACE";
                }

                byte[] inventoryA=
                    BootstrapPackets.itemContainer53(
                        BankState.NORMAL_INVENTORY_CONTAINER,
                        postA[0],
                        postA[1]
                    );
                byte[] inventoryB=
                    BootstrapPackets.itemContainer53(
                        BankState.NORMAL_INVENTORY_CONTAINER,
                        postB[0],
                        postB[1]
                    );

                int framedA=
                    inventoryA.length+4;
                int framedB=
                    inventoryB.length+4;

                ServerPacketWriter.AtomicPairBatch pair;

                try{
                    pair=
                        ServerPacketWriter.beginAtomicQueuePair(
                            a.writer,
                            framedA,
                            b.writer,
                            framedB
                        );
                }catch(IOException admissionFailure){
                    return "TRADE_COMMIT_REJECTED_PRESENTATION_ADMISSION "+
                        admissionFailure.getMessage();
                }

                if(pair!=null){
                    boolean committed=false;

                    try{
                        a.writer.varShort(
                            53,
                            inventoryA
                        );
                        a.writer.fixed(
                            219,
                            new byte[0]
                        );
                        b.writer.varShort(
                            53,
                            inventoryB
                        );
                        b.writer.fixed(
                            219,
                            new byte[0]
                        );
                        pair.commit();
                        committed=true;
                    }finally{
                        if(!committed)
                            pair.abort();
                    }
                }else{
                    /*
                     * Direct/non-queue writers have no cross-stream atomic
                     * commit boundary. Reject before either participant sees
                     * the final inventory projection or interface close. This
                     * mirrors the direct prospective-offer rule: canonical
                     * state stays untouched and the live Trade may retry only
                     * through a transport that can prove pair atomicity.
                     */
                    return "TRADE_COMMIT_REJECTED_DIRECT_NONATOMIC";
                }

                a.bank.replaceInventorySemantic(
                    postA[0],
                    postA[1]
                );
                b.bank.replaceInventorySemantic(
                    postB[0],
                    postB[1]
                );

                t.stage=Stage.COMMITTED;
                detach(
                    state(a.world),
                    t
                );

                /*
                 * Canonical Trade state is committed and detached while both
                 * TradeService.class and the PlayerRegistry monitor are still
                 * held. Persistence capture takes player mutation -> registry,
                 * so defer callbacks until handleWidget(...) has released both
                 * monitors. Preserve historical A-then-B propagation by
                 * executing these slots sequentially in the outer wrapper.
                 */
                postCommitSaves[0]=a.save;
                postCommitSaves[1]=b.save;

                return "TRADE_COMMITTED a="+
                    a.player.username()+
                    " gives="+t.offerA+
                    " b="+b.player.username()+
                    " gives="+t.offerB;
        }
    }

    private static String changeOffer(
        Trade trade,
        Context changing,
        int item,
        int delta
    )throws IOException{
        LinkedHashMap<Integer,Integer> prospective=
            new LinkedHashMap<>(
                trade.offer(changing)
            );

        int quantity=
            prospective.getOrDefault(
                item,
                0
            )+
            delta;

        if(quantity<=0)
            prospective.remove(item);
        else
            prospective.put(
                item,
                quantity
            );

        String presentationFailure=
            publishProspectiveFirstStage(
                trade,
                changing,
                prospective
            );

        if(presentationFailure!=null)
            return presentationFailure;

        LinkedHashMap<Integer,Integer> canonical=
            trade.offer(changing);
        canonical.clear();
        canonical.putAll(prospective);

        trade.firstAcceptedA=false;
        trade.firstAcceptedB=false;
        trade.finalAcceptedA=false;
        trade.finalAcceptedB=false;
        trade.a.pendingX=null;
        trade.b.pendingX=null;

        return null;
    }

    private static String publishProspectiveFirstStage(
        Trade trade,
        Context changing,
        LinkedHashMap<Integer,Integer> prospective
    )throws IOException{
        LinkedHashMap<Integer,Integer> offerA=
            changing==trade.a
                ?prospective
                :trade.offerA;
        LinkedHashMap<Integer,Integer> offerB=
            changing==trade.b
                ?prospective
                :trade.offerB;

        byte[][] postA=
            firstStagePostimage(
                trade,
                trade.a,
                offerA,
                offerB
            );
        byte[][] postB=
            firstStagePostimage(
                trade,
                trade.b,
                offerA,
                offerB
            );

        ServerPacketWriter.AtomicPairBatch pair;

        try{
            pair=
                ServerPacketWriter.beginAtomicQueuePair(
                    trade.a.writer,
                    firstStageFramedBytes(postA),
                    trade.b.writer,
                    firstStageFramedBytes(postB)
                );
        }catch(IOException admissionFailure){
            return "TRADE_OFFER_CHANGE_REJECTED_PRESENTATION_ADMISSION "+
                admissionFailure.getMessage();
        }

        if(pair!=null){
            boolean committed=false;

            try{
                writeFirstStagePostimage(
                    trade.a.writer,
                    postA
                );
                writeFirstStagePostimage(
                    trade.b.writer,
                    postB
                );
                pair.commit();
                committed=true;
            }finally{
                if(!committed)
                    pair.abort();
            }

            return null;
        }

        /*
         * Direct/non-queue writers cannot provide a cross-stream commit
         * boundary. Writer-local batches are not sufficient: participant A
         * could flush a prospective postimage before participant B fails.
         * Reject before either writer is touched and leave canonical offer
         * state unchanged. Queue-backed atomic-pair behavior above remains the
         * only two-party postimage commit path.
         */
        return "TRADE_OFFER_CHANGE_REJECTED_DIRECT_NONATOMIC";
    }

    private static byte[][] firstStagePostimage(
        Trade trade,
        Context context,
        LinkedHashMap<Integer,Integer> offerA,
        LinkedHashMap<Integer,Integer> offerB
    )throws IOException{
        Context other=
            trade.other(context);
        LinkedHashMap<Integer,Integer> ownOffer=
            context==trade.a
                ?offerA
                :offerB;
        LinkedHashMap<Integer,Integer> otherOffer=
            context==trade.a
                ?offerB
                :offerA;

        int[][] inventory=
            projectedInventory(
                context.bank,
                ownOffer
            );
        int[][] own=
            offerUi(ownOffer);
        int[][] peer=
            offerUi(otherOffer);

        return new byte[][]{
            BootstrapPackets.interfaceOverlay248(
                TRADE_ROOT,
                INVENTORY_OVERLAY
            ),
            BootstrapPackets.itemContainer53(
                INVENTORY_GRID,
                inventory[0],
                inventory[1]
            ),
            BootstrapPackets.itemContainer53(
                OWN_OFFER,
                own[0],
                own[1]
            ),
            BootstrapPackets.itemContainer53(
                OTHER_OFFER,
                peer[0],
                peer[1]
            ),
            BootstrapPackets.widgetText126(
                PARTNER_TEXT,
                "Trading With: "+
                    other.player.username()
            ),
            BootstrapPackets.widgetText126(
                STATUS_TEXT,
                ""
            )
        };
    }

    private static int firstStageFramedBytes(
        byte[][] postimage
    ){
        if(postimage==null||
           postimage.length!=6)
            throw new IllegalArgumentException(
                "first-stage postimage"
            );

        int bytes=
            1+
            postimage[0].length;

        for(int i=1;i<postimage.length;i++)
            bytes+=
                3+
                postimage[i].length;

        return bytes;
    }

    private static void writeFirstStagePostimage(
        ServerPacketWriter writer,
        byte[][] postimage
    )throws IOException{
        writer.fixed(
            248,
            postimage[0]
        );
        writer.varShort(
            53,
            postimage[1]
        );
        writer.varShort(
            53,
            postimage[2]
        );
        writer.varShort(
            53,
            postimage[3]
        );
        writer.varShort(
            126,
            postimage[4]
        );
        writer.varShort(
            126,
            postimage[5]
        );
    }

    private static void publishFirst(Trade t)throws IOException{
        publishFirstFor(t,t.a);publishFirstFor(t,t.b);
        // Exact cache default says "Waiting for other player", but production-style
        // state semantics require no waiting message before either side has accepted.
        t.a.writer.varShort(126,BootstrapPackets.widgetText126(STATUS_TEXT,""));
        t.b.writer.varShort(126,BootstrapPackets.widgetText126(STATUS_TEXT,""));
    }
    private static void publishFirstFor(Trade t,Context c)throws IOException{
        Context o=t.other(c);c.writer.fixed(248,BootstrapPackets.interfaceOverlay248(TRADE_ROOT,INVENTORY_OVERLAY));
        int[][] inv=projectedInventory(c.bank,t.offer(c));c.writer.varShort(53,BootstrapPackets.itemContainer53(INVENTORY_GRID,inv[0],inv[1]));
        int[][] own=offerUi(t.offer(c)),other=offerUi(t.offer(o));c.writer.varShort(53,BootstrapPackets.itemContainer53(OWN_OFFER,own[0],own[1]));c.writer.varShort(53,BootstrapPackets.itemContainer53(OTHER_OFFER,other[0],other[1]));
        c.writer.varShort(126,BootstrapPackets.widgetText126(PARTNER_TEXT,"Trading With: "+o.player.username()));
    }
    private static String publishOneSidedAcceptStatus(
        Trade trade,
        Context accepting,
        boolean finalStage
    )throws IOException{
        String own=
            "Waiting for other player...";
        String peer=
            "Other player has accepted.";
        Context other=
            trade.other(
                accepting
            );

        int widget=
            finalStage
                ?CONFIRM_STATUS
                :STATUS_TEXT;

        byte[] acceptingBody=
            BootstrapPackets.widgetText126(
                widget,
                own
            );
        byte[] peerBody=
            BootstrapPackets.widgetText126(
                widget,
                peer
            );

        int acceptingFramed=
            acceptingBody.length+3;
        int peerFramed=
            peerBody.length+3;

        ServerPacketWriter.AtomicPairBatch pair;

        try{
            pair=
                ServerPacketWriter.beginAtomicQueuePair(
                    accepting.writer,
                    acceptingFramed,
                    other.writer,
                    peerFramed
                );
        }catch(IOException admissionFailure){
            return (finalStage
                ?"TRADE_FINAL_ACCEPT_REJECTED_PRESENTATION_ADMISSION "
                :"TRADE_FIRST_ACCEPT_REJECTED_PRESENTATION_ADMISSION ")+
                admissionFailure.getMessage();
        }

        if(pair!=null){
            boolean committed=false;

            try{
                accepting.writer.varShort(
                    126,
                    acceptingBody
                );
                other.writer.varShort(
                    126,
                    peerBody
                );
                pair.commit();
                committed=true;
            }finally{
                if(!committed)
                    pair.abort();
            }

            return null;
        }

        try{
            accepting.writer.varShort(
                126,
                acceptingBody
            );
        }catch(IOException failure){
            retireTerminalParticipantPublicationFailure(
                trade,
                accepting,
                failure
            );
            throw failure;
        }

        try{
            other.writer.varShort(
                126,
                peerBody
            );
        }catch(IOException failure){
            retireTerminalParticipantPublicationFailure(
                trade,
                other,
                failure
            );
            throw failure;
        }

        return null;
    }

    private static void publishFirstAcceptStatus(Trade t)throws IOException{
        String sa=t.firstAcceptedA?"Waiting for other player...":(t.firstAcceptedB?"Other player has accepted.":"");
        String sb=t.firstAcceptedB?"Waiting for other player...":(t.firstAcceptedA?"Other player has accepted.":"");
        t.a.writer.varShort(126,BootstrapPackets.widgetText126(STATUS_TEXT,sa));
        t.b.writer.varShort(126,BootstrapPackets.widgetText126(STATUS_TEXT,sb));
    }
    private static void publishFinalAcceptStatus(Trade t)throws IOException{
        String sa=t.finalAcceptedA?"Waiting for other player...":(t.finalAcceptedB?"Other player has accepted.":"Are you sure you want to make this trade?");
        String sb=t.finalAcceptedB?"Waiting for other player...":(t.finalAcceptedA?"Other player has accepted.":"Are you sure you want to make this trade?");
        t.a.writer.varShort(126,BootstrapPackets.widgetText126(CONFIRM_STATUS,sa));
        t.b.writer.varShort(126,BootstrapPackets.widgetText126(CONFIRM_STATUS,sb));
    }
    private static void publishConfirm(Trade t)throws IOException{
        try{
            publishConfirmFor(
                t,
                t.a
            );
        }catch(IOException failure){
            retireTerminalParticipantPublicationFailure(
                t,
                t.a,
                failure
            );
            throw failure;
        }

        try{
            publishConfirmFor(
                t,
                t.b
            );
        }catch(IOException failure){
            retireTerminalParticipantPublicationFailure(
                t,
                t.b,
                failure
            );
            throw failure;
        }
    }

    private static void retireTerminalParticipantPublicationFailure(
        Trade trade,
        Context failed,
        IOException failure
    ){
        Context peer=
            trade.other(
                failed
            );

        trade.stage=Stage.CANCELLED;
        detach(
            state(failed.world),
            trade
        );

        /*
         * The failed participant publication was ordinary/unbatched. Its
         * exact writer is already terminal and must not receive a cleanup
         * close. Retire that runtime locally first, then close only the peer.
         */
        LocalSessionRuntimeBindings
            .retireTerminalRuntimeBundle(
                failed.player,
                failed.writer,
                true,
                failure
            );

        closeCancelledParticipant(
            peer
        );
    }
    private static void publishConfirmFor(Trade t,Context c)throws IOException{
        Context o=t.other(c);
        c.writer.fixed(97,BootstrapPackets.interface97(CONFIRM_ROOT));
        int[][] give=offerUi(t.offer(c)),recv=offerUi(t.offer(o));
        c.writer.varShort(53,BootstrapPackets.itemContainer53(GIVE_LIST,give[0],give[1]));
        c.writer.varShort(53,BootstrapPackets.itemContainer53(RECEIVE_LIST,recv[0],recv[1]));
        c.writer.varShort(126,BootstrapPackets.widgetText126(CONFIRM_STATUS,"Are you sure you want to make this trade?"));
        // 3538/3539 are the exact current 2-column item-name review grids.
        // 3557/3558 are only empty-list fallback labels; never overlay helper text.
        c.writer.varShort(126,BootstrapPackets.widgetText126(3557,give[0].length==0?"Absolutely nothing!":""));
        c.writer.varShort(126,BootstrapPackets.widgetText126(3558,recv[0].length==0?"Absolutely nothing!":""));
    }

    private static void retireTerminalStartRootPublicationFailure(
        Context failed,
        Context a,
        Context b,
        Trade priorA,
        Trade priorB,
        boolean[] entered,
        IOException failure
    ){
        Trade failedPrior=
            failed==a
                ?priorA
                :priorB;
        Context other=
            failed==a
                ?b
                :a;
        Trade otherPrior=
            failed==a
                ?priorB
                :priorA;
        boolean otherEntered=
            failed==a
                ?entered[1]
                :entered[0];

        /*
         * Retire the exact transport that failed inside packet publication.
         * If it belonged to a prior Trade, broken-writer retirement detaches
         * that Trade and closes only its distinct healthy peer.
         */
        LocalSessionRuntimeBindings
            .retireTerminalRuntimeBundle(
                failed.player,
                failed.writer,
                true,
                failure
            );

        if(!otherEntered)
            return;

        /*
         * If both participants shared the failed writer's prior Trade, its
         * terminal retirement already closed the other participant exactly
         * once. Otherwise the other entered root still needs its own reversible
         * restore/close rollback.
         */
        if(failedPrior!=null&&
           otherPrior==failedPrior)
            return;

        restoreOrCloseEnteredTradeRootAfterStartFailure(
            other,
            true,
            otherPrior,
            failure
        );
    }

    private static void restoreOrCloseEnteredTradeRootAfterStartFailure(
        Context context,
        boolean entered,
        Trade prior,
        Throwable primary
    ){
        if(!entered)
            return;

        try{
            if(prior!=null&&
               context.trade==prior&&
               tradeCurrent(prior)){
                publishCurrentStageFor(
                    prior,
                    context
                );
            }else{
                context.writer.fixed(
                    219,
                    new byte[0]
                );
            }
        }catch(IOException cleanup){
            /*
             * The original start failure remains primary, but this rollback
             * publication has now independently made the exact cleanup writer
             * terminal. Retire its remaining runtime authority without any
             * further write through that transport, then retain the cleanup
             * failure as suppressed evidence on the original failure.
             */
            LocalSessionRuntimeBindings
                .retireTerminalRuntimeBundle(
                    context.player,
                    context.writer,
                    true,
                    cleanup
                );

            primary.addSuppressed(
                cleanup
            );
        }catch(Throwable cleanup){
            primary.addSuppressed(
                cleanup
            );
        }
    }

    private static void retirePriorTradeForReplacement(
        State state,
        Trade prior,
        Context replacementA,
        Context replacementB
    ){
        if(prior==null||
           prior.stage==Stage.CANCELLED||
           prior.stage==Stage.COMMITTED)
            return;

        prior.stage=Stage.CANCELLED;
        detach(state,prior);

        closeReplacedPeer(
            prior.a,
            replacementA,
            replacementB
        );
        closeReplacedPeer(
            prior.b,
            replacementA,
            replacementB
        );
    }

    private static void closeReplacedPeer(
        Context context,
        Context replacementA,
        Context replacementB
    ){
        if(context==replacementA||
           context==replacementB)
            return;

        IOException terminalFailure=null;

        /*
         * The new replacement Trade is already committed. Fence this old
         * peer's close to the exact stored Context + World generation under
         * the PlayerRegistry monitor. Do not acquire World.lifecycleLock
         * from under TradeService; runtime registration already owns the
         * opposite World lifecycle -> TradeService order.
         */
        synchronized(context.world.players()){
            State current=
                STATES.get(
                    context.world
                );

            if(context.world.closed()||
               current==null||
               current.contexts.get(
                    context.player.id()
               )!=context||
               !context.ownerCurrent())
                return;

            try{
                context.writer.fixed(
                    219,
                    new byte[0]
                );
            }catch(IOException failure){
                terminalFailure=failure;
            }catch(Throwable ignored){
                return;
            }
        }

        if(terminalFailure==null)
            return;

        /*
         * The replacement Trade is already committed and the prior Trade is
         * already detached. Retire only this terminal old peer after releasing
         * the PlayerRegistry monitor; never disturb the new replacement Trade.
         */
        LocalSessionRuntimeBindings
            .retireTerminalRuntimeBundle(
                context.player,
                context.writer,
                true,
                terminalFailure
            );

        System.err.println(
            "[ENGINE-R4] terminal replaced Trade peer close failed; "+
            "old peer runtime retired: "+
            terminalFailure
        );
    }

    private static void publishCurrentStageFor(
        Trade trade,
        Context context
    )throws IOException{
        if(trade.stage==Stage.OFFERING){
            publishFirstFor(
                trade,
                context
            );
            context.writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    STATUS_TEXT,
                    firstAcceptStatusFor(
                        trade,
                        context
                    )
                )
            );
            if(context.pendingX!=null)
                context.writer.fixed(
                    27,
                    new byte[0]
                );
            return;
        }

        if(trade.stage==Stage.CONFIRMING){
            publishConfirmFor(
                trade,
                context
            );
            context.writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    CONFIRM_STATUS,
                    finalAcceptStatusFor(
                        trade,
                        context
                    )
                )
            );
        }
    }

    private static String firstAcceptStatusFor(
        Trade trade,
        Context context
    ){
        boolean own=
            context==trade.a
                ?trade.firstAcceptedA
                :trade.firstAcceptedB;
        boolean other=
            context==trade.a
                ?trade.firstAcceptedB
                :trade.firstAcceptedA;

        return own
            ?"Waiting for other player..."
            :other
                ?"Other player has accepted."
                :"";
    }

    private static String finalAcceptStatusFor(
        Trade trade,
        Context context
    ){
        boolean own=
            context==trade.a
                ?trade.finalAcceptedA
                :trade.finalAcceptedB;
        boolean other=
            context==trade.a
                ?trade.finalAcceptedB
                :trade.finalAcceptedA;

        return own
            ?"Waiting for other player..."
            :other
                ?"Other player has accepted."
                :"Are you sure you want to make this trade?";
    }

    private static void cancel0(State s,Context c,String reason,boolean notify){
        Trade t=c.trade;
        if(t==null)
            return;

        t.stage=Stage.CANCELLED;
        detach(s,t);

        if(!notify)
            return;

        closeCancelledParticipant(
            t.a
        );
        closeCancelledParticipant(
            t.b
        );
    }

    private static void closeCancelledParticipant(
        Context context
    ){
        IOException terminalFailure=null;

        /*
         * TradeService registration/replacement is serialized by the class
         * monitor. World generation changes are independent, so fence this
         * snapshotted Context under the World PlayerRegistry monitor before touching
         * its writer. Do not acquire World.lifecycleLock here: runtime binding
         * registration already orders World lifecycle -> TradeService, and
         * reversing that order would introduce a deadlock.
         */
        synchronized(context.world.players()){
            State current=
                STATES.get(
                    context.world
                );

            if(context.world.closed()||
               current==null||
               current.contexts.get(
                    context.player.id()
               )!=context||
               !context.ownerCurrent())
                return;

            try{
                context.writer.fixed(
                    219,
                    new byte[0]
                );
            }catch(IOException failure){
                terminalFailure=failure;
            }catch(Throwable ignored){
                /*
                 * Preserve the historical cleanup behavior for non-I/O
                 * throwables.
                 */
                return;
            }
        }

        if(terminalFailure==null)
            return;

        /*
         * Cancellation is already semantically committed. Handle terminal
         * retirement only after releasing the PlayerRegistry monitor so
         * cross-service cleanup never expands that lock's scope.
         */
        LocalSessionRuntimeBindings
            .retireTerminalRuntimeBundle(
                context.player,
                context.writer,
                true,
                terminalFailure
            );

        System.err.println(
            "[ENGINE-R4] terminal Trade cancellation close failed; "+
            "participant runtime retired: "+
            terminalFailure
        );
    }
    private static void detach(State s,Trade t){s.trades.remove(t.a.player.id());s.trades.remove(t.b.player.id());t.a.trade=null;t.b.trade=null;t.a.pendingX=t.b.pendingX=null;}

    private static int amountFor(int opcode){if(opcode==145)return 1;if(opcode==117)return 5;if(opcode==43)return 10;if(opcode==129)return Integer.MAX_VALUE;if(opcode==135)return -1;return 0;}
    private static int offerItemAt(Trade t,Context c,int slot){int[][] ui=offerUi(t.offer(c));return slot>=0&&slot<ui[0].length?ui[0][slot]:-1;}
    private static int[][] offerUi(LinkedHashMap<Integer,Integer> m){ArrayList<Integer> ids=new ArrayList<>(),qs=new ArrayList<>();for(Map.Entry<Integer,Integer> e:m.entrySet()){int item=e.getKey(),q=e.getValue();if(q<=0)continue;if(BankState.isStackable(item)){ids.add(item);qs.add(q);}else for(int i=0;i<q&&ids.size()<28;i++){ids.add(item);qs.add(1);}if(ids.size()>=28)break;}int[] a=new int[ids.size()],b=new int[qs.size()];for(int i=0;i<a.length;i++){a[i]=ids.get(i);b[i]=qs.get(i);}return new int[][]{a,b};}
    private static int[][] projectedInventory(BankState bank,LinkedHashMap<Integer,Integer> offer){int n=bank.inventoryCapacity();int[] ids=new int[n],qs=new int[n];HashMap<Integer,Integer> rem=new HashMap<>(offer);for(int i=0;i<n;i++){BankState.Stack s=bank.inventoryAt(i);if(s==null){ids[i]=-1;continue;}int q=s.qty;int r=rem.getOrDefault(s.itemId,0);int take=Math.min(q,r);q-=take;if(take>0)rem.put(s.itemId,r-take);if(q>0){ids[i]=s.itemId;qs[i]=q;}else ids[i]=-1;}return new int[][]{ids,qs};}

    private static boolean offersAvailable(Context c,LinkedHashMap<Integer,Integer> offer){for(Map.Entry<Integer,Integer> e:offer.entrySet())if(c.bank.inventoryCount(e.getKey())<e.getValue()||ItemPolicyRepository.explicitlyUntradeable(e.getKey()))return false;return true;}
    private static boolean canAfterExchange(BankState bank,LinkedHashMap<Integer,Integer> outgoing,LinkedHashMap<Integer,Integer> incoming){
        return projectedExchangeInventory(
            bank,
            outgoing,
            incoming
        )!=null;
    }

    private static int[][] projectedExchangeInventory(
        BankState bank,
        LinkedHashMap<Integer,Integer> outgoing,
        LinkedHashMap<Integer,Integer> incoming
    ){
        int n=bank.inventoryCapacity();
        int[] ids=new int[n];
        int[] qs=new int[n];

        for(int i=0;i<n;i++){
            BankState.Stack s=
                bank.inventoryAt(i);
            ids[i]=s==null?-1:s.itemId;
            qs[i]=s==null?0:s.qty;
        }

        for(Map.Entry<Integer,Integer> e:
                outgoing.entrySet())
            if(!simRemove(
                    ids,
                    qs,
                    e.getKey(),
                    e.getValue()))
                return null;

        for(Map.Entry<Integer,Integer> e:
                incoming.entrySet())
            if(!simAdd(
                    ids,
                    qs,
                    e.getKey(),
                    e.getValue()))
                return null;

        return new int[][]{
            ids,
            qs
        };
    }
    private static boolean simRemove(int[] ids,int[] qs,int item,int amount){int rem=amount;for(int i=0;i<ids.length&&rem>0;i++)if(ids[i]==item){int take=Math.min(qs[i],rem);qs[i]-=take;rem-=take;if(qs[i]==0)ids[i]=-1;}return rem==0;}
    private static boolean simAdd(int[] ids,int[] qs,int item,int amount){if(amount<=0)return true;if(BankState.isStackable(item)){for(int i=0;i<ids.length;i++)if(ids[i]==item){long x=(long)qs[i]+amount;if(x>Integer.MAX_VALUE)return false;qs[i]=(int)x;return true;}for(int i=0;i<ids.length;i++)if(ids[i]<0){ids[i]=item;qs[i]=amount;return true;}return false;}int empty=0;for(int id:ids)if(id<0)empty++;if(empty<amount)return false;for(int i=0;i<ids.length&&amount>0;i++)if(ids[i]<0){ids[i]=item;qs[i]=1;amount--;}return amount==0;}
    private static void removeOffer(BankState bank,LinkedHashMap<Integer,Integer> offer,ServerPacketWriter w)throws IOException{for(Map.Entry<Integer,Integer> e:offer.entrySet()){int item=e.getKey(),rem=e.getValue();for(int i=0;i<bank.inventoryCapacity()&&rem>0;i++){BankState.Stack s=bank.inventoryAt(i);if(s==null||s.itemId!=item)continue;if(s.qty<=rem){int q=s.qty;int got=bank.consumeInventoryAll(i,item,w);if(got<0)throw new IOException("trade remove failed item="+item+" slot="+i);rem-=q;}else{s.qty-=rem;rem=0;bank.sendNormalInventory(w);}}if(rem!=0)throw new IOException("trade remove remainder item="+item+" rem="+rem);}}
    private static void addOffer(BankState bank,LinkedHashMap<Integer,Integer> offer,ServerPacketWriter w)throws IOException{for(Map.Entry<Integer,Integer> e:offer.entrySet())if(bank.addInventoryAmount(e.getKey(),e.getValue(),w)<0)throw new IOException("trade add failed item="+e.getKey()+" qty="+e.getValue());}

    private static int removePlayerContexts(
        WorldPlayer player,
        ServerPacketWriter expectedWriter,
        String reason,
        boolean notify
    ){
        int removed=0;

        for(
            Iterator<Map.Entry<World,State>> it=
                STATES.entrySet().iterator();
            it.hasNext();
        ){
            State s=it.next().getValue();
            Context c=s.contexts.get(player.id());

            if(c==null)
                continue;

            if(expectedWriter!=null&&
               c.writer!=expectedWriter)
                continue;

            cancel0(
                s,
                c,
                reason,
                notify&&
                    !s.world.closed()&&
                    tradeCurrent(c.trade)
            );
            s.contexts.remove(player.id());
            removed++;

            if(s.contexts.isEmpty()&&s.trades.isEmpty())
                it.remove();
        }

        return removed;
    }

    private static State state(World w){State s=STATES.get(w);if(s==null){s=new State(w);STATES.put(w,s);}return s;}

    private static Context context(WorldPlayer p){
        if(p==null)return null;
        for(State s:STATES.values()){
            Context c=s.contexts.get(p.id());
            if(c!=null&&
               !c.world.closed()&&
               c.player==p&&
               c.ownerCurrent())
                return c;
        }
        return null;
    }

    private static Trade liveTrade(Context c){
        if(c==null||c.trade==null)
            return null;

        Trade t=c.trade;
        if(tradeCurrent(t))
            return t;

        cancel0(
            state(c.world),
            c,
            "STALE_OWNER",
            false
        );
        return null;
    }

    private static boolean tradeCurrent(Trade t){
        return t!=null&&
            t.a.ownerCurrent()&&
            t.b.ownerCurrent();
    }

    private static final class State{final World world;final HashMap<EntityId,Context> contexts=new HashMap<>();final HashMap<EntityId,Trade> trades=new HashMap<>();State(World w){world=w;}}

    private static final class Context{
        final World world;
        final WorldPlayer player;
        final long ownerGeneration;
        final BankState bank;
        final ServerPacketWriter writer;
        final Runnable save;
        final RootOwner rootOwner;
        Trade trade;
        PendingX pendingX;

        Context(
            World w,
            WorldPlayer p,
            long generation,
            BankState b,
            ServerPacketWriter wr,
            Runnable s,
            RootOwner owner
        ){
            world=w;
            player=p;
            ownerGeneration=generation;
            bank=b;
            writer=wr;
            save=s;
            rootOwner=owner;
        }

        boolean ownerCurrent(){
            return world.players().owns(
                player,
                ownerGeneration
            );
        }
    }
    private static final class PendingX{final XKind kind;final int item;PendingX(XKind k,int i){kind=k;item=i;}}
    private static final class Trade{final Context a,b;final LinkedHashMap<Integer,Integer> offerA=new LinkedHashMap<>(),offerB=new LinkedHashMap<>();Stage stage=Stage.OFFERING;boolean firstAcceptedA,firstAcceptedB,finalAcceptedA,finalAcceptedB;Trade(Context a,Context b){this.a=a;this.b=b;}Context other(Context c){return c==a?b:a;}LinkedHashMap<Integer,Integer> offer(Context c){return c==a?offerA:offerB;}void setFirstAccepted(Context c,boolean v){if(c==a)firstAcceptedA=v;else firstAcceptedB=v;}void setFinalAccepted(Context c,boolean v){if(c==a)finalAcceptedA=v;else finalAcceptedB=v;}}
}