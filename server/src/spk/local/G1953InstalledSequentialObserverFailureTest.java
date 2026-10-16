package spk.local;

/**
 * G19.5.3: a real installed sequential G1/G14 observer reports THREW
 * after malformed G14 blocks later consumers, while drops stay settled.
 */
public final class G1953InstalledSequentialObserverFailureTest {
    public static void main(String[] args)throws Exception{
        G1884TerminalStageIsolationPropertySnapshotTest
            .runObserverDiagnosticCase(false,3103L);
        System.out.println(
            "G1953_INSTALLED_SEQUENTIAL_OBSERVER_FAILURE_PASS"+
            " actualProductionProvisioning=true"+
            " g1AndGroundSettled=true"+
            " g14ExceptionStopsSequentialTail=true"+
            " terminalObserverTHREW=true"+
            " noDuplicateSettlementOrObserver=true");
    }
    private G1953InstalledSequentialObserverFailureTest(){}
}
