package spk.local;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class ConcurrentCanonicalProfileAllocationTest {
    public static void main(String[] args)throws Exception{
        BlockingRepository repository=new BlockingRepository();
        World world=World.isolatedForTest(50L,repository);
        ExecutorService executor=Executors.newFixedThreadPool(2);

        WorldPlayer firstPlayer=new WorldPlayer();
        WorldPlayer secondPlayer=new WorldPlayer();

        try{
            Future<LocalSessionPlayerInitializer.Result> first=
                executor.submit(
                    ()->initializer(world,firstPlayer)
                        .initialize(
                            LocalAccountProfiles.PRIMARY,
                            "[concurrent-profile-first] "
                        )
                );

            if(!repository.firstLoadEntered.await(
                    3,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "first opensrc load did not begin"
                );

            CountDownLatch secondAttempting=
                new CountDownLatch(1);

            Future<LocalSessionPlayerInitializer.Result> second=
                executor.submit(
                    ()->{
                        secondAttempting.countDown();
                        return initializer(
                            world,
                            secondPlayer
                        ).initialize(
                            LocalAccountProfiles.PRIMARY,
                            "[concurrent-profile-second] "
                        );
                    }
                );

            if(!secondAttempting.await(
                    1,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "second canonical initialization did not start"
                );

            boolean secondLoadOverlapped=
                repository.secondLoadEntered.await(
                    750,
                    TimeUnit.MILLISECONDS
                );

            repository.releaseFirstLoad.countDown();

            LocalSessionPlayerInitializer.Result firstResult=
                first.get(
                    4,
                    TimeUnit.SECONDS
                );

            LocalSessionPlayerInitializer.Result secondResult=
                second.get(
                    4,
                    TimeUnit.SECONDS
                );

            if(secondLoadOverlapped)
                throw new AssertionError(
                    "second repository load overlapped first profile allocation"
                );

            if(!LocalAccountProfiles.PRIMARY.equals(
                    firstResult.username))
                throw new AssertionError(
                    "first canonical profile="+
                    firstResult.username
                );

            if(!LocalAccountProfiles.SECONDARY.equals(
                    secondResult.username))
                throw new AssertionError(
                    "second canonical profile="+
                    secondResult.username
                );

            List<String> loads=
                repository.loadsSnapshot();

            if(!loads.equals(
                    Arrays.asList(
                        LocalAccountProfiles.PRIMARY,
                        LocalAccountProfiles.SECONDARY
                    )))
                throw new AssertionError(
                    "repository load order="+loads
                );

            if(world.players().size()!=2||
               world.players().byName(
                   LocalAccountProfiles.PRIMARY
               )!=firstPlayer||
               world.players().byName(
                   LocalAccountProfiles.SECONDARY
               )!=secondPlayer)
                throw new AssertionError(
                    "final canonical membership incorrect"
                );

            System.out.println(
                "CONCURRENT_CANONICAL_PROFILE_ALLOCATION_PASS "+
                "overlapPrevented=true "+
                "loads=opensrc,src "+
                "members=2"
            );
        }finally{
            repository.releaseFirstLoad.countDown();

            executor.shutdownNow();
            executor.awaitTermination(
                2,
                TimeUnit.SECONDS
            );

            world.unregisterPlayer(firstPlayer);
            world.unregisterPlayer(secondPlayer);
            world.close();
        }
    }

    private static LocalSessionPlayerInitializer initializer(
        World world,
        WorldPlayer player
    ){
        return new LocalSessionPlayerInitializer(
            world,
            player,
            player.bank(),
            player.equipment(),
            player.movement(),
            player.petState(),
            player.playerState(),
            player.petEffects(),
            new PetAccessoryState()
        );
    }

    private static final class BlockingRepository
        implements PlayerRepository {
        final CountDownLatch firstLoadEntered=
            new CountDownLatch(1);
        final CountDownLatch secondLoadEntered=
            new CountDownLatch(1);
        final CountDownLatch releaseFirstLoad=
            new CountDownLatch(1);

        private final AtomicInteger loadCount=
            new AtomicInteger();
        private final List<String> loads=
            Collections.synchronizedList(
                new ArrayList<>()
            );

        @Override public Optional<PlayerSnapshot> load(
            String username
        )throws IOException{
            int number=loadCount.incrementAndGet();
            loads.add(username);

            if(number==1){
                firstLoadEntered.countDown();
                try{
                    if(!releaseFirstLoad.await(
                            5,
                            TimeUnit.SECONDS))
                        throw new IOException(
                            "first load release timeout"
                        );
                }catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                    throw new IOException(
                        "first load interrupted",
                        e
                    );
                }
            }else if(number==2){
                secondLoadEntered.countDown();
            }

            return Optional.empty();
        }

        @Override public void save(
            PlayerSnapshot snapshot
        )throws IOException{
        }

        List<String> loadsSnapshot(){
            synchronized(loads){
                return new ArrayList<>(loads);
            }
        }
    }
}
