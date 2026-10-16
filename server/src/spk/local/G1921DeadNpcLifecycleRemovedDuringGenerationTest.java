package spk.local;

/** G19.2.1: missing selected NPC lifecycle vetoes dead-terminal retry. */
public final class G1921DeadNpcLifecycleRemovedDuringGenerationTest {
    public static void main(String[] args)throws Exception{
        G1919DeadNpcRemovedDuringGenerationTest.run(
            G1919DeadNpcRemovedDuringGenerationTest.REMOVE_SELECTED_LIFECYCLE);
        System.out.println(
            "G1921_DEAD_NPC_LIFECYCLE_REMOVED_DURING_GENERATION_PASS"+
            " selectedCanonicalStillCurrent=true"+
            " selectedLifecycleMissing=true"+
            " noRetryCallback=true"+
            " originalServerAttributionUnknown=true");
    }
    private G1921DeadNpcLifecycleRemovedDuringGenerationTest(){}
}
