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
    private final CombatHitScheduler hitScheduler;
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

    CombatEngine(){
        this(
            new CombatState(),
            new DevAuthorityWorkbench(),
            CombatDamageRules.dummyFixture()
        );
    }

    CombatEngine(DevAuthorityWorkbench dev){
        this(
            new CombatState(),
            dev,
            CombatDamageRules.dummyFixture()
        );
    }

    CombatEngine(
        CombatState state,
        DevAuthorityWorkbench dev
    ){
        this(
            state,
            dev,
            CombatDamageRules.dummyFixture()
        );
    }

    CombatEngine(
        CombatState state,
        DevAuthorityWorkbench dev,
        CombatDamageRules damageRules
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
        this.hitScheduler=
            new CombatHitScheduler(this.state);
    }

    String request(NpcEntity npc,MovementState movement,int weaponId,long now){
        return request(npc,movement,weaponId,now,null);
    }

    String request(NpcEntity npc,MovementState movement,int weaponId,long now,CombatStyleRepository.Style style){
        try{return request(npc,movement,weaponId,now,style,null);}
        catch(IOException ioe){throw new IllegalStateException(ioe);}
    }

    String request(NpcEntity npc,MovementState movement,int weaponId,long now,CombatStyleRepository.Style style,ServerPacketWriter writer)throws IOException{
        CombatTargetRepository.Target target=CombatTargetRepository.forDefinition(npc.definitionId);
        if(target==null) return "REJECTED_NOT_COMBAT_TARGET scene="+npc.sceneIndex+" def="+npc.definitionId;
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

        int dueAtTickStart=
            publishDueHits(
                worldTick,
                npcs,
                w
            );

        if(!state.active())
            return dueAtTickStart>0
                ?"PENDING_HIT_PUBLISHED count="+dueAtTickStart+
                    " hitDelayAuthority="+
                    CombatHitScheduler.DELAY_AUTHORITY
                :null;

        NpcEntity target=npcs.scene(state.targetSceneIndex);
        if(target==null || target.definitionId!=state.targetDefinitionId){ state.clear(); approachFacingPending=false; return "TARGET_CLEARED_NOT_VISIBLE"; }

        CombatWeaponProfile profile=CombatWeaponRepository.resolve(equipment.weapon());
        V913WeaponRuntimeAuthority.Profile runtime=V913WeaponRuntimeAuthority.resolve(equipment.weapon());
        boolean runtimeBasic=runtime!=null&&runtime.directBasicAttack;
        boolean mechanicsResolved=profile!=null&&profile.mechanicsResolved();
        boolean presentationOnly=!mechanicsResolved&&runtimeBasic;
        if(!mechanicsResolved&&!presentationOnly){
            if(!state.readyEmitted){ state.readyEmitted=true; return "ATTACK_BLOCKED_UNRESOLVED_WEAPON weapon="+equipment.weapon()+" targetDef="+target.definitionId; }
            return null;
        }
        // Presentation-only runtime authority never invents weapon reach. Until the
        // production server range is recovered, require conservative adjacency.
        int legalRange=mechanicsResolved?profile.attackRange:1;
        int dist=LocalSession.chebyshev(movement.x(),movement.y(),target.x,target.y);
        if(!inLegalRange(movement.x(),movement.y(),target.x,target.y,legalRange)){ state.pendingRange=true; return null; }
        state.pendingRange=false;
        if(state.nextAttackTick>0 && worldTick<state.nextAttackTick) return null;

        int attackAnimation=runtimeBasic&&runtime.attackAnimation>=0?runtime.attackAnimation:profile.attackAnimation;
        int actorGfx=runtimeBasic&&runtime.actorGfx>=0?runtime.actorGfx:((equipment.weapon()==11235)?profile.gfxId:-1);
        int targetGfx=runtimeBasic?runtime.targetGfx:-1;
        int projectileId=runtimeBasic&&runtime.projectileId>=0?runtime.projectileId:profile.projectileId;
        int attackSpeedTicks=runtimeBasic&&runtime.speedTicks>0?runtime.speedTicks:profile.attackSpeedTicks;
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
        boolean runtimeActorGfxPublished=false;
        if(attackAnimation>=0){
            if(actorGfx>=0){
                w.varShort(81,CombatSync.player81AnimationGfxAndInteraction(attackAnimation,actorGfx,0,0,target.sceneIndex));
                runtimeActorGfxPublished=true;
            } else {
                w.varShort(81,CombatSync.player81AnimationAndInteraction(attackAnimation,target.sceneIndex));
            }
        } else {
            w.varShort(81,CombatSync.player81InteractionOnly(target.sceneIndex));
        }
        CombatDamageRules.Result calculatedDamage=null;
        int damage=0,hitType=-1,hp=DUMMY_HP_MAX;
        if(mechanicsResolved){
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

            hitScheduler.scheduleCompatibility(
                target,
                damage,
                hitType,
                hp,
                DUMMY_HP_MAX,
                targetGfx,
                worldTick,
                calculatedDamage
            );

            publishDueHits(
                worldTick,
                npcs,
                w
            );
        } else if(targetGfx>=0){
            // Target-side GFX is direct presentation authority and does not imply a hit.
            npcs.sendMask(target,NpcSyncEncoder.Mask.gfx(targetGfx,0,0),w);
        }
        String runtimeProjectilePublication="NONE";
        if(runtimeBasic && projectileId>=0){
            runtimeProjectilePublication=V913LiveProjectilePublisher.publish(runtime,movement,target,scene);
        }
        boolean runtimeSoundPublished=false;
        if(runtimeBasic && runtime.hasBasicSound()){
            w.fixed(174,new PacketPayloadWriter().putU16BE(runtime.soundId).putU16BE(runtime.soundParam2).putU16BE(runtime.soundParam3).toByteArray());
            runtimeSoundPublished=true;
        }
        state.lastAttackTick=worldTick;
        // Directly observed cadence is presentation authority. If cadence itself is
        // unresolved, emit one presentation and require a fresh request rather than
        // inventing a repeat rate.
        state.nextAttackTick=attackSpeedTicks>0?worldTick+attackSpeedTicks:Long.MAX_VALUE;
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
            " speedTicks="+attackSpeedTicks+" tickMs=600 nextAttackTick="+state.nextAttackTick+
            " mechanicsAuthority="+(mechanicsResolved?"RESOLVED_PRESENTATION_WITH_INJECTED_DAMAGE_RULES":"UNRESOLVED_PRESENTATION_ONLY")+" legalRange="+legalRange+
            " damage="+(mechanicsResolved?Integer.toString(damage):"NOT_APPLIED")+
            " damageAuthority="+(calculatedDamage==null?"NOT_APPLIED":calculatedDamage.authority)+
            " damageFormula="+(calculatedDamage==null?"NOT_APPLIED":calculatedDamage.formula)+
            " damageMode="+(mechanicsResolved?activeDamageMode(calculatedDamage):"NOT_APPLIED")+
            " hitDelayTicks="+(mechanicsResolved?Long.toString(CombatHitScheduler.LOCALLAB_COMPAT_DELAY_TICKS):"NOT_APPLIED")+
            " hitDelayAuthority="+(mechanicsResolved?CombatHitScheduler.DELAY_AUTHORITY:"NOT_APPLIED")+
            " pendingHits="+hitScheduler.pending()+
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

    private int publishDueHits(
        long worldTick,
        NpcRegistry npcs,
        ServerPacketWriter writer
    )throws IOException{
        int published=0;

        for(CombatHitScheduler.PendingHit hit:
                hitScheduler.drainDue(worldTick)){
            NpcEntity target=
                npcs.scene(hit.targetSceneIndex);

            if(target==null||
               target.definitionId!=
                    hit.targetDefinitionId)
                continue;

            NpcSyncEncoder.Mask mask=
                NpcSyncEncoder.Mask.singleHit(
                    hit.damage,
                    hit.hitType,
                    hit.hp,
                    hit.maxHp
                );

            if(hit.targetGfx>=0)
                mask=mask.withGfx(
                    hit.targetGfx,
                    0,
                    0
                );

            npcs.sendMask(
                target,
                mask,
                writer
            );

            lastDamageThisAction+=hit.damage;
            published++;
        }

        return published;
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

    String devHitCommand(String[] p){
        String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"info";
        if(sub.equals("info")) return devHitSummary();
        if(sub.equals("reset")){
            devHitType=6;devHitVariantAuto=true;devHitStyleIcon=255;devHitPlacement="primary";
            devHitDamage=null;devHitDamageSequence=null;devHitDamageSequenceIndex=0;
            return "DEVHIT_RESET "+devHitSummary();
        }
        if(sub.equals("damage")&&p.length>=3){
            String v=p[2].toLowerCase(java.util.Locale.ROOT);
            if(v.equals("auto")||v.equals("off")||v.equals("reset")){
                devHitDamage=null;devHitDamageSequence=null;devHitDamageSequenceIndex=0;
                return "DEVHIT_DAMAGE "+devHitSummary();
            }
            try{
                int n=Integer.parseInt(v);
                if(n<0||n>255)return "DEVHIT_REJECTED damage=0..255|auto";
                devHitDamage=Integer.valueOf(n);devHitDamageSequence=null;devHitDamageSequenceIndex=0;
                return "DEVHIT_DAMAGE "+devHitSummary();
            }catch(Exception e){return "DEVHIT_REJECTED damage=0..255|auto";}
        }
        if(sub.equals("sequence")&&p.length>=3){
            String v=p[2].trim();
            if(v.equalsIgnoreCase("off")||v.equalsIgnoreCase("auto")||v.equalsIgnoreCase("reset")){
                devHitDamageSequence=null;devHitDamageSequenceIndex=0;devHitDamage=null;
                return "DEVHIT_SEQUENCE "+devHitSummary();
            }
            try{
                String[] parts=v.split(",");
                if(parts.length<2||parts.length>16)return "DEVHIT_REJECTED sequence=comma-separated_2..16_values_0..255";
                int[] seq=new int[parts.length];
                for(int i=0;i<parts.length;i++){
                    seq[i]=Integer.parseInt(parts[i].trim());
                    if(seq[i]<0||seq[i]>255)return "DEVHIT_REJECTED sequence=value_range_0..255";
                }
                devHitDamageSequence=seq;devHitDamageSequenceIndex=0;devHitDamage=null;
                return "DEVHIT_SEQUENCE "+devHitSummary();
            }catch(Exception e){return "DEVHIT_REJECTED sequence=example_37,100";}
        }
        if(sub.equals("variant")&&p.length>=3){
            String v=p[2].toLowerCase(java.util.Locale.ROOT);
            if(v.equals("auto")){devHitVariantAuto=true;return "DEVHIT_VARIANT "+devHitSummary();}
            if(v.equals("manual")){devHitVariantAuto=false;return "DEVHIT_VARIANT "+devHitSummary();}
            return "DEVHIT_REJECTED variant=auto|manual";
        }
        if(sub.equals("next")){devHitVariantAuto=false;devHitType=(devHitType+1)&255;return "DEVHIT_TYPE "+devHitSummary();}
        if(sub.equals("prev")){devHitVariantAuto=false;devHitType=(devHitType+255)&255;return "DEVHIT_TYPE "+devHitSummary();}
        if(sub.equals("type")&&p.length>=3){
            try{int v=Integer.parseInt(p[2]);if(v<0||v>255)return "DEVHIT_REJECTED type=0..255";devHitType=v;devHitVariantAuto=false;return "DEVHIT_TYPE "+devHitSummary();}
            catch(Exception e){return "DEVHIT_REJECTED type=0..255";}
        }
        if(sub.equals("styleicon")&&p.length>=3){
            try{int v=Integer.parseInt(p[2]);if(v<0||v>255)return "DEVHIT_REJECTED styleicon=0..255";devHitStyleIcon=v;return "DEVHIT_STYLEICON_METADATA_ONLY "+devHitSummary()+" transportNote=current_NPC_singleHit_mask_has_no_styleIcon_field";}
            catch(Exception e){return "DEVHIT_REJECTED styleicon=0..255";}
        }
        if(sub.equals("placement")&&p.length>=3){
            String v=p[2].toLowerCase(java.util.Locale.ROOT);
            if(v.equals("primary")){devHitPlacement="primary";return "DEVHIT_PLACEMENT "+devHitSummary();}
            if(v.equals("secondary"))return "DEVHIT_REJECTED placement=secondary reason=current_NPC_sync_encoder_only_certifies_primary_singleHit_mask";
            return "DEVHIT_REJECTED placement=primary|secondary";
        }
        return "DEVHIT_HELP info | variant auto|manual | type <0..255> | next | prev | damage <0..255|auto> | sequence <a,b,...|off> | styleicon <0..255> | placement primary|secondary | reset";
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
