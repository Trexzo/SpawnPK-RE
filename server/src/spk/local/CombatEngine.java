package spk.local;

import java.io.*;
import java.util.*;

/**
 * Combat attack-cycle/presentation coordinator.
 *
 * Recovered animation/GFX/projectile/timing authority stays separate from the
 * injected damage rule provider. Standalone constructors retain the historical
 * M2 dummy fixture; production LocalLab injects an explicitly custom fallback.
 */
final class CombatEngine {
    private final CombatState state;
    private final DevAuthorityWorkbench dev;
    private final CombatDamageRules damageRules;
    private final CombatAttackTimingRules timingRules;
    private final CombatPresentationAdapter presentation;
    private final CombatSystemHooks systemHooks;
    private long syntheticTick;
    private int lastDamageThisAction;
    private static final int DUMMY_HP_MAX=255;
    // Latest LocalLab visual A/B: user advanced from the old type-4 fixture twice
    // and identified type 6 as the red-ish max-hit presentation that matches the
    // intended SpawnPK look. Keep the older type-4 production semantic capture
    // documented separately rather than pretending this visual selection changes it.
    private int devHitType=6;
    /**
     * R2.11 exact-client semantic split: type 6 is the crit/max visual family,
     * while type 1 is the ordinary basic-damage family in the New-school renderer.
     * Auto mode is a LocalLab fixture helper only; it does not claim a SpawnPK
     * damage formula. The current dummy fixture's baseline value is treated as max.
     */
    private boolean devHitVariantAuto=true;
    private int devHitStyleIcon=255;
    private String devHitPlacement="primary";
    /** Optional LocalLab-only damage override for visual hitsplat A/B testing. */
    private Integer devHitDamage;
    /** Optional deterministic damage sequence; never claimed as SpawnPK formula authority. */
    private int[] devHitDamageSequence;
    private int devHitDamageSequenceIndex;

    // v5.12.3: the server owns interaction movement. The stock client may still
    // emit one immediate walking packet as part of the same out-of-range NPC
    // click. That packet is a transport echo, not a new manual movement intent.
    private boolean approachEchoArmed;
    private long approachEchoDeadlineMs;
    private int approachDestinationX=Integer.MIN_VALUE;
    private int approachDestinationY=Integer.MIN_VALUE;
    // R2.13 measured-facing contract: an out-of-range click does not publish a
    // click-time target. The first authoritative movement packet owns facing.
    private boolean approachFacingPending;

    static final class MovementFacingSnapshot {
        final boolean approachFacingPending;

        private MovementFacingSnapshot(
            boolean approachFacingPending
        ){
            this.approachFacingPending=
                approachFacingPending;
        }
    }

    MovementFacingSnapshot snapshotMovementFacing(){
        return new MovementFacingSnapshot(
            approachFacingPending
        );
    }

    void restoreMovementFacing(
        MovementFacingSnapshot snapshot
    ){
        if(snapshot==null)
            throw new NullPointerException(
                "movement facing snapshot"
            );
        approachFacingPending=
            snapshot.approachFacingPending;
    }

    CombatEngine(){
        this(
            new CombatState(),
            new DevAuthorityWorkbench(),
            CombatDamageRules.dummyFixture(),
            CombatAttackTimingRules.recoveredCompatibility()
        );
    }

    CombatEngine(DevAuthorityWorkbench dev){
        this(
            new CombatState(),
            dev,
            CombatDamageRules.dummyFixture(),
            CombatAttackTimingRules.recoveredCompatibility()
        );
    }

    CombatEngine(
        CombatState state,
        DevAuthorityWorkbench dev
    ){
        this(
            state,
            dev,
            CombatDamageRules.dummyFixture(),
            CombatAttackTimingRules.recoveredCompatibility()
        );
    }

    CombatEngine(
        CombatState state,
        DevAuthorityWorkbench dev,
        CombatDamageRules damageRules
    ){
        this(
            state,
            dev,
            damageRules,
            CombatAttackTimingRules.recoveredCompatibility()
        );
    }

    CombatEngine(
        CombatState state,
        DevAuthorityWorkbench dev,
        CombatDamageRules damageRules,
        CombatAttackTimingRules timingRules
    ){
        this(
            state,
            dev,
            damageRules,
            timingRules,
            new CombatPresentationAdapter()
        );
    }

