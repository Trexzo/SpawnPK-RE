package spk.local;

import java.io.*;
import java.util.*;

/**
 * Per-session NPC registry. MAINLINE remains the single packet-65 owner.
 * WORLD-R7 feeds production HOME population/wandering through this same registry
 * while preserving MAINLINE follower publication through packet 65.
 */
final class NpcRegistry {
    static final int LOCAL_PLAYER_INDEX = 1;
    static final int BLOOD_FOUNTAIN_INDEX=1;
    static final int PLAYER_DUMMY_INDEX=2;
    static final int PVM_DUMMY_INDEX=3;
    static final int PET_INDEX=4;

    private final ArrayList<NpcEntity> visible=new ArrayList<>();
    private final ArrayDeque<int[]> ownerTrail=new ArrayDeque<>();
    /** Exact-v9.12 follower train: each entry is a tile vacated by the main pet. */
    private final ArrayDeque<int[]> miniTrail=new ArrayDeque<>();
    private final LinkedHashSet<Integer> devOwnedSceneIndexes=new LinkedHashSet<>();
    private final DevAuthorityWorkbench dev;
    private final WorldPetNpcService worldPets;
    private final EntityId canonicalOwnerId;
    private NpcEntity pet;
    /** World-visible numeric native state for pets such as Behemoth charge stacks. */
    private int petNativeState;
    /** Configured mini-pet actor; it targets/follows the exact active main pet entity. */
    private NpcEntity miniPet;
    private boolean recentOwnerRunning;
    private boolean hasLastOwnerAnchor;
    private int lastOwnerAnchorX,lastOwnerAnchorY;
    /** Last known cardinal owner facing used only for Drop egress. Production V9.09: first pet step is cardinal in owner facing direction. */
    private int lastOwnerFacingDir=6;
    private int miniTrailX,miniTrailY;
    private boolean hasMiniTrail;
    /** One-tick hold after a live route replacement/U-turn so the pet never steps into the abandoned branch. */
    private boolean suppressNextOwnerBreadcrumb;
    /** Broken breadcrumb recovery is triggered by proven trail discontinuity, never by a guessed distance threshold. */
    private int petDiscontinuityTicks;
    private int miniDiscontinuityTicks;

    NpcRegistry(){
        this(new DevAuthorityWorkbench(),null,null);
    }

    NpcRegistry(DevAuthorityWorkbench dev){
        this(dev,null,null);
    }

    NpcRegistry(
        DevAuthorityWorkbench dev,
        WorldPetNpcService worldPets,
        EntityId canonicalOwnerId
    ){
        this.dev=dev==null
            ?new DevAuthorityWorkbench()
            :dev;
        if((worldPets==null)!=(canonicalOwnerId==null))
            throw new IllegalArgumentException(
                "worldPets and canonicalOwnerId must be supplied together"
            );
        this.worldPets=worldPets;
        this.canonicalOwnerId=canonicalOwnerId;
    }

    void bootstrap(ServerPacketWriter w,MovementState movement,PetState petState) throws IOException {
        canonicalRemoveAll();
        visible.clear(); ownerTrail.clear(); miniTrail.clear(); devOwnedSceneIndexes.clear(); pet=null; petNativeState=0; miniPet=null; recentOwnerRunning=false; hasLastOwnerAnchor=false; hasMiniTrail=false; lastOwnerFacingDir=6; suppressNextOwnerBreadcrumb=false; petDiscontinuityTicks=0; miniDiscontinuityTicks=0;
        // Existing home coordinates remain explicitly LOCAL diagnostic placements.
        visible.add(new NpcEntity(BLOOD_FOUNTAIN_INDEX,1799,movement.x()+3,movement.y()+3));
        // Exact definitions, localhost diagnostic placement only.
        visible.add(new NpcEntity(PLAYER_DUMMY_INDEX,1488,movement.x()+2,movement.y()));
        visible.add(new NpcEntity(PVM_DUMMY_INDEX,1489,movement.x()+2,movement.y()+2));
        if(petState.active()){
            pet=new NpcEntity(PET_INDEX,petState.npcId(),movement.x()-1,movement.y(),true,petState.itemId(),LOCAL_PLAYER_INDEX);
            visible.add(pet);
            canonicalEnsureMain(petState.itemId());
        }
        w.varShort(65,NpcSyncEncoder.initial(visible,movement.x(),movement.y()));
    }

    /** Production HOME bootstrap supplied by WORLD, emitted by MAINLINE's one registry. */
    void bootstrapHome(ServerPacketWriter w,MovementState movement,PetState petState,HomeWorldRuntimePlan home) throws IOException {
        if(home==null) throw new NullPointerException("home");
        canonicalRemoveAll();
        visible.clear(); ownerTrail.clear(); miniTrail.clear(); devOwnedSceneIndexes.clear(); pet=null; petNativeState=0; miniPet=null; recentOwnerRunning=false; hasLastOwnerAnchor=false; hasMiniTrail=false; lastOwnerFacingDir=6; suppressNextOwnerBreadcrumb=false; petDiscontinuityTicks=0; miniDiscontinuityTicks=0;
        List<NpcEntity> world=home.bootstrapNpcs(movement.x(),movement.y());
        for(NpcEntity n:world){
            if(n.sceneIndex==PET_INDEX) throw new IllegalStateException("HOME scene index collides with PET_INDEX");
            visible.add(n);
        }
        if(petState.active()){
            pet=new NpcEntity(PET_INDEX,petState.npcId(),movement.x()-1,movement.y(),true,petState.itemId(),LOCAL_PLAYER_INDEX);
            visible.add(pet);
            canonicalEnsureMain(petState.itemId());
        }
        assertUniqueSceneIndexes();
        w.varShort(65,NpcSyncEncoder.initial(visible,movement.x(),movement.y()));
    }

    NpcEntity pet(){
        refreshCanonicalActorProjections();
        return pet;
    }
    NpcEntity miniPet(){
        refreshCanonicalActorProjections();
        return miniPet;
    }
    NpcEntity scene(int sceneIndex){ return findScene(sceneIndex); }
    NpcEntity canonical(EntityId canonicalId){
        if(canonicalId==null)return null;
        for(NpcEntity npc:visible)
            if(canonicalId.equals(npc.canonicalId()))
                return npc;
        return null;
    }
    int visibleCount(){return visible.size();}
    List<NpcEntity> snapshot(){return new ArrayList<>(visible);}

    /**
     * Remove region-local NPC view members while preserving this player's
     * follower actors. Packet 73 rebases retained actors without changing
     * their global/world position.
     *
     * The server-side visible list is mutated together with packet 65. This
     * prevents later pet packets from retaining HOME NPCs the client has
     * already removed from its scene list.
     */
    int detachRegionViewPreservingFollowers(
        ServerPacketWriter w
    )throws IOException{
        refreshCanonicalActorProjections();

        ArrayList<NpcSyncEncoder.Update> updates=
            new ArrayList<>();
        int removed=0;

        for(NpcEntity n:visible){
            if(n==pet||n==miniPet){
                updates.add(
                    NpcSyncEncoder.Update.retain(n)
                );
            }else{
                updates.add(
                    NpcSyncEncoder.Update.remove(n)
                );
                removed++;
            }
        }

        if(removed>0){
            w.varShort(
                65,
                NpcSyncEncoder.encode(
                    updates,
                    Collections.emptyList(),
                    0,
                    0
                )
            );

            visible.removeIf(
                n->n!=pet&&n!=miniPet
            );
            devOwnedSceneIndexes.clear();
        }

        ownerTrail.clear();
        miniTrail.clear();
        recentOwnerRunning=false;
        hasLastOwnerAnchor=false;
        hasMiniTrail=false;
        suppressNextOwnerBreadcrumb=false;
        petDiscontinuityTicks=0;
        miniDiscontinuityTicks=0;

        assertUniqueSceneIndexes();
        return removed;
    }

    /**
     * Re-add the current WORLD-owned HOME projection while retaining any
     * follower actors that survived the region transition.
     */
    int reattachHomeView(
        ServerPacketWriter w,
        MovementState movement,
        HomeWorldRuntimePlan home
    )throws IOException{
        if(home==null)
            throw new NullPointerException("home");

        refreshCanonicalActorProjections();

        List<NpcEntity> homeNpcs=
            home.bootstrapNpcs(
                movement.x(),
                movement.y()
            );

        for(NpcEntity n:homeNpcs)
            if(n.sceneIndex==PET_INDEX)
                throw new IllegalStateException(
                    "HOME scene index collides with PET_INDEX"
                );

        ensureNoAddedSceneCollision(homeNpcs);

        if(!homeNpcs.isEmpty()){
            w.varShort(
                65,
                NpcSyncEncoder.encode(
                    retains(),
                    homeNpcs,
                    movement.x(),
                    movement.y()
                )
            );
            visible.addAll(homeNpcs);
        }

        assertUniqueSceneIndexes();
        return homeNpcs.size();
    }

    /**
     * Emit a world-visible mask update. Local publication remains immediate; Engine
     * R3.2 records the same semantic mask for the other viewers in the shared World.
     */
    void sendMask(NpcEntity target,NpcSyncEncoder.Mask mask,ServerPacketWriter w) throws IOException {
        sendMaskLocal(target,mask,w);
        SharedNpcWorldRelay.relayMask(w,this,target,mask);
    }

    /** Viewer-local publication used by the shared replication layer itself. */
    void sendMaskLocal(NpcEntity target,NpcSyncEncoder.Mask mask,ServerPacketWriter w) throws IOException {
        if(target==null || mask==null || findScene(target.sceneIndex)!=target) throw new IllegalArgumentException("target not visible");
        ArrayList<NpcSyncEncoder.Update> updates=new ArrayList<>();
        for(NpcEntity n:visible) updates.add(n==target?NpcSyncEncoder.Update.mask(n,mask):NpcSyncEncoder.Update.retain(n));
        w.varShort(65,NpcSyncEncoder.encode(updates,Collections.emptyList(),0,0));
    }

