package spk.local;

import java.lang.reflect.Field;
import java.util.*;
import java.util.Locale;

public final class AdventureServiceTest {
    public static void main(String[] args){
        ObjectiveProgressService objectives=
            new ObjectiveProgressService();

        objectives.define(
            new ObjectiveDefinition(
                "adventure:one",
                2L,
                "LOCAL_LAB_POLICY"
            )
        );
        objectives.define(
            new ObjectiveDefinition(
                "adventure:two",
                1L,
                "LOCAL_LAB_POLICY"
            ),
            1L,
            false
        );
        objectives.define(
            new ObjectiveDefinition(
                "adventure:three",
                1L,
                "LOCAL_LAB_POLICY"
            ),
            1L,
            true
        );
        objectives.define(
            new ObjectiveDefinition(
                "adventure:four",
                3L,
                "LOCAL_LAB_POLICY"
            )
        );

        AdventureService service=
            new AdventureService(
                objectives,
                new AdventureBookProjectionMapper(),
                "EXACT_CURRENT_CLIENT",
                "LOCAL_LAB_POLICY"
            );

        AdventureService.Snapshot initial=
            service.replaceChapters(
                Arrays.asList(
                    new AdventureService.ChapterSpec(
                        "chapter:one",
                        Arrays.asList(
                            "adventure:one",
                            "adventure:two",
                            "adventure:three"
                        )
                    ),
                    new AdventureService.ChapterSpec(
                        "chapter:two",
                        Collections.singletonList(
                            "adventure:four"
                        )
                    )
                )
            );

        require(
            service.chapterCount()==2&&
            "chapter:one".equals(
                initial.selectedChapterKey
            )&&
            initial.selectedChapterOrdinal==0&&
            initial.chapter.objectives.size()==3,
            "initial Adventure chapter"
        );

        require(
            initial.chapter
                .claimableObjectiveKeys
                .equals(
                    Collections.singletonList(
                        "adventure:two"
                    )
                ),
            "initial claimable objective"
        );

        AdventureBookProjectionMapper.Snapshot
            projection=
                service.currentProjection();

        require(
            projection.rows().size()==3,
            "Adventure projection row count"
        );

        require(
            "adventure:one".equals(
                projection.rows()
                    .get(0)
                    .objectiveKey
            )&&
            "adventure:two".equals(
                projection.rows()
                    .get(1)
                    .objectiveKey
            )&&
            "adventure:three".equals(
                projection.rows()
                    .get(2)
                    .objectiveKey
            ),
            "Adventure projection ordering"
        );

        require(
            projection.rows()
                    .get(0)
                    .claimWidget()==null&&
            Integer.valueOf(30422)
                .equals(
                    projection.rows()
                        .get(1)
                        .claimWidget()
                )&&
            projection.rows()
                    .get(2)
                    .claimWidget()==null,
            "Adventure claim visibility"
        );

        boolean incompleteClaimRejected=false;

        try{
            service
                .confirmRewardSettledAndMarkClaimed(
                    "adventure:one"
                );
        }catch(
            IllegalStateException expected
        ){
            incompleteClaimRejected=true;
        }

        require(
            incompleteClaimRejected,
            "incomplete Adventure claim accepted"
        );

        require(
            service
                .confirmRewardSettledAndMarkClaimed(
                    "adventure:two"
                ),
            "completed Adventure claim not marked"
        );

        require(
            !service
                .confirmRewardSettledAndMarkClaimed(
                    "adventure:two"
                ),
            "duplicate Adventure claim changed state"
        );

        AdventureBookProjectionMapper.Snapshot
            afterClaim=
                service.currentProjection();

        require(
            "adventure:one".equals(
                afterClaim.rows()
                    .get(0)
                    .objectiveKey
            )&&
            afterClaim.rows()
                    .get(0)
                    .claimWidget()==null&&
            "adventure:two".equals(
                afterClaim.rows()
                    .get(1)
                    .objectiveKey
            )&&
            afterClaim.rows()
                    .get(1)
                    .claimed&&
            afterClaim.rows()
                    .get(1)
                    .claimWidget()==null&&
            "adventure:three".equals(
                afterClaim.rows()
                    .get(2)
                    .objectiveKey
            )&&
            afterClaim.rows()
                    .get(2)
                    .claimed,
            "Adventure projection after claim"
        );

        objectives.advance(
            "adventure:one",
            2L
        );

        require(
            service.claimableObjectiveKeys()
                .equals(
                    Collections.singletonList(
                        "adventure:one"
                    )
                ),
            "external progress not reflected in Adventure"
        );

        require(
            service.nextChapter()&&
            "chapter:two".equals(
                service.snapshot()
                    .selectedChapterKey
            )&&
            !service.nextChapter(),
            "Adventure next navigation"
        );

        require(
            service.previousChapter()&&
            "chapter:one".equals(
                service.snapshot()
                    .selectedChapterKey
            )&&
            !service.previousChapter(),
            "Adventure previous navigation"
        );

        require(
            service.selectChapter(
                "chapter:two"
            )&&
            !service.selectChapter(
                "CHAPTER:TWO"
            ),
            "Adventure explicit chapter selection"
        );

        require(
            "chapter:one".equals(
                service.chapterForObjective(
                    "ADVENTURE:ONE"
                )
            )&&
            service.objective(
                "adventure:four"
            )!=null,
            "Adventure objective membership"
        );

        boolean unknownObjectiveRejected=false;

        try{
            service.replaceChapters(
                Collections.singletonList(
                    new AdventureService.ChapterSpec(
                        "chapter:bad",
                        Collections.singletonList(
                            "adventure:missing"
                        )
                    )
                )
            );
        }catch(
            IllegalArgumentException expected
        ){
            unknownObjectiveRejected=true;
        }

        require(
            unknownObjectiveRejected,
            "unknown Adventure objective accepted"
        );

        boolean duplicateMembershipRejected=false;

        try{
            service.replaceChapters(
                Arrays.asList(
                    new AdventureService.ChapterSpec(
                        "chapter:a",
                        Collections.singletonList(
                            "adventure:one"
                        )
                    ),
                    new AdventureService.ChapterSpec(
                        "chapter:b",
                        Collections.singletonList(
                            "adventure:one"
                        )
                    )
                )
            );
        }catch(
            IllegalArgumentException expected
        ){
            duplicateMembershipRejected=true;
        }

        require(
            duplicateMembershipRejected,
            "duplicate Adventure objective membership accepted"
        );

        boolean duplicateChapterRejected=false;

        try{
            service.replaceChapters(
                Arrays.asList(
                    new AdventureService.ChapterSpec(
                        "chapter:dup",
                        Collections.singletonList(
                            "adventure:one"
                        )
                    ),
                    new AdventureService.ChapterSpec(
                        "CHAPTER:DUP",
                        Collections.singletonList(
                            "adventure:four"
                        )
                    )
                )
            );
        }catch(
            IllegalArgumentException expected
        ){
            duplicateChapterRejected=true;
        }

        require(
            duplicateChapterRejected,
            "duplicate Adventure chapter accepted"
        );

        // Failed replacement attempts must not partially replace live state.
        require(
            service.chapterCount()==2&&
            "chapter:two".equals(
                service.snapshot()
                    .selectedChapterKey
            ),
            "failed Adventure replacement mutated live configuration"
        );

        boolean immutable=false;

        try{
            service.snapshot()
                .chapterKeys
                .clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "Adventure snapshot chapter list mutable"
        );

        assertDomainBoundary();

        AdventureService.Snapshot finalSnapshot=
            service.snapshot();

        require(
            "EXACT_CURRENT_CLIENT".equals(
                finalSnapshot
                    .presentationAuthority
            )&&
            "LOCAL_LAB_POLICY".equals(
                finalSnapshot
                    .policyAuthority
            ),
            "Adventure authority separation"
        );

        System.out.println(
            "ADVENTURE_SERVICE_PASS "+
            "existingObjectiveComposition=true "+
            "chapterNavigation=true "+
            "nonWrappingPolicy=true "+
            "exactProjectionReuse=true "+
            "claimableFromProgress=true "+
            "externalRewardSettlementRequired=true "+
            "claimBookkeeping=true "+
            "failedReplaceAtomic=true "+
            "rewardMutation=false "+
            "authoritySeparated=true "+
            "protocolIndependent=true"
        );
    }

    private static void assertDomainBoundary(){
        Class<?>[] types={
            AdventureService.class,
            AdventureService.ChapterSpec.class,
            AdventureService.ChapterSnapshot.class,
            AdventureService.Snapshot.class
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
                   name.contains("subtype")||
                   name.contains("clientid"))
                    throw new AssertionError(
                        "protocol identity stored in Adventure domain "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );

                if(name.contains("rewarditem")||
                   name.contains("rewardcurrency"))
                    throw new AssertionError(
                        "reward payload leaked into Adventure domain "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
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

    private AdventureServiceTest(){}
}
