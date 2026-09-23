package spk.local;

import java.util.*;

/**
 * Concrete Adventure gameplay/domain composition above ObjectiveProgressService
 * and the exact-current Adventure book projection mapper.
 *
 * Objective definitions, progress attribution, reward settlement, teleport
 * destination policy, reset cadence and persistence remain external.
 */
final class AdventureService {
    static final class ChapterSpec {
        final String key;
        final List<String> objectiveKeys;

        ChapterSpec(
            String key,
            Collection<String> objectiveKeys
        ){
            this.key=
                ObjectiveDefinition
                    .normalizeKey(key);

            Objects.requireNonNull(
                objectiveKeys,
                "objectiveKeys"
            );

            if(objectiveKeys.isEmpty())
                throw new IllegalArgumentException(
                    "Adventure chapter has no objectives "+
                    this.key
                );

            ArrayList<String> normalized=
                new ArrayList<>();
            HashSet<String> seen=
                new HashSet<>();

            for(String objectiveKey:
                    objectiveKeys){
                String value=
                    ObjectiveDefinition
                        .normalizeKey(
                            objectiveKey
                        );

                if(!seen.add(value))
                    throw new IllegalArgumentException(
                        "duplicate Adventure objective "+
                        value+
                        " in chapter "+
                        this.key
                    );

                normalized.add(value);
            }

            this.objectiveKeys=
                Collections.unmodifiableList(
                    normalized
                );
        }
    }

    static final class ChapterSnapshot {
        final String key;
        final int ordinal;
        final List<ObjectiveProgressService.Snapshot>
            objectives;
        final List<String> claimableObjectiveKeys;

        ChapterSnapshot(
            Chapter chapter,
            int ordinal,
            ObjectiveProgressService progress
        ){
            this.key=chapter.key;
            this.ordinal=ordinal;

            ArrayList<ObjectiveProgressService.Snapshot>
                states=
                    new ArrayList<>();
            ArrayList<String> claimable=
                new ArrayList<>();

            for(String objectiveKey:
                    chapter.objectiveKeys){
                ObjectiveProgressService.Snapshot
                    state=
                        progress.get(
                            objectiveKey
                        );

                if(state==null)
                    throw new IllegalStateException(
                        "Adventure objective disappeared "+
                        objectiveKey
                    );

                states.add(state);

                if(state.complete&&
                   !state.claimed)
                    claimable.add(
                        state.key
                    );
            }

            this.objectives=
                Collections.unmodifiableList(
                    states
                );
            this.claimableObjectiveKeys=
                Collections.unmodifiableList(
                    claimable
                );
        }

        boolean hasClaimableObjectives(){
            return !claimableObjectiveKeys
                .isEmpty();
        }
    }

    static final class Snapshot {
        final String selectedChapterKey;
        final int selectedChapterOrdinal;
        final List<String> chapterKeys;
        final ChapterSnapshot chapter;
        final String presentationAuthority;
        final String policyAuthority;

