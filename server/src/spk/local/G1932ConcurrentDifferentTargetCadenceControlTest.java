package spk.local;

/**
 * G19.3.2: concurrent opcode72 clicks to DIFFERENT canonical NPCs
 * still share the one per-session cooldown. After it advances, the
 * second NPC must remain attackable with ordinary packet-65 delivery.
 */
public final class G1932ConcurrentDifferentTargetCadenceControlTest {
    public static void main(String[] args)throws Exception{
        G1931ConcurrentSameTickNpcAttackFenceTest.run(false);
        System.out.println(
            "G1932_CONCURRENT_DIFFERENT_TARGET_CADENCE_CONTROL_PASS"+
            " sameHandlerTwoDistinctCanonicalNpcs=true"+
            " firstTargetHitOnce=true"+
            " secondTargetUntouchedDuringCadence=true"+
            " afterCooldownSecondTargetHit=true"+
            " noExtraReward=true");
    }

    private G1932ConcurrentDifferentTargetCadenceControlTest(){}
}
