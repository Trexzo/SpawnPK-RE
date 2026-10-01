package spk.local;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Per-session adapter from World ground-item presentation events to exact scene
 * packets. Canonical world state remains in GroundItemRegistry.
 */
final class LocalGroundItemPresentationRelay {
    private final World world;
    private final WorldPlayer viewer;
    private final MovementState movement;

    LocalGroundItemPresentationRelay(
        World world,
        WorldPlayer viewer,
        MovementState movement
    ){
        this.world=Objects.requireNonNull(
            world,
            "world"
        );
        this.viewer=Objects.requireNonNull(
            viewer,
            "viewer"
        );
        this.movement=Objects.requireNonNull(
            movement,
            "movement"
        );
    }

    int publishPending(
        long now,
        SceneUpdatePublisher publisher
    )throws IOException{
        Objects.requireNonNull(
            publisher,
            "publisher"
        );

        long generation=
            viewer.generation();

        if(!world.players().owns(
                viewer,
                generation
            ))
            return 0;

        List<WorldGroundItemPresentationEvents.Event>
            pending=
                world.groundItemPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generation,
                        now
                    );

        int published=0;

        for(WorldGroundItemPresentationEvents.Event event:
                pending){
            if(event.tile.plane!=
                    movement.plane()||
               !movement.insideCurrentLoadedRegion(
                    event.tile.x,
                    event.tile.y
                )){
                world.groundItemPresentationEvents()
                    .markDelivered(
                        event.sequence,
                        viewer.id(),
                        generation,
                        now
                    );
                continue;
            }

            if(event.kind==
                    WorldGroundItemPresentationEvents.Kind.SPAWN)
                publisher.groundSpawn(
                    event.itemId,
                    event.newAmount,
                    event.tile
                );
            else if(event.kind==
                    WorldGroundItemPresentationEvents.Kind.AMOUNT)
                publisher.groundAmount(
                    event.itemId,
                    event.oldAmount,
                    event.newAmount,
                    event.tile
                );
            else
                throw new IllegalStateException(
                    "unsupported ground presentation kind "+
                    event.kind
                );

            if(!world.groundItemPresentationEvents()
                    .markDelivered(
                        event.sequence,
                        viewer.id(),
                        generation,
                        now
                    ))
                throw new IllegalStateException(
                    "ground presentation delivery identity changed sequence="+
                    event.sequence
                );

            published++;
        }

        return published;
    }

    int publishPendingIfSceneReady(
        long now,
        SceneUpdatePublisher publisher,
        boolean regionLoadPending
    )throws IOException{
        if(regionLoadPending)
            return 0;

        return publishPending(
            now,
            publisher
        );
    }

    int consumeSnapshotCoveredAfterSnapshot(
        long now,
        boolean groundSnapshotPublished
    ){
        if(!groundSnapshotPublished)
            return 0;

        return consumeSnapshotCovered(
            now
        );
    }

    int consumeSnapshotCovered(
        long now
    ){
        long generation=
            viewer.generation();

        if(!world.players().owns(
                viewer,
                generation
            ))
            return 0;

        List<WorldGroundItemPresentationEvents.Event>
            pending=
                world.groundItemPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generation,
                        now
                    );

        int consumed=0;

        for(WorldGroundItemPresentationEvents.Event event:
                pending)
            if(event.tile.plane==
                    movement.plane()&&
               movement.insideCurrentLoadedRegion(
                    event.tile.x,
                    event.tile.y
                )&&
               world.groundItemPresentationEvents()
                    .markDelivered(
                        event.sequence,
                        viewer.id(),
                        generation,
                        now
                    ))
                consumed++;

        return consumed;
    }
}