        Snapshot(
            String selectedChapterKey,
            int selectedChapterOrdinal,
            List<String> chapterKeys,
            ChapterSnapshot chapter,
            String presentationAuthority,
            String policyAuthority
        ){
            this.selectedChapterKey=
                selectedChapterKey;
            this.selectedChapterOrdinal=
                selectedChapterOrdinal;
            this.chapterKeys=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        chapterKeys
                    )
                );
            this.chapter=chapter;
            this.presentationAuthority=
                presentationAuthority;
            this.policyAuthority=
                policyAuthority;
        }
    }

    private static final class Chapter {
        final String key;
        final List<String> objectiveKeys;

        Chapter(
            ChapterSpec spec
        ){
            this.key=spec.key;
            this.objectiveKeys=
                spec.objectiveKeys;
        }
    }

    private final ObjectiveProgressService objectives;
    private final AdventureBookProjectionMapper mapper;
    private final String presentationAuthority;
    private final String policyAuthority;

    private List<Chapter> chapters=
        Collections.emptyList();

    private LinkedHashMap<String,Integer>
        chapterOrdinalByKey=
            new LinkedHashMap<>();

    private HashMap<String,String>
        chapterByObjectiveKey=
            new HashMap<>();

    private int selectedChapterOrdinal=-1;

    AdventureService(
        ObjectiveProgressService objectives,
        AdventureBookProjectionMapper mapper,
        String presentationAuthority,
        String policyAuthority
    ){
        this.objectives=
            Objects.requireNonNull(
                objectives,
                "objectives"
            );
        this.mapper=
            Objects.requireNonNull(
                mapper,
                "mapper"
            );
        this.presentationAuthority=
            requireAuthority(
                presentationAuthority,
                "presentationAuthority"
            );
        this.policyAuthority=
            requireAuthority(
                policyAuthority,
                "policyAuthority"
            );
    }

    Snapshot replaceChapters(
        Collection<ChapterSpec> specs
    ){
        Objects.requireNonNull(
            specs,
            "specs"
        );

        if(specs.isEmpty())
            throw new IllegalArgumentException(
                "Adventure requires at least one chapter"
            );

        ArrayList<Chapter> next=
            new ArrayList<>();
        LinkedHashMap<String,Integer>
            nextOrdinals=
                new LinkedHashMap<>();
        HashMap<String,String>
            nextObjectiveOwners=
                new HashMap<>();

        int ordinal=0;

        /*
         * Objective existence validation uses ObjectiveProgressService's own
         * synchronization. No Adventure monitor is held during shared reads.
         */
        for(ChapterSpec spec:specs){
            ChapterSpec checked=
                Objects.requireNonNull(
                    spec,
                    "chapter spec"
                );

            if(nextOrdinals.containsKey(
                    checked.key))
                throw new IllegalArgumentException(
                    "duplicate Adventure chapter "+
                    checked.key
                );

            if(checked.objectiveKeys.size()>
                    AdventureBookProjectionMapper
                        .MAX_ROWS)
                throw new IllegalArgumentException(
                    "Adventure chapter "+
                    checked.key+
                    " exceeds exact-client row cap "+
                    AdventureBookProjectionMapper
                        .MAX_ROWS
                );

            for(String objectiveKey:
                    checked.objectiveKeys){
                ObjectiveProgressService.Snapshot state=
                    objectives.get(
                        objectiveKey
                    );

                if(state==null)
                    throw new IllegalArgumentException(
                        "unknown Adventure objective "+
                        objectiveKey
                    );

                String prior=
                    nextObjectiveOwners.put(
                        objectiveKey,
                        checked.key
                    );

                if(prior!=null)
                    throw new IllegalArgumentException(
                        "Adventure objective "+
                        objectiveKey+
                        " assigned to chapters "+
                        prior+
                        " and "+
                        checked.key
                    );
            }

            nextOrdinals.put(
                checked.key,
                ordinal++
            );
            next.add(
                new Chapter(
                    checked
                )
            );
        }

        synchronized(this){
            chapters=
                Collections.unmodifiableList(
                    next
                );
            chapterOrdinalByKey=
                new LinkedHashMap<>(
                    nextOrdinals
                );
            chapterByObjectiveKey=
                new HashMap<>(
                    nextObjectiveOwners
                );
            selectedChapterOrdinal=0;
        }

        return snapshot();
    }

    synchronized boolean selectChapter(
        String chapterKey
    ){
        Integer ordinal=
            chapterOrdinalByKey.get(
                ObjectiveDefinition
                    .normalizeKey(
                        chapterKey
                    )
            );

        if(ordinal==null)
            throw new IllegalArgumentException(
                "unknown Adventure chapter "+
                chapterKey
            );

        if(selectedChapterOrdinal==
                ordinal)
            return false;

        selectedChapterOrdinal=
            ordinal;
        return true;
    }

    synchronized boolean nextChapter(){
        requireConfiguredLocked();

        if(selectedChapterOrdinal>=
                chapters.size()-1)
            return false;

        selectedChapterOrdinal++;
        return true;
    }

    synchronized boolean previousChapter(){
        requireConfiguredLocked();

        if(selectedChapterOrdinal<=0)
            return false;

        selectedChapterOrdinal--;
        return true;
    }

    Snapshot snapshot(){
        final Chapter chapter;
        final int ordinal;
        final List<String> keys;

        synchronized(this){
            chapter=
                selectedChapterLocked();
            ordinal=
                selectedChapterOrdinal;

            ArrayList<String> copy=
                new ArrayList<>();

            for(Chapter value:chapters)
                copy.add(
                    value.key
                );

            keys=
                Collections.unmodifiableList(
                    copy
                );
        }

        return new Snapshot(
            chapter.key,
            ordinal,
            keys,
            new ChapterSnapshot(
                chapter,
                ordinal,
                objectives
            ),
            presentationAuthority,
            policyAuthority
        );
    }

    ChapterSnapshot chapter(
        String chapterKey
    ){
        final Chapter chapter;
        final int ordinal;

        synchronized(this){
            Integer found=
                chapterOrdinalByKey.get(
                    ObjectiveDefinition
                        .normalizeKey(
                            chapterKey
                        )
                );

            if(found==null)
                return null;

            ordinal=found;
            chapter=
                chapters.get(
                    ordinal
                );
        }

        return new ChapterSnapshot(
            chapter,
            ordinal,
            objectives
        );
    }

    List<String> claimableObjectiveKeys(){
        return snapshot()
            .chapter
            .claimableObjectiveKeys;
    }

    boolean confirmRewardSettledAndMarkClaimed(
        String objectiveKey
    ){
        String normalized=
            ObjectiveDefinition
                .normalizeKey(
                    objectiveKey
                );

        /*
         * Fixed cross-aggregate order: ObjectiveProgressService -> Adventure.
         * Other Adventure paths never hold the Adventure monitor while
         * entering ObjectiveProgressService.
         */
        synchronized(objectives){
            synchronized(this){
                String chapterKey=
                    chapterByObjectiveKey.get(
                        normalized
                    );

                if(chapterKey==null)
                    throw new IllegalArgumentException(
                        "objective not assigned to Adventure "+
                        normalized
                    );

                ObjectiveProgressService.Snapshot state=
                    objectives.get(
                        normalized
                    );

                if(state==null)
                    throw new IllegalStateException(
                        "Adventure objective disappeared "+
                        normalized
                    );

                if(!state.complete)
                    throw new IllegalStateException(
                        "Adventure objective incomplete "+
                        normalized
                    );

                return objectives.markClaimed(
                    normalized
                );
            }
        }
    }

    AdventureBookProjectionMapper.Snapshot
        currentProjection(){
        final Chapter chapter;

        synchronized(this){
            chapter=
                selectedChapterLocked();
        }

        ArrayList<AdventureBookProjectionMapper.Entry>
            entries=
                new ArrayList<>();

        for(String objectiveKey:
                chapter.objectiveKeys){
            ObjectiveProgressService.Snapshot state=
                objectives.get(
                    objectiveKey
                );

            if(state==null)
                throw new IllegalStateException(
                    "Adventure objective disappeared "+
                    objectiveKey
                );

            entries.add(
                new AdventureBookProjectionMapper.Entry(
                    state.key,
                    state.claimed,
                    state.complete&&
                        !state.claimed
                )
            );
        }

        return mapper.project(
            entries
        );
    }

    ObjectiveProgressService.Snapshot objective(
        String objectiveKey
    ){
        String normalized=
            ObjectiveDefinition
                .normalizeKey(
                    objectiveKey
                );
        final boolean assigned;

        synchronized(this){
            assigned=
                chapterByObjectiveKey
                    .containsKey(
                        normalized
                    );
        }

        return assigned
            ?objectives.get(
                normalized
            )
            :null;
    }

    String chapterForObjective(
        String objectiveKey
    ){
        synchronized(this){
            return chapterByObjectiveKey.get(
                ObjectiveDefinition
                    .normalizeKey(
                        objectiveKey
                    )
            );
        }
    }

    int chapterCount(){
        synchronized(this){
            return chapters.size();
        }
    }

    private Chapter selectedChapterLocked(){
        requireConfiguredLocked();
        return chapters.get(
            selectedChapterOrdinal
        );
    }

    private void requireConfiguredLocked(){
        if(selectedChapterOrdinal<0||
           chapters.isEmpty())
            throw new IllegalStateException(
                "Adventure chapters not configured"
            );
    }

    private static String requireAuthority(
        String value,
        String label
    ){
        if(value==null)
            throw new NullPointerException(
                label
            );

        String normalized=
            value.trim();

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                label+" blank"
            );

        return normalized;
    }
}
