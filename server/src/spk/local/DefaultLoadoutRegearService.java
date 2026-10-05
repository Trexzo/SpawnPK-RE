package spk.local;

import java.util.Locale;
import java.util.Objects;

/**
 * Applies the currently selected world-owned default loadout for one live
 * player. This owns semantic regear composition only; packet publication and
 * automatic respawn policy remain outside.
 */
final class DefaultLoadoutRegearService {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_DEFAULT_REGEAR_V1";

    static final class Result {
        final EntityId playerId;
        final long playerGeneration;
        final String ownerRef;
        final PlayerLoadoutId loadoutId;
        final PlayerLoadoutVersion version;
        final long selectionRevision;
        final PlayerLoadoutApplyService.Result apply;
        final String authority;

        private Result(
            WorldPlayer player,
            String ownerRef,
            DefaultLoadoutService.Snapshot selected,
            PlayerLoadoutApplyService.Result apply
        ){
            this.playerId=player.id();
            this.playerGeneration=
                player.generation();
            this.ownerRef=ownerRef;
            this.loadoutId=
                selected.defaultLoadoutId;
            this.version=
                selected.currentLoadout.version;
            this.selectionRevision=
                selected.selectionRevision;
            this.apply=
                Objects.requireNonNull(
                    apply,
                    "apply"
                );
            this.authority=AUTHORITY;
        }
    }

    private final World world;
    private final PlayerLoadoutSemanticResolver resolver;

    DefaultLoadoutRegearService(
        World world
    ){
        this(
            world,
            new CanonicalPlayerLoadoutSemanticResolver()
        );
    }

    DefaultLoadoutRegearService(
        World world,
        PlayerLoadoutSemanticResolver resolver
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
        this.resolver=
            Objects.requireNonNull(
                resolver,
                "resolver"
            );
    }

    Result regear(
        WorldPlayer player,
        long expectedGeneration
    ){
        WorldPlayer checked=
            Objects.requireNonNull(
                player,
                "player"
            );

        if(!world.players().owns(
                checked,
                expectedGeneration))
            throw new IllegalStateException(
                "regear player ownership changed id="+
                checked.id()+
                " expectedGeneration="+
                expectedGeneration+
                " actualGeneration="+
                checked.generation()
            );

        String ownerRef=
            ownerRef(
                checked
            );

        DefaultLoadoutService.Snapshot selected=
            world.defaultLoadouts()
                .snapshot(
                    ownerRef
                );

        if(!selected.hasDefault()||
           selected.currentLoadout==null)
            throw new IllegalStateException(
                "no default loadout selected owner="+
                ownerRef
            );

        if(!selected.currentLoadout
                .ownerRef.equals(
                    ownerRef))
            throw new IllegalStateException(
                "default loadout owner mismatch expected="+
                ownerRef+
                " actual="+
                selected.currentLoadout.ownerRef
            );

        PlayerLoadoutApplyService applyService=
            new PlayerLoadoutApplyService(
                checked,
                world.loadouts(),
                resolver
            );

        LoadoutService.LoadoutApplyPlan plan=
            world.loadouts()
                .planApply(
                    ownerRef,
                    selected.defaultLoadoutId,
                    applyService::validate
                );

        /*
         * The default may follow a newer loadout revision between snapshot and
         * plan construction. Require this invocation to apply the exact
         * revision it observed rather than silently switching revisions.
         */
        if(plan.loadout!=
                selected.currentLoadout||
           !plan.loadout.version.equals(
                selected.currentLoadout.version))
            throw new IllegalStateException(
                "default loadout revision changed before regear owner="+
                ownerRef+
                " selected="+
                selected.currentLoadout.version+
                " planned="+
                plan.loadout.version
            );

        if(!world.players().owns(
                checked,
                expectedGeneration))
            throw new IllegalStateException(
                "regear player ownership changed before apply id="+
                checked.id()
            );

        PlayerLoadoutApplyService.Result applied=
            applyService.apply(
                plan
            );

        return new Result(
            checked,
            ownerRef,
            selected,
            applied
        );
    }

    static String ownerRef(
        WorldPlayer player
    ){
        WorldPlayer checked=
            Objects.requireNonNull(
                player,
                "player"
            );
        String username=
            checked.username();

        if(username==null||
           username.trim().isEmpty())
            throw new IllegalStateException(
                "regear requires registered username id="+
                checked.id()
            );

        return "player:"+
            username.trim()
                .toLowerCase(
                    Locale.ROOT
                );
    }

    String resolverAuthority(){
        return resolver.authority();
    }
}
