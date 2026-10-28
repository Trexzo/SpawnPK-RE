package spk.local;

import java.io.IOException;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * P5.21 SOURCE-ONLY, explicitly CUSTOM_LOCALLAB joint Wind Strike settlement.
 *
 * One tick-local demonstration hit is FIXED at one damage, NOT inferred
 * SpawnPK combat. Canonical NPC HP, one air+mind rune cost, and the local
 * viewer's packet-53 inventory / packet-65 hit update are composed under
 * the same in-JVM current World, player, NPC registry and lifecycle guards.
 *
 * Production LocalSpellTargetHandler does NOT call this component. No real
 * attack timing, LOS, pathing, multi-viewer relay, kill drops, XP, projectile,
 * socket acknowledgment, cross-JVM lease or crash-durable transaction.
 */
final class LocalLabWindStrikeNpcSettlement {
    static final int DAMAGE=1;
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_P521_FIXED_ONE_DAMAGE";

    private final World world;
    private final WorldPlayer player;
    private final LongSupplier generation;
    private final NpcRegistry view;
    // P5.22: tick admission is owned by the canonical WorldPlayer, not
    // by this disposable service wrapper. Still only in-JVM LocalLab policy.

    LocalLabWindStrikeNpcSettlement(
        World world,
        WorldPlayer player,
        LongSupplier generation,
        NpcRegistry view
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.player=Objects.requireNonNull(player,"player");
        this.generation=Objects.requireNonNull(generation,"generation");
        this.view=Objects.requireNonNull(view,"view");
    }

    String settle(
        SpellTargetRequest request,
        ServerPacketWriter writer
    )throws IOException{
        Objects.requireNonNull(writer,"writer");
        if(request==null||
           request.kind!=SpellTargetRequest.Kind.NPC||
           request.spellWidget!=
               LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET)
            return "REJECTED_NOT_REVIEWED_WIND_STRIKE_NPC";

        final long expectedGeneration=generation.getAsLong();
        final String[] response=new String[1];
        boolean current=world.withOpenPlayerOwnershipIfCurrent(
            player,expectedGeneration,
            ()->{
                synchronized(player.mutationLock()){
                    response[0]=settleWithPlayerOwner(
                        request,writer,expectedGeneration);
                }
            }
        );
        return current
            ?response[0]
            :"REJECTED_STALE_WORLD_OWNER";
    }