    CombatEngine(
        CombatState state,
        DevAuthorityWorkbench dev,
        CombatDamageRules damageRules,
        CombatAttackTimingRules timingRules,
        CombatPresentationAdapter presentation
    ){
        this(
            state,
            dev,
            damageRules,
            timingRules,
            presentation,
            CombatSystemHooks.none()
        );
    }

    CombatEngine(
        CombatState state,
        DevAuthorityWorkbench dev,
        CombatDamageRules damageRules,
        CombatSystemHooks systemHooks
    ){
        this(
            state,
            dev,
            damageRules,
            CombatAttackTimingRules.recoveredCompatibility(),
            new CombatPresentationAdapter(),
            systemHooks
        );
    }

    CombatEngine(
        CombatState state,
        DevAuthorityWorkbench dev,
        CombatDamageRules damageRules,
        CombatAttackTimingRules timingRules,
        CombatPresentationAdapter presentation,
        CombatSystemHooks systemHooks
    ){
        this.state=java.util.Objects.requireNonNull(
            state,
            "state"
        );
        this.dev=
            dev==null
                ?new DevAuthorityWorkbench()
                :dev;
        this.damageRules=java.util.Objects.requireNonNull(
            damageRules,
            "damageRules"
        );
        this.timingRules=java.util.Objects.requireNonNull(
            timingRules,
            "timingRules"
        );
        this.presentation=java.util.Objects.requireNonNull(
            presentation,
            "presentation"
        );
        this.systemHooks=java.util.Objects.requireNonNull(
            systemHooks,
            "systemHooks"
        );
    }

    String request(NpcEntity npc,MovementState movement,int weaponId,long now){
        return request(npc,movement,weaponId,now,null);
    }

    String request(NpcEntity npc,MovementState movement,int weaponId,long now,CombatStyleRepository.Style style){
        try{return request(npc,movement,weaponId,now,style,null);}
        catch(IOException ioe){throw new IllegalStateException(ioe);}
    }

    String request(NpcEntity npc,MovementState movement,int weaponId,long now,CombatStyleRepository.Style style,ServerPacketWriter writer)throws IOException{
        CombatTargetValidator.Result validity=
            CombatTargetValidator.acquireNpc(npc);
        if(!validity.valid)
            return "REJECTED_COMBAT_TARGET_"+validity.reason+
                " detail="+validity.detail;
        CombatTargetRepository.Target target=
            CombatTargetRepository.forDefinition(npc.definitionId);
        CombatWeaponProfile weapon=CombatWeaponRepository.resolve(weaponId);
        int range=weapon!=null&&weapon.attackRange>0?weapon.attackRange:1;
        int dist=LocalSession.chebyshev(movement.x(),movement.y(),npc.x,npc.y);
        boolean deferred=!inLegalRange(movement.x(),movement.y(),npc.x,npc.y,range);
        clearApproachEcho();
        approachFacingPending=deferred;
        state.target(npc,target,now,deferred);
        return "TARGET_"+(deferred?"DEFERRED_RANGE":"ACQUIRED")+
            " scene="+npc.sceneIndex+" def="+npc.definitionId+" context="+target.context+
            " world="+npc.x+","+npc.y+" distance="+dist+" weapon="+weaponId+
            " weaponProfile="+(weapon==null?"UNRESOLVED":weapon.certainty)+
            " speedTicks="+(weapon==null?-1:weapon.attackSpeedTicks)+
            " style="+(style==null?"UNSPECIFIED":style.label+"/"+style.mode+"/value"+style.value)+
            " clickFacing=false facingAuthority="+(deferred?"FIRST_AUTHORITATIVE_MOVEMENT":"ATTACK_TICK")+
            " faceTarget="+npc.sceneIndex+
            " damageAuthority="+damageRules.authority()+
            " formula="+damageRules.formula();
    }

    Integer consumeApproachFacingTargetForMovement(){
        if(!approachFacingPending)return null;
        if(!state.active()||!state.pendingRange){approachFacingPending=false;return null;}
        approachFacingPending=false;
        return Integer.valueOf(state.targetSceneIndex);
    }

