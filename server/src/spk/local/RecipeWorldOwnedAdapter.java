package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Source-only recipe bridge into the EXISTING World/player ownership monitor.
 *
 * This does not register a recipe action, determine actual v308 item IDs,
 * grant XP or create a named-account/cross-JVM durable lease. Never use the
 * underlying direct semantic recipe commit as a live gameplay shortcut.
 */
final class RecipeWorldOwnedAdapter {
    private RecipeWorldOwnedAdapter(){}

    static final class Prepared {
        private final World world;
        private final WorldPlayer player;
        private final long generation;
        private final ServerPacketWriter writer;
        private final RecipeBankSemanticAdapter.Prepared inventory;

        private Prepared(
            World world,WorldPlayer player,long generation,
            ServerPacketWriter writer,
            RecipeBankSemanticAdapter.Prepared inventory
        ){
            this.world=world;
            this.player=player;
            this.generation=generation;
            this.writer=writer;
            this.inventory=inventory;
        }
    }

    static Prepared prepareStackable(
        World world,WorldPlayer player,long generation,
        ServerPacketWriter writer,Map<Integer,Integer> ingredients,
        int product,int perBatch,int batches,Set<Integer> stackable,
        Integer toolId,boolean consumeTool
    )throws Exception{
        checkInputs(world,player,generation,writer);
        AtomicReference<RecipeBankSemanticAdapter.Prepared> planned=
            new AtomicReference<>();
        boolean owned=world.withOpenPlayerMutationOwnershipIfCurrent(
            player,generation,
            ()->planned.set(
                RecipeBankSemanticAdapter.prepareWithTool(
                    player.bank(),ingredients,product,perBatch,batches,
                    stackable,toolId,consumeTool))
        );
        if(!owned)
            throw new IllegalStateException(
                "RECIPE_WORLD_OWNER_GENERATION_NOT_CURRENT");
        return new Prepared(world,player,generation,writer,
            Objects.requireNonNull(planned.get(),"owned recipe postimage"));
    }

    static Prepared prepareMixed(
        World world,WorldPlayer player,long generation,
        ServerPacketWriter writer,Map<Integer,Integer> ingredients,
        int product,int perBatch,int batches,
        Set<Integer> stackable,Set<Integer> nonstackable,
        Integer toolId,boolean consumeTool
    )throws Exception{
        checkInputs(world,player,generation,writer);
        AtomicReference<RecipeBankSemanticAdapter.Prepared> planned=
            new AtomicReference<>();
        boolean owned=world.withOpenPlayerMutationOwnershipIfCurrent(
            player,generation,
            ()->planned.set(
                RecipeBankSemanticAdapter.prepareMixedWithTool(
                    player.bank(),ingredients,product,perBatch,batches,
                    stackable,nonstackable,toolId,consumeTool))
        );
        if(!owned)
            throw new IllegalStateException(
                "RECIPE_WORLD_OWNER_GENERATION_NOT_CURRENT");
        return new Prepared(world,player,generation,writer,
            Objects.requireNonNull(planned.get(),"owned mixed recipe postimage"));
    }

    static ServerPacketWriter.RecoverablePacketResult publish(
        Prepared prepared
    )throws Exception{
        Objects.requireNonNull(prepared,"prepared");
        AtomicReference<ServerPacketWriter.RecoverablePacketResult> result=
            new AtomicReference<>();
        boolean owned=prepared.world.withOpenPlayerMutationOwnershipIfCurrent(
            prepared.player,prepared.generation,
            ()->result.set(
                RecipeBankSemanticAdapter.publishAndCommitIfIdle(
                    prepared.player.bank(),prepared.inventory,prepared.writer))
        );
        if(!owned)
            throw new IllegalStateException(
                "RECIPE_WORLD_OWNER_GENERATION_NOT_CURRENT");
        return Objects.requireNonNull(result.get(),"owned recipe publication result");
    }

    private static void checkInputs(
        World world,WorldPlayer player,long generation,ServerPacketWriter writer
    ){
        Objects.requireNonNull(world,"world");
        Objects.requireNonNull(player,"player");
        Objects.requireNonNull(writer,"writer");
        if(generation<=0)
            throw new IllegalArgumentException(
                "invalid recipe World owner generation");
    }
}