    Integer petParticleSelector(){ return dev.petParticleSelector(); }
    int petNativeState(){ return petNativeState; }

    /**
     * Spawn a mirrored remote-world NPC with an optional exact add-time particle
     * presentation. It is relay-owned so the viewer can remove it safely.
     */
    NpcEntity spawnMirroredNpc(int npcId,int worldX,int worldY,Integer particleSelector,MovementState movement,ServerPacketWriter w)throws IOException{
        if(npcId<0||npcId>16383) throw new IllegalArgumentException("npcId");
        if(!MovementState.insideLoadedRegion(worldX,worldY)) throw new IllegalArgumentException("outside loaded region");
        int dx=worldX-movement.x(),dy=worldY-movement.y();
        if(dx<-16||dx>15||dy<-16||dy>15) throw new IllegalArgumentException("offset outside add range");
        int scene=allocateDevSceneIndex();
        if(scene<0) throw new IllegalStateException("no free scene index");
        NpcEntity e=new NpcEntity(scene,npcId,worldX,worldY);
        Map<Integer,NpcSpawnPresentation> presentation=particleSelector==null
            ? Collections.<Integer,NpcSpawnPresentation>emptyMap()
            : Collections.singletonMap(scene,NpcSpawnPresentation.particle(particleSelector.intValue()));
        w.varShort(65,NpcSyncEncoder.encode(retains(),Collections.singletonList(e),movement.x(),movement.y(),presentation));
        visible.add(e);devOwnedSceneIndexes.add(scene);
        return e;
    }

    /** Exact NPC forced-text mask path. NPC 8330 consumes literal SNIPE in the
     * pinned client and enters its bespoke Scopesight presentation state. */
    String forcePetText(String text,ServerPacketWriter w) throws IOException {
        if(pet==null) return "REJECTED_NO_ACTIVE_PET";
        sendMask(pet,NpcSyncEncoder.Mask.forceText(text),w);
        return "PET_FORCE_TEXT_OK item="+pet.petItemId+" npc="+pet.definitionId+" text="+text;
    }

    String setPetNativeState(int state,ServerPacketWriter w) throws IOException {
        if(pet==null) return "REJECTED_NO_ACTIVE_PET";
        if(state<0||state>3) return "REJECTED_STATE_RANGE expected=0..3";
        PetPresentationProfile.NativeStateFamily family=PetPresentationProfile.nativeStateFamily(pet.definitionId);
        if(family==PetPresentationProfile.NativeStateFamily.NONE)
            return "REJECTED_PET_HAS_NO_NUMERIC_NATIVE_STATE item="+pet.petItemId+" npc="+pet.definitionId;
        sendMask(pet,NpcSyncEncoder.Mask.forceText(Integer.toString(state)),w);
        petNativeState=state;
        return "PET_NATIVE_STATE_OK item="+pet.petItemId+" npc="+pet.definitionId+" family="+family+
            " state="+state+" visual="+PetPresentationProfile.nativeStateVisual(pet.definitionId,state);
    }

    String animatePet(int animationId,int delay,ServerPacketWriter w) throws IOException {
        if(pet==null) return "REJECTED_NO_ACTIVE_PET";
        if(animationId < -1 || animationId > 0xffff || delay < 0 || delay > 255) return "REJECTED_ANIMATION_RANGE";
        sendMask(pet,NpcSyncEncoder.Mask.animation(animationId,delay),w);
        return "PET_ANIMATION_OK item="+pet.petItemId+" npc="+pet.definitionId+" anim="+animationId+" delay="+delay;
    }

    String gfxPet(int gfxId,int height,int delay,ServerPacketWriter w) throws IOException {
        if(pet==null) return "REJECTED_NO_ACTIVE_PET";
        try {
            sendMask(pet,NpcSyncEncoder.Mask.gfx(gfxId,height,delay),w);
        } catch(IllegalArgumentException e){ return "REJECTED_GFX_RANGE "+e.getMessage(); }
        return "PET_GFX_OK item="+pet.petItemId+" npc="+pet.definitionId+" gfx="+gfxId+" height="+height+" delay="+delay;
    }

    String animationAndGfxPet(int animationId,int animationDelay,int gfxId,int height,int gfxDelay,ServerPacketWriter w) throws IOException {
        if(pet==null) return "REJECTED_NO_ACTIVE_PET";
        try {
            sendMask(pet,NpcSyncEncoder.Mask.animationAndGfx(animationId,animationDelay,gfxId,height,gfxDelay),w);
        } catch(IllegalArgumentException e){ return "REJECTED_ANIMFX_RANGE "+e.getMessage(); }
        return "PET_ANIM_GFX_OK item="+pet.petItemId+" npc="+pet.definitionId+" anim="+animationId+" animDelay="+animationDelay+
            " gfx="+gfxId+" height="+height+" gfxDelay="+gfxDelay;
    }

    /** Localhost-only visual diagnostic: keep the pet item but replace the visible NPC definition. */
    String previewPetDefinition(int npcId,MovementState movement,ServerPacketWriter w) throws IOException {
        if(pet==null) return "REJECTED_NO_ACTIVE_PET";
        if(npcId<0 || npcId>16383) return "REJECTED_NPC_ID_RANGE";
        NpcEntity old=pet;
        ArrayList<NpcSyncEncoder.Update> remove=new ArrayList<>();
        for(NpcEntity n:visible) remove.add(n==old?NpcSyncEncoder.Update.remove(n):NpcSyncEncoder.Update.retain(n));
        w.varShort(65,NpcSyncEncoder.encode(remove,Collections.emptyList(),0,0));
        visible.remove(old);
        NpcEntity replacement=new NpcEntity(PET_INDEX,npcId,old.x,old.y,true,old.petItemId,LOCAL_PLAYER_INDEX);
        ArrayList<NpcSyncEncoder.Update> retained=retains();
        w.varShort(65,NpcSyncEncoder.encode(retained,Collections.singletonList(replacement),movement.x(),movement.y(),spawnPresentationFor(replacement)));
        visible.add(replacement); pet=replacement; ownerTrail.clear(); miniTrail.clear();
        canonicalEnsureMain(old.petItemId);
        if(miniPet!=null) sendMask(miniPet,NpcSyncEncoder.Mask.interactionTarget(pet.sceneIndex),w);
        return "PET_PREVIEW_DEFINITION_OK item="+old.petItemId+" npc="+old.definitionId+"->"+npcId+
            " world="+replacement.x+","+replacement.y+" persisted=false pickupStillReturnsItem="+old.petItemId;
    }

    static final class PreparedMiniPetRemoval {
        final NpcEntity expectedMini;

        PreparedMiniPetRemoval(
            NpcEntity expectedMini
        ){
            this.expectedMini=expectedMini;
        }
    }

    PreparedMiniPetRemoval prepareMiniPetRemoval(){
        return new PreparedMiniPetRemoval(
            miniPet
        );
    }

    String publishMiniPetRemoval(
        PreparedMiniPetRemoval prepared,
        ServerPacketWriter w
    )throws IOException{
        if(prepared==null)
            throw new NullPointerException(
                "prepared"
            );

        if(prepared.expectedMini==null)
            return "MINIPET_NONE_ACTIVE";

        ArrayList<NpcSyncEncoder.Update> updates=
            new ArrayList<>();

        for(NpcEntity n:visible)
            updates.add(
                n==prepared.expectedMini
                    ?NpcSyncEncoder.Update.remove(n)
                    :NpcSyncEncoder.Update.retain(n)
            );

        w.varShort(
            65,
            NpcSyncEncoder.encode(
                updates,
                Collections.emptyList(),
                0,
                0
            )
        );

        return "MINIPET_DESPAWN_OK npc="+
            prepared.expectedMini.definitionId+
            " scene="+
            prepared.expectedMini.sceneIndex;
    }

    void commitMiniPetRemoval(
        PreparedMiniPetRemoval prepared
    ){
        if(prepared==null)
            throw new NullPointerException(
                "prepared"
            );

        if(miniPet!=prepared.expectedMini)
            throw new IllegalStateException(
                "mini-pet state changed before prepared removal commit"
            );

        if(prepared.expectedMini==null)
            return;

        visible.remove(
            prepared.expectedMini
        );
        miniPet=null;
        miniTrail.clear();
        hasMiniTrail=false;
        canonicalRemoveMini();
    }

    static final class PreparedMiniPetReplacement {
        final NpcEntity expectedMainPet;
        final NpcEntity oldMini;
        final NpcEntity newMini;
        final int itemId;

        PreparedMiniPetReplacement(
            NpcEntity expectedMainPet,
            NpcEntity oldMini,
            NpcEntity newMini,
            int itemId
        ){
            this.expectedMainPet=expectedMainPet;
            this.oldMini=oldMini;
            this.newMini=newMini;
            this.itemId=itemId;
        }
    }

    PreparedMiniPetReplacement prepareMiniPetReplacement(
        MiniPetDefinitionRepository.Def d,
        MovementState movement
    ){
        if(d==null)
            throw new IllegalArgumentException(
                "mini definition"
            );
        if(pet==null)
            throw new IllegalStateException(
                "no active main pet"
            );

        int scene=
            miniPet!=null
                ?miniPet.sceneIndex
                :allocateDynamicSceneIndex();

        if(scene<0)
            return null;

        int[] start=
            miniTrailingTile(movement);

        NpcEntity next=
            new NpcEntity(
                scene,
                d.npcId,
                start[0],
                start[1]
            );

        return new PreparedMiniPetReplacement(
            pet,
            miniPet,
            next,
            d.itemId
        );
    }

