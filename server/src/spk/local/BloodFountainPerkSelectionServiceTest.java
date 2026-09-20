package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

public final class BloodFountainPerkSelectionServiceTest {
    public static void main(String[] args){
        assertZeroIsFirstPerk();
        assertDeterministicSelection();
        assertInvalidIdsFailClosed();
        assertAuthorityBoundary();

        System.out.println(
            "BLOOD_FOUNTAIN_PERK_SELECTION_PASS "+
            "catalogEntries=44 "+
            "zeroMeansBloodVengeanceI=true "+
            "resetSentinel=false "+
            "selectionIdempotent=true "+
            "invalidIdsFailClosed=true "+
            "mechanicsInvented=false "+
            "protocolIdentityInState=false"
        );
    }

    private static void assertZeroIsFirstPerk(){
        BloodFountainPerkSelectionService service=
            new BloodFountainPerkSelectionService(
                0,
                "CUSTOM_LOCALLAB"
            );

        BloodFountainPerkSelectionService.Snapshot snapshot=
            service.snapshot();

        if(snapshot.selectedPerkId!=0||
           !"Blood vengeance I".equals(
               snapshot.selectedPerkName))
            throw new AssertionError(
                "perk zero="+snapshot
            );

        if(!BloodFountainSelectablePerkCatalog
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
    }

    private static void assertDeterministicSelection(){
        BloodFountainPerkSelectionService service=
            new BloodFountainPerkSelectionService(
                4,
                "CUSTOM_LOCALLAB"
            );

        if(!service.select(43))
            throw new AssertionError(
                "4 -> 43 did not change"
            );

        BloodFountainPerkSelectionService.Snapshot escape=
            service.snapshot();

        if(escape.selectedPerkId!=43||
           !"Escape Artist".equals(
               escape.selectedPerkName))
            throw new AssertionError(
                "escape="+escape
            );

        if(service.select(43))
            throw new AssertionError(
                "same selection not idempotent"
            );

        if(!service.select(0))
            throw new AssertionError(
                "43 -> 0 did not change"
            );

        BloodFountainPerkSelectionService.Snapshot first=
            service.snapshot();

        if(first.selectedPerkId!=0||
           !"Blood vengeance I".equals(
               first.selectedPerkName))
            throw new AssertionError(
                "return to perk zero="+first
            );
    }

    private static void assertInvalidIdsFailClosed(){
        expectIllegalArgument(
            ()->new BloodFountainPerkSelectionService(
                -1,
                "CUSTOM_LOCALLAB"
            )
        );

        expectIllegalArgument(
            ()->new BloodFountainPerkSelectionService(
                44,
                "CUSTOM_LOCALLAB"
            )
        );

        BloodFountainPerkSelectionService service=
            new BloodFountainPerkSelectionService(
                17,
                "CUSTOM_LOCALLAB"
            );

        BloodFountainPerkSelectionService.Snapshot before=
            service.snapshot();

        expectIllegalArgument(
            ()->service.select(44)
        );

        BloodFountainPerkSelectionService.Snapshot after=
            service.snapshot();

        if(before.selectedPerkId!=after.selectedPerkId||
           !before.selectedPerkName.equals(
               after.selectedPerkName))
            throw new AssertionError(
                "invalid selection mutated state"
            );

        expectIllegalArgument(
            ()->new BloodFountainPerkSelectionService(
                0,
                "   "
            )
        );
    }

    private static void assertAuthorityBoundary(){
        for(Field field:
                BloodFountainPerkSelectionService
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
               name.contains("command")||
               name.contains("cost")||
               name.contains("price")||
               name.contains("effect")||
               name.contains("unlock")||
               name.contains("owned")||
               name.contains("prerequisite"))
                throw new AssertionError(
                    "forbidden state field "+
                    field.getName()
                );
        }

        for(Method method:
                BloodFountainPerkSelectionService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("reset")||
               name.contains("unselect")||
               name.equals("clear"))
                throw new AssertionError(
                    "invented reset/unselect API "+
                    method.getName()
                );
        }
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
