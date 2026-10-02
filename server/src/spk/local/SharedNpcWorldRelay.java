package spk.local;

import java.io.IOException;
import java.util.*;

/**
 * Engine R3.2 world-visible NPC presentation bus.
 *
 * LocalSession/NpcRegistry remain the exact current-client packet-65 owners, but
 * any semantic NPC mask emitted by the owner is projected to the other viewers
 * in the same shared World.  Remote pet actors are mirrored with their complete
 * add-time presentation (particle selector) and current native state.
 *
 * Cross-viewer mask delivery is barriered behind the source player's packet-81
 * event sequence.  This preserves attack presentation ordering:
 *
 *   remote player swing/facing (81) -> target hitsplat/HP (65)
 *
 * instead of allowing the hit to overtake the attack animation.
 */
final class SharedNpcWorldRelay {
    private static final IdentityHashMap<ServerPacketWriter,Context> BY_WRITER=new IdentityHashMap<>();
    private static final IdentityHashMap<World,WorldState> BY_WORLD=new IdentityHashMap<>();

    private SharedNpcWorldRelay(){}

    static synchronized void register(ServerPacketWriter writer,World world,WorldPlayer owner,NpcRegistry npcs,MovementState movement){
        if(writer==null||world==null||owner==null||npcs==null||movement==null)return;

        if(world.closed())
            throw new IllegalStateException(
                "cannot register SharedNpcWorldRelay on closed World"
            );

        Context oldWriter=BY_WRITER.remove(writer);
        if(oldWriter!=null)
            cleanupContext(oldWriter);

        WorldState existingState=
            BY_WORLD.get(world);
        Context oldOwner=
            existingState==null
                ?null
                :existingState.contexts.get(
                    owner.id()
                );

        if(oldOwner!=null){
            BY_WRITER.remove(
                oldOwner.writer
            );
            cleanupContext(oldOwner);
        }

        WorldState ws=BY_WORLD.get(world);
        if(ws==null){
            ws=new WorldState(world);
            BY_WORLD.put(world,ws);
        }

        Context c=new Context(writer,ws,owner,npcs,movement);BY_WRITER.put(writer,c);ws.contexts.put(owner.id(),c);
    }

    static synchronized void unregister(ServerPacketWriter writer){
        Context c=BY_WRITER.remove(writer);if(c==null)return;
        cleanupContext(c);
    }

    private static void cleanupContext(Context c){
        c.state.contexts.remove(c.owner.id());

        if(c.state.world.closed()){
            /*
             * World.close() has crossed the terminal publication boundary.
             * Retire viewer-local bookkeeping only: live-session cleanup below
             * can emit packet-65 removals and mutate presentation queues, which
             * is no longer valid once the World is terminal.
             */
            c.remote.clear();
            c.remoteIndexes.clear();
            c.genericNpcs.clear();
            c.genericIndexes.clear();

            if(c.state.contexts.isEmpty()&&
               c.state.genericNpcIds.isEmpty())
                BY_WORLD.remove(c.state.world);
            return;
        }

        c.state.world
            .npcPresentationEvents()
            .removeSource(
                c.owner.id(),
                System.currentTimeMillis()
            );

        try{c.removeAllRemotePets();}catch(Throwable ignored){}
        try{c.removeAllGenericNpcs();}catch(Throwable ignored){}
        c.state.pruneDeadRecipients();
        if(c.state.contexts.isEmpty()&&
           c.state.genericNpcIds.isEmpty())
            BY_WORLD.remove(c.state.world);
    }

    static synchronized void trackCanonicalNpc(
        World world,
        WorldNpc npc
    ){
        World checkedWorld=
            Objects.requireNonNull(
                world,
                "world"
            );
        WorldNpc checkedNpc=
            Objects.requireNonNull(
                npc,
                "npc"
            );

        if(checkedWorld.closed())
            throw new IllegalStateException(
                "cannot track canonical NPC on closed World"
            );

        if(checkedWorld.npcs().byId(
                checkedNpc.id
            )!=checkedNpc)
            throw new IllegalArgumentException(
                "canonical NPC is not owned by World id="+
                checkedNpc.id
            );

        if(checkedNpc.ownerId!=null)
            throw new IllegalArgumentException(
                "owned pet/mini NPC must use existing relay id="+
                checkedNpc.id
            );

        if(checkedWorld.homeNpcs()
                .ownsCanonical(
                    checkedNpc.id
                ))
            throw new IllegalArgumentException(
                "HOME NPC must use HomeWorldRuntimePlan id="+
                checkedNpc.id
            );

        WorldState state=
            BY_WORLD.get(
                checkedWorld
            );

        if(state==null){
            state=
                new WorldState(
                    checkedWorld
                );
            BY_WORLD.put(
                checkedWorld,
                state
            );
        }

        state.genericNpcIds.add(
            checkedNpc.id
        );
    }

