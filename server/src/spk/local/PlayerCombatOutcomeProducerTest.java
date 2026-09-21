package spk.local;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayerCombatOutcomeProducerTest {

    public static void main(String[] args) {
        WorldPlayer attacker = new WorldPlayer();
        WorldPlayer target = new WorldPlayer();
        List<CombatOutcome> observed =
            new ArrayList<>();

        target.playerState().setCurrentLevel(
            PlayerState.HITPOINTS,
            20
        );

        PlayerCombatResolutionService service =
            new PlayerCombatResolutionService(
                attacker,
                new FixedDamageRules(10),
                CombatAttackTimingRules
                    .recoveredCompatibility(),
                CombatSystemHooks.none(),
                observed::add
            );

        PlayerCombatResolutionService.Result
            nonLethal =
                service.resolveImmediate(
                    target,
                    4151,
                    null,
                    10L
                );

        if (nonLethal.lifecycle.died ||
            nonLethal.lifecycle.hpAfter != 10 ||
            !observed.isEmpty()) {
            throw new AssertionError(
                "non-lethal combat emitted outcome " +
                nonLethal.lifecycle +
                " observed=" + observed.size()
            );
        }

        PlayerCombatResolutionService.Result lethal =
            service.resolveImmediate(
                target,
                4151,
                null,
                11L
            );

        if (!lethal.lifecycle.died ||
            lethal.lifecycle.hpAfter != 0 ||
            !target.lifecycle().dead()) {
            throw new AssertionError(
                "lethal combat did not remain authoritative " +
                lethal.lifecycle
            );
        }

        if (observed.size() != 2) {
            throw new AssertionError(
                "lethal outcome count=" +
                observed.size()
            );
        }

        assertOutcome(
            observed.get(0),
            CombatOutcomeType.PLAYER_KILL,
            attacker,
            target,
            11L
        );

        assertOutcome(
            observed.get(1),
            CombatOutcomeType.PLAYER_DEATH,
            attacker,
            target,
            11L
        );

        PlayerCombatResolutionService.Result
            alreadyDead =
                service.resolveImmediate(
                    target,
                    4151,
                    null,
                    12L
                );

        if (!alreadyDead.lifecycle.ignoredDead ||
            observed.size() != 2) {
            throw new AssertionError(
                "already-dead target duplicated outcomes " +
                alreadyDead.lifecycle +
                " observed=" + observed.size()
            );
        }

        assertObserverFailureIsolation();
        assertNoProtocolIdentity();

        System.out.println(
            "PLAYER_COMBAT_OUTCOME_PRODUCER_PASS " +
            "nonLethalEmits=false " +
            "lethalFacts=2 " +
            "killFact=true " +
            "deathFact=true " +
            "canonicalEntityIds=true " +
            "context=PLAYER_PVP " +
            "authority=" +
            PlayerLifecycleService.AUTHORITY + " " +
            "alreadyDeadDuplicate=false " +
            "observerFailureIsolated=true " +
            "rewardMutation=false " +
            "protocolIndependent=true"
        );
    }

    private static void assertOutcome(
        CombatOutcome outcome,
        CombatOutcomeType type,
        WorldPlayer attacker,
        WorldPlayer target,
        long tick
    ) {
        if (outcome.type() != type ||
            outcome.context() !=
                CombatOutcomeContext.PLAYER_PVP ||
            !attacker.id().toString().equals(
                outcome.attacker()) ||
            !target.id().toString().equals(
                outcome.victim()) ||
            outcome.worldTick() != tick ||
            !PlayerLifecycleService.AUTHORITY.equals(
                outcome.sourceAuthority())) {
            throw new AssertionError(
                "outcome mismatch type=" +
                outcome.type() +
                " attacker=" + outcome.attacker() +
                " victim=" + outcome.victim() +
                " context=" + outcome.context() +
                " tick=" + outcome.worldTick() +
                " authority=" +
                outcome.sourceAuthority()
            );
        }
    }

    private static void
        assertObserverFailureIsolation() {
        WorldPlayer attacker =
            new WorldPlayer();
        WorldPlayer target =
            new WorldPlayer();

        target.playerState().setCurrentLevel(
            PlayerState.HITPOINTS,
            10
        );

        final int[] callbacks = {0};

        PlayerCombatResolutionService service =
            new PlayerCombatResolutionService(
                attacker,
                new FixedDamageRules(10),
                CombatAttackTimingRules
                    .recoveredCompatibility(),
                CombatSystemHooks.none(),
                outcome -> {
                    callbacks[0]++;
                    throw new IllegalStateException(
                        "test observer failure"
                    );
                }
            );

        PlayerCombatResolutionService.Result result =
            service.resolveImmediate(
                target,
                4151,
                null,
                20L
            );

        if (!result.lifecycle.died ||
            result.lifecycle.hpAfter != 0 ||
            !target.lifecycle().dead() ||
            callbacks[0] != 2) {
            throw new AssertionError(
                "observer failure changed combat state " +
                result.lifecycle +
                " callbacks=" + callbacks[0]
            );
        }
    }

    private static void assertNoProtocolIdentity() {
        for (Field field :
                PlayerCombatResolutionService.class
                    .getDeclaredFields()) {
            String name =
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if (name.contains("packet") ||
                name.contains("widget") ||
                name.contains("opcode") ||
                name.contains("clientindex") ||
                name.contains("sceneindex") ||
                name.contains("reward")) {
                throw new AssertionError(
                    "protocol/reward identity leaked " +
                    field.getName()
                );
            }
        }
    }

    private static final class FixedDamageRules
        implements CombatDamageRules {

        private final int damage;

        FixedDamageRules(int damage) {
            this.damage = damage;
        }

        @Override
        public Result calculate(
            Request request
        ) {
            return new Result(
                damage,
                damage,
                "CUSTOM_LOCALLAB",
                "TEST_FIXED_DAMAGE"
            );
        }

        @Override
        public String authority() {
            return "CUSTOM_LOCALLAB";
        }

        @Override
        public String formula() {
            return "TEST_FIXED_DAMAGE";
        }
    }

    private PlayerCombatOutcomeProducerTest() {}
}
