package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Explicit subject-scoped bridge from semantic combat facts to Collection Log
 * kill-count projection.
 *
 * This observer deliberately owns no +1 accumulator. A caller-owned resolver
 * supplies the authoritative absolute count for every matching fact.
 */
final class CombatOutcomeCollectionLogObserver
    implements CombatOutcomeObserver {

    enum SubjectRole {
        ATTACKER,
        VICTIM
    }

    @FunctionalInterface
    interface AuthoritativeKillCountResolver {
        long resolve(
            String subjectRef,
            Binding binding,
            CombatOutcome outcome,
            CollectionLogService.ProgressSnapshot current
        );
    }

    static final class Binding {
        final CombatOutcomeType outcomeType;
        final CombatOutcomeContext context;
        final SubjectRole subjectRole;
        final CollectionLogDefinition.CollectionId
            collectionId;
        final String sourceAuthority;

        Binding(
            CombatOutcomeType outcomeType,
            CombatOutcomeContext context,
            SubjectRole subjectRole,
            CollectionLogDefinition.CollectionId
                collectionId,
            String sourceAuthority
        ){
            this.outcomeType=
                Objects.requireNonNull(
                    outcomeType,
                    "outcomeType"
                );
            this.context=
                Objects.requireNonNull(
                    context,
                    "context"
                );
            this.subjectRole=
                Objects.requireNonNull(
                    subjectRole,
                    "subjectRole"
                );
            this.collectionId=
                Objects.requireNonNull(
                    collectionId,
                    "collectionId"
                );
            this.sourceAuthority=
                requireText(
                    sourceAuthority,
                    "sourceAuthority"
                );
        }

        String sourceAuthority(){
            return sourceAuthority;
        }

        private String duplicateKey(){
            return outcomeType.name()+"|"+
                context.name()+"|"+
                subjectRole.name()+"|"+
                collectionId.toString();
        }
    }

    private static final class PendingProjection {
        final Binding binding;
        final Long expectedPrevious;
        final long authoritativeCount;

        PendingProjection(
            Binding binding,
            Long expectedPrevious,
            long authoritativeCount
        ){
            this.binding=binding;
            this.expectedPrevious=
                expectedPrevious;
            this.authoritativeCount=
                authoritativeCount;
        }
    }

    private final CollectionLogService log;
    private final String subjectRef;
    private final AuthoritativeKillCountResolver
        resolver;
    private final List<Binding> bindings;

    CombatOutcomeCollectionLogObserver(
        CollectionLogService log,
        String subjectRef,
        AuthoritativeKillCountResolver resolver,
        List<Binding> bindings
    ){
        this.log=
            Objects.requireNonNull(
                log,
                "log"
            );
        this.subjectRef=
            requireText(
                subjectRef,
                "subjectRef"
            );
        this.resolver=
            Objects.requireNonNull(
                resolver,
                "resolver"
            );

        Objects.requireNonNull(
            bindings,
            "bindings"
        );

        if(bindings.isEmpty())
            throw new IllegalArgumentException(
                "bindings empty"
            );

        ArrayList<Binding> copy=
            new ArrayList<>();
        Set<String> duplicateGuard=
            new HashSet<>();

        for(Binding binding:bindings){
            Binding checked=
                Objects.requireNonNull(
                    binding,
                    "binding"
                );

            CollectionLogDefinition definition=
                definition(
                    checked.collectionId
                );

            if(definition==null)
                throw new IllegalArgumentException(
                    "unknown collection "+
                    checked.collectionId
                );

            if(!definition.supportsKillCount())
                throw new IllegalArgumentException(
                    "collection does not support kill count "+
                    checked.collectionId
                );

            if(!duplicateGuard.add(
                    checked.duplicateKey()))
                throw new IllegalArgumentException(
                    "duplicate combat Collection Log binding "+
                    checked.duplicateKey()
                );

            copy.add(checked);
        }

        this.bindings=
            Collections.unmodifiableList(
                copy
            );
    }

    @Override
    public void onCombatOutcome(
        CombatOutcome outcome
    ){
        Objects.requireNonNull(
            outcome,
            "outcome"
        );

        ArrayList<PendingProjection> pending=
            new ArrayList<>();

        /*
         * Resolve every caller-owned absolute count with no observer/log lock
         * held. Every matching binding captures the exact prior absolute count
         * that the resolver saw.
         */
        for(Binding binding:bindings){
            if(binding.outcomeType!=
                    outcome.type()||
               binding.context!=
                    outcome.context()||
               !subjectMatches(
                    binding.subjectRole,
                    outcome))
                continue;

            CollectionLogService.ProgressSnapshot
                current=
                    log.snapshot(
                        binding.collectionId
                    );

            Long expectedPrevious=
                current.killCount()
                    .isPresent()
                    ?Long.valueOf(
                        current.killCount()
                            .getAsLong()
                    )
                    :null;

            long authoritativeCount=
                resolver.resolve(
                    subjectRef,
                    binding,
                    outcome,
                    current
                );

            if(authoritativeCount<0L)
                throw new IllegalArgumentException(
                    "authoritative kill count negative collection="+
                    binding.collectionId+
                    " count="+
                    authoritativeCount
                );

            pending.add(
                new PendingProjection(
                    binding,
                    expectedPrevious,
                    authoritativeCount
                )
            );
        }

        ArrayList<CollectionLogService.KillCountUpdate>
            updates=
                new ArrayList<>();

        for(PendingProjection projection:
                pending)
            updates.add(
                new CollectionLogService
                    .KillCountUpdate(
                        projection.binding
                            .collectionId,
                        projection
                            .expectedPrevious,
                        projection
                            .authoritativeCount
                    )
            );

        log.observeKillCountsAtomically(
            updates
        );
    }

    List<Binding> bindings(){
        return bindings;
    }

    private CollectionLogDefinition definition(
        CollectionLogDefinition.CollectionId id
    ){
        for(CollectionLogDefinition definition:
                log.definitions())
            if(definition.id().equals(id))
                return definition;

        return null;
    }

    private boolean subjectMatches(
        SubjectRole role,
        CombatOutcome outcome
    ){
        String actual=
            role==SubjectRole.ATTACKER
                ?outcome.attacker()
                :outcome.victim();

        return subjectRef.equals(actual);
    }

    private static String requireText(
        String value,
        String field
    ){
        Objects.requireNonNull(
            value,
            field
        );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field
            );

        return clean;
    }
}