    static synchronized void closeWorld(
        World world
    ){
        if(world==null)
            return;

        WorldState state=
            BY_WORLD.remove(
                world
            );

        if(state==null)
            return;

        Iterator<Map.Entry<ServerPacketWriter,Context>>
            writers=
                BY_WRITER.entrySet()
                    .iterator();

        while(writers.hasNext()){
            Map.Entry<ServerPacketWriter,Context>
                entry=
                    writers.next();

            if(entry.getValue().state==state)
                writers.remove();
        }

        state.contexts.clear();
        state.genericNpcIds.clear();
    }

    static synchronized boolean untrackCanonicalNpc(
        World world,
        EntityId npcId
    ){
        if(world==null||npcId==null)
            return false;

        WorldState state=
            BY_WORLD.get(
                world
            );

        if(state==null)
            return false;

        boolean removed=
            state.genericNpcIds.remove(
                npcId
            );

        if(state.contexts.isEmpty()&&
           state.genericNpcIds.isEmpty())
            BY_WORLD.remove(
                world
            );

        return removed;
    }

    static void syncRemotePets(
        ServerPacketWriter viewerWriter
    ){
        final Context candidate;

        synchronized(SharedNpcWorldRelay.class){
            candidate=
                BY_WRITER.get(
                    viewerWriter
                );
        }

        if(candidate==null)
            return;

        try{
            candidate.state.world
                .withOpenPlayerOwnershipIfCurrent(
                    candidate.owner,
                    candidate.ownerGeneration,
                    ()->{
                        synchronized(
                            SharedNpcWorldRelay.class
                        ){
                            if(BY_WRITER.get(
                                    viewerWriter
                                )!=candidate||
                               candidate.state.contexts.get(
                                    candidate.owner.id()
                               )!=candidate)
                                return;

                            candidate.syncRemotePets();
                            if(candidate.pendingMirrorMasks.isEmpty())
                                candidate.syncCanonicalNpcs();
                        }
                    }
                );
        }catch(Throwable t){
            System.err.println(
                "[ENGINE-R3.2] remote pet sync failed viewer="+
                candidate.owner.id()+": "+t
            );
        }
    }

    /**
     * Called by NpcRegistry.sendMask after the owner-local packet has been emitted.
     * The semantic mask is queued once and translated against each viewer's scene
     * index when that viewer reaches the corresponding player-presentation barrier.
     */
    static void relayMask(ServerPacketWriter sourceWriter,NpcRegistry sourceNpcs,NpcEntity sourceTarget,NpcSyncEncoder.Mask mask){
        if(sourceWriter==null||sourceNpcs==null||sourceTarget==null||mask==null)return;

        final Context candidate;

        synchronized(SharedNpcWorldRelay.class){
            candidate=BY_WRITER.get(sourceWriter);
        }

        if(candidate==null)return;

        candidate.state.world.runIfOpen(
            ()->{
                synchronized(SharedNpcWorldRelay.class){
                    Context src=
                        BY_WRITER.get(sourceWriter);

                    if(src!=candidate)
                        return;

                    if(!src.state.world.players().owns(
                            src.owner,
                            src.ownerGeneration
                        ))
                        return;

                    WorldNpcPresentationEvents.Target target;
                    if(sourceTarget.canonicalId()!=null)
                        target=WorldNpcPresentationEvents.Target.canonical(
                            sourceTarget.canonicalId(),
                            sourceTarget.definitionId
                        );
                    else if(sourceTarget==sourceNpcs.pet())
                        target=WorldNpcPresentationEvents.Target.pet(
                            sourceNpcs.canonicalPetId(),
                            sourceTarget.definitionId
                        );
                    else if(sourceTarget==sourceNpcs.miniPet())
                        target=WorldNpcPresentationEvents.Target.mini(
                            sourceNpcs.canonicalMiniPetId(),
                            sourceTarget.definitionId
                        );
                    else
                        target=WorldNpcPresentationEvents.Target.scene(
                            sourceTarget.sceneIndex,
                            sourceTarget.definitionId
                        );

                    long barrier=
                        Player81WorldSync.latestPublishedEventSequence(
                            sourceWriter
                        );

                    LinkedHashMap<EntityId,Long> recipients=
                        new LinkedHashMap<>();

                    for(Context context:
                            src.state.contexts.values())
                        if(context!=src&&
                           !context.projectionTransportFailedClosed&&
                           context.ownerCurrent()&&
                           !context.owner.id().equals(
                               src.owner.id()
                           ))
                            recipients.put(
                                context.owner.id(),
                                context.ownerGeneration
                            );

                    if(recipients.isEmpty())
                        return;

                    src.state.world
                        .npcPresentationEvents()
                        .enqueueOwned(
                            System.currentTimeMillis(),
                            src.owner.id(),
                            src.ownerGeneration,
                            target,
                            mask,
                            barrier,
                            recipients
                        );
                }
            }
        );
    }

