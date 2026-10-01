package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Engine R3 per-view player synchronization for the exact current SpawnPK client.
 *
 * The current server's packet-81 producers are intentionally left as the local-player
 * authority.  This layer parses only that proven local bit prefix, replaces the legacy
 * remote-count=0 section with a per-view remote-player section, and then appends the
 * original local mask bytes unchanged.  Remote movement comes from the owning player's
 * exact local packet-81 movement bits, while remote presentation masks are relayed from
 * the owning player's exact mask payload.
 */
final class Player81WorldSync {
    private static final IdentityHashMap<ServerPacketWriter,Context> BY_WRITER=new IdentityHashMap<>();
    private static final IdentityHashMap<World,WorldState> BY_WORLD=new IdentityHashMap<>();
    private static final int SENTINEL=2047;
    private static final int LOCAL_PLAYER_INDEX=NpcRegistry.LOCAL_PLAYER_INDEX;

    private Player81WorldSync(){}

    static synchronized Context register(ServerPacketWriter writer,World world,WorldPlayer owner,DevAuthorityWorkbench dev){
        if(writer==null||world==null||owner==null)throw new NullPointerException();

        if(world.closed())
            throw new IllegalStateException(
                "cannot register Player81WorldSync on closed World"
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

        Context c=new Context(writer,ws,owner,dev);
        BY_WRITER.put(writer,c);ws.contexts.put(owner.id(),c);
        return c;
    }

    static synchronized int clientIndexFor(ServerPacketWriter writer,WorldPlayer target){
        Context c=BY_WRITER.get(writer);
        return c==null||!c.ownerCurrent()
            ?-1
            :c.clientIndexFor(target);
    }

    /** Latest world-visible packet81 presentation event emitted by this source. */
    static synchronized long latestPublishedEventSequence(ServerPacketWriter sourceWriter){
        Context c=BY_WRITER.get(sourceWriter);
        if(c==null||!c.ownerCurrent())return 0L;
        Event e=c.state.latestEvent(c.owner.id());
        return e==null?0L:e.seq;
    }

    /** Last packet81 presentation event from sourceId consumed by this viewer. */
    static synchronized long consumedEventSequence(
        ServerPacketWriter viewerWriter,
        EntityId sourceId
    ){
        Context viewer=
            BY_WRITER.get(viewerWriter);

        if(viewer==null||
           !viewer.ownerCurrent()||
           sourceId==null)
            return -1L;

        if(sourceId.equals(
                viewer.owner.id()))
            return Long.MAX_VALUE;

        Track track=
            viewer.visible.get(sourceId);

        return track==null
            ?-1L
            :track.lastEventSeq;
    }

    static synchronized long consumedEventSequence(
        ServerPacketWriter viewerWriter,
        EntityId sourceId,
        long expectedSourceGeneration
    ){
        Context viewer=
            BY_WRITER.get(viewerWriter);

        if(viewer==null||
           !viewer.ownerCurrent()||
           sourceId==null)
            return -1L;

        if(sourceId.equals(
                viewer.owner.id()))
            return viewer.ownerGeneration==
                    expectedSourceGeneration
                ?Long.MAX_VALUE
                :-1L;

        Track track=
            viewer.visible.get(sourceId);

        if(track==null||
           track.generation!=
                expectedSourceGeneration)
            return -1L;

        Context source=
            viewer.state.contexts.get(
                sourceId
            );

        if(source==null||
           source.ownerGeneration!=
                expectedSourceGeneration||
           !source.ownerCurrent())
            return -1L;

        WorldPlayer current=
            viewer.state.world.players()
                .byId(sourceId);

        if(current==null||
           current!=source.owner||
           !viewer.state.world.players()
                .owns(
                    current,
                    expectedSourceGeneration
                ))
            return -1L;

        return track.lastEventSeq;
    }

    static synchronized boolean sendSkillUpdate(
        World world,
        WorldPlayer player,
        int skill,
        int xp,
        int currentLevel
    )throws IOException{
        if(world==null||player==null)return false;
        WorldState state=BY_WORLD.get(world);
        if(state==null)return false;
        Context context=state.contexts.get(player.id());
        if(context==null||
           context.owner!=player||
           !context.ownerCurrent())
            return false;

        context.writer.fixed(
            134,
            BootstrapPackets.skill134(
                skill,
                xp,
                currentLevel
            )
        );
        return true;
    }

    static synchronized void unregister(ServerPacketWriter writer){
        Context c=BY_WRITER.remove(writer);
        if(c==null)return;
        cleanupContext(c);
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

            if(entry.getValue().state==state){
                entry.getValue().closed=true;
                writers.remove();
            }
        }

        synchronized(state){
            for(Context context:
                    state.contexts.values())
                context.closed=true;

            state.contexts.clear();
            state.motions.clear();
            state.events.clear();
            state.tradeRequests.clear();
        }
    }

    private static void cleanupContext(Context c){
        c.state.removeOwner(c.owner.id());
        c.closed=true;
        if(c.state.contexts.isEmpty())
            BY_WORLD.remove(c.state.world);
    }

