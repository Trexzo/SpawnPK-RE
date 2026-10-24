package spk.local;

import java.util.Objects;

/**
 * R51: pure, deliberately UNTRUSTED rehearsal of a single account HEAD
 * state machine. No on-disk write, lock, login, checkpoint or grant
 * integration. Every state stays non-admitting, including COMMITTED.
 *
 * It models optimistic sequence and World-generation rejection only:
 * constructing a matching token or state is NOT authenticated ownership
 * and cannot authorize post-crash account release.
 */
final class LocalAccountEpochTransitionModel {
    enum Phase { COMMITTED, ACTIVE, FINAL_PENDING, REVIEW }
    static final class Head {
        final String account;
        final long epoch;
        final long generation;
        final long sequence;
        final String ownerNonce;
        final String digest;
        final Phase phase;
        final boolean admissionAuthorized=false;
        final boolean releaseAuthorized=false;
        private Head(String a,long e,long g,long s,String n,String d,Phase p){
            account=a;epoch=e;generation=g;sequence=s;
            ownerNonce=n;digest=d;phase=p;
        }
    }
    static final class UntrustedOwner {
        final String account;
        final long epoch;
        final long generation;
        final String nonce;
        private UntrustedOwner(String a,long e,long g,String n){
            account=a;epoch=e;generation=g;nonce=n;
        }
    }

    private LocalAccountEpochTransitionModel(){}

    private static void require(boolean ok,String code){
        if(!ok)throw new IllegalStateException("R51_"+code);
    }
    private static String digest(String value){
        require(value!=null&&value.matches("[0-9a-f]{64}"),
            "INVALID_SNAPSHOT_DIGEST");
        return value;
    }
    private static String account(String value){
        require(value!=null&&value.matches("[a-z0-9_-]{1,64}"),
            "INVALID_ACCOUNT");
        return value;
    }
    private static String nonce(String value){
        require(value!=null&&value.matches("[a-z0-9_-]{1,64}"),
            "INVALID_OWNER_NONCE");
        return value;
    }
    // This SEED is caller-manufacturable fixture data, not a read from
    // a durable authenticated HEAD or a legacy profile migration.
    static Head untrustedCommittedFixture(
        String account,long epoch,long sequence,String digest
    ){
        require(epoch>0&&sequence>=0,"INVALID_SEED_SEQUENCE");
        return new Head(account(account),epoch,0L,sequence,"",
            digest(digest),Phase.COMMITTED);
    }
    static UntrustedOwner untrustedOwnerFixture(
        String account,long epoch,long generation,String nonce
    ){
        require(epoch>0&&generation>0,"INVALID_OWNER_GENERATION");
        return new UntrustedOwner(
            account(account),epoch,generation,nonce(nonce));
    }

    private static void currentAndExpected(
        Head current,Head observed
    ){
        require(current!=null&&observed!=null,"MISSING_HEAD");
        // An observed matching sequence is insufficient when another
        // owner or a different postimage is already current.
        require(current.phase==observed.phase&&
                current.epoch==observed.epoch&&
                current.sequence==observed.sequence&&
                current.generation==observed.generation&&
                Objects.equals(current.account,observed.account)&&
                Objects.equals(current.ownerNonce,observed.ownerNonce)&&
                Objects.equals(current.digest,observed.digest),
            "STALE_OR_REPLACED_HEAD");
    }
    private static void currentOwner(
        Head current,UntrustedOwner owner
    ){
        require(current!=null&&owner!=null,"MISSING_OWNER_OR_HEAD");
        require(current.account.equals(owner.account)&&
                current.epoch==owner.epoch&&
                current.generation==owner.generation&&
                current.ownerNonce.equals(owner.nonce),
            "FOREIGN_OR_STALE_OWNER");
    }
    static Head proposeClaim(
        Head current,Head observed,UntrustedOwner next
    ){
        currentAndExpected(current,observed);
        require(current.phase==Phase.COMMITTED,"UNRESOLVED_STATE_NO_CLAIM");
        require(next!=null&&current.account.equals(next.account)&&
                current.epoch<Long.MAX_VALUE&&
                next.epoch==current.epoch+1L&&next.generation>0L,
            "NEXT_EPOCH_OR_ACCOUNT_INVALID");
        require(current.sequence<Long.MAX_VALUE,"SEQUENCE_OVERFLOW");
        // The earlier snapshot remains the reference until a real
        // World-owned checkpoint is verified by a future protocol.
        return new Head(current.account,next.epoch,next.generation,
            current.sequence+1L,next.nonce,current.digest,Phase.ACTIVE);
    }
    static Head proposeCheckpoint(
        Head current,Head observed,UntrustedOwner owner,
        String nextSnapshotDigest
    ){
        currentAndExpected(current,observed);
        currentOwner(current,owner);
        require(current.phase==Phase.ACTIVE,"CHECKPOINT_NOT_ACTIVE");
        require(current.sequence<Long.MAX_VALUE,"SEQUENCE_OVERFLOW");
        return new Head(current.account,current.epoch,current.generation,
            current.sequence+1L,current.ownerNonce,
            digest(nextSnapshotDigest),Phase.ACTIVE);
    }
    static Head proposeFinalPending(
        Head current,Head observed,UntrustedOwner owner,
        String finalSnapshotDigest
    ){
        currentAndExpected(current,observed);
        currentOwner(current,owner);
        require(current.phase==Phase.ACTIVE,"FINAL_NOT_ACTIVE");
        require(current.sequence<Long.MAX_VALUE,"SEQUENCE_OVERFLOW");
        // FINAL_PENDING is never reusable/admitting: persistence,
        // content verification, force and release are not implemented.
        return new Head(current.account,current.epoch,current.generation,
            current.sequence+1L,current.ownerNonce,
            digest(finalSnapshotDigest),Phase.FINAL_PENDING);
    }
    static Head proposeReview(
        Head current,Head observed
    ){
        currentAndExpected(current,observed);
        require(current.phase!=Phase.REVIEW,"REVIEW_IRREVERSIBLE");
        require(current.sequence<Long.MAX_VALUE,"SEQUENCE_OVERFLOW");
        return new Head(current.account,current.epoch,current.generation,
            current.sequence+1L,current.ownerNonce,current.digest,
            Phase.REVIEW);
    }
}
