package spk.local;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * R63: ANY detached reusable LAB sidecar (R57/R58/R59) is permanent
 * negative evidence for existing legacy RAW, World-save and session
 * admission paths. Never infer permission from absent LAB HEAD: an
 * orphan image/archive/intent may survive a crash before its HEAD.
 *
 * This is a bounded read-only presence veto, not an authenticated
 * account epoch, lock, cleanup or complete hostile-writer CAS. LAB
 * forensic inspection and LAB transitions do NOT call this gate.
 */
final class LocalReusableLabEvidenceNegativeGate {
    private static final String[] NAMESPACES={
        ".g21385-", ".g21386-", ".g21387-",
        // R68: R50 forces immutable snapshot BEFORE forced negative
        // HEAD. A killed writer may leave only this orphan object.
        // It must be negative even when HEAD publication never began.
        ".g21378-object-",
        // R160: R153 forces this independent snapshot object before
        // attempting its R154 HEAD. An abrupt halt before HEAD leaves
        // an orphan that must still veto ordinary legacy admission.
        LocalAccountEpochSnapshotObjects.PREFIX
    };
    private LocalReusableLabEvidenceNegativeGate(){}

    static void requireNoLegacyCollision(Path account)throws IOException{
        if(account==null)
            throw new IOException("R63_NULL_ACCOUNT_PATH");
        Path selected=account.toAbsolutePath().normalize();
        Path parent=selected.getParent();
        if(parent==null||selected.getFileName()==null)
            throw new IOException("R63_INVALID_ACCOUNT_PATH");
        // Leave existing R34/R53.1 ancestry and inode diagnostics to
        // their owners. Do NOT follow an unsafe parent merely to scan
        // LAB sidecars, or create directories as a side effect.
        BasicFileAttributes parentAttrs;
        try{
            parentAttrs=Files.readAttributes(parent,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        }catch(NoSuchFileException absent){
            return; // no sibling leaf can exist in a missing parent
        }
        if(!parentAttrs.isDirectory()||parentAttrs.isSymbolicLink())
            return;
        final String stem=selected.getFileName().toString();
        try(DirectoryStream<Path> names=Files.newDirectoryStream(parent)){
            for(Path child:names){
                String leaf=child.getFileName().toString();
                for(String namespace:NAMESPACES){
                    if(leaf.startsWith(stem+namespace))
                        throw new IOException(
                            "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION"+
                            " namespace="+namespace);
                }
            }
        }
    }
}