    static byte[] transform(
        ServerPacketWriter writer,
        byte[] body
    ){
        if(body==null)
            return null;

        final Context candidate;

        synchronized(Player81WorldSync.class){
            candidate=
                BY_WRITER.get(writer);
        }

        if(candidate==null)
            return body;

        final byte[][] transformed=
            new byte[][]{body};

        try{
            boolean accepted=
                candidate.state.world
                    .withOpenPlayerOwnershipIfCurrent(
                        candidate.owner,
                        candidate.ownerGeneration,
                        ()->{
                            synchronized(
                                Player81WorldSync.class
                            ){
                                if(BY_WRITER.get(
                                        writer
                                    )!=candidate)
                                    return;
                            }

                            transformed[0]=
                                candidate.transform(
                                    body
                                );
                        }
                    );

            return accepted
                ?transformed[0]
                :body;
        }catch(Throwable t){
            // Fail closed to the already-certified local packet rather than corrupt framing.
            System.err.println(
                "[ENGINE-R3] player81 merge failed for "+
                candidate.owner.id()+": "+t+
                "; using certified local-only body"
            );
            return body;
        }
    }

    static synchronized void sendPlayerOptionsIfMultiplayer(World world)throws IOException{
        WorldState ws=BY_WORLD.get(world);
        if(ws==null||world.players().size()<2)return;
        for(Context c:ws.contexts.values()){
            if(!c.ownerCurrent()||
               c.playerOptionsSent)
                continue;
            sendPlayerOptions(c.writer);
            c.playerOptionsSent=true;
        }
    }

    static void sendPlayerOptions(ServerPacketWriter w)throws IOException{
        // S2C104 exact-current decoder: option=O() => wire -slot, flag=N() => wire flag+128,
        // then newline-terminated text. Slot -> C2S mapping is exact current client:
        // 1=128 BE16, 2=153 LE16, 3=73 LE16, 4=139 LE16, 5=39 LE16.
        w.varByte(104,option104(1,0,"Attack"));
        w.varByte(104,option104(2,0,"Follow"));
        w.varByte(104,option104(3,0,"Trade with"));
        w.varByte(104,option104(4,0,"null"));
        w.varByte(104,option104(5,0,"null"));
    }

    static byte[] option104(int slot,int flag,String text)throws IOException{
        if(slot<1||slot>5)throw new IllegalArgumentException("slot");
        if(flag<0||flag>255)throw new IllegalArgumentException("flag");
        if(text==null)text="null";
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write((-slot)&255);
        out.write((flag+128)&255);
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
        out.write(10);
        return out.toByteArray();
    }

    static byte[] transformForTest(Context c,byte[] body)throws IOException{return c.transform(body);}

    static byte[] latestEventForViewerForTest(
        Context viewer,
        EntityId sourceId
    ){
        Event event=
            viewer.state.latestEvent(
                sourceId
            );
        return event==null
            ?null
            :event.forViewer(viewer);
    }

    static final class PreparedBatch {
        final Context context;
        final LinkedHashMap<EntityId,Track> visible;
        final HashMap<EntityId,Integer> reservedIndexes;
        final ArrayList<LegacyLocal> publications=
            new ArrayList<>();
        boolean completed;

        PreparedBatch(
            Context context,
            LinkedHashMap<EntityId,Track> visible,
            HashMap<EntityId,Integer> reservedIndexes
        ){
            this.context=context;
            this.visible=visible;
            this.reservedIndexes=reservedIndexes;
        }
    }

    static PreparedBatch beginPreparedBatch(
        ServerPacketWriter writer
    )throws IOException{
        final Context candidate;

        synchronized(Player81WorldSync.class){
            candidate=BY_WRITER.get(writer);
        }

        if(candidate==null)
            return null;

        final PreparedBatch[] prepared=
            new PreparedBatch[1];

        boolean accepted=
            candidate.state.world
                .withOpenPlayerOwnershipIfCurrent(
                    candidate.owner,
                    candidate.ownerGeneration,
                    ()->prepared[0]=
                        candidate.beginPreparedBatch()
                );

        return accepted
            ?prepared[0]
            :null;
    }

    static byte[] transformPrepared(
        PreparedBatch prepared,
        byte[] body
    ){
        if(body==null)
            return null;

        if(prepared==null)
            return body;

        Context candidate=
            prepared.context;

        final byte[][] transformed=
            new byte[][]{body};

        try{
            boolean accepted=
                candidate.state.world
                    .withOpenPlayerOwnershipIfCurrent(
                        candidate.owner,
                        candidate.ownerGeneration,
                        ()->transformed[0]=
                            candidate.transformPrepared(
                                prepared,
                                body
                            )
                    );

            return accepted
                ?transformed[0]
                :body;
        }catch(Throwable t){
            System.err.println(
                "[ENGINE-R3] prepared player81 merge failed for "+
                candidate.owner.id()+": "+t+
                "; using certified local-only body"
            );
            return body;
        }
    }

    @FunctionalInterface
    interface PreparedBatchTransport {
        void commit()throws IOException;
    }

    @FunctionalInterface
    interface PreparedBatchOwnedCommit {
        void commit()throws IOException;
    }

    static boolean withPreparedBatchOwnership(
        PreparedBatch prepared,
        PreparedBatchOwnedCommit commit
    )throws IOException{
        if(prepared==null||
           commit==null)
            throw new NullPointerException(
                "prepared/commit"
            );

        Context candidate=
            prepared.context;
        final boolean[] completed=
            new boolean[]{false};

        boolean accepted=
            candidate.state.world
                .withOpenPlayerOwnershipIfCurrent(
                    candidate.owner,
                    candidate.ownerGeneration,
                    ()->{
                        synchronized(Player81WorldSync.class){
                            if(BY_WRITER.get(
                                    candidate.writer
                                )!=candidate)
                                return;
                        }

                        if(prepared.completed)
                            return;

                        commit.commit();
                        completed[0]=true;
                    }
                );

        return accepted&&
            completed[0];
    }