    String publishMiniPetReplacement(
        PreparedMiniPetReplacement prepared,
        MovementState movement,
        ServerPacketWriter w
    )throws IOException{
        if(prepared==null)
            return "REJECTED_NO_FREE_SCENE_INDEX";

        if(prepared.oldMini!=null){
            ArrayList<NpcSyncEncoder.Update> remove=
                new ArrayList<>();

            for(NpcEntity n:visible)
                remove.add(
                    n==prepared.oldMini
                        ?NpcSyncEncoder.Update.remove(n)
                        :NpcSyncEncoder.Update.retain(n)
                );

            w.varShort(
                65,
                NpcSyncEncoder.encode(
                    remove,
                    Collections.emptyList(),
                    0,
                    0
                )
            );
        }

        ArrayList<NpcSyncEncoder.Update> retained=
            new ArrayList<>();

        for(NpcEntity n:visible)
            if(n!=prepared.oldMini)
                retained.add(
                    NpcSyncEncoder.Update.retain(n)
                );

        w.varShort(
            65,
            NpcSyncEncoder.encode(
                retained,
                Collections.singletonList(
                    prepared.newMini
                ),
                movement.x(),
                movement.y()
            )
        );

        ArrayList<NpcSyncEncoder.Update> masked=
            new ArrayList<>();

        for(NpcEntity n:visible)
            if(n!=prepared.oldMini)
                masked.add(
                    NpcSyncEncoder.Update.retain(n)
                );

        masked.add(
            NpcSyncEncoder.Update.mask(
                prepared.newMini,
                NpcSyncEncoder.Mask.interactionTarget(
                    prepared.expectedMainPet.sceneIndex
                )
            )
        );

        w.varShort(
            65,
            NpcSyncEncoder.encode(
                masked,
                Collections.emptyList(),
                0,
                0
            )
        );

        return "MINIPET_SPAWN_OK item="+
            prepared.itemId+
            " npc="+prepared.newMini.definitionId+
            " scene="+prepared.newMini.sceneIndex+
            " world="+prepared.newMini.x+
            ","+prepared.newMini.y+
            " mainPetScene="+
            prepared.expectedMainPet.sceneIndex+
            " relation=PLAYER_TO_MAINPET_TO_MINIPET spawnPolicy=LOCAL_TRAILING_MAINPET";
    }

    void commitMiniPetReplacement(
        PreparedMiniPetReplacement prepared
    ){
        if(prepared==null)
            throw new NullPointerException(
                "prepared"
            );
        if(pet!=prepared.expectedMainPet||
           miniPet!=prepared.oldMini)
            throw new IllegalStateException(
                "mini-pet state changed before prepared commit"
            );

        if(prepared.oldMini!=null)
            visible.remove(
                prepared.oldMini
            );

        visible.add(
            prepared.newMini
        );
        miniPet=prepared.newMini;
        miniTrail.clear();
        hasMiniTrail=true;
        miniTrailX=pet.x;
        miniTrailY=pet.y;
        canonicalEnsureMini(
            prepared.itemId
        );
    }

    void relayCommittedMiniPetInteractionTarget(
        PreparedMiniPetReplacement prepared,
        ServerPacketWriter sourceWriter
    ){
        if(prepared==null)
            throw new NullPointerException(
                "prepared"
            );
        if(sourceWriter==null)
            throw new NullPointerException(
                "sourceWriter"
            );
        if(pet!=prepared.expectedMainPet||
           miniPet!=prepared.newMini)
            throw new IllegalStateException(
                "prepared mini replacement not committed"
            );

        SharedNpcWorldRelay.relayMask(
            sourceWriter,
            this,
            miniPet,
            NpcSyncEncoder.Mask.interactionTarget(
                pet.sceneIndex
            )
        );
    }

    String spawnOrReplaceMiniPet(MiniPetDefinitionRepository.Def d,MovementState movement,ServerPacketWriter w)throws IOException {
        if(d==null)return "REJECTED_NULL_MINI_DEFINITION";
        if(pet==null)return "REJECTED_NO_MAIN_PET";
        if(miniPet!=null) removeMiniPet(w);
        int scene=allocateDynamicSceneIndex(); if(scene<0)return "REJECTED_NO_FREE_SCENE_INDEX";
        // Exact production first-spawn offset is not certified.  Keep the mini
        // one tile behind the main pet rather than stacking both actors.
        int[] start=miniTrailingTile(movement);
        NpcEntity n=new NpcEntity(scene,d.npcId,start[0],start[1]);
        w.varShort(65,NpcSyncEncoder.encode(retains(),Collections.singletonList(n),movement.x(),movement.y()));
        visible.add(n); miniPet=n; miniTrail.clear(); hasMiniTrail=true; miniTrailX=pet.x; miniTrailY=pet.y;
        canonicalEnsureMini(d.itemId);
        // Production V9.10 authority: mini-pet interaction target is the exact active main-pet NPC scene index.
        sendMask(n,NpcSyncEncoder.Mask.interactionTarget(pet.sceneIndex),w);
        return "MINIPET_SPAWN_OK item="+d.itemId+" npc="+d.npcId+" scene="+scene+" world="+n.x+","+n.y+
            " mainPetScene="+pet.sceneIndex+" relation=PLAYER_TO_MAINPET_TO_MINIPET spawnPolicy=LOCAL_TRAILING_MAINPET authority="+d.authority;
    }

    String removeMiniPet(ServerPacketWriter w)throws IOException {
        PreparedMiniPetRemoval prepared=
            prepareMiniPetRemoval();

        String result=
            publishMiniPetRemoval(
                prepared,
                w
            );

        commitMiniPetRemoval(
            prepared
        );
        return result;
    }

    String spawnPet(PetDefinitionRepository.Def d,MovementState movement,ServerPacketWriter w) throws IOException {
        if(pet!=null) return "REJECTED_ACTIVE_PET item="+pet.petItemId+" npc="+pet.definitionId;
        // Production V9.09 Drop geometry:
        //   owner animation 827, pet ADD on owner's exact tile, then one ordinary
        //   cardinal WALK in the owner's current facing direction. Do NOT inherit
        //   the previous Pick-up interaction target or use the opposite/old anchor.
        NpcEntity n=new NpcEntity(PET_INDEX,d.npcId,movement.x(),movement.y(),true,d.itemId,LOCAL_PLAYER_INDEX);
        if(findScene(PET_INDEX)!=null) throw new IllegalStateException("PET_INDEX already occupied");
        ArrayList<NpcSyncEncoder.Update> old=retains();
        w.varShort(65,NpcSyncEncoder.encode(old,Collections.singletonList(n),movement.x(),movement.y(),spawnPresentationFor(n)));
        visible.add(n); pet=n; petNativeState=0; ownerTrail.clear(); miniTrail.clear(); recentOwnerRunning=false;
        canonicalEnsureMain(d.itemId);
        int egressDir=cardinalizeFacing(lastOwnerFacingDir);
        int[] egress=directionDelta(egressDir);
        int outX=movement.x()+egress[0],outY=movement.y()+egress[1];
        if(!MovementState.insideLoadedRegion(outX,outY)){
            // Fail-safe only for region edge; the broader HOME collision authority is
            // separate. Prefer a recently occupied cardinal tile when available.
            if(hasLastOwnerAnchor && Math.abs(lastOwnerAnchorX-movement.x())+Math.abs(lastOwnerAnchorY-movement.y())==1){
                outX=lastOwnerAnchorX; outY=lastOwnerAnchorY;
            } else {
                int[] west=directionDelta(3); outX=movement.x()+west[0]; outY=movement.y()+west[1];
            }
        }
        enqueueTrail(outX,outY);
        return "PET_SPAWN_OK item="+d.itemId+" npc="+d.npcId+" sceneIndex="+n.sceneIndex+" world="+n.x+","+n.y+" spawn=OWNER_TILE stepOutTarget="+outX+","+outY+
            " ownerTarget="+(32768+LOCAL_PLAYER_INDEX)+" stand="+d.standAnim+" walk="+d.walkAnim+" particle="+(dev.petParticleSelector()==null?"AUTO":dev.petParticleSelector())+" provenance="+d.provenance;
    }

    /**
     * Production Pick-up presentation: execute only from cardinal adjacency, face
     * the live pet, and play owner animation 827 with no GFX. The interaction target
     * is temporary; LocalSession clears it after the presentation so a later Drop
     * cannot make the player face scene 4 forever.
     */
    String preparePetPickupFacing(MovementState movement,ServerPacketWriter w) throws IOException {
        if(pet==null) return "REJECTED_NO_ACTIVE_PET";
        int dx=pet.x-movement.x(),dy=pet.y-movement.y();
        if(Math.abs(dx)+Math.abs(dy)!=1)
            return "REJECTED_NOT_CARDINAL_ADJACENT pet="+pet.x+","+pet.y+" owner="+movement.x()+","+movement.y();
        int sx=Integer.compare(dx,0),sy=Integer.compare(dy,0);
        lastOwnerFacingDir=MovementState.direction(movement.x(),movement.y(),movement.x()+sx,movement.y()+sy);
        w.varShort(81,CombatSync.player81AnimationAndInteraction(PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION,pet.sceneIndex));
        return "PET_PICKUP_FACE_OK targetScene="+pet.sceneIndex+" pet="+pet.x+","+pet.y+" owner="+movement.x()+","+movement.y()+
            " facingVector="+sx+","+sy+" ownerFacingDir="+lastOwnerFacingDir+
            " ownerAnim="+PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION+" ownerGfx=NONE facingTemporary=true";
    }