    /**
     * Invoked immediately after a viewer's packet-81 body is appended to the
     * outbound stream.  Any NPC masks whose source-player barrier has now been
     * consumed are appended after it, preserving visible event order.
     */
    static void flushAfterPlayer81(
        ServerPacketWriter viewerWriter
    )throws IOException{
        Context viewer;
        synchronized(SharedNpcWorldRelay.class){
            viewer=BY_WRITER.get(viewerWriter);
        }
        if(viewer==null)
            return;

        viewer.state.world
            .withOpenPlayerOwnershipIfCurrent(
                viewer.owner,
                viewer.ownerGeneration,
                ()->flushCurrentViewer(
                    viewerWriter,
                    viewer
                )
            );
    }

    private static void flushCurrentViewer(
        ServerPacketWriter viewerWriter,
        Context viewer
    )throws IOException{
        /*
         * Exact relay context identity is part of the delivery authority.
         * register()/unregister() use this same monitor, so retain it across
         * resolve -> packet65 publication -> markDelivered. Retirement/rebind
         * therefore linearizes before this delivery or after it, never through
         * an in-flight event commit.
         *
         * World/player ownership is acquired before entering this method, and
         * packet publication acquires the writer only after this registry
         * monitor. No writer-held path enters SharedNpcWorldRelay.
         */
        synchronized(SharedNpcWorldRelay.class){
            if(BY_WRITER.get(viewerWriter)!=
                    viewer||
               viewer.state.contexts.get(
                    viewer.owner.id()
                )!=viewer||
               viewer.projectionTransportFailedClosed)
                return;

            long now=System.currentTimeMillis();

            List<WorldNpcPresentationEvents.Event> pending=
                viewer.state.world
                    .npcPresentationEvents()
                    .pendingFor(
                        viewer.owner.id(),
                        viewer.ownerGeneration,
                        now
                    );

            for(WorldNpcPresentationEvents.Event event:
                pending){
                if(!sourceCurrent(
                        viewer,
                        event
                    )){
                    if(event.sourceGeneration>=0L)
                        viewer.state.world
                            .npcPresentationEvents()
                            .removeSourceGeneration(
                                event.sourceId,
                                event.sourceGeneration,
                                now
                            );
                    continue;
                }

                long consumed=
                    event.sourceGeneration>=0L
                        ?Player81WorldSync
                            .consumedEventSequence(
                                viewerWriter,
                                event.sourceId,
                                event.sourceGeneration
                            )
                        :Player81WorldSync
                            .consumedEventSequence(
                                viewerWriter,
                                event.sourceId
                            );

                if(event.playerBarrierSequence>0&&
                   consumed<
                        event.playerBarrierSequence)
                    continue;

                NpcEntity target=
                    viewer.resolve(
                        event.sourceId,
                        event.sourceGeneration,
                        event.target
                    );

                if(target==null)
                    continue;

                viewer.npcs.sendMaskLocal(
                    target,
                    event.mask,
                    viewer.writer
                );

                viewer.state.world
                    .npcPresentationEvents()
                    .markDelivered(
                        event.sequence,
                        viewer.owner.id(),
                        viewer.ownerGeneration,
                        now
                    );
            }
        }
    }


    private static boolean sourceCurrent(
        Context viewer,
        WorldNpcPresentationEvents.Event event
    ){
        if(event.sourceGeneration<0L)
            return true;

        Context source;

        synchronized(SharedNpcWorldRelay.class){
            source=
                viewer.state.contexts.get(
                    event.sourceId
                );
        }

        if(source==null||
           source.ownerGeneration!=
                event.sourceGeneration||
           !source.ownerCurrent())
            return false;

        synchronized(SharedNpcWorldRelay.class){
            return viewer.state.contexts.get(
                    event.sourceId
                )==source&&
                source.ownerGeneration==
                    event.sourceGeneration;
        }
    }


    private static final class WorldState{
        final World world;
        final HashMap<EntityId,Context> contexts=
            new HashMap<>();
        final LinkedHashSet<EntityId> genericNpcIds=
            new LinkedHashSet<>();

        WorldState(World world){
            this.world=world;
        }

        void pruneDeadRecipients(){
            LinkedHashMap<EntityId,Long> live=
                new LinkedHashMap<>();

            for(Context context:
                    contexts.values())
                if(context.ownerCurrent())
                    live.put(
                        context.owner.id(),
                        context.ownerGeneration
                    );

            world.npcPresentationEvents()
                .retainRecipientsOwned(
                    live,
                    System.currentTimeMillis()
                );
        }
    }

    private static final class RemotePetTrack{
        final long sourceGeneration;
        int mainScene=-1,miniScene=-1,mainDef=-1,miniDef=-1;
        int mainX,mainY,miniX,miniY;
        EntityId mainCanonicalId,miniCanonicalId;
        Integer mainParticleSelector;

        RemotePetTrack(
            long sourceGeneration
        ){
            this.sourceGeneration=
                sourceGeneration;
        }
    }

    private static final class PendingMirrorMask{
        final EntityId sourceId;
        final long sourceGeneration;
        final NpcEntity npc;
        final NpcSyncEncoder.Mask mask;

        PendingMirrorMask(
            EntityId sourceId,
            long sourceGeneration,
            NpcEntity npc,
            NpcSyncEncoder.Mask mask
        ){
            this.sourceId=sourceId;
            this.sourceGeneration=sourceGeneration;
            this.npc=npc;
            this.mask=mask;
        }
    }

