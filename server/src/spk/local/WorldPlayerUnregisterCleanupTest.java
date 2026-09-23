package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class WorldPlayerUnregisterCleanupTest {
    public static void main(String[] args){
        List<String> attempted=
            new ArrayList<>();

        WorldPlayerUnregisterCleanup.run(
            ()->{
                attempted.add(
                    "PERSISTENCE"
                );
                throw new IllegalStateException(
                    "persistence-failed"
                );
            },
            ()->{
                attempted.add(
                    "COMMAND"
                );
                throw new AssertionError(
                    "command-failed"
                );
            },
            ()->attempted.add(
                "REALTIME"
            ),
            ()->attempted.add(
                "PET"
            )
        );

        List<String> expected=
            Arrays.asList(
                "PERSISTENCE",
                "COMMAND",
                "REALTIME",
                "PET"
            );

        if(!attempted.equals(expected))
            throw new AssertionError(
                "cleanup order mismatch expected="+
                expected+
                " actual="+
                attempted
            );

        if(!attempted.contains(
                "REALTIME"
            )||
           !attempted.contains(
               "PET"
           ))
            throw new AssertionError(
                "later cleanup suppressed by earlier failure"
            );

        List<String> normal=
            new ArrayList<>();

        WorldPlayerUnregisterCleanup.run(
            ()->normal.add(
                "PERSISTENCE"
            ),
            ()->normal.add(
                "COMMAND"
            ),
            ()->normal.add(
                "REALTIME"
            ),
            ()->normal.add(
                "PET"
            )
        );

        if(!normal.equals(expected))
            throw new AssertionError(
                "normal cleanup order changed expected="+
                expected+
                " actual="+
                normal
            );

        System.out.println(
            "WORLD_PLAYER_UNREGISTER_CLEANUP_PASS "+
            "persistenceFailureContained=true "+
            "commandFailureContained=true "+
            "realtimeStillAttempted=true "+
            "petStillAttempted=true "+
            "normalOrderPreserved=true"
        );
    }

    private WorldPlayerUnregisterCleanupTest(){}
}