    static void commitPreparedBatchOwned(
        PreparedBatch prepared
    ){
        if(prepared==null)
            throw new NullPointerException(
                "prepared"
            );

        prepared.context
            .commitPreparedBatch(
                prepared
            );
    }

    static boolean commitPreparedBatchWithTransport(
        PreparedBatch prepared,
        PreparedBatchTransport transport
    )throws IOException{
        if(prepared==null||
           transport==null)
            throw new NullPointerException(
                "prepared/transport"
            );

        Context candidate=
            prepared.context;
        final boolean[] committed=
            new boolean[]{false};

        boolean accepted=
            candidate.state.world
                .withOpenPlayerOwnershipIfCurrent(
                    candidate.owner,
                    candidate.ownerGeneration,
                    ()->{
                        synchronized(Player81WorldSync.class){
                            if(BY_WRITER.get(
                                    candidate.writer
                                )!=candidate)
                                return;
                        }

                        if(prepared.completed)
                            return;

                        transport.commit();
                        candidate.commitPreparedBatch(
                            prepared
                        );
                        committed[0]=true;
                    }
                );

        return accepted&&
            committed[0];
    }

    static void commitPreparedBatch(
        PreparedBatch prepared
    ){
        if(prepared==null)
            return;

        Context candidate=
            prepared.context;

        try{
            boolean accepted=
                candidate.state.world
                    .withOpenPlayerOwnershipIfCurrent(
                        candidate.owner,
                        candidate.ownerGeneration,
                        ()->candidate.commitPreparedBatch(
                            prepared
                        )
                    );

            if(!accepted)
                prepared.completed=true;
        }catch(Throwable t){
            prepared.completed=true;
            System.err.println(
                "[ENGINE-R3] prepared player81 commit failed owner="+
                candidate.owner.id()+": "+t
            );
        }
    }

    static void abortPreparedBatch(
        PreparedBatch prepared
    ){
        if(prepared!=null)
            prepared.completed=true;
    }

    static final class Context {
        final ServerPacketWriter writer;
        final WorldState state;
        final WorldPlayer owner;
        final long ownerGeneration;
        final DevAuthorityWorkbench dev;
        final LinkedHashMap<EntityId,Track> visible=new LinkedHashMap<>();
        final HashMap<EntityId,Integer> reservedIndexes=new HashMap<>();
        boolean closed;
        boolean playerOptionsSent;

        Context(ServerPacketWriter writer,WorldState state,WorldPlayer owner,DevAuthorityWorkbench dev){
            this.writer=writer;
            this.state=state;
            this.owner=owner;
            this.ownerGeneration=owner.generation();
            this.dev=dev;
        }

        boolean ownerCurrent(){
            return !closed&&
                !state.world.closed()&&
                state.world.players().owns(
                    owner,
                    ownerGeneration
                );
        }

        private synchronized Track visibleTrack(
            int clientIndex
        ){
            if(!ownerCurrent())
                return null;

            for(Track track:visible.values()){
                if(track.clientIndex!=clientIndex)
                    continue;

                WorldPlayer player=
                    state.world.players()
                        .byId(track.id);

                return player!=null&&
                    state.world.players().owns(
                        player,
                        track.generation
                    )
                    ?track
                    :null;
            }

            return null;
        }

        synchronized WorldPlayer resolveVisible(
            int clientIndex
        ){
            Track track=
                visibleTrack(clientIndex);

            return track==null
                ?null
                :state.world.players()
                    .byId(track.id);
        }

        synchronized int clientIndexFor(WorldPlayer p){
            if(!ownerCurrent()||p==null)return -1;
            if(p==owner)return LOCAL_PLAYER_INDEX;
            Track t=visible.get(p.id());
            return t!=null&&
                state.world.players().owns(
                    p,
                    t.generation
                )
                ?t.clientIndex
                :-1;
        }

        synchronized int interactionTargetFor(WorldPlayer p){
            int idx=clientIndexFor(p);
            return idx<0?-1:32768+idx;
        }

        synchronized String requestTrade(WorldPlayer target,long now){
            if(!ownerCurrent())
                return "TRADE_REJECTED_STALE_OWNER";
            return state.requestTrade(owner,target,now);
        }

        synchronized String summary(){return "viewer="+owner.id()+" visible="+visible.size()+" reserved="+reservedIndexes.size();}