    String devPetInteractionTarget(int rawTarget,ServerPacketWriter w)throws IOException{
        if(pet==null)return "REJECTED_NO_ACTIVE_PET";
        if(rawTarget<0||rawTarget>65535)return "REJECTED_TARGET_RANGE expected=0..65535";
        sendMask(pet,NpcSyncEncoder.Mask.interactionTarget(rawTarget),w);
        return "DEV_PET_OWNER_TARGET_OK npc="+pet.definitionId+" rawTarget="+rawTarget+" authority=TEMPORARY_SERVER_MASK";
    }

    String removePet(ServerPacketWriter w) throws IOException {
        if(pet==null) return "REJECTED_NO_ACTIVE_PET";
        NpcEntity oldPet=pet,oldMini=miniPet;
        ArrayList<NpcSyncEncoder.Update> updates=new ArrayList<>();
        for(NpcEntity n:visible) updates.add((n==oldPet||n==oldMini)?NpcSyncEncoder.Update.remove(n):NpcSyncEncoder.Update.retain(n));
        w.varShort(65,NpcSyncEncoder.encode(updates,Collections.emptyList(),0,0));
        visible.remove(oldPet); if(oldMini!=null)visible.remove(oldMini); pet=null; miniPet=null; ownerTrail.clear(); miniTrail.clear(); recentOwnerRunning=false;
        canonicalRemoveAll();
        return "PET_DESPAWN_OK item="+oldPet.petItemId+" npc="+oldPet.definitionId+" sceneIndex="+oldPet.sceneIndex+
            " miniRemoved="+(oldMini==null?"none":oldMini.sceneIndex);
    }

    /**
     * V9.12 authority: record the owner's vacated route tiles instead of replacing
     * follower guidance with one direct target. A two-tile owner RUN contributes
     * both vacated positions. Loop erasure handles U-turns without replaying a
     * stale branch.
     */
    void queueOwnerMovement(MovementState.Tick tick){
        if(tick==null) return;
        recentOwnerRunning=tick.running || tick.tiles>1;
        int finalDir=tick.dir2>=0?tick.dir2:tick.dir1;
        if(finalDir>=0) lastOwnerFacingDir=cardinalizeFacing(finalDir);
        int anchorX=tick.fromX,anchorY=tick.fromY;
        hasLastOwnerAnchor=true; lastOwnerAnchorX=anchorX; lastOwnerAnchorY=anchorY;
        if(pet==null) { suppressNextOwnerBreadcrumb=false; petDiscontinuityTicks=0; miniDiscontinuityTicks=0; return; }
        if(suppressNextOwnerBreadcrumb){
            // The first authoritative step after a replaced route vacates a tile on
            // the abandoned branch. Production/user runtime expects the follower to
            // hold instead of stepping back into that old branch.
            suppressNextOwnerBreadcrumb=false; petDiscontinuityTicks=0; miniDiscontinuityTicks=0;
            return;
        }

        enqueueOwnerBreadcrumb(tick.fromX,tick.fromY);
        if(tick.tiles>1 && tick.dir1>=0){
            int[] d=directionDelta(tick.dir1);
            int ix=tick.fromX+d[0], iy=tick.fromY+d[1];
            hasLastOwnerAnchor=true; lastOwnerAnchorX=ix; lastOwnerAnchorY=iy;
            enqueueOwnerBreadcrumb(ix,iy);
        }
    }

    /**
     * A new client movement intent replaces the owner's previous route. Production
     * and the earlier v5.12.3 runtime both show that a U-turn must not make the pet
     * replay the abandoned branch tile-by-tile. Keep the current V9.12 breadcrumb
     * train, but invalidate stale main/mini history at route replacement.
     */
    String onOwnerRouteReplaced(){
        int oldOwner=ownerTrail.size(),oldMini=miniTrail.size();
        ownerTrail.clear(); miniTrail.clear(); hasMiniTrail=false;
        // A replacement route invalidates stale future breadcrumbs, but the first
        // tile vacated on the NEW route is valid follower authority. R2.8's
        // suppress/hold let a running owner gain two tiles on every client route
        // chunk and the pet could never close that gap at the same 2-tile cadence.
        suppressNextOwnerBreadcrumb=false;
        return "PET_ROUTE_REPLACED staleOwnerBreadcrumbs="+oldOwner+" staleMiniBreadcrumbs="+oldMini+" firstNewTick=FOLLOW_NEW_ROUTE";
    }

    boolean hasQueuedFollow(){ return pet!=null && (!ownerTrail.isEmpty() || !miniTrail.isEmpty()); }

    /**
     * A follower still needs realtime pulses even after a broken breadcrumb queue
     * has been cleared. R2.10 used hasQueuedFollow() as the scheduler gate, which
     * made direct catch-up stop after a single pulse while the pet was still
     * several tiles behind.
     */
    boolean needsFollow(MovementState movement){
        refreshCanonicalActorProjections();
        if(pet==null||movement==null)return false;
        if(!ownerTrail.isEmpty()||!miniTrail.isEmpty())return true;
        if(LocalSession.chebyshev(pet.x,pet.y,movement.x(),movement.y())>1)return true;
        return miniPet!=null && LocalSession.chebyshev(miniPet.x,miniPet.y,pet.x,pet.y)>1;
    }

    /** Retain pulse for the ordinary 600ms NPC update cadence. */
    void retainPulse(ServerPacketWriter w) throws IOException {
        w.varShort(65,NpcSyncEncoder.encode(retains(),Collections.emptyList(),0,0));
    }

