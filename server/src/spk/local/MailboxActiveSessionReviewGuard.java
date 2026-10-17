package spk.local;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * G21.37: terminate an already-online local session if a durable
 * Mailbox REVIEW_REQUIRED_NO_GRANT sidecar appears after login.
 *
 * Checks are explicit from the session socket thread, NEVER from a
 * World tick or persistence writer. This is a marker metadata check,
 * not a repository account load, reward receipt, or claim decision.
 * At most one periodic check per interval; boundary checks bypass the
 * throttle before login-success or World tick-target publication.
 */
final class MailboxActiveSessionReviewGuard {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2137_ONLINE_NEGATIVE_FENCE_REVOKE";
    static final long POLL_INTERVAL_NANOS=
        TimeUnit.SECONDS.toNanos(1);

    interface FencePresence {
        boolean present(String account)throws IOException;
    }

    private final String account;
    private final boolean enabled;
    private final FencePresence presence;
    private boolean checked;
    private long lastCheckNanos;

    static MailboxActiveSessionReviewGuard forSession(
        WorldPlayerPersistence persistence,
        String account,boolean persistent
    ){
        Objects.requireNonNull(persistence,"persistence");
        return new MailboxActiveSessionReviewGuard(
            account,
            persistent&&persistence.supportsDurableMailboxReviewFence(),
            persistence::hasDurableMailboxReviewFence
        );
    }

    MailboxActiveSessionReviewGuard(
        String account,boolean enabled,FencePresence presence
    ){
        this.account=Objects.requireNonNull(account,"account");
        this.enabled=enabled;
        this.presence=Objects.requireNonNull(presence,"presence");
    }

    /**
     * Explicit last-chance boundaries cannot be skipped because a
     * previous periodic check happened shortly beforehand.
     */
    void requireAtBoundary()throws IOException{
        if(!enabled)return;
        inspectPresence();
        checked=true;
        lastCheckNanos=System.nanoTime();
    }

    /**
     * Time source is supplied by session, deterministic fixture friendly.
     * No private threads, busy loop, repeating task or file scans.
     */
    void poll(long nowNanos)throws IOException{
        if(!enabled)return;
        if(checked&&
           nowNanos-lastCheckNanos<POLL_INTERVAL_NANOS)
            return;
        inspectPresence();
        checked=true;
        lastCheckNanos=nowNanos;
    }

    private void inspectPresence()throws IOException{
        final boolean found;
        try{
            found=presence.present(account);
        }catch(IOException|RuntimeException failed){
            throw new IOException(
                "G21.37 MAILBOX_LIVE_REVIEW_STATUS_FAILED"+
                " account="+account+" action=TERMINATE_SESSION",
                failed
            );
        }
        if(found)
            throw new IOException(
                "G21.37 MAILBOX_LIVE_REVIEW_FENCE"+
                " account="+account+" action=TERMINATE_SESSION"
            );
    }

    private MailboxActiveSessionReviewGuard(){
        throw new AssertionError("not instantiable");
    }
}