        synchronized byte[] transform(byte[] legacy)throws IOException{
            if(!ownerCurrent())
                return legacy;

            LegacyLocal local=LegacyLocal.parse(legacy);
            if(local==null||local.oldRemoteCount!=0||local.sentinel!=SENTINEL)return legacy;

            state.publish(owner,this,local);

            List<WorldPlayer> players=state.world.players().snapshot();
            HashMap<EntityId,WorldPlayer> current=new HashMap<>();
            for(WorldPlayer p:players)current.put(p.id(),p);

            ArrayList<Track> oldTracks=new ArrayList<>(visible.values());
            LinkedHashMap<EntityId,Track> nextVisible=new LinkedHashMap<>();
            ArrayList<byte[]> remoteTails=new ArrayList<>();
            HashSet<EntityId> removedThisPacket=new HashSet<>();

            BitWriter bits=new BitWriter();
            local.writeLocal(bits);
            bits.write(oldTracks.size(),8);

            for(Track t:oldTracks){
                WorldPlayer remote=current.get(t.id);
                boolean keep=
                    isVisible(remote)&&
                    state.world.players().owns(
                        remote,
                        t.generation
                    );
                if(!keep){
                    bits.write(1,1);bits.write(3,2);
                    removedThisPacket.add(t.id);
                    continue;
                }

                Motion m=state.motions.get(t.id);
                boolean hasNewMotion=m!=null&&m.seq>t.lastMotionSeq;
                boolean hardReanchor=false;
                int moveType=0,dir1=-1,dir2=-1;
                if(hasNewMotion){
                    if(m.type==1||m.type==2){moveType=m.type;dir1=m.dir1;dir2=m.dir2;}
                    else if(m.type==3)hardReanchor=true;
                }else if(remote.movement().x()!=t.x||remote.movement().y()!=t.y||remote.movement().plane()!=t.plane){
                    int dx=remote.movement().x()-t.x,dy=remote.movement().y()-t.y;
                    if(remote.movement().plane()!=t.plane)hardReanchor=true;
                    else if(Math.abs(dx)<=1&&Math.abs(dy)<=1&&(dx!=0||dy!=0)){
                        moveType=1;dir1=MovementState.direction(t.x,t.y,remote.movement().x(),remote.movement().y());
                        if(dir1<0)hardReanchor=true;
                    }else if(Math.abs(dx)<=2&&Math.abs(dy)<=2&&(dx!=0||dy!=0)){
                        int mx=t.x+Integer.signum(dx),my=t.y+Integer.signum(dy);
                        int d1=MovementState.direction(t.x,t.y,mx,my);
                        int d2=MovementState.direction(mx,my,remote.movement().x(),remote.movement().y());
                        if(d1>=0&&d2>=0){moveType=2;dir1=d1;dir2=d2;}else hardReanchor=true;
                    }else hardReanchor=true;
                }

                if(hardReanchor){
                    bits.write(1,1);bits.write(3,2);
                    removedThisPacket.add(t.id);
                    continue;
                }

                Event ev=state.nextEventAfter(t.id,t.lastEventSeq);
                boolean eventPending=ev!=null;
                byte[] appearance=appearanceTail(remote);
                int appearanceHash=Arrays.hashCode(appearance);
                boolean appearanceChanged=appearanceHash!=t.appearanceHash;
                byte[] maskTail=null;
                long consumedEventSeq=t.lastEventSeq;
                if(eventPending){
                    maskTail=ev.forViewer(this);
                    consumedEventSeq=ev.seq;
                    if((maskOfTail(maskTail)&0x10)!=0)appearanceChanged=false;
                }else if(appearanceChanged){
                    maskTail=appearance;
                }
                boolean hasMask=maskTail!=null&&maskTail.length>0;

                if(moveType==1){
                    bits.write(1,1);bits.write(1,2);bits.write(dir1,3);bits.write(hasMask?1:0,1);
                }else if(moveType==2){
                    bits.write(1,1);bits.write(2,2);bits.write(dir1,3);bits.write(dir2,3);bits.write(hasMask?1:0,1);
                }else if(hasMask){
                    bits.write(1,1);bits.write(0,2);
                }else bits.write(0,1);

                t.x=remote.movement().x();t.y=remote.movement().y();t.plane=remote.movement().plane();
                if(hasNewMotion)t.lastMotionSeq=m.seq;
                if(eventPending)t.lastEventSeq=consumedEventSeq;
                if(!appearanceChanged || (hasMask&&(maskOfTail(maskTail)&0x10)!=0))t.appearanceHash=appearanceHash;
                nextVisible.put(t.id,t);
                if(hasMask)remoteTails.add(maskTail);
            }

            // Add newly-visible players after processing the previous remote list.
            for(WorldPlayer remote:players){
                if(remote==owner||!isVisible(remote)||visible.containsKey(remote.id())||removedThisPacket.contains(remote.id()))continue;

                long remoteGeneration=
                    remote.generation();
                if(!state.world.players().owns(
                        remote,
                        remoteGeneration
                    ))
                    continue;

                int idx=indexFor(remote.id());
                int dx=remote.movement().x()-owner.movement().x(),dy=remote.movement().y()-owner.movement().y();
                if(!signed5(dx)||!signed5(dy))continue;
                bits.write(idx,11);
                bits.write(1,1); // appearance mask follows
                bits.write(1,1); // discard stale walking queue on add
                bits.write(dy&31,5); // exact client order: relative Y then X
                bits.write(dx&31,5);
                byte[] tail=appearanceTail(remote);

                Track t=new Track(
                    remote.id(),
                    remoteGeneration,
                    idx,
                    remote.movement().x(),
                    remote.movement().y(),
                    remote.movement().plane()
                );
                t.appearanceHash=Arrays.hashCode(tail);
                Motion m=state.motions.get(remote.id());if(m!=null)t.lastMotionSeq=m.seq;
                Event e=state.latestEvent(remote.id());if(e!=null)t.lastEventSeq=e.seq;
                nextVisible.put(t.id,t);remoteTails.add(tail);
            }
            bits.write(SENTINEL,11);

            ByteArrayOutputStream out=new ByteArrayOutputStream(legacy.length+remoteTails.size()*80+16);
            out.write(bits.finish());
            if(local.maskTail.length>0)out.write(local.maskTail);
            for(byte[] tail:remoteTails)out.write(tail);
            visible.clear();visible.putAll(nextVisible);
            return out.toByteArray();
        }