    /**
     * V9.12 follower train:
     *   owner vacated tiles -> main pet -> main-pet vacated tiles -> mini-pet.
     *
     * Packet 65 WALK/RUN still contains one/two cardinal component steps. Apparent
     * diagonal displacement is therefore a deterministic L-turn, never a literal
     * diagonal follower step. The old 50/50 X/Y alternator is intentionally gone.
     *
     * Breadcrumb presentation remains cardinal, while every follower catch-up
     * component is selected through the shared collision authority. When the old
     * deterministic X/Y component is blocked, the follower takes a cardinal detour.
     */
    String tickFollow(MovementState movement,ServerPacketWriter w) throws IOException {
        refreshCanonicalActorProjections();
        if(pet==null) return null;
        int dist=LocalSession.chebyshev(pet.x,pet.y,movement.x(),movement.y());

        // User-approved temporary LocalLab catch-up policy. This is explicitly not
        // production-proven V9.12 authority: >=8 tiles may reanchor, below 8 must
        // keep walking immediately rather than sitting in DISCONTINUITY_WAIT.
        if(dist>=8){
            String recovery=teleportBesideOwner(movement,w,dist);
            petDiscontinuityTicks=0; ownerTrail.clear();
            if(miniPet!=null) recovery += " "+reanchorMiniBesidePet(movement,w);
            return "PET_FOLLOW_TEMP_8_TILE_REANCHOR dist="+dist+" "+recovery+" policy=USER_APPROVED_TEMPORARY";
        }

        int petDir1=-1,petDir2=-1,petMoves=0;
        String petTrailState="OK";
        pruneReached(ownerTrail,pet);
        boolean directPetCatchup=false;
        if(!ownerTrail.isEmpty()){
            int[] first=ownerTrail.peekFirst();
            if(LocalSession.chebyshev(pet.x,pet.y,first[0],first[1])>1){
                if(discardUntilAdjacent(ownerTrail,pet)){
                    petTrailState="DISCONTINUITY_RECONNECTED"; petDiscontinuityTicks=0;
                } else {
                    ownerTrail.clear(); petDiscontinuityTicks=0; directPetCatchup=true;
                    petTrailState="BROKEN_TRAIL_DIRECT_CARDINAL_CATCHUP";
                }
            }
        } else if(dist>1){
            directPetCatchup=true;
            petTrailState="EMPTY_TRAIL_DIRECT_CARDINAL_CATCHUP";
        }

        if(directPetCatchup){
            int budget=(recentOwnerRunning||dist>2)?2:1;
            for(int i=0;i<budget;i++){
                if(LocalSession.chebyshev(pet.x,pet.y,movement.x(),movement.y())<=1)break;
                int dir=FollowerStepResolver.nextDirection(
                    pet.x,
                    pet.y,
                    movement.x(),
                    movement.y(),
                    1,
                    movement
                );
                if(dir<0)break;
                enqueueMiniBreadcrumb(pet.x,pet.y);
                applyPetDirection(pet,dir,true);
                if(petMoves==0)petDir1=dir;else petDir2=dir;
                petMoves++;
            }
        } else if(!ownerTrail.isEmpty()){
            int[] first=ownerTrail.peekFirst();
            int firstManhattan=Math.abs(first[0]-pet.x)+Math.abs(first[1]-pet.y);
            int budget=(recentOwnerRunning||ownerTrail.size()>1||firstManhattan>1||dist>2)?2:1;
            for(int i=0;i<budget;i++){
                pruneReached(ownerTrail,pet);
                if(ownerTrail.isEmpty())break;
                int[] target=ownerTrail.peekFirst();
                if(LocalSession.chebyshev(pet.x,pet.y,target[0],target[1])>1){
                    ownerTrail.clear();
                    petTrailState="DISCONTINUITY_DURING_STEP_DIRECT_NEXT_TICK";
                    break;
                }
                int dir=FollowerStepResolver.nextDirection(
                    pet.x,
                    pet.y,
                    target[0],
                    target[1],
                    0,
                    movement
                );
                if(dir<0)break;
                enqueueMiniBreadcrumb(pet.x,pet.y);
                applyPetDirection(pet,dir,true);
                if(petMoves==0)petDir1=dir;else petDir2=dir;
                petMoves++;
                if(pet.x==target[0]&&pet.y==target[1])ownerTrail.removeFirst();
            }
        }

        int miniDir1=-1,miniDir2=-1,miniMoves=0;
        String miniTrailState="OK";
        if(miniPet!=null){
            int miniDist=LocalSession.chebyshev(miniPet.x,miniPet.y,pet.x,pet.y);
            if(miniDist>=8){
                String recovery=reanchorMiniBesidePet(movement,w);
                miniDiscontinuityTicks=0;
                return "PET_FOLLOW_MINI_TEMP_8_TILE_REANCHOR dist="+miniDist+" "+recovery+" policy=USER_APPROVED_TEMPORARY";
            }
            pruneReached(miniTrail,miniPet);
            boolean directMiniCatchup=false;
            if(!miniTrail.isEmpty()){
                int[] first=miniTrail.peekFirst();
                if(LocalSession.chebyshev(miniPet.x,miniPet.y,first[0],first[1])>1){
                    if(discardUntilAdjacent(miniTrail,miniPet)){
                        miniTrailState="DISCONTINUITY_RECONNECTED";miniDiscontinuityTicks=0;
                    }else{
                        miniTrail.clear();miniDiscontinuityTicks=0;directMiniCatchup=true;
                        miniTrailState="BROKEN_TRAIL_DIRECT_CARDINAL_CATCHUP";
                    }
                }
            }else if(miniDist>1){
                directMiniCatchup=true;miniTrailState="EMPTY_TRAIL_DIRECT_CARDINAL_CATCHUP";
            }

            if(directMiniCatchup){
                int budget=(petMoves>0||miniDist>2)?2:1;
                for(int i=0;i<budget;i++){
                    if(LocalSession.chebyshev(miniPet.x,miniPet.y,pet.x,pet.y)<=1)break;
                    int dir=FollowerStepResolver.nextDirection(
                        miniPet.x,
                        miniPet.y,
                        pet.x,
                        pet.y,
                        1,
                        movement
                    );
                    if(dir<0)break;
                    applyPetDirection(miniPet,dir,false);
                    if(miniMoves==0)miniDir1=dir;else miniDir2=dir;
                    miniMoves++;
                }
            }else if(!miniTrail.isEmpty()){
                int[] first=miniTrail.peekFirst();
                int firstManhattan=Math.abs(first[0]-miniPet.x)+Math.abs(first[1]-miniPet.y);
                int budget=petMoves>0?petMoves:((miniTrail.size()>1||firstManhattan>1||miniDist>2)?2:1);
                budget=Math.max(1,Math.min(2,budget));
                for(int i=0;i<budget;i++){
                    pruneReached(miniTrail,miniPet);
                    if(miniTrail.isEmpty())break;
                    int[] target=miniTrail.peekFirst();
                    if(LocalSession.chebyshev(miniPet.x,miniPet.y,target[0],target[1])>1){miniTrail.clear();miniTrailState="DISCONTINUITY_DURING_STEP_DIRECT_NEXT_TICK";break;}
                    int dir=FollowerStepResolver.nextDirection(
                        miniPet.x,
                        miniPet.y,
                        target[0],
                        target[1],
                        0,
                        movement
                    );
                    if(dir<0)break;
                    applyPetDirection(miniPet,dir,false);
                    if(miniMoves==0)miniDir1=dir;else miniDir2=dir;
                    miniMoves++;
                    if(miniPet.x==target[0]&&miniPet.y==target[1])miniTrail.removeFirst();
                }
            }
            hasMiniTrail=!miniTrail.isEmpty();
            if(hasMiniTrail){int[] t=miniTrail.peekFirst();miniTrailX=t[0];miniTrailY=t[1];}
        }

        if(petDir1<0&&miniDir1<0)return null;
        ArrayList<NpcSyncEncoder.Update> updates=new ArrayList<>();
        for(NpcEntity n:visible){
            if(n==pet&&petDir1>=0)updates.add(petDir2>=0?NpcSyncEncoder.Update.run(n,petDir1,petDir2):NpcSyncEncoder.Update.walk(n,petDir1));
            else if(n==miniPet&&miniDir1>=0)updates.add(miniDir2>=0?NpcSyncEncoder.Update.run(n,miniDir1,miniDir2):NpcSyncEncoder.Update.walk(n,miniDir1));
            else updates.add(NpcSyncEncoder.Update.retain(n));
        }
        w.varShort(65,NpcSyncEncoder.encode(updates,Collections.emptyList(),0,0));
        return "PET_FOLLOW_CURRENT_ROUTE_CARDINAL_TRAIL_R28_FLUID sceneIndex="+pet.sceneIndex+" npc="+pet.definitionId+
            " distBefore="+dist+" movement="+(petDir2>=0?"RUN":petDir1>=0?"WALK":"RETAIN")+" dir1="+petDir1+" dir2="+petDir2+
            " world="+pet.x+","+pet.y+" owner="+movement.x()+","+movement.y()+" ownerTrail="+ownerTrail.size()+" petTrailState="+petTrailState+
            " mini="+(miniPet==null?"none":(miniPet.sceneIndex+"@"+miniPet.x+","+miniPet.y+" dir1="+miniDir1+" dir2="+miniDir2+" trail="+miniTrail.size()+" state="+miniTrailState))+
            " thresholdPolicy=TEMPORARY_8_TILE";
    }

    private static boolean discardUntilAdjacent(ArrayDeque<int[]> trail,NpcEntity actor){
        if(trail==null||actor==null)return false;
        Iterator<int[]> it=trail.iterator(); int keep=-1,idx=0;
        while(it.hasNext()){int[] t=it.next(); if(LocalSession.chebyshev(actor.x,actor.y,t[0],t[1])<=1){keep=idx;break;} idx++;}
        if(keep<0)return false;
        while(keep-->0)trail.removeFirst();
        return !trail.isEmpty();
    }

    /** Recovery only for an internally broken mini breadcrumb chain. Not a gameplay catch-up threshold. */
    private String reanchorMiniBesidePet(MovementState movement,ServerPacketWriter w)throws IOException{
        if(miniPet==null||pet==null)return "MINIPET_RECOVERY_NONE";
        NpcEntity old=miniPet;
        ArrayList<NpcSyncEncoder.Update> remove=new ArrayList<>();
        for(NpcEntity n:visible)remove.add(n==old?NpcSyncEncoder.Update.remove(n):NpcSyncEncoder.Update.retain(n));
        w.varShort(65,NpcSyncEncoder.encode(remove,Collections.emptyList(),0,0));
        visible.remove(old);
        int tx=pet.x-1,ty=pet.y;
        if(!MovementState.insideLoadedRegion(tx,ty)){tx=pet.x;ty=pet.y-1;}
        setCanonicalActorPosition(old,false,tx,ty);
        ArrayList<NpcSyncEncoder.Update> retained=new ArrayList<>();for(NpcEntity n:visible)retained.add(NpcSyncEncoder.Update.retain(n));
        w.varShort(65,NpcSyncEncoder.encode(retained,Collections.singletonList(old),movement.x(),movement.y()));
        visible.add(old);miniPet=old;miniTrail.clear();hasMiniTrail=false;
        sendMask(miniPet,NpcSyncEncoder.Mask.interactionTarget(pet.sceneIndex),w);
        return "MINIPET_BROKEN_TRAIL_REANCHOR scene="+old.sceneIndex+" world="+old.x+","+old.y+" mainPet="+pet.x+","+pet.y;
    }

    private boolean miniNeedsFollow(){return miniPet!=null&&pet!=null&&(!miniTrail.isEmpty()||LocalSession.chebyshev(miniPet.x,miniPet.y,pet.x,pet.y)>1);}

    private int[] miniTrailingTile(MovementState movement){
        int dx=Integer.compare(pet.x,movement.x()), dy=Integer.compare(pet.y,movement.y());
        int tx=pet.x,ty=pet.y;
        if(dx!=0||dy!=0){
            // Initial spawn placement may be diagonal, but follower movement itself
            // is always cardinalized in tickFollow().
            tx+=dx; ty+=dy;
        } else { tx=pet.x-1; }
        if(!MovementState.insideLoadedRegion(tx,ty)){
            tx=pet.x+1;ty=pet.y;
            if(!MovementState.insideLoadedRegion(tx,ty)){tx=pet.x;ty=pet.y-1;}
        }
        if(tx==pet.x&&ty==pet.y)tx=pet.x-1;
        return new int[]{tx,ty};
    }

    private static void pruneReached(ArrayDeque<int[]> trail,NpcEntity mover){
        while(!trail.isEmpty()){
            int[] h=trail.peekFirst();
            if(h[0]==mover.x&&h[1]==mover.y)trail.removeFirst(); else break;
        }
    }

    /** Normal gameplay cadence: one NPC walk step per 600 ms world tick. */
    long followDelayMs(MovementState movement){
        refreshCanonicalActorProjections();
        if(pet==null) return Long.MAX_VALUE;
        Long override=dev.petFollowDelayMs();
        if(override!=null)return override.longValue();
        // Ordinary follower cadence remains one NPC update per 600 ms. Only when
        // the main pet has already fallen >2 tiles behind do we temporarily pulse
        // at 300 ms so a 2-tile NPC RUN can actually close distance to a player
        // who is also running two tiles every 600 ms. At <=2 tiles, immediately
        // return to the normal cadence.
        int dist=LocalSession.chebyshev(pet.x,pet.y,movement.x(),movement.y());
        return dist>2 && dist<8?300L:600L;
    }

    boolean recentOwnerRunning(){ return recentOwnerRunning; }

