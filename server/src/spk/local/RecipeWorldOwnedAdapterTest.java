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

    private static void concurrentOwnerExit(World world)
        throws Exception{
        // The real World lifecycle/player mutation locks must linearize
        // publication against exact-generation unregister. Unlike a
        // BankState-only lock this directly exercises World membership.
        java.util.concurrent.ExecutorService workers=
            java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            for(int trial=0;trial<32;trial++){
                WorldPlayer player=new WorldPlayer();
                seed(player);
                long generation=world.registerPlayer(
                    player,"recipe_race_"+trial);
                OutboundPacketQueue queue=new OutboundPacketQueue(4096);
                ServerPacketWriter writer=writer(queue);
                RecipeWorldOwnedAdapter.Prepared prepared=
                    stackable(world,player,generation,writer);
                java.util.concurrent.CountDownLatch ready=
                    new java.util.concurrent.CountDownLatch(2);
                java.util.concurrent.CountDownLatch start=
                    new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.Future<String> publication=
                    workers.submit(()->{
                        ready.countDown();start.await();
                        try{
                            return String.valueOf(
                                RecipeWorldOwnedAdapter.publish(prepared));
                        }catch(IllegalStateException expected){
                            return String.valueOf(expected.getMessage());
                        }
                    });
                java.util.concurrent.Future<Boolean> unregistration=
                    workers.submit(()->{
                        ready.countDown();start.await();
                        return world.unregisterPlayer(
                            player,generation);
                    });
                ok(ready.await(5,java.util.concurrent.TimeUnit.SECONDS),
                    "ownership contenders missed start gate "+trial);
                start.countDown();
                String result=publication.get(
                    5,java.util.concurrent.TimeUnit.SECONDS);
                ok(unregistration.get(
                    5,java.util.concurrent.TimeUnit.SECONDS),
                    "World unregister lost generation "+trial);
                boolean published="COMMITTED".equals(result);
                ok(published||result.contains(
                        "RECIPE_WORLD_OWNER_GENERATION_NOT_CURRENT"),
                    "ownership race unexpected result trial="+trial+
                    " result="+result);
                ok(queue.queuedPackets()==(published?1:0)&&
                    player.bank().inventoryAt(0).qty==
                        (published?1:5)&&
                    ((player.bank().inventoryAt(3)==null)==!published),
                    "publish/unregister race leaked stale state trial="+
                    trial+" result="+result);
                // Regardless of which operation won, old owner is now
                // unregistered and cannot replay the prepared request.
                ownerDenied(()->RecipeWorldOwnedAdapter.publish(prepared),
                    "unregister race allowed post-exit replay "+trial);
            }
        }finally{
            workers.shutdownNow();
        }
    }

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

            // A backpressured publication is retryable ONLY while the
            // identical World owner generation remains active. Unregister
            // followed by rejoin must not resurrect the old pending action.
            WorldPlayer delayedPlayer=new WorldPlayer();
            seed(delayedPlayer);
            long delayedGen=world.registerPlayer(
                delayedPlayer,"recipe_owner_backpressure_fixture");
            OutboundPacketQueue delayedQueue=new OutboundPacketQueue(4096);
            delayedQueue.offerBatch(new byte[4096]);
            ServerPacketWriter delayedWriter=writer(delayedQueue);
            RecipeWorldOwnedAdapter.Prepared waiting=
                stackable(world,delayedPlayer,delayedGen,delayedWriter);
            ok(RecipeWorldOwnedAdapter.publish(waiting)==
                ServerPacketWriter.RecoverablePacketResult.RETRACTED_RETRYABLE&&
                delayedQueue.queuedBytes()==4096&&
                delayedPlayer.bank().inventoryAt(0).qty==5,
                "World-owned backpressure mutated or admitted source");
            delayedQueue.drainTo(new ByteArrayOutputStream(),4096);
            ok(world.unregisterPlayer(delayedPlayer,delayedGen),
                "backpressured player unregister failed");
            ownerDenied(()->RecipeWorldOwnedAdapter.publish(waiting),
                "backpressured recipe committed after unregister");
            long freshGen=world.registerPlayer(
                delayedPlayer,"recipe_owner_backpressure_fixture");
            ownerDenied(()->RecipeWorldOwnedAdapter.publish(waiting),
                "backpressured recipe committed after generation changed");
            ok(delayedQueue.queuedBytes()==0&&
                delayedPlayer.bank().inventoryAt(0).qty==5,
                "stale retry leaked queued bytes or changed inventory");
            RecipeWorldOwnedAdapter.Prepared updated=
                stackable(world,delayedPlayer,freshGen,delayedWriter);
            ok(RecipeWorldOwnedAdapter.publish(updated)==
                ServerPacketWriter.RecoverablePacketResult.COMMITTED&&
                delayedPlayer.bank().inventoryAt(0).qty==1&&
                delayedPlayer.bank().inventoryAt(3).qty==6&&
                delayedQueue.queuedPackets()==1,
                "fresh generation could not retry after stale denial");
            ok(world.unregisterPlayer(delayedPlayer,freshGen),
                "backpressured player final cleanup failed");

            // Valid World membership alone cannot revive a prepared
            // request after a source-local inventory ABA. Both guards
            // apply, with zero queued packets on the stale preimage.
            WorldPlayer changedPlayer=new WorldPlayer();
            seed(changedPlayer);
            long changedGen=world.registerPlayer(
                changedPlayer,"recipe_owner_aba_fixture");
            OutboundPacketQueue changedQueue=new OutboundPacketQueue(4096);
            ServerPacketWriter changedWriter=writer(changedQueue);
            RecipeWorldOwnedAdapter.Prepared oldInventory=
                stackable(world,changedPlayer,changedGen,changedWriter);
            ok(world.withOpenPlayerMutationOwnershipIfCurrent(
                changedPlayer,changedGen,
                ()->{
                    int[] restoredIds=new int[
                        changedPlayer.bank().inventoryCapacity()];
                    int[] restoredQty=new int[restoredIds.length];
                    Arrays.fill(restoredIds,-1);
                    restoredIds[0]=100;restoredQty[0]=5;
                    restoredIds[1]=200;restoredQty[1]=1;
                    restoredIds[2]=200;restoredQty[2]=1;
                    changedPlayer.bank().replaceInventorySemantic(
                        restoredIds,restoredQty);
                }),
                "World owner did not admit identical-value ABA fixture");
            boolean generationStale=false;
            try{
                RecipeWorldOwnedAdapter.publish(oldInventory);
            }catch(IllegalStateException expected){
                generationStale=String.valueOf(expected.getMessage()).contains(
                    "RECIPE_STALE_INVENTORY_GENERATION");
            }
            ok(generationStale&&changedQueue.queuedBytes()==0&&
                changedPlayer.bank().inventoryAt(0).qty==5,
                "World-authorized inventory ABA emitted stale recipe");
            RecipeWorldOwnedAdapter.Prepared newInventory=
                stackable(world,changedPlayer,changedGen,changedWriter);
            ok(RecipeWorldOwnedAdapter.publish(newInventory)==
                ServerPacketWriter.RecoverablePacketResult.COMMITTED&&
                changedPlayer.bank().inventoryAt(0).qty==1&&
                changedQueue.queuedPackets()==1,
                "fresh World-owned recipe failed after inventory ABA");
            ok(world.unregisterPlayer(changedPlayer,changedGen),
                "ABA owner cleanup failed");

            WorldPlayer terminalPlayer=new WorldPlayer();
            seed(terminalPlayer);
            long terminalGen=world.registerPlayer(
                terminalPlayer,"recipe_terminal_fixture");
            OutboundPacketQueue terminalQueue=new OutboundPacketQueue(4096);
            ServerPacketWriter terminalWriter=writer(terminalQueue);
            RecipeWorldOwnedAdapter.Prepared terminalPrepared=
                stackable(world,terminalPlayer,terminalGen,terminalWriter);
            terminalWriter.markTerminal();
            boolean terminalDenied=false;
            try{
                RecipeWorldOwnedAdapter.publish(terminalPrepared);
            }catch(IOException expected){
                terminalDenied=String.valueOf(expected.getMessage())
                    .toLowerCase(Locale.ROOT).contains("terminal");
            }
            ok(terminalDenied&&terminalQueue.queuedBytes()==0&&
                terminalPlayer.bank().inventoryAt(0).qty==5,
                "terminal session writer published World-owned recipe");
            ok(world.unregisterPlayer(terminalPlayer,terminalGen),
                "terminal owner cleanup failed");

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

            concurrentOwnerExit(world);
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
            " backpressureGenerationFenced=true"+
            " ownedInventoryAbaDenied=true terminalWriterDenied=true"+
            " unregisterPublicationRaces=32"+
            " unrelatedWorldDenied=true"+
            " noLiveRecipes=true noCrossWorldAccountLease=true");
    }
}