        synchronized byte[] transformPrepared(PreparedBatch prepared,byte[] legacy)throws IOException{
            if(!ownerCurrent())
                return legacy;

            LegacyLocal local=LegacyLocal.parse(legacy);
            if(local==null||local.oldRemoteCount!=0||local.sentinel!=SENTINEL)return legacy;

            if(prepared==null||
               prepared.context!=this||
               prepared.completed)
                return legacy;

            LinkedHashMap<EntityId,Track> workingVisible=
                copyTracks(
                    prepared.visible
                );
            HashMap<EntityId,Integer> workingReservedIndexes=
                new HashMap<>(
                    prepared.reservedIndexes
                );

            List<WorldPlayer> players=state.world.players().snapshot();
            HashMap<EntityId,WorldPlayer> current=new HashMap<>();
            for(WorldPlayer p:players)current.put(p.id(),p);

            ArrayList<Track> oldTracks=new ArrayList<>(workingVisible.values());
            LinkedHashMap<EntityId,Track> nextVisible=new LinkedHashMap<>();
            ArrayList<byte[]> remoteTails=new ArrayList<>();
            HashSet<EntityId> removedThisPacket=new HashSet<>();

            BitWriter bits=new BitWriter();
            local.writeLocal(bits);
            bits.write(oldTracks.size(),8);

            for(Track t:oldTracks){
                WorldPlayer remote=current.get(t.id);
                boolean keep=
                    isVisible(remote)&&
                    state.world.players().owns(
                        remote,
                        t.generation
                    );
                if(!keep){
                    bits.write(1,1);bits.write(3,2);
                    removedThisPacket.add(t.id);
                    continue;
                }

                Motion m=state.motions.get(t.id);
                boolean hasNewMotion=m!=null&&m.seq>t.lastMotionSeq;
                boolean hardReanchor=false;
                int moveType=0,dir1=-1,dir2=-1;
                if(hasNewMotion){
                    if(m.type==1||m.type==2){moveType=m.type;dir1=m.dir1;dir2=m.dir2;}
                    else if(m.type==3)hardReanchor=true;
                }else if(remote.movement().x()!=t.x||remote.movement().y()!=t.y||remote.movement().plane()!=t.plane){
                    int dx=remote.movement().x()-t.x,dy=remote.movement().y()-t.y;
                    if(remote.movement().plane()!=t.plane)hardReanchor=true;
                    else if(Math.abs(dx)<=1&&Math.abs(dy)<=1&&(dx!=0||dy!=0)){
                        moveType=1;dir1=MovementState.direction(t.x,t.y,remote.movement().x(),remote.movement().y());
                        if(dir1<0)hardReanchor=true;
                    }else if(Math.abs(dx)<=2&&Math.abs(dy)<=2&&(dx!=0||dy!=0)){
                        int mx=t.x+Integer.signum(dx),my=t.y+Integer.signum(dy);
                        int d1=MovementState.direction(t.x,t.y,mx,my);
                        int d2=MovementState.direction(mx,my,remote.movement().x(),remote.movement().y());
                        if(d1>=0&&d2>=0){moveType=2;dir1=d1;dir2=d2;}else hardReanchor=true;
                    }else hardReanchor=true;
                }

                if(hardReanchor){
                    bits.write(1,1);bits.write(3,2);
                    removedThisPacket.add(t.id);
                    continue;
                }

                Event ev=state.nextEventAfter(t.id,t.lastEventSeq);
                boolean eventPending=ev!=null;
                byte[] appearance=appearanceTail(remote);
                int appearanceHash=Arrays.hashCode(appearance);
                boolean appearanceChanged=appearanceHash!=t.appearanceHash;
                byte[] maskTail=null;
                long consumedEventSeq=t.lastEventSeq;
                if(eventPending){
                    maskTail=ev.forViewer(this);
                    consumedEventSeq=ev.seq;
                    if((maskOfTail(maskTail)&0x10)!=0)appearanceChanged=false;
                }else if(appearanceChanged){
                    maskTail=appearance;
                }
                boolean hasMask=maskTail!=null&&maskTail.length>0;

                if(moveType==1){
                    bits.write(1,1);bits.write(1,2);bits.write(dir1,3);bits.write(hasMask?1:0,1);
                }else if(moveType==2){
                    bits.write(1,1);bits.write(2,2);bits.write(dir1,3);bits.write(dir2,3);bits.write(hasMask?1:0,1);
                }else if(hasMask){
                    bits.write(1,1);bits.write(0,2);
                }else bits.write(0,1);

                t.x=remote.movement().x();t.y=remote.movement().y();t.plane=remote.movement().plane();
                if(hasNewMotion)t.lastMotionSeq=m.seq;
                if(eventPending)t.lastEventSeq=consumedEventSeq;
                if(!appearanceChanged || (hasMask&&(maskOfTail(maskTail)&0x10)!=0))t.appearanceHash=appearanceHash;
                nextVisible.put(t.id,t);
                if(hasMask)remoteTails.add(maskTail);
            }

            // Add newly-visible players after processing the previous remote list.
            for(WorldPlayer remote:players){
                if(remote==owner||!isVisible(remote)||workingVisible.containsKey(remote.id())||removedThisPacket.contains(remote.id()))continue;

                long remoteGeneration=
                    remote.generation();
                if(!state.world.players().owns(
                        remote,
                        remoteGeneration
                    ))
                    continue;

                int idx=indexForPrepared(workingReservedIndexes,remote.id());
                int dx=remote.movement().x()-owner.movement().x(),dy=remote.movement().y()-owner.movement().y();
                if(!signed5(dx)||!signed5(dy))continue;
                bits.write(idx,11);
                bits.write(1,1); // appearance mask follows
                bits.write(1,1); // discard stale walking queue on add
                bits.write(dy&31,5); // exact client order: relative Y then X
                bits.write(dx&31,5);
                byte[] tail=appearanceTail(remote);

                Track t=new Track(
                    remote.id(),
                    remoteGeneration,
                    idx,
                    remote.movement().x(),
                    remote.movement().y(),
                    remote.movement().plane()
                );
                t.appearanceHash=Arrays.hashCode(tail);
                Motion m=state.motions.get(remote.id());if(m!=null)t.lastMotionSeq=m.seq;
                Event e=state.latestEvent(remote.id());if(e!=null)t.lastEventSeq=e.seq;
                nextVisible.put(t.id,t);remoteTails.add(tail);
            }
            bits.write(SENTINEL,11);

            ByteArrayOutputStream out=new ByteArrayOutputStream(legacy.length+remoteTails.size()*80+16);
            out.write(bits.finish());
            if(local.maskTail.length>0)out.write(local.maskTail);
            for(byte[] tail:remoteTails)out.write(tail);
            prepared.visible.clear();
            prepared.visible.putAll(nextVisible);
            prepared.reservedIndexes.clear();
            prepared.reservedIndexes.putAll(
                workingReservedIndexes
            );
            prepared.publications.add(local);
            return out.toByteArray();
        }