    /**
     * WORLD HOME 600 ms pulse. HOME visibility/wander deltas and the v5.5 pet share
     * one coherent existing-list ordering. The pet is only RETAINED here; its follower
     * movement remains exclusively in tickFollow().
     */
    String tickHome(MovementState movement,ServerPacketWriter w,HomeWorldRuntimePlan home,long worldTick) throws IOException {
        if(home==null) throw new NullPointerException("home");
        HomeWorldRuntimePlan.NpcDelta wd=home.tick(worldTick,movement.x(),movement.y());
        ArrayList<NpcSyncEncoder.Update> updates=new ArrayList<>();
        for(NpcEntity n:visible){
            if(HomeWorldRuntimePlan.isHomeWorldSceneIndex(n.sceneIndex)){
                if(wd.shouldRemove(n.sceneIndex)){ updates.add(NpcSyncEncoder.Update.remove(n)); continue; }
                Integer d=wd.walkDirection(n.sceneIndex);
                if(d!=null){ applyDirection(n,d); updates.add(NpcSyncEncoder.Update.walk(n,d)); continue; }
            }
            // Dynamic pet and any other MAINLINE-owned actor are retained here.
            updates.add(NpcSyncEncoder.Update.retain(n));
        }
        ensureNoAddedSceneCollision(wd.added);
        w.varShort(65,NpcSyncEncoder.encode(updates,wd.added,movement.x(),movement.y()));
        applyWorldMembership(wd);
        assertUniqueSceneIndexes();
        return "HOME_NPC_PULSE tick="+worldTick+" worldAdd="+wd.added.size()+" worldRemove="+wd.removedSceneIndexes.size()+
            " worldWalk="+wd.walkDirectionBySceneIndex.size()+" pet="+(pet==null?"none":"RETAIN")+" visible="+visible.size();
    }

    private String teleportBesideOwner(MovementState movement,ServerPacketWriter w,int distBefore)throws IOException{
        // Prefer the most recent authoritative owner breadcrumb when it is adjacent
        // to the owner's current tile. That tile is known traversable because the
        // player just occupied it; only fall back to west-adjacent when no such
        // breadcrumb exists (e.g. a hard teleport/region relocation).
        int targetX=movement.x()-1,targetY=movement.y();
        int[] safe=ownerTrail.peekLast();
        if(safe!=null && LocalSession.chebyshev(safe[0],safe[1],movement.x(),movement.y())<=1){
            targetX=safe[0]; targetY=safe[1];
        }
        ownerTrail.clear(); miniTrail.clear();
        NpcEntity oldPet=pet;
        ArrayList<NpcSyncEncoder.Update> remove=new ArrayList<>();
        for(NpcEntity n:visible) remove.add(n==oldPet?NpcSyncEncoder.Update.remove(n):NpcSyncEncoder.Update.retain(n));
        w.varShort(65,NpcSyncEncoder.encode(remove,Collections.emptyList(),0,0));
        visible.remove(oldPet);
        setCanonicalActorPosition(
            oldPet,
            true,
            targetX,
            targetY
        );
        ArrayList<NpcSyncEncoder.Update> retained=new ArrayList<>();
        for(NpcEntity n:visible) retained.add(NpcSyncEncoder.Update.retain(n));
        w.varShort(65,NpcSyncEncoder.encode(retained,Collections.singletonList(oldPet),movement.x(),movement.y(),spawnPresentationFor(oldPet)));
        visible.add(oldPet); pet=oldPet;
        if(miniPet!=null) sendMask(miniPet,NpcSyncEncoder.Mask.interactionTarget(pet.sceneIndex),w);
        return "PET_TELEPORT_TO_OWNER sceneIndex="+pet.sceneIndex+" npc="+pet.definitionId+" distBefore="+distBefore+" world="+pet.x+","+pet.y+" owner="+movement.x()+","+movement.y()+" targetAuthority="+(safe!=null?"OWNER_CURRENT_ROUTE":"FALLBACK_ADJACENT");
    }

    boolean followFrozen(){ return dev.petFollowFrozen(); }

    String devSetParticleSelector(Integer selector,MovementState movement,ServerPacketWriter w)throws IOException{
        String result=
            publishPetParticleSelector(
                selector,
                movement,
                w
            );
        commitPetParticleSelector(
            selector
        );
        return result;
    }

    String publishPetParticleSelector(
        Integer selector,
        MovementState movement,
        ServerPacketWriter w
    )throws IOException{
        if(selector!=null&&
           (selector<0||selector>255))
            throw new IllegalArgumentException(
                "particle selector 0..255"
            );

        if(pet==null)
            return "DEV_PET_FX_SET value="+
                (selector==null?"AUTO":selector)+
                " activePet=none";

        NpcEntity currentPet=pet;
        ArrayList<NpcSyncEncoder.Update> remove=
            new ArrayList<>();

        for(NpcEntity n:visible)
            remove.add(
                n==currentPet
                    ?NpcSyncEncoder.Update.remove(n)
                    :NpcSyncEncoder.Update.retain(n)
            );

        w.varShort(
            65,
            NpcSyncEncoder.encode(
                remove,
                Collections.emptyList(),
                0,
                0
            )
        );

        ArrayList<NpcSyncEncoder.Update> retained=
            new ArrayList<>();

        for(NpcEntity n:visible)
            if(n!=currentPet)
                retained.add(
                    NpcSyncEncoder.Update.retain(n)
                );

        w.varShort(
            65,
            NpcSyncEncoder.encode(
                retained,
                Collections.singletonList(
                    currentPet
                ),
                movement.x(),
                movement.y(),
                spawnPresentationFor(
                    currentPet,
                    selector
                )
            )
        );

        if(miniPet!=null)
            sendMask(
                miniPet,
                NpcSyncEncoder.Mask
                    .interactionTarget(
                        currentPet.sceneIndex
                    ),
                w
            );

        return "DEV_PET_FX_SET value="+
            (selector==null?"AUTO":selector)+
            " item="+currentPet.petItemId+
            " npc="+currentPet.definitionId+
            " authority=TEMPORARY_OVERRIDE";
    }

    void commitPetParticleSelector(
        Integer selector
    ){
        dev.setPetParticleSelector(
            selector
        );
    }

    String devFollowFreeze(boolean freeze){
        dev.setPetFollowFrozen(freeze);
        return "DEV_PET_FOLLOW_"+(freeze?"FROZEN":"RESUMED")+" queued="+ownerTrail.size();
    }

    String devFollowDelay(Long delay){
        dev.setPetFollowDelayMs(delay);
        return "DEV_PET_FOLLOW_DELAY "+(delay==null?"NORMAL_600MS":delay+"ms")+" authority=TEMPORARY_OVERRIDE";
    }

    String devFollowStep(MovementState movement,ServerPacketWriter w)throws IOException{
        boolean frozen=dev.petFollowFrozen();
        dev.setPetFollowFrozen(false);
        try { String r=tickFollow(movement,w); return r==null?"DEV_PET_FOLLOW_STEP_IDLE":r; }
        finally { dev.setPetFollowFrozen(frozen); }
    }

    String devSnapToOwner(MovementState movement,ServerPacketWriter w)throws IOException{
        refreshCanonicalActorProjections();
        if(pet==null) return "REJECTED_NO_ACTIVE_PET";
        int dist=LocalSession.chebyshev(pet.x,pet.y,movement.x(),movement.y());
        return teleportBesideOwner(movement,w,dist);
    }

    String devInfo(MovementState movement){
        refreshCanonicalActorProjections();
        return "DEV_PET_INFO active="+(pet!=null)+" item="+(pet==null?-1:pet.petItemId)+" npc="+(pet==null?-1:pet.definitionId)+
            " pet="+(pet==null?"none":pet.x+","+pet.y)+" owner="+movement.x()+","+movement.y()+" queued="+ownerTrail.size()+
            " particle="+(dev.petParticleSelector()==null?"AUTO":dev.petParticleSelector())+" follow="+(dev.petFollowFrozen()?"FROZEN":"LIVE")+
            " delay="+(dev.petFollowDelayMs()==null?"NORMAL_600MS":dev.petFollowDelayMs()+"ms")+" mini="+(miniPet==null?"none":miniPet.sceneIndex+"/"+miniPet.definitionId+"@"+miniPet.x+","+miniPet.y)+" authority=TEMPORARY_OVERRIDE";
    }

    String devNpcList(int limit){
        int max=Math.max(1,Math.min(100,limit));
        StringBuilder sb=new StringBuilder("DEV_NPC_LIST count=").append(visible.size()).append(" showing=").append(Math.min(max,visible.size()));
        int n=0;
        for(NpcEntity e:visible){
            if(n++>=max) break;
            sb.append("\nscene=").append(e.sceneIndex)
              .append(" npc=").append(e.definitionId)
              .append(" world=").append(e.x).append(',').append(e.y)
              .append(" source=").append(devNpcSource(e))
              .append(" ownerTarget=").append(e.ownerPlayerIndex>=0?32768+e.ownerPlayerIndex:-1);
        }
        return sb.toString();
    }

    String devNpcInfo(int sceneIndex,MovementState movement){
        NpcEntity e=findScene(sceneIndex);
        if(e==null) return "DEV_NPC_INFO_NOT_FOUND scene="+sceneIndex;
        return "DEV_NPC_INFO scene="+e.sceneIndex+" npc="+e.definitionId+
            " world="+e.x+","+e.y+" owner="+movement.x()+","+movement.y()+
            " source="+devNpcSource(e)+" pet="+e.pet+" petItem="+e.petItemId+
            " ownerPlayerIndex="+e.ownerPlayerIndex+" devOwned="+devOwnedSceneIndexes.contains(sceneIndex);
    }

