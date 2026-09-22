package spk.local;

import java.util.*;

/**
 * Runtime semantic default-loadout selection over LoadoutService.
 *
 * Exact-current client evidence proves a Set as default intent only.
 * Persistence, auto-apply and reset behavior remain outside this service.
 */
final class DefaultLoadoutService {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static final class Snapshot {
        final String ownerRef;
        final PlayerLoadoutId defaultLoadoutId;
        final PlayerLoadout currentLoadout;
        final long selectionRevision;
        final String policyAuthority;
        final String presentationAuthority;

        Snapshot(
            String ownerRef,
            Selection selection,
            PlayerLoadout currentLoadout,
            String policyAuthority
        ){
            this.ownerRef=ownerRef;
            this.defaultLoadoutId=
                selection==null
                    ?null
                    :selection.loadoutId;
            this.currentLoadout=
                currentLoadout;
            this.selectionRevision=
                selection==null
                    ?0L
                    :selection.revision;
            this.policyAuthority=
                policyAuthority;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        boolean hasDefault(){
            return defaultLoadoutId!=null;
        }
    }

    private static final class Selection {
        final PlayerLoadoutId loadoutId;
        final long revision;

        Selection(
            PlayerLoadoutId loadoutId,
            long revision
        ){
            this.loadoutId=
                Objects.requireNonNull(
                    loadoutId,
                    "loadoutId"
                );

            if(revision<=0L)
                throw new IllegalArgumentException(
                    "selection revision="+
                    revision
                );

            this.revision=revision;
        }
    }

    private final LoadoutService loadouts;
    private final String policyAuthority;

    private final LinkedHashMap<String,Selection>
        defaults=
            new LinkedHashMap<>();

    DefaultLoadoutService(
        LoadoutService loadouts,
        String policyAuthority
    ){
        this.loadouts=
            Objects.requireNonNull(
                loadouts,
                "loadouts"
            );
        this.policyAuthority=
            localPolicyAuthority(
                policyAuthority
            );
    }

    synchronized Snapshot setDefault(
        String ownerRef,
        PlayerLoadoutId loadoutId
    ){
        String owner=
            normalizeOwner(
                ownerRef
            );
        PlayerLoadoutId id=
            Objects.requireNonNull(
                loadoutId,
                "loadoutId"
            );

        LoadoutService.Snapshot target=
            loadouts.get(owner,id);

        if(target==null)
            throw new IllegalArgumentException(
                "unknown default loadout owner="+
                owner+
                " id="+id
            );

        Selection current=
            defaults.get(owner);

        if(current!=null&&
           current.loadoutId.equals(id))
            return snapshotOf(
                owner,
                current
            );

        long nextRevision=
            nextRevision(current);

        Selection replacement=
            new Selection(
                id,
                nextRevision
            );

        defaults.put(
            owner,
            replacement
        );

        return new Snapshot(
            owner,
            replacement,
            target.loadout,
            policyAuthority
        );
    }

    synchronized Snapshot snapshot(
        String ownerRef
    ){
        String owner=
            normalizeOwner(
                ownerRef
            );

        return snapshotOf(
            owner,
            defaults.get(owner)
        );
    }

    synchronized int ownerCount(){
        return defaults.size();
    }

    private Snapshot snapshotOf(
        String owner,
        Selection selection
    ){
        if(selection==null)
            return new Snapshot(
                owner,
                null,
                null,
                policyAuthority
            );

        LoadoutService.Snapshot current=
            loadouts.get(
                owner,
                selection.loadoutId
            );

        if(current==null)
            throw new IllegalStateException(
                "default loadout disappeared owner="+
                owner+
                " id="+
                selection.loadoutId
            );

        return new Snapshot(
            owner,
            selection,
            current.loadout,
            policyAuthority
        );
    }

    private static long nextRevision(
        Selection current
    ){
        long value=
            current==null
                ?0L
                :current.revision;

        try{
            return Math.addExact(
                value,
                1L
            );
        }catch(ArithmeticException error){
            throw new IllegalStateException(
                "default loadout selection revision overflow",
                error
            );
        }
    }

    private static String normalizeOwner(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "ownerRef"
            );

        String normalized=
            value.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "ownerRef blank"
            );

        return normalized;
    }

    private static String localPolicyAuthority(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "policyAuthority"
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "policyAuthority blank"
            );

        if(!clean.toUpperCase(
                Locale.ROOT
            ).startsWith(
                "LOCAL_LAB_POLICY_"))
            throw new IllegalArgumentException(
                "default loadout requires explicit LOCAL_LAB_POLICY_* authority actual="+
                clean
            );

        return clean;
    }
}
