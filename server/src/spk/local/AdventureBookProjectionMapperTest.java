package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class AdventureBookProjectionMapperTest {
    public static void main(String[] args){
        assertExactOrderingAndWidgets();
        assertReverseResolution();
        assertFixedChapterControls();
        assertFailClosedBoundaries();
        assertDomainBoundary();

        System.out.println(
            "ADVENTURE_BOOK_PROJECTION_PASS "+
            "maxRows=25 "+
            "unclaimedThenClaimed=true "+
            "stride=15 "+
            "dynamicWidgetsExact=true "+
            "reverseSemanticResolution=true "+
            "claimVisibilityGuard=true "+
            "chapterControlsExact=true "+
            "staleMisalignedFailClosed=true "+
            "domainWidgetIdentity=false"
        );
    }

    private static void assertExactOrderingAndWidgets(){
        AdventureBookProjectionMapper mapper=
            new AdventureBookProjectionMapper();

        AdventureBookProjectionMapper.Snapshot snapshot=
            mapper.project(
                Arrays.asList(
                    new AdventureBookProjectionMapper.Entry(
                        "chapter:claimed_a",
                        true,
                        false
                    ),
                    new AdventureBookProjectionMapper.Entry(
                        "chapter:active_a",
                        false,
                        false
                    ),
                    new AdventureBookProjectionMapper.Entry(
                        "chapter:claimable",
                        false,
                        true
                    ),
                    new AdventureBookProjectionMapper.Entry(
                        "chapter:claimed_b",
                        true,
                        false
                    )
                )
            );

        List<AdventureBookProjectionMapper.Row> rows=
            snapshot.rows();

        if(rows.size()!=4)
            throw new AssertionError(
                "rows="+rows.size()
            );

        String[] expected={
            "chapter:active_a",
            "chapter:claimable",
            "chapter:claimed_a",
            "chapter:claimed_b"
        };

        for(int i=0;i<expected.length;i++){
            AdventureBookProjectionMapper.Row row=
                rows.get(i);

            if(row.ordinal!=i||
               !expected[i].equals(
                   row.objectiveKey))
                throw new AssertionError(
                    "row["+i+"]="+row
                );
        }

        AdventureBookProjectionMapper.Row first=
            rows.get(0);

        if(first.infoWidget()!=30400||
           first.teleportWidget()!=30403||
           first.claimWidget()!=null)
            throw new AssertionError(
                "first widgets="+first
            );

        AdventureBookProjectionMapper.Row claimable=
            rows.get(1);

        if(claimable.infoWidget()!=30415||
           claimable.teleportWidget()!=30418||
           !Integer.valueOf(30422)
               .equals(
                   claimable.claimWidget()))
            throw new AssertionError(
                "claimable widgets="+
                claimable
            );

        AdventureBookProjectionMapper.Row claimed=
            rows.get(2);

        if(claimed.claimWidget()!=null)
            throw new AssertionError(
                "claimed row exposed claim widget"
            );

        if(snapshot.row(
                "CHAPTER:CLAIMABLE")!=
                claimable)
            throw new AssertionError(
                "semantic key lookup failed"
            );

        boolean immutable=false;

        try{
            rows.clear();
        }catch(UnsupportedOperationException expectedException){
            immutable=true;
        }

        if(!immutable)
            throw new AssertionError(
                "rows mutable"
            );
    }

    private static void assertReverseResolution(){
        AdventureBookProjectionMapper mapper=
            new AdventureBookProjectionMapper();

        AdventureBookProjectionMapper.Snapshot snapshot=
            mapper.project(
                Arrays.asList(
                    new AdventureBookProjectionMapper.Entry(
                        "objective:one",
                        false,
                        true
                    ),
                    new AdventureBookProjectionMapper.Entry(
                        "objective:two",
                        true,
                        false
                    )
                )
            );

        AdventureBookProjectionMapper.DynamicSelection info=
            snapshot.resolveDynamicWidget(
                30400
            );

        assertSelection(
            info,
            AdventureBookProjectionMapper
                .DynamicAction.INFO,
            0,
            "objective:one"
        );

        AdventureBookProjectionMapper.DynamicSelection teleport=
            snapshot.resolveDynamicWidget(
                30418
            );

        assertSelection(
            teleport,
            AdventureBookProjectionMapper
                .DynamicAction.TELEPORT,
            1,
            "objective:two"
        );

        AdventureBookProjectionMapper.DynamicSelection claim=
            snapshot.resolveDynamicWidget(
                30407
            );

        assertSelection(
            claim,
            AdventureBookProjectionMapper
                .DynamicAction.CLAIM,
            0,
            "objective:one"
        );

        if(snapshot.resolveDynamicWidget(
                30422)!=null)
            throw new AssertionError(
                "claimed row claim action resolved"
            );

        if(snapshot.resolveDynamicWidget(
                30401)!=null)
            throw new AssertionError(
                "misaligned widget resolved"
            );

        if(snapshot.resolveDynamicWidget(
                30767)!=null)
            throw new AssertionError(
                "stale row outside current projection resolved"
            );

        if(snapshot.resolveDynamicWidget(
                30399)!=null)
            throw new AssertionError(
                "below dynamic range resolved"
            );
    }

    private static void assertFixedChapterControls(){
        if(AdventureBookProjectionMapper
                .resolveChapterWidget(
                    30380)!=
                AdventureBookProjectionMapper
                    .ChapterAction.NEXT_CHAPTER)
            throw new AssertionError(
                "next chapter mapping"
            );

        if(AdventureBookProjectionMapper
                .resolveChapterWidget(
                    30383)!=
                AdventureBookProjectionMapper
                    .ChapterAction.PREVIOUS_CHAPTER)
            throw new AssertionError(
                "previous chapter mapping"
            );

        if(AdventureBookProjectionMapper
                .resolveChapterWidget(
                    30390)!=
                AdventureBookProjectionMapper
                    .ChapterAction.CLAIM_REWARDS)
            throw new AssertionError(
                "claim chapter mapping"
            );

        if(AdventureBookProjectionMapper
                .resolveChapterWidget(
                    30393)!=null)
            throw new AssertionError(
                "presentation text treated as action"
            );
    }

    private static void assertFailClosedBoundaries(){
        AdventureBookProjectionMapper mapper=
            new AdventureBookProjectionMapper();

        ArrayList<AdventureBookProjectionMapper.Entry>
            max=
                new ArrayList<>();

        for(int i=0;i<
                AdventureBookProjectionMapper
                    .MAX_ROWS;
            i++)
            max.add(
                new AdventureBookProjectionMapper.Entry(
                    "objective:"+i,
                    false,
                    i==24
                )
            );

        AdventureBookProjectionMapper.Snapshot snapshot=
            mapper.project(max);

        AdventureBookProjectionMapper.Row last=
            snapshot.rows().get(24);

        if(last.infoWidget()!=30760||
           last.teleportWidget()!=30763||
           !Integer.valueOf(30767)
               .equals(
                   last.claimWidget()))
            throw new AssertionError(
                "last exact widget row="+
                last
            );

        max.add(
            new AdventureBookProjectionMapper.Entry(
                "objective:overflow",
                false,
                false
            )
        );

        expectIllegalArgument(
            ()->mapper.project(max)
        );

        expectIllegalState(
            ()->mapper.project(
                Arrays.asList(
                    new AdventureBookProjectionMapper.Entry(
                        "objective:dup",
                        false,
                        false
                    ),
                    new AdventureBookProjectionMapper.Entry(
                        "OBJECTIVE:DUP",
                        false,
                        false
                    )
                )
            )
        );

        expectIllegalArgument(
            ()->new AdventureBookProjectionMapper.Entry(
                "objective:bad-state",
                true,
                true
            )
        );
    }

    private static void assertDomainBoundary(){
        for(Class<?> type:
                new Class<?>[]{
                    ObjectiveDefinition.class,
                    ObjectiveProgressService
                        .Snapshot.class
                }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("widget")||
                   name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("subtype"))
                    throw new AssertionError(
                        "presentation identity leaked into "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void assertSelection(
        AdventureBookProjectionMapper.DynamicSelection selection,
        AdventureBookProjectionMapper.DynamicAction action,
        int ordinal,
        String key
    ){
        if(selection==null||
           selection.action!=action||
           selection.rowOrdinal!=ordinal||
           !key.equals(
               selection.objectiveKey))
            throw new AssertionError(
                "selection="+selection+
                " expected="+
                action+
                "/"+
                ordinal+
                "/"+
                key
            );
    }

    private static void expectIllegalArgument(
        Runnable action
    ){
        boolean failed=false;

        try{
            action.run();
        }catch(IllegalArgumentException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "expected IllegalArgumentException"
            );
    }

    private static void expectIllegalState(
        Runnable action
    ){
        boolean failed=false;

        try{
            action.run();
        }catch(IllegalStateException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "expected IllegalStateException"
            );
    }
}
