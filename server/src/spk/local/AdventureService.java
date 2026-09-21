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

    synchronized Snapshot replaceChapters(
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
                ObjectiveProgressService.Snapshot
                    state=
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

        chapters=
            Collections.unmodifiableList(
                next
            );
        chapterOrdinalByKey=
            nextOrdinals;
        chapterByObjectiveKey=
            nextObjectiveOwners;
        selectedChapterOrdinal=0;

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
        requireConfigured();

        if(selectedChapterOrdinal>=
                chapters.size()-1)
            return false;

        selectedChapterOrdinal++;
        return true;
    }

    synchronized boolean previousChapter(){
        requireConfigured();

        if(selectedChapterOrdinal<=0)
            return false;

        selectedChapterOrdinal--;
        return true;
    }

    synchronized Snapshot snapshot(){
        Chapter chapter=
            selectedChapter();

        ArrayList<String> keys=
            new ArrayList<>();

        for(Chapter value:chapters)
            keys.add(value.key);

        return new Snapshot(
            chapter.key,
            selectedChapterOrdinal,
            keys,
            new ChapterSnapshot(
                chapter,
                selectedChapterOrdinal,
                objectives
            ),
            presentationAuthority,
            policyAuthority
        );
    }

    synchronized ChapterSnapshot chapter(
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
            return null;

        return new ChapterSnapshot(
            chapters.get(ordinal),
            ordinal,
            objectives
        );
    }

    synchronized List<String>
        claimableObjectiveKeys(){
        return snapshot()
            .chapter
            .claimableObjectiveKeys;
    }

    synchronized boolean confirmRewardSettledAndMarkClaimed(
        String objectiveKey
    ){
        String normalized=
            ObjectiveDefinition
                .normalizeKey(
                    objectiveKey
                );

        String chapterKey=
            chapterByObjectiveKey.get(
                normalized
            );

        if(chapterKey==null)
            throw new IllegalArgumentException(
                "objective not assigned to Adventure "+
                normalized
            );

        ObjectiveProgressService.Snapshot
            state=
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

    synchronized AdventureBookProjectionMapper.Snapshot
        currentProjection(){
        Chapter chapter=
            selectedChapter();

        ArrayList<AdventureBookProjectionMapper.Entry>
            entries=
                new ArrayList<>();

        for(String objectiveKey:
                chapter.objectiveKeys){
            ObjectiveProgressService.Snapshot
                state=
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

    synchronized ObjectiveProgressService.Snapshot
        objective(
            String objectiveKey
        ){
        String normalized=
            ObjectiveDefinition
                .normalizeKey(
                    objectiveKey
                );

        if(!chapterByObjectiveKey
                .containsKey(normalized))
            return null;

        return objectives.get(
            normalized
        );
    }

    synchronized String chapterForObjective(
        String objectiveKey
    ){
        return chapterByObjectiveKey.get(
            ObjectiveDefinition
                .normalizeKey(
                    objectiveKey
                )
        );
    }

    synchronized int chapterCount(){
        return chapters.size();
    }

    private Chapter selectedChapter(){
        requireConfigured();
        return chapters.get(
            selectedChapterOrdinal
        );
    }

    private void requireConfigured(){
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