        synchronized PreparedBatch beginPreparedBatch(){
            return new PreparedBatch(
                this,
                copyTracks(
                    visible
                ),
                new HashMap<>(
                    reservedIndexes
                )
            );
        }

        synchronized void commitPreparedBatch(
            PreparedBatch prepared
        ){
            if(prepared==null||
               prepared.context!=this||
               prepared.completed)
                return;

            for(LegacyLocal local:
                    prepared.publications)
                state.publish(
                    owner,
                    this,
                    local
                );

            visible.clear();
            visible.putAll(
                prepared.visible
            );
            reservedIndexes.clear();
            reservedIndexes.putAll(
                prepared.reservedIndexes
            );
            prepared.completed=true;
        }

        private static LinkedHashMap<EntityId,Track>
            copyTracks(
                Map<EntityId,Track> source
            ){
            LinkedHashMap<EntityId,Track> copy=
                new LinkedHashMap<>();

            for(Map.Entry<EntityId,Track> entry:
                    source.entrySet())
                copy.put(
                    entry.getKey(),
                    entry.getValue().copy()
                );

            return copy;
        }

        private int indexForPrepared(
            Map<EntityId,Integer> reserved,
            EntityId id
        ){
            Integer existing=
                reserved.get(id);

            if(existing!=null)
                return existing;

            boolean[] used=
                new boolean[2047];
            used[LOCAL_PLAYER_INDEX]=true;

            for(Integer value:
                    reserved.values())
                if(value>=0&&
                   value<used.length)
                    used[value]=true;

            for(int i=2;i<2047;i++)
                if(!used[i]){
                    reserved.put(
                        id,
                        i
                    );
                    return i;
                }

            throw new IllegalStateException(
                "no remote player indexes available"
            );
        }

        private boolean isVisible(WorldPlayer p){
            if(p==null||p==owner||!p.registered())return false;
            MovementState a=owner.movement(),b=p.movement();
            if(a.plane()!=b.plane())return false;
            return Math.abs(b.x()-a.x())<=15&&Math.abs(b.y()-a.y())<=15;
        }

        private int indexFor(EntityId id){
            Integer existing=reservedIndexes.get(id);if(existing!=null)return existing;
            boolean[] used=new boolean[2047];used[LOCAL_PLAYER_INDEX]=true;
            for(Integer v:reservedIndexes.values())if(v>=0&&v<used.length)used[v]=true;
            for(int i=2;i<2047;i++)if(!used[i]){reservedIndexes.put(id,i);return i;}
            throw new IllegalStateException("no remote player indexes available");
        }

        private byte[] appearanceTail(WorldPlayer p)throws IOException{
            Context rc=state.contexts.get(p.id());
            Integer morph=
                rc==null||
                !rc.ownerCurrent()||
                rc.dev==null
                    ?null
                    :rc.dev.playerNpcTransformId();
            byte[] block=
                BootstrapPackets.appearanceBlock(
                    p.username(),
                    p.equipment().appearanceItems(),
                    p.playerState(),
                    morph,
                    state.world.appearanceRoleFor(
                        p.username(),
                        p.playerState()
                    )
                );
            ByteArrayOutputStream out=new ByteArrayOutputStream(block.length+2);
            out.write(0x10);out.write((-block.length)&255);out.write(block);return out.toByteArray();
        }
    }

    private static final class WorldState {
        final World world;
        final HashMap<EntityId,Context> contexts=new HashMap<>();
        final HashMap<EntityId,Motion> motions=new HashMap<>();
        final HashMap<EntityId,ArrayDeque<Event>> events=new HashMap<>();
        final HashMap<String,TradeRequest> tradeRequests=
            new HashMap<>();
        long sequence;
        WorldState(World world){this.world=world;}

