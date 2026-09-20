package spk.local;

import java.io.IOException;

/**
 * Active player Follow / Attack / Trade coordinator.
 *
 * Client-index resolution remains per-view in LocalSession/Player81WorldSync.
 * This class owns only already-resolved authoritative player interaction state.
 */
final class LocalPlayerInteractionHandler {
    private final World world;
    private final WorldPlayer owner;
    private final MovementState movement;
    private final EquipmentState equipment;

    private EntityId activeFollow;
    private EntityId activeAttack;
    private EntityId activeTrade;
    private long nextAttackTick;

    LocalPlayerInteractionHandler(
        World world,
        WorldPlayer owner,
        MovementState movement,
        EquipmentState equipment
    ){
        this.world=java.util.Objects.requireNonNull(world,"world");
        this.owner=java.util.Objects.requireNonNull(owner,"owner");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.equipment=java.util.Objects.requireNonNull(equipment,"equipment");
    }

    String handleResolved(
        PlayerAction action,
        WorldPlayer target,
        Player81WorldSync.Context sync
    )throws IOException{
        if(action==null||target==null)return null;

        if(action.optionSlot==1){
            CombatTargetValidator.Result validity=
                CombatTargetValidator.player(
                    owner,
                    target,
                    sync
                );

            if(!validity.valid){
                activeAttack=null;
                nextAttackTick=0;
                return "V5131_PLAYER_ATTACK_REJECTED "+action+
                    " target="+target.username()+
                    " reason="+validity.reason+
                    " detail="+validity.detail;
            }

            activeTrade=null;
            activeFollow=null;
            activeAttack=target.id();
            nextAttackTick=0;
            movement.clearQueuedPath();

            return "V5131_PLAYER_ATTACK_REQUEST "+action+
                " target="+target.username()+
                " world="+target.movement().x()+","+target.movement().y()+
                " clickFacing=false facingAuthority=FIRST_AUTHORITATIVE_MOVEMENT"+
                " targetValidity=VALID"+
                " damage=DEFERRED_SERVER_FORMULA_AUTHORITY";
        }

        if(action.optionSlot==2){
            activeTrade=null;
            activeAttack=null;
            activeFollow=target.id();
            nextAttackTick=0;
            movement.clearQueuedPath();

            return "V5131_PLAYER_FOLLOW_REQUEST "+action+
                " target="+target.username()+
                " world="+target.movement().x()+","+target.movement().y()+
                " authority=SERVER_ROUTE";
        }

        if(action.optionSlot==3){
            activeFollow=null;
            activeAttack=null;
            nextAttackTick=0;
            movement.clearQueuedPath();
            activeTrade=target.id();

            int dx=Math.abs(target.movement().x()-movement.x());
            int dy=Math.abs(target.movement().y()-movement.y());

            if(dx+dy==1){
                return dispatchTrade(target,sync,"ALREADY_ADJACENT");
            }

            return "V5141_PLAYER_TRADE_APPROACH "+action+
                " source="+owner.username()+
                " target="+target.username()+
                " distanceChebyshev="+Math.max(dx,dy)+
                " distanceManhattan="+(dx+dy)+
                " action=DEFERRED_UNTIL_CARDINAL_ADJACENT authority=SERVER_ROUTE";
        }

        return "V5131_PLAYER_ACTION "+action+
            " result=DECODED_HIDDEN_OPTION_FAIL_CLOSED";
    }

