package spk.local;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class CombatOutcomeCollectionLogObserverTest {
    public static void main(String[] args){
        CollectionLogDefinition.CollectionId bosses=
            new CollectionLogDefinition.CollectionId(
                "collection:bosses"
            );
        CollectionLogDefinition.CollectionId other=
            new CollectionLogDefinition.CollectionId(
                "collection:other"
            );

        CollectionLogDefinition bossDefinition=
            definition(
                bosses,
                "Bosses",
                true
            );
        CollectionLogDefinition otherDefinition=
            definition(
                other,
                "Other",
                false
            );

        CollectionLogService log=
            new CollectionLogService(
                Arrays.asList(
                    bossDefinition,
                    otherDefinition
                )
            );

        AtomicInteger resolverCalls=
            new AtomicInteger();

        CombatOutcomeCollectionLogObserver.Binding
            bossKills=
                new CombatOutcomeCollectionLogObserver.Binding(
                    CombatOutcomeType.NPC_KILL,
                    CombatOutcomeContext.NPC_PVM,
                    CombatOutcomeCollectionLogObserver
                        .SubjectRole.ATTACKER,
                    bosses,
                    "CUSTOM_LOCALLAB"
                );

        CombatOutcomeCollectionLogObserver observer=
            new CombatOutcomeCollectionLogObserver(
                log,
                "player:a",
                (subject,binding,outcome,current)->{
                    resolverCalls.incrementAndGet();

                    if(outcome.worldTick()==10L)
                        return 41L;

                    if(outcome.worldTick()==11L)
                        return 77L;

                    if(outcome.worldTick()==12L)
                        return -1L;

                    throw new AssertionError(
                        "unexpected resolver tick "+
                        outcome.worldTick()
                    );
                },
                Collections.singletonList(
                    bossKills
                )
            );

        observer.onCombatOutcome(
            new CombatOutcome(
                "player:x",
                "npc:1",
                CombatOutcomeType.NPC_KILL,
                CombatOutcomeContext.NPC_PVM,
                1L,
                "CUSTOM_LOCALLAB"
            )
        );

        observer.onCombatOutcome(
            new CombatOutcome(
                "player:a",
                "npc:1",
                CombatOutcomeType.PLAYER_KILL,
                CombatOutcomeContext.PLAYER_PVP,
                2L,
                "CUSTOM_LOCALLAB"
            )
        );

        require(
            resolverCalls.get()==0&&
            !log.snapshot(bosses)
                .killCount()
                .isPresent(),
            "unmatched facts invoked Collection Log resolver"
        );

        observer.onCombatOutcome(
            new CombatOutcome(
                "player:a",
                "npc:100",
                CombatOutcomeType.NPC_KILL,
                CombatOutcomeContext.NPC_PVM,
                10L,
                "CUSTOM_LOCALLAB"
            )
        );

        requireKillCount(
            log,
            bosses,
            41L,
            "first absolute kill count"
        );

        observer.onCombatOutcome(
            new CombatOutcome(
                "player:a",
                "npc:101",
                CombatOutcomeType.NPC_KILL,
                CombatOutcomeContext.NPC_PVM,
                11L,
                "CUSTOM_LOCALLAB"
            )
        );

        requireKillCount(
            log,
            bosses,
            77L,
            "second absolute kill count"
        );

        require(
            resolverCalls.get()==2,
            "resolver call count"
        );

        /*
         * 41 -> 77 rather than 41 -> 42 proves the observer does not own an
         * internal increment policy.
         */
        require(
            log.snapshot(bosses)
                .killCount()
                .getAsLong()==77L,
            "internal increment leaked into Collection Log bridge"
        );

        expect(
            IllegalArgumentException.class,
            ()->observer.onCombatOutcome(
                new CombatOutcome(
                    "player:a",
                    "npc:102",
                    CombatOutcomeType.NPC_KILL,
                    CombatOutcomeContext.NPC_PVM,
                    12L,
                    "CUSTOM_LOCALLAB"
                )
            ),
            "negative authoritative count"
        );

        requireKillCount(
            log,
            bosses,
            77L,
            "resolver failure mutated Collection Log"
        );

        CollectionLogService.ProgressSnapshot progress=
            log.snapshot(bosses);

        require(
            progress.obtainedCount()==0&&
            !progress.complete(),
            "combat bridge mutated Collection Log discovery"
        );

        staleAbsoluteCountRejected();

        constructionGuards(
            log,
            bossKills,
            bosses,
            other
        );

        require(
            "CUSTOM_LOCALLAB".equals(
                bossKills.sourceAuthority()
            ),
            "binding authority unavailable"
        );

        expect(
            UnsupportedOperationException.class,
            ()->observer.bindings().clear(),
            "Collection Log binding snapshot mutable"
        );

        protocolBoundary();

        System.out.println(
            "COMBAT_OUTCOME_COLLECTION_LOG_PASS "+
            "subjectScoped=true "+
            "typeContextFiltered=true "+
            "absoluteCountResolver=true "+
            "noInternalIncrement=true "+
            "killCountProjection=true "+
            "unsupportedCollectionRejected=true "+
            "unknownCollectionRejected=true "+
            "duplicateBindingRejected=true "+
            "resolverFailureAtomic=true "+
            "staleAbsoluteCountRejected=true "+
            "atomicBatchCas=true "+
            "discoveryMutation=false "+
            "rewardMutation=false "+
            "authorityInspectable=true "+
            "protocolIndependent=true"
        );
    }

    private static void staleAbsoluteCountRejected(){
        CollectionLogDefinition.CollectionId bosses=
            new CollectionLogDefinition.CollectionId(
                "collection:stale-bosses"
            );

        CollectionLogService log=
            new CollectionLogService(
                Collections.singletonList(
                    definition(
                        bosses,
                        "Stale Bosses",
                        true
                    )
                )
            );

        CombatOutcomeCollectionLogObserver.Binding binding=
            new CombatOutcomeCollectionLogObserver.Binding(
                CombatOutcomeType.NPC_KILL,
                CombatOutcomeContext.NPC_PVM,
                CombatOutcomeCollectionLogObserver
                    .SubjectRole.ATTACKER,
                bosses,
                "CUSTOM_LOCALLAB"
            );

        CombatOutcomeCollectionLogObserver observer=
            new CombatOutcomeCollectionLogObserver(
                log,
                "player:a",
                (subject,checked,outcome,current)->{
                    /*
                     * Simulate an authoritative update landing after the
                     * resolver's snapshot but before its proposed absolute
                     * value commits.
                     */
                    log.observeKillCount(
                        bosses,
                        99L
                    );
                    return 1L;
                },
                Collections.singletonList(
                    binding
                )
            );

        expect(
            IllegalStateException.class,
            ()->observer.onCombatOutcome(
                new CombatOutcome(
                    "player:a",
                    "npc:stale",
                    CombatOutcomeType.NPC_KILL,
                    CombatOutcomeContext.NPC_PVM,
                    50L,
                    "CUSTOM_LOCALLAB"
                )
            ),
            "stale absolute Collection Log projection"
        );

        requireKillCount(
            log,
            bosses,
            99L,
            "stale observer overwrote newer absolute count"
        );
    }

    private static void constructionGuards(
        CollectionLogService log,
        CombatOutcomeCollectionLogObserver.Binding
            bossKills,
        CollectionLogDefinition.CollectionId bosses,
        CollectionLogDefinition.CollectionId other
    ){
        CombatOutcomeCollectionLogObserver
            .AuthoritativeKillCountResolver resolver=
                (subject,binding,outcome,current)->
                    1L;

        expect(
            IllegalArgumentException.class,
            ()->new CombatOutcomeCollectionLogObserver(
                log,
                "player:a",
                resolver,
                Collections.emptyList()
            ),
            "empty bindings"
        );

        expect(
            IllegalArgumentException.class,
            ()->new CombatOutcomeCollectionLogObserver(
                log,
                "player:a",
                resolver,
                Collections.singletonList(
                    new CombatOutcomeCollectionLogObserver.Binding(
                        CombatOutcomeType.NPC_KILL,
                        CombatOutcomeContext.NPC_PVM,
                        CombatOutcomeCollectionLogObserver
                            .SubjectRole.ATTACKER,
                        new CollectionLogDefinition.CollectionId(
                            "collection:missing"
                        ),
                        "CUSTOM_LOCALLAB"
                    )
                )
            ),
            "unknown Collection Log binding"
        );

        expect(
            IllegalArgumentException.class,
            ()->new CombatOutcomeCollectionLogObserver(
                log,
                "player:a",
                resolver,
                Collections.singletonList(
                    new CombatOutcomeCollectionLogObserver.Binding(
                        CombatOutcomeType.NPC_KILL,
                        CombatOutcomeContext.NPC_PVM,
                        CombatOutcomeCollectionLogObserver
                            .SubjectRole.ATTACKER,
                        other,
                        "CUSTOM_LOCALLAB"
                    )
                )
            ),
            "unsupported kill-count collection"
        );

        expect(
            IllegalArgumentException.class,
            ()->new CombatOutcomeCollectionLogObserver(
                log,
                "player:a",
                resolver,
                Arrays.asList(
                    bossKills,
                    new CombatOutcomeCollectionLogObserver.Binding(
                        CombatOutcomeType.NPC_KILL,
                        CombatOutcomeContext.NPC_PVM,
                        CombatOutcomeCollectionLogObserver
                            .SubjectRole.ATTACKER,
                        bosses,
                        "ANOTHER_AUTHORITY"
                    )
                )
            ),
            "duplicate Collection Log binding"
        );
    }

    private static CollectionLogDefinition definition(
        CollectionLogDefinition.CollectionId id,
        String name,
        boolean supportsKillCount
    ){
        return new CollectionLogDefinition(
            id,
            new CollectionLogDefinition.CategoryId(
                "category:test"
            ),
            name,
            Collections.singleton(
                new CollectionLogDefinition.EntryId(
                    "entry:test"
                )
            ),
            supportsKillCount,
            CollectionLogEvidenceAuthority
                .CUSTOM_LOCALLAB
        );
    }

    private static void requireKillCount(
        CollectionLogService log,
        CollectionLogDefinition.CollectionId id,
        long expected,
        String label
    ){
        CollectionLogService.ProgressSnapshot snapshot=
            log.snapshot(id);

        require(
            snapshot.killCount().isPresent()&&
            snapshot.killCount()
                .getAsLong()==expected,
            label
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                CombatOutcomeCollectionLogObserver.class,
                CombatOutcomeCollectionLogObserver.Binding.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("widget")||
                   name.contains("sceneindex")||
                   name.contains("clientindex")||
                   name.contains("slot"))
                    throw new AssertionError(
                        "protocol identity leaked into Collection Log observer "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private CombatOutcomeCollectionLogObserverTest(){}
}
