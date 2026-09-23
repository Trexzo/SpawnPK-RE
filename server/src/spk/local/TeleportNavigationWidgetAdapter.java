package spk.local;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Exact-v308 input adapter for the first-class spellbook teleport entries.
 *
 * Raw widget aliases stay here. Runtime destination selection, coordinates,
 * eligibility, costs, cooldowns and Bounty unlock policy remain server-owned
 * and are deliberately absent from this adapter.
 */
final class TeleportNavigationWidgetAdapter {
    static final int WIDGET_ACTION_OPCODE=185;
    static final String TRANSPORT_AUTHORITY="EXACT_CURRENT_CLIENT";
    static final int PROVEN_ALIAS_COUNT=23;

    private static final Map<Integer,TeleportNavigationService.EntryKind>
        BY_WIDGET=build();

    static TeleportNavigationService.EntryKind resolve(int widgetId){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );

        return BY_WIDGET.get(widgetId);
    }

    static int provenAliasCount(){
        return BY_WIDGET.size();
    }

    private static Map<Integer,TeleportNavigationService.EntryKind> build(){
        LinkedHashMap<Integer,TeleportNavigationService.EntryKind> out=
            new LinkedHashMap<>();

        /*
         * Home is intentionally asymmetric. Exact v308 proves classic 1195 and
         * Ancient 12856. A third Lunar Home alias is not proven and must not be
         * invented merely to force a three-book table.
         */
        add(out,1195,TeleportNavigationService.EntryKind.HOME);
        add(out,12856,TeleportNavigationService.EntryKind.HOME);

        add(out,1164,TeleportNavigationService.EntryKind.MONEY);
        add(out,13035,TeleportNavigationService.EntryKind.MONEY);
        add(out,30064,TeleportNavigationService.EntryKind.MONEY);

        add(out,1167,TeleportNavigationService.EntryKind.TRAINING);
        add(out,13045,TeleportNavigationService.EntryKind.TRAINING);
        add(out,30075,TeleportNavigationService.EntryKind.TRAINING);

        add(out,1170,TeleportNavigationService.EntryKind.BOSS);
        add(out,13053,TeleportNavigationService.EntryKind.BOSS);
        add(out,30083,TeleportNavigationService.EntryKind.BOSS);

        add(out,1174,TeleportNavigationService.EntryKind.PK);
        add(out,13061,TeleportNavigationService.EntryKind.PK);
        add(out,30106,TeleportNavigationService.EntryKind.PK);

        add(out,1540,TeleportNavigationService.EntryKind.MINIGAME);
        add(out,13069,TeleportNavigationService.EntryKind.MINIGAME);
        add(out,30114,TeleportNavigationService.EntryKind.MINIGAME);

        add(out,1541,TeleportNavigationService.EntryKind.HOUSE);
        add(out,13079,TeleportNavigationService.EntryKind.HOUSE);
        add(out,30138,TeleportNavigationService.EntryKind.HOUSE);

        add(out,7455,TeleportNavigationService.EntryKind.BOUNTY);
        add(out,13095,TeleportNavigationService.EntryKind.BOUNTY);
        add(out,30162,TeleportNavigationService.EntryKind.BOUNTY);

        if(out.size()!=PROVEN_ALIAS_COUNT)
            throw new IllegalStateException(
                "teleport alias count expected="+
                PROVEN_ALIAS_COUNT+
                " actual="+out.size()
            );

        return Collections.unmodifiableMap(out);
    }

    private static void add(
        Map<Integer,TeleportNavigationService.EntryKind> out,
        int widgetId,
        TeleportNavigationService.EntryKind kind
    ){
        Objects.requireNonNull(kind,"kind");

        if(out.put(widgetId,kind)!=null)
            throw new IllegalStateException(
                "duplicate teleport widget alias "+
                widgetId
            );
    }

    private TeleportNavigationWidgetAdapter(){}
}
