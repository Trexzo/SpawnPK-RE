package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Explicit, subject-scoped bridge from semantic combat facts to one objective
 * ledger.
 *
 * Attribution policy is caller-owned. This class only applies configured
 * bindings and never claims rewards or mutates combat state.
 */
final class CombatOutcomeObjectiveObserver
    implements CombatOutcomeObserver {

    enum SubjectRole {
        ATTACKER,
        VICTIM
    }

    static final class Binding {
        final CombatOutcomeType outcomeType;
        final CombatOutcomeContext context;
        final SubjectRole subjectRole;
        final String objectiveKey;
        final long amount;
        final String sourceAuthority;

        Binding(
            CombatOutcomeType outcomeType,
            CombatOutcomeContext context,
            SubjectRole subjectRole,
            String objectiveKey,
            long amount,
            String sourceAuthority
        ) {
            this.outcomeType =
                Objects.requireNonNull(
                    outcomeType,
                    "outcomeType"
                );
            this.context =
                Objects.requireNonNull(
                    context,
                    "context"
                );
            this.subjectRole =
                Objects.requireNonNull(
                    subjectRole,
                    "subjectRole"
                );
            this.objectiveKey =
                ObjectiveDefinition.normalizeKey(
                    objectiveKey
                );

            if (amount <= 0L) {
                throw new IllegalArgumentException(
                    "amount=" + amount
                );
            }

            this.amount = amount;
            this.sourceAuthority =
                requireText(
                    sourceAuthority,
                    "sourceAuthority"
                );
        }

        String sourceAuthority() {
            return sourceAuthority;
        }

        private String duplicateKey() {
            return outcomeType.name() + "|" +
                context.name() + "|" +
                subjectRole.name() + "|" +
                objectiveKey;
        }
    }

    private final ObjectiveProgressService objectives;
    private final String subjectRef;
    private final List<Binding> bindings;

    CombatOutcomeObjectiveObserver(
        ObjectiveProgressService objectives,
        String subjectRef,
        List<Binding> bindings
    ) {
        this.objectives =
            Objects.requireNonNull(
                objectives,
                "objectives"
            );
        this.subjectRef =
            requireText(
                subjectRef,
                "subjectRef"
            );

        Objects.requireNonNull(
            bindings,
            "bindings"
        );

        if (bindings.isEmpty()) {
            throw new IllegalArgumentException(
                "bindings empty"
            );
        }

        ArrayList<Binding> copy =
            new ArrayList<>();
        Set<String> duplicateGuard =
            new HashSet<>();

        for (Binding binding : bindings) {
            Binding checked =
                Objects.requireNonNull(
                    binding,
                    "binding"
                );

            if (objectives.get(
                    checked.objectiveKey
                ) == null) {
                throw new IllegalArgumentException(
                    "unknown objective key=" +
                    checked.objectiveKey
                );
            }

            if (!duplicateGuard.add(
                    checked.duplicateKey())) {
                throw new IllegalArgumentException(
                    "duplicate combat objective binding " +
                    checked.duplicateKey()
                );
            }

            copy.add(checked);
        }

        this.bindings =
            Collections.unmodifiableList(
                copy
            );
    }

    @Override
    public void onCombatOutcome(
        CombatOutcome outcome
    ) {
        Objects.requireNonNull(
            outcome,
            "outcome"
        );

        for (Binding binding : bindings) {
            if (binding.outcomeType !=
                    outcome.type() ||
                binding.context !=
                    outcome.context() ||
                !subjectMatches(
                    binding.subjectRole,
                    outcome)) {
                continue;
            }

            objectives.advance(
                binding.objectiveKey,
                binding.amount
            );
        }
    }

    List<Binding> bindings() {
        return bindings;
    }

    private boolean subjectMatches(
        SubjectRole role,
        CombatOutcome outcome
    ) {
        String actual =
            role == SubjectRole.ATTACKER
                ? outcome.attacker()
                : outcome.victim();

        return subjectRef.equals(actual);
    }

    private static String requireText(
        String value,
        String label
    ) {
        Objects.requireNonNull(
            value,
            label
        );

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                label
            );
        }

        return normalized;
    }
}