    String devSpawnNpc(int npcId,int dx,int dy,MovementState movement,ServerPacketWriter w)throws IOException{
        if(npcId<0||npcId>16383) return "DEV_NPC_SPAWN_REJECTED npcId=0..16383";
        if(dx<-16||dx>15||dy<-16||dy>15) return "DEV_NPC_SPAWN_REJECTED offsets=-16..15";
        int wx=movement.x()+dx,wy=movement.y()+dy;
        if(!MovementState.insideLoadedRegion(wx,wy)) return "DEV_NPC_SPAWN_REJECTED outside_loaded_region world="+wx+","+wy;
        int scene=allocateDevSceneIndex();
        if(scene<0) return "DEV_NPC_SPAWN_REJECTED no_free_scene_index";
        NpcEntity e=new NpcEntity(scene,npcId,wx,wy);
        w.varShort(65,NpcSyncEncoder.encode(retains(),Collections.singletonList(e),movement.x(),movement.y()));
        visible.add(e); devOwnedSceneIndexes.add(scene);
        dev.trace().record("NPC_DEV_SPAWN",
            "REQUEST=C2S103_DEV -> route=NpcDevService -> state=add(scene:"+scene+",npc:"+npcId+",world:"+wx+","+wy+") -> publish=S2C65_NEW_NPC -> result=VISIBLE",
            "EXACT_PACKET65_TRANSPORT/LOCAL_DEV_CONTENT");
        return "DEV_NPC_SPAWN_OK scene="+scene+" npc="+npcId+" world="+wx+","+wy+" persisted=false";
    }

    String devRemoveNpc(int sceneIndex,ServerPacketWriter w)throws IOException{
        if(!devOwnedSceneIndexes.contains(sceneIndex)) return "DEV_NPC_REMOVE_REJECTED scene="+sceneIndex+" reason=NOT_DEV_OWNED";
        NpcEntity target=findScene(sceneIndex);
        if(target==null){ devOwnedSceneIndexes.remove(sceneIndex); return "DEV_NPC_REMOVE_ALREADY_GONE scene="+sceneIndex; }
        ArrayList<NpcSyncEncoder.Update> updates=new ArrayList<>();
        for(NpcEntity n:visible) updates.add(n==target?NpcSyncEncoder.Update.remove(n):NpcSyncEncoder.Update.retain(n));
        w.varShort(65,NpcSyncEncoder.encode(updates,Collections.emptyList(),0,0));
        visible.remove(target); devOwnedSceneIndexes.remove(sceneIndex);
        dev.trace().record("NPC_DEV_REMOVE",
            "REQUEST=C2S103_DEV -> route=NpcDevService -> state=remove(scene:"+sceneIndex+") -> publish=S2C65_REMOVE -> result=REMOVED",
            "EXACT_PACKET65_TRANSPORT/LOCAL_DEV_CONTENT");
        return "DEV_NPC_REMOVE_OK scene="+sceneIndex+" npc="+target.definitionId;
    }

    String devRemoveAllNpcs(ServerPacketWriter w)throws IOException{
        if(devOwnedSceneIndexes.isEmpty()) return "DEV_NPC_REMOVE_ALL count=0";
        LinkedHashSet<Integer> copy=new LinkedHashSet<>(devOwnedSceneIndexes);
        ArrayList<NpcSyncEncoder.Update> updates=new ArrayList<>();
        int removed=0;
        for(NpcEntity n:visible){
            if(copy.contains(n.sceneIndex)){ updates.add(NpcSyncEncoder.Update.remove(n)); removed++; }
            else updates.add(NpcSyncEncoder.Update.retain(n));
        }
        if(removed>0) w.varShort(65,NpcSyncEncoder.encode(updates,Collections.emptyList(),0,0));
        visible.removeIf(n->copy.contains(n.sceneIndex));
        devOwnedSceneIndexes.clear();
        return "DEV_NPC_REMOVE_ALL count="+removed;
    }

    String devNpcAnimation(int sceneIndex,int animationId,int delay,ServerPacketWriter w)throws IOException{
        NpcEntity e=findScene(sceneIndex); if(e==null)return "DEV_NPC_ANIM_NOT_FOUND scene="+sceneIndex;
        if(animationId<-1||animationId>65535||delay<0||delay>255)return "DEV_NPC_ANIM_REJECTED_RANGE";
        sendMask(e,NpcSyncEncoder.Mask.animation(animationId,delay),w);
        dev.trace().record("NPC_ANIMATION","REQUEST=C2S103_DEV -> route=NpcDevService -> state=none -> publish=S2C65_MASK_0x10(scene:"+sceneIndex+",anim:"+animationId+")","EXACT_CURRENT_CLIENT");
        return "DEV_NPC_ANIM_OK scene="+sceneIndex+" npc="+e.definitionId+" anim="+animationId+" delay="+delay;
    }

    String devNpcGfx(int sceneIndex,int gfxId,int height,int delay,ServerPacketWriter w)throws IOException{
        NpcEntity e=findScene(sceneIndex); if(e==null)return "DEV_NPC_GFX_NOT_FOUND scene="+sceneIndex;
        try{ sendMask(e,NpcSyncEncoder.Mask.gfx(gfxId,height,delay),w); }
        catch(IllegalArgumentException ex){ return "DEV_NPC_GFX_REJECTED "+ex.getMessage(); }
        dev.trace().record("NPC_GFX","REQUEST=C2S103_DEV -> route=NpcDevService -> state=none -> publish=S2C65_MASK_0x80(scene:"+sceneIndex+",gfx:"+gfxId+")","EXACT_CURRENT_CLIENT");
        return "DEV_NPC_GFX_OK scene="+sceneIndex+" npc="+e.definitionId+" gfx="+gfxId+" height="+height+" delay="+delay;
    }

    String devNpcText(int sceneIndex,String text,ServerPacketWriter w)throws IOException{
        NpcEntity e=findScene(sceneIndex); if(e==null)return "DEV_NPC_TEXT_NOT_FOUND scene="+sceneIndex;
        if(text==null||text.isEmpty()) return "DEV_NPC_TEXT_REJECTED empty";
        sendMask(e,NpcSyncEncoder.Mask.forceText(text),w);
        dev.trace().record("NPC_FORCE_TEXT","REQUEST=C2S103_DEV -> route=NpcDevService -> state=none -> publish=S2C65_MASK_0x01(scene:"+sceneIndex+")","EXACT_CURRENT_CLIENT");
        return "DEV_NPC_TEXT_OK scene="+sceneIndex+" npc="+e.definitionId+" text="+text;
    }

    String devNpcTarget(int sceneIndex,int target,ServerPacketWriter w)throws IOException{
        NpcEntity e=findScene(sceneIndex); if(e==null)return "DEV_NPC_TARGET_NOT_FOUND scene="+sceneIndex;
        if(target<0||target>65535)return "DEV_NPC_TARGET_REJECTED target=0..65535";
        sendMask(e,NpcSyncEncoder.Mask.interactionTarget(target),w);
        dev.trace().record("NPC_INTERACTION_TARGET","REQUEST=C2S103_DEV -> route=NpcDevService -> state=none -> publish=S2C65_MASK_0x20(scene:"+sceneIndex+",target:"+target+")","EXACT_CURRENT_CLIENT");
        return "DEV_NPC_TARGET_OK scene="+sceneIndex+" npc="+e.definitionId+" target="+target;
    }

    String devNpcHit(int sceneIndex,int damage,int currentHp,int maxHp,ServerPacketWriter w)throws IOException{
        NpcEntity e=findScene(sceneIndex); if(e==null)return "DEV_NPC_HIT_NOT_FOUND scene="+sceneIndex;
        try{ sendMask(e,NpcSyncEncoder.Mask.singleHit(damage,0,currentHp,maxHp),w); }
        catch(IllegalArgumentException ex){return "DEV_NPC_HIT_REJECTED "+ex.getMessage();}
        dev.trace().record("NPC_SINGLE_HIT","REQUEST=C2S103_DEV -> route=NpcDevService -> state=none -> publish=S2C65_MASK_0x40(scene:"+sceneIndex+",damage:"+damage+")","EXACT_CURRENT_CLIENT_FIXTURE");
        return "DEV_NPC_HIT_OK scene="+sceneIndex+" npc="+e.definitionId+" damage="+damage+" hp="+currentHp+"/"+maxHp;
    }

    private int allocateDynamicSceneIndex(){
        for(int i=16382;i>=5;i--)if(findScene(i)==null)return i; return -1;
    }
    private int allocateDevSceneIndex(){ return allocateDynamicSceneIndex(); }

    private String devNpcSource(NpcEntity e){
        if(e==pet) return "PET";
        if(e==miniPet) return "MINIPET";
        if(devOwnedSceneIndexes.contains(e.sceneIndex)) return "DEV";
        if(HomeWorldRuntimePlan.isHomeWorldSceneIndex(e.sceneIndex)) return "HOME";
        return "MAIN";
    }

    private void respawnPetSameTile(MovementState movement,ServerPacketWriter w)throws IOException{
        NpcEntity oldPet=pet;
        ArrayList<NpcSyncEncoder.Update> remove=new ArrayList<>();
        for(NpcEntity n:visible) remove.add(n==oldPet?NpcSyncEncoder.Update.remove(n):NpcSyncEncoder.Update.retain(n));
        w.varShort(65,NpcSyncEncoder.encode(remove,Collections.emptyList(),0,0));
        visible.remove(oldPet);
        ArrayList<NpcSyncEncoder.Update> retained=retains();
        w.varShort(65,NpcSyncEncoder.encode(retained,Collections.singletonList(oldPet),movement.x(),movement.y(),spawnPresentationFor(oldPet)));
        visible.add(oldPet); pet=oldPet;
        if(miniPet!=null) sendMask(miniPet,NpcSyncEncoder.Mask.interactionTarget(pet.sceneIndex),w);
    }

