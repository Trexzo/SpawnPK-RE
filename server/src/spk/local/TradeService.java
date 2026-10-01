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
        Objects.requireNonNull(world,"world");
        Objects.requireNonNull(player,"player");
        Objects.requireNonNull(bank,"bank");
        Objects.requireNonNull(writer,"writer");

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
                save
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

    static synchronized String start(World world,WorldPlayer a,WorldPlayer b)throws IOException{
        State s=STATES.get(world);
        if(s==null)return "TRADE_UI_REJECTED_CONTEXT_MISSING";
        Context ca=s.contexts.get(a.id()),cb=s.contexts.get(b.id());
        if(ca==null||cb==null)return "TRADE_UI_REJECTED_CONTEXT_MISSING";
        if(ca.player!=a||cb.player!=b||
           !ca.ownerCurrent()||!cb.ownerCurrent())
            return "TRADE_UI_REJECTED_STALE_CONTEXT";
        cancel0(s,ca,"REPLACED_BY_NEW_TRADE",tradeCurrent(ca.trade));
        cancel0(s,cb,"REPLACED_BY_NEW_TRADE",tradeCurrent(cb.trade));
        Trade t=new Trade(ca,cb);s.trades.put(a.id(),t);s.trades.put(b.id(),t);ca.trade=t;cb.trade=t;
        publishFirst(t);
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
            if(amount<0){c.pendingX=new PendingX(XKind.OFFER,a.itemId);c.writer.fixed(27,new byte[0]);return "TRADE_OFFER_X_PROMPT item="+a.itemId;}
            int available=Math.max(0,c.bank.inventoryCount(a.itemId)-t.offer(c).getOrDefault(a.itemId,0));
            int add=amount==Integer.MAX_VALUE?available:Math.min(amount,available);if(add<=0)return "TRADE_OFFER_REJECTED_NO_AVAILABLE item="+a.itemId;
            changeOffer(t,c,a.itemId,add);return "TRADE_OFFER_OK item="+a.itemId+" qty="+add+" explicitTradeable="+ItemPolicyRepository.explicitTradeable(a.itemId);
        }else{
            int item=offerItemAt(t,c,a.slot);if(item<0||item!=a.itemId)return "TRADE_REMOVE_REJECTED_SLOT_MISMATCH slot="+a.slot+" item="+a.itemId+" resolved="+item;
            if(amount<0){c.pendingX=new PendingX(XKind.REMOVE,item);c.writer.fixed(27,new byte[0]);return "TRADE_REMOVE_X_PROMPT item="+item;}
            int have=t.offer(c).getOrDefault(item,0);int rem=amount==Integer.MAX_VALUE?have:Math.min(amount,have);if(rem<=0)return "TRADE_REMOVE_REJECTED_EMPTY item="+item;
            changeOffer(t,c,item,-rem);return "TRADE_REMOVE_OK item="+item+" qty="+rem;
        }
    }

    static synchronized String handleAmount(WorldPlayer player,int amount)throws IOException{
        Context c=context(player);Trade t=liveTrade(c);if(t==null||c.pendingX==null)return null;PendingX p=c.pendingX;c.pendingX=null;
        if(amount<=0)return "TRADE_X_REJECTED_AMOUNT amount="+amount;
        if(t.stage!=Stage.OFFERING)return "TRADE_X_REJECTED_STAGE_"+t.stage;
        if(p.kind==XKind.OFFER){
            if(ItemPolicyRepository.explicitlyUntradeable(p.item))return "TRADE_X_REJECTED_EXPLICIT_UNTRADEABLE item="+p.item;
            int available=Math.max(0,c.bank.inventoryCount(p.item)-t.offer(c).getOrDefault(p.item,0));int add=Math.min(amount,available);if(add<=0)return "TRADE_X_REJECTED_NO_AVAILABLE";changeOffer(t,c,p.item,add);return "TRADE_OFFER_X_OK item="+p.item+" qty="+add;
        }
        int have=t.offer(c).getOrDefault(p.item,0);int rem=Math.min(amount,have);if(rem<=0)return "TRADE_X_REMOVE_EMPTY";changeOffer(t,c,p.item,-rem);return "TRADE_REMOVE_X_OK item="+p.item+" qty="+rem;
    }

    static synchronized String handleWidget(WorldPlayer player,int widget)throws IOException{
        Context c=context(player);Trade t=liveTrade(c);if(t==null)return null;
        if(widget==FIRST_DECLINE || widget==FINAL_DECLINE){
            cancel0(state(c.world),c,widget==FIRST_DECLINE?"FIRST_STAGE_DECLINE":"FINAL_STAGE_DECLINE",true);
            return "TRADE_CANCELLED_DECLINE widget="+widget;
        }
        if(widget==FIRST_ACCEPT){
            if(t.stage!=Stage.OFFERING)return "TRADE_FIRST_ACCEPT_REJECTED_STAGE_"+t.stage;
            t.setFirstAccepted(c,true);
            if(t.firstAcceptedA&&t.firstAcceptedB){
                t.stage=Stage.CONFIRMING;t.a.pendingX=t.b.pendingX=null;publishConfirm(t);
                return "TRADE_FIRST_ACCEPT_BOTH_CONFIRM_OPEN root=3443";
            }
            publishFirstAcceptStatus(t);
            return "TRADE_FIRST_ACCEPT_WAITING_OTHER";
        }
        if(widget==FINAL_ACCEPT){
            if(t.stage!=Stage.CONFIRMING)return "TRADE_FINAL_ACCEPT_REJECTED_STAGE_"+t.stage;
            t.setFinalAccepted(c,true);
            if(t.finalAcceptedA&&t.finalAcceptedB)return commit(t);
            publishFinalAcceptStatus(t);
            return "TRADE_FINAL_ACCEPT_WAITING_OTHER";
        }
        return null;
    }

    static synchronized boolean cancelIfActive(WorldPlayer player,String reason)throws IOException{
        Context c=context(player);Trade t=liveTrade(c);if(t==null)return false;cancel0(state(c.world),c,reason,true);return true;
    }
    static synchronized boolean active(WorldPlayer p){
        Context c=context(p);
        return liveTrade(c)!=null;
    }

    private static String commit(Trade t)throws IOException{
        Context a=t.a,b=t.b;
        Object first=a.player.id().value<b.player.id().value?a.player.mutationLock():b.player.mutationLock();
        Object second=first==a.player.mutationLock()?b.player.mutationLock():a.player.mutationLock();
        synchronized(first){synchronized(second){
            if(!a.ownerCurrent()||!b.ownerCurrent()){
                cancel0(
                    state(a.world),
                    a,
                    "FINAL_VALIDATION_STALE_OWNER",
                    false
                );
                return "TRADE_COMMIT_REJECTED_STALE_OWNER";
            }
            if(!offersAvailable(a,t.offerA)||!offersAvailable(b,t.offerB)){cancel0(state(a.world),a,"FINAL_VALIDATION_MISSING_OFFER",true);return "TRADE_COMMIT_REJECTED_OFFER_CHANGED";}
            if(!canAfterExchange(a.bank,t.offerA,t.offerB)||!canAfterExchange(b.bank,t.offerB,t.offerA)){cancel0(state(a.world),a,"FINAL_VALIDATION_INVENTORY_SPACE",true);return "TRADE_COMMIT_REJECTED_INVENTORY_SPACE";}
            a.writer.beginBatch();b.writer.beginBatch();boolean endedA=false,endedB=false;
            try{
                removeOffer(a.bank,t.offerA,a.writer);removeOffer(b.bank,t.offerB,b.writer);
                addOffer(a.bank,t.offerB,a.writer);addOffer(b.bank,t.offerA,b.writer);
                a.bank.sendNormalInventory(a.writer);b.bank.sendNormalInventory(b.writer);
                a.writer.fixed(219,new byte[0]);b.writer.fixed(219,new byte[0]);
                a.writer.endBatch();endedA=true;b.writer.endBatch();endedB=true;
            }finally{if(!endedA)try{a.writer.endBatch();}catch(Throwable ignored){}if(!endedB)try{b.writer.endBatch();}catch(Throwable ignored){}}
            t.stage=Stage.COMMITTED;detach(state(a.world),t);if(a.save!=null)a.save.run();if(b.save!=null)b.save.run();
            return "TRADE_COMMITTED a="+a.player.username()+" gives="+t.offerA+" b="+b.player.username()+" gives="+t.offerB;
        }}
    }

    private static void changeOffer(Trade t,Context c,int item,int delta)throws IOException{
        LinkedHashMap<Integer,Integer> m=t.offer(c);int q=m.getOrDefault(item,0)+delta;if(q<=0)m.remove(item);else m.put(item,q);
        t.firstAcceptedA=t.firstAcceptedB=t.finalAcceptedA=t.finalAcceptedB=false;c.pendingX=null;t.other(c).pendingX=null;publishFirst(t);
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
    private static void publishConfirm(Trade t)throws IOException{publishConfirmFor(t,t.a);publishConfirmFor(t,t.b);}
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

    private static void cancel0(State s,Context c,String reason,boolean notify){
        Trade t=c.trade;if(t==null)return;t.stage=Stage.CANCELLED;detach(s,t);if(notify){try{t.a.writer.fixed(219,new byte[0]);}catch(Throwable ignored){}try{t.b.writer.fixed(219,new byte[0]);}catch(Throwable ignored){}}
    }
    private static void detach(State s,Trade t){s.trades.remove(t.a.player.id());s.trades.remove(t.b.player.id());t.a.trade=null;t.b.trade=null;t.a.pendingX=t.b.pendingX=null;}

    private static int amountFor(int opcode){if(opcode==145)return 1;if(opcode==117)return 5;if(opcode==43)return 10;if(opcode==129)return Integer.MAX_VALUE;if(opcode==135)return -1;return 0;}
    private static int offerItemAt(Trade t,Context c,int slot){int[][] ui=offerUi(t.offer(c));return slot>=0&&slot<ui[0].length?ui[0][slot]:-1;}
    private static int[][] offerUi(LinkedHashMap<Integer,Integer> m){ArrayList<Integer> ids=new ArrayList<>(),qs=new ArrayList<>();for(Map.Entry<Integer,Integer> e:m.entrySet()){int item=e.getKey(),q=e.getValue();if(q<=0)continue;if(BankState.isStackable(item)){ids.add(item);qs.add(q);}else for(int i=0;i<q&&ids.size()<28;i++){ids.add(item);qs.add(1);}if(ids.size()>=28)break;}int[] a=new int[ids.size()],b=new int[qs.size()];for(int i=0;i<a.length;i++){a[i]=ids.get(i);b[i]=qs.get(i);}return new int[][]{a,b};}
    private static int[][] projectedInventory(BankState bank,LinkedHashMap<Integer,Integer> offer){int n=bank.inventoryCapacity();int[] ids=new int[n],qs=new int[n];HashMap<Integer,Integer> rem=new HashMap<>(offer);for(int i=0;i<n;i++){BankState.Stack s=bank.inventoryAt(i);if(s==null){ids[i]=-1;continue;}int q=s.qty;int r=rem.getOrDefault(s.itemId,0);int take=Math.min(q,r);q-=take;if(take>0)rem.put(s.itemId,r-take);if(q>0){ids[i]=s.itemId;qs[i]=q;}else ids[i]=-1;}return new int[][]{ids,qs};}

    private static boolean offersAvailable(Context c,LinkedHashMap<Integer,Integer> offer){for(Map.Entry<Integer,Integer> e:offer.entrySet())if(c.bank.inventoryCount(e.getKey())<e.getValue()||ItemPolicyRepository.explicitlyUntradeable(e.getKey()))return false;return true;}
    private static boolean canAfterExchange(BankState bank,LinkedHashMap<Integer,Integer> outgoing,LinkedHashMap<Integer,Integer> incoming){
        int n=bank.inventoryCapacity();int[] ids=new int[n],qs=new int[n];for(int i=0;i<n;i++){BankState.Stack s=bank.inventoryAt(i);ids[i]=s==null?-1:s.itemId;qs[i]=s==null?0:s.qty;}
        for(Map.Entry<Integer,Integer> e:outgoing.entrySet())if(!simRemove(ids,qs,e.getKey(),e.getValue()))return false;
        for(Map.Entry<Integer,Integer> e:incoming.entrySet())if(!simAdd(ids,qs,e.getKey(),e.getValue()))return false;return true;
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
                notify&&tradeCurrent(c.trade)
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
        Trade trade;
        PendingX pendingX;

        Context(
            World w,
            WorldPlayer p,
            long generation,
            BankState b,
            ServerPacketWriter wr,
            Runnable s
        ){
            world=w;
            player=p;
            ownerGeneration=generation;
            bank=b;
            writer=wr;
            save=s;
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