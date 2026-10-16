package spk.local;

/**
 * G19.6.2: opt-in isolated stage delivery must reject a same-name
 * replacement player when the terminal belongs to a retired origin.
 */
public final class G1962IsolatedSameNameRecipientRebindFenceTest {
    public static void main(String[] args)throws Exception{
        G1961SameNameRecipientRebindFenceTest.run(true);
        System.out.println(
            "G1962_ISOLATED_SAME_NAME_RECIPIENT_REBIND_FENCE_PASS"+
            " realCanonicalSpawnOrigin=true"+
            " recipientOriginMismatchDetected=true"+
            " allFiveIsolatedStagesSkipped=true"+
            " replacementG1AndG15G16G18Uncredited=true"+
            " noDuplicateSettlement=true");
    }
    private G1962IsolatedSameNameRecipientRebindFenceTest(){}
}