        synchronized void publish(WorldPlayer owner,Context ownerContext,LegacyLocal local){
            if(local.moveType==1||local.moveType==2||local.moveType==3)
                motions.put(owner.id(),new Motion(++sequence,local.moveType,local.dir1,local.dir2));
            if(local.maskTail.length>0){
                ArrayDeque<Event> q=events.get(owner.id());
                if(q==null){q=new ArrayDeque<Event>();events.put(owner.id(),q);}
                q.addLast(Event.from(++sequence,local.maskTail,ownerContext));
                while(q.size()>64)q.removeFirst();
            }
        }

        synchronized Event nextEventAfter(EntityId id,long seq){
            ArrayDeque<Event> q=events.get(id);
            if(q==null)return null;
            for(Event e:q)if(e.seq>seq)return e;
            return null;
        }

        synchronized Event latestEvent(EntityId id){
            ArrayDeque<Event> q=events.get(id);
            return q==null||q.isEmpty()?null:q.peekLast();
        }

        synchronized void removeOwner(EntityId ownerId){
            contexts.remove(ownerId);
            motions.remove(ownerId);
            events.remove(ownerId);
            String prefix=ownerId+">";
            String suffix=">"+ownerId;
            for(Iterator<Map.Entry<String,TradeRequest>> it=tradeRequests.entrySet().iterator();it.hasNext();){
                String key=it.next().getKey();
                if(key.startsWith(prefix)||key.endsWith(suffix))it.remove();
            }
        }

        synchronized String requestTrade(
            WorldPlayer from,
            WorldPlayer to,
            long now
        ){
            if(from==null||to==null||from==to)
                return "TRADE_REJECTED_INVALID_TARGET";

            long fromGeneration=
                from.generation();
            long toGeneration=
                to.generation();

            if(!world.players().owns(
                    from,
                    fromGeneration
                )||
               !world.players().owns(
                    to,
                    toGeneration
                ))
                return "TRADE_REJECTED_STALE_OWNER";

            String a=
                from.id()+">"+to.id();
            String b=
                to.id()+">"+from.id();

            TradeRequest reciprocal=
                tradeRequests.get(b);

            if(reciprocal!=null&&
               reciprocal.fromGeneration==
                    toGeneration&&
               reciprocal.toGeneration==
                    fromGeneration&&
               now-reciprocal.atMillis<=30_000L){
                tradeRequests.remove(a);
                tradeRequests.remove(b);

                return "TRADE_MUTUAL_ACCEPTED target="+
                    to.username()+
                    " itemExchange=DEFERRED_UNTIL_TRADE_INTERFACE_AUTHORITY";
            }

            tradeRequests.put(
                a,
                new TradeRequest(
                    now,
                    fromGeneration,
                    toGeneration
                )
            );

            Context targetContext=
                contexts.get(to.id());

            if(targetContext!=null&&
               targetContext.ownerCurrent()){
                try{
                    byte[] msg=
                        (from.username()+":tradereq:\n")
                            .getBytes(
                                StandardCharsets
                                    .ISO_8859_1
                            );

                    targetContext.writer
                        .varByte(
                            253,
                            msg
                        );
                }catch(IOException ioe){
                    return "TRADE_REQUEST_RECORDED_NOTIFY_FAILED target="+
                        to.username()+
                        " error="+
                        ioe.getClass()
                            .getSimpleName()+
                        " itemExchange=DEFERRED_UNTIL_TRADE_INTERFACE_AUTHORITY";
                }
            }

            return "TRADE_REQUEST_RECORDED_NOTIFY_SENT target="+
                to.username()+
                " packet253=:tradereq: reciprocalWindowMs=30000 itemExchange=DEFERRED_UNTIL_TRADE_INTERFACE_AUTHORITY";
        }
    }

    private static final class TradeRequest {
        final long atMillis;
        final long fromGeneration;
        final long toGeneration;

        TradeRequest(
            long atMillis,
            long fromGeneration,
            long toGeneration
        ){
            this.atMillis=atMillis;
            this.fromGeneration=fromGeneration;
            this.toGeneration=toGeneration;
        }
    }

    private static final class Track {
        final EntityId id;
        final long generation;
        final int clientIndex;
        int x,y,plane,appearanceHash;
        long lastMotionSeq,lastEventSeq;

        Track(
            EntityId id,
            long generation,
            int clientIndex,
            int x,
            int y,
            int plane
        ){
            this.id=id;
            this.generation=generation;
            this.clientIndex=clientIndex;
            this.x=x;
            this.y=y;
            this.plane=plane;
        }

        Track copy(){
            Track copy=
                new Track(
                    id,
                    generation,
                    clientIndex,
                    x,
                    y,
                    plane
                );
            copy.appearanceHash=appearanceHash;
            copy.lastMotionSeq=lastMotionSeq;
            copy.lastEventSeq=lastEventSeq;
            return copy;
        }
    }
    private static final class Motion {
        final long seq;final int type,dir1,dir2;
        Motion(long seq,int type,int dir1,int dir2){this.seq=seq;this.type=type;this.dir1=dir1;this.dir2=dir2;}
    }
    private static final class Event {
        final long seq;
        final byte[] tail;
        final int interactionOffset;
        final boolean playerInteraction;
        final EntityId targetEntity;
        final long targetGeneration;

        Event(
            long seq,
            byte[] tail,
            int interactionOffset,
            boolean playerInteraction,
            EntityId targetEntity,
            long targetGeneration
        ){
            this.seq=seq;
            this.tail=tail;
            this.interactionOffset=interactionOffset;
            this.playerInteraction=playerInteraction;
            this.targetEntity=targetEntity;
            this.targetGeneration=targetGeneration;
        }

