package spk.local;

import java.util.*;

/**
 * Exact-current Collection Log application layer over CollectionLogService.
 *
 * The semantic aggregate remains the source of truth for discoveries,
 * completion and optional kill-count observations. This layer owns only
 * player-scoped application selection and post-settlement completion-claim
 * bookkeeping.
 */
final class CollectionLogApplicationService {
    static final int MAX_RESULT_SLOTS=120;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static final class ApplicationDefinition {
        final CollectionLogDefinition definition;
        final List<CollectionLogDefinition.EntryId>
            resultEntries;
        final String completionRewardHeading;
        final String completionRewardDescription;
        final CollectionLogEvidenceAuthority
            sourceAuthority;

        ApplicationDefinition(
            CollectionLogDefinition definition,
            Collection<
                CollectionLogDefinition.EntryId
            > resultEntries,
            String completionRewardHeading,
            String completionRewardDescription,
            CollectionLogEvidenceAuthority
                sourceAuthority
        ){
            this.definition=
                Objects.requireNonNull(
                    definition,
                    "definition"
                );
            this.sourceAuthority=
                Objects.requireNonNull(
                    sourceAuthority,
                    "sourceAuthority"
                );

            if(definition.authority()!=
                    sourceAuthority)
                throw new IllegalArgumentException(
                    "Collection Log application authority mismatch "+
                    definition.id()
                );

            Objects.requireNonNull(
                resultEntries,
                "resultEntries"
            );

            if(resultEntries.size()>
                    MAX_RESULT_SLOTS)
                throw new IllegalArgumentException(
                    "Collection Log result slots="+
                    resultEntries.size()+
                    " max="+MAX_RESULT_SLOTS
                );

            ArrayList<
                CollectionLogDefinition.EntryId
            > copy=
                new ArrayList<>();

            HashSet<
                CollectionLogDefinition.EntryId
            > unique=
                new HashSet<>();

            for(
                CollectionLogDefinition.EntryId
                    entryId:
                resultEntries
            ){
                CollectionLogDefinition.EntryId
                    checked=
                        Objects.requireNonNull(
                            entryId,
                            "result entry"
                        );

                if(!definition
                        .requiredEntries()
                        .contains(checked))
                    throw new IllegalArgumentException(
                        "Collection Log result entry "+
                        checked+
                        " not required by "+
                        definition.id()
                    );

                if(!unique.add(checked))
                    throw new IllegalArgumentException(
                        "duplicate Collection Log result entry "+
                        checked
                    );

                copy.add(checked);
            }

            this.resultEntries=
                Collections.unmodifiableList(
                    copy
                );
            this.completionRewardHeading=
                requireText(
                    completionRewardHeading,
                    "completionRewardHeading"
                );
            this.completionRewardDescription=
                requireText(
                    completionRewardDescription,
                    "completionRewardDescription"
                );
        }
    }

    static final class CollectionSnapshot {
        final CollectionLogDefinition.CollectionId
            collectionId;
        final CollectionLogDefinition.CategoryId
            categoryId;
        final String name;
        final List<
            CollectionLogDefinition.EntryId
        > resultEntries;
        final int obtainedCount;
        final int totalCount;
        final boolean complete;
        final Long killCount;
        final boolean completionRewardClaimed;
        final String completionRewardHeading;
        final String completionRewardDescription;
        final CollectionLogEvidenceAuthority
            sourceAuthority;
        final String presentationAuthority;

        CollectionSnapshot(
            ApplicationDefinition application,
            CollectionLogService.ProgressSnapshot
                progress,
            boolean completionRewardClaimed
        ){
            this.collectionId=
                application.definition.id();
            this.categoryId=
                application.definition.categoryId();
            this.name=
                application.definition.name();
            this.resultEntries=
                application.resultEntries;
            this.obtainedCount=
                progress.obtainedCount();
            this.totalCount=
                progress.totalCount();
            this.complete=
                progress.complete();
            this.killCount=
                progress.killCount()
                    .isPresent()
                    ?Long.valueOf(
                        progress.killCount()
                            .getAsLong()
                    )
                    :null;
            this.completionRewardClaimed=
                completionRewardClaimed;
            this.completionRewardHeading=
                application
                    .completionRewardHeading;
            this.completionRewardDescription=
                application
                    .completionRewardDescription;
            this.sourceAuthority=
                application.sourceAuthority;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        boolean supportsKillCount(){
            return killCount!=null;
        }

        boolean rewardClaimable(){
            return complete&&
                !completionRewardClaimed;
        }
    }

    static final class PlayerSnapshot {
        final String playerRef;
        final CollectionLogDefinition.CategoryId
            selectedCategoryId;
        final CollectionLogDefinition.CollectionId
            selectedCollectionId;
        final List<CollectionSnapshot> collections;

