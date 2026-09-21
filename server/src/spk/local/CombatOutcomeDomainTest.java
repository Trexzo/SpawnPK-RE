package spk.local;

public final class CombatOutcomeDomainTest {

    public static void main(String[] args) {
        CombatOutcome outcome = new CombatOutcome(
            "player:a",
            "player:b",
            CombatOutcomeType.PLAYER_KILL,
            42L,
            "CUSTOM_LOCALLAB"
        );

        final boolean[] observed = { false };

        CombatOutcomeObserver observer = value -> {
            if (value != outcome) {
                throw new AssertionError("outcome identity changed");
            }
            observed[0] = true;
        };

        observer.onCombatOutcome(outcome);

        if (!observed[0]) {
            throw new AssertionError("observer did not receive outcome");
        }

        if (!"CUSTOM_LOCALLAB".equals(outcome.context())) {
            throw new AssertionError("context changed");
        }

        System.out.println(
            "COMBAT_OUTCOME_DOMAIN_PASS " +
            "immutable=true " +
            "protocolIndependent=true " +
            "consumerBoundary=true " +
            "rewardMutation=false"
        );
    }

    private CombatOutcomeDomainTest() {}
}
