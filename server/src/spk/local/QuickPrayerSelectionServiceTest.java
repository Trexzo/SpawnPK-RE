package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class QuickPrayerSelectionServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_QUICK_SELECTION";

    public static void main(String[] args)
        throws Exception
    {
        WorldPlayer player=
            new WorldPlayer();
        PrayerState prayers=
            player.prayers();

        QuickPrayerSelectionService service=
            new QuickPrayerSelectionService(
                player,
                POLICY
            );

        catalogContract();
        authorityFence(player);

        QuickPrayerSelectionService.Snapshot
            initial=
                service.snapshot();

        require(
            initial.currentBook==
                QuickPrayerSelectionService
                    .Book.NORMAL&&
            initial.revision(
                QuickPrayerSelectionService
                    .Book.NORMAL)==0L&&
            initial.revision(
                QuickPrayerSelectionService
                    .Book.CURSES)==0L&&
            !initial.editing,
            "quick selection initial state"
        );

        service.beginEdit();
        service.toggleDraft(
            "normal:thick_skin"
        );
        QuickPrayerSelectionService.Snapshot
            draft=
                service.toggleDraft(
                    "normal:smite"
                );

        require(
            draft.editing&&
            draft.editingBook==
                QuickPrayerSelectionService
                    .Book.NORMAL&&
            draft.draftSelection.equals(
                Arrays.asList(
                    "normal:thick_skin",
                    "normal:smite"
                )
            ),
            "normal quick selection draft"
        );

        QuickPrayerSelectionService.Snapshot
            normalConfirmed=
                service.confirmEdit();

        require(
            !normalConfirmed.editing&&
            normalConfirmed.revision(
                QuickPrayerSelectionService
                    .Book.NORMAL)==1L&&
            normalConfirmed.confirmed(
                QuickPrayerSelectionService
                    .Book.NORMAL
            ).equals(
                Arrays.asList(
                    "normal:thick_skin",
                    "normal:smite"
                )
            )&&
            normalConfirmed.confirmed(
                QuickPrayerSelectionService
                    .Book.CURSES
            ).isEmpty(),
            "normal quick selection confirm"
        );

        service.beginEdit();
        service.toggleDraft(
            "normal:smite"
        );

        require(
            service.cancelEdit(),
            "quick selection cancel changed"
        );
        require(
            !service.cancelEdit(),
            "quick selection cancel idempotent"
        );

        QuickPrayerSelectionService.Snapshot
            afterCancel=
                service.snapshot();

        require(
            afterCancel.revision(
                QuickPrayerSelectionService
                    .Book.NORMAL)==1L&&
            afterCancel.confirmed(
                QuickPrayerSelectionService
                    .Book.NORMAL
            ).equals(
                normalConfirmed.confirmed(
                    QuickPrayerSelectionService
                        .Book.NORMAL
                )
            ),
            "cancel preserves confirmed selection"
        );

        bookContextDrift(
            service,
            player
        );

        setPrayerBook(
            player,
            PrayerDefinitionRepository
                .Book.CURSES
        );

        service.beginEdit();

        expect(
            IllegalArgumentException.class,
            ()->service.toggleDraft(
                "normal:thick_skin"
            ),
            "cross-book semantic key"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.toggleDraft(
                "curses:turmoil_range"
            ),
            "unattached curse variant"
        );

        service.toggleDraft(
            "curses:soul_split"
        );
        service.toggleDraft(
            "curses:turmoil"
        );

        QuickPrayerSelectionService.Snapshot
            cursesConfirmed=
                service.confirmEdit();

        require(
            cursesConfirmed.currentBook==
                QuickPrayerSelectionService
                    .Book.CURSES&&
            cursesConfirmed.revision(
                QuickPrayerSelectionService
                    .Book.CURSES)==1L&&
            cursesConfirmed.confirmed(
                QuickPrayerSelectionService
                    .Book.CURSES
            ).equals(
                Arrays.asList(
                    "curses:soul_split",
                    "curses:turmoil"
                )
            )&&
            cursesConfirmed.confirmed(
                QuickPrayerSelectionService
                    .Book.NORMAL
            ).equals(
                Arrays.asList(
                    "normal:thick_skin",
                    "normal:smite"
                )
            ),
            "independent confirmed book selections"
        );

        immutableSnapshots(
            service
        );
        protocolBoundary();
        noActivationOrPersistencePolicy();

        System.out.println(
            "QUICK_PRAYER_SELECTION_SERVICE_PASS "+
            "normalVisible=29 "+
            "curseVisible=20 "+
            "semanticCatalog=true "+
            "bookContextRequired=true "+
            "draftConfirm=true "+
            "cancelPreservesConfirmed=true "+
            "revisionedConfirm=true "+
            "independentBookSetsLocalPolicy=true "+
            "unattachedCurseVariantsRejected=true "+
            "selectionLevelPolicyOwned=false "+
            "activationOrderOwned=false "+
            "conflictResolutionOwned=false "+
            "drainOwned=false "+
            "persistenceOwned=false "+
            "rawWidgetOwned=false "+
            "rawConfigOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void catalogContract(){
        List<QuickPrayerSelectionService.Option>
            normal=
                QuickPrayerSelectionService
                    .available(
                        QuickPrayerSelectionService
                            .Book.NORMAL
                    );

        List<QuickPrayerSelectionService.Option>
            curses=
                QuickPrayerSelectionService
                    .available(
                        QuickPrayerSelectionService
                            .Book.CURSES
                    );

        require(
            normal.size()==29,
            "exact normal visible count"
        );
        require(
            curses.size()==20,
            "exact curse visible count"
        );

        require(
            "normal:thick_skin".equals(
                normal.get(0).prayerKey
            )&&
            "normal:augury".equals(
                normal.get(28).prayerKey
            ),
            "normal semantic catalog endpoints"
        );

        require(
            "curses:protect_item".equals(
                curses.get(0).prayerKey
            )&&
            "curses:turmoil".equals(
                curses.get(19).prayerKey
            ),
            "curse semantic catalog endpoints"
        );

        require(
            curses.stream().noneMatch(
                option->
                    option.prayerKey.equals(
                        "curses:turmoil_range"
                    )||
                    option.prayerKey.equals(
                        "curses:turmoil_magic"
                    )
            ),
            "unattached curse variants not visible"
        );

        expect(
            UnsupportedOperationException.class,
            ()->normal.clear(),
            "catalog immutability"
        );
    }

    private static void authorityFence(
        WorldPlayer player
    ){
        expect(
            IllegalArgumentException.class,
            ()->new QuickPrayerSelectionService(
                player,
                "EXACT_CURRENT_CLIENT"
            ),
            "presentation authority used as policy"
        );

        expect(
            IllegalArgumentException.class,
            ()->new QuickPrayerSelectionService(
                player,
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown authority used as policy"
        );
    }

    private static void bookContextDrift(
        QuickPrayerSelectionService service,
        WorldPlayer player
    ) throws Exception {
        setPrayerBook(
            player,
            PrayerDefinitionRepository
                .Book.NORMAL
        );

        service.beginEdit();
        service.toggleDraft(
            "normal:piety"
        );

        setPrayerBook(
            player,
            PrayerDefinitionRepository
                .Book.CURSES
        );

        expect(
            IllegalStateException.class,
            ()->service.toggleDraft(
                "normal:rigour"
            ),
            "book drift toggle"
        );

        expect(
            IllegalStateException.class,
            service::confirmEdit,
            "book drift confirm"
        );

        require(
            service.cancelEdit(),
            "book drift draft cancellation"
        );

        setPrayerBook(
            player,
            PrayerDefinitionRepository
                .Book.NORMAL
        );

        QuickPrayerSelectionService.Snapshot
            stable=
                service.snapshot();

        require(
            stable.revision(
                QuickPrayerSelectionService
                    .Book.NORMAL)==1L&&
            stable.confirmed(
                QuickPrayerSelectionService
                    .Book.NORMAL
            ).equals(
                Arrays.asList(
                    "normal:thick_skin",
                    "normal:smite"
                )
            ),
            "book drift did not mutate confirmed state"
        );
    }

    private static void immutableSnapshots(
        QuickPrayerSelectionService service
    ){
        QuickPrayerSelectionService.Snapshot
            snapshot=
                service.snapshot();

        expect(
            UnsupportedOperationException.class,
            ()->snapshot.confirmed(
                QuickPrayerSelectionService
                    .Book.NORMAL
            ).clear(),
            "confirmed selection immutability"
        );

        expect(
            UnsupportedOperationException.class,
            ()->snapshot.confirmedSelections
                .clear(),
            "confirmed map immutability"
        );

        expect(
            UnsupportedOperationException.class,
            ()->snapshot.confirmedRevisions
                .clear(),
            "revision map immutability"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                QuickPrayerSelectionService.class,
                QuickPrayerSelectionService
                    .Option.class,
                QuickPrayerSelectionService
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
                   name.contains("config")||
                   name.contains("varp")||
                   name.contains("opcode")||
                   name.contains("packet")||
                   name.contains("root")||
                   name.contains("subtype")||
                   name.contains("clientslot"))
                    throw new AssertionError(
                        "protocol identity leaked into quick selection "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void
        noActivationOrPersistencePolicy()
    {
        for(Method method:
                QuickPrayerSelectionService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("activate")||
               name.contains("drain")||
               name.contains("persist")||
               name.contains("savefile"))
                throw new AssertionError(
                    "unowned quick-prayer mechanics leaked into "+
                    method.getName()
                );
        }
    }

    private static void setPrayerBook(
        WorldPlayer player,
        PrayerDefinitionRepository.Book book
    ) throws Exception {
        synchronized(player.mutationLock()){
            Field field=
                PrayerState.class
                    .getDeclaredField(
                        "book"
                    );

            field.setAccessible(true);
            field.set(
                player.prayers(),
                book
            );
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

    private QuickPrayerSelectionServiceTest(){}
}
