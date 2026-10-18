package spk.local;

import java.io.*;
import java.util.*;

/**
 * Exercise real World membership/generation ownership around synthetic
 * recipe preparation and queue-backed publication, without TCP or saves.
 */
public final class RecipeWorldOwnedAdapterTest {
    private RecipeWorldOwnedAdapterTest(){}

    private static void ok(boolean value,String why){
        if(!value)throw new AssertionError("RECIPE_WORLD_OWNER "+why);
    }

    private static final Set<Integer> STACKABLE=
        new HashSet<>(Arrays.asList(100,300));
    private static final Set<Integer> NONSTACKABLE=
        new HashSet<>(Arrays.asList(200,400));

    private static World isolatedWorld(){
        PlayerRepository isolated=new PlayerRepository(){
            @Override public Optional<PlayerSnapshot> load(String username){
                return Optional.empty();
            }
            @Override public void save(PlayerSnapshot snapshot){
                throw new AssertionError("recipe source-only test issued a save");
            }
        };
        return World.isolatedForTest(60000L,isolated);
    }

    private static void seed(WorldPlayer player){
        int[] ids=new int[player.bank().inventoryCapacity()];
        int[] qty=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[0]=100;qty[0]=5;
        ids[1]=200;qty[1]=1;
        ids[2]=200;qty[2]=1;
        player.bank().replaceInventorySemantic(ids,qty);
    }

