package spk.local;

import java.util.Objects;

/**
 * Semantic current-selection state for the exact-current Blood Fountain perk
 * catalogue.
 *
 * This is selection state only. It does not imply ownership, unlock status,
 * affordability, prerequisites or active gameplay effects.
 */
final class BloodFountainPerkSelectionService {
    static final class Snapshot {
        final int selectedPerkId;
        final String selectedPerkName;
        final String runtimeAuthority;
        final String catalogAuthority;

        Snapshot(
            BloodFountainSelectablePerkCatalog.Definition definition,
            String runtimeAuthority
        ){
            this.selectedPerkId=definition.perkId;
            this.selectedPerkName=definition.name;
            this.runtimeAuthority=runtimeAuthority;
            this.catalogAuthority=definition.authority;
        }

        @Override public String toString(){
            return "BloodFountainPerkSelection{"+
                "selectedPerkId="+selectedPerkId+
                ",selectedPerkName="+selectedPerkName+
                ",runtimeAuthority="+runtimeAuthority+
                ",catalogAuthority="+catalogAuthority+
                "}";
        }
    }

    private final String runtimeAuthority;
    private BloodFountainSelectablePerkCatalog.Definition selected;

    BloodFountainPerkSelectionService(
        int initialPerkId,
        String runtimeAuthority
    ){
        this.runtimeAuthority=
            requireAuthority(runtimeAuthority);

        this.selected=
            BloodFountainSelectablePerkCatalog
                .requireById(initialPerkId);
    }

    synchronized Snapshot snapshot(){
        return new Snapshot(
            selected,
            runtimeAuthority
        );
    }

    /**
     * Select a semantic perk id from the exact-current catalogue.
     *
     * Per exact-current v308 authority, id 0 is Blood vengeance I. It is not a
     * NONE/reset sentinel.
     *
     * @return true when the selection changed, false when already selected.
     */
    synchronized boolean select(
        int perkId
    ){
        BloodFountainSelectablePerkCatalog.Definition next=
            BloodFountainSelectablePerkCatalog
                .requireById(perkId);

        if(next.perkId==selected.perkId)
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