    private static final class GenericNpcTrack{
        int scene=-1;
        int definition=-1;
        int x;
        int y;
    }

    private static final class Context{
        final ServerPacketWriter writer;final WorldState state;final WorldPlayer owner;final NpcRegistry npcs;final MovementState movement;
        final long ownerGeneration;
        final HashMap<EntityId,RemotePetTrack> remote=new HashMap<>();
        final NpcViewIndexMap remoteIndexes=new NpcViewIndexMap();
        final HashMap<EntityId,GenericNpcTrack> genericNpcs=
            new HashMap<>();
        final NpcViewIndexMap genericIndexes=
            new NpcViewIndexMap();
        final ArrayDeque<PendingMirrorMask>
            pendingMirrorMasks=
                new ArrayDeque<>();
        boolean projectionTransportFailedClosed;
        Context(ServerPacketWriter w,WorldState s,WorldPlayer o,NpcRegistry n,MovementState m){
            writer=w;
            state=s;
            owner=o;
            npcs=n;
            movement=m;
            ownerGeneration=o.generation();
        }

        boolean ownerCurrent(){
            return state.world.players().owns(
                owner,
                ownerGeneration
            );
        }

        private static final int RETRY_SCENE=
            Integer.MIN_VALUE;

        private void failCloseProjection(){
            projectionTransportFailedClosed=true;
            pendingMirrorMasks.clear();
        }

        private NpcEntity spawnMirror(
            int definition,
            int x,
            int y,
            Integer particleSelector
        )throws IOException{
            try{
                return npcs.spawnMirroredNpc(
                    definition,
                    x,
                    y,
                    particleSelector,
                    movement,
                    writer
                );
            }catch(IOException terminal){
                failCloseProjection();
                throw terminal;
            }
        }

        private ServerPacketWriter.RecoverablePacketResult
            moveMirror(
                NpcEntity target,
                int direction1,
                int direction2,
                int x,
                int y
            )throws IOException
        {
            try{
                return npcs.moveMirroredNpcRetractable(
                    target,
                    direction1,
                    direction2,
                    x,
                    y,
                    writer
                );
            }catch(IOException terminal){
                failCloseProjection();
                throw terminal;
            }
        }

        private ServerPacketWriter.RecoverablePacketResult
            removeMirror(
                int scene
            )throws IOException
        {
            try{
                return npcs.removeMirroredNpcRetractable(
                    scene,
                    writer
                );
            }catch(IOException terminal){
                failCloseProjection();
                throw terminal;
            }
        }

