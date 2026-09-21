package spk.local;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WorldPlayerPersistenceCheckpointShutdownTest {
    public static void main(String[] args)throws Exception{
        UninterruptibleRepository repository=
            new UninterruptibleRepository();

        World world=
            World.isolatedForTest(
                60_000L,
                repository
            );

        WorldPlayer player=
            new WorldPlayer();

        try{
            world.registerPlayer(
                player,
                LocalAccountProfiles.PRIMARY
            );
            world.start();

            world.submitAndWait(
                player,
                ()->world.persistence()
                    .checkpointDue(
                        WorldPlayerPersistence
                            .AUTOSAVE_INTERVAL_TICKS
                    ),
                5_000L
            );

            if(!repository.started.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "first checkpoint save did not start"
                );

            world.submitAndWait(
                player,
                ()->world.persistence()
                    .checkpointDue(
                        WorldPlayerPersistence
                            .AUTOSAVE_INTERVAL_TICKS*2L
                    ),
                5_000L
            );

            WorldPlayerPersistence persistence=
                world.persistence();

            if(persistence
                    .checkpointCapturedCount()!=2L||
               persistence
                    .checkpointCoalescedCount()!=1L)
                throw new AssertionError(
                    "checkpoint fixture setup failed "+
                    persistence.metrics()
                );

            if(repository.saves.get()!=1)
                throw new AssertionError(
                    "coalesced checkpoint wrote early saves="+
                    repository.saves.get()
                );

            long started=
                System.nanoTime();

            persistence.close();

            long closeMillis=
                TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime()-started
                );

            if(closeMillis<5_500L)
                throw new AssertionError(
                    "forced shutdown path not exercised closeMillis="+
                    closeMillis
                );

            if(repository.finished.getCount()==0L)
                throw new AssertionError(
                    "repository unexpectedly terminated during close"
                );

            if(repository.interrupts.get()==0)
                throw new AssertionError(
                    "shutdownNow did not interrupt active checkpoint save"
                );

            if(repository.saves.get()!=1)
                throw new AssertionError(
                    "coalesced checkpoint wrote before release saves="+
                    repository.saves.get()
                );

            if(persistence
                    .checkpointRejectedCount()!=1L||
               persistence.failedCount()!=1L||
               persistence
                    .checkpointWrittenCount()!=0L)
                throw new AssertionError(
                    "forced checkpoint shutdown accounting "+
                    persistence.metrics()
                );

            String beforeRelease=
                persistence.metrics();

            repository.release.countDown();

            if(!repository.finished.await(
                    3,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "repository did not finish after release"
                );

            persistence.close();

            if(repository.saves.get()!=1)
                throw new AssertionError(
                    "coalesced checkpoint wrote after close saves="+
                    repository.saves.get()
                );

            if(persistence
                    .checkpointRejectedCount()!=1L||
               persistence.failedCount()!=1L||
               persistence
                    .checkpointWrittenCount()!=0L)
                throw new AssertionError(
                    "late checkpoint return changed accounting "+
                    persistence.metrics()
                );

            if(!beforeRelease.equals(
                    persistence.metrics()))
                throw new AssertionError(
                    "metrics changed after close/release before="+
                    beforeRelease+
                    " after="+
                    persistence.metrics()
                );

            System.out.println(
                "WORLD_PLAYER_PERSISTENCE_CHECKPOINT_SHUTDOWN_PASS "+
                "activeRejected=true "+
                "coalescedDropped=true "+
                "lateReturnIgnored=true "+
                "repositoryWrites=1 "+
                "captured=2 coalesced=1 rejected=1 written=0"
            );
        }finally{
            repository.release.countDown();

            if(player.registered())
                world.unregisterPlayer(
                    player
                );

            world.close();
        }
    }

    private static final class UninterruptibleRepository
        implements PlayerRepository {

        final CountDownLatch started=
            new CountDownLatch(1);
        final CountDownLatch release=
            new CountDownLatch(1);
        final CountDownLatch finished=
            new CountDownLatch(1);
        final AtomicInteger interrupts=
            new AtomicInteger();
        final AtomicInteger saves=
            new AtomicInteger();

        @Override public Optional<PlayerSnapshot> load(
            String username
        ){
            return Optional.empty();
        }

        @Override public void save(
            PlayerSnapshot snapshot
        )throws IOException{
            saves.incrementAndGet();
            started.countDown();

            try{
                while(true){
                    try{
                        release.await();
                        return;
                    }catch(InterruptedException ignored){
                        interrupts.incrementAndGet();
                    }
                }
            }finally{
                finished.countDown();
            }
        }
    }

    private WorldPlayerPersistenceCheckpointShutdownTest(){}
}
