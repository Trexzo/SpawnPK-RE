package spk.local;

/**
 * R112 OFFLINE ONLY. Strict variant of R111's read-only queued opcode14
 * intent: require a nonzero, unchanged conservative BankState inventory
 * mutation epoch at the exact selected source actor and generation.
 *
 * This is an experimental diagnosis, never an item mutation permit.
 * The epoch tracks 20 known BankState mutator entrypoints only;
 * raw Stack aliases and unknown external writes remain outside its proof.
 */
final class R112OfflineEpochPinnedItemOnPlayerIntent {
    private final R111OfflinePinnedItemOnPlayerIntent prior;
    private final long initialInventoryEpoch;

    private R112OfflineEpochPinnedItemOnPlayerIntent(
        R111OfflinePinnedItemOnPlayerIntent prior,long epoch
    ){
        this.prior=prior;
        this.initialInventoryEpoch=epoch;
    }

    static R112OfflineEpochPinnedItemOnPlayerIntent captureForTest(
        World world,WorldPlayer source,long generation,
        Player81WorldSync.Context viewer,GenericInteractionEvent event
    )throws Exception{
        R111OfflinePinnedItemOnPlayerIntent prior=
            R111OfflinePinnedItemOnPlayerIntent.captureForTest(
                world,source,generation,viewer,event);
        R110OfflineItemOnPlayerPreflight.Review current=
            prior.recheckForTest(world,source,generation,viewer);
        if(current.selectedMutationEpoch<=0L)
            throw rejected("inventory mutation audit not enabled");
        return new R112OfflineEpochPinnedItemOnPlayerIntent(
            prior,current.selectedMutationEpoch);
    }

    R110OfflineItemOnPlayerPreflight.Review recheckForTest(
        World world,WorldPlayer source,long generation,
        Player81WorldSync.Context viewer
    )throws Exception{
        // R111 verifies source and target IDs/generations, item/slot and
        // quantity. R110 captures this epoch under both actor locks.
        R110OfflineItemOnPlayerPreflight.Review current=
            prior.recheckForTest(world,source,generation,viewer);
        if(current.selectedMutationEpoch<=0L||
           current.selectedMutationEpoch!=initialInventoryEpoch)
            throw rejected("inventory mutation epoch changed while queued");
        return current;
    }

    long initialEpochForTest(){return initialInventoryEpoch;}

    private static IllegalStateException rejected(String detail){
        return new IllegalStateException(
            "R112_QUEUED_ITEM_EPOCH_DENIED "+detail);
    }
}