        void syncRemotePets()throws IOException{
            if(projectionTransportFailedClosed||
               !ownerCurrent())
                return;

            flushPendingMirrorMasks();

            if(!pendingMirrorMasks.isEmpty()||
               projectionTransportFailedClosed)
                return;

            ArrayList<Context> sources;
            synchronized(SharedNpcWorldRelay.class){
                sources=new ArrayList<>(
                    state.contexts.values()
                );
            }

            HashSet<EntityId> live=new HashSet<>();

            for(Context src:sources){
                if(src==this||
                   !src.ownerCurrent())
                    continue;

                int playerIndex=
                    Player81WorldSync.clientIndexFor(
                        writer,
                        src.owner
                    );

                if(playerIndex<0){
                    if(!removeRemote(src.owner.id()))
                        return;
                    continue;
                }

                WorldNpc canonicalPet=
                    state.world.petNpcs().main(
                        src.owner.id()
                    );
                WorldNpc canonicalMini=
                    state.world.petNpcs().mini(
                        src.owner.id()
                    );

                NpcEntity fallbackPet=src.npcs.pet();
                NpcEntity fallbackMini=src.npcs.miniPet();

                boolean canonicalSource=
                    canonicalPet!=null;

                if(canonicalPet==null&&
                   fallbackPet==null){
                    if(!removeRemote(src.owner.id()))
                        return;
                    continue;
                }

                int petDef=canonicalSource
                    ?canonicalPet.definitionId
                    :fallbackPet.definitionId;
                int petX=canonicalSource
                    ?canonicalPet.x()
                    :fallbackPet.x;
                int petY=canonicalSource
                    ?canonicalPet.y()
                    :fallbackPet.y;
                EntityId petCanonicalId=canonicalSource
                    ?canonicalPet.id
                    :null;

                boolean canonicalMiniSource=
                    canonicalMini!=null;
                boolean miniPresent=
                    canonicalMiniSource||
                    (!canonicalSource&&fallbackMini!=null);

                int miniDef=miniPresent
                    ?(canonicalMiniSource
                        ?canonicalMini.definitionId
                        :fallbackMini.definitionId)
                    :-1;
                int miniX=miniPresent
                    ?(canonicalMiniSource
                        ?canonicalMini.x()
                        :fallbackMini.x)
                    :0;
                int miniY=miniPresent
                    ?(canonicalMiniSource
                        ?canonicalMini.y()
                        :fallbackMini.y)
                    :0;
                EntityId miniCanonicalId=
                    canonicalMiniSource
                        ?canonicalMini.id
                        :null;

                live.add(src.owner.id());

                RemotePetTrack t=
                    remote.get(src.owner.id());

                if(t!=null&&
                   t.sourceGeneration!=
                        src.ownerGeneration){
                    if(!removeRemote(
                            src.owner.id()
                        ))
                        return;
                    t=null;
                }

                if(t==null){
                    t=new RemotePetTrack(
                        src.ownerGeneration
                    );
                    remote.put(
                        src.owner.id(),
                        t
                    );
                }

                Integer selector=
                    src.npcs.petParticleSelector();

                boolean mainIdentityChanged=
                    t.mainScene>=0&&
                    !Objects.equals(
                        t.mainCanonicalId,
                        petCanonicalId
                    )&&
                    (t.mainCanonicalId!=null||
                     petCanonicalId!=null);

                boolean selectorChanged=
                    t.mainScene>=0&&
                    !Objects.equals(
                        t.mainParticleSelector,
                        selector
                    );

                int previousMainScene=t.mainScene;

                if(mainIdentityChanged||
                   selectorChanged){
                    if(t.mainScene>=0&&
                       removeMirror(t.mainScene)!=
                            ServerPacketWriter
                                .RecoverablePacketResult
                                .COMMITTED)
                        return;

                    if(t.mainCanonicalId!=null)
                        remoteIndexes.unbind(
                            t.mainCanonicalId
                        );
                    t.mainScene=-1;
                    t.mainDef=-1;
                }

                boolean mainWasAbsent=t.mainScene<0;

                int nextMainScene=syncOne(
                    src.owner.id(),
                    src.ownerGeneration,
                    t.mainScene,
                    t.mainDef,
                    petDef,
                    t.mainX,
                    t.mainY,
                    petX,
                    petY,
                    32768+playerIndex,
                    selector,
                    petCanonicalId
                );

                if(nextMainScene==RETRY_SCENE)
                    return;

                t.mainScene=nextMainScene;
                t.mainDef=petDef;
                t.mainX=petX;
                t.mainY=petY;
                t.mainCanonicalId=petCanonicalId;
                t.mainParticleSelector=selector;

                boolean mainRespawned=
                    mainWasAbsent||
                    mainIdentityChanged||
                    selectorChanged||
                    previousMainScene!=t.mainScene;

                if(mainRespawned&&
                   t.mainScene>=0&&
                   PetPresentationProfile.supportsNativeState(
                       petDef
                   )){
                    int nativeState=
                        src.npcs.petNativeState();

                    if(nativeState!=0){
                        NpcEntity mirrored=
                            npcs.scene(t.mainScene);
                        if(mirrored!=null)
                            sendMirrorMaskOrDefer(
                                src.owner.id(),
                                src.ownerGeneration,
                                mirrored,
                                NpcSyncEncoder.Mask.forceText(
                                    Integer.toString(
                                        nativeState
                                    )
                                )
                            );
                    }

                    if(!pendingMirrorMasks.isEmpty())
                        return;
                }

                if(miniPresent&&
                   t.mainScene>=0){
                    boolean miniIdentityChanged=
                        t.miniScene>=0&&
                        !Objects.equals(
                            t.miniCanonicalId,
                            miniCanonicalId
                        )&&
                        (t.miniCanonicalId!=null||
                         miniCanonicalId!=null);

                    if(miniIdentityChanged){
                        if(t.miniScene>=0&&
                           removeMirror(t.miniScene)!=
                                ServerPacketWriter
                                    .RecoverablePacketResult
                                    .COMMITTED)
                            return;

                        if(t.miniCanonicalId!=null)
                            remoteIndexes.unbind(
                                t.miniCanonicalId
                            );
                        t.miniScene=-1;
                        t.miniDef=-1;
                    }

                    int oldMiniScene=t.miniScene;

                    int nextMiniScene=syncOne(
                        src.owner.id(),
                        src.ownerGeneration,
                        t.miniScene,
                        t.miniDef,
                        miniDef,
                        t.miniX,
                        t.miniY,
                        miniX,
                        miniY,
                        t.mainScene,
                        null,
                        miniCanonicalId
                    );

                    if(nextMiniScene==RETRY_SCENE)
                        return;

                    t.miniScene=nextMiniScene;
                    t.miniDef=miniDef;
                    t.miniX=miniX;
                    t.miniY=miniY;
                    t.miniCanonicalId=miniCanonicalId;

                    if(t.miniScene>=0&&
                       (mainRespawned||
                        miniIdentityChanged||
                        oldMiniScene!=t.miniScene)){
                        NpcEntity mirroredMini=
                            npcs.scene(t.miniScene);
                        if(mirroredMini!=null)
                            sendMirrorMaskOrDefer(
                                src.owner.id(),
                                src.ownerGeneration,
                                mirroredMini,
                                NpcSyncEncoder.Mask.interactionTarget(
                                    t.mainScene
                                )
                            );

                        if(!pendingMirrorMasks.isEmpty())
                            return;
                    }
                }else if(t.miniScene>=0){
                    if(removeMirror(t.miniScene)!=
                            ServerPacketWriter
                                .RecoverablePacketResult
                                .COMMITTED)
                        return;

                    if(t.miniCanonicalId!=null)
                        remoteIndexes.unbind(
                            t.miniCanonicalId
                        );
                    t.miniScene=-1;
                    t.miniDef=-1;
                    t.miniCanonicalId=null;
                }
            }

            ArrayList<EntityId> stale=
                new ArrayList<>();
            for(EntityId id:remote.keySet())
                if(!live.contains(id))
                    stale.add(id);
            for(EntityId id:stale)
                if(!removeRemote(id))
                    return;
        }

