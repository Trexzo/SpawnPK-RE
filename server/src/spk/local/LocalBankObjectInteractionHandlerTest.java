package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalBankObjectInteractionHandlerTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();

        try{
            world.registerPlayer(
                player,
                "bank-object-handler"
            );
            world.start();

            BankState bank=player.bank();
            MovementState movement=player.movement();

            LocalBankObjectInteractionHandler h=
                new LocalBankObjectInteractionHandler(
                    bank,
                    movement,
                    world.content()
                );

            final int[] rootPublications={0};
            h.installRootOwner(
                action->{
                    rootPublications[0]++;
                    return action.open();
                }
            );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter w=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{1,2,3,4}
                    )
                );

            ObjectInteraction immediate=
                new ObjectInteraction(
                    132,
                    BankState.BANK_OBJECT_ID,
                    movement.x()+1,
                    movement.y()
                );

            int before=wire.size();
            String opened=
                onWorld(
                    world,
                    player,
                    ()->h.handle(
                        immediate,
                        w
                    )
                );

            if(opened==null||
               !opened.contains("V5_BANK_OPEN")||
               !opened.contains(
                   "action=OPENED_ADJACENT_IMMEDIATE"))
                throw new AssertionError(
                    "immediate bank route="+
                    opened
                );

            if(!bank.isOpen())
                throw new AssertionError(
                    "bank not opened"
                );

            if(wire.size()<=before)
                throw new AssertionError(
                    "bank open emitted no packets"
                );

            if(rootPublications[0]!=1)
                throw new AssertionError(
                    "immediate bank open bypassed root owner count="+
                    rootPublications[0]
                );

            ObjectInteraction nonBank=
                new ObjectInteraction(
                    132,
                    12345,
                    movement.x(),
                    movement.y()
                );

            String unknown=
                onWorld(
                    world,
                    player,
                    ()->h.handle(
                        nonBank,
                        w
                    )
                );

            if(unknown==null||
               !unknown.contains(
                   "action=DECODED_NOT_IMPLEMENTED"))
                throw new AssertionError(
                    "non-bank fail-closed route="+
                    unknown
                );

            if(h.hasPending())
                throw new AssertionError(
                    "non-bank object should clear stale pending state"
                );

            ObjectInteraction deferred=
                new ObjectInteraction(
                    132,
                    BankState.BANK_OBJECT_ID,
                    movement.x()+2,
                    movement.y()
                );

            String queued=
                onWorld(
                    world,
                    player,
                    ()->h.handle(
                        deferred,
                        w
                    )
                );

            if(queued==null||
               !queued.contains(
                   "action=DEFERRED_UNTIL_ADJACENT"))
                throw new AssertionError(
                    "deferred bank route="+
                    queued
                );

            if(!h.hasPending())
                throw new AssertionError(
                    "deferred request not retained"
                );

            if(movement.queued()<=0)
                throw new AssertionError(
                    "server-owned approach route was not queued"
                );

            String whileQueued=
                onWorld(
                    world,
                    player,
                    ()->h.tick(
                        System.currentTimeMillis(),
                        w
                    )
                );

            if(whileQueued!=null)
                throw new AssertionError(
                    "pending interaction resolved before queued approach completed="+
                    whileQueued
                );

            movement.clearQueuedPath();

            String cancelled=
                onWorld(
                    world,
                    player,
                    ()->h.tick(
                        System.currentTimeMillis(),
                        w
                    )
                );

            if(cancelled==null||
               !cancelled.contains(
                   "action=CANCELLED_PATH_ENDED_NOT_ADJACENT"))
                throw new AssertionError(
                    "path-ended cancellation="+
                    cancelled
                );

            if(h.hasPending())
                throw new AssertionError(
                    "cancelled request still pending"
                );

            if(rootPublications[0]!=1)
                throw new AssertionError(
                    "non-opening bank paths entered root ownership count="+
                    rootPublications[0]
                );

            ObjectInteraction deferredOpen=
                new ObjectInteraction(
                    132,
                    BankState.BANK_OBJECT_ID,
                    movement.x()+2,
                    movement.y()
                );

            String queuedOpen=
                onWorld(
                    world,
                    player,
                    ()->h.handle(
                        deferredOpen,
                        w
                    )
                );

            if(queuedOpen==null||
               !queuedOpen.contains(
                   "action=DEFERRED_UNTIL_ADJACENT"))
                throw new AssertionError(
                    "deferred-open route="+
                    queuedOpen
                );

            MovementState.Tick approachStep=
                movement.advance();

            if(approachStep==null)
                throw new AssertionError(
                    "deferred-open approach produced no movement"
                );

            String openedAfterArrival=
                onWorld(
                    world,
                    player,
                    ()->h.tick(
                        System.currentTimeMillis(),
                        w
                    )
                );

            if(openedAfterArrival==null||
               !openedAfterArrival.contains(
                   "action=OPENED_AFTER_AUTHORITATIVE_ARRIVAL")||
               rootPublications[0]!=2)
                throw new AssertionError(
                    "deferred bank open bypassed root owner result="+
                    openedAfterArrival+
                    " count="+rootPublications[0]
                );

            System.out.println(
                "LOCAL_BANK_OBJECT_HANDLER_PASS "+
                "contentOwned=true "+
                "immediateOpen=true "+
                "immediateRootOwnership=true "+
                "nonBankFailClosed=true "+
                "deferredOwnership=true "+
                "deferredRootOwnership=true "+
                "serverApproachQueued=true "+
                "pathEndCancel=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static String onWorld(
        World world,
        WorldPlayer player,
        ThrowingString action
    )throws Exception{
        AtomicReference<String>
            result=new AtomicReference<>();
        AtomicReference<Throwable>
            failure=new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->{
                try{
                    result.set(
                        action.run()
                    );
                }catch(Throwable error){
                    failure.set(error);
                }
            },
            5_000L
        );

        if(failure.get()!=null)
            throw new AssertionError(
                "world action failed",
                failure.get()
            );

        return result.get();
    }

    @FunctionalInterface
    private interface ThrowingString {
        String run()throws Exception;
    }

    private LocalBankObjectInteractionHandlerTest(){}
}
