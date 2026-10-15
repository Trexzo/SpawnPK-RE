package spk.local;

/**
 * G19.1.4 positive control: removing an unrelated current viewer
 * projection inside the pre-resolution seam must not block a valid
 * decoded NPC attack whose exact selected scene object remains live.
 */
public final class G1914UnrelatedProjectionRetiredAttackControlTest {
    public static void main(String[] args)throws Exception{
        G1913ProjectionRetiredBeforeDamageAdmissionTest.run(false);
        System.out.println(
            "G1914_UNRELATED_PROJECTION_RETIRED_ATTACK_CONTROL_PASS"+
            " exactSelectedProjectionRetained=true"+
            " unrelatedProjectionRetired=true"+
            " currentOpcode72AttackAdmitted=true"+
            " canonicalHitTen=true"+
            " originalSpawnpkKillPolicyUnknown=true");
    }

    private G1914UnrelatedProjectionRetiredAttackControlTest(){}
}