    String tick(MovementState movement,NpcRegistry npcs,EquipmentState equipment,ServerPacketWriter w)throws IOException{
        return tick(movement,npcs,equipment,w,++syntheticTick,null);
    }

    String tick(MovementState movement,NpcRegistry npcs,EquipmentState equipment,ServerPacketWriter w,long worldTick) throws IOException {
        return tick(movement,npcs,equipment,w,worldTick,null);
    }

    String tick(MovementState movement,NpcRegistry npcs,EquipmentState equipment,ServerPacketWriter w,long worldTick,CombatStyleRepository.Style style) throws IOException {
        return tick(movement,npcs,equipment,w,worldTick,style,null);
    }

    String tick(MovementState movement,NpcRegistry npcs,EquipmentState equipment,ServerPacketWriter w,long worldTick,CombatStyleRepository.Style style,SceneUpdatePublisher scene) throws IOException {
        lastDamageThisAction=0;
        if(!state.active()) return null;
        NpcEntity target=npcs.scene(state.targetSceneIndex);
        CombatTargetValidator.Result validity=
            CombatTargetValidator.activeNpc(
                target,
                state
            );
        if(!validity.valid){
            state.clear();
            approachFacingPending=false;
            clearApproachEcho();
            return "TARGET_CLEARED_"+validity.reason+
                " detail="+validity.detail;
        }

        String dueHitPublication=
            publishDueHit(
                worldTick,
                npcs,
                w
            );

        CombatWeaponProfile profile=CombatWeaponRepository.resolve(equipment.weapon());
        V913WeaponRuntimeAuthority.Profile runtime=V913WeaponRuntimeAuthority.resolve(equipment.weapon());
        boolean runtimeBasic=runtime!=null&&runtime.directBasicAttack;
        boolean mechanicsResolved=profile!=null&&profile.mechanicsResolved();
        boolean presentationOnly=!mechanicsResolved&&runtimeBasic;
        if(!mechanicsResolved&&!presentationOnly){
            if(!state.readyEmitted){
                state.readyEmitted=true;
                String blocked=
                    "ATTACK_BLOCKED_UNRESOLVED_WEAPON weapon="+
                    equipment.weapon()+
                    " targetDef="+target.definitionId;
                return dueHitPublication==null
                    ?blocked
                    :dueHitPublication+" "+blocked;
            }
            return dueHitPublication;
        }
        // Presentation-only runtime authority never invents weapon reach. Until the
        // production server range is recovered, require conservative adjacency.
        int legalRange=mechanicsResolved?profile.attackRange:1;
        int dist=LocalSession.chebyshev(movement.x(),movement.y(),target.x,target.y);
        if(!inLegalRange(movement.x(),movement.y(),target.x,target.y,legalRange)){
            state.pendingRange=true;
            return dueHitPublication;
        }
        state.pendingRange=false;
        if(state.nextAttackTick>0 && worldTick<state.nextAttackTick)
            return dueHitPublication;

        CombatAttackTimingRules.Result timing=
            timingRules.resolve(
                new CombatAttackTimingRules.Request(
                    equipment.weapon(),
                    profile,
                    runtime
                )
            );

        int attackAnimation=runtimeBasic&&runtime.attackAnimation>=0?runtime.attackAnimation:profile.attackAnimation;
        int actorGfx=runtimeBasic&&runtime.actorGfx>=0?runtime.actorGfx:((equipment.weapon()==11235)?profile.gfxId:-1);
        int targetGfx=runtimeBasic?runtime.targetGfx:-1;
        int projectileId=runtimeBasic&&runtime.projectileId>=0?runtime.projectileId:profile.projectileId;
        int attackSpeedTicks=timing.attackSpeedTicks;
        String animationAuthority=runtimeBasic?"V9.13_DIRECT_RUNTIME":"PROFILE";
        if(dev.hasCombatAnimationOverride(equipment.weapon())){
            Integer v=dev.combatAnimationOverride(equipment.weapon());
            attackAnimation=v==null?-1:v.intValue();
            animationAuthority="TEMPORARY_OVERRIDE";
        } else if(equipment.weapon()==28860 && attackAnimation==15624){
            // 15624 was only an unbound cache-name candidate and crashed the pinned
            // client when tried live. V9.13 later proved Scorching (i) uses 15409
            // with GFX 4080 / projectile 4079. Never regress to the obsolete row.
            attackAnimation=-1;
            animationAuthority="SCORCHING_OBSOLETE_15624_FAIL_CLOSED";
        }
        CombatPresentationAdapter.ActorPublication actorPublication=
            presentation.publishActor(
                attackAnimation,
                actorGfx,
                target,
                w
            );
        boolean runtimeActorGfxPublished=
            actorPublication.actorGfxPublished;
        CombatDamageRules.Result calculatedDamage=null;
        CombatSystemHooks.Snapshot hookSnapshot=null;
        int damage=0,hitType=-1,hp=DUMMY_HP_MAX;
        if(mechanicsResolved){
            hookSnapshot=
                systemHooks.beforeDamage(
                    state.context,
                    equipment.weapon(),
                    worldTick
                );

            calculatedDamage=
                damageRules.calculate(
                    new CombatDamageRules.Request(
                        state.context,
                        equipment.weapon(),
                        style,
                        worldTick
                    )
                );

            damage=nextDevDamage(
                calculatedDamage.damage
            );
            hitType=effectiveHitType(
                damage,
                calculatedDamage.maxHitReference
            );

            CombatHitScheduler.ScheduledHit scheduled=
                CombatHitScheduler.schedule(
                    state,
                    new CombatHitScheduler.ScheduledHit(
                        target.sceneIndex,
                        target.definitionId,
                        damage,
                        hitType,
                        hp,
                        DUMMY_HP_MAX,
                        targetGfx,
                        calculatedDamage.authority,
                        calculatedDamage.formula,
                        worldTick+timing.hitDelayTicks
                    )
                );

            CombatHitScheduler.ScheduledHit immediate=
                CombatHitScheduler.consumeDue(
                    state,
                    worldTick
                );

            if(immediate!=null){
                CombatPresentationAdapter.HitPublication hitPublication=
                    presentation.publishHit(
                        immediate,
                        npcs,
                        w
                    );
                if(hitPublication.published)
                    lastDamageThisAction=
                        hitPublication.damage;
                String immediatePublication=
                    hitPublication.log;
                dueHitPublication=
                    dueHitPublication==null
                        ?immediatePublication
                        :dueHitPublication+" "+immediatePublication;
            }
        } else if(targetGfx>=0){
            presentation.publishTargetGfx(
                target,
                targetGfx,
                npcs,
                w
            );
        }

        CombatPresentationAdapter.RuntimePublication runtimePresentation=
            presentation.publishRuntimeEffects(
                runtimeBasic,
                projectileId,
                runtime,
                movement,
                target,
                scene,
                w
            );
        String runtimeProjectilePublication=
            runtimePresentation.projectilePublication;
        boolean runtimeSoundPublished=
            runtimePresentation.soundPublished;
        state.lastAttackTick=worldTick;
        // Cadence and hit delay now come from the explicit timing-rule boundary.
        // Unresolved cadence still emits one presentation and requires a fresh request.
        state.nextAttackTick=
            attackSpeedTicks>0
                ?worldTick+attackSpeedTicks
                :Long.MAX_VALUE;
        state.attackCount++;
        state.readyEmitted=true;
        return (presentationOnly?"M2_PRESENTATION_ONLY_SENT":"M2_ATTACK_SENT")+" context="+state.context+" targetScene="+target.sceneIndex+" def="+target.definitionId+
            " distance="+dist+" weapon="+equipment.weapon()+" attackAnim="+(attackAnimation<0?"SUPPRESSED":attackAnimation)+" profileAttackAnim="+profile.attackAnimation+
            " animationAuthority="+animationAuthority+" faceTarget="+target.sceneIndex+
            " actorGfxCandidate="+profile.gfxId+" actorGfx="+actorGfx+" actorGfxPublished="+runtimeActorGfxPublished+" targetGfx="+targetGfx+
            " projectileCandidate="+profile.projectileId+" projectile="+projectileId+
            " projectileGeometry="+(runtimeBasic&&runtime.hasProjectileGeometry()?(runtime.projectileStartHeight+"/"+runtime.projectileEndHeight+" slope="+runtime.projectileSlope+" startDistance="+runtime.projectileStartDistance):"UNRESOLVED")+
            " projectilePublication="+runtimeProjectilePublication+
            " runtimeSound="+(runtimeBasic&&runtime.soundId>=0?runtime.soundId:"NONE")+" runtimeSoundPublished="+runtimeSoundPublished+
            " speedTicks="+attackSpeedTicks+
            " cadenceAuthority="+timing.cadenceAuthority+
            " hitDelayTicks="+timing.hitDelayTicks+
            " hitDelayAuthority="+timing.hitDelayAuthority+
            " hitDelayRule="+timing.hitDelayRule+
            " pendingHitTick="+
                (state.pendingHit==null
                    ?"NONE"
                    :Long.toString(state.pendingHit.scheduledTick))+
            " tickMs=600 nextAttackTick="+state.nextAttackTick+
            " mechanicsAuthority="+(mechanicsResolved?"RESOLVED_PRESENTATION_WITH_INJECTED_DAMAGE_RULES":"UNRESOLVED_PRESENTATION_ONLY")+" legalRange="+legalRange+
            " damage="+(mechanicsResolved?Integer.toString(damage):"NOT_APPLIED")+
            " damageAuthority="+(calculatedDamage==null?"NOT_APPLIED":calculatedDamage.authority)+
            " damageFormula="+(calculatedDamage==null?"NOT_APPLIED":calculatedDamage.formula)+
            " damageMode="+(mechanicsResolved?activeDamageMode(calculatedDamage):"NOT_APPLIED")+
            " systemHooks="+
                (mechanicsResolved
                    ?hookSnapshot
                    :"NOT_APPLIED")+
            " hpFixture="+(mechanicsResolved?(hp+"/"+DUMMY_HP_MAX):"UNCHANGED")+
            " hitsplatType="+(mechanicsResolved?Integer.toString(hitType):"NONE")+
            " hitsplatVariantMode="+(mechanicsResolved?(devHitVariantAuto?"auto(normal=1,max=6)":"manual"):"NONE")+
            " styleIcon="+(mechanicsResolved?Integer.toString(devHitStyleIcon):"NONE")+
            " placement="+(mechanicsResolved?devHitPlacement:"NONE")+
            " attackCount="+state.attackCount+
            " style="+(style==null?"UNSPECIFIED":style.label+"/"+style.mode+"/value"+style.value)+
            " styleMath="+(calculatedDamage==null?"NOT_APPLIED":calculatedDamage.formula)+
            " maxHit="+(calculatedDamage==null?"DEFERRED":Integer.toString(calculatedDamage.maxHitReference))+
            " accuracy=DEFERRED assetEvidence="+(runtimeBasic?runtime.evidence:profile.evidence);
    }