    private Map<Integer,NpcSpawnPresentation> spawnPresentationFor(NpcEntity n){
        return spawnPresentationFor(
            n,
            dev.petParticleSelector()
        );
    }

    private Map<Integer,NpcSpawnPresentation> spawnPresentationFor(
        NpcEntity n,
        Integer selector
    ){
        if(n==null || !n.pet || selector==null)
            return Collections.emptyMap();

        return Collections.singletonMap(
            n.sceneIndex,
            NpcSpawnPresentation.particle(
                selector
            )
        );
    }

    private void enqueueTrail(int x,int y){ enqueueOwnerBreadcrumb(x,y); }

    private void enqueueOwnerBreadcrumb(int x,int y){
        if(pet!=null && pet.x==x && pet.y==y){ ownerTrail.clear(); return; }
        if(loopErase(ownerTrail,x,y))return;
        ownerTrail.addLast(new int[]{x,y});
        while(ownerTrail.size()>64)ownerTrail.removeFirst();
    }

    private void enqueueMiniBreadcrumb(int x,int y){
        if(miniPet==null)return;
        if(miniPet.x==x && miniPet.y==y){ miniTrail.clear(); return; }
        if(loopErase(miniTrail,x,y))return;
        miniTrail.addLast(new int[]{x,y});
        while(miniTrail.size()>64)miniTrail.removeFirst();
    }

    /**
     * If a route folds back onto a queued tile, discard the branch after that tile.
     * This is deterministic U-turn handling rather than alternating X/Y guesses.
     */
    private static boolean loopErase(ArrayDeque<int[]> trail,int x,int y){
        if(trail.isEmpty())return false;
        int match=-1,i=0;
        for(int[] t:trail){if(t[0]==x&&t[1]==y)match=i;i++;}
        if(match<0)return false;
        while(trail.size()>match+1)trail.removeLast();
        return true;
    }

    private static int cardinalizeFacing(int dir){
        switch(dir){
            case 0:return 1; // NW-looking -> north cardinal egress
            case 2:return 1; // NE-looking -> north
            case 5:return 6; // SW-looking -> south
            case 7:return 6; // SE-looking -> south
            case 1:case 3:case 4:case 6:return dir;
            default:return 6;
        }
    }

    private static int[] directionDelta(int dir){
        switch(dir){
            case 0:return new int[]{-1,1}; case 1:return new int[]{0,1}; case 2:return new int[]{1,1};
            case 3:return new int[]{-1,0}; case 4:return new int[]{1,0};
            case 5:return new int[]{-1,-1}; case 6:return new int[]{0,-1}; case 7:return new int[]{1,-1};
            default:throw new IllegalArgumentException("dir="+dir);
        }
    }

    private void applyWorldMembership(HomeWorldRuntimePlan.NpcDelta wd){
        if(!wd.removedSceneIndexes.isEmpty())
            visible.removeIf(n->HomeWorldRuntimePlan.isHomeWorldSceneIndex(n.sceneIndex)&&wd.shouldRemove(n.sceneIndex));
        for(NpcEntity n:wd.added) visible.add(n);
    }

    private void ensureNoAddedSceneCollision(List<NpcEntity> added){
        for(NpcEntity a:added){
            for(NpcEntity n:visible){
                if(n.sceneIndex==a.sceneIndex && !HomeWorldRuntimePlan.isHomeWorldSceneIndex(n.sceneIndex))
                    throw new IllegalStateException("HOME add collides with dynamic scene index "+a.sceneIndex);
            }
        }
    }

    private NpcEntity findScene(int scene){
        for(NpcEntity n:visible) if(n.sceneIndex==scene) return n;
        return null;
    }

    private void assertUniqueSceneIndexes(){
        HashSet<Integer> seen=new HashSet<>();
        for(NpcEntity n:visible)
            if(!seen.add(n.sceneIndex)) throw new IllegalStateException("duplicate visible NPC scene index "+n.sceneIndex);
    }

    private static void applyDirection(NpcEntity n,int d){
        switch(d){
            case 0: n.x--; n.y++; break;
            case 1: n.y++; break;
            case 2: n.x++; n.y++; break;
            case 3: n.x--; break;
            case 4: n.x++; break;
            case 5: n.x--; n.y--; break;
            case 6: n.y--; break;
            case 7: n.x++; n.y--; break;
            default: throw new IllegalArgumentException("direction "+d);
        }
    }

    EntityId canonicalPetId(){
        if(pet!=null&&pet.canonicalId()!=null)
            return pet.canonicalId();
        if(worldPets==null||canonicalOwnerId==null)
            return null;
        WorldNpc canonical=worldPets.main(canonicalOwnerId);
        return canonical==null?null:canonical.id;
    }

    EntityId canonicalMiniPetId(){
        if(miniPet!=null&&miniPet.canonicalId()!=null)
            return miniPet.canonicalId();
        if(worldPets==null||canonicalOwnerId==null)
            return null;
        WorldNpc canonical=worldPets.mini(canonicalOwnerId);
        return canonical==null?null:canonical.id;
    }

    private void canonicalEnsureMain(int sourceItemId){
        if(worldPets==null||canonicalOwnerId==null||pet==null)
            return;
        WorldNpc canonical=worldPets.ensureMain(
            canonicalOwnerId,
            pet.definitionId,
            sourceItemId,
            pet.x,
            pet.y,
            0
        );
        pet.bindCanonicalId(canonical.id);
    }

    private void canonicalEnsureMini(int sourceItemId){
        if(worldPets==null||canonicalOwnerId==null||miniPet==null)
            return;
        WorldNpc canonical=worldPets.ensureMini(
            canonicalOwnerId,
            miniPet.definitionId,
            sourceItemId,
            miniPet.x,
            miniPet.y,
            0
        );
        miniPet.bindCanonicalId(canonical.id);
    }

    private void refreshCanonicalActorProjections(){
        if(worldPets==null||canonicalOwnerId==null)
            return;

        if(pet!=null){
            WorldNpc canonical=
                worldPets.main(canonicalOwnerId);
            if(canonical!=null){
                pet.bindCanonicalId(canonical.id);
                pet.x=canonical.x();
                pet.y=canonical.y();
            }
        }

        if(miniPet!=null){
            WorldNpc canonical=
                worldPets.mini(canonicalOwnerId);
            if(canonical!=null){
                miniPet.bindCanonicalId(canonical.id);
                miniPet.x=canonical.x();
                miniPet.y=canonical.y();
            }
        }
    }

    private void applyPetDirection(
        NpcEntity actor,
        int direction,
        boolean main
    ){
        if(actor==null)
            throw new NullPointerException("actor");

        if(worldPets==null||canonicalOwnerId==null){
            applyDirection(actor,direction);
            return;
        }

        WorldNpc canonical=
            main
                ?worldPets.main(canonicalOwnerId)
                :worldPets.mini(canonicalOwnerId);

        if(canonical==null)
            throw new IllegalStateException(
                (main?"main":"mini")+
                " pet projection has no canonical World actor"
            );

        actor.bindCanonicalId(canonical.id);

        int[] delta=directionDelta(direction);
        int targetX=canonical.x()+delta[0];
        int targetY=canonical.y()+delta[1];

        WorldNpc moved=
            main
                ?worldPets.moveMain(
                    canonicalOwnerId,
                    targetX,
                    targetY,
                    canonical.plane()
                )
                :worldPets.moveMini(
                    canonicalOwnerId,
                    targetX,
                    targetY,
                    canonical.plane()
                );

        if(moved==null)
            throw new IllegalStateException(
                (main?"main":"mini")+
                " canonical movement target disappeared"
            );

        actor.x=moved.x();
        actor.y=moved.y();
    }

    private void setCanonicalActorPosition(
        NpcEntity actor,
        boolean main,
        int x,
        int y
    ){
        if(actor==null)
            throw new NullPointerException("actor");

        if(worldPets==null||canonicalOwnerId==null){
            actor.x=x;
            actor.y=y;
            return;
        }

        WorldNpc canonical=
            main
                ?worldPets.main(canonicalOwnerId)
                :worldPets.mini(canonicalOwnerId);

        if(canonical==null)
            throw new IllegalStateException(
                (main?"main":"mini")+
                " pet projection has no canonical World actor"
            );

        WorldNpc moved=
            main
                ?worldPets.moveMain(
                    canonicalOwnerId,
                    x,
                    y,
                    canonical.plane()
                )
                :worldPets.moveMini(
                    canonicalOwnerId,
                    x,
                    y,
                    canonical.plane()
                );

        if(moved==null)
            throw new IllegalStateException(
                (main?"main":"mini")+
                " canonical actor disappeared during reanchor"
            );

        actor.bindCanonicalId(moved.id);
        actor.x=moved.x();
        actor.y=moved.y();
    }

    private void canonicalRemoveMini(){
        if(worldPets==null||canonicalOwnerId==null)
            return;
        worldPets.removeMini(canonicalOwnerId);
    }

    private void canonicalRemoveAll(){
        if(worldPets==null||canonicalOwnerId==null)
            return;
        worldPets.removeMainAndMini(canonicalOwnerId);
    }

    private ArrayList<NpcSyncEncoder.Update> retains(){
        ArrayList<NpcSyncEncoder.Update> x=new ArrayList<>();
        for(NpcEntity n:visible)x.add(NpcSyncEncoder.Update.retain(n));
        return x;
    }
}
