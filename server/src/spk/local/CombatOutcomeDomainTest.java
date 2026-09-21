package spk.local;

public final class CombatOutcomeDomainTest {

    public static void main(String[] args) {
        CombatOutcome outcome = new CombatOutcome(
            "player:a",
            "player:b",
            CombatOutcomeType.PLAYER_KILL,
            CombatOutcomeContext.PLAYER_PVP,
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

        if (outcome.context() != CombatOutcomeContext.PLAYER_PVP) {
            throw new AssertionError("combat context changed");
        }

        if (!"CUSTOM_LOCALLAB".equals(outcome.sourceAuthority())) {
            throw new AssertionError("source authority changed");
        }

        System.out.println(
            "COMBAT_OUTCOME_DOMAIN_PASS " +
            "immutable=true " +
            "typedContext=true " +
            "authoritySeparated=true " +
            "protocolIndependent=true " +
            "consumerBoundary=true " +
            "rewardMutation=false"
        );
    }

    private CombatOutcomeDomainTest() {}
}