        PlayerSnapshot(
            String playerRef,
            CollectionLogDefinition.CategoryId
                selectedCategoryId,
            CollectionLogDefinition.CollectionId
                selectedCollectionId,
            Collection<CollectionSnapshot>
                collections
        ){
            this.playerRef=playerRef;
            this.selectedCategoryId=
                selectedCategoryId;
            this.selectedCollectionId=
                selectedCollectionId;
            this.collections=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        collections
                    )
                );
        }

        CollectionSnapshot collection(
            CollectionLogDefinition.CollectionId
                collectionId
        ){
            CollectionLogDefinition.CollectionId
                id=
                    Objects.requireNonNull(
                        collectionId,
                        "collectionId"
                    );

            for(CollectionSnapshot collection:
                    collections)
                if(collection.collectionId
                        .equals(id))
                    return collection;

            return null;
        }

        CollectionSnapshot selectedCollection(){
            return selectedCollectionId==null
                ?null
                :collection(
                    selectedCollectionId
                );
        }
    }

    static final class DiscoveryResult {
        final CollectionLogService.DiscoveryResult
            result;
        final CollectionSnapshot collection;

        DiscoveryResult(
            CollectionLogService.DiscoveryResult
                result,
            CollectionSnapshot collection
        ){
            this.result=
                Objects.requireNonNull(
                    result,
                    "result"
                );
            this.collection=
                Objects.requireNonNull(
                    collection,
                    "collection"
                );
        }
    }

    static final class ClaimResult {
        final boolean changed;
        final CollectionSnapshot collection;

        ClaimResult(
            boolean changed,
            CollectionSnapshot collection
        ){
            this.changed=changed;
            this.collection=
                Objects.requireNonNull(
                    collection,
                    "collection"
                );
        }
    }

    private static final class PlayerState {
        final String playerRef;
        final CollectionLogService log;
        final LinkedHashSet<
            CollectionLogDefinition.CollectionId
        > claimed=
            new LinkedHashSet<>();

        CollectionLogDefinition.CategoryId
            selectedCategoryId;
        CollectionLogDefinition.CollectionId
            selectedCollectionId;

        PlayerState(
            String playerRef,
            CollectionLogService log
        ){
            this.playerRef=playerRef;
            this.log=log;
        }
    }

    private final List<CollectionLogDefinition>
        semanticDefinitions;

    private final LinkedHashMap<
        CollectionLogDefinition.CollectionId,
        ApplicationDefinition
    > applications=
        new LinkedHashMap<>();

    private final LinkedHashSet<
        CollectionLogDefinition.CategoryId
    > categories=
        new LinkedHashSet<>();

    private final LinkedHashMap<String,PlayerState>
        players=
            new LinkedHashMap<>();

    CollectionLogApplicationService(
        Collection<ApplicationDefinition>
            definitions
    ){
        Objects.requireNonNull(
            definitions,
            "definitions"
        );

        if(definitions.isEmpty())
            throw new IllegalArgumentException(
                "at least one Collection Log application definition required"
            );

        ArrayList<CollectionLogDefinition>
            semantic=
                new ArrayList<>();

        for(ApplicationDefinition application:
                definitions){
            ApplicationDefinition checked=
                Objects.requireNonNull(
                    application,
                    "application definition"
                );

            CollectionLogDefinition.CollectionId
                id=
                    checked.definition.id();

            if(applications.put(
                    id,
                    checked)!=null)
                throw new IllegalArgumentException(
                    "duplicate Collection Log application "+
                    id
                );

            semantic.add(
                checked.definition
            );
            categories.add(
                checked.definition
                    .categoryId()
            );
        }

        this.semanticDefinitions=
            Collections.unmodifiableList(
                semantic
            );
    }

    synchronized PlayerSnapshot selectCategory(
        String playerRef,
        CollectionLogDefinition.CategoryId
            categoryId
    ){
        PlayerState state=
            state(playerRef);

        CollectionLogDefinition.CategoryId
            category=
                Objects.requireNonNull(
                    categoryId,
                    "categoryId"
                );

        if(!categories.contains(category))
            throw new IllegalArgumentException(
                "unknown Collection Log category "+
                category
            );

        state.selectedCategoryId=
            category;

        if(state.selectedCollectionId!=null){
            ApplicationDefinition selected=
                applications.get(
                    state.selectedCollectionId
                );

            if(selected==null)
                throw new IllegalStateException(
                    "selected Collection Log disappeared "+
                    state.selectedCollectionId
                );

            if(!selected.definition
                    .categoryId()
                    .equals(category))
                state.selectedCollectionId=
                    null;
        }

        return snapshotOf(state);
    }

    synchronized PlayerSnapshot selectCollection(
        String playerRef,
        CollectionLogDefinition.CollectionId
            collectionId
    ){
        PlayerState state=
            state(playerRef);

        ApplicationDefinition application=
            requireApplication(
                collectionId
            );

        if(state.selectedCategoryId!=null&&
           !application.definition
                .categoryId()
                .equals(
                    state.selectedCategoryId))
            throw new IllegalStateException(
                "Collection "+
                collectionId+
                " not in selected category "+
                state.selectedCategoryId
            );

        state.selectedCollectionId=
            application.definition.id();

        return snapshotOf(state);
    }

    synchronized DiscoveryResult
        recordValidatedDiscovery(
            String playerRef,
            CollectionLogDefinition.CollectionId
                collectionId,
            CollectionLogDefinition.EntryId
                entryId
        ){
        PlayerState state=
            state(playerRef);

        CollectionLogService.DiscoveryResult
            result=
                state.log.observe(
                    new CollectionLogService
                        .CollectionEntryObserved(
                            Objects.requireNonNull(
                                collectionId,
                                "collectionId"
                            ),
                            Objects.requireNonNull(
                                entryId,
                                "entryId"
                            )
                        )
                );

        return new DiscoveryResult(
            result,
            snapshotCollection(
                state,
                requireApplication(
                    collectionId
                )
            )
        );
    }

    synchronized CollectionSnapshot
        recordAuthoritativeKillCount(
            String playerRef,
            CollectionLogDefinition.CollectionId
                collectionId,
            long authoritativeCount
        ){
        PlayerState state=
            state(playerRef);

        state.log.observeKillCount(
            Objects.requireNonNull(
                collectionId,
                "collectionId"
            ),
            authoritativeCount
        );

        return snapshotCollection(
            state,
            requireApplication(
                collectionId
            )
        );
    }

    /**
     * Call only after completion-reward settlement succeeded externally.
     */
    synchronized ClaimResult
        confirmRewardSettledAndMarkClaimed(
            String playerRef,
            CollectionLogDefinition.CollectionId
                collectionId
        ){
        PlayerState state=
            state(playerRef);
        ApplicationDefinition application=
            requireApplication(
                collectionId
            );

        CollectionLogService.ProgressSnapshot
            progress=
                state.log.snapshot(
                    application.definition.id()
                );

        if(!progress.complete())
            throw new IllegalStateException(
                "Collection Log incomplete "+
                application.definition.id()
            );

        boolean changed=
            state.claimed.add(
                application.definition.id()
            );

        return new ClaimResult(
            changed,
            snapshotCollection(
                state,
                application
            )
        );
    }

    synchronized PlayerSnapshot getPlayer(
        String playerRef
    ){
        PlayerState state=
            players.get(
                normalizePlayer(
                    playerRef
                )
            );

        return state==null
            ?null
            :snapshotOf(state);
    }

    synchronized List<ApplicationDefinition>
        catalog(){
        return Collections.unmodifiableList(
            new ArrayList<>(
                applications.values()
            )
        );
    }

    synchronized int playerCount(){
        return players.size();
    }

    private PlayerState state(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        PlayerState state=
            players.get(player);

        if(state==null){
            state=
                new PlayerState(
                    player,
                    new CollectionLogService(
                        semanticDefinitions
                    )
                );

            players.put(
                player,
                state
            );
        }

        return state;
    }

    private PlayerSnapshot snapshotOf(
        PlayerState state
    ){
        ArrayList<CollectionSnapshot>
            out=
                new ArrayList<>();

        for(ApplicationDefinition application:
                applications.values())
            out.add(
                snapshotCollection(
                    state,
                    application
                )
            );

        return new PlayerSnapshot(
            state.playerRef,
            state.selectedCategoryId,
            state.selectedCollectionId,
            out
        );
    }

    private CollectionSnapshot snapshotCollection(
        PlayerState state,
        ApplicationDefinition application
    ){
        if(application.definition
                .authority()!=
                application.sourceAuthority)
            throw new IllegalStateException(
                "Collection Log application authority drift "+
                application.definition.id()
            );

        return new CollectionSnapshot(
            application,
            state.log.snapshot(
                application.definition.id()
            ),
            state.claimed.contains(
                application.definition.id()
            )
        );
    }

    private ApplicationDefinition
        requireApplication(
            CollectionLogDefinition.CollectionId
                collectionId
        ){
        CollectionLogDefinition.CollectionId
            id=
                Objects.requireNonNull(
                    collectionId,
                    "collectionId"
                );

        ApplicationDefinition application=
            applications.get(id);

        if(application==null)
            throw new IllegalArgumentException(
                "unknown Collection Log "+
                id
            );

        return application;
    }

    private static String normalizePlayer(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "playerRef"
            );

        String normalized=
            value.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "playerRef blank"
            );

        return normalized;
    }

    private static String requireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(
                field
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        return clean;
    }
}