    private String publishDueHit(
        long worldTick,
        NpcRegistry npcs,
        ServerPacketWriter w
    )throws IOException{
        CombatHitScheduler.ScheduledHit due=
            CombatHitScheduler.consumeDue(
                state,
                worldTick
            );

        if(due==null)
            return null;

        CombatPresentationAdapter.HitPublication publication=
            presentation.publishHit(
                due,
                npcs,
                w
            );

        if(publication.published)
            lastDamageThisAction=
                publication.damage;

        return publication.log;
    }

    String magicFixtureHit(int sceneIndex,SpellDefinitionRepository.Spell spell,NpcRegistry npcs,ServerPacketWriter w)throws IOException{
        NpcEntity target=npcs.scene(sceneIndex);
        if(target==null)return "MAGIC_FIXTURE_REJECTED_TARGET_NOT_VISIBLE";
        CombatTargetRepository.Target context=CombatTargetRepository.forDefinition(target.definitionId);
        if(context==null)return "MAGIC_FIXTURE_ROUTED_NO_EFFECT_NOT_DUMMY def="+target.definitionId;
        int baselineDamage=context.context==CombatContext.PLAYER_PVP?100:200;
        int damage=nextDevDamage(baselineDamage);
        int hitType=effectiveHitType(damage,baselineDamage);
        int hp=DUMMY_HP_MAX;
        NpcSyncEncoder.Mask hitMask=NpcSyncEncoder.Mask.singleHit(damage,hitType,hp,DUMMY_HP_MAX);
        npcs.sendMask(target,hitMask,w);
        lastDamageThisAction=damage;
        return "MAGIC_ROUTER_FIXTURE_HIT spell="+(spell==null?"UNKNOWN":spell.name)+" damage="+damage+
            " context="+context.context+" targetScene="+sceneIndex+" hpFixture="+hp+"/"+DUMMY_HP_MAX+" formula=LOCAL_M2_FIXED_DUMMY_HIT hitsplatType="+hitType+" hitsplatVariantMode="+(devHitVariantAuto?"auto(normal=1,max=6)":"manual")+" styleIcon="+devHitStyleIcon+" placement="+devHitPlacement+
            " spellDamageFormula=NOT_RECONSTRUCTED animationGfxProjectile=NOT_BOUND";
    }

