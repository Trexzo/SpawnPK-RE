package spk.local;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorldAutosaveCheckpointTest {
    public static void main(String[] args)throws Exception{
        testAutosaveCoalescesToNewestSnapshot();
        testBoundedWriteBackpressure();

        System.out.println(
            "WORLD_AUTOSAVE_CHECKPOINT_PASS "+
            "intervalTicks="+
                WorldPlayerPersistence.AUTOSAVE_INTERVAL_TICKS+
            " coalescedNewest=true "+
            "accessoryComplete=true "+
            "queueCapacity="+
                WorldPlayerPersistence.MAX_PENDING_WRITES+
            " backpressure=true"
        );
    }

    private static void testAutosaveCoalescesToNewestSnapshot()
        throws Exception{
        BlockingRepository repository=
            new BlockingRepository(
                true,
                2
            );

        World world=
            World.isolatedForTest(
                600L,
                repository
            );

        WorldPlayer player=
            new WorldPlayer();

        try{
            world.registerPlayer(
                player,
                "opensrc"
            );

            player.petAccessoryState()
                .setActiveItem(12345);
            player.movement()
                .setRunEnergy(10);

            pulse(
                world,
                WorldPlayerPersistence
                    .AUTOSAVE_INTERVAL_TICKS
            );

            if(!repository.firstStarted.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "first autosave did not start"
                );

            player.movement()
                .setRunEnergy(20);

            pulse(
                world,
                WorldPlayerPersistence
                    .AUTOSAVE_INTERVAL_TICKS
            );

            player.movement()
                .setRunEnergy(30);

            pulse(
                world,
                WorldPlayerPersistence
                    .AUTOSAVE_INTERVAL_TICKS
            );

            if(world.persistence()
                    .checkpointCapturedCount()!=3L)
                throw new AssertionError(
                    "checkpoint capture count="+
                    world.persistence()
                        .checkpointCapturedCount()
                );

            if(world.persistence()
                    .checkpointCoalescedCount()!=1L)
                throw new AssertionError(
                    "checkpoint coalesce count="+
                    world.persistence()
                        .checkpointCoalescedCount()
                );

            repository.releaseFirst.countDown();

            if(!repository.expectedWrites.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "coalesced autosaves did not finish"
                );

            List<Integer> energy=
                repository.energySnapshot();

            if(!energy.equals(
                    Arrays.asList(10,30)))
                throw new AssertionError(
                    "autosave did not preserve first + newest "+
                    energy
                );

            List<Integer> accessories=
                repository.accessorySnapshot();

            if(!accessories.equals(
                    Arrays.asList(12345,12345)))
                throw new AssertionError(
                    "world-owned accessory missing from checkpoint "+
                    accessories
                );

            if(world.persistence()
                    .checkpointWrittenCount()!=2L)
                throw new AssertionError(
                    "checkpoint write count="+
                    world.persistence()
                        .checkpointWrittenCount()
                );
        }finally{
            repository.releaseFirst.countDown();
            world.unregisterPlayer(player);
            world.close();
        }
    }

    private static void testBoundedWriteBackpressure()
        throws Exception{
        BlockingRepository repository=
            new BlockingRepository(
                true,
                1
            );

        World world=
            World.isolatedForTest(
                20L,
                repository
            );

        WorldPlayer player=
            new WorldPlayer();

        try{
            world.registerPlayer(
                player,
                "opensrc"
            );
            world.start();

            List<WorldPlayerPersistence.SaveTicket> tickets=
                Collections.synchronizedList(
                    new ArrayList<>()
                );

            world.submitAndWait(
                player,
                ()->{
                    for(int i=0;
                        i<
                            WorldPlayerPersistence
                                .MAX_PENDING_WRITES+
                            12;
                        i++){
                        player.movement()
                            .setRunEnergy(
                                1+(i%99)
                            );

                        tickets.add(
                            world.persistence()
                                .captureAndSave(
                                    "opensrc",
                                    player,
                                    0,
                                    "[backpressure-test] ",
                                    "BURST_"+i
                                )
                        );
                    }
                },
                5_000L
            );

            if(!repository.firstStarted.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "backpressure worker did not start"
                );

            if(world.persistence()
                    .queuedWrites()>
               WorldPlayerPersistence
                    .MAX_PENDING_WRITES)
                throw new AssertionError(
                    "write queue exceeded bound queued="+
                    world.persistence().queuedWrites()
                );

            int rejected=0;
            for(WorldPlayerPersistence.SaveTicket ticket:
                    tickets)
                if(ticket.completion
                        .isCompletedExceptionally())
                    rejected++;

            if(rejected==0)
                throw new AssertionError(
                    "bounded queue produced no rejected saves"
                );

            repository.releaseFirst.countDown();
        }finally{
            repository.releaseFirst.countDown();
            world.unregisterPlayer(player);
            world.close();
        }
    }

    private static void pulse(
        World world,
        long count
    ){
        for(long i=0;i<count;i++)
            world.observePulse(
                1_000L+i
            );
    }

    private static final class BlockingRepository
        implements PlayerRepository {

        final CountDownLatch firstStarted=
            new CountDownLatch(1);
        final CountDownLatch releaseFirst=
            new CountDownLatch(1);
        final CountDownLatch expectedWrites;
        final AtomicInteger calls=
            new AtomicInteger();

        private final boolean blockFirst;
        private final List<Integer> energy=
            Collections.synchronizedList(
                new ArrayList<>()
            );
        private final List<Integer> accessories=
            Collections.synchronizedList(
                new ArrayList<>()
            );

        BlockingRepository(
            boolean blockFirst,
            int expectedWrites
        ){
            this.blockFirst=blockFirst;
            this.expectedWrites=
                new CountDownLatch(
                    expectedWrites
                );
        }

        @Override public Optional<PlayerSnapshot> load(
            String username
        ){
            return Optional.empty();
        }

        @Override public void save(
            PlayerSnapshot snapshot
        )throws IOException{
            int call=calls.incrementAndGet();

            if(call==1){
                firstStarted.countDown();

                if(blockFirst)
                    try{
                        if(!releaseFirst.await(
                                5,
                                TimeUnit.SECONDS))
                            throw new IOException(
                                "test release timeout"
                            );
                    }catch(InterruptedException e){
                        Thread.currentThread()
                            .interrupt();
                        throw new IOException(
                            "interrupted",
                            e
                        );
                    }
            }

            energy.add(
                Integer.parseInt(
                    snapshot.value(
                        "movement.runEnergy"
                    )
                )
            );

            accessories.add(
                PlayerSnapshotCodec
                    .accessoryItem(
                        snapshot
                    )
            );

            expectedWrites.countDown();
        }

        List<Integer> energySnapshot(){
            synchronized(energy){
                return new ArrayList<>(
                    energy
                );
            }
        }

        List<Integer> accessorySnapshot(){
            synchronized(accessories){
                return new ArrayList<>(
                    accessories
                );
            }
        }
    }
}
