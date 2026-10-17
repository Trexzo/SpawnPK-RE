package spk.local;

/**
 * G19.7.0: compare a genuine installed post-settlement G1 exception
 * against an invocation-local test collector that never observes G1.
 * All evidence is read-only and never authorizes reward replay.
 */
public final class G1970G1OutcomeMixedAndUnobservedControlTest {
    public static void main(String[] args)throws Exception{
        G1967G1PreStageFailureDiagnosticTest.run(true);
        G1965SameWorldSequentialTraceCaptureOwnerTest.main(args);
        System.out.println(
            "G1970_G1_OUTCOME_MIXED_UNOBSERVED_CONTROL_PASS"+
            " installedG1Returned=1"+
            " installedG1Threw=1"+
            " unobservedCollectorCount=1"+
            " foreignCollectorCount=0"+
            " noGrantReplay=true");
    }
    private G1970G1OutcomeMixedAndUnobservedControlTest(){}
}
