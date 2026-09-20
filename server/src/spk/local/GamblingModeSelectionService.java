package spk.local;

import java.util.Objects;

/**
 * Protocol-independent current mode selection for the exact-current native
 * gambling catalogue.
 *
 * Selection does not imply wager ownership, host authorization, RNG, outcome
 * rules, settlement or persistence.
 */
final class GamblingModeSelectionService {
    static final class Snapshot {
        final int selectedModeId;
        final String selectedModeName;
        final String runtimeAuthority;
        final String catalogAuthority;

        Snapshot(
            GamblingModeCatalog.Definition definition,
            String runtimeAuthority
        ){
            this.selectedModeId=
                definition.clientIndex;
            this.selectedModeName=
                definition.name;
            this.runtimeAuthority=
                runtimeAuthority;
            this.catalogAuthority=
                definition.authority;
        }

        @Override public String toString(){
            return "GamblingModeSelection{"+
                "selectedModeId="+
                    selectedModeId+
                ",selectedModeName="+
                    selectedModeName+
                ",runtimeAuthority="+
                    runtimeAuthority+
                ",catalogAuthority="+
                    catalogAuthority+
                "}";
        }
    }

    private final String runtimeAuthority;
    private GamblingModeCatalog.Definition selected;

    GamblingModeSelectionService(
        int initialModeId,
        String runtimeAuthority
    ){
        this.runtimeAuthority=
            requireAuthority(
                runtimeAuthority
            );

        this.selected=
            GamblingModeCatalog
                .requireByClientIndex(
                    initialModeId
                );
    }

    synchronized Snapshot snapshot(){
        return new Snapshot(
            selected,
            runtimeAuthority
        );
    }

    /**
     * @return true when selection changed; false when already selected.
     */
    synchronized boolean select(
        int modeId
    ){
        GamblingModeCatalog.Definition next=
            GamblingModeCatalog
                .requireByClientIndex(
                    modeId
                );

        if(next.clientIndex==
                selected.clientIndex)
            return false;

        selected=next;
        return true;
    }

    private static String requireAuthority(
        String authority
    ){
        Objects.requireNonNull(
            authority,
            "runtimeAuthority"
        );

        String normalized=
            authority.trim();

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "blank runtimeAuthority"
            );

        return normalized;
    }
}
