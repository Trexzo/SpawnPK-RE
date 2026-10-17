package spk.local;

/**
 * G19.0.8: positive control of the SAME ISAAC-framed opcode72 to
 * canonical hit/death/settlement path when authenticated attacker
 * and original Monster Spawner recipient happen to be the same
 * current WorldPlayer. Does not invent a universal reward policy.
 */
public final class G1908DecodedLethalHitSameSpawnerRecipientControlTest {
    public static void main(String[] args)throws Exception{
        G1907DecodedLethalHitVsSpawnerRecipientCharacterizationTest.Evidence
            e=G1907DecodedLethalHitVsSpawnerRecipientCharacterizationTest
                .run(true);
        check(e.sameAttackerAndSpawnerRecipient&&
                e.acceptedDamage==10&&e.acceptedLethal&&
                e.spawnerRecipientG1Kills==1L&&
                e.separateAttackerG1Kills==0L&&
                e.g1Receipts==1&&e.coinSettlements==1&&
                e.exactAttackerWitness&&e.sourceWorldMatched&&
                e.spawnRecipientOriginMatched,
            "real same-owner current-client opcode72 proves matching identities");
        System.out.println(
            "G1908_DECODED_LETHAL_HIT_MATCHING_SPAWNER_RECIPIENT_PASS"+
            " realIsaacOpcode72Accepted=true"+
            " exactAttackerEqualsSpawnRecipient=true"+
            " canonicalNpcHitLethalTen=true"+
            " G1CreditOne=true"+
            " groundCoinOne=true"+
            " noDuplicateFinalization=true"+
            " originalSpawnpkKillPolicyUnknown=true"+
            " noRewardPolicyChange=true");
    }

    private static void check(boolean value,String explanation){
        if(!value)throw new AssertionError("G19.0.8 "+explanation);
    }

    private G1908DecodedLethalHitSameSpawnerRecipientControlTest(){}
}