    String fixtureHit(int damage,NpcRegistry npcs,ServerPacketWriter w) throws IOException {
        if(damage<0 || damage>255) return "REJECTED_DAMAGE_RANGE expected=0..255";
        if(!state.active()) return "REJECTED_NO_SELECTED_TARGET";
        NpcEntity target=npcs.scene(state.targetSceneIndex);
        if(target==null) return "REJECTED_TARGET_NOT_VISIBLE";
        if(!CombatTargetRepository.isCombatDummy(target.definitionId)) return "REJECTED_NOT_DUMMY";
        int baselineDamage=state.context==CombatContext.PLAYER_PVP?100:200;
        int hitType=effectiveHitType(damage,baselineDamage);
        int hp=DUMMY_HP_MAX;
        NpcSyncEncoder.Mask hitMask=NpcSyncEncoder.Mask.singleHit(damage,hitType,hp,DUMMY_HP_MAX);
        npcs.sendMask(target,hitMask,w);
        lastDamageThisAction=damage;
        return "FIXTURE_HIT_SENT damage="+damage+" targetScene="+target.sceneIndex+" def="+target.definitionId+
            " hpFixture="+hp+"/"+DUMMY_HP_MAX+" hitsplatType="+hitType+" hitsplatVariantMode="+(devHitVariantAuto?"auto":"manual")+" authority=CLIENT_PACKET65_TRANSPORT_ONLY";
    }

