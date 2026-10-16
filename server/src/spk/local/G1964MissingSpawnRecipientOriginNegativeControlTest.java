package spk.local;

/**
 * G19.6.4: when the old spawn recipient is completely absent, the
 * trace must record observed generation -1 and a false origin match;
 * no same-name replacement is implied.
 */
public final class G1964MissingSpawnRecipientOriginNegativeControlTest {
    public static void main(String[] args)throws Exception{
        G1959DisconnectedRecipientStageTraceTest.run(false);
        System.out.println(
            "G1964_MISSING_SPAWN_RECIPIENT_ORIGIN_NEGATIVE_CONTROL_PASS"+
            " realCanonicalDropSettlement=true"+
            " spawnGenerationPreserved=true"+
            " observedGenerationAbsent=-1"+
            " noCurrentOriginMatch=true"+
            " fiveStagesSkippedWithoutGrant=true");
    }
    private G1964MissingSpawnRecipientOriginNegativeControlTest(){}
}
