package spk.local;

import java.util.*;

/**
 * In-game loadout-editor application boundary over the versioned LoadoutService.
 *
 * Exact-current client evidence proves that inventory + equipment are one
 * logical saved snapshot even though compatibility transport splits them.
 * Transport staging/parsing is deliberately outside this gameplay service.
 */
final class LoadoutEditorService {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static final class SaveSnapshot {
        final PlayerLoadout loadout;
        final PlayerLoadoutVersion previousVersion;
        final String presentationAuthority;

        SaveSnapshot(
            PlayerLoadout loadout,
            PlayerLoadoutVersion previousVersion
        ){
            this.loadout=
                Objects.requireNonNull(
                    loadout,
                    "loadout"
                );
            this.previousVersion=
                Objects.requireNonNull(
                    previousVersion,
                    "previousVersion"
                );
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }
    }

    private final LoadoutService loadouts;

    LoadoutEditorService(
        LoadoutService loadouts
    ){
        this.loadouts=
            Objects.requireNonNull(
                loadouts,
                "loadouts"
            );
    }

    /**
     * Atomically replaces the editor-owned inventory + equipment sections of
     * one existing semantic loadout revision.
     *
     * The caller must already have normalized/paired any split compatibility
     * transport into these two complete semantic collections.
     */
    synchronized SaveSnapshot saveSnapshot(
        String ownerRef,
        PlayerLoadoutId loadoutId,
        PlayerLoadoutVersion expectedCurrentVersion,
        Collection<PlayerLoadout.InventoryEntry>
            inventory,
        Collection<PlayerLoadout.EquipmentEntry>
            equipment,
        String gameplaySourceAuthority
    ){
        String owner=
            PlayerLoadout.requireText(
                ownerRef,
                "ownerRef"
            );
        PlayerLoadoutId id=
            Objects.requireNonNull(
                loadoutId,
                "loadoutId"
            );
        PlayerLoadoutVersion expected=
            Objects.requireNonNull(
                expectedCurrentVersion,
                "expectedCurrentVersion"
            );
        String authority=
            gameplayAuthority(
                gameplaySourceAuthority
            );

        Objects.requireNonNull(
            inventory,
            "inventory"
        );
        Objects.requireNonNull(
            equipment,
            "equipment"
        );

        LoadoutService.Snapshot current=
            loadouts.get(owner,id);

        if(current==null)
            throw new IllegalArgumentException(
                "unknown loadout owner="+
                owner+
                " id="+id
            );

        if(!current.loadout.version.equals(
                expected))
            throw new IllegalStateException(
                "loadout editor version conflict expected="+
                expected+
                " actual="+
                current.loadout.version
            );

        /*
         * Validate and materialize both semantic halves before touching the
         * underlying registry. PlayerLoadout performs duplicate/shape checks.
         */
        PlayerLoadout replacement=
            new PlayerLoadout(
                current.loadout.id,
                expected.next(),
                current.loadout.ownerRef,
                new ArrayList<>(inventory),
                new ArrayList<>(equipment),
                current.loadout.skillProfile,
                current.loadout.petSelection,
                authority
            );

        LoadoutService.Snapshot saved=
            loadouts.replace(
                replacement,
                expected
            );

        return new SaveSnapshot(
            saved.loadout,
            expected
        );
    }

    synchronized SaveSnapshot get(
        String ownerRef,
        PlayerLoadoutId loadoutId
    ){
        LoadoutService.Snapshot current=
            loadouts.get(
                PlayerLoadout.requireText(
                    ownerRef,
                    "ownerRef"
                ),
                Objects.requireNonNull(
                    loadoutId,
                    "loadoutId"
                )
            );

        return current==null
            ?null
            :new SaveSnapshot(
                current.loadout,
                current.loadout.version
            );
    }

    private static String gameplayAuthority(
        String value
    ){
        String authority=
            PlayerLoadout.requireText(
                value,
                "gameplaySourceAuthority"
            );

        if(PRESENTATION_AUTHORITY
                .equalsIgnoreCase(
                    authority)||
           "UNKNOWN_SERVER_AUTHORITY"
                .equalsIgnoreCase(
                    authority))
            throw new IllegalArgumentException(
                "gameplay source authority cannot be "+
                authority
            );

        return authority;
    }
}
