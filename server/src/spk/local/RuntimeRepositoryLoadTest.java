package spk.local;

import java.io.*;
import java.util.*;

public final class RuntimeRepositoryLoadTest {
    public static void main(String[] args)throws Exception{
        testValidStagedLoad();
        testMalformedSnapshotLeavesLiveStateUntouched();
        testMissingSnapshotKeepsDefaults();

        System.out.println(
            "RUNTIME_REPOSITORY_LOAD_PASS "+
            "validStagedLoad=true "+
            "loadedClassified=true "+
            "malformedNoPartialMutation=true "+
            "failedClassified=true "+
            "missingKeepsDefaults=true "+
            "missingClassified=true"
        );
    }

    private static void testValidStagedLoad(){
        WorldPlayer source=
            new WorldPlayer();

        source.movement().setPersistentRun(
            true
        );
        source.movement().setRunEnergy(
            64
        );
        source.equipment().setWeapon(
            21566
        );
        source.playerState().setSpecialEnergy(
            36
        );

        PlayerSnapshot snapshot=
            PlayerSnapshotCodec.capture(
                "opensrc",
                source
            );

        PlayerRepository repository=
            fixed(snapshot);

        WorldPlayer live=
            new WorldPlayer();

        live.movement().setRunEnergy(
            11
        );
        live.equipment().setWeapon(
            4151
        );
        live.playerState().setSpecialEnergy(
            7
        );

        LocalAccountLifecycle.LoadResult result=
            LocalAccountLifecycle.load(
                new LocalAccountLifecycle.Selection(
                    "opensrc",
                    true
                ),
                live,
                repository,
                PetAccessoryAuthority::isAccessory,
                "[load-test] "
            );

        if(!result.loaded||
           result.missing||
           result.failed||
           result.status!=
               LocalAccountLifecycle
                   .LoadStatus.LOADED)
            throw new AssertionError(
                "valid repository snapshot classification "+
                result.status
            );

        if(live.movement().runEnergy()!=64||
           !live.movement().persistentRun()||
           live.equipment().weapon()!=21566||
           live.playerState().specialEnergy()!=36)
            throw new AssertionError(
                "valid staged load state mismatch"
            );
    }

    private static void testMalformedSnapshotLeavesLiveStateUntouched(){
        WorldPlayer seed=
            new WorldPlayer();

        seed.movement().setRunEnergy(
            31
        );
        seed.equipment().setWeapon(
            4151
        );

        PlayerSnapshot valid=
            PlayerSnapshotCodec.capture(
                "opensrc",
                seed
            );

        TreeMap<String,String> badValues=
            new TreeMap<>(
                valid.values()
            );

        badValues.put(
            "bank.0",
            "definitely-not-a-stack"
        );

        PlayerSnapshot malformed=
            new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                "opensrc",
                badValues
            );

        WorldPlayer live=
            new WorldPlayer();

        live.movement().setPersistentRun(
            true
        );
        live.movement().setRunEnergy(
            88
        );
        live.equipment().setWeapon(
            21566
        );
        live.playerState().setSpecialEnergy(
            42
        );

        LocalAccountLifecycle.LoadResult result=
            LocalAccountLifecycle.load(
                new LocalAccountLifecycle.Selection(
                    "opensrc",
                    true
                ),
                live,
                fixed(malformed),
                PetAccessoryAuthority::isAccessory,
                "[load-test] "
            );

        if(result.loaded||
           result.missing||
           !result.failed||
           result.status!=
               LocalAccountLifecycle
                   .LoadStatus.FAILED)
            throw new AssertionError(
                "malformed snapshot classification "+
                result.status
            );

        if(!live.movement().persistentRun()||
           live.movement().runEnergy()!=88||
           live.equipment().weapon()!=21566||
           live.playerState().specialEnergy()!=42)
            throw new AssertionError(
                "malformed staged load partially mutated live state"
            );
    }

    private static void testMissingSnapshotKeepsDefaults(){
        WorldPlayer live=
            new WorldPlayer();

        live.movement().setRunEnergy(
            77
        );

        PlayerRepository empty=
            new PlayerRepository(){
                @Override public Optional<PlayerSnapshot> load(
                    String username
                ){
                    return Optional.empty();
                }

                @Override public void save(
                    PlayerSnapshot snapshot
                ){}
            };

        LocalAccountLifecycle.LoadResult result=
            LocalAccountLifecycle.load(
                new LocalAccountLifecycle.Selection(
                    "opensrc",
                    true
                ),
                live,
                empty,
                PetAccessoryAuthority::isAccessory,
                "[load-test] "
            );

        if(result.loaded||
           !result.missing||
           result.failed||
           result.status!=
               LocalAccountLifecycle
                   .LoadStatus.MISSING)
            throw new AssertionError(
                "missing snapshot classification "+
                result.status
            );

        if(live.movement().runEnergy()!=77)
            throw new AssertionError(
                "missing snapshot changed live defaults"
            );
    }

    private static PlayerRepository fixed(
        PlayerSnapshot snapshot
    ){
        return new PlayerRepository(){
            @Override public Optional<PlayerSnapshot> load(
                String username
            ){
                return Optional.of(snapshot);
            }

            @Override public void save(
                PlayerSnapshot ignored
            )throws IOException{}
        };
    }
}
