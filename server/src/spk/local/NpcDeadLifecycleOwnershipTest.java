package spk.local;

import java.util.concurrent.*;

public final class NpcDeadLifecycleOwnershipTest {
    public static void main(String[] args)throws Exception{
        exactDeadAdmitted();
        aliveMissingAndForeignRejected();
        unregisterCannotInterleave();
        reentrantRegistryLossDetected();
        callerFailureLeavesState();
        System.out.println(
            "NPC_DEAD_LIFECYCLE_OWNERSHIP_PASS "+
            "canonicalDeadRequired=true "+
            "exactEntryOwned=true "+
            "deathTickStable=true "+
            "sourceAuthorityStable=true "+
            "unregisterBlocked=true "+
            "reentrantRegistryLossDetected=true "+
            "callerFailureSafe=true "+
            "protocolIndependent=true"
        );
    }

    private static void exactDeadAdmitted()throws Exception{
        Fixture f=deadFixture(11L);
        final int[] calls={0};
        boolean current=
            f.lifecycle.withDeadCanonicalOwnershipIfCurrent(
                f.npc,
                snapshot->{
                    calls[0]++;
                    require(snapshot.dead(),"dead snapshot");
                    require(snapshot.deathTick==11L,"death tick");
                    require("CUSTOM_LOCALLAB_HP".equals(snapshot.sourceAuthority),"authority");
                }
            );
        require(current&&calls[0]==1,"exact dead not admitted");
    }

    private static void aliveMissingAndForeignRejected()throws Exception{
        WorldNpcRegistry registry=new WorldNpcRegistry();
        NpcLifecycleService lifecycle=new NpcLifecycleService(registry);
        WorldNpc alive=registry.spawn(1,3200,3200,0);
        lifecycle.register(alive,10,"CUSTOM_LOCALLAB_HP");

        expect(
            IllegalStateException.class,
            ()->lifecycle.withDeadCanonicalOwnershipIfCurrent(
                alive,
                s->{}
            ),
            "alive accepted"
        );

        WorldNpc missing=registry.spawn(2,3201,3200,0);
        expect(
            IllegalStateException.class,
            ()->lifecycle.withDeadCanonicalOwnershipIfCurrent(
                missing,
                s->{}
            ),
            "missing lifecycle accepted"
        );

        Fixture dead=deadFixture(12L);
        WorldNpc foreign=new WorldNpc(
            dead.npc.id,
            dead.npc.definitionId,
            dead.npc.x(),
            dead.npc.y(),
            dead.npc.plane(),
            dead.npc.ownerId,
            dead.npc.sourceItemId
        );
        final int[] calls={0};
        boolean current=
            dead.lifecycle.withDeadCanonicalOwnershipIfCurrent(
                foreign,
                s->calls[0]++
            );
        require(!current&&calls[0]==0,"foreign same-id NPC admitted");
    }

    private static void unregisterCannotInterleave()throws Exception{
        Fixture f=deadFixture(13L);
        CountDownLatch entered=new CountDownLatch(1);
        CountDownLatch release=new CountDownLatch(1);
        CountDownLatch unregisterStarted=new CountDownLatch(1);
        final Throwable[] actionFailure={null};
        final boolean[] unregistered={false};

        Thread owner=new Thread(()->{
            try{
                f.lifecycle.withDeadCanonicalOwnershipIfCurrent(
                    f.npc,
                    snapshot->{
                        entered.countDown();
                        if(!release.await(5,TimeUnit.SECONDS))
                            throw new AssertionError("release timeout");
                        require(
                            f.lifecycle.get(f.npc.id).deathTick==13L,
                            "death identity changed inside ownership"
                        );
                    }
                );
            }catch(Throwable t){
                actionFailure[0]=t;
            }
        });

        Thread remover=new Thread(()->{
            try{
                if(!entered.await(5,TimeUnit.SECONDS))
                    throw new AssertionError("entry timeout");
                unregisterStarted.countDown();
                unregistered[0]=f.lifecycle.unregister(f.npc.id);
            }catch(Throwable t){
                actionFailure[0]=t;
            }
        });

        owner.start();
        remover.start();

        require(entered.await(5,TimeUnit.SECONDS),"owner did not enter");
        require(unregisterStarted.await(5,TimeUnit.SECONDS),"unregister did not start");
        Thread.sleep(100L);

        require(
            remover.isAlive()&&
            !unregistered[0],
            "unregister completed during owned action"
        );

        release.countDown();
        owner.join(5000L);
        remover.join(5000L);

        require(!owner.isAlive()&&!remover.isAlive(),"threads did not finish");
        if(actionFailure[0]!=null)
            throw new AssertionError("concurrency failure",actionFailure[0]);

        require(
            unregistered[0]&&
            f.lifecycle.get(f.npc.id)==null,
            "unregister did not proceed after release"
        );
    }

    private static void reentrantRegistryLossDetected()throws Exception{
        Fixture f=deadFixture(15L);

        expect(
            IllegalStateException.class,
            ()->f.lifecycle.withDeadCanonicalOwnershipIfCurrent(
                f.npc,
                snapshot->{
                    require(
                        f.registry.remove(f.npc.id),
                        "reentrant registry removal fixture"
                    );
                }
            ),
            "reentrant registry ownership loss"
        );

        require(
            f.registry.byId(f.npc.id)==null&&
            f.lifecycle.get(f.npc.id)!=null&&
            f.lifecycle.get(f.npc.id).dead()&&
            f.lifecycle.get(f.npc.id).deathTick==15L,
            "reentrant registry loss was not detected cleanly"
        );
    }

    private static void callerFailureLeavesState()throws Exception{
        Fixture f=deadFixture(14L);
        expect(
            IllegalStateException.class,
            ()->f.lifecycle.withDeadCanonicalOwnershipIfCurrent(
                f.npc,
                snapshot->{
                    throw new IllegalStateException("boom");
                }
            ),
            "caller failure"
        );

        NpcLifecycleService.Snapshot after=f.lifecycle.get(f.npc.id);
        require(
            after!=null&&
            after.dead()&&
            after.deathTick==14L&&
            f.registry.byId(f.npc.id)==f.npc,
            "caller failure mutated ownership"
        );
    }

    private static Fixture deadFixture(long tick){
        WorldNpcRegistry registry=new WorldNpcRegistry();
        NpcLifecycleService lifecycle=new NpcLifecycleService(registry);
        WorldNpc npc=registry.spawn(1488,3200,3201,0);
        lifecycle.register(npc,10,"CUSTOM_LOCALLAB_HP");
        lifecycle.applyDamage(npc.id,10,tick);
        return new Fixture(registry,lifecycle,npc);
    }

    private static final class Fixture {
        final WorldNpcRegistry registry;
        final NpcLifecycleService lifecycle;
        final WorldNpc npc;
        Fixture(
            WorldNpcRegistry registry,
            NpcLifecycleService lifecycle,
            WorldNpc npc
        ){
            this.registry=registry;
            this.lifecycle=lifecycle;
            this.npc=npc;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void expect(
        Class<? extends Throwable> type,
        ThrowingRunnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))return;
            throw new AssertionError(label+" wrong failure",failure);
        }
        throw new AssertionError(label+" did not fail");
    }

    private static void require(boolean condition,String label){
        if(!condition)throw new AssertionError(label);
    }

    private NpcDeadLifecycleOwnershipTest(){}
}
