package spk.local;

import java.io.IOException;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Source-only CUSTOM_LOCALLAB Wind Strike readiness check for one
 * canonical World NPC. It deliberately makes NO state or packet changes.
 *
 * A future combined spell/rune/NPC-hit transaction must revalidate while
 * retaining these locks, and must never interpret this preview as a
 * reservation, successful hit, or permission to pre-charge runes.
 */
final class LocalLabWindStrikeNpcPreflight {
    private final World world;
    private final WorldPlayer owner;
    private final LongSupplier generation;
    private final NpcRegistry scene;

    LocalLabWindStrikeNpcPreflight(
        World world,
        WorldPlayer owner,
        LongSupplier generation,
        NpcRegistry scene
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.owner=Objects.requireNonNull(owner,"owner");
        this.generation=Objects.requireNonNull(generation,"generation");
        this.scene=Objects.requireNonNull(scene,"scene");
    }

    String check(SpellTargetRequest request)throws IOException{
        if(request==null||
           request.kind!=SpellTargetRequest.Kind.NPC||
           request.spellWidget!=
               LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET)
            return "REJECTED_NOT_CANONICAL_WIND_STRIKE_NPC_REQUEST";

        final String[] response=new String[1];
        boolean current=world.withOpenPlayerOwnershipIfCurrent(
            owner,generation.getAsLong(),
            ()->{
                synchronized(owner.mutationLock()){
                    response[0]=checkCurrent(request);
                }
            }
        );
        return current
            ?response[0]
            :"REJECTED_STALE_WORLD_OWNER";
    }

    private String checkCurrent(SpellTargetRequest request)
        throws IOException{
        if(owner.lifecycle().dead()||
           owner.playerState().currentLevel(
               PlayerState.HITPOINTS)<=0)
            return "REJECTED_CASTER_DEAD";

        NpcEntity view=scene.scene(request.targetIndex);
        if(view==null||view.canonicalId()==null)
            return "REJECTED_TARGET_NOT_CANONICAL_NPC";

        final EntityId canonicalId=view.canonicalId();
        WorldNpc expected=world.npcs().byId(canonicalId);
        if(expected==null||
           expected.definitionId!=view.definitionId)
            return "REJECTED_STALE_CANONICAL_NPC";

        final String[] answer=new String[1];
        boolean current;
        try{
            current=world.npcs()
                .withCurrentMutationOwnershipIfCurrent(
                    expected,
                    ()->{
                        synchronized(world.npcLifecycle()){
                            NpcEntity now=scene.scene(
                                request.targetIndex);
                            if(now==null||
                               !canonicalId.equals(now.canonicalId())||
                               now.definitionId!=expected.definitionId){
                                answer[0]="REJECTED_STALE_NPC_SCENE";
                                return;
                            }

                            NpcLifecycleService.Snapshot npc=
                                world.npcLifecycle().get(canonicalId);
                            if(npc==null||!npc.alive()||
                               npc.hitpoints<=0||
                               npc.definitionId!=expected.definitionId){
                                answer[0]="REJECTED_NPC_NOT_ALIVE";
                                return;
                            }

                            SpellDefinitionRepository.Spell spell=
                                SpellDefinitionRepository.byWidget(
                                    request.spellWidget);
                            if(!reviewedWindStrike(spell)){
                                answer[0]=
                                    "REJECTED_WIND_STRIKE_REQUIREMENT_DRIFT";
                                return;
                            }

                            MagicState.Check check=
                                owner.magic().previewTarget(
                                    request,owner.bank(),
                                    owner.equipment(),owner.playerState());
                            if(!check.accepted){
                                answer[0]="REJECTED_MAGIC_REQUIREMENTS "+
                                    check.message;
                                return;
                            }

                            // Explicit narrow policy: only rune 556/558
                            // and equipped air staff 1381 are admitted.
                            // Native alternative combination runes remain
                            // unsupported pending original-game evidence.
                            boolean airStaff=owner.equipment()
                                .hasEquipped(
                                    LocalLabWindStrikeRuneTransaction.AIR_STAFF);
                            if(!airStaff&&owner.bank().inventoryCount(
                                    LocalLabWindStrikeRuneTransaction.AIR_RUNE)<1){
                                answer[0]="REJECTED_CANONICAL_AIR_RUNE";
                                return;
                            }
                            if(owner.bank().inventoryCount(
                                    LocalLabWindStrikeRuneTransaction.MIND_RUNE)<1){
                                answer[0]="REJECTED_CANONICAL_MIND_RUNE";
                                return;
                            }

                            answer[0]="LOCAL_LAB_WIND_STRIKE_NPC_READY"+
                                " npc="+canonicalId+
                                " airStaff="+airStaff+
                                " authority=CUSTOM_LOCALLAB_P520"+
                                " readOnly=true"+
                                " noRuneSpendHitOrMagicLastMutation=true";
                        }
                    }
                );
        }catch(IOException failure){
            throw failure;
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IOException(
                "Wind Strike NPC preflight failed",failure);
        }
        return current
            ?answer[0]
            :"REJECTED_STALE_CANONICAL_NPC";
    }

    private static boolean reviewedWindStrike(
        SpellDefinitionRepository.Spell s
    ){
        return s!=null&&
            s.book==SpellDefinitionRepository.Book.MODERN&&
            s.targeted&&s.level==1&&
            "Wind strike".equals(s.name)&&
            s.resources.length==2&&
            s.resources[0].required==1&&
            s.resources[1].required==1&&
            s.resources[0].inventoryIds.length>0&&
            s.resources[1].inventoryIds.length>0&&
            s.resources[0].inventoryIds[0]==
                LocalLabWindStrikeRuneTransaction.AIR_RUNE&&
            s.resources[1].inventoryIds[0]==
                LocalLabWindStrikeRuneTransaction.MIND_RUNE;
    }
}