        void syncCanonicalNpcs()throws IOException{
            if(projectionTransportFailedClosed||
               !ownerCurrent())
                return;

            ArrayList<EntityId> tracked;

            synchronized(SharedNpcWorldRelay.class){
                tracked=
                    new ArrayList<>(
                        state.genericNpcIds
                    );
            }

            HashSet<EntityId> desired=
                new HashSet<>(
                    tracked
                );

            for(EntityId id:tracked){
                WorldNpc canonical=
                    state.world.npcs()
                        .byId(id);

                if(canonical==null||
                   canonical.ownerId!=null){
                    if(!removeGeneric(id))
                        return;
                    continue;
                }

                GenericNpcTrack track=
                    genericNpcs.get(id);

                if(track==null){
                    track=
                        new GenericNpcTrack();
                    genericNpcs.put(
                        id,
                        track
                    );
                }

                if(!syncGenericOne(
                        id,
                        canonical,
                        track
                    ))
                    return;
            }

            ArrayList<EntityId> stale=
                new ArrayList<>();

            for(EntityId id:
                    genericNpcs.keySet())
                if(!desired.contains(id))
                    stale.add(id);

            for(EntityId id:stale)
                if(!removeGeneric(id))
                    return;
        }

        private boolean syncGenericOne(
            EntityId id,
            WorldNpc canonical,
            GenericNpcTrack track
        )throws IOException{
            int x=canonical.x();
            int y=canonical.y();

            if(canonical.plane()!=
                    movement.plane()||
               !movement.insideCurrentLoadedRegion(
                    x,
                    y
                )||
               Math.abs(x-movement.x())>15||
               Math.abs(y-movement.y())>15){
                if(track.scene>=0&&
                   removeMirror(track.scene)!=
                        ServerPacketWriter
                            .RecoverablePacketResult
                            .COMMITTED)
                    return false;

                genericIndexes.unbind(id);
                track.scene=-1;
                track.definition=
                    canonical.definitionId;
                track.x=x;
                track.y=y;
                return true;
            }

            if(track.scene<0||
               track.definition!=
                    canonical.definitionId||
               npcs.scene(track.scene)==null){
                if(track.scene>=0&&
                   removeMirror(track.scene)!=
                        ServerPacketWriter
                            .RecoverablePacketResult
                            .COMMITTED)
                    return false;

                genericIndexes.unbind(id);

                NpcEntity projected=
                    spawnMirror(
                        canonical.definitionId,
                        x,
                        y,
                        null
                    );

                if(projected==null)
                    return false;

                projected.bindCanonicalId(
                    id
                );
                genericIndexes.bind(
                    id,
                    projected.sceneIndex
                );

                track.scene=
                    projected.sceneIndex;
                track.definition=
                    canonical.definitionId;
                track.x=x;
                track.y=y;
                return true;
            }

            NpcEntity projected=
                npcs.scene(
                    track.scene
                );

            if(projected==null){
                track.scene=-1;
                return syncGenericOne(
                    id,
                    canonical,
                    track
                );
            }

            projected.bindCanonicalId(
                id
            );
            genericIndexes.bind(
                id,
                projected.sceneIndex
            );

            int dx=x-track.x;
            int dy=y-track.y;

            if(dx==0&&dy==0)
                return true;

            int d1=-1;
            int d2=-1;

            if(Math.abs(dx)<=1&&
               Math.abs(dy)<=1){
                d1=
                    MovementState.direction(
                        track.x,
                        track.y,
                        x,
                        y
                    );
            }else if(Math.abs(dx)<=2&&
                     Math.abs(dy)<=2){
                int mx=
                    track.x+
                    Integer.signum(dx);
                int my=
                    track.y+
                    Integer.signum(dy);

                d1=
                    MovementState.direction(
                        track.x,
                        track.y,
                        mx,
                        my
                    );
                d2=
                    MovementState.direction(
                        mx,
                        my,
                        x,
                        y
                    );
            }

            if(d1<0||
               (Math.max(
                    Math.abs(dx),
                    Math.abs(dy)
                )>1&&
                d2<0)){
                if(removeMirror(track.scene)!=
                        ServerPacketWriter
                            .RecoverablePacketResult
                            .COMMITTED)
                    return false;

                genericIndexes.unbind(id);
                track.scene=-1;
                track.x=x;
                track.y=y;
                return syncGenericOne(
                    id,
                    canonical,
                    track
                );
            }

            if(moveMirror(
                    projected,
                    d1,
                    d2,
                    x,
                    y
                )!=
                    ServerPacketWriter
                        .RecoverablePacketResult
                        .COMMITTED)
                return false;

            track.x=x;
            track.y=y;
            return true;
        }

