package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * P5.19 canonical NPC registry + lifecycle admission ownership.
 * This is validation-only; no runes, hit damage, XP or native GUI.
 */
public final class PlayableCanonicalSpellNpcLeaseTest {
    private static final int[] SEED={271,272,273,274};
    private static final int SPELL=19100;

    private PlayableCanonicalSpellNpcLeaseTest(){}

    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(
            "P519_CANONICAL_MAGIC_NPC_LEASE "+why);
    }

    private static SpellTargetRequest npc(int scene){
        return new SpellTargetRequest(
            131,SpellTargetRequest.Kind.NPC,SPELL,
            scene,-1,-1,-1,-1,-1);
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream out
    ){
        return new ServerPacketWriter(
            out,new IsaacCipher(SEED.clone()));
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected CLI args");

        World world=World.isolatedForTest(600L);
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(
            owner,"p519-canonical-npc");
        NpcRegistry scene=new NpcRegistry(
            new DevAuthorityWorkbench(),
            world.petNpcs(),owner.id());
        ByteArrayOutputStream relayBytes=
            new ByteArrayOutputStream();
        ServerPacketWriter relay=writer(relayBytes);
        ByteArrayOutputStream castBytes=
            new ByteArrayOutputStream();
        ServerPacketWriter casts=writer(castBytes);
        boolean relayRegistered=false;
        WorldNpc canonical=null;

        try{
            SharedNpcWorldRelay.register(
                relay,world,owner,scene,owner.movement());
            relayRegistered=true;
            canonical=world.npcs().spawn(
                100,3088,3495,0);
            world.npcLifecycle().register(
                canonical,10,"CUSTOM_LOCALLAB_P519_NPC_HP");
            SharedNpcWorldRelay.trackCanonicalNpc(world,canonical);
            SharedNpcWorldRelay.syncRemotePets(relay);
            NpcEntity view=scene.canonical(canonical.id);
            require(view!=null&&
                view.canonicalId().equals(canonical.id),
                "canonical scene projection absent");

            final SpellTargetRequest request=npc(view.sceneIndex);
            LocalSpellTargetHandler handler=
                new LocalSpellTargetHandler(
                    world,owner,()->generation,scene,
                    new CombatEngine());

            String valid=handler.handle(request,casts);
            require(valid.contains("TARGET_NPC_VISIBLE def=100")&&
                owner.magic().last().startsWith("TARGET_ACCEPT")&&
                castBytes.size()==0,
                "live canonical target validation regressed");
            String last=owner.magic().last();

            // Lifecycle can be unregistered independently of the World
            // NPC registry. A visible, still-canonical scene alone is not
            // authorization for another spell.
            require(world.npcLifecycle().unregisterExact(canonical),
                "lifecycle unregister refused");
            String missing=handler.handle(request,casts);
            require(missing.contains(
                    "REJECTED_CANONICAL_NPC_NOT_ALIVE")&&
                last.equals(owner.magic().last())&&
                castBytes.size()==0,
                "missing lifecycle generated phantom acceptance");

            // New registration of the same canonical object is accepted
            // only after a real live lifecycle has been established.
            world.npcLifecycle().register(
                canonical,10,"CUSTOM_LOCALLAB_P519_NPC_HP");
            String restored=handler.handle(request,casts);
            require(restored.contains(
                    "TARGET_NPC_VISIBLE def=100")&&
                castBytes.size()==0,
                "new valid NPC lifecycle rejected");
            last=owner.magic().last();

            // Deterministically queue a cast behind the canonical NPC
            // registry lock, retire the exact NPC before releasing it,
            // and verify the waiting cast cannot invent a successful hit.
            CountDownLatch castStarted=new CountDownLatch(1);
            AtomicReference<String> answer=new AtomicReference<>();
            AtomicReference<Throwable> failure=
                new AtomicReference<>();
            Thread pending=new Thread(()->{
                castStarted.countDown();
                try{
                    answer.set(handler.handle(request,casts));
                }catch(Throwable error){
                    failure.set(error);
                }
            },"p519-pending-target");
            synchronized(world.npcs()){
                pending.start();
                require(castStarted.await(5L,TimeUnit.SECONDS),
                    "queued cast did not start");
                require(world.npcLifecycle().unregisterExact(canonical),
                    "concurrent lifecycle retire refused");
                require(world.npcs().remove(canonical.id),
                    "concurrent canonical NPC retire refused");
            }
            pending.join(5000L);
            require(!pending.isAlive()&&
                failure.get()==null&&
                answer.get()!=null&&
                answer.get().contains("REJECTED_STALE_CANONICAL_NPC")&&
                last.equals(owner.magic().last())&&
                castBytes.size()==0,
                "queued cast accepted retired canonical NPC");

            System.out.println(
                "P519_CANONICAL_MAGIC_NPC_LEASE_PASS"+
                " liveRoutingPreserved=true"+
                " lifecycleMissingDenied=true"+
                " lifecycleReRegistrationChecked=true"+
                " retiredBeforeAdmissionDenied=true"+
                " magicLastPreservedOnDenial=true"+
                " zeroPacketOnDenial=true"+
                " runeDamageXpAndNativeGuiUnverified=true");
        }finally{
            if(canonical!=null)
                SharedNpcWorldRelay.untrackCanonicalNpc(
                    world,canonical.id);
            if(relayRegistered)
                SharedNpcWorldRelay.unregister(relay);
            if(canonical!=null){
                world.npcLifecycle().unregisterExact(canonical);
                world.npcs().remove(canonical.id);
            }
            if(world.players().owns(owner,generation))
                world.unregisterPlayer(owner,generation);
            if(!world.closed())world.close();
        }
    }
}
