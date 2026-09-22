package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LocalSessionRuntimeBindingCleanupTest {
    public static void main(String[] args){
        List<String> attempted=
            new ArrayList<>();

        LocalSessionRuntimeBindingCleanup.run(
            "[runtime-binding-cleanup-test] ",
            ()->attempted.add(
                "CLEAR_LOCAL"
            ),
            ()->{
                attempted.add(
                    "TRADE"
                );
                throw new IllegalStateException(
                    "trade-cleanup-failed"
                );
            },
            ()->{
                attempted.add(
                    "RELAY"
                );
                throw new AssertionError(
                    "relay-cleanup-failed"
                );
            },
            ()->attempted.add(
                "PLAYER81"
            )
        );

        List<String> expected=
            Arrays.asList(
                "CLEAR_LOCAL",
                "TRADE",
                "RELAY",
                "PLAYER81"
            );

        if(!attempted.equals(expected))
            throw new AssertionError(
                "runtime binding cleanup order mismatch expected="+
                expected+
                " actual="+
                attempted
            );

        if(attempted.indexOf(
                "CLEAR_LOCAL"
            )!=0)
            throw new AssertionError(
                "local ownership was not cleared before external cleanup"
            );

        if(!attempted.contains(
                "PLAYER81"
            ))
            throw new AssertionError(
                "Player81 cleanup was suppressed by earlier failure"
            );

        List<String> normal=
            new ArrayList<>();

        LocalSessionRuntimeBindingCleanup.run(
            "[runtime-binding-cleanup-test] ",
            ()->normal.add(
                "CLEAR_LOCAL"
            ),
            ()->normal.add(
                "TRADE"
            ),
            ()->normal.add(
                "RELAY"
            ),
            ()->normal.add(
                "PLAYER81"
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
            "LOCAL_SESSION_RUNTIME_BINDING_CLEANUP_PASS "+
            "localClearedFirst=true "+
            "tradeFailureContained=true "+
            "relayFailureContained=true "+
            "player81StillAttempted=true "+
            "normalOrderPreserved=true"
        );
    }

    private LocalSessionRuntimeBindingCleanupTest(){}
}
