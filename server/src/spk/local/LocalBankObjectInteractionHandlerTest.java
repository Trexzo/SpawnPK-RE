package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalBankObjectInteractionHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();
        MovementState movement=player.movement();

        LocalBankObjectInteractionHandler h=
            new LocalBankObjectInteractionHandler(bank,movement);

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        ObjectInteraction immediate=new ObjectInteraction(
            132,
            BankState.BANK_OBJECT_ID,
            movement.x()+1,
            movement.y()
        );

        int before=wire.size();
        String opened=h.handle(immediate,w);
        if(opened==null||
           !opened.contains("V5_BANK_OPEN")||
           !opened.contains("action=OPENED_ADJACENT_IMMEDIATE"))
            throw new AssertionError("immediate bank route="+opened);
        if(!bank.isOpen())
            throw new AssertionError("bank not opened");
        if(wire.size()<=before)
            throw new AssertionError("bank open emitted no packets");

        ObjectInteraction nonBank=new ObjectInteraction(
            132,12345,movement.x(),movement.y());
        String unknown=h.handle(nonBank,w);
        if(unknown==null||
           !unknown.contains("action=DECODED_NOT_IMPLEMENTED"))
            throw new AssertionError("non-bank fail-closed route="+unknown);
        if(h.hasPending())
            throw new AssertionError("non-bank object should clear stale pending state");

        ObjectInteraction deferred=new ObjectInteraction(
            132,
            BankState.BANK_OBJECT_ID,
            movement.x()+2,
            movement.y()
        );
        String queued=h.handle(deferred,w);
        if(queued==null||
           !queued.contains("action=DEFERRED_UNTIL_ADJACENT"))
            throw new AssertionError("deferred bank route="+queued);
        if(!h.hasPending())
            throw new AssertionError("deferred request not retained");

        if(movement.queued()<=0)
            throw new AssertionError(
                "server-owned approach route was not queued");

        String whileQueued=
            h.tick(System.currentTimeMillis(),w);
        if(whileQueued!=null)
            throw new AssertionError(
                "pending interaction resolved before queued approach completed="+
                whileQueued);

        movement.clearQueuedPath();

        String cancelled=h.tick(System.currentTimeMillis(),w);
        if(cancelled==null||
           !cancelled.contains("action=CANCELLED_PATH_ENDED_NOT_ADJACENT"))
            throw new AssertionError("path-ended cancellation="+cancelled);
        if(h.hasPending())
            throw new AssertionError("cancelled request still pending");

        System.out.println(
            "LOCAL_BANK_OBJECT_HANDLER_PASS immediateOpen=true nonBankFailClosed=true deferredOwnership=true serverApproachQueued=true pathEndCancel=true");
    }
}
