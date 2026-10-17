package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

final class GroundItemRegistry {
    static final class AddRequest {
        final int itemId;
        final int amount;
        final Tile tile;
        final String owner;
        final long spawnedTick;
        final boolean devOwned;

        AddRequest(
            int itemId,
            int amount,
            Tile tile,
            String owner,
            long spawnedTick,
            boolean devOwned
        ){
            if(itemId<0)
                throw new IllegalArgumentException(
                    "itemId="+itemId
                );
            if(amount<=0)
                throw new IllegalArgumentException(
                    "amount="+amount
                );
            if(tile==null)
                throw new NullPointerException(
                    "tile"
                );
            this.itemId=itemId;
            this.amount=amount;
            this.tile=tile;
            this.owner=owner;
            this.spawnedTick=spawnedTick;
            this.devOwned=devOwned;
        }
    }

    static final class BatchMutation {
        final long groundItemId;
        final int itemId;
        final Tile tile;
        final String owner;
        final long spawnedTick;
        final boolean devOwned;
        final int addedAmount;
        final int oldAmount;
        final int newAmount;

        BatchMutation(
            GroundItem item,
            int addedAmount,
            int oldAmount
        ){
            GroundItem checked=
                Objects.requireNonNull(
                    item,
                    "item"
                );

            if(addedAmount<=0)
                throw new IllegalArgumentException(
                    "addedAmount="+
                    addedAmount
                );
            if(oldAmount<0||
               oldAmount>=checked.amount)
                throw new IllegalArgumentException(
                    "oldAmount="+
                    oldAmount+
                    " newAmount="+
                    checked.amount
                );

            this.groundItemId=checked.id;
            this.itemId=checked.itemId;
            this.tile=checked.tile;
            this.owner=checked.owner;
            this.spawnedTick=checked.spawnedTick;
            this.devOwned=checked.devOwned;
            this.addedAmount=addedAmount;
            this.oldAmount=oldAmount;
            this.newAmount=checked.amount;
        }

        boolean created(){
            return oldAmount==0;
        }
    }

    private static final class StackKey {
        final int itemId;
        final Tile tile;
        final String owner;
        final boolean devOwned;

        StackKey(
            int itemId,
            Tile tile,
            String owner,
            boolean devOwned
        ){
            this.itemId=itemId;
            this.tile=tile;
            this.owner=owner;
            this.devOwned=devOwned;
        }

        @Override public boolean equals(
            Object other
        ){
            if(this==other)
                return true;
            if(!(other instanceof StackKey))
                return false;

            StackKey key=
                (StackKey)other;

            return itemId==key.itemId&&
                devOwned==key.devOwned&&
                tile.equals(key.tile)&&
                Objects.equals(
                    owner,
                    key.owner
                );
        }

        @Override public int hashCode(){
            return Objects.hash(
                itemId,
                tile,
                owner,
                devOwned
            );
        }
    }

    private final LinkedHashMap<Long,GroundItem> byId=
        new LinkedHashMap<>();
    private final AtomicLong ids=
        new AtomicLong();

    static final class PreparedAdd {
        final GroundItem expectedExisting;
        final int expectedOldAmount;
        final int itemId;
        final int amount;
        final Tile tile;
        final String owner;
        final long tick;
        final boolean devOwned;
        final int newAmount;

        PreparedAdd(
            GroundItem expectedExisting,
            int expectedOldAmount,
            int itemId,
            int amount,
            Tile tile,
            String owner,
            long tick,
            boolean devOwned,
            int newAmount
        ){
            this.expectedExisting=expectedExisting;
            this.expectedOldAmount=expectedOldAmount;
            this.itemId=itemId;
            this.amount=amount;
            this.tile=tile;
            this.owner=owner;
            this.tick=tick;
            this.devOwned=devOwned;
            this.newAmount=newAmount;
        }

        boolean merge(){
            return expectedExisting!=null;
        }
    }

    static final class PreparedRemove {
        final GroundItem expected;

        PreparedRemove(
            GroundItem expected
        ){
            this.expected=expected;
        }
    }

    enum OwnerTransitionCommit {
        COMMITTED,
        TARGET_STACK_EXISTS,
        STALE_PREIMAGE
    }

