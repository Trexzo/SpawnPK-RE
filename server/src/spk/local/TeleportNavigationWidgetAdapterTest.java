package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class TeleportNavigationWidgetAdapterTest {
    public static void main(String[] args){
        LinkedHashMap<Integer,TeleportNavigationService.EntryKind> expected=
            new LinkedHashMap<>();

        expected.put(1195,TeleportNavigationService.EntryKind.HOME);
        expected.put(12856,TeleportNavigationService.EntryKind.HOME);

        expected.put(1164,TeleportNavigationService.EntryKind.MONEY);
        expected.put(13035,TeleportNavigationService.EntryKind.MONEY);
        expected.put(30064,TeleportNavigationService.EntryKind.MONEY);

        expected.put(1167,TeleportNavigationService.EntryKind.TRAINING);
        expected.put(13045,TeleportNavigationService.EntryKind.TRAINING);
        expected.put(30075,TeleportNavigationService.EntryKind.TRAINING);

        expected.put(1170,TeleportNavigationService.EntryKind.BOSS);
        expected.put(13053,TeleportNavigationService.EntryKind.BOSS);
        expected.put(30083,TeleportNavigationService.EntryKind.BOSS);

        expected.put(1174,TeleportNavigationService.EntryKind.PK);
        expected.put(13061,TeleportNavigationService.EntryKind.PK);
        expected.put(30106,TeleportNavigationService.EntryKind.PK);

        expected.put(1540,TeleportNavigationService.EntryKind.MINIGAME);
        expected.put(13069,TeleportNavigationService.EntryKind.MINIGAME);
        expected.put(30114,TeleportNavigationService.EntryKind.MINIGAME);

        expected.put(1541,TeleportNavigationService.EntryKind.HOUSE);
        expected.put(13079,TeleportNavigationService.EntryKind.HOUSE);
        expected.put(30138,TeleportNavigationService.EntryKind.HOUSE);

        expected.put(7455,TeleportNavigationService.EntryKind.BOUNTY);
        expected.put(13095,TeleportNavigationService.EntryKind.BOUNTY);
        expected.put(30162,TeleportNavigationService.EntryKind.BOUNTY);

        require(
            expected.size()==23&&
            TeleportNavigationWidgetAdapter.provenAliasCount()==23,
            "exact alias count"
        );

        for(Map.Entry<Integer,TeleportNavigationService.EntryKind> entry:
                expected.entrySet())
            require(
                TeleportNavigationWidgetAdapter.resolve(
                    entry.getKey()
                )==entry.getValue(),
                "widget alias "+
                entry.getKey()+
                " -> "+
                entry.getValue()
            );

        require(
            TeleportNavigationWidgetAdapter.WIDGET_ACTION_OPCODE==185,
            "exact widget-action opcode"
        );
        require(
            "EXACT_CURRENT_CLIENT".equals(
                TeleportNavigationWidgetAdapter.TRANSPORT_AUTHORITY
            ),
            "transport authority"
        );

        /*
         * Current exact evidence explicitly says no third Lunar Home alias is
         * proven. 30000 is a separate legacy/direct-magic Home widget in the
         * LocalLab codebase and must not be promoted into this recovered atlas.
         */
        require(
            TeleportNavigationWidgetAdapter.resolve(30000)==null,
            "unproven Lunar Home alias invented"
        );

        require(
            TeleportNavigationWidgetAdapter.resolve(5001)==null,
            "unrelated widget classified as teleport navigation"
        );

        expect(
            IllegalArgumentException.class,
            ()->TeleportNavigationWidgetAdapter.resolve(-1),
            "negative widget"
        );
        expect(
            IllegalArgumentException.class,
            ()->TeleportNavigationWidgetAdapter.resolve(65536),
            "widget above unsigned-short range"
        );

        authorityBoundary();

        System.out.println(
            "TELEPORT_NAVIGATION_WIDGET_ADAPTER_PASS "+
            "exactAliases=23 "+
            "homeAliases=2 "+
            "crossBookAliases=21 "+
            "c2s185=true "+
            "lunarHomeInvented=false "+
            "bountyClientLockOwned=false "+
            "destinationPolicyOwned=false "+
            "coordinatesOwned=false"
        );
    }

    private static void authorityBoundary(){
        for(Field field:
                TeleportNavigationWidgetAdapter.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("coordinate")||
               name.contains("region")||
               name.contains("cooldown")||
               name.contains("cost")||
               name.contains("teleblock")||
               name.contains("wilderness")||
               name.contains("unlock")||
               name.contains("magicrequirement")||
               name.contains("destinationlist"))
                throw new AssertionError(
                    "unowned teleport policy field "+
                    field.getName()
                );
        }

        for(Method method:
                TeleportNavigationWidgetAdapter.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("execute")||
               name.contains("teleportto")||
               name.contains("eligible")||
               name.contains("unlock")||
               name.contains("cooldown"))
                throw new AssertionError(
                    "unowned teleport policy method "+
                    method.getName()
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
                label+" wrong failure "+failure,
                failure
            );
        }

        throw new AssertionError(label+" did not fail");
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private TeleportNavigationWidgetAdapterTest(){}
}
