package spk.local;

import java.io.IOException;
import java.nio.file.Path;

/**
 * R52: opt-in COOPERATING-JVM publication gate for the UNWIRED R50
 * permanent negative HEAD. It deliberately uses the same stable
 * account session lock as G21.185, rather than another lock protocol.
 *
 * This does NOT authenticate a World owner, exclude nonparticipating
 * RAW writers, establish an atomic filesystem CAS or authorize
 * admission, repair, marker retirement, replay or final-save ACK.
 * The R50 HEAD stays irreversible and NONADMITTING even on success.
 */
final class LocalNegativeAccountHeadPublicationGate {
    private LocalNegativeAccountHeadPublicationGate(){}

    static LocalNegativeAccountHeadPrototype.NegativeObservation publish(
        Path account,String username,long generation,byte[] snapshot
    )throws IOException{
        return publish(account,username,generation,snapshot,stage->{});
    }

    static LocalNegativeAccountHeadPrototype.NegativeObservation publish(
        Path account,String username,long generation,byte[] snapshot,
        LocalNegativeAccountHeadPrototype.Cutpoint cutpoint
    )throws IOException{
        // Without both features, no session-lock IO or account-object
        // temp must start: partial participation is not exclusion.
        if(!Boolean.getBoolean(
                LocalCrossJvmAccountSessionLock.PROPERTY)||
           !Boolean.getBoolean(
                LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY))
            throw new IOException(
                "R52_NEGATIVE_HEAD_REQUIRES_CROSS_JVM_LOCK_CONTINUITY");
        if(account==null||cutpoint==null)
            throw new IOException("R52_NEGATIVE_HEAD_NULL_INPUT");

        // Hold the ORIGINAL stable session-lock inode across object
        // publication, HEAD creation, directory forces and readback.
        // Never delete the lock name after use.
        try(LocalCrossJvmAccountSessionLock.Handle lock=
                LocalCrossJvmAccountSessionLock.acquire(account)){
            lock.requireOriginalLockNameStillPresent();
            LocalNegativeAccountHeadPrototype.NegativeObservation result=
                LocalNegativeAccountHeadPrototype.publishNegative(
                    account,username,generation,snapshot,stage->{
                        lock.requireOriginalLockNameStillPresent();
                        cutpoint.reached(stage);
                        lock.requireOriginalLockNameStillPresent();
                    });
            lock.requireOriginalLockNameStillPresent();
            return result;
        }
    }
}
