package spk.local;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real FIFO loader identity fence. Explicit forensic reads stay untrusted. */
public final class WorldPersistenceLoadOwnerIdentityRegression {
    private WorldPersistenceLoadOwnerIdentityRegression(){}

    private static final class Repository implements PlayerRepository {
        volatile PlayerSnapshot response;
        volatile CountDownLatch entered;
        volatile CountDownLatch release;
        final AtomicInteger reads=new AtomicInteger();
        final AtomicInteger writes=new AtomicInteger();
        final AtomicReference<String> thread=new AtomicReference<>();

        @Override public Optional<PlayerSnapshot> load(String username)
            throws IOException {
            reads.incrementAndGet();
            thread.set(Thread.currentThread().getName());
            CountDownLatch e=entered,r=release;
            if(e!=null&&r!=null){
                entered=null;
                release=null;
                e.countDown();
                try {
                    if(!r.await(5,TimeUnit.SECONDS))
                        throw new IOException("identity test repository timeout");
                }catch(InterruptedException x){
                    Thread.currentThread().interrupt();
                    throw new IOException("identity test interrupted",x);
                }
            }
            return Optional.ofNullable(response);
        }
        @Override public void save(PlayerSnapshot snapshot)
            throws IOException {writes.incrementAndGet();}
    }

    private static PlayerSnapshot image(String account,int energy){
        WorldPlayer staged=new WorldPlayer();
        staged.movement().setRunEnergy(energy);
        return PlayerSnapshotCodec.capture(account,staged,0);
    }

    private static void check(boolean good,String reason){
        if(!good)throw new AssertionError("WORLD_LOAD_IDENTITY "+reason);
    }

    private static void reject(WorldPlayerPersistence persistence,String name)
        throws Exception {
        boolean failed=false;
        try{
            persistence.load(name);
        }catch(IOException expected){
            failed=expected.getMessage()!=null&&expected.getMessage()
                .contains("ACCOUNT_LOAD_WORKER_PROFILE_MISMATCH");
        }
        check(failed,"foreign load escaped worker name="+name);
    }

    public static void main(String[] args)throws Exception {
        Repository repository=new Repository();
        World world=World.isolatedForTest(60_000L,repository);
        try{
            world.start();
            WorldPlayerPersistence persistence=world.persistence();
            repository.response=image("another-owner",17);
            repository.entered=new CountDownLatch(1);
            repository.release=new CountDownLatch(1);
            CountDownLatch arrived=repository.entered;
            CountDownLatch allow=repository.release;
            AtomicReference<Throwable> result=new AtomicReference<>();
            Thread caller=new Thread(()->{
                try{
                    persistence.load("mixedcase.owner");
                    result.set(new AssertionError("foreign snapshot escaped"));
                }catch(Throwable error){result.set(error);}
            },"concurrent-session-load");
            caller.start();
            try{
                check(arrived.await(5,TimeUnit.SECONDS),
                    "FIFO repository was not entered");
                check(caller.isAlive(),
                    "future completed before repository returned");
            }finally{allow.countDown();}
            caller.join(5_000L);
            check(!caller.isAlive()&&result.get() instanceof IOException&&
                result.get().getMessage().contains(
                    "ACCOUNT_LOAD_WORKER_PROFILE_MISMATCH"),
                "future did not propagate worker identity veto");
            check(repository.thread.get().startsWith(
                "spk-player-persistence-"),
                "load identity gate bypassed FIFO worker");

            Optional<PlayerSnapshot> untrusted=
                persistence.observeUntrustedMailboxAccount("mixedcase.owner");
            check(untrusted.isPresent()&&
                "another-owner".equals(untrusted.get().username())&&
                "17".equals(untrusted.get().value("movement.runEnergy")),
                "read-only forensic API incorrectly trusted or quarantined");
            reject(persistence,"mixedcase.owner");
            reject(persistence," MiXeDcAsE.OwNeR ");
            reject(persistence,"");

            repository.response=image("MIXEDCASE.OWNER",64);
            Optional<PlayerSnapshot> allowed=persistence.load("mixedcase.owner");
            check(allowed.isPresent()&&
                "mixedcase.owner".equals(allowed.get().username())&&
                "64".equals(allowed.get().value("movement.runEnergy")),
                "matching canonical owner rejected");
            allowed=persistence.load("  MIXEDCASE.owner  ");
            check(allowed.isPresent()&&
                "mixedcase.owner".equals(allowed.get().username()),
                "legitimate casing/whitespace alias rejected");

            repository.response=null;
            check(!persistence.load("mixedcase.owner").isPresent(),
                "missing account changed");
            check(repository.reads.get()==8&&
                repository.writes.get()==0,
                "invalid repository call counts "+repository.reads.get());
            System.out.println("WORLD_PERSISTENCE_LOAD_OWNER_IDENTITY_PASS"+
                " blockedForeignFifoDenied=true"+
                " workerExceptionPropagated=true"+
                " forensicUntrustedObservationPreserved=true"+
                " matchingCanonicalAlias=true"+
                " missingAccountUnchanged=true"+
                " noRepositoryWrites=true");
        }finally{world.close();}
    }
}
