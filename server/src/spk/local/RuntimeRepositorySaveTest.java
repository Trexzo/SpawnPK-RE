package spk.local;

import java.io.*;
import java.util.*;

public final class RuntimeRepositorySaveTest {
    public static void main(String[] args)throws Exception{
        testRepositoryBackedCapture();
        testRepositoryFailureDoesNotMutateLiveState();

        System.out.println(
            "RUNTIME_REPOSITORY_SAVE_PASS "+
            "snapshotCapture=true "+
            "immutableAfterSave=true "+
            "failureNoLiveMutation=true"
        );
    }

    private static void testRepositoryBackedCapture()
        throws Exception{
        WorldPlayer player=
            new WorldPlayer();

        player.movement().setPersistentRun(
            true
        );
        player.movement().setRunEnergy(
            74
        );
        player.equipment().setWeapon(
            21566
        );
        player.playerState().setSpecialEnergy(
            41
        );

        final PlayerSnapshot[] saved=
            new PlayerSnapshot[1];

        PlayerRepository repository=
            new PlayerRepository(){
                @Override public Optional<PlayerSnapshot> load(
                    String username
                ){
                    return Optional.empty();
                }

                @Override public void save(
                    PlayerSnapshot snapshot
                ){
                    saved[0]=snapshot;
                }
            };

        PlayerSnapshot returned=
            LocalAccountLifecycle.captureAndSave(
                "opensrc",
                player,
                repository
            );

        if(saved[0]!=returned)
            throw new AssertionError(
                "repository did not receive captured snapshot"
            );

        if(!"74".equals(
                returned.value(
                    "movement.runEnergy"
                ))||
           !"true".equals(
                returned.value(
                    "movement.runEnabled"
                ))||
           !"41".equals(
                returned.value(
                    "combat.special.energy"
                )))
            throw new AssertionError(
                "runtime snapshot state mismatch "+
                returned.values()
            );

        SortedMap<String,String> before=
            new TreeMap<>(
                returned.values()
            );

        player.movement().setRunEnergy(
            12
        );
        player.playerState().setSpecialEnergy(
            9
        );

        if(!before.equals(
                returned.values()))
            throw new AssertionError(
                "saved snapshot changed after live mutation"
            );
    }

    private static void testRepositoryFailureDoesNotMutateLiveState(){
        WorldPlayer player=
            new WorldPlayer();

        player.movement().setRunEnergy(
            88
        );
        player.equipment().setWeapon(
            21566
        );

        PlayerRepository failing=
            new PlayerRepository(){
                @Override public Optional<PlayerSnapshot> load(
                    String username
                ){
                    return Optional.empty();
                }

                @Override public void save(
                    PlayerSnapshot snapshot
                )throws IOException{
                    throw new IOException(
                        "EXPECTED_TEST_FAILURE"
                    );
                }
            };

        LocalAccountLifecycle.saveQuiet(
            "opensrc",
            true,
            player,
            failing,
            0,
            "[repository-test] ",
            "FAILURE_TEST"
        );

        if(player.movement().runEnergy()!=88||
           player.equipment().weapon()!=21566)
            throw new AssertionError(
                "failed repository save mutated live player"
            );
    }
}