    /**
     * Re-plan the active interaction against the target's current authoritative
     * world tile. Returns a complete log line when this tick produces one.
     */
    String prepareTick(long worldTick,Player81WorldSync.Context sync)throws IOException{
        EntityId id=
            activeAttack!=null?activeAttack:
            activeFollow!=null?activeFollow:
            activeTrade;

        if(id==null||sync==null)return null;

        WorldPlayer target=world.players().byId(id);

        if(activeAttack!=null){
            CombatTargetValidator.Result validity=
                CombatTargetValidator.player(
                    owner,
                    target,
                    sync
                );

            if(!validity.valid){
                activeAttack=null;
                nextAttackTick=0;
                movement.clearQueuedPath();
                return "[world player="+owner.id()+
                    "] V5131_PLAYER_ATTACK_CANCELLED reason="+
                    validity.reason+
                    " detail="+validity.detail+
                    " worldTick="+worldTick;
            }
        }else if(target==null||
                 !target.registered()||
                 target.movement().plane()!=movement.plane()||
                 sync.clientIndexFor(target)<0){
            clearTargets();
            movement.clearQueuedPath();
            return null;
        }

        int range=activeAttack!=null?playerAttackRange():1;
        int dx=Math.abs(target.movement().x()-movement.x());
        int dy=Math.abs(target.movement().y()-movement.y());
        int dist=Math.max(dx,dy);
        boolean inRange=
            range==1
                ?dx+dy==1
                :dist<=range&&!(dx==0&&dy==0);

        if(inRange){
            movement.clearQueuedPath();
            if(activeTrade!=null){
                String dispatched=
                    dispatchTrade(target,sync,"ARRIVED_ADJACENT_PRE_TICK");
                return dispatched==null?null:
                    "[world player="+owner.id()+"] "+dispatched;
            }
            return null;
        }

        java.util.List<int[]> route=
            HomeCombatPathfinder.route(
                movement.x(),
                movement.y(),
                target.movement().x(),
                target.movement().y(),
                range
            );

        if(route==null||route.isEmpty())return null;

        int n=Math.min(route.size(),MovementState.MAX_QUEUED_STEPS);
        int[] xs=new int[n];
        int[] ys=new int[n];

        for(int i=0;i<n;i++){
            xs[i]=route.get(i)[0];
            ys[i]=route.get(i)[1];
        }

        movement.clearQueuedPath();

        String result=movement.accept(
            new MovementRequest(
                164,
                movement.persistentRun(),
                xs,
                ys,
                new byte[0]
            )
        );

        if(result.startsWith("ACCEPTED"))return null;

        return "[world player="+owner.id()+"] V5141_PLAYER_ROUTE result="+result+
            " target="+target.username()+
            " range="+range+
            " steps="+n+
            " interaction="+
                (activeTrade!=null?"TRADE":activeAttack!=null?"ATTACK":"FOLLOW")+
            " worldTick="+worldTick;
    }

    String afterMovement(Player81WorldSync.Context sync)throws IOException{
        if(activeTrade==null||sync==null)return null;

        WorldPlayer target=world.players().byId(activeTrade);
        if(target==null||!target.registered()){
            activeTrade=null;
            return null;
        }

        return dispatchTrade(
            target,
            sync,
            "ARRIVED_ADJACENT_AFTER_MOVEMENT"
        );
    }

    Integer movementInteractionTarget(Player81WorldSync.Context sync){
        if(sync==null)return null;

        EntityId id=activeAttack!=null?activeAttack:activeFollow;
        if(id==null)return null;

        WorldPlayer target=world.players().byId(id);
        if(target==null||!target.registered())return null;

        int value=sync.interactionTargetFor(target);
        return value<0?null:Integer.valueOf(value);
    }