    static final class PreparedOwnerTransition {
        final GroundItem expected;
        final String expectedOwner;
        final String nextOwner;
        final int expectedItemId;
        final int expectedAmount;
        final Tile expectedTile;
        final long expectedSpawnedTick;
        final boolean expectedDevOwned;

        PreparedOwnerTransition(
            GroundItem expected,
            String expectedOwner,
            String nextOwner
        ){
            this.expected=Objects.requireNonNull(
                expected,
                "expected"
            );
            this.expectedOwner=expectedOwner;
            this.nextOwner=nextOwner;
            this.expectedItemId=expected.itemId;
            this.expectedAmount=expected.amount;
            this.expectedTile=expected.tile;
            this.expectedSpawnedTick=expected.spawnedTick;
            this.expectedDevOwned=expected.devOwned;
        }
    }

    synchronized PreparedAdd prepareAdd(
        int itemId,
        int amount,
        Tile tile,
        String owner,
        long tick,
        boolean devOwned
    ){
        if(itemId<0||
           amount<=0||
           tile==null)
            throw new IllegalArgumentException(
                "invalid prepared ground add"
            );

        GroundItem existing=null;

        for(GroundItem item:byId.values()){
            if(item.itemId==itemId&&
               item.tile.equals(tile)&&
               Objects.equals(item.owner,owner)&&
               item.devOwned==devOwned){
                existing=item;
                break;
            }
        }

        int oldAmount=
            existing==null
                ?0
                :existing.amount;

        long next=
            Math.addExact(
                (long)oldAmount,
                (long)amount
            );

        if(next>Integer.MAX_VALUE)
            throw new IllegalStateException(
                "ground amount overflow"
            );

        if(existing==null&&ids.get()==Long.MAX_VALUE)
            throw new IllegalStateException(
                "ground item id sequence exhausted"
            );

        return new PreparedAdd(
            existing,
            oldAmount,
            itemId,
            amount,
            tile,
            owner,
            tick,
            devOwned,
            (int)next
        );
    }

    synchronized GroundItem commitPreparedAdd(
        PreparedAdd prepared
    ){
        Objects.requireNonNull(
            prepared,
            "prepared"
        );

        if(prepared.expectedExisting!=null){
            GroundItem current=
                byId.get(
                    prepared.expectedExisting.id
                );

            if(current!=prepared.expectedExisting||
               current.amount!=prepared.expectedOldAmount)
                throw new IllegalStateException(
                    "ground add preimage changed before commit"
                );

            current.amount=
                prepared.newAmount;
            return current;
        }

        for(GroundItem current:byId.values()){
            if(current.itemId==prepared.itemId&&
               current.tile.equals(prepared.tile)&&
               Objects.equals(current.owner,prepared.owner)&&
               current.devOwned==prepared.devOwned)
                throw new IllegalStateException(
                    "ground add preimage changed before commit"
                );
        }

        long id=
            ids.incrementAndGet();

        if(id<=0L)
            throw new IllegalStateException(
                "ground item id sequence exhausted"
            );

        GroundItem created=
            new GroundItem(
                id,
                prepared.itemId,
                prepared.amount,
                prepared.tile,
                prepared.owner,
                prepared.tick,
                prepared.devOwned
            );

        byId.put(
            created.id,
            created
        );
        return created;
    }

    synchronized PreparedRemove prepareRemove(
        long id
    ){
        return new PreparedRemove(
            byId.get(id)
        );
    }

    synchronized boolean commitPreparedRemove(
        PreparedRemove prepared
    ){
        Objects.requireNonNull(
            prepared,
            "prepared"
        );

        if(prepared.expected==null)
            return false;

        GroundItem current=
            byId.get(
                prepared.expected.id
            );

        if(current!=prepared.expected)
            throw new IllegalStateException(
                "ground remove preimage changed before commit"
            );

        byId.remove(
            prepared.expected.id
        );
        return true;
    }

    synchronized PreparedOwnerTransition
        prepareOwnerTransition(
            long id,
            String expectedOwner,
            String nextOwner
        ){
        GroundItem current=
            byId.get(id);

        if(current==null||
           !Objects.equals(
                current.owner,
                expectedOwner
           ))
            return null;

        return new PreparedOwnerTransition(
            current,
            expectedOwner,
            nextOwner
        );
    }

