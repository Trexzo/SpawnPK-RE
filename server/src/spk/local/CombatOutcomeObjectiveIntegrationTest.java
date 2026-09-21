package spk.local;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;

public final class CombatOutcomeObjectiveIntegrationTest {

    public static void main(String[] args) {
        ObjectiveProgressService objectives =
            new ObjectiveProgressService();

        objectives.define(
            new ObjectiveDefinition(
                "combat:pvp_kills",
                2L,
                "CUSTOM_LOCALLAB"
            )
        );
        objectives.define(
            new ObjectiveDefinition(
                "combat:pvp_deaths",
                1L,
                "CUSTOM_LOCALLAB"
            )
        );

        CombatOutcomeObjectiveObserver.Binding kills =
            new CombatOutcomeObjectiveObserver.Binding(
                CombatOutcomeType.PLAYER_KILL,
                CombatOutcomeContext.PLAYER_PVP,
                CombatOutcomeObjectiveObserver.SubjectRole.ATTACKER,
                "combat:pvp_kills",
                1L,
                "CUSTOM_LOCALLAB"
            );

        CombatOutcomeObjectiveObserver.Binding deaths =
            new CombatOutcomeObjectiveObserver.Binding(
                CombatOutcomeType.PLAYER_DEATH,
                CombatOutcomeContext.PLAYER_PVP,
                CombatOutcomeObjectiveObserver.SubjectRole.VICTIM,
                "combat:pvp_deaths",
                1L,
                "CUSTOM_LOCALLAB"
            );

        CombatOutcomeObjectiveObserver observer =
            new CombatOutcomeObjectiveObserver(
                objectives,
                "player:a",
                Arrays.asList(
                    kills,
                    deaths
                )
            );

        observer.onCombatOutcome(
            new CombatOutcome(
                "player:x",
                "player:b",
                CombatOutcomeType.PLAYER_KILL,
                CombatOutcomeContext.PLAYER_PVP,
                1L,
                "CUSTOM_LOCALLAB"
            )
        );

        assertProgress(
            objectives,
            "combat:pvp_kills",
            0L,
            false,
            "unrelated attacker"
        );

        observer.onCombatOutcome(
            new CombatOutcome(
                "npc:42",
                "player:a",
                CombatOutcomeType.PLAYER_DEATH,
                CombatOutcomeContext.NPC_PVM,
                2L,
                "CUSTOM_LOCALLAB"
            )
        );

        assertProgress(
            objectives,
            "combat:pvp_deaths",
            0L,
            false,
            "context mismatch"
        );

        observer.onCombatOutcome(
            new CombatOutcome(
                "player:a",
                "player:b",
                CombatOutcomeType.PLAYER_KILL,
                CombatOutcomeContext.PLAYER_PVP,
                3L,
                "CUSTOM_LOCALLAB"
            )
        );

        assertProgress(
            objectives,
            "combat:pvp_kills",
            1L,
            false,
            "first attacker fact"
        );

        observer.onCombatOutcome(
            new CombatOutcome(
                "player:a",
                "player:c",
                CombatOutcomeType.PLAYER_KILL,
                CombatOutcomeContext.PLAYER_PVP,
                4L,
                "CUSTOM_LOCALLAB"
            )
        );
        observer.onCombatOutcome(
            new CombatOutcome(
                "player:a",
                "player:d",
                CombatOutcomeType.PLAYER_KILL,
                CombatOutcomeContext.PLAYER_PVP,
                5L,
                "CUSTOM_LOCALLAB"
            )
        );

        assertProgress(
            objectives,
            "combat:pvp_kills",
            2L,
            true,
            "goal clamp"
        );

        observer.onCombatOutcome(
            new CombatOutcome(
                "player:b",
                "player:a",
                CombatOutcomeType.PLAYER_DEATH,
                CombatOutcomeContext.PLAYER_PVP,
                6L,
                "CUSTOM_LOCALLAB"
            )
        );

        ObjectiveProgressService.Snapshot
            deathSnapshot =
                objectives.get(
                    "combat:pvp_deaths"
                );

        if (deathSnapshot == null ||
            deathSnapshot.progress != 1L ||
            !deathSnapshot.complete ||
            deathSnapshot.claimed) {
            throw new AssertionError(
                "victim binding/reward boundary " +
                deathSnapshot
            );
        }

        if (!"CUSTOM_LOCALLAB".equals(
                kills.sourceAuthority()) ||
            !"CUSTOM_LOCALLAB".equals(
                deaths.sourceAuthority())) {
            throw new AssertionError(
                "binding authority unavailable"
            );
        }

        if (observer.bindings().size() != 2) {
            throw new AssertionError(
                "binding snapshot size=" +
                observer.bindings().size()
            );
        }

        boolean immutable = false;

        try {
            observer.bindings().clear();
        } catch (
            UnsupportedOperationException expected
        ) {
            immutable = true;
        }

        if (!immutable) {
            throw new AssertionError(
                "binding snapshot mutable"
            );
        }

        boolean unknownRejected = false;

        try {
            new CombatOutcomeObjectiveObserver(
                objectives,
                "player:a",
                Arrays.asList(
                    new CombatOutcomeObjectiveObserver.Binding(
                        CombatOutcomeType.NPC_KILL,
                        CombatOutcomeContext.NPC_PVM,
                        CombatOutcomeObjectiveObserver.SubjectRole.ATTACKER,
                        "combat:missing",
                        1L,
                        "CUSTOM_LOCALLAB"
                    )
                )
            );
        } catch (
            IllegalArgumentException expected
        ) {
            unknownRejected = true;
        }

        if (!unknownRejected) {
            throw new AssertionError(
                "unknown objective binding accepted"
            );
        }

        assertNoProtocolIdentity();

        System.out.println(
            "COMBAT_OUTCOME_OBJECTIVE_INTEGRATION_PASS " +
            "subjectScoped=true " +
            "attackerBinding=true " +
            "victimBinding=true " +
            "typeContextFiltered=true " +
            "unknownObjectiveFailClosed=true " +
            "goalClamp=true " +
            "rewardMutation=false " +
            "authorityInspectable=true " +
            "protocolIndependent=true"
        );
    }

    private static void assertProgress(
        ObjectiveProgressService objectives,
        String key,
        long progress,
        boolean complete,
        String label
    ) {
        ObjectiveProgressService.Snapshot
            snapshot =
                objectives.get(key);

        if (snapshot == null ||
            snapshot.progress != progress ||
            snapshot.complete != complete ||
            snapshot.claimed) {
            throw new AssertionError(
                label + " " + snapshot
            );
        }
    }

    private static void assertNoProtocolIdentity() {
        for (Class<?> type :
                new Class<?>[]{
                    CombatOutcomeObjectiveObserver.class,
                    CombatOutcomeObjectiveObserver.Binding.class
                }) {
            for (Field field :
                    type.getDeclaredFields()) {
                String name =
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if (name.contains("packet") ||
                    name.contains("opcode") ||
                    name.contains("widget") ||
                    name.contains("sceneindex")) {
                    throw new AssertionError(
                        "protocol identity leaked into " +
                        type.getSimpleName() +
                        "." +
                        field.getName()
                    );
                }
            }
        }
    }

    private CombatOutcomeObjectiveIntegrationTest() {}
}
