package spk.local;

/**
 * G19.1.8 positive control: generation-time retirement of a different
 * scene does not disable a valid current dead-NPC terminal retry.
 */
public final class G1918DeadNpcOtherSceneRetiredControlTest {
    public static void main(String[] args)throws Exception{
        G1917DeadNpcSceneRetiredDuringGenerationTest.run(false);
        System.out.println(
            "G1918_DEAD_NPC_OTHER_SCENE_RETIRED_CONTROL_PASS"+
            " unrelatedSceneRetired=true"+
            " selectedSceneCurrent=true"+
            " legitimateDeadRetryOnce=true"+
            " originalSpawnpkPolicyUnknown=true");
    }

    private G1918DeadNpcOtherSceneRetiredControlTest(){}
}