    synchronized OwnerTransitionCommit
        commitPreparedOwnerTransition(
            PreparedOwnerTransition prepared
        ){
        if(prepared==null)
            return OwnerTransitionCommit
                .STALE_PREIMAGE;

        GroundItem current=
            byId.get(
                prepared.expected.id
            );

        if(current!=prepared.expected||
           current.itemId!=prepared.expectedItemId||
           current.amount!=prepared.expectedAmount||
           !current.tile.equals(
                prepared.expectedTile
           )||
           !Objects.equals(
                current.owner,
                prepared.expectedOwner
           )||
           current.spawnedTick!=
                prepared.expectedSpawnedTick||
           current.devOwned!=
                prepared.expectedDevOwned)
            return OwnerTransitionCommit
                .STALE_PREIMAGE;

        for(GroundItem candidate:
                byId.values()){
            if(candidate==current)
                continue;

            if(candidate.itemId==
                    prepared.expectedItemId&&
               candidate.tile.equals(
                    prepared.expectedTile
               )&&
               Objects.equals(
                    candidate.owner,
                    prepared.nextOwner
               )&&
               candidate.devOwned==
                    prepared.expectedDevOwned)
                return OwnerTransitionCommit
                    .TARGET_STACK_EXISTS;
        }

        current.owner=
            prepared.nextOwner;

        return OwnerTransitionCommit
            .COMMITTED;
    }

    synchronized GroundItem add(
        int itemId,
        int amount,
        Tile tile,
        String owner,
        long tick,
        boolean devOwned
    ){
        return addBatch(
            Collections.singletonList(
                new AddRequest(
                    itemId,
                    amount,
                    tile,
                    owner,
                    tick,
                    devOwned
                )
            )
        ).get(0);
    }

    synchronized List<GroundItem> addBatch(
        List<AddRequest> requests
    ){
        List<BatchMutation> mutations=
            addBatchDetailed(
                requests
            );

        if(mutations.isEmpty())
            return Collections.emptyList();

        ArrayList<GroundItem> out=
            new ArrayList<>(
                mutations.size()
            );

        for(BatchMutation mutation:mutations){
            GroundItem item=
                byId.get(
                    mutation.groundItemId
                );

            if(item==null)
                throw new IllegalStateException(
                    "committed ground item missing id="+
                    mutation.groundItemId
                );

            out.add(item);
        }

        return Collections.unmodifiableList(
            out
        );
    }

