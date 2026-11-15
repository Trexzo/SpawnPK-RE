package spk.local;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R75: real World actor, actual WorldRealtimeQueue and canonical pet-NPC
 * registry. Deterministic drain avoids timing-dependent gameplay tests.
 * No socket, production account, persistent IO, or live gate installation.
 */
public final class R75OfflineRealtimePetIngressTest {
    public static void main(String[] args)throws Exception{
        try(World world=World.isolatedForTest(60_000L)){
            WorldPlayer owner=new WorldPlayer();
            long generation=world.registerPlayer(owner,"opensrc");
            WorldPlayer unrelated=new WorldPlayer();
            long otherGeneration=world.registerPlayer(unrelated,"unrelated");
            R74OfflineCommandIngressFence commands=
                new R74OfflineCommandIngressFence(world);
            commands.installForTest();
            R75OfflineCrossIngressFence cross=
                new R75OfflineCrossIngressFence(
                    world,owner,generation,commands
                );
            cross.installForTest();

            try{
                cross.installForTest();
                throw new AssertionError(
                    "R75 duplicate cross-ingress installation accepted"
                );
            }catch(IllegalStateException denied){
                require(denied.getMessage().contains("already installed"),
                    "R75 duplicate refusal mismatch "+denied);
            }

            world.start();
            AtomicInteger realtimeRuns=new AtomicInteger();
            AtomicInteger unrelatedRuns=new AtomicInteger();
            WorldNpc[] main={null};
            world.submitAndWait(owner,generation,()->{
                main[0]=world.petNpcs().ensureMain(
                    owner.id(),131,3200,3200,0,0
                );
                require(main[0]!=null &&
                    world.petNpcs().ownerActorCount(owner.id())==1,
                    "R75 active pet main did not spawn before quiescence");

                world.scheduleRealtime(
                    1L,owner,generation,
                    ()->realtimeRuns.incrementAndGet()
                );
                world.realtime().runDue(Long.MAX_VALUE);
                require(realtimeRuns.get()==1,
                    "R75 realtime allowed callback did not execute");

                // This callback is admitted BEFORE the World actor is
                // quiesced, but execution is delayed until AFTER the
                // quiescence boundary. It must not run.
                world.scheduleRealtime(
                    Long.MAX_VALUE,owner,generation,
                    ()->realtimeRuns.incrementAndGet()
                );
                commands.quiesceInsideWorldActor(owner,generation);
            },10_000L);

            require(commands.isQuiesced(owner,generation),
                "R75 World actor not marked quiesced");
            require(world.realtime().size()==1,
                "R75 delayed realtime callback missing");

            int drained=world.realtime().runDue(Long.MAX_VALUE);
            require(drained==1 && realtimeRuns.get()==1 &&
                    world.realtime().size()==0,
                "R75 old queued realtime handler executed after quiescence");

            expectIllegal(()->world.scheduleRealtime(
                0L,owner,generation,
                ()->realtimeRuns.incrementAndGet()
            ),"R75_REALTIME_QUIESCED");

            require(world.realtime().size()==0,
                "R75 realtime producer bypassed schedule denial");

            int x=main[0].x(),y=main[0].y();
            expectIllegal(()->world.petNpcs().moveMain(
                owner.id(),x+1,y+1,0
            ),"R75_PET_QUIESCED");
            expectIllegal(()->world.petNpcs().ensureMain(
                owner.id(),131,x+2,y+2,0,0
            ),"R75_PET_QUIESCED");
            expectIllegal(()->world.petNpcs().ensureMini(
                owner.id(),132,x,y,0,0
            ),"R75_PET_QUIESCED");
            expectIllegal(()->world.petNpcs().removeMini(
                owner.id()
            ),"R75_PET_QUIESCED");
            require(world.petNpcs().ownerActorCount(owner.id())==1 &&
                    main[0].x()==x && main[0].y()==y,
                "R75 quiesced pet state mutated via direct service");

            // R75 gate is scoped to the named player/generation, not
            // an indiscriminate global gameplay freeze.
            world.scheduleRealtime(
                Long.MAX_VALUE,unrelated,otherGeneration,
                ()->unrelatedRuns.incrementAndGet()
            );
            world.realtime().runDue(Long.MAX_VALUE);
            require(unrelatedRuns.get()==1,
                "R75 unrelated owned realtime callback was blocked");

            world.petNpcs().ensureMain(
                unrelated.id(),135,3300,3300,0,0
            );
            require(world.petNpcs().ownerActorCount(
                    unrelated.id())==1,
                "R75 unrelated owner's pet was incorrectly quiesced");

            // R74 command submission guard still applies; realtime
            // and pet ingress are additions, not replacements.
            try{
                world.submit(
                    owner,generation,()->realtimeRuns.incrementAndGet()
                ).get(10L,TimeUnit.SECONDS);
                throw new AssertionError(
                    "R75 post-quiescence World command ran"
                );
            }catch(ExecutionException|CancellationException denied){
                Throwable cause=denied instanceof ExecutionException
                    ?denied.getCause():denied;
                require(cause.getMessage().contains("R74_COMMAND_QUIESCED"),
                    "R75 wrong World inbox refusal "+cause);
            }

            // Pet actor removal must remain AVAILABLE during the
            // legitimate unregister cleanup, not blocked by pet gate.
            require(world.unregisterPlayer(owner,generation),
                "R75 owner unregister failed");
            require(world.petNpcs().ownerActorCount(owner.id())==0 &&
                    world.petNpcs().ownerActorCount(
                        unrelated.id())==1,
                "R75 pet cleanup was blocked by quiescence gate");

            System.out.println(
                "R75_OFFLINE_REALTIME_PET_INGRESS_PASS "+
                "preQuiescenceRealtimeAllowed=true "+
                "queuedOldRealtimeDeniedAtExecution=true "+
                "postQuiescenceRealtimeScheduleDenied=true "+
                "directPetMoveAndSpawnDenied=true "+
                "petStatePreserved=true "+
                "unrelatedOwnerUnaffected=true "+
                "R74CommandFenceStillApplies=true "+
                "petUnregisterCleanupUnaffected=true "+
                "noProductionHooks=true"
            );
        }
    }

    @FunctionalInterface private interface Action{
        void run()throws Exception;
    }

    private static void expectIllegal(
        Action action,String expected
    )throws Exception{
        try{
            action.run();
            throw new AssertionError(
                "expected R75 refusal "+expected
            );
        }catch(IllegalStateException denied){
            require(denied.getMessage().contains(expected),
                "wrong R75 denial "+denied);
        }
    }

    private static void require(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }

    private R75OfflineRealtimePetIngressTest(){}
}
