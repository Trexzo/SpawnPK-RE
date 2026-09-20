package spk.local;

import java.io.IOException;
import java.util.Optional;

public final class DuplicateSrcProfilePreloadGuardTest {
    public static void main(String[] args)throws Exception{
        CountingRepository repository=new CountingRepository();
        World world=World.isolatedForTest(50L,repository);
        try{
            WorldPlayer online=new WorldPlayer();
            world.registerPlayer(online,LocalAccountProfiles.SECONDARY);

            WorldPlayer candidate=new WorldPlayer();
            PetAccessoryState accessory=new PetAccessoryState();

            LocalSessionPlayerInitializer initializer=
                new LocalSessionPlayerInitializer(
                    world,
                    candidate,
                    candidate.bank(),
                    candidate.equipment(),
                    candidate.movement(),
                    candidate.petState(),
                    candidate.playerState(),
                    candidate.petEffects(),
                    accessory
                );

            boolean rejected=false;
            try{
                initializer.initialize(
                    LocalAccountProfiles.SECONDARY,
                    "[duplicate-src-test] "
                );
            }catch(IllegalStateException expected){
                rejected=
                    ("DUPLICATE_LOGIN username="+
                        LocalAccountProfiles.SECONDARY)
                        .equals(expected.getMessage());
            }

            if(!rejected)
                throw new AssertionError(
                    "occupied explicit src profile was not rejected early"
                );

            if(repository.loadCalls!=0)
                throw new AssertionError(
                    "repository load occurred before duplicate rejection calls="+
                    repository.loadCalls
                );

            if(candidate.registered()||
               candidate.generation()!=0L)
                throw new AssertionError(
                    "rejected candidate mutated lifecycle="+candidate
                );

            if(world.players().size()!=1||
               world.players().byName(LocalAccountProfiles.SECONDARY)!=online)
                throw new AssertionError(
                    "existing src membership changed"
                );

            world.unregisterPlayer(online);

            System.out.println(
                "DUPLICATE_SRC_PROFILE_PRELOAD_GUARD_PASS "+
                "duplicateRejected=true "+
                "repositoryLoadCalls=0 "+
                "candidateUnregistered=true"
            );
        }finally{
            world.close();
        }
    }

    private static final class CountingRepository
        implements PlayerRepository {
        int loadCalls;

        @Override public Optional<PlayerSnapshot> load(
            String username
        )throws IOException{
            loadCalls++;
            return Optional.empty();
        }

        @Override public void save(
            PlayerSnapshot snapshot
        )throws IOException{
        }
    }
}
