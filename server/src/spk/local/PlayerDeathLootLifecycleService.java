package spk.local;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class PlayerDeathLootLifecycleService
    implements AutoCloseable {

    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_DEATH_LOOT_LIFECYCLE_V1";
    static final long PRIVATE_VISIBILITY_MS=60_000L;
    static final long PUBLIC_VISIBILITY_MS=120_000L;

    static final class TickResult {
        final int publicized;
        final int expired;
        final int cancelledMissing;
        final int stalePreimage;

        TickResult(
            int publicized,
            int expired,
            int cancelledMissing,
            int stalePreimage
        ){
            this.publicized=publicized;
            this.expired=expired;
            this.cancelledMissing=cancelledMissing;
            this.stalePreimage=stalePreimage;
        }
    }

    private static final class Entry {
        final long groundItemId;
        final int itemId;
        final Tile tile;
        final long spawnedTick;
        final boolean devOwned;
        final String originalOwner;
        final long publicAt;
        final long expiresAt;
        int expectedAmount;
        String expectedOwner;
        boolean publicized;

        Entry(
            GroundItem item,
            long now
        ){
            groundItemId=item.id;
            itemId=item.itemId;
            tile=item.tile;
            spawnedTick=item.spawnedTick;
            devOwned=item.devOwned;
            originalOwner=item.owner;
            expectedAmount=item.amount;
            expectedOwner=item.owner;
            publicized=item.owner==null;
            publicAt=publicized
                ?now
                :Math.addExact(
                    now,
                    PRIVATE_VISIBILITY_MS
                );
            expiresAt=Math.addExact(
                publicAt,
                PUBLIC_VISIBILITY_MS
            );
        }

        boolean matches(
            GroundItem item
        ){
            return item!=null&&
                item.id==groundItemId&&
                item.itemId==itemId&&
                item.amount==expectedAmount&&
                item.tile.equals(tile)&&
                Objects.equals(
                    item.owner,
                    expectedOwner
                )&&
                item.spawnedTick==spawnedTick&&
                item.devOwned==devOwned;
        }
    }

    private final World world;
    private final GroundItemRegistry groundItems;
    private final LinkedHashMap<Long,Entry> entries=
        new LinkedHashMap<>();
    private boolean closed;

    PlayerDeathLootLifecycleService(
        World world
    ){
        this.world=Objects.requireNonNull(
            world,
            "world"
        );
        this.groundItems=world.groundItems();
    }

    synchronized int registerSettlement(
        PlayerDeathGroundSettlementService.Settlement settlement,
        long now
    ){
        if(closed||settlement==null)
            return 0;

        int registered=0;

        for(PlayerDeathGroundSettlementService.GroundDrop drop:
                settlement.drops)
            if(registerGroundItem(
                    drop.groundItemId,
                    now
                ))
                registered++;

        return registered;
    }

    synchronized boolean registerGroundItem(
        long groundItemId,
        long now
    ){
        if(closed)
            return false;

        GroundItem item=
            groundItems.byId(
                groundItemId
            );

        if(item==null||
           item.devOwned)
            return false;

        entries.put(
            groundItemId,
            new Entry(
                item,
                now
            )
        );
        return true;
    }

    synchronized TickResult tick(
        long now
    ){
        if(closed)
            return new TickResult(
                0,0,0,0
            );

        int publicized=0;
        int expired=0;
        int cancelledMissing=0;
        int stalePreimage=0;

        for(Iterator<Map.Entry<Long,Entry>> iterator=
                entries.entrySet()
                    .iterator();
                iterator.hasNext();){
            Entry entry=
                iterator.next()
                    .getValue();

            GroundItem current=
                groundItems.byId(
                    entry.groundItemId
                );

            if(current==null){
                iterator.remove();
                cancelledMissing++;
                continue;
            }

            if(!entry.matches(current)){
                iterator.remove();
                stalePreimage++;
                continue;
            }

            if(!entry.publicized&&
               now>=entry.publicAt){
                GroundItemRegistry.PreparedOwnerTransition
                    transition=
                        groundItems.prepareOwnerTransition(
                            entry.groundItemId,
                            entry.expectedOwner,
                            null
                        );

                if(transition==null){
                    iterator.remove();
                    stalePreimage++;
                    continue;
                }

                GroundItemRegistry.OwnerTransitionCommit
                    transitionCommit=
                        groundItems
                            .commitPreparedOwnerTransition(
                                transition
                            );

                if(transitionCommit==
                        GroundItemRegistry
                            .OwnerTransitionCommit
                            .TARGET_STACK_EXISTS){
                    /*
                     * Never merge quantities across provenance. The existing
                     * public stack may be unrelated NPC/world loot with a
                     * different lifecycle. Keep this exact death-loot stack
                     * private and retry on a later world tick.
                     */
                    if(now<entry.expiresAt)
                        continue;
                }else if(transitionCommit!=
                        GroundItemRegistry
                            .OwnerTransitionCommit
                            .COMMITTED){
                    iterator.remove();
                    stalePreimage++;
                    continue;
                }else{
                    current=
                        groundItems.byId(
                            entry.groundItemId
                        );

                    if(current==null){
                        iterator.remove();
                        cancelledMissing++;
                        continue;
                    }

                    entry.expectedOwner=null;
                    entry.expectedAmount=
                        current.amount;
                    entry.publicized=true;

                    enqueuePublicSpawn(
                        current,
                        entry.originalOwner,
                        now
                    );
                    publicized++;
                }

                current=
                    groundItems.byId(
                        entry.groundItemId
                    );
            }

            if(now<entry.expiresAt)
                continue;

            current=
                groundItems.byId(
                    entry.groundItemId
                );

            if(!entry.matches(current)){
                if(current==null)
                    cancelledMissing++;
                else
                    stalePreimage++;
                iterator.remove();
                continue;
            }

            GroundItem immutableSnapshot=
                new GroundItem(
                    current.id,
                    current.itemId,
                    current.amount,
                    current.tile,
                    current.owner,
                    current.spawnedTick,
                    current.devOwned
                );

            GroundItem publicReplacement=
                current.owner==null
                    ?null
                    :groundItems.findOwned(
                        current.itemId,
                        current.tile.x,
                        current.tile.y,
                        current.tile.plane,
                        null
                    );

            GroundItemRegistry.PreparedRemove removal=
                groundItems.prepareRemove(
                    current.id
                );

            if(removal.expected!=current||
               !groundItems.commitPreparedRemove(
                    removal
               )){
                iterator.remove();
                stalePreimage++;
                continue;
            }

            if(immutableSnapshot.owner==null)
                enqueueRemove(
                    immutableSnapshot,
                    now
                );
            else
                enqueuePrivateRemoveAndRestorePublic(
                    immutableSnapshot,
                    publicReplacement,
                    immutableSnapshot.owner,
                    now
                );

            iterator.remove();
            expired++;
        }

        return new TickResult(
            publicized,
            expired,
            cancelledMissing,
            stalePreimage
        );
    }

    synchronized int trackedCount(){
        return entries.size();
    }

    private void enqueuePublicSpawn(
        GroundItem item,
        String priorOwner,
        long now
    ){
        for(WorldPlayer recipient:
                world.players().snapshot()){
            long generation=
                recipient.generation();

            if(!world.players().owns(
                    recipient,
                    generation))
                continue;

            if(priorOwner!=null&&
               priorOwner.equalsIgnoreCase(
                    recipient.username()
               ))
                continue;

            if(!sceneContains(
                    recipient,
                    item.tile
                ))
                continue;

            world.groundItemPresentationEvents()
                .enqueueSpawnSnapshot(
                    now,
                    item,
                    recipient,
                    generation
                );
        }
    }

    private void enqueuePrivateRemoveAndRestorePublic(
        GroundItem removedPrivate,
        GroundItem publicReplacement,
        String owner,
        long now
    ){
        if(owner==null)
            return;

        WorldPlayer recipient=
            world.players().byName(
                owner
            );

        if(recipient==null)
            return;

        long generation=
            recipient.generation();

        if(!world.players().owns(
                recipient,
                generation)||
           !sceneContains(
                recipient,
                removedPrivate.tile
           ))
            return;

        world.groundItemPresentationEvents()
            .enqueueRemove(
                now,
                removedPrivate,
                recipient,
                generation
            );

        if(publicReplacement!=null&&
           publicReplacement.owner==null&&
           publicReplacement.itemId==
                removedPrivate.itemId&&
           publicReplacement.tile.equals(
                removedPrivate.tile
           )&&
           publicReplacement.devOwned==
                removedPrivate.devOwned)
            world.groundItemPresentationEvents()
                .enqueueSpawnSnapshot(
                    now,
                    publicReplacement,
                    recipient,
                    generation
                );
    }

    private void enqueueRemove(
        GroundItem item,
        long now
    ){
        for(WorldPlayer recipient:
                world.players().snapshot()){
            long generation=
                recipient.generation();

            if(!world.players().owns(
                    recipient,
                    generation))
                continue;

            if(!sceneContains(
                    recipient,
                    item.tile
                ))
                continue;

            if(world.groundItemPresentationEvents()
                    .enqueueRemove(
                        now,
                        item,
                        recipient,
                        generation
                    )){
                GroundItem privateReplacement=
                    world.groundItems()
                        .findOwned(
                            item.itemId,
                            item.tile.x,
                            item.tile.y,
                            item.tile.plane,
                            recipient.username()
                        );

                if(privateReplacement!=null)
                    world.groundItemPresentationEvents()
                        .enqueueSpawnSnapshot(
                            now,
                            privateReplacement,
                            recipient,
                            generation
                        );
            }
        }
    }

    private static boolean sceneContains(
        WorldPlayer recipient,
        Tile tile
    ){
        MovementState movement=
            recipient.movement();

        return movement.plane()==tile.plane&&
            movement.insideCurrentLoadedRegion(
                tile.x,
                tile.y
            );
    }

    @Override public synchronized void close(){
        if(closed)
            return;

        closed=true;
        entries.clear();
    }
}
