package spk.local;

import java.io.IOException;
import java.util.function.LongSupplier;

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
    private final CombatStyleState combatStyles;
    private final LongSupplier ownerGeneration;
    private final PlayerPvpEligibilityPolicy pvpEligibility;
    private final PlayerCombatResolutionService pvpCombat;

    private EntityId activeFollow;
    private long activeFollowGeneration;
    private EntityId activeAttack;
    private long activeAttackGeneration;
    private EntityId activeTrade;
    private long activeTradeGeneration;
    private long nextAttackTick;

    static final class Snapshot {
        final EntityId activeFollow;
        final long activeFollowGeneration;
        final EntityId activeAttack;
        final long activeAttackGeneration;
        final EntityId activeTrade;
        final long activeTradeGeneration;
        final long nextAttackTick;

        private Snapshot(
            LocalPlayerInteractionHandler source
        ){
            activeFollow=source.activeFollow;
            activeFollowGeneration=
                source.activeFollowGeneration;
            activeAttack=source.activeAttack;
            activeAttackGeneration=
                source.activeAttackGeneration;
            activeTrade=source.activeTrade;
            activeTradeGeneration=
                source.activeTradeGeneration;
            nextAttackTick=source.nextAttackTick;
        }
    }

    Snapshot snapshot(){
        return new Snapshot(this);
    }

    void restore(
        Snapshot snapshot
    ){
        if(snapshot==null)
            throw new NullPointerException(
                "player interaction snapshot"
            );

        activeFollow=snapshot.activeFollow;
        activeFollowGeneration=
            snapshot.activeFollowGeneration;
        activeAttack=snapshot.activeAttack;
        activeAttackGeneration=
            snapshot.activeAttackGeneration;
        activeTrade=snapshot.activeTrade;
        activeTradeGeneration=
            snapshot.activeTradeGeneration;
        nextAttackTick=snapshot.nextAttackTick;
    }

    LocalPlayerInteractionHandler(
        World world,
        WorldPlayer owner,
        MovementState movement,
        EquipmentState equipment
    ){
        this(
            world,
            owner,
            movement,
            equipment,
            owner==null
                ?()->0L
                :owner::generation
        );
    }

    LocalPlayerInteractionHandler(
        World world,
        WorldPlayer owner,
        MovementState movement,
        EquipmentState equipment,
        LongSupplier ownerGeneration
    ){
        this(
            world,
            owner,
            movement,
            equipment,
            owner==null?null:owner.combatStyles(),
            CombatDamageRules.localLabFallback(),
            CombatAttackTimingRules.recoveredCompatibility(),
            owner==null
                ?CombatSystemHooks.none()
                :CombatSystemHooks.forPlayer(owner),
            ownerGeneration,
            PlayerPvpEligibilityPolicy.allowAllTestSeam()
        );
    }

    LocalPlayerInteractionHandler(
        World world,
        WorldPlayer owner,
        MovementState movement,
        EquipmentState equipment,
        LongSupplier ownerGeneration,
        PlayerPvpEligibilityPolicy pvpEligibility
    ){
        this(
            world,
            owner,
            movement,
            equipment,
            owner==null?null:owner.combatStyles(),
            CombatDamageRules.localLabFallback(),
            CombatAttackTimingRules.recoveredCompatibility(),
            owner==null
                ?CombatSystemHooks.none()
                :CombatSystemHooks.forPlayer(owner),
            ownerGeneration,
            pvpEligibility
        );
    }

    LocalPlayerInteractionHandler(
        World world,
        WorldPlayer owner,
        MovementState movement,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        CombatDamageRules damageRules,
        CombatAttackTimingRules timingRules,
        CombatSystemHooks systemHooks
    ){
        this(
            world,
            owner,
            movement,
            equipment,
            combatStyles,
            damageRules,
            timingRules,
            systemHooks,
            owner==null
                ?()->0L
                :owner::generation,
            PlayerPvpEligibilityPolicy.allowAllTestSeam()
        );
    }

    LocalPlayerInteractionHandler(
        World world,
        WorldPlayer owner,
        MovementState movement,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        CombatDamageRules damageRules,
        CombatAttackTimingRules timingRules,
        CombatSystemHooks systemHooks,
        LongSupplier ownerGeneration
    ){
        this(
            world,
            owner,
            movement,
            equipment,
            combatStyles,
            damageRules,
            timingRules,
            systemHooks,
            ownerGeneration,
            PlayerPvpEligibilityPolicy.allowAllTestSeam()
        );
    }

    LocalPlayerInteractionHandler(
        World world,
        WorldPlayer owner,
        MovementState movement,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        CombatDamageRules damageRules,
        CombatAttackTimingRules timingRules,
        CombatSystemHooks systemHooks,
        LongSupplier ownerGeneration,
        PlayerPvpEligibilityPolicy pvpEligibility
    ){
        this.world=java.util.Objects.requireNonNull(world,"world");
        this.owner=java.util.Objects.requireNonNull(owner,"owner");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.equipment=java.util.Objects.requireNonNull(equipment,"equipment");
        this.combatStyles=java.util.Objects.requireNonNull(
            combatStyles,
            "combatStyles"
        );
        this.ownerGeneration=
            java.util.Objects.requireNonNull(
                ownerGeneration,
                "ownerGeneration"
            );
        this.pvpEligibility=
            java.util.Objects.requireNonNull(
                pvpEligibility,
                "pvpEligibility"
            );
        this.pvpCombat=
            new PlayerCombatResolutionService(
                owner,
                java.util.Objects.requireNonNull(
                    damageRules,
                    "damageRules"
                ),
                java.util.Objects.requireNonNull(
                    timingRules,
                    "timingRules"
                ),
                java.util.Objects.requireNonNull(
                    systemHooks,
                    "systemHooks"
                ),
                world.pvpRecords()
            );
    }

    String handleResolved(
        PlayerAction action,
        WorldPlayer target,
        Player81WorldSync.Context sync
    )throws IOException{
        if(action==null||target==null)return null;

        long targetGeneration=
            target.generation();

        if((action.optionSlot==1||
            action.optionSlot==2||
            action.optionSlot==3)&&
           !world.players().owns(
                target,
                targetGeneration
            )){
            clearTargets();
            movement.clearQueuedPath();
            return "V5131_PLAYER_ACTION_REJECTED reason=TARGET_OWNERSHIP_CHANGED"+
                " expectedGeneration="+targetGeneration+
                " target="+target.id();
        }

        if(action.optionSlot==1){
            CombatTargetValidator.Result validity=
                CombatTargetValidator.player(
                    owner,
                    target,
                    sync
                );

            if(!validity.valid){
                clearAttack();
                return "V5131_PLAYER_ATTACK_REJECTED "+action+
                    " target="+target.username()+
                    " reason="+validity.reason+
                    " detail="+validity.detail;
            }

            PlayerPvpEligibilityPolicy.Result eligibility=
                pvpEligibility.evaluate(
                    owner,
                    target
                );

            if(!eligibility.eligible){
                clearAttack();
                movement.clearQueuedPath();
                return "V5131_PLAYER_ATTACK_REJECTED "+action+
                    " target="+target.username()+
                    " reason=PVP_REGION_POLICY"+
                    " detail="+eligibility.detail;
            }

            clearTrade();
            clearFollow();
            activeAttack=target.id();
            activeAttackGeneration=targetGeneration;
            nextAttackTick=0;
            movement.clearQueuedPath();

            return "V5131_PLAYER_ATTACK_REQUEST "+action+
                " target="+target.username()+
                " world="+target.movement().x()+","+target.movement().y()+
                " clickFacing=false facingAuthority=FIRST_AUTHORITATIVE_MOVEMENT"+
                " targetValidity=VALID"+
                " damageAuthority="+
                pvpCombat.damageAuthority()+
                " damageFormula="+
                pvpCombat.damageFormula();
        }

        if(action.optionSlot==2){
            clearTrade();
            clearAttack();
            activeFollow=target.id();
            activeFollowGeneration=targetGeneration;
            nextAttackTick=0;
            movement.clearQueuedPath();

            return "V5131_PLAYER_FOLLOW_REQUEST "+action+
                " target="+target.username()+
                " world="+target.movement().x()+","+target.movement().y()+
                " authority=SERVER_ROUTE";
        }

        if(action.optionSlot==3){
            clearFollow();
            clearAttack();
            movement.clearQueuedPath();
            activeTrade=target.id();
            activeTradeGeneration=targetGeneration;

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

        long expectedGeneration=
            activeAttack!=null
                ?activeAttackGeneration
                :activeFollow!=null
                    ?activeFollowGeneration
                    :activeTradeGeneration;

        WorldPlayer target=world.players().byId(id);

        if(!ownsTarget(
                target,
                expectedGeneration
            )){
            String kind=
                activeAttack!=null
                    ?"ATTACK"
                    :activeFollow!=null
                        ?"FOLLOW"
                        :"TRADE";

            clearTargets();
            movement.clearQueuedPath();

            return "[world player="+owner.id()+
                "] V5131_PLAYER_INTERACTION_CANCELLED kind="+
                kind+
                " reason=TARGET_OWNERSHIP_CHANGED"+
                " expectedGeneration="+
                expectedGeneration+
                " worldTick="+worldTick;
        }

        if(activeAttack!=null){
            CombatTargetValidator.Result validity=
                CombatTargetValidator.player(
                    owner,
                    target,
                    sync
                );

            if(!validity.valid){
                clearAttack();
                movement.clearQueuedPath();
                return "[world player="+owner.id()+
                    "] V5131_PLAYER_ATTACK_CANCELLED reason="+
                    validity.reason+
                    " detail="+validity.detail+
                    " worldTick="+worldTick;
            }

            PlayerPvpEligibilityPolicy.Result eligibility=
                pvpEligibility.evaluate(
                    owner,
                    target
                );

            if(!eligibility.eligible){
                clearAttack();
                movement.clearQueuedPath();
                return "[world player="+owner.id()+
                    "] V5131_PLAYER_ATTACK_CANCELLED reason=PVP_REGION_POLICY"+
                    " detail="+eligibility.detail+
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

        RouteRequest routeRequest=
            movement.transientRegion()
                ?RouteRequest.worldStatic(
                    movement.x(),
                    movement.y(),
                    movement.plane(),
                    target.movement().x(),
                    target.movement().y(),
                    range,
                    RouteRequest.Purpose.INTERACTION_APPROACH
                )
                :RouteRequest.interactionHomeRecovered(
                    movement.x(),
                    movement.y(),
                    movement.plane(),
                    target.movement().x(),
                    target.movement().y(),
                    range
                );

        RouteFinder.Result routeResult=
            RouteFinder.find(routeRequest);

        java.util.List<int[]> route=
            routeResult.path;

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
            " routeAuthority="+routeResult.authority+
            " interaction="+
                (activeTrade!=null?"TRADE":activeAttack!=null?"ATTACK":"FOLLOW")+
            " worldTick="+worldTick;
    }

    String afterMovement(Player81WorldSync.Context sync)throws IOException{
        if(activeTrade==null||sync==null)return null;

        WorldPlayer target=world.players().byId(activeTrade);
        if(!ownsTarget(
                target,
                activeTradeGeneration
            )){
            clearTrade();
            movement.clearQueuedPath();
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

        long expectedGeneration=
            activeAttack!=null
                ?activeAttackGeneration
                :activeFollowGeneration;

        WorldPlayer target=world.players().byId(id);
        if(!ownsTarget(
                target,
                expectedGeneration
            )){
            if(activeAttack!=null)
                clearAttack();
            else
                clearFollow();
            movement.clearQueuedPath();
            return null;
        }

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
        long targetGeneration=
            activeAttackGeneration;

        if(!ownsTarget(
                target,
                targetGeneration
            )){
            clearAttack();
            movement.clearQueuedPath();
            return "V5131_PLAYER_ATTACK_CANCELLED reason=TARGET_OWNERSHIP_CHANGED"+
                " expectedGeneration="+targetGeneration+
                " worldTick="+worldTick;
        }

        CombatTargetValidator.Result validity=
            CombatTargetValidator.player(
                owner,
                target,
                sync
            );

        if(!validity.valid){
            clearAttack();
            movement.clearQueuedPath();
            return "V5131_PLAYER_ATTACK_CANCELLED reason="+
                validity.reason+
                " detail="+validity.detail+
                " worldTick="+worldTick;
        }

        PlayerPvpEligibilityPolicy.Result eligibility=
            pvpEligibility.evaluate(
                owner,
                target
            );

        if(!eligibility.eligible){
            clearAttack();
            movement.clearQueuedPath();
            return "V5131_PLAYER_ATTACK_CANCELLED reason=PVP_REGION_POLICY"+
                " detail="+eligibility.detail+
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

        CombatStyleRepository.Style style=
            combatStyles.current(
                CombatInterfaceRepository.forWeapon(
                    equipment.weapon()
                )
            );

        PlayerCombatResolutionService.Result resolution;

        try{
            resolution=
                pvpCombat.resolveImmediateOwned(
                    world,
                    ownerGeneration.getAsLong(),
                    target,
                    targetGeneration,
                    equipment.weapon(),
                    style,
                    worldTick
                );
        }catch(
            PlayerCombatResolutionService
                .StaleAttackerOwnershipException stale
        ){
            clearAttack();
            movement.clearQueuedPath();

            return "V5131_PLAYER_ATTACK_CANCELLED reason=ATTACKER_OWNERSHIP_CHANGED"+
                " expectedGeneration="+
                stale.expectedGeneration+
                " worldTick="+worldTick;
        }catch(
            PlayerCombatResolutionService
                .StaleTargetOwnershipException stale
        ){
            clearAttack();
            movement.clearQueuedPath();

            return "V5131_PLAYER_ATTACK_CANCELLED reason=TARGET_OWNERSHIP_CHANGED"+
                " expectedGeneration="+targetGeneration+
                " worldTick="+worldTick;
        }

        boolean hpPublished=
            Player81WorldSync.sendSkillUpdate(
                world,
                target,
                PlayerState.HITPOINTS,
                target.playerState().xp(
                    PlayerState.HITPOINTS
                ),
                target.playerState().currentLevel(
                    PlayerState.HITPOINTS
                )
            );

        nextAttackTick=
            worldTick+
            resolution.nextAttackDelayTicks;

        if(resolution.lifecycle.died){
            clearAttack();
            movement.clearQueuedPath();
        }

        return "V5131_PLAYER_ATTACK_RESOLVED target="+target.username()+
            " clientTarget="+targetValue+
            " distance="+Math.max(dx,dy)+
            " range="+range+
            " weapon="+equipment.weapon()+
            " attackAnim="+(animation>=0?animation:"DEFERRED")+
            " speedTicks="+resolution.nextAttackDelayTicks+
            " cadenceAuthority="+resolution.timing.cadenceAuthority+
            " hitDelayTicks="+resolution.timing.hitDelayTicks+
            " hitDelayAuthority="+resolution.timing.hitDelayAuthority+
            " damage="+resolution.lifecycle.applied+
            " damageAuthority="+resolution.damage.authority+
            " damageFormula="+resolution.damage.formula+
            " hp="+resolution.lifecycle.hpBefore+
            "->"+resolution.lifecycle.hpAfter+
            " targetDied="+resolution.lifecycle.died+
            " targetHpPacket134="+hpPublished+
            " systemHooks="+resolution.hooks+
            " remoteMaskRelay=true nextAttackTick="+
            (activeAttack==null?"CLEARED_ON_DEATH":Long.toString(nextAttackTick));
    }

    Cancellation cancelActive(){
        boolean hadFacing=activeFollow!=null||activeAttack!=null;
        boolean hadTrade=activeTrade!=null;
        clearTargets();
        return new Cancellation(hadFacing,hadTrade);
    }

    void clearTargets(){
        clearFollow();
        clearAttack();
        clearTrade();
    }

    private void clearFollow(){
        activeFollow=null;
        activeFollowGeneration=0L;
    }

    private void clearAttack(){
        activeAttack=null;
        activeAttackGeneration=0L;
        nextAttackTick=0L;
    }

    private void clearTrade(){
        activeTrade=null;
        activeTradeGeneration=0L;
    }

    private boolean ownsTarget(
        WorldPlayer target,
        long expectedGeneration
    ){
        return target!=null&&
            expectedGeneration>0L&&
            world.players().owns(
                target,
                expectedGeneration
            );
    }

    boolean hasActive(){
        return activeFollow!=null||activeAttack!=null||activeTrade!=null;
    }

    EntityId activeFollow(){return activeFollow;}
    long activeFollowGeneration(){return activeFollowGeneration;}
    EntityId activeAttack(){return activeAttack;}
    long activeAttackGeneration(){return activeAttackGeneration;}
    EntityId activeTrade(){return activeTrade;}
    long activeTradeGeneration(){return activeTradeGeneration;}
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

        if(!ownsTarget(
                target,
                activeTradeGeneration
            )){
            long staleGeneration=
                activeTradeGeneration;
            clearTrade();
            movement.clearQueuedPath();
            return "V5141_PLAYER_TRADE_CANCELLED reason=TARGET_OWNERSHIP_CHANGED"+
                " expectedGeneration="+staleGeneration;
        }

        int dx=Math.abs(target.movement().x()-movement.x());
        int dy=Math.abs(target.movement().y()-movement.y());

        if(dx+dy!=1)return null;

        clearTrade();
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