    synchronized List<BatchMutation> addBatchDetailed(
        List<AddRequest> requests
    ){
        Objects.requireNonNull(
            requests,
            "requests"
        );

        LinkedHashMap<StackKey,AddRequest>
            firstRequest=
                new LinkedHashMap<>();
        LinkedHashMap<StackKey,Long>
            requestedAmounts=
                new LinkedHashMap<>();

        for(AddRequest request:requests){
            AddRequest checked=
                Objects.requireNonNull(
                    request,
                    "request"
                );

            /*
             * Revalidate even though AddRequest validates construction.
             * This keeps the registry boundary authoritative if the request
             * representation changes later.
             */
            if(checked.itemId<0||
               checked.amount<=0||
               checked.tile==null)
                throw new IllegalArgumentException(
                    "invalid ground-item batch request"
                );

            StackKey key=
                new StackKey(
                    checked.itemId,
                    checked.tile,
                    checked.owner,
                    checked.devOwned
                );

            firstRequest.putIfAbsent(
                key,
                checked
            );

            long prior=
                requestedAmounts.getOrDefault(
                    key,
                    0L
                );

            long combined=
                Math.addExact(
                    prior,
                    (long)checked.amount
                );

            if(combined>
                    Integer.MAX_VALUE)
                throw new IllegalStateException(
                    "ground amount overflow"
                );

            requestedAmounts.put(
                key,
                combined
            );
        }

        if(firstRequest.isEmpty())
            return Collections.emptyList();

        LinkedHashMap<StackKey,GroundItem>
            existing=
                new LinkedHashMap<>();

        for(GroundItem item:byId.values()){
            StackKey key=
                new StackKey(
                    item.itemId,
                    item.tile,
                    item.owner,
                    item.devOwned
                );

            if(requestedAmounts.containsKey(
                    key))
                existing.put(
                    key,
                    item
                );
        }

        for(Map.Entry<StackKey,Long> entry:
                requestedAmounts.entrySet()){
            GroundItem current=
                existing.get(
                    entry.getKey()
                );

            if(current==null)
                continue;

            long combined=
                Math.addExact(
                    (long)current.amount,
                    entry.getValue()
                );

            if(combined>
                    Integer.MAX_VALUE)
                throw new IllegalStateException(
                    "ground amount overflow"
                );
        }

        long newStacks=
            requestedAmounts.size()-
            existing.size();

        if(newStacks>0L){
            long lastId=
                Math.addExact(
                    ids.get(),
                    newStacks
                );

            if(lastId<=0L)
                throw new IllegalStateException(
                    "ground item id sequence exhausted"
                );
        }

        ArrayList<BatchMutation> out=
            new ArrayList<>(
                requestedAmounts.size()
            );

        for(Map.Entry<StackKey,Long> entry:
                requestedAmounts.entrySet()){
            StackKey key=entry.getKey();
            int requested=
                Math.toIntExact(
                    entry.getValue()
                );

            GroundItem current=
                existing.get(key);

            if(current!=null){
                int oldAmount=
                    current.amount;

                current.amount=
                    Math.addExact(
                        current.amount,
                        requested
                    );
                out.add(
                    new BatchMutation(
                        current,
                        requested,
                        oldAmount
                    )
                );
                continue;
            }

            AddRequest first=
                firstRequest.get(key);

            long id=
                ids.incrementAndGet();

            if(id<=0L)
                throw new IllegalStateException(
                    "ground item id sequence exhausted"
                );

            GroundItem created=
                new GroundItem(
                    id,
                    key.itemId,
                    requested,
                    key.tile,
                    key.owner,
                    first.spawnedTick,
                    key.devOwned
                );

            byId.put(
                created.id,
                created
            );
            out.add(
                new BatchMutation(
                    created,
                    requested,
                    0
                )
            );
        }

        return Collections.unmodifiableList(
            out
        );
    }

    synchronized GroundItem byId(long id){
        return byId.get(id);
    }

    synchronized GroundItem find(
        int itemId,
        int x,
        int y,
        int plane
    ){
        for(GroundItem g:byId.values())
            if(g.itemId==itemId&&
               g.tile.x==x&&
               g.tile.y==y&&
               g.tile.plane==plane)
                return g;

        return null;
    }

    synchronized GroundItem findVisible(
        int itemId,
        int x,
        int y,
        int plane,
        String viewer
    ){
        GroundItem publicFallback=null;

        for(GroundItem g:byId.values()){
            if(g.itemId!=itemId||
               g.tile.x!=x||
               g.tile.y!=y||
               g.tile.plane!=plane)
                continue;

            if(g.owner==null){
                if(publicFallback==null)
                    publicFallback=g;
                continue;
            }

            if(viewer!=null&&
               g.owner.equalsIgnoreCase(viewer))
                return g;
        }

        return publicFallback;
    }

    synchronized GroundItem findOwned(
        int itemId,
        int x,
        int y,
        int plane,
        String owner
    ){
        for(GroundItem g:byId.values())
            if(g.itemId==itemId&&
               g.tile.x==x&&
               g.tile.y==y&&
               g.tile.plane==plane&&
               Objects.equals(g.owner,owner))
                return g;

        return null;
    }

    synchronized boolean remove(long id){
        return byId.remove(id)!=null;
    }

    synchronized List<GroundItem> snapshot(){
        return new ArrayList<>(
            byId.values()
        );
    }

    synchronized List<GroundItem> removeDevOwned(){
        List<GroundItem> out=
            new ArrayList<>();

        Iterator<GroundItem> it=
            byId.values()
                .iterator();

        while(it.hasNext()){
            GroundItem g=
                it.next();

            if(g.devOwned){
                out.add(g);
                it.remove();
            }
        }

        return out;
    }

    synchronized int size(){
        return byId.size();
    }
}