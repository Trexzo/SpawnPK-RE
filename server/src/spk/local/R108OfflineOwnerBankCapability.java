package spk.local;

import java.util.Objects;

/**
 * R108 OFFLINE TEST ONLY: a deliberately tiny example of owner-bound
 * mutation, rather than exposing WorldPlayer, BankState or a Runnable
 * that gets raw state.
 *
 * Callers must already be executing inside an R103/R104 owner-tagged
 * callback with this exact World queue, EntityId and generation. World
 * then revalidates membership under its actual lifecycle/player locks.
 *
 * This is NOT a public PluginScheduler/ContentPlayer capability, and
 * arbitrary captured raw WorldPlayer references remain unprotected.
 */
final class R108OfflineOwnerBankCapability {
    private final World world;
    private final EntityId ownerId;
    private final long generation;

    R108OfflineOwnerBankCapability(
            World world,EntityId ownerId,long generation){
        this.world=Objects.requireNonNull(world,"world");
        this.ownerId=Objects.requireNonNull(ownerId,"ownerId");
        if(generation<=0L)
            throw new IllegalArgumentException(
                "R108_BANK_CAPABILITY_DENIED invalid generation");
        this.generation=generation;
    }

    /**
     * Synthetic single-item grant only. Intentionally returns a value,
     * not WorldPlayer, BankState, packet writer or a mutable array.
     * All actual state changes happen under World actor ownership.
     */
    String grantSingleItemForTest(int itemId)throws Exception{
        if(itemId<=0)
            throw new IllegalArgumentException(
                "R108_BANK_CAPABILITY_DENIED invalid itemId");
        R107OfflinePluginOwnerCall.requireTaggedExactForTest(
            world.events(),ownerId,generation);
        if(!world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "R108_BANK_CAPABILITY_DENIED not World pulse");

        WorldPlayer actor=world.players().byId(ownerId);
        if(actor==null)
            throw new IllegalStateException(
                "R108_BANK_CAPABILITY_DENIED missing owner");
        final String[] result=new String[1];
        boolean owned=world.withOpenPlayerMutationOwnershipIfCurrent(
            actor,generation,()->{
                // This second assertion keeps the origin explicit even
                // under future refactors of the World lock helper.
                R107OfflinePluginOwnerCall.requireTaggedExactForTest(
                    world.events(),ownerId,generation);
                ServerPacketWriter writer=new ServerPacketWriter(
                    new OutboundPacketQueue(),new IsaacCipher(
                        new int[]{1,2,3,4}));
                result[0]=actor.bank().spawnItem(itemId,1,writer);
            });
        if(!owned)
            throw new IllegalStateException(
                "R108_BANK_CAPABILITY_DENIED stale owner");
        return result[0];
    }
}