        boolean removeGeneric(
            EntityId id
        )throws IOException{
            GenericNpcTrack track=
                genericNpcs.get(id);

            if(track==null)
                return true;

            if(track.scene>=0){
                if(removeMirror(track.scene)!=
                        ServerPacketWriter
                            .RecoverablePacketResult
                            .COMMITTED)
                    return false;

                track.scene=-1;
            }

            genericIndexes.unbind(id);
            genericNpcs.remove(id);
            return true;
        }

        void removeAllGenericNpcs()
            throws IOException{
            for(EntityId id:
                    new ArrayList<>(
                        genericNpcs.keySet()
                    ))
                if(!removeGeneric(id))
                    return;
        }

        private void sendMirrorMaskOrDefer(
            EntityId sourceId,
            long sourceGeneration,
            NpcEntity npc,
            NpcSyncEncoder.Mask mask
        )throws IOException{
            PendingMirrorMask pending=
                new PendingMirrorMask(
                    sourceId,
                    sourceGeneration,
                    npc,
                    mask
                );

            if(!pendingMirrorMasks.isEmpty()){
                pendingMirrorMasks.addLast(
                    pending
                );
                return;
            }

            final ServerPacketWriter.RecoverablePacketResult
                publication;

            try{
                publication=
                    writer.publishRecoverablePacket(
                        ()->npcs.sendMaskLocal(
                            npc,
                            mask,
                            writer
                        )
                    );
            }catch(IOException nonRetryable){
                projectionTransportFailedClosed=true;
                pendingMirrorMasks.clear();
                throw nonRetryable;
            }

            if(publication==
                    ServerPacketWriter
                        .RecoverablePacketResult
                        .RETRACTED_RETRYABLE){
                pendingMirrorMasks.addLast(
                    pending
                );

                System.err.println(
                    "[ENGINE-R3.2] deferred retractable remote mirror mask scene="+
                    npc.sceneIndex
                );
            }
        }

        private void flushPendingMirrorMasks()
            throws IOException
        {
            if(projectionTransportFailedClosed){
                pendingMirrorMasks.clear();
                return;
            }

            while(!pendingMirrorMasks.isEmpty()){
                PendingMirrorMask pending=
                    pendingMirrorMasks.peekFirst();

                Context source=
                    state.contexts.get(
                        pending.sourceId
                    );

                if(source==null||
                   source.ownerGeneration!=
                        pending.sourceGeneration||
                   !source.ownerCurrent()){
                    pendingMirrorMasks.removeFirst();
                    continue;
                }

                NpcEntity current=
                    npcs.scene(
                        pending.npc.sceneIndex
                    );

                if(current!=pending.npc){
                    pendingMirrorMasks.removeFirst();
                    continue;
                }

                final ServerPacketWriter.RecoverablePacketResult
                    publication;

                try{
                    publication=
                        writer.publishRecoverablePacket(
                            ()->npcs.sendMaskLocal(
                                pending.npc,
                                pending.mask,
                                writer
                            )
                        );
                }catch(IOException nonRetryable){
                    projectionTransportFailedClosed=true;
                    pendingMirrorMasks.clear();
                    throw nonRetryable;
                }

                if(publication==
                        ServerPacketWriter
                            .RecoverablePacketResult
                            .RETRACTED_RETRYABLE)
                    return;

                pendingMirrorMasks.removeFirst();
            }
        }

