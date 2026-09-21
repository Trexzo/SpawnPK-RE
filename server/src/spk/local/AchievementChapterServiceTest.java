package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class AchievementChapterServiceTest {
    public static void main(String[] args){
        ObjectiveProgressService objectives=
            objectiveLedger();

        AchievementChapterService service=
            new AchievementChapterService(
                objectives
            );

        AchievementChapterService.Snapshot initial=
            service.replaceBindings(
                normalBindings()
            );

        require(
            service.size()==9&&
            initial.rows.size()==9,
            "all recovered rows not bound"
        );

        // Catalog row 0 says bootstrap progress 0 / claimed false.
        // Semantic state intentionally starts complete/unclaimed.
        AchievementChapterService.RowSnapshot row0=
            initial.row(0);

        require(
            row0.bootstrapInitialProgressEvidence==0L&&
            !row0.bootstrapClaimedEvidence&&
            row0.progress==1L&&
            row0.complete&&
            !row0.claimed&&
            row0.claimable,
            "catalog bootstrap state leaked into semantic objective"
        );

        // Catalog row 1 also says unclaimed. Semantic state intentionally
        // starts complete + claimed, proving the catalog does not overwrite it.
        AchievementChapterService.RowSnapshot row1=
            initial.row(1);

        require(
            !row1.bootstrapClaimedEvidence&&
            row1.complete&&
            row1.claimed&&
            !row1.claimable,
            "catalog claimed evidence overwrote semantic state"
        );

        require(
            "1464x1".equals(
                row0.rewardPairsEvidence
            )&&
            AchievementChapterBootstrapCatalog
                .PRESENTATION_AUTHORITY
                .equals(
                    row0.presentationAuthority
                )&&
            "LOCAL_LAB_POLICY".equals(
                row0.objectiveAuthority
            ),
            "Achievement evidence/authority separation"
        );

        require(
            initial.claimableObjectiveKeys
                .equals(
                    Collections.singletonList(
                        "achievement:row:0"
                    )
                ),
            "initial claimable set"
        );

        AchievementChapterService.RowSnapshot row4=
            initial.row(4);

        require(
            row4.progress==0L&&
            row4.semanticGoal==50L&&
            !row4.complete&&
            !row4.claimable,
            "row4 initial semantic state"
        );

        objectives.advance(
            "achievement:row:4",
            50L
        );

        AchievementChapterService.RowSnapshot
            row4Complete=
                service.row(4);

        require(
            row4Complete.progress==50L&&
            row4Complete.complete&&
            row4Complete.claimable,
            "external progress not reflected"
        );

        boolean incompleteClaimRejected=false;

        try{
            service
                .confirmRewardSettledAndMarkClaimed(
                    "achievement:row:2"
                );
        }catch(
            IllegalStateException expected
        ){
            incompleteClaimRejected=true;
        }

        require(
            incompleteClaimRejected,
            "incomplete Achievement claim accepted"
        );

        require(
            service
                .confirmRewardSettledAndMarkClaimed(
                    "achievement:row:0"
                ),
            "externally settled Achievement not marked claimed"
        );

        require(
            !service
                .confirmRewardSettledAndMarkClaimed(
                    "achievement:row:0"
                ),
            "duplicate Achievement claim changed state"
        );

        require(
            service.row(0).claimed&&
            !service.row(0).claimable,
            "claimed state not reflected"
        );

        assertBindingFailuresAreAtomic(
            service,
            objectives
        );

        boolean immutable=false;

        try{
            service.snapshot()
                .rows
                .clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "Achievement snapshot rows mutable"
        );

        assertDomainBoundary();

        System.out.println(
            "ACHIEVEMENT_CHAPTER_SERVICE_PASS "+
            "exactRows=9 "+
            "semanticBindings=true "+
            "goalCompatibility=true "+
            "bootstrapStateNotAuthoritative=true "+
            "externalProgressReflected=true "+
            "externalRewardSettlementRequired=true "+
            "claimBookkeeping=true "+
            "rewardPairsOpaqueEvidence=true "+
            "failedReplaceAtomic=true "+
            "rewardMutation=false "+
            "authoritySeparated=true "+
            "protocolIndependent=true"
        );
    }

    private static ObjectiveProgressService
        objectiveLedger(){
        ObjectiveProgressService objectives=
            new ObjectiveProgressService();

        for(AchievementChapterBootstrapCatalog.Row
                row:
                AchievementChapterBootstrapCatalog
                    .all()){
            String key=
                key(row.index);

            long progress=0L;
            boolean claimed=false;

            if(row.index==0)
                progress=row.goal;

            if(row.index==1){
                progress=row.goal;
                claimed=true;
            }

            objectives.define(
                new ObjectiveDefinition(
                    key,
                    row.goal,
                    "LOCAL_LAB_POLICY"
                ),
                progress,
                claimed
            );
        }

        objectives.define(
            new ObjectiveDefinition(
                "achievement:mismatch",
                2L,
                "LOCAL_LAB_POLICY"
            )
        );

        return objectives;
    }

    private static List<
        AchievementChapterService.BindingSpec
    > normalBindings(){
        ArrayList<
            AchievementChapterService.BindingSpec
        > out=
            new ArrayList<>();

        for(int index=0;
            index<
                AchievementChapterBootstrapCatalog
                    .size();
            index++)
            out.add(
                new AchievementChapterService.BindingSpec(
                    index,
                    key(index)
                )
            );

        return out;
    }

    private static void assertBindingFailuresAreAtomic(
        AchievementChapterService service,
        ObjectiveProgressService objectives
    ){
        List<
            AchievementChapterService.BindingSpec
        > missing=
            new ArrayList<>(
                normalBindings()
            );
        missing.remove(
            missing.size()-1
        );

        expectIllegalArgument(
            ()->
                service.replaceBindings(
                    missing
                )
        );

        List<
            AchievementChapterService.BindingSpec
        > duplicateObjective=
            new ArrayList<>(
                normalBindings()
            );
        duplicateObjective.set(
            1,
            new AchievementChapterService.BindingSpec(
                1,
                key(0)
            )
        );

        expectIllegalArgument(
            ()->
                service.replaceBindings(
                    duplicateObjective
                )
        );

        List<
            AchievementChapterService.BindingSpec
        > goalMismatch=
            new ArrayList<>(
                normalBindings()
            );
        goalMismatch.set(
            0,
            new AchievementChapterService.BindingSpec(
                0,
                "achievement:mismatch"
            )
        );

        expectIllegalArgument(
            ()->
                service.replaceBindings(
                    goalMismatch
                )
        );

        require(
            service.size()==9&&
            key(0).equals(
                service.row(0)
                    .objectiveKey
            )&&
            objectives.get(
                key(0)
            ).claimed,
            "failed binding replacement mutated live state"
        );
    }

    private static String key(
        int index
    ){
        return "achievement:row:"+
            index;
    }

    private static void assertDomainBoundary(){
        Class<?>[] types={
            AchievementChapterService.class,
            AchievementChapterService.BindingSpec.class
        };

        for(Class<?> type:types){
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
                   name.contains("clientindex"))
                    throw new AssertionError(
                        "protocol identity stored as Achievement domain state "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }

        for(Field field:
                ObjectiveDefinition.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("render")||
               name.contains("reward")||
               name.contains("widget")||
               name.contains("packet"))
                throw new AssertionError(
                    "presentation/reward identity leaked into ObjectiveDefinition "+
                    field.getName()
                );
        }
    }

    private static void expectIllegalArgument(
        Runnable action
    ){
        boolean rejected=false;

        try{
            action.run();
        }catch(
            IllegalArgumentException expected
        ){
            rejected=true;
        }

        require(
            rejected,
            "expected IllegalArgumentException"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private AchievementChapterServiceTest(){}
}