    private String settleWithPlayerOwner(
        SpellTargetRequest request,
        ServerPacketWriter writer,
        long expectedGeneration
    )throws IOException{
        if(player.lifecycle().dead()||
           player.playerState().currentLevel(
               PlayerState.HITPOINTS)<=0)
            return "REJECTED_CASTER_DEAD";

        final long tick=world.clock().tick();
        if(player.localLabWindStrikeSettledThisTick(
                world,expectedGeneration,tick))
            return "REJECTED_SAME_TICK_DUPLICATE";

        NpcEntity target=view.scene(request.targetIndex);
        if(target==null||target.canonicalId()==null)
            return "REJECTED_NOT_CANONICAL_VISIBLE_NPC";

        final EntityId id=target.canonicalId();
        WorldNpc canonical=world.npcs().byId(id);
        if(canonical==null||
           canonical.definitionId!=target.definitionId)
            return "REJECTED_STALE_CANONICAL_NPC";

        final String[] response=new String[1];
        boolean owned;
        try{
            owned=world.npcs().withCurrentMutationOwnershipIfCurrent(
                canonical,
                ()->{
                    // Registry monitor excludes replacement/removal;
                    // lifecycle monitor also excludes unregisterExact.
                    synchronized(world.npcLifecycle()){
                        NpcEntity currentView=
                            view.scene(request.targetIndex);
                        if(currentView!=target||
                           !id.equals(currentView.canonicalId())||
                           currentView.definitionId!=canonical.definitionId){
                            response[0]="REJECTED_STALE_NPC_SCENE";
                            return;
                        }

                        NpcLifecycleService.Snapshot npc=
                            world.npcLifecycle().get(id);
                        if(npc==null||!npc.alive()||
                           npc.hitpoints<=0||
                           npc.definitionId!=canonical.definitionId){
                            response[0]="REJECTED_NPC_NOT_ALIVE";
                            return;
                        }
                        if(npc.maxHitpoints>255||npc.hitpoints>255){
                            response[0]="REJECTED_UNREPRESENTABLE_HIT_MASK";
                            return;
                        }

                        // Exact canonical NPC coordinates, never the
                        // stale per-viewer NpcEntity scene coordinates.
                        // Source-only bounded geometry; no LOS proof.
                        String geometry=
                            LocalLabWindStrikeTargetGeometry.rejection(
                                player.movement(),canonical);
                        if(geometry!=null){
                            response[0]=geometry;
                            return;
                        }

                        SpellDefinitionRepository.Spell spell=
                            SpellDefinitionRepository.byWidget(
                                request.spellWidget);
                        if(!reviewedWindStrike(spell)){
                            response[0]="REJECTED_UNREVIEWED_REQUIREMENT_DRIFT";
                            return;
                        }
                        MagicState.Check check=
                            player.magic().previewTarget(
                                request,player.bank(),
                                player.equipment(),player.playerState());
                        if(!check.accepted){
                            response[0]="REJECTED_MAGIC_REQUIREMENTS "+
                                check.message;
                            return;
                        }

                        BankState bank=player.bank();
                        int[] ids=new int[BankState.INVENTORY_CAPACITY];
                        int[] qty=new int[BankState.INVENTORY_CAPACITY];
                        int airSlot=-1,mindSlot=-1;
                        for(int slot=0;slot<ids.length;slot++){
                            BankState.Stack stack=bank.inventoryAt(slot);
                            ids[slot]=stack==null?-1:stack.itemId;
                            qty[slot]=stack==null?0:stack.qty;
                            if(stack!=null&&stack.qty>0){
                                if(stack.itemId==
                                    LocalLabWindStrikeRuneTransaction.AIR_RUNE)
                                    airSlot=slot;
                                if(stack.itemId==
                                    LocalLabWindStrikeRuneTransaction.MIND_RUNE)
                                    mindSlot=slot;
                            }
                        }
                        boolean staff=player.equipment().hasEquipped(
                            LocalLabWindStrikeRuneTransaction.AIR_STAFF);
                        if(!staff&&airSlot<0){
                            response[0]="REJECTED_CANONICAL_AIR_RUNE";
                            return;
                        }
                        if(mindSlot<0){
                            response[0]="REJECTED_CANONICAL_MIND_RUNE";
                            return;
                        }

                        int[] postIds=ids.clone();
                        int[] postQty=qty.clone();
                        if(!staff)consumeOne(postIds,postQty,airSlot);
                        consumeOne(postIds,postQty,mindSlot);

                        // Every wire payload is validated before the
                        // recoverable, queue-only atomic publication.
                        byte[][] inventoryViews=
                            bank.previewRecipeInventoryViews(
                                postIds,postQty);
                        int hpAfter=Math.max(0,npc.hitpoints-DAMAGE);
                        NpcSyncEncoder.Mask hit=
                            NpcSyncEncoder.Mask.singleHit(
                                DAMAGE,1,hpAfter,npc.maxHitpoints);
                        ServerPacketWriter.RecoverablePacketResult result=
                            writer.publishQueueOnlyRecoverablePacketIfIdle(
                                ()->{
                                    for(byte[] body:inventoryViews)
                                        writer.varShort(53,body);
                                    // Only this viewer's native hit mask.
                                    // World relay and kill consequences stay
                                    // separate, explicitly unimplemented.
                                    view.sendMaskLocal(target,hit,writer);
                                }
                            );
                        if(result!=ServerPacketWriter.RecoverablePacketResult
                            .COMMITTED){
                            response[0]="REJECTED_WRITER_BACKPRESSURE_RETRYABLE";
                            return;
                        }

                        // Under all four owner monitors, the previously
                        // validated source-local semantic transitions have
                        // no expected failure path. This is NOT crash ACID.
                        NpcLifecycleService.DamageResult damage=
                            world.npcLifecycle()
                                .applyDamageOwnedIfRegistered(
                                    id,DAMAGE,tick,npc.registrationIdentity);
                        if(damage.ignoredDead||
                           damage.appliedDamage!=DAMAGE||
                           damage.hitpointsAfter!=hpAfter)
                            throw new IllegalStateException(
                                "prevalidated NPC hit result changed");
                        bank.replaceInventorySemantic(postIds,postQty);
                        player.markLocalLabWindStrikeSettled(
                            world,expectedGeneration,tick);

                        response[0]="LOCAL_LAB_WIND_STRIKE_NPC_SETTLED"+
                            " npc="+id+
                            " damage="+damage.appliedDamage+
                            " npcHpAfter="+damage.hitpointsAfter+
                            " newlyDead="+damage.newlyDied+
                            " airSpent="+(staff?0:1)+
                            " mindSpent=1"+
                            " authority="+AUTHORITY+
                            " queueAdmittedNotClientAck=true"+
                            " magicXpLootAndRealCombatNotApplied=true";
                    }
                }
            );
        }catch(IOException failure){
            throw failure;
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IOException(
                "Wind Strike NPC joint settlement failed",failure);
        }
        return owned?response[0]:"REJECTED_STALE_CANONICAL_NPC";
    }

    private static boolean reviewedWindStrike(
        SpellDefinitionRepository.Spell spell
    ){
        return spell!=null&&
            spell.book==SpellDefinitionRepository.Book.MODERN&&
            spell.targeted&&spell.level==1&&
            "Wind strike".equals(spell.name)&&
            spell.resources.length==2&&
            spell.resources[0].required==1&&
            spell.resources[1].required==1&&
            spell.resources[0].inventoryIds.length>0&&
            spell.resources[1].inventoryIds.length>0&&
            spell.resources[0].inventoryIds[0]==
                LocalLabWindStrikeRuneTransaction.AIR_RUNE&&
            spell.resources[1].inventoryIds[0]==
                LocalLabWindStrikeRuneTransaction.MIND_RUNE;
    }

    private static void consumeOne(int[] ids,int[] qty,int slot){
        if(slot<0||slot>=qty.length||qty[slot]<=0)
            throw new IllegalStateException("invalid rune postimage");
        qty[slot]--;
        if(qty[slot]==0)ids[slot]=-1;
    }
}