        static Event from(
            long seq,
            byte[] tail,
            Context owner
        ){
            byte[] copy=tail.clone();
            int off=interactionOffset(copy);
            boolean player=false;
            EntityId target=null;
            long targetGeneration=-1L;

            if(off>=0&&off+1<copy.length){
                int raw=
                    (copy[off]&255)|
                    ((copy[off+1]&255)<<8);

                if(raw>=32768&&raw!=65535){
                    player=true;

                    Track targetTrack=
                        owner.visibleTrack(
                            raw-32768
                        );

                    if(targetTrack!=null){
                        target=targetTrack.id;
                        targetGeneration=
                            targetTrack.generation;
                    }
                }
            }

            return new Event(
                seq,
                copy,
                off,
                player,
                target,
                targetGeneration
            );
        }

        byte[] forViewer(Context viewer){
            byte[] out=tail.clone();

            if(playerInteraction&&
               interactionOffset>=0){
                int value=65535;

                if(targetEntity!=null){
                    WorldPlayer player=
                        viewer.state.world.players()
                            .byId(targetEntity);

                    if(player!=null&&
                       viewer.state.world.players()
                           .owns(
                               player,
                               targetGeneration
                           )){
                        int translated=
                            viewer.interactionTargetFor(
                                player
                            );

                        if(translated>=0)
                            value=translated;
                    }
                }

                out[interactionOffset]=
                    (byte)(value&255);
                out[interactionOffset+1]=
                    (byte)((value>>>8)&255);
            }

            return out;
        }
    }

    static final class LegacyLocal {
        final boolean update;final int moveType,dir1,dir2,oldRemoteCount,sentinel;final byte[] maskTail;
        final int teleportPlane,teleportClear,teleportMask,teleportY,teleportX;
        LegacyLocal(boolean update,int moveType,int dir1,int dir2,int oldRemoteCount,int sentinel,byte[] maskTail,
                    int teleportPlane,int teleportClear,int teleportMask,int teleportY,int teleportX){
            this.update=update;this.moveType=moveType;this.dir1=dir1;this.dir2=dir2;this.oldRemoteCount=oldRemoteCount;this.sentinel=sentinel;this.maskTail=maskTail;
            this.teleportPlane=teleportPlane;this.teleportClear=teleportClear;this.teleportMask=teleportMask;this.teleportY=teleportY;this.teleportX=teleportX;
        }
        static LegacyLocal parse(byte[] body){
            try{
                BitReader r=new BitReader(body);boolean update=r.read(1)==1;int type=-1,d1=-1,d2=-1,tp=-1,tc=0,tm=0,ty=0,tx=0;
                if(update){
                    type=r.read(2);
                    if(type==1){d1=r.read(3);r.read(1);} // mask bit is represented in tail
                    else if(type==2){d1=r.read(3);d2=r.read(3);r.read(1);}
                    else if(type==3){tp=r.read(2);tc=r.read(1);tm=r.read(1);ty=r.read(7);tx=r.read(7);}
                }
                int old=r.read(8);int sentinel=r.read(11);int bitBytes=(r.pos+7)/8;
                if(bitBytes>body.length)return null;
                byte[] tail=Arrays.copyOfRange(body,bitBytes,body.length);
                return new LegacyLocal(update,type,d1,d2,old,sentinel,tail,tp,tc,tm,ty,tx);
            }catch(RuntimeException e){return null;}
        }
        void writeLocal(BitWriter b){
            b.write(update?1:0,1);if(!update)return;
            b.write(moveType,2);
            if(moveType==1){b.write(dir1,3);b.write(maskTail.length>0?1:0,1);}
            else if(moveType==2){b.write(dir1,3);b.write(dir2,3);b.write(maskTail.length>0?1:0,1);}
            else if(moveType==3){b.write(teleportPlane,2);b.write(teleportClear,1);b.write(teleportMask,1);b.write(teleportY,7);b.write(teleportX,7);}
            // moveType 0 implies mask by protocol; no extra flag.
        }
    }

    private static final class BitReader {
        final byte[] b;int pos;
        BitReader(byte[] b){this.b=b;}
        int read(int n){if(n<0||n>31||pos+n>b.length*8)throw new IllegalArgumentException("bits");int v=0;for(int i=0;i<n;i++){int bit=(b[pos>>>3]>>(7-(pos&7)))&1;v=(v<<1)|bit;pos++;}return v;}
    }

    private static boolean signed5(int v){return v>=-16&&v<=15;}

    static int maskOfTail(byte[] tail){
        if(tail==null||tail.length==0)return 0;int low=tail[0]&255,mask=low;if((low&0x40)!=0&&tail.length>1)mask|=(tail[1]&255)<<8;return mask;
    }

    private static int interactionOffset(byte[] tail){
        if(tail==null||tail.length==0)return -1;
        int low=tail[0]&255,mask=low,p=1;if((low&0x40)!=0){if(p>=tail.length)return -1;mask|=(tail[p++]&255)<<8;}
        if((mask&0x400)!=0){p+=9;if(p>tail.length)return -1;}
        if((mask&0x100)!=0){p+=6;if(p>tail.length)return -1;}
        if((mask&0x8)!=0){p+=4;if(p>tail.length)return -1;}
        if((mask&0x4)!=0){while(p<tail.length&&tail[p++]!=10){}if(p>tail.length)return -1;}
        if((mask&0x80)!=0)return -1; // variable public-chat payload not emitted by LocalLab player81 today
        if((mask&0x1)!=0)return p+1<tail.length?p:-1;
        return -1;
    }
}