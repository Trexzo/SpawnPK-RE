package spk.local;

/** G19.4.6: long attack callback also fences a different canonical target. */
public final class G1946DifferentTargetCompletionTickCadenceTest {
    public static void main(String[] args)throws Exception{
        G1945DelayedResolutionWorldTickCadenceTest.run(false);
        System.out.println(
            "G1946_DIFFERENT_TARGET_COMPLETION_TICK_CADENCE_PASS"+
            " exactDecodedOpcode72=true"+
            " twoIndependentHandlerInstances=true"+
            " twelveWorldTicksAdvancedBeforeDamage=true"+
            " unrelatedNpcUntouchedDuringNewCooldown=true"+
            " laterSecondTargetHitAllowed=true");
    }
    private G1946DifferentTargetCompletionTickCadenceTest(){}
}
