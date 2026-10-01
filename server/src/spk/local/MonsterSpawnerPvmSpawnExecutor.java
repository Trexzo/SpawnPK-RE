package spk.local;

import java.util.Objects;

/**
 * Policy-neutral bridge from an activated Monster Spawner service session to
 * one canonical PvM spawn.
 *
 * Placement, recipient and scheduling remain caller-owned. The exact session
 * snapshot observed by policy is revalidated atomically at canonical spawn.
 */
final class MonsterSpawnerPvmSpawnExecutor {
    enum Status {
        NOT_READY,
        STALE_SESSION,
        SPAWNED
    }

    static final class Context {
        final String ownerRef;
        final MonsterSpawnerService.SessionSnapshot session;

        private Context(
            String ownerRef,
            MonsterSpawnerService.SessionSnapshot session
        ){
            this.ownerRef=ownerRef;
            this.session=session;
        }
    }

    static final class Request {
        final String recipientRef;
        final Tile tile;

        Request(
            String recipientRef,
            Tile tile
        ){
            this.recipientRef=
                PartyService.requireRef(
                    recipientRef
                );
            this.tile=
                Objects.requireNonNull(
                    tile,
                    "tile"
                );
        }
    }

    interface RequestResolver {
        Request resolve(
            Context context
        ) throws Exception;

        String authority();
    }

    static final class Result {
        final Status status;
        final MonsterSpawnerService.SessionSnapshot session;
        final MonsterSpawnerPvmRuntime.SpawnResult spawn;

        private Result(
            Status status,
            MonsterSpawnerService.SessionSnapshot session,
            MonsterSpawnerPvmRuntime.SpawnResult spawn
        ){
            this.status=
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.session=session;
            this.spawn=spawn;
        }
    }

    private final MonsterSpawnerService spawner;
    private final MonsterSpawnerPvmRuntime runtime;
    private final RequestResolver requestResolver;
    private final String requestAuthority;

    MonsterSpawnerPvmSpawnExecutor(
        World world,
        MonsterSpawnerService spawner,
        MonsterSpawnerPvmRuntime runtime,
        RequestResolver requestResolver
    ){
        World checkedWorld=
            Objects.requireNonNull(
                world,
                "world"
            );

        this.spawner=
            Objects.requireNonNull(
                spawner,
                "spawner"
            );
        this.runtime=
            Objects.requireNonNull(
                runtime,
                "runtime"
            );
        this.requestResolver=
            Objects.requireNonNull(
                requestResolver,
                "requestResolver"
            );
        this.requestAuthority=
            requireServerAuthority(
                this.requestResolver.authority()
            );

        if(!this.runtime.isBoundTo(
                checkedWorld,
                this.spawner
            ))
            throw new IllegalArgumentException(
                "Monster Spawner spawn executor requires one exact World/service/runtime graph"
            );
    }

    Result execute(
        String ownerRef
    )throws Exception{
        String owner=
            PartyService.requireRef(
                ownerRef
            );

        MonsterSpawnerService.SessionSnapshot before=
            spawner.getSession(
                owner
            );

        if(before==null||
           !before.active||
           before.remainingSpawnBudget<=0||
           !before.hasSelection())
            return new Result(
                Status.NOT_READY,
                before,
                null
            );

        String currentAuthority=
            requireServerAuthority(
                requestResolver.authority()
            );

        if(!requestAuthority.equals(
                currentAuthority))
            throw new IllegalStateException(
                "Monster Spawner spawn request authority changed expected="+
                requestAuthority+
                " actual="+currentAuthority
            );

        Request request=
            Objects.requireNonNull(
                requestResolver.resolve(
                    new Context(
                        owner,
                        before
                    )
                ),
                "spawn request"
            );

        String postResolveAuthority=
            requireServerAuthority(
                requestResolver.authority()
            );

        if(!requestAuthority.equals(
                postResolveAuthority))
            throw new IllegalStateException(
                "Monster Spawner spawn request authority changed during resolve expected="+
                requestAuthority+
                " actual="+postResolveAuthority
            );

        try{
            MonsterSpawnerPvmRuntime.SpawnResult
                spawned=
                    runtime.spawnAndBindIfCurrent(
                        owner,
                        request.recipientRef,
                        before,
                        request.tile.x,
                        request.tile.y,
                        request.tile.plane
                    );

            return new Result(
                Status.SPAWNED,
                spawned.spawn.combat.spawn.session,
                spawned
            );
        }catch(MonsterSpawnerService.StaleSessionException stale){
            return new Result(
                Status.STALE_SESSION,
                spawner.getSession(
                    owner
                ),
                null
            );
        }
    }

    String requestAuthority(){
        return requestAuthority;
    }

    private static String requireServerAuthority(
        String value
    ){
        String clean=
            MatchRules.requireText(
                value,
                "requestAuthority"
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean)||
           "UNCONFIGURED".equals(clean))
            throw new IllegalArgumentException(
                "Monster Spawner spawn request authority cannot own server policy actual="+
                clean
            );

        return clean;
    }
}