        int syncOne(
            EntityId sourceId,
            long sourceGeneration,
            int scene,
            int oldDef,
            int def,
            int oldX,
            int oldY,
            int x,
            int y,
            int interactionTarget,
            Integer particleSelector,
            EntityId canonicalId
        )throws IOException{
            if(Math.abs(x-movement.x())>15||
               Math.abs(y-movement.y())>15){
                if(scene>=0&&
                   removeMirror(scene)!=
                        ServerPacketWriter
                            .RecoverablePacketResult
                            .COMMITTED)
                    return RETRY_SCENE;

                if(canonicalId!=null)
                    remoteIndexes.unbind(
                        canonicalId
                    );
                return -1;
            }

            if(scene<0||
               oldDef!=def||
               npcs.scene(scene)==null){
                if(scene>=0&&
                   removeMirror(scene)!=
                        ServerPacketWriter
                            .RecoverablePacketResult
                            .COMMITTED)
                    return RETRY_SCENE;

                if(canonicalId!=null)
                    remoteIndexes.unbind(
                        canonicalId
                    );

                NpcEntity e=
                    spawnMirror(
                        def,
                        x,
                        y,
                        particleSelector
                    );

                if(e==null)
                    return RETRY_SCENE;

                if(canonicalId!=null){
                    e.bindCanonicalId(canonicalId);
                    remoteIndexes.bind(
                        canonicalId,
                        e.sceneIndex
                    );
                }

                sendMirrorMaskOrDefer(
                    sourceId,
                    sourceGeneration,
                    e,
                    NpcSyncEncoder.Mask.interactionTarget(
                        interactionTarget
                    )
                );

                return e.sceneIndex;
            }

            NpcEntity e=npcs.scene(scene);
            if(e==null)return -1;

            if(canonicalId!=null){
                e.bindCanonicalId(canonicalId);
                remoteIndexes.bind(
                    canonicalId,
                    scene
                );
            }

            int dx=x-oldX;
            int dy=y-oldY;
            if(dx==0&&dy==0)
                return scene;

            int d1=-1,d2=-1;
            if(Math.abs(dx)<=1&&
               Math.abs(dy)<=1){
                d1=MovementState.direction(
                    oldX,
                    oldY,
                    x,
                    y
                );
            }else if(Math.abs(dx)<=2&&
                     Math.abs(dy)<=2){
                int mx=oldX+Integer.signum(dx);
                int my=oldY+Integer.signum(dy);
                d1=MovementState.direction(
                    oldX,
                    oldY,
                    mx,
                    my
                );
                d2=MovementState.direction(
                    mx,
                    my,
                    x,
                    y
                );
            }

            if(d1<0||
               (Math.max(
                    Math.abs(dx),
                    Math.abs(dy)
                )>1&&d2<0)){
                if(removeMirror(scene)!=
                        ServerPacketWriter
                            .RecoverablePacketResult
                            .COMMITTED)
                    return RETRY_SCENE;

                if(canonicalId!=null)
                    remoteIndexes.unbind(
                        canonicalId
                    );

                return syncOne(
                    sourceId,
                    sourceGeneration,
                    -1,
                    -1,
                    def,
                    x,
                    y,
                    x,
                    y,
                    interactionTarget,
                    particleSelector,
                    canonicalId
                );
            }

            if(moveMirror(
                    e,
                    d1,
                    d2,
                    x,
                    y
                )!=
                    ServerPacketWriter
                        .RecoverablePacketResult
                        .COMMITTED)
                return RETRY_SCENE;

            return scene;
        }

        NpcEntity resolve(
            EntityId sourceId,
            WorldNpcPresentationEvents.Target ref
        ){
            return resolve(
                sourceId,
                -1L,
                ref
            );
        }

        NpcEntity resolve(
            EntityId sourceId,
            long sourceGeneration,
            WorldNpcPresentationEvents.Target ref
        ){
            if(!ownerCurrent())
                return null;

            if(ref.kind==
                    WorldNpcPresentationEvents.Target.CANONICAL){
                NpcEntity canonical=
                    npcs.canonical(ref.canonicalId);
                return canonical!=null&&
                    canonical.definitionId==
                        ref.definition
                    ?canonical
                    :null;
            }

            if(ref.kind==
                    WorldNpcPresentationEvents.Target.SCENE){
                NpcEntity same=npcs.scene(ref.scene);
                return same!=null&&
                    same.definitionId==ref.definition
                    ?same
                    :null;
            }

            RemotePetTrack t=
                remote.get(sourceId);
            if(t==null)
                return null;

            if(sourceGeneration>=0L&&
               t.sourceGeneration!=
                    sourceGeneration)
                return null;

            Integer mapped=
                ref.canonicalId==null
                    ?null
                    :remoteIndexes.sceneIndex(
                        ref.canonicalId
                    );

            int scene=mapped!=null
                ?mapped.intValue()
                :(ref.kind==WorldNpcPresentationEvents.Target.PET
                    ?t.mainScene
                    :t.miniScene);

            if(scene<0)return null;

            NpcEntity e=npcs.scene(scene);
            return e!=null&&
                e.definitionId==ref.definition
                ?e
                :null;
        }

        boolean removeRemote(EntityId id)throws IOException{
            RemotePetTrack t=remote.get(id);
            if(t==null)return true;

            if(t.miniScene>=0){
                if(removeMirror(t.miniScene)!=
                        ServerPacketWriter
                            .RecoverablePacketResult
                            .COMMITTED)
                    return false;

                t.miniScene=-1;

                if(t.miniCanonicalId!=null){
                    remoteIndexes.unbind(
                        t.miniCanonicalId
                    );
                    t.miniCanonicalId=null;
                }
            }

            if(t.mainScene>=0){
                if(removeMirror(t.mainScene)!=
                        ServerPacketWriter
                            .RecoverablePacketResult
                            .COMMITTED)
                    return false;

                t.mainScene=-1;

                if(t.mainCanonicalId!=null){
                    remoteIndexes.unbind(
                        t.mainCanonicalId
                    );
                    t.mainCanonicalId=null;
                }
            }

            remote.remove(id);
            return true;
        }

        void removeAllRemotePets()throws IOException{
            for(EntityId id:
                    new ArrayList<>(
                        remote.keySet()
                    ))
                if(!removeRemote(id))
                    return;
        }
    }
}
