package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class QuickPrayerSelectionPresentationTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_QUICK_SELECTION";

    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        QuickPrayerSelectionService selections=
            new QuickPrayerSelectionService(
                player,
                POLICY
            );
        QuickPrayerSelectionPresentation presentation=
            new QuickPrayerSelectionPresentation(
                selections
            );

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter packets=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{91,92,93,94}
                )
            );

        presentation.open(
            QuickPrayerSelectionService.Book.NORMAL,
            packets
        );
        require(
            presentation.open()&&
            presentation.activeBook()==
                QuickPrayerSelectionService.Book.NORMAL,
            "normal presentation open"
        );

        String normal=
            presentation.handleWidget(17202);
        require(
            normal!=null&&
            normal.contains("normal:thick_skin")&&
            selections.snapshot()
                .draftSelection
                .contains("normal:thick_skin"),
            "shared selector normal context"
        );

        String confirm=
            presentation.handleWidget(17241);
        require(
            confirm!=null&&
            confirm.contains("book=NORMAL")&&
            !presentation.open()&&
            selections.snapshot()
                .confirmed(
                    QuickPrayerSelectionService.Book.NORMAL
                )
                .contains("normal:thick_skin"),
            "normal confirm"
        );

        setPrayerBook(
            player,
            PrayerDefinitionRepository.Book.CURSES
        );

        presentation.open(
            QuickPrayerSelectionService.Book.CURSES,
            packets
        );

        String curse=
            presentation.handleWidget(17202);
        require(
            curse!=null&&
            curse.contains("curses:protect_item")&&
            selections.snapshot()
                .draftSelection
                .contains("curses:protect_item"),
            "shared selector curse context"
        );

        require(
            presentation.handleWidget(17222)==null,
            "unattached curse selector rejected"
        );

        require(
            presentation.close()&&
            !presentation.close()&&
            !presentation.open(),
            "close/cancel context"
        );

        setPrayerBook(
            player,
            PrayerDefinitionRepository.Book.NORMAL
        );

        boolean mismatch=false;
        try{
            presentation.open(
                QuickPrayerSelectionService.Book.CURSES,
                packets
            );
        }catch(IllegalStateException expected){
            mismatch=
                expected.getMessage()
                    .contains("book/context mismatch");
        }
        require(
            mismatch,
            "book/context mismatch fails closed"
        );

        QuickPrayerSelectionPresentation.publishActive(
            packets,
            true
        );
        QuickPrayerSelectionPresentation.publishActive(
            packets,
            false
        );
        QuickPrayerSelectionPresentation.publishDisabled(
            packets
        );

        protocolBoundary();

        System.out.println(
            "QUICK_PRAYER_SELECTION_PRESENTATION_PASS "+
            "normalRoot=20000 "+
            "curseRoot=22000 "+
            "sharedSelectorContext=true "+
            "confirm17241=true "+
            "activeTokens=true "+
            "rootSelectionPolicyOwned=false "+
            "selectedConfigValueOwned=false "+
            "activationMechanicsOwned=false "+
            "persistenceOwned=false"
        );
    }

    private static void protocolBoundary(){
        for(Field field:
                QuickPrayerSelectionPresentation.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(Locale.ROOT);
            if(name.contains("drain")||
               name.contains("persist")||
               name.contains("level")||
               name.contains("conflict"))
                throw new AssertionError(
                    "unowned quick mechanics field "+
                    field.getName()
                );
        }

        for(Method method:
                QuickPrayerSelectionPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);
            if(name.contains("activateprayer")||
               name.contains("drain")||
               name.contains("persist"))
                throw new AssertionError(
                    "unowned quick mechanics method "+
                    method.getName()
                );
        }
    }

    private static void setPrayerBook(
        WorldPlayer player,
        PrayerDefinitionRepository.Book book
    )throws Exception{
        synchronized(player.mutationLock()){
            Field field=
                PrayerState.class
                    .getDeclaredField("book");
            field.setAccessible(true);
            field.set(
                player.prayers(),
                book
            );
        }
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private QuickPrayerSelectionPresentationTest(){}
}
