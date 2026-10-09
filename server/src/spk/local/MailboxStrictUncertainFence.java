package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * G21.47: write-once NEGATIVE account-admission sidecar for a strict
 * PREPARED save which may already have changed account bytes, but
 * cannot return a confirmed owner-congruent strict checkpoint receipt.
 *
 * Presence, even malformed content or a symlink, is a permanent veto
 * until a separately authorized manual repair. There is NO automatic
 * release, inventory grant, replay, rollback, or reward settlement.
 *
 * armInsidePublicationLock MUST be invoked inside the SAME account's
 * MailboxAccountPublicationCoordinator critical section AFTER strict
 * replacement is known to be uncertain; never acquire the nested lock.
 * Unsupported hard links/directory force fail closed. This is NOT a
 * write-ahead intent: a process crash before sidecar publication can
 * still leave the old/new account file without this marker.
 */
final class MailboxStrictUncertainFence {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2147_STRICT_UNCERTAIN_NEGATIVE_NO_GRANT";
    static final String SUFFIX=".g2147-strict-uncertain";
    private static final String FORMAT=
        "SPK-G2147-STRICT-UNCERTAIN-NO-GRANT-V1";

    private final FilePlayerRepository.PathResolver resolver;

    MailboxStrictUncertainFence(
        FilePlayerRepository.PathResolver resolver
    ){
        this.resolver=Objects.requireNonNull(resolver,"resolver");
    }

    Path fencePath(String account){
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.47 noncanonical account"
            );
        Path file=Objects.requireNonNull(
            resolver.resolve(account),"G21.47 account file"
        ).toAbsolutePath().normalize();
        return file.resolveSibling(
            file.getFileName().toString()+SUFFIX
        );
    }

    boolean present(String account)throws IOException{
        try{
            Files.readAttributes(
                fencePath(account),BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
            );
            return true;
        }catch(NoSuchFileException missing){
            return false;
        }
    }

    /**
     * Strict caller contract: same-account exclusive publication lock
     * ALREADY held, after possible account replacement. Never return
     * a positive transaction/durability/grant receipt.
     */
    void armInsidePublicationLock(
        String account,String preparedSha256
    )throws IOException{
        if(preparedSha256==null||
           !preparedSha256.matches("[0-9a-f]{64}"))
            throw new IOException(
                "G21.47 invalid canonical snapshot fingerprint"
            );
        Path fence=fencePath(account);
        Path parent=Objects.requireNonNull(
            fence.getParent(),"G21.47 parent"
        );
        if(present(account))
            throw new IOException(
                "G21.47 review sidecar already exists; "+
                "no clobber and no automatic release"
            );

        String record=FORMAT+"\n"+
            account+"\n"+preparedSha256+"\n"+
            "MANUAL_REVIEW_NO_GRANT\n";
        byte[] bytes=record.getBytes(StandardCharsets.US_ASCII);
        Files.createDirectories(parent);
        Path temp=Files.createTempFile(
            parent,fence.getFileName().toString()+".write-",".tmp"
        );
        try{
            try(FileChannel channel=FileChannel.open(
                    temp,StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer buffer=ByteBuffer.wrap(bytes);
                while(buffer.hasRemaining())
                    channel.write(buffer);
                channel.force(true);
            }
            // Hard-link publication is an exclusive name creation.
            // Never use REPLACE_EXISTING or a non-atomic copy fallback.
            Files.createLink(fence,temp);
            // Once linked, even if metadata force fails, the fence may
            // already be visible. It MUST NOT be deleted or retried
            // automatically; all downstream readers veto on presence.
            try(FileChannel directory=FileChannel.open(
                    parent,StandardOpenOption.READ)){
                directory.force(true);
            }
        }finally{
            Files.deleteIfExists(temp);
        }
    }

    private MailboxStrictUncertainFence(){}
}
