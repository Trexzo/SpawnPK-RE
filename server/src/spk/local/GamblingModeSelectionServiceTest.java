package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;

public final class GamblingModeSelectionServiceTest {
    public static void main(String[] args){
        assertExactModeSelection();
        assertIdempotentTransitions();
        assertInvalidIdsFailClosed();
        assertAuthorityBoundary();

        System.out.println(
            "GAMBLING_MODE_SELECTION_PASS "+
            "catalogEntries=6 "+
            "semanticModeIds=0..5 "+
            "selectionIdempotent=true "+
            "invalidIdsFailClosed=true "+
            "wagerMechanicsInvented=false "+
            "protocolIdentityInState=false"
        );
    }

    private static void assertExactModeSelection(){
        GamblingModeSelectionService service=
            new GamblingModeSelectionService(
                0,
                "CUSTOM_LOCALLAB"
            );

        assertSnapshot(
            service.snapshot(),
            0,
            "55x2 (P1 host)"
        );

        if(!service.select(1))
            throw new AssertionError(
                "0 -> 1 did not change"
            );

        assertSnapshot(
            service.snapshot(),
            1,
            "55x2 (P2 host)"
        );

        if(!service.select(2))
            throw new AssertionError(
                "1 -> 2 did not change"
            );

        assertSnapshot(
            service.snapshot(),
            2,
            "BJ (P1 host)"
        );

        if(!service.select(3))
            throw new AssertionError(
                "2 -> 3 did not change"
            );

        assertSnapshot(
            service.snapshot(),
            3,
            "BJ (P2 host)"
        );

        if(!service.select(4))
            throw new AssertionError(
                "3 -> 4 did not change"
            );

        assertSnapshot(
            service.snapshot(),
            4,
            "Dice duel"
        );

        if(!service.select(5))
            throw new AssertionError(
                "4 -> 5 did not change"
            );

        assertSnapshot(
            service.snapshot(),
            5,
            "Flower poker"
        );
    }

    private static void assertIdempotentTransitions(){
        GamblingModeSelectionService service=
            new GamblingModeSelectionService(
                5,
                "CUSTOM_LOCALLAB"
            );

        if(service.select(5))
            throw new AssertionError(
                "same mode not idempotent"
            );

        if(!service.select(0))
            throw new AssertionError(
                "5 -> 0 did not change"
            );

        if(service.select(0))
            throw new AssertionError(
                "repeat 0 not idempotent"
            );
    }

    private static void assertInvalidIdsFailClosed(){
        expectIllegalArgument(
            ()->new GamblingModeSelectionService(
                -1,
                "CUSTOM_LOCALLAB"
            )
        );

        expectIllegalArgument(
            ()->new GamblingModeSelectionService(
                6,
                "CUSTOM_LOCALLAB"
            )
        );

        GamblingModeSelectionService service=
            new GamblingModeSelectionService(
                4,
                "CUSTOM_LOCALLAB"
            );

        GamblingModeSelectionService.Snapshot before=
            service.snapshot();

        expectIllegalArgument(
            ()->service.select(6)
        );

        GamblingModeSelectionService.Snapshot after=
            service.snapshot();

        if(before.selectedModeId!=
                after.selectedModeId||
           !before.selectedModeName.equals(
               after.selectedModeName))
            throw new AssertionError(
                "invalid mode mutated state"
            );

        expectIllegalArgument(
            ()->new GamblingModeSelectionService(
                0,
                " "
            )
        );
    }

    private static void assertAuthorityBoundary(){
        GamblingModeSelectionService service=
            new GamblingModeSelectionService(
                0,
                "CUSTOM_LOCALLAB"
            );

        GamblingModeSelectionService.Snapshot snapshot=
            service.snapshot();

        if(!GamblingModeCatalog
                .AUTHORITY.equals(
                    snapshot.catalogAuthority))
            throw new AssertionError(
                "catalogAuthority="+
                snapshot.catalogAuthority
            );

        if(!"CUSTOM_LOCALLAB".equals(
                snapshot.runtimeAuthority))
            throw new AssertionError(
                "runtimeAuthority="+
                snapshot.runtimeAuthority
            );

        for(Field field:
                GamblingModeSelectionService
                    .Snapshot.class
                    .getDeclaredFields()){
            if(!Modifier.isFinal(
                    field.getModifiers()))
                throw new AssertionError(
                    "snapshot field mutable "+
                    field.getName()
                );

            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("widget")||
               name.contains("packet")||
               name.contains("opcode")||
               name.contains("wager")||
               name.contains("bet")||
               name.contains("currency")||
               name.contains("escrow")||
               name.contains("rng")||
               name.contains("winner")||
               name.contains("payout")||
               name.contains("refund"))
                throw new AssertionError(
                    "forbidden state field "+
                    field.getName()
                );
        }
    }

    private static void assertSnapshot(
        GamblingModeSelectionService.Snapshot snapshot,
        int modeId,
        String name
    ){
        if(snapshot.selectedModeId!=modeId||
           !name.equals(
               snapshot.selectedModeName))
            throw new AssertionError(
                "mode snapshot="+snapshot
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
}