    private static ServerPacketWriter writer(OutboundPacketQueue queue){
        return new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{0,0,0,0}));
    }

    private static RecipeWorldOwnedAdapter.Prepared mixed(
        World world,WorldPlayer player,long generation,
        ServerPacketWriter writer
    )throws Exception{
        Map<Integer,Integer> ingredients=new HashMap<>();
        ingredients.put(100,1);
        ingredients.put(200,1);
        return RecipeWorldOwnedAdapter.prepareMixed(
            world,player,generation,writer,ingredients,
            400,1,2,STACKABLE,NONSTACKABLE,null,false);
    }

    private static RecipeWorldOwnedAdapter.Prepared stackable(
        World world,WorldPlayer player,long generation,
        ServerPacketWriter writer
    )throws Exception{
        return RecipeWorldOwnedAdapter.prepareStackable(
            world,player,generation,writer,
            Collections.singletonMap(100,2),300,3,2,
            STACKABLE,null,false);
    }

    private static void ownerDenied(CheckedAction action,String why)
        throws Exception{
        boolean denied=false;
        try{
            action.run();
        }catch(IllegalStateException expected){
            denied=String.valueOf(expected.getMessage()).contains(
                "RECIPE_WORLD_OWNER_GENERATION_NOT_CURRENT");
        }
        ok(denied,why);
    }

    @FunctionalInterface
    private interface CheckedAction { void run()throws Exception; }

    public static void main(String[] args)throws Exception{
        // No fake World ownership from a prepared BankState alone.
        World world=isolatedWorld();
        try{
            WorldPlayer player=new WorldPlayer();
            seed(player);
            OutboundPacketQueue queue=new OutboundPacketQueue(4096);
            ServerPacketWriter writer=writer(queue);
            ownerDenied(()->mixed(world,player,1,writer),
                "unregistered player prepared a recipe");
            ok(queue.queuedBytes()==0&&
                player.bank().inventoryAt(0).qty==5,
                "unregistered preparation emitted or changed items");

            long firstGeneration=world.registerPlayer(
                player,"recipe_world_owner_fixture");
            ok(firstGeneration>0,"World generation not registered");
            RecipeWorldOwnedAdapter.Prepared prepared=
                mixed(world,player,firstGeneration,writer);
            ok(player.bank().inventoryAt(0).qty==5&&
                player.bank().inventoryAt(1).itemId==200,
                "World-owned preparation mutated bank");
            ok(RecipeWorldOwnedAdapter.publish(prepared)==
                ServerPacketWriter.RecoverablePacketResult.COMMITTED,
                "World-owned mixed recipe did not publish");
            ok(player.bank().inventoryAt(0).qty==3&&
                player.bank().inventoryAt(1).itemId==400&&
                player.bank().inventoryAt(2).itemId==400&&
                queue.queuedPackets()==1,
                "World-owned mixed recipe postimage or queue incorrect");

            boolean replayDenied=false;
            try{
                RecipeWorldOwnedAdapter.publish(prepared);
            }catch(IllegalStateException expected){
                replayDenied=String.valueOf(expected.getMessage()).contains(
                    "RECIPE_PREPARED_ALREADY_COMMITTED");
            }
            ok(replayDenied&&queue.queuedPackets()==1,
                "World-owned recipe replay duplicated publication");
            queue.drainTo(new ByteArrayOutputStream(),4096);

            // A distinct, registered WorldPlayer cannot publish the same
            // prepared authority. The wrapper does not accept substitute
            // player or BankState arguments at publication time.
            WorldPlayer secondPlayer=new WorldPlayer();
            seed(secondPlayer);
            long secondGeneration=world.registerPlayer(
                secondPlayer,"recipe_second_owner_fixture");
            OutboundPacketQueue staleQueue=new OutboundPacketQueue(4096);
            ServerPacketWriter staleWriter=writer(staleQueue);
            RecipeWorldOwnedAdapter.Prepared old=
                stackable(world,secondPlayer,secondGeneration,staleWriter);
            ok(world.unregisterPlayer(secondPlayer,secondGeneration),
                "World could not unregister recipe owner");
            ownerDenied(()->RecipeWorldOwnedAdapter.publish(old),
                "unregistered owner published prepared recipe");
            ok(staleQueue.queuedBytes()==0&&
                secondPlayer.bank().inventoryAt(0).qty==5,
                "owner exit emitted bytes or changed bank");
            long rejoined=world.registerPlayer(
                secondPlayer,"recipe_second_owner_fixture");
            ok(rejoined>secondGeneration,
                "World owner generation did not advance on rejoin");
            ownerDenied(()->RecipeWorldOwnedAdapter.publish(old),
                "rejoined owner revived prior-generation recipe");
            ownerDenied(()->stackable(
                world,secondPlayer,secondGeneration,staleWriter),
                "old generation could prepare after re-registration");
            RecipeWorldOwnedAdapter.Prepared fresh=
                stackable(world,secondPlayer,rejoined,staleWriter);
            ok(RecipeWorldOwnedAdapter.publish(fresh)==
                ServerPacketWriter.RecoverablePacketResult.COMMITTED&&
                secondPlayer.bank().inventoryAt(0).qty==1&&
                secondPlayer.bank().inventoryAt(3).itemId==300&&
                secondPlayer.bank().inventoryAt(3).qty==6&&
                staleQueue.queuedPackets()==1,
                "fresh registered generation could not publish");
            ok(world.unregisterPlayer(secondPlayer,rejoined),
                "World second owner cleanup failed");

            // A different World's player with its own registration may
            // not masquerade as the owner in this World's registry.
            World foreignWorld=isolatedWorld();
            try{
                WorldPlayer foreignPlayer=new WorldPlayer();
                seed(foreignPlayer);
                long foreignGen=foreignWorld.registerPlayer(
                    foreignPlayer,"recipe_foreign_world_fixture");
                OutboundPacketQueue foreignQueue=
                    new OutboundPacketQueue(4096);
                ServerPacketWriter foreignWriter=writer(foreignQueue);
                ownerDenied(()->mixed(
                    world,foreignPlayer,foreignGen,foreignWriter),
                    "different World registry admitted foreign player");
                ok(foreignQueue.queuedBytes()==0&&
                    foreignPlayer.bank().inventoryAt(0).qty==5,
                    "foreign World check mutated player");
                ok(foreignWorld.unregisterPlayer(
                    foreignPlayer,foreignGen),
                    "foreign World cleanup failed");
            }finally{
                foreignWorld.close();
            }

            ok(world.unregisterPlayer(player,firstGeneration),
                "World primary owner cleanup failed");
        }finally{
            world.close();
        }
        System.out.println("RECIPE_WORLD_OWNER_PASS"+
            " unregisteredOwnerDenied=true"+
            " registeredWorldMixedCommit=true"+
            " sourceOnlyReplayDenied=true"+
            " unregisterRejoinGenerationFenced=true"+
            " unrelatedWorldDenied=true"+
            " noLiveRecipes=true noCrossWorldAccountLease=true");
    }
}
