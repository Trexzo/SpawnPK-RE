package spk.local;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R58 OFFLINE-ONLY account preflight. Scans a dedicated, finite journal
 * directory and identifies PREPARED trade intents involving either account.
 *
 * This is NOT wired to real login or trade saves. A CLEAR result ONLY means
 * no pair-journal evidence was found in this supplied directory at the time
 * of inspection; it does not authorize login, default-account creation,
 * autosave, logout save, or account recovery.
 */
final class R58TradePairLoginPreflight {
    static final int MAX_JOURNAL_ENTRIES=256;

    private static final Pattern INTENT_NAME=Pattern.compile(
        "trade-r57-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"+
        "[0-9a-f]{4}-[0-9a-f]{12})\\.properties"
    );
    private static final Pattern USERNAME=Pattern.compile(
        "[a-z][a-z0-9_]{0,31}"
    );

    enum Status {
        NO_PENDING_PAIR_EVIDENCE,
        BLOCK_PENDING_PAIR_INTENT,
        BLOCK_INDETERMINATE_JOURNAL
    }

    static final class Result {
        final Status status;
        final int examinedEntries;
        final int matchingIntents;
        final UUID firstMatchingTransaction;
        final String reasonCode;

        Result(Status status,int count,int matches,UUID transaction,
               String reason){
            this.status=status;
            examinedEntries=count;
            matchingIntents=matches;
            firstMatchingTransaction=transaction;
            reasonCode=reason;
        }

        /** A clear result is necessary but NEVER sufficient for login. */
        boolean canProceedToOtherLoginChecks(){
            return status==Status.NO_PENDING_PAIR_EVIDENCE;
        }

        boolean automaticRecoveryAttempted(){return false;}
        boolean accountSnapshotsOpened(){return false;}
    }

    /**
     * Only accepts a dedicated journal directory. Unknown entries, orphan
     * R16 stages, unreadable manifests, a missing directory, symlinked
     * ancestors, excessive entries or concurrent file changes cause a
     * global fail-closed result rather than guessed account ownership.
     */
    static Result inspect(Path directory,String requestedUsername){
        if(directory==null||requestedUsername==null)
            return blocked(0,0,null,"INVALID_INPUT");

        String username=requestedUsername.trim().toLowerCase(Locale.ROOT);
        if(!USERNAME.matcher(username).matches())
            return blocked(0,0,null,"INVALID_ACCOUNT");

        Path root=directory.toAbsolutePath().normalize();
        for(Path p=root;p!=null;p=p.getParent()){
            if(Files.isSymbolicLink(p))
                return blocked(0,0,null,"SYMLINK_DIRECTORY");
        }
        final BasicFileAttributes attributes;
        try{
            attributes=Files.readAttributes(
                root,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
            );
        }catch(IOException error){
            return blocked(0,0,null,"MISSING_OR_UNREADABLE_DIRECTORY");
        }
        if(!attributes.isDirectory())
            return blocked(0,0,null,"NOT_A_DIRECTORY");

        int entries=0,matches=0;
        UUID firstMatching=null;
        try(DirectoryStream<Path> children=Files.newDirectoryStream(root)){
            for(Path child:children){
                entries++;
                if(entries>MAX_JOURNAL_ENTRIES)
                    return blocked(entries,matches,firstMatching,
                        "JOURNAL_SCAN_LIMIT");

                String filename=child.getFileName().toString();
                Matcher match=INTENT_NAME.matcher(filename);
                if(!match.matches())
                    return blocked(entries,matches,firstMatching,
                        "UNKNOWN_OR_STAGED_ENTRY");

                UUID fileId;
                try{
                    fileId=UUID.fromString(match.group(1));
                }catch(IllegalArgumentException error){
                    return blocked(entries,matches,firstMatching,
                        "INVALID_FILE_TRANSACTION_ID");
                }
                if(!fileId.toString().equals(match.group(1)))
                    return blocked(entries,matches,firstMatching,
                        "NONCANONICAL_FILE_TRANSACTION_ID");

                R57TradePairJournal.Participants involved;
                try{
                    involved=R57TradePairJournal.readParticipants(child);
                }catch(IOException|RuntimeException error){
                    return blocked(entries,matches,firstMatching,
                        "INVALID_OR_AMBIGUOUS_INTENT");
                }
                if(!fileId.equals(involved.transaction))
                    return blocked(entries,matches,firstMatching,
                        "TRANSACTION_ID_FILENAME_MISMATCH");

                if(involved.includes(username)){
                    matches++;
                    if(firstMatching==null)
                        firstMatching=fileId;
                }
            }
        }catch(IOException|RuntimeException error){
            return blocked(entries,matches,firstMatching,
                "JOURNAL_ENUMERATION_FAILED");
        }

        if(matches>0)
            return new Result(
                Status.BLOCK_PENDING_PAIR_INTENT,entries,matches,
                firstMatching,"UNRESOLVED_PREPARED_INTENT"
            );
        return new Result(
            Status.NO_PENDING_PAIR_EVIDENCE,entries,0,null,
            "ONLY_JOURNAL_SCAN_CLEAR"
        );
    }

    private static Result blocked(
        int entries,int matches,UUID first,String reason
    ){
        return new Result(
            Status.BLOCK_INDETERMINATE_JOURNAL,
            entries,matches,first,reason
        );
    }

    private R58TradePairLoginPreflight(){}
}
