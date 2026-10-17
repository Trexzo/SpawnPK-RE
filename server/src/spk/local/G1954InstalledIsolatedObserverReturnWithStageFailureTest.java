package spk.local;

/**
 * G19.5.4: opt-in stage isolation records G14 THREW while installed
 * overall observer RETURNED and G15/G16/G18 continue normally.
 */
public final class G1954InstalledIsolatedObserverReturnWithStageFailureTest {
    public static void main(String[] args)throws Exception{
        G1884TerminalStageIsolationPropertySnapshotTest
            .runObserverDiagnosticCase(true,3204L);
        System.out.println(
            "G1954_INSTALLED_ISOLATED_OBSERVER_RETURN_WITH_STAGE_FAILURE_PASS"+
            " realInstalledObserver=true"+
            " g14StageTHREW=true"+
            " g15G16G18Returned=true"+
            " overallObserverRETURNEDIsNotCreditACK=true"+
            " noDuplicateSettlementOrObserver=true");
    }
    private G1954InstalledIsolatedObserverReturnWithStageFailureTest(){}
}