    String devHitInfo(){
        return devHitSummary();
    }

    String devHitReset(){
        devHitType=6;
        devHitVariantAuto=true;
        devHitStyleIcon=255;
        devHitPlacement="primary";
        devHitDamage=null;
        devHitDamageSequence=null;
        devHitDamageSequenceIndex=0;
        return "DEVHIT_RESET "+devHitSummary();
    }

    String devHitDamageAuto(){
        devHitDamage=null;
        devHitDamageSequence=null;
        devHitDamageSequenceIndex=0;
        return "DEVHIT_DAMAGE "+devHitSummary();
    }

    String devHitDamage(int damage){
        if(damage<0||damage>255)
            return "DEVHIT_REJECTED damage=0..255|auto";

        devHitDamage=Integer.valueOf(damage);
        devHitDamageSequence=null;
        devHitDamageSequenceIndex=0;
        return "DEVHIT_DAMAGE "+devHitSummary();
    }

    String devHitSequenceOff(){
        devHitDamageSequence=null;
        devHitDamageSequenceIndex=0;
        devHitDamage=null;
        return "DEVHIT_SEQUENCE "+devHitSummary();
    }

    String devHitSequence(int[] sequence){
        if(sequence==null||
           sequence.length<2||
           sequence.length>16)
            return "DEVHIT_REJECTED sequence=comma-separated_2..16_values_0..255";

        int[] copy=sequence.clone();

        for(int value:copy)
            if(value<0||value>255)
                return "DEVHIT_REJECTED sequence=value_range_0..255";

        devHitDamageSequence=copy;
        devHitDamageSequenceIndex=0;
        devHitDamage=null;
        return "DEVHIT_SEQUENCE "+devHitSummary();
    }

    String devHitVariant(boolean auto){
        devHitVariantAuto=auto;
        return "DEVHIT_VARIANT "+devHitSummary();
    }

    String devHitNextType(){
        devHitVariantAuto=false;
        devHitType=(devHitType+1)&255;
        return "DEVHIT_TYPE "+devHitSummary();
    }

    String devHitPreviousType(){
        devHitVariantAuto=false;
        devHitType=(devHitType+255)&255;
        return "DEVHIT_TYPE "+devHitSummary();
    }

