package spk.local;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Source-only exact LocalSession writer identity and revocation tests.
 * No recipe opcode registration, TCP login or durable account authority.
 */
public final class RecipeSessionWriterGuardTest {
    private RecipeSessionWriterGuardTest(){}
    private static final Set<Integer> STACKABLE=
        new HashSet<>(Arrays.asList(100,300));
    private static final Set<Integer> NONSTACKABLE=
        new HashSet<>(Arrays.asList(200,400));

    @FunctionalInterface
    interface Checked { void run() throws Exception; }

    private static void ok(boolean condition,String reason){
        if(!condition)
            throw new AssertionError("RECIPE_SESSION_WRITER "+reason);
    }
    private static void denied(Checked action,String token)throws Exception{
        boolean failed=false;
        try{action.run();}
        catch(IllegalStateException expected){
            failed=String.valueOf(expected.getMessage()).contains(token);
        }
        ok(failed,"missing denial "+token);
    }

    private static World world(){
        return World.isolatedForTest(60000L,new PlayerRepository(){
            @Override public Optional<PlayerSnapshot> load(String user){
                return Optional.empty();
            }
            @Override public void save(PlayerSnapshot snapshot){
                throw new AssertionError("recipe guard cannot write accounts");
            }
        });
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
    private static RecipeWorldOwnedAdapter.SessionPrepared prepare(
        RecipeSessionWriterGuard witness)throws Exception{
        return RecipeWorldOwnedAdapter.prepareSessionStackable(
            witness,Collections.singletonMap(100,2),300,3,2,
            STACKABLE,null,false);
    }
    private static RecipeWorldOwnedAdapter.SessionPrepared prepareMixed(
        RecipeSessionWriterGuard witness)throws Exception{
        Map<Integer,Integer> inputs=new HashMap<>();
        inputs.put(100,1);
        inputs.put(200,1);
        return RecipeWorldOwnedAdapter.prepareSessionMixed(
            witness,inputs,400,1,2,
            STACKABLE,NONSTACKABLE,null,false);
    }

    public static void main(String[] args)throws Exception{
        // Login installs the guard on the socket thread; logout can be
        // requested from a World callback. Protect this publication edge
        // against accidental removal of the volatile field contract.
        java.lang.reflect.Field sessionField=
            LocalSession.class.getDeclaredField("recipeSessionWriterGuard");
        ok(java.lang.reflect.Modifier.isVolatile(
                sessionField.getModifiers()),
            "LocalSession recipe witness publication is not volatile");
        World world=world();
        try{
            WorldPlayer player=new WorldPlayer();
            seed(player);
            OutboundPacketQueue queue=new OutboundPacketQueue(4096);
            ServerPacketWriter writer=writer(queue);
            denied(()->new RecipeSessionWriterGuard(world,player,1,writer),
                "RECIPE_WORLD_OWNER_GENERATION_NOT_CURRENT");

            long generation=world.registerPlayer(
                player,"recipe_session_guard");
            RecipeSessionWriterGuard witness=
                new RecipeSessionWriterGuard(world,player,generation,writer);
            denied(()->witness.runBound(
                    world,player,generation,writer,()->null),
                "RECIPE_WORLD_MUTATION_LOCK_REQUIRED");
            ok(witness.activeForTest(),
                "rejected unowned call revoked rightful session writer");
            ServerPacketWriter foreign=writer(
                new OutboundPacketQueue(4096));
            denied(()->world.withOpenPlayerMutationOwnershipIfCurrent(
                player,generation,
                ()->witness.runBound(
                    world,player,generation,foreign,()->null)),
                "RECIPE_SESSION_WRITER_IDENTITY_MISMATCH");
            denied(()->world.withOpenPlayerMutationOwnershipIfCurrent(
                player,generation,
                ()->witness.runBound(
                    world,new WorldPlayer(),generation,writer,()->null)),
                "RECIPE_SESSION_WRITER_IDENTITY_MISMATCH");
            RecipeWorldOwnedAdapter.SessionPrepared prepared=prepare(witness);
            ok(RecipeWorldOwnedAdapter.publishSession(prepared)==
                    ServerPacketWriter.RecoverablePacketResult.COMMITTED&&
                queue.queuedPackets()==1&&
                player.bank().inventoryAt(0).qty==1&&
                player.bank().inventoryAt(3).qty==6,
                "bound writer did not publish expected inventory");
            denied(()->RecipeWorldOwnedAdapter.publishSession(prepared),
                "RECIPE_PREPARED_ALREADY_COMMITTED");
            witness.revokeAndAwait();
            denied(()->prepare(witness),"RECIPE_SESSION_WRITER_REVOKED");
            denied(()->RecipeWorldOwnedAdapter.publishSession(prepared),
                "RECIPE_SESSION_WRITER_REVOKED");
            denied(()->new RecipeSessionWriterGuard(
                    world,player,generation,writer),
                "RECIPE_SESSION_WRITER_GENERATION_ALREADY_CLAIMED");
            denied(()->new RecipeSessionWriterGuard(
                    world,player,generation,foreign),
                "RECIPE_SESSION_WRITER_GENERATION_ALREADY_CLAIMED");
            ok(world.unregisterPlayer(player,generation),
                "first owner cleanup failed");

            WorldPlayer delayed=new WorldPlayer();
            seed(delayed);
            long delayedGeneration=world.registerPlayer(
                delayed,"recipe_session_backpressure");
            OutboundPacketQueue delayedQueue=new OutboundPacketQueue(4096);
            delayedQueue.offerBatch(new byte[4096]);
            ServerPacketWriter delayedWriter=writer(delayedQueue);
            RecipeSessionWriterGuard delayedGuard=
                new RecipeSessionWriterGuard(
                    world,delayed,delayedGeneration,delayedWriter);
            RecipeWorldOwnedAdapter.SessionPrepared waiting=
                prepare(delayedGuard);
            ok(RecipeWorldOwnedAdapter.publishSession(waiting)==
                    ServerPacketWriter.RecoverablePacketResult.RETRACTED_RETRYABLE&&
                delayed.bank().inventoryAt(0).qty==5&&
                delayedQueue.queuedBytes()==4096,
                "queue-full session attempt mutated inventory");
            delayedQueue.drainTo(new java.io.ByteArrayOutputStream(),4096);
            delayedGuard.revokeAndAwait();
            denied(()->RecipeWorldOwnedAdapter.publishSession(waiting),
                "RECIPE_SESSION_WRITER_REVOKED");
            ok(delayedQueue.queuedBytes()==0&&
                delayed.bank().inventoryAt(0).qty==5,
                "revocation retried a formerly backpressured request");
            // A revoked writer cannot be re-minted for this same owner
            // generation; only a fresh World registration can claim again.
            denied(()->new RecipeSessionWriterGuard(
                    world,delayed,delayedGeneration,delayedWriter),
                "RECIPE_SESSION_WRITER_GENERATION_ALREADY_CLAIMED");
            ok(world.unregisterPlayer(delayed,delayedGeneration),
                "backpressure owner cleanup failed");
            long rejoined=world.registerPlayer(
                delayed,"recipe_session_backpressure");
            ok(rejoined>delayedGeneration,
                "re-registration did not advance World generation");
            denied(()->RecipeWorldOwnedAdapter.publishSession(waiting),
                "RECIPE_WORLD_OWNER_GENERATION_NOT_CURRENT");
            RecipeSessionWriterGuard renewed=
                new RecipeSessionWriterGuard(
                    world,delayed,rejoined,delayedWriter);
            RecipeWorldOwnedAdapter.SessionPrepared fresh=prepare(renewed);
            ok(RecipeWorldOwnedAdapter.publishSession(fresh)==
                    ServerPacketWriter.RecoverablePacketResult.COMMITTED&&
                delayed.bank().inventoryAt(0).qty==1&&
                delayedQueue.queuedPackets()==1,
                "fresh generation could not commit after revoked request");
            renewed.revokeAndAwait();
            ok(world.unregisterPlayer(delayed,rejoined),
                "rejoined backpressure owner cleanup failed");

            // Foreign-World construction must reject without consuming a
            // registered player's one allowed generation claim.
            World foreignWorld=world();
            try{
                WorldPlayer foreignPlayer=new WorldPlayer();
                seed(foreignPlayer);
                long foreignGeneration=foreignWorld.registerPlayer(
                    foreignPlayer,"recipe_session_foreign_world");
                ServerPacketWriter foreignOwnedWriter=writer(
                    new OutboundPacketQueue(4096));
                denied(()->new RecipeSessionWriterGuard(
                        world,foreignPlayer,foreignGeneration,
                        foreignOwnedWriter),
                    "RECIPE_WORLD_OWNER_GENERATION_NOT_CURRENT");
                RecipeSessionWriterGuard rightful=
                    new RecipeSessionWriterGuard(
                        foreignWorld,foreignPlayer,foreignGeneration,
                        foreignOwnedWriter);
                ok(rightful.activeForTest(),
                    "foreign-world denial consumed rightful witness");
                rightful.revokeAndAwait();
                ok(foreignWorld.unregisterPlayer(
                    foreignPlayer,foreignGeneration),
                    "foreign-owner cleanup failed");
            }finally{
                foreignWorld.close();
            }

            WorldPlayer terminalPlayer=new WorldPlayer();
            seed(terminalPlayer);
            long terminalGen=world.registerPlayer(
                terminalPlayer,"recipe_session_terminal");
            OutboundPacketQueue terminalQueue=new OutboundPacketQueue(4096);
            ServerPacketWriter terminalWriter=writer(terminalQueue);
            // A writer already dead before binding may not consume the
            // only session writer claim of this registered generation.
            ServerPacketWriter initiallyDead=writer(
                new OutboundPacketQueue(4096));
            initiallyDead.markTerminal();
            denied(()->new RecipeSessionWriterGuard(
                    world,terminalPlayer,terminalGen,initiallyDead),
                "RECIPE_TERMINAL_WRITER_CANNOT_CLAIM");
            RecipeSessionWriterGuard terminal=
                new RecipeSessionWriterGuard(
                    world,terminalPlayer,terminalGen,terminalWriter);
            RecipeWorldOwnedAdapter.SessionPrepared terminalReady=
                prepareMixed(terminal);
            terminalWriter.markTerminal();
            boolean terminalDenied=false;
            try{
                RecipeWorldOwnedAdapter.publishSession(terminalReady);
            }catch(IOException expected){
                terminalDenied=String.valueOf(expected.getMessage())
                    .contains("terminal recipe session writer");
            }
            ok(terminalDenied&&terminalQueue.queuedBytes()==0&&
                terminalPlayer.bank().inventoryAt(0).qty==5&&
                terminalPlayer.bank().inventoryAt(1).itemId==200&&
                !terminal.activeForTest(),
                "terminal writer leaked products or left active authority");
            denied(()->prepareMixed(terminal),
                "RECIPE_SESSION_WRITER_REVOKED");
            denied(()->new RecipeSessionWriterGuard(
                    world,terminalPlayer,terminalGen,
                    writer(new OutboundPacketQueue(4096))),
                "RECIPE_SESSION_WRITER_GENERATION_ALREADY_CLAIMED");
            terminal.revokeAndAwait();
            ok(world.unregisterPlayer(terminalPlayer,terminalGen),
                "terminal owner cleanup failed");

            revocationWaitsForGuardedAction(world);
            revocationPublicationRaces(world);
            concurrentWitnessClaims(world);
        }finally{
            world.close();
        }
        System.out.println("RECIPE_SESSION_WRITER_GUARD_PASS"+
            " exactWriterDenied=true foreignPlayerDenied=true"+
            " directUnownedCallDenied=true terminalBindDenied=true"+
            " terminalRevokesWitness=true"+
            " sameGenerationRebindDenied=true"+
            " loginLogoutVolatileWitness=true"+
            " foreignWorldWitnessClaimDenied=true"+
            " revokedPrepareDenied=true revokedPublicationDenied=true"+
            " backpressureRevocationDenied=true terminalWriterDenied=true"+
            " inFlightRevocationBarrier=true"+
            " revocationPublicationRaces=64"+
            " concurrentOneTimeWitnessClaims=64"+
            " liveRecipeRegistered=false durableAccountEpoch=false");
    }

    private static void concurrentWitnessClaims(World world)
        throws Exception{
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try{
            for(int trial=0;trial<64;trial++){
                WorldPlayer player=new WorldPlayer();
                seed(player);
                long generation=world.registerPlayer(
                    player,"recipe_unique_claim_"+trial);
                ServerPacketWriter firstWriter=writer(
                    new OutboundPacketQueue(4096));
                ServerPacketWriter secondWriter=writer(
                    new OutboundPacketQueue(4096));
                CountDownLatch ready=new CountDownLatch(2);
                CountDownLatch begin=new CountDownLatch(1);
                java.util.concurrent.atomic.AtomicReference<
                    RecipeSessionWriterGuard> winner=
                    new java.util.concurrent.atomic.AtomicReference<>();
                java.util.concurrent.Callable<String> a=()->{
                    ready.countDown();begin.await();
                    try{
                        RecipeSessionWriterGuard guard=
                            new RecipeSessionWriterGuard(
                                world,player,generation,firstWriter);
                        winner.set(guard);
                        return "CLAIMED";
                    }catch(IllegalStateException expected){
                        return String.valueOf(expected.getMessage());
                    }
                };
                java.util.concurrent.Callable<String> b=()->{
                    ready.countDown();begin.await();
                    try{
                        RecipeSessionWriterGuard guard=
                            new RecipeSessionWriterGuard(
                                world,player,generation,secondWriter);
                        winner.set(guard);
                        return "CLAIMED";
                    }catch(IllegalStateException expected){
                        return String.valueOf(expected.getMessage());
                    }
                };
                Future<String> first=workers.submit(a);
                Future<String> second=workers.submit(b);
                ok(ready.await(5,TimeUnit.SECONDS),
                    "concurrent claim contenders not ready "+trial);
                begin.countDown();
                String left=first.get(5,TimeUnit.SECONDS),
                    right=second.get(5,TimeUnit.SECONDS);
                ok(("CLAIMED".equals(left)^"CLAIMED".equals(right))&&
                    (left.contains("GENERATION_ALREADY_CLAIMED")||
                     right.contains("GENERATION_ALREADY_CLAIMED"))&&
                    winner.get()!=null,
                    "simultaneous witness claims both acquired "+trial+
                    " a="+left+" b="+right);
                winner.get().revokeAndAwait();
                denied(()->new RecipeSessionWriterGuard(
                        world,player,generation,firstWriter),
                    "RECIPE_SESSION_WRITER_GENERATION_ALREADY_CLAIMED");
                ok(world.unregisterPlayer(player,generation),
                    "claimed generation cleanup failed "+trial);
                long renewed=world.registerPlayer(
                    player,"recipe_unique_claim_"+trial);
                ok(renewed>generation,
                    "new World generation did not advance "+trial);
                RecipeSessionWriterGuard fresh=
                    new RecipeSessionWriterGuard(
                        world,player,renewed,firstWriter);
                fresh.revokeAndAwait();
                ok(world.unregisterPlayer(player,renewed),
                    "renewed witness owner cleanup failed "+trial);
            }
        }finally{
            workers.shutdownNow();
        }
    }

    private static void revocationPublicationRaces(World world)
        throws Exception{
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try{
            for(int trial=0;trial<64;trial++){
                WorldPlayer player=new WorldPlayer();
                seed(player);
                long generation=world.registerPlayer(
                    player,"recipe_guard_revoke_race_"+trial);
                OutboundPacketQueue queue=new OutboundPacketQueue(4096);
                ServerPacketWriter writer=writer(queue);
                RecipeSessionWriterGuard guard=
                    new RecipeSessionWriterGuard(
                        world,player,generation,writer);
                RecipeWorldOwnedAdapter.SessionPrepared prepared=
                    prepare(guard);
                CountDownLatch ready=new CountDownLatch(2);
                CountDownLatch start=new CountDownLatch(1);
                Future<String> publication=workers.submit(()->{
                    ready.countDown();start.await();
                    try{
                        return String.valueOf(
                            RecipeWorldOwnedAdapter.publishSession(prepared));
                    }catch(IllegalStateException expected){
                        return String.valueOf(expected.getMessage());
                    }
                });
                Future<?> revocation=workers.submit(()->{
                    ready.countDown();
                    try{
                        start.await();
                    }catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                            "RECIPE_GUARD_RACE_INTERRUPTED",interrupted);
                    }
                    guard.revokeAndAwait();
                });
                ok(ready.await(5,TimeUnit.SECONDS),
                    "publication and revoke did not reach start gate "+trial);
                start.countDown();
                String outcome=publication.get(5,TimeUnit.SECONDS);
                revocation.get(5,TimeUnit.SECONDS);
                boolean admitted="COMMITTED".equals(outcome);
                ok(admitted||outcome.contains("RECIPE_SESSION_WRITER_REVOKED"),
                    "unexpected revoke/publish outcome "+trial+
                    " result="+outcome);
                ok(queue.queuedPackets()==(admitted?1:0)&&
                    player.bank().inventoryAt(0).qty==
                        (admitted?1:5)&&
                    ((player.bank().inventoryAt(3)==null)==!admitted),
                    "revocation race packet/inventory mismatch "+trial);
                denied(()->RecipeWorldOwnedAdapter.publishSession(prepared),
                    "RECIPE_SESSION_WRITER_REVOKED");
                ok(world.unregisterPlayer(player,generation),
                    "revocation race owner cleanup failed "+trial);
            }
        }finally{
            workers.shutdownNow();
        }
    }

    private static void revocationWaitsForGuardedAction(World world)
        throws Exception{
        WorldPlayer player=new WorldPlayer();
        seed(player);
        long generation=world.registerPlayer(
            player,"recipe_guard_quiescence");
        ServerPacketWriter writer=writer(new OutboundPacketQueue(4096));
        RecipeSessionWriterGuard guard=
            new RecipeSessionWriterGuard(world,player,generation,writer);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch inside=new CountDownLatch(1),
            exit=new CountDownLatch(1);
        try{
            Future<?> action=workers.submit(()->{
                try{
                    world.withOpenPlayerMutationOwnershipIfCurrent(
                        player,generation,
                        ()->guard.runBound(
                            world,player,generation,writer,()->{
                                inside.countDown();
                                if(!exit.await(5,TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "guarded action release timed out");
                                return null;
                            }));
                }catch(Exception failure){
                    throw new RuntimeException(failure);
                }
            });
            ok(inside.await(5,TimeUnit.SECONDS),
                "test action never entered session guard");
            CountDownLatch revokerStarted=new CountDownLatch(1);
            Future<?> revocation=workers.submit(()->{
                revokerStarted.countDown();
                guard.revokeAndAwait();
            });
            ok(revokerStarted.await(5,TimeUnit.SECONDS),
                "revoker never started");
            try{
                revocation.get(100,TimeUnit.MILLISECONDS);
                throw new AssertionError(
                    "revocation returned before guarded action exited");
            }catch(TimeoutException expected){
                // Revocation is blocked by the guarded action monitor.
            }
            exit.countDown();
            action.get(5,TimeUnit.SECONDS);
            revocation.get(5,TimeUnit.SECONDS);
            ok(!guard.activeForTest(),
                "revoked witness still active");
            denied(()->guard.runBound(
                world,player,generation,writer,()->null),
                "RECIPE_SESSION_WRITER_REVOKED");
            ok(world.unregisterPlayer(player,generation),
                "quiescence fixture cleanup failed");
        }finally{
            exit.countDown();
            workers.shutdownNow();
        }
    }
}