    String tickAttack(
        long worldTick,
        ServerPacketWriter serverPackets,
        Player81WorldSync.Context sync
    )throws IOException{
        if(activeAttack==null||sync==null)return null;

        WorldPlayer target=world.players().byId(activeAttack);
        CombatTargetValidator.Result validity=
            CombatTargetValidator.player(
                owner,
                target,
                sync
            );

        if(!validity.valid){
            activeAttack=null;
            nextAttackTick=0;
            movement.clearQueuedPath();
            return "V5131_PLAYER_ATTACK_CANCELLED reason="+
                validity.reason+
                " detail="+validity.detail+
                " worldTick="+worldTick;
        }

        int targetValue=sync.interactionTargetFor(target);

        int range=playerAttackRange();
        int dx=Math.abs(target.movement().x()-movement.x());
        int dy=Math.abs(target.movement().y()-movement.y());

        boolean inRange=
            range==1
                ?dx+dy==1
                :Math.max(dx,dy)<=range&&!(dx==0&&dy==0);

        if(!inRange||worldTick<nextAttackTick)return null;

        CombatWeaponProfile profile=
            CombatWeaponRepository.resolve(equipment.weapon());

        int speed=
            profile==null||profile.attackSpeedTicks<=0
                ?4
                :profile.attackSpeedTicks;

        int animation=
            profile==null?-1:profile.attackAnimation;

        if(animation>=0){
            serverPackets.varShort(
                81,
                CombatSync.player81AnimationAndInteraction(
                    animation,
                    targetValue
                )
            );
        }else{
            serverPackets.varShort(
                81,
                CombatSync.player81InteractionOnly(targetValue)
            );
        }

        nextAttackTick=worldTick+Math.max(1,speed);

        return "V5131_PLAYER_ATTACK_PRESENTATION target="+target.username()+
            " clientTarget="+targetValue+
            " distance="+Math.max(dx,dy)+
            " range="+range+
            " weapon="+equipment.weapon()+
            " attackAnim="+(animation>=0?animation:"DEFERRED")+
            " speedTicks="+speed+
            " damage=DEFERRED_FORMULA_AUTHORITY"+
            " remoteMaskRelay=true nextAttackTick="+nextAttackTick;
    }

    Cancellation cancelActive(){
        boolean hadFacing=activeFollow!=null||activeAttack!=null;
        boolean hadTrade=activeTrade!=null;
        clearTargets();
        return new Cancellation(hadFacing,hadTrade);
    }

    void clearTargets(){
        activeFollow=null;
        activeAttack=null;
        activeTrade=null;
        nextAttackTick=0;
    }

    boolean hasActive(){
        return activeFollow!=null||activeAttack!=null||activeTrade!=null;
    }

    EntityId activeFollow(){return activeFollow;}
    EntityId activeAttack(){return activeAttack;}
    EntityId activeTrade(){return activeTrade;}
    long nextAttackTick(){return nextAttackTick;}

    private String dispatchTrade(
        WorldPlayer target,
        Player81WorldSync.Context sync,
        String reason
    )throws IOException{
        if(activeTrade==null||
           target==null||
           !activeTrade.equals(target.id())||
           sync==null){
            return null;
        }

        int dx=Math.abs(target.movement().x()-movement.x());
        int dy=Math.abs(target.movement().y()-movement.y());

        if(dx+dy!=1)return null;

        activeTrade=null;
        movement.clearQueuedPath();

        String result=
            sync.requestTrade(target,System.currentTimeMillis());

        if(result.startsWith("TRADE_MUTUAL_ACCEPTED")){
            String ui=TradeService.start(world,owner,target);
            result=result+" "+ui;
        }

        return "V5141_PLAYER_TRADE_DISPATCH source="+owner.username()+
            " target="+target.username()+
            " reason="+reason+
            " adjacency=CARDINAL_1 result="+result;
    }

    private int playerAttackRange(){
        CombatWeaponProfile profile=
            CombatWeaponRepository.resolve(equipment.weapon());

        if(profile==null||profile.attackRange<=0)return 1;
        return Math.max(1,Math.min(10,profile.attackRange));
    }

    static final class Cancellation {
        final boolean hadFacingInteraction;
        final boolean hadTrade;

        Cancellation(boolean hadFacingInteraction,boolean hadTrade){
            this.hadFacingInteraction=hadFacingInteraction;
            this.hadTrade=hadTrade;
        }

        boolean hadAnything(){
            return hadFacingInteraction||hadTrade;
        }
    }
}