    String devHitType(int type){
        if(type<0||type>255)
            return "DEVHIT_REJECTED type=0..255";

        devHitType=type;
        devHitVariantAuto=false;
        return "DEVHIT_TYPE "+devHitSummary();
    }

    String devHitStyleIcon(int styleIcon){
        if(styleIcon<0||styleIcon>255)
            return "DEVHIT_REJECTED styleicon=0..255";

        devHitStyleIcon=styleIcon;
        return "DEVHIT_STYLEICON_METADATA_ONLY "+
            devHitSummary()+
            " transportNote=current_NPC_singleHit_mask_has_no_styleIcon_field";
    }

    String devHitPlacementPrimary(){
        devHitPlacement="primary";
        return "DEVHIT_PLACEMENT "+devHitSummary();
    }

    String devHitSummary(){
        return "type="+devHitType+" variantMode="+(devHitVariantAuto?"auto(normal=1,max=6)":"manual")+" styleIcon="+devHitStyleIcon+" placement="+devHitPlacement+
            " damageMode="+devDamageSummary()+
            " resetFixture=auto_normal1_max6/style255/primary+legacy_damage exactClientRenderer=type6_CRIT_MAX_FAMILY,type1_BASIC_DAMAGE priorProductionDummyCapture=type4";
    }
    private String devDamageSummary(){
        if(devHitDamageSequence!=null)return "sequence"+java.util.Arrays.toString(devHitDamageSequence)+"@"+devHitDamageSequenceIndex;
        if(devHitDamage!=null)return "fixed:"+devHitDamage;
        return "legacy_context_fixture";
    }
    private String activeDamageMode(
        CombatDamageRules.Result calculated
    ){
        if(devHitDamageSequence!=null||
           devHitDamage!=null)
            return "DEV_OVERRIDE_"+devDamageSummary();

        return calculated==null
            ?"NOT_APPLIED"
            :"RULES_"+calculated.formula;
    }

    private int nextDevDamage(int fallback){
        if(devHitDamageSequence!=null && devHitDamageSequence.length>0){
            int v=devHitDamageSequence[devHitDamageSequenceIndex%devHitDamageSequence.length];
            devHitDamageSequenceIndex=(devHitDamageSequenceIndex+1)%devHitDamageSequence.length;
            return v;
        }
        return devHitDamage==null?fallback:devHitDamage.intValue();
    }

    private int effectiveHitType(int damage,int fixtureMaxDamage){
        if(!devHitVariantAuto)return devHitType;
        // Exact current client renderer: New-school type 6 selects the crit/max
        // sprite family (indices 3/4/5); ordinary basic damage is type 1
        // (same basic family as type 0, indices 0/1/2). Whether a real SpawnPK
        // hit is max is server authority, so the LocalLab fixture only compares
        // against its own configured context baseline.
        return damage>=fixtureMaxDamage?6:1;
    }

    int consumeLastDamage(){ int v=lastDamageThisAction; lastDamageThisAction=0; return v; }

