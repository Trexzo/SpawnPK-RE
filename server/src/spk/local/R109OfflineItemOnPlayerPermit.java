package spk.local;

import java.util.Objects;

/**
 * R109 OFFLINE TEST ONLY: one explicit item-on-player authorization
 * for a source/target pair, exact generations, item and World.
 *
 * There is deliberately NO general-purpose callback accepting WorldPlayer
 * or BankState and NO public ContentItemOnPlayerContext API change.
 * Creation here is by a trusted *synthetic fixture*, not an authenticated
 * user intent or a non-forgeable permit issued to untrusted Java code.
 */
final class R109OfflineItemOnPlayerPermit {
    private final World world;
    private final EntityId sourceId;
    private final long sourceGeneration;
    private final EntityId targetId;
    private final long targetGeneration;
    private final int allowedItem;
    private boolean spent;

    private R109OfflineItemOnPlayerPermit(
            World world,EntityId sourceId,long sourceGeneration,
            EntityId targetId,long targetGeneration,int allowedItem){
        this.world=world;
        this.sourceId=sourceId;
        this.sourceGeneration=sourceGeneration;
        this.targetId=targetId;
        this.targetGeneration=targetGeneration;
        this.allowedItem=allowedItem;
    }

    /** The fixture, acting as a trusted intent resolver, picks the pair. */
    static R109OfflineItemOnPlayerPermit issueForTest(
            World world,WorldPlayer source,long sourceGeneration,
            WorldPlayer target,long targetGeneration,int allowedItem){
        Objects.requireNonNull(world,"world");
        Objects.requireNonNull(source,"source");
        Objects.requireNonNull(target,"target");
        if(source==target||source.id().equals(target.id())||
                sourceGeneration<=0L||targetGeneration<=0L||
                allowedItem<=0)
            throw new IllegalArgumentException(
                "R109_PAIR_PERMIT_DENIED invalid source/target/item");
        if(!world.players().owns(source,sourceGeneration)||
                !world.players().owns(target,targetGeneration))
            throw new IllegalStateException(
                "R109_PAIR_PERMIT_DENIED not currently owned at issue");
        return new R109OfflineItemOnPlayerPermit(
            world,source.id(),sourceGeneration,target.id(),
            targetGeneration,allowedItem);
    }

    /**
     * The caller cannot swap the target or item after issuance.
     * Actual permission is rechecked under BOTH World actor locks.
     * Single-use state is consumed only once the pair is current.
     */
    synchronized String grantToTargetForTest(
            EntityId requestedTarget,int selectedItem)throws Exception{
        if(!targetId.equals(requestedTarget)||
                selectedItem!=allowedItem)
            throw new IllegalStateException(
                "R109_PAIR_PERMIT_DENIED target/item mismatch");
        if(spent)
            throw new IllegalStateException(
                "R109_PAIR_PERMIT_DENIED already spent");
        R107OfflinePluginOwnerCall.requireTaggedExactForTest(
            world.events(),sourceId,sourceGeneration);
        if(!world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "R109_PAIR_PERMIT_DENIED not World pulse");

        WorldPlayer source=world.players().byId(sourceId);
        WorldPlayer target=world.players().byId(targetId);
        if(source==null||target==null)
            throw new IllegalStateException(
                "R109_PAIR_PERMIT_DENIED stale actor reference");
        final String[] result=new String[1];
        boolean current=world.withOfflineDualPlayerMutationOwnershipForTest(
            source,sourceGeneration,target,targetGeneration,()->{
                R107OfflinePluginOwnerCall.requireTaggedExactForTest(
                    world.events(),sourceId,sourceGeneration);
                if(!Thread.holdsLock(source.mutationLock())||
                        !Thread.holdsLock(target.mutationLock()))
                    throw new AssertionError(
                        "R109 missing either player mutation lock");
                if(spent)
                    throw new IllegalStateException(
                        "R109_PAIR_PERMIT_DENIED already spent");
                // Fail closed on later packet/state exceptions: never
                // replay this synthetic intent automatically.
                spent=true;
                ServerPacketWriter writer=new ServerPacketWriter(
                    new OutboundPacketQueue(),new IsaacCipher(
                        new int[]{1,2,3,4}));
                result[0]=target.bank().spawnItem(
                    allowedItem,1,writer);
                if(!result[0].startsWith("ITEM_SPAWN_OK"))
                    throw new IllegalStateException(
                        "R109_PAIR_PERMIT_DENIED semantic item grant failed");
            });
        if(!current)
            throw new IllegalStateException(
                "R109_PAIR_PERMIT_DENIED stale owner generation");
        return result[0];
    }

    synchronized boolean spentForTest(){return spent;}
}
