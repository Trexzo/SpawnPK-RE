package spk.local;

import java.util.Objects;

/**
 * R107 OFFLINE TEST ONLY. Provenance of a running R103/R104 tagged plugin
 * callback on the current execution thread, not a sandbox or security
 * restriction on arbitrary Runnable closures.
 *
 * A captured direct reference (e.g. other.bank()) bypasses this helper.
 * Nothing in the public plugin API or production session runtime installs
 * this owner context. See the R107 deliberate negative-control regression.
 */
final class R107OfflinePluginOwnerCall {
    private static final class Owner {
        final WorldEventQueue queue;
        final EntityId id;
        final long generation;

        Owner(WorldEventQueue queue,EntityId id,long generation){
            this.queue=queue;
            this.id=id;
            this.generation=generation;
        }
    }

    private static final ThreadLocal<Owner> CURRENT=new ThreadLocal<>();

    static void runTaggedForTest(
            WorldEventQueue queue,EntityId id,long generation,
            Runnable action){
        Objects.requireNonNull(queue,"queue");
        Objects.requireNonNull(id,"id");
        Objects.requireNonNull(action,"action");
        if(generation<=0L)
            throw new IllegalArgumentException(
                "R107_PLUGIN_OWNER_DENIED invalid generation");
        Owner prior=CURRENT.get();
        if(prior!=null && (prior.queue!=queue ||
                !prior.id.equals(id)||prior.generation!=generation))
            throw new IllegalStateException(
                "R107_PLUGIN_OWNER_DENIED nested owner change");
        CURRENT.set(new Owner(queue,id,generation));
        try{action.run();}
        finally{
            if(prior==null)CURRENT.remove();
            else CURRENT.set(prior);
        }
    }

    /**
     * No active tagged callback: preserve the old World behavior.
     * Tagged callback: only the exact World queue, actor and generation
     * may enter World.withOpenPlayerMutationOwnershipIfCurrent.
     */
    static void requireCompatibleIfTagged(
            WorldEventQueue queue,EntityId id,long generation){
        Owner owner=CURRENT.get();
        if(owner!=null&&
                (owner.queue!=queue||!owner.id.equals(id)||
                 owner.generation!=generation))
            throw new IllegalStateException(
                "R107_PLUGIN_CROSS_ACTOR_MUTATION_DENIED");
    }

    /**
     * R108 OFFLINE TEST ONLY. Unlike the R107 compatibility check,
     * a capability must be invoked by an actively tagged callback
     * with EXACT World queue, EntityId and registration generation.
     * No tagged callback means denial, never implicit authorization.
     */
    static void requireTaggedExactForTest(
            WorldEventQueue queue,EntityId id,long generation){
        Owner owner=CURRENT.get();
        if(owner==null||owner.queue!=queue||
                !owner.id.equals(id)||owner.generation!=generation)
            throw new IllegalStateException(
                "R108_CAPABILITY_CONTEXT_DENIED");
    }

    static boolean activeForTest(){return CURRENT.get()!=null;}

    private R107OfflinePluginOwnerCall(){}
}