    /**
     * v5.12.3 server-owned interaction approach. The destination is the nearest
     * straight-line tile inside the equipped weapon range. MovementState expands
     * the single waypoint into legal adjacent steps and uses the player's normal
     * persistent run preference. This no longer depends on a later client path
     * packet to make the attack happen.
     */
    String beginServerOwnedApproach(NpcEntity npc,MovementState movement,int weaponId,long now){
        if(npc==null || movement==null || !state.active()) return "NO_ACTIVE_COMBAT";
        CombatWeaponProfile profile=CombatWeaponRepository.resolve(weaponId);
        int range=profile!=null&&profile.attackRange>0?profile.attackRange:1;
        int dist=LocalSession.chebyshev(movement.x(),movement.y(),npc.x,npc.y);
        if(inLegalRange(movement.x(),movement.y(),npc.x,npc.y,range)){ clearApproachEcho(); return "ALREADY_IN_RANGE distance="+dist+" range="+range; }

        // Route ownership now enters through the generic routing service. This
        // first roadmap slice intentionally selects the exact recovered HOME combat
        // compatibility policy, so path geometry/authority is unchanged.
        RouteFinder.Result routeResult=
            RouteFinder.find(
                RouteRequest.combatCompatibility(
                    movement.x(),
                    movement.y(),
                    movement.plane(),
                    npc.x,
                    npc.y,
                    range
                )
            );
        java.util.List<int[]> path=routeResult.path;
        if(path==null || path.isEmpty()){
            clearApproachEcho();
            return path==null?
                "APPROACH_REJECTED_NO_COLLISION_SAFE_ROUTE target="+npc.x+","+npc.y+" range="+range+
                    " blockedTiles="+routeResult.blockedTileCount+" blockedEdges="+routeResult.blockedEdgeCount:
                "ALREADY_IN_RANGE distance="+dist+" range="+range;
        }
        int[] xs=new int[path.size()], ys=new int[path.size()];
        for(int i=0;i<path.size();i++){xs[i]=path.get(i)[0];ys[i]=path.get(i)[1];}
        int destX=xs[xs.length-1],destY=ys[ys.length-1];
        MovementRequest route=new MovementRequest(164,false,xs,ys,new byte[0]);
        String accepted=movement.accept(route);
        if(!accepted.startsWith("ACCEPTED")){
            clearApproachEcho();
            return "APPROACH_"+accepted+" dest="+destX+","+destY+" pathSteps="+path.size();
        }
        approachDestinationX=destX; approachDestinationY=destY;
        approachEchoArmed=true;
        // Localhost transport echo is expected immediately after the click. Keep
        // the fence short so a genuinely later ground click is never swallowed.
        approachEchoDeadlineMs=now+350L;
        return "SERVER_APPROACH_ACCEPTED_COLLISION_SAFE dest="+destX+","+destY+" range="+range+" distance="+dist+
            " pathSteps="+path.size()+" blockedTiles="+routeResult.blockedTileCount+" blockedEdges="+routeResult.blockedEdgeCount+
            " queued="+movement.queued()+" echoFenceMs=350";
    }

    /**
     * Consume at most one immediate stock-client path packet generated by the
     * same out-of-range NPC click. Every later walking/minimap packet is manual
     * intent and cancels combat unconditionally.
     */
    boolean consumeImmediateApproachEcho(MovementRequest req,long now){
        if(req==null || !state.active() || !state.pendingRange || !approachEchoArmed) return false;
        if(now>approachEchoDeadlineMs){ clearApproachEcho(); return false; }
        approachEchoArmed=false;
        return true;
    }

    String approachEchoSummary(MovementRequest req){
        return "ignoredClientFinal="+(req==null?"none":req.finalX()+","+req.finalY())+
            " serverDest="+approachDestinationX+","+approachDestinationY;
    }

    /** Melee range-1 is cardinal adjacency only; diagonal touch is not legal melee range. */
    private static boolean inLegalRange(int px,int py,int tx,int ty,int range){
        int dx=Math.abs(px-tx),dy=Math.abs(py-ty);
        if(range<=1) return dx+dy<=1;
        return Math.max(dx,dy)<=range;
    }

    private static int[] nearestCardinalAdjacent(int px,int py,int tx,int ty){
        int[][] c={{tx-1,ty},{tx+1,ty},{tx,ty-1},{tx,ty+1}};
        int best=-1,bestCheb=Integer.MAX_VALUE,bestMan=Integer.MAX_VALUE;
        for(int i=0;i<c.length;i++){
            if(!MovementState.insideLoadedRegion(c[i][0],c[i][1]))continue;
            int dx=Math.abs(px-c[i][0]),dy=Math.abs(py-c[i][1]);
            int cheb=Math.max(dx,dy),man=dx+dy;
            if(cheb<bestCheb||(cheb==bestCheb&&man<bestMan)){best=i;bestCheb=cheb;bestMan=man;}
        }
        if(best<0)return new int[]{tx,ty};
        return c[best];
    }

    boolean active(){ return state.active(); }

    boolean cancelForManualMovement(){
        boolean had=state.active();
        if(had) state.clear();
        approachFacingPending=false;
        clearApproachEcho();
        return had;
    }

    private void clearApproachEcho(){
        approachEchoArmed=false; approachEchoDeadlineMs=0L;
        approachDestinationX=Integer.MIN_VALUE; approachDestinationY=Integer.MIN_VALUE;
    }

    CombatState state(){ return state; }
}
