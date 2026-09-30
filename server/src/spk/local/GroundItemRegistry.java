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