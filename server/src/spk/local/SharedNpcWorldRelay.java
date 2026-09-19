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
    private static final long MASK_TTL_MS=5000L;

    private SharedNpcWorldRelay(){}

    static synchronized void register(ServerPacketWriter writer,World world,WorldPlayer owner,NpcRegistry npcs,MovementState movement){
        if(writer==null||world==null||owner==null||npcs==null||movement==null)return;
        WorldState ws=BY_WORLD.get(world);if(ws==null){ws=new WorldState(world);BY_WORLD.put(world,ws);}
        Context old=BY_WRITER.remove(writer);if(old!=null)old.state.contexts.remove(old.owner.id());
        Context c=new Context(writer,ws,owner,npcs,movement);BY_WRITER.put(writer,c);ws.contexts.put(owner.id(),c);
    }

    static synchronized void unregister(ServerPacketWriter writer){
        Context c=BY_WRITER.remove(writer);if(c==null)return;
        c.state.contexts.remove(c.owner.id());
        try{c.removeAllRemotePets();}catch(Throwable ignored){}
        c.state.pruneDeadRecipients();
        if(c.state.contexts.isEmpty())BY_WORLD.remove(c.state.world);
    }

    static void syncRemotePets(ServerPacketWriter viewerWriter){
        Context c; synchronized(SharedNpcWorldRelay.class){c=BY_WRITER.get(viewerWriter);} if(c==null)return;
        try{c.syncRemotePets();}catch(Throwable t){System.err.println("[ENGINE-R3.2] remote pet sync failed viewer="+c.owner.id()+": "+t);}
    }

    /**
     * Called by NpcRegistry.sendMask after the owner-local packet has been emitted.
     * The semantic mask is queued once and translated against each viewer's scene
     * index when that viewer reaches the corresponding player-presentation barrier.
     */
    static void relayMask(ServerPacketWriter sourceWriter,NpcRegistry sourceNpcs,NpcEntity sourceTarget,NpcSyncEncoder.Mask mask){
        if(sourceWriter==null||sourceNpcs==null||sourceTarget==null||mask==null)return;
        synchronized(SharedNpcWorldRelay.class){
            Context src=BY_WRITER.get(sourceWriter);if(src==null)return;
            TargetRef target;
            if(sourceTarget==sourceNpcs.pet()) target=TargetRef.pet(sourceTarget.definitionId);
            else if(sourceTarget==sourceNpcs.miniPet()) target=TargetRef.mini(sourceTarget.definitionId);
            else target=TargetRef.scene(sourceTarget.sceneIndex,sourceTarget.definitionId);

            long barrier=Player81WorldSync.latestPublishedEventSequence(sourceWriter);
            LinkedHashSet<EntityId> recipients=new LinkedHashSet<>();
            for(Context c:src.state.contexts.values())if(c!=src&&!c.owner.id().equals(src.owner.id()))recipients.add(c.owner.id());
            if(recipients.isEmpty())return;
            long now=System.currentTimeMillis();
            // R3.1 had explicit combat relay callsites in addition to the semantic
            // NpcRegistry mask publication.  Suppress only same-tick semantic
            // duplicates while the old callsites are retained for binary compatibility.
            MaskEvent last=src.state.maskEvents.peekLast();
            if(last!=null && now-last.createdAt<=100L && last.playerBarrierSeq==barrier
                && last.sourceId.equals(src.owner.id()) && sameTarget(last.target,target)
                && sameMask(last.mask,mask)) return;
            src.state.maskEvents.addLast(new MaskEvent(++src.state.maskSequence,now,
                src.owner.id(),target,mask,barrier,recipients));
            while(src.state.maskEvents.size()>256)src.state.maskEvents.removeFirst();
        }
    }

    /**
     * Invoked immediately after a viewer's packet-81 body is appended to the
     * outbound stream.  Any NPC masks whose source-player barrier has now been
     * consumed are appended after it, preserving visible event order.
     */
    static void flushAfterPlayer81(ServerPacketWriter viewerWriter)throws IOException{
        Context viewer;
        ArrayList<MaskEvent> ready=new ArrayList<>();
        long now=System.currentTimeMillis();
        synchronized(SharedNpcWorldRelay.class){
            viewer=BY_WRITER.get(viewerWriter);if(viewer==null)return;
            Iterator<MaskEvent> it=viewer.state.maskEvents.iterator();
            while(it.hasNext()){
                MaskEvent e=it.next();
                if(now-e.createdAt>MASK_TTL_MS){it.remove();continue;}
                if(!e.recipients.contains(viewer.owner.id())||e.delivered.contains(viewer.owner.id()))continue;
                long consumed=Player81WorldSync.consumedEventSequence(viewerWriter,e.sourceId);
                if(e.playerBarrierSeq>0 && consumed<e.playerBarrierSeq)continue;
                ready.add(e);
            }
        }

        for(MaskEvent e:ready){
            NpcEntity target=viewer.resolve(e.sourceId,e.target);
            if(target==null)continue; // wait for remote pet add / viewport entry until TTL
            viewer.npcs.sendMaskLocal(target,e.mask,viewer.writer);
            synchronized(SharedNpcWorldRelay.class){e.delivered.add(viewer.owner.id());}
        }

        synchronized(SharedNpcWorldRelay.class){viewer.state.pruneDelivered(now);}
    }


    private static boolean sameTarget(TargetRef a,TargetRef b){
        return a!=null&&b!=null&&a.kind==b.kind&&a.scene==b.scene&&a.definition==b.definition;
    }

    private static boolean sameMask(NpcSyncEncoder.Mask a,NpcSyncEncoder.Mask b){
        if(a==b)return true;if(a==null||b==null)return false;
        return Objects.equals(a.animationId,b.animationId)
            && a.animationDelay==b.animationDelay
            && Objects.equals(a.interactionTarget,b.interactionTarget)
            && Objects.equals(a.hitDamage,b.hitDamage)
            && Objects.equals(a.gfxId,b.gfxId)
            && a.gfxHeight==b.gfxHeight
            && a.gfxDelay==b.gfxDelay
            && Objects.equals(a.forceText,b.forceText)
            && a.hitType==b.hitType
            && a.hitCycle==b.hitCycle
            && a.currentHp==b.currentHp
            && a.maxHp==b.maxHp;
    }

    private static final class WorldState{
        final World world;final HashMap<EntityId,Context> contexts=new HashMap<>();
        final ArrayDeque<MaskEvent> maskEvents=new ArrayDeque<>();
        long maskSequence;
        WorldState(World w){world=w;}

        void pruneDeadRecipients(){
            HashSet<EntityId> live=new HashSet<>(contexts.keySet());
            for(MaskEvent e:maskEvents)e.recipients.retainAll(live);
            pruneDelivered(System.currentTimeMillis());
        }
        void pruneDelivered(long now){
            Iterator<MaskEvent> it=maskEvents.iterator();
            while(it.hasNext()){
                MaskEvent e=it.next();
                if(now-e.createdAt>MASK_TTL_MS||e.delivered.containsAll(e.recipients)||e.recipients.isEmpty())it.remove();
            }
        }
    }

    private static final class TargetRef{
        static final int SCENE=0,PET=1,MINI=2;
        final int kind,scene,definition;
        TargetRef(int k,int s,int d){kind=k;scene=s;definition=d;}
        static TargetRef scene(int s,int d){return new TargetRef(SCENE,s,d);}
        static TargetRef pet(int d){return new TargetRef(PET,-1,d);}
        static TargetRef mini(int d){return new TargetRef(MINI,-1,d);}
    }

    private static final class MaskEvent{
        final long seq,createdAt,playerBarrierSeq;
        final EntityId sourceId;
        final TargetRef target;
        final NpcSyncEncoder.Mask mask;
        final LinkedHashSet<EntityId> recipients;
        final HashSet<EntityId> delivered=new HashSet<>();
        MaskEvent(long s,long at,EntityId src,TargetRef t,NpcSyncEncoder.Mask m,long barrier,LinkedHashSet<EntityId> r){
            seq=s;createdAt=at;sourceId=src;target=t;mask=m;playerBarrierSeq=barrier;recipients=r;
        }
    }

    private static final class RemotePetTrack{
        int mainScene=-1,miniScene=-1,mainDef=-1,miniDef=-1;
        int mainX,mainY,miniX,miniY;
        Integer mainParticleSelector;
    }

    private static final class Context{
        final ServerPacketWriter writer;final WorldState state;final WorldPlayer owner;final NpcRegistry npcs;final MovementState movement;
        final HashMap<EntityId,RemotePetTrack> remote=new HashMap<>();
        Context(ServerPacketWriter w,WorldState s,WorldPlayer o,NpcRegistry n,MovementState m){writer=w;state=s;owner=o;npcs=n;movement=m;}

        void syncRemotePets()throws IOException{
            ArrayList<Context> sources;
            synchronized(SharedNpcWorldRelay.class){sources=new ArrayList<>(state.contexts.values());}
            HashSet<EntityId> live=new HashSet<>();
            for(Context src:sources){
                if(src==this)continue;
                int playerIndex=Player81WorldSync.clientIndexFor(writer,src.owner);
                if(playerIndex<0){removeRemote(src.owner.id());continue;}
                NpcEntity pet=src.npcs.pet(),mini=src.npcs.miniPet();
                if(pet==null){removeRemote(src.owner.id());continue;}
                live.add(src.owner.id());
                RemotePetTrack t=remote.get(src.owner.id());if(t==null){t=new RemotePetTrack();remote.put(src.owner.id(),t);}

                Integer selector=src.npcs.petParticleSelector();
                boolean selectorChanged=t.mainScene>=0&&!Objects.equals(t.mainParticleSelector,selector);
                int previousMainScene=t.mainScene;
                if(selectorChanged){
                    if(t.mainScene>=0)npcs.devRemoveNpc(t.mainScene,writer);
                    t.mainScene=-1;t.mainDef=-1;
                }

                boolean mainWasAbsent=t.mainScene<0;
                t.mainScene=syncOne(t.mainScene,t.mainDef,pet.definitionId,t.mainX,t.mainY,pet.x,pet.y,32768+playerIndex,selector);
                t.mainDef=pet.definitionId;t.mainX=pet.x;t.mainY=pet.y;t.mainParticleSelector=selector;

                boolean mainRespawned=mainWasAbsent||selectorChanged||previousMainScene!=t.mainScene;
                if(mainRespawned&&t.mainScene>=0&&PetPresentationProfile.supportsNativeState(pet.definitionId)){
                    int nativeState=src.npcs.petNativeState();
                    if(nativeState!=0){
                        NpcEntity mirrored=npcs.scene(t.mainScene);
                        if(mirrored!=null)npcs.sendMaskLocal(mirrored,NpcSyncEncoder.Mask.forceText(Integer.toString(nativeState)),writer);
                    }
                }

                if(mini!=null && t.mainScene>=0){
                    int oldMiniScene=t.miniScene;
                    t.miniScene=syncOne(t.miniScene,t.miniDef,mini.definitionId,t.miniX,t.miniY,mini.x,mini.y,t.mainScene,null);
                    t.miniDef=mini.definitionId;t.miniX=mini.x;t.miniY=mini.y;
                    if(t.miniScene>=0&&(mainRespawned||oldMiniScene!=t.miniScene)){
                        NpcEntity mirroredMini=npcs.scene(t.miniScene);
                        if(mirroredMini!=null)npcs.sendMaskLocal(mirroredMini,NpcSyncEncoder.Mask.interactionTarget(t.mainScene),writer);
                    }
                }else if(t.miniScene>=0){npcs.devRemoveNpc(t.miniScene,writer);t.miniScene=-1;t.miniDef=-1;}
            }
            ArrayList<EntityId> stale=new ArrayList<>();for(EntityId id:remote.keySet())if(!live.contains(id))stale.add(id);for(EntityId id:stale)removeRemote(id);
        }

        int syncOne(int scene,int oldDef,int def,int oldX,int oldY,int x,int y,int interactionTarget,Integer particleSelector)throws IOException{
            if(Math.abs(x-movement.x())>15||Math.abs(y-movement.y())>15){if(scene>=0)npcs.devRemoveNpc(scene,writer);return -1;}
            if(scene<0||oldDef!=def||npcs.scene(scene)==null){
                if(scene>=0)npcs.devRemoveNpc(scene,writer);
                NpcEntity e=npcs.spawnMirroredNpc(def,x,y,particleSelector,movement,writer);
                if(e!=null)npcs.sendMaskLocal(e,NpcSyncEncoder.Mask.interactionTarget(interactionTarget),writer);
                return e==null?-1:e.sceneIndex;
            }
            NpcEntity e=npcs.scene(scene);if(e==null)return -1;
            int dx=x-oldX,dy=y-oldY;
            if(dx==0&&dy==0)return scene;
            int d1=-1,d2=-1;
            if(Math.abs(dx)<=1&&Math.abs(dy)<=1){d1=MovementState.direction(oldX,oldY,x,y);}
            else if(Math.abs(dx)<=2&&Math.abs(dy)<=2){
                int mx=oldX+Integer.signum(dx),my=oldY+Integer.signum(dy);d1=MovementState.direction(oldX,oldY,mx,my);d2=MovementState.direction(mx,my,x,y);
            }
            if(d1<0 || (Math.max(Math.abs(dx),Math.abs(dy))>1&&d2<0)){
                npcs.devRemoveNpc(scene,writer);return syncOne(-1,-1,def,x,y,x,y,interactionTarget,particleSelector);
            }
            ArrayList<NpcSyncEncoder.Update> ups=new ArrayList<>();
            for(NpcEntity n:npcs.snapshot()){
                if(n.sceneIndex==scene){ups.add(d2>=0?NpcSyncEncoder.Update.run(n,d1,d2):NpcSyncEncoder.Update.walk(n,d1));}
                else ups.add(NpcSyncEncoder.Update.retain(n));
            }
            writer.varShort(65,NpcSyncEncoder.encode(ups,Collections.<NpcEntity>emptyList(),0,0));
            e.x=x;e.y=y;
            return scene;
        }

        NpcEntity resolve(EntityId sourceId,TargetRef ref){
            if(ref.kind==TargetRef.SCENE){
                NpcEntity same=npcs.scene(ref.scene);
                return same!=null&&same.definitionId==ref.definition?same:null;
            }
            RemotePetTrack t=remote.get(sourceId);if(t==null)return null;
            int scene=ref.kind==TargetRef.PET?t.mainScene:t.miniScene;
            if(scene<0)return null;
            NpcEntity e=npcs.scene(scene);
            return e!=null&&e.definitionId==ref.definition?e:null;
        }

        void removeRemote(EntityId id)throws IOException{
            RemotePetTrack t=remote.remove(id);if(t==null)return;
            if(t.miniScene>=0)npcs.devRemoveNpc(t.miniScene,writer);
            if(t.mainScene>=0)npcs.devRemoveNpc(t.mainScene,writer);
        }
        void removeAllRemotePets()throws IOException{for(EntityId id:new ArrayList<>(remote.keySet()))removeRemote(id);}
    }
}
