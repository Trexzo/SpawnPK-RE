package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

/**
 * G21.196 STANDALONE, NON-ADMITTING, test-only design prototype.
 *
 * A forced reservation can exist BEFORE World assigns a player generation.
 * The generation is recorded later in a distinct, forced, immutable binding.
 * Missing, malformed, contradictory, or partially written records never
 * authorize admission, final save, release, recovery, or marker retirement.
 *
 * This is deliberately NOT wired to LocalSession / LocalSessionPlayerInitializer,
 * FilePlayerRepository, World, or the G21.188 ACTIVE review marker.
 * Epoch ID is an untrusted correlation value, NOT an authenticated owner.
 */
final class LocalDurableOwnerEpochPrototype {
    private static final String RESERVE_MAGIC="G21196_OWNER_RESERVATION_V1";
    private static final String BIND_MAGIC="G21196_OWNER_BINDING_V1";
    private static final String RESERVE_SUFFIX=".g21196-epoch-reservation";
    private static final String BIND_SUFFIX=".g21196-epoch-binding";
    private static final int MAX_RECORD_BYTES=4096;

    private LocalDurableOwnerEpochPrototype(){}

    static final class Reservation {
        final Path account;
        final String accountSha;
        final String epoch;
        final String reservationSha;

        private Reservation(Path account,String accountSha,String epoch,
                            String reservationSha){
            this.account=account;
            this.accountSha=accountSha;
            this.epoch=epoch;
            this.reservationSha=reservationSha;
        }
    }

    static final class Observation {
        final Reservation reservation;
        final boolean generationBound;
        final long worldGeneration;

        private Observation(Reservation reservation,boolean bound,long generation){
            this.reservation=reservation;
            this.generationBound=bound;
            this.worldGeneration=generation;
        }
    }

    static Path reservationPath(Path account)throws IOException{
        return sibling(account,RESERVE_SUFFIX);
    }

    static Path bindingPath(Path account)throws IOException{
        return sibling(account,BIND_SUFFIX);
    }

    private static Path canonical(Path account)throws IOException{
        if(account==null)
            throw new IOException("G21.196 NULL_ACCOUNT");
        Path normalized=account.toAbsolutePath().normalize();
        if(normalized.getParent()==null||normalized.getFileName()==null)
            throw new IOException("G21.196 INVALID_ACCOUNT");
        return normalized;
    }

    private static Path sibling(Path account,String suffix)throws IOException{
        Path file=canonical(account);
        return file.resolveSibling(file.getFileName().toString()+suffix);
    }

    private static void safeParent(Path account)throws IOException{
        Path file=canonical(account);
        MailboxAccountPublicationCoordinator
            .requireSafeWorldTempAncestry(file,false);
        Path parent=file.getParent();
        if(!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("G21.196 PARENT_NOT_SAFE");
        try(java.nio.file.DirectoryStream<Path> open=
                Files.newDirectoryStream(parent)){
            // Access preflight, NOT raw path race exclusion.
        }
    }

    private static void requireNoLeaf(Path file)throws IOException{
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("G21.196 EPOCH_RECORD_PRESENT_NO_OVERWRITE");
    }

    private static byte[] readRegular(Path file)throws IOException{
        BasicFileAttributes attrs=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!attrs.isRegularFile()||attrs.isSymbolicLink()||
           attrs.fileKey()==null||attrs.size()<=0||
           attrs.size()>MAX_RECORD_BYTES)
            throw new IOException("G21.196 RECORD_IDENTITY_OR_SIZE_UNSAFE");
        byte[] bytes=Files.readAllBytes(file);
        if(bytes.length==0||bytes.length>MAX_RECORD_BYTES)
            throw new IOException("G21.196 RECORD_READ_SIZE_UNSAFE");
        BasicFileAttributes after=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!after.isRegularFile()||after.isSymbolicLink()||
           !attrs.fileKey().equals(after.fileKey())||
           attrs.size()!=after.size())
            throw new IOException("G21.196 RECORD_CHANGED_DURING_READ");
        return bytes;
    }

    private static void createForced(Path account,Path leaf,byte[] bytes)
        throws IOException{
        safeParent(account);
        requireNoLeaf(leaf);
        if(bytes.length==0||bytes.length>MAX_RECORD_BYTES)
            throw new IOException("G21.196 RECORD_TOO_LARGE");
        try(FileChannel channel=FileChannel.open(leaf,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)){
            ByteBuffer content=ByteBuffer.wrap(bytes);
            while(content.hasRemaining())channel.write(content);
            channel.force(true);
        }catch(java.nio.file.FileAlreadyExistsException existing){
            throw new IOException(
                "G21.196 EPOCH_RECORD_PRESENT_NO_OVERWRITE",existing);
        }
        try(FileChannel dir=FileChannel.open(leaf.getParent(),
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            dir.force(true);
        }
        // Any failure above leaves an uncertain leaf. Never delete it.
        if(!Arrays.equals(bytes,readRegular(leaf)))
            throw new IOException("G21.196 FORCED_RECORD_READBACK_CHANGED");
    }

    static Reservation reserveBeforeWorldRegistration(Path accountFile)
        throws IOException{
        Path account=canonical(accountFile);
        Path reservation=reservationPath(account);
        Path binding=bindingPath(account);
        safeParent(account);
        requireNoLeaf(reservation);
        requireNoLeaf(binding);
        String fingerprint=sha(account.toString().getBytes(
            StandardCharsets.UTF_8));
        String epoch=UUID.randomUUID().toString();
        String record=RESERVE_MAGIC+"\n"+
            "accountSha256="+fingerprint+"\n"+
            "prototypeEpoch="+epoch+"\n"+
            "state=RESERVED_NO_ADMISSION\n";
        byte[] bytes=record.getBytes(StandardCharsets.UTF_8);
        createForced(account,reservation,bytes);
        Reservation observed=readReservation(account);
        if(!fingerprint.equals(observed.accountSha)||
           !epoch.equals(observed.epoch)||
           !sha(bytes).equals(observed.reservationSha))
            throw new IOException("G21.196 RESERVATION_READBACK_MISMATCH");
        return observed;
    }

    private static Reservation readReservation(Path account)throws IOException{
        byte[] bytes=readRegular(reservationPath(account));
        String[] lines=new String(bytes,StandardCharsets.UTF_8)
            .split("\n",-1);
        if(lines.length!=5||
           !RESERVE_MAGIC.equals(lines[0])||
           !lines[1].startsWith("accountSha256=")||
           !lines[2].startsWith("prototypeEpoch=")||
           !"state=RESERVED_NO_ADMISSION".equals(lines[3])||
           !lines[4].isEmpty())
            throw new IOException("G21.196 MALFORMED_RESERVATION_NO_AUTHORITY");
        String expected=sha(account.toString().getBytes(
            StandardCharsets.UTF_8));
        String actual=lines[1].substring("accountSha256=".length());
        String epoch=lines[2].substring("prototypeEpoch=".length());
        if(!expected.equals(actual))
            throw new IOException("G21.196 RESERVATION_ACCOUNT_MISMATCH");
        try{
            if(!UUID.fromString(epoch).toString().equals(epoch))
                throw new IllegalArgumentException("noncanonical epoch");
        }catch(IllegalArgumentException invalid){
            throw new IOException("G21.196 RESERVATION_EPOCH_MALFORMED",invalid);
        }
        return new Reservation(account,actual,epoch,sha(bytes));
    }

    static void bindAfterWorldRegistration(Reservation token,long generation)
        throws IOException{
        if(token==null)
            throw new IOException("G21.196 NULL_RESERVATION");
        if(generation<=0)
            throw new IOException("G21.196 INVALID_WORLD_GENERATION");
        Reservation exact=readReservation(token.account);
        if(!token.epoch.equals(exact.epoch)||
           !token.reservationSha.equals(exact.reservationSha)||
           !token.accountSha.equals(exact.accountSha))
            throw new IOException("G21.196 RESERVATION_CHANGED_NO_BIND");
        Path binding=bindingPath(token.account);
        requireNoLeaf(binding);
        String record=BIND_MAGIC+"\n"+
            "reservationSha256="+exact.reservationSha+"\n"+
            "accountSha256="+exact.accountSha+"\n"+
            "worldGeneration="+generation+"\n"+
            "state=BOUND_NO_ADMISSION\n";
        createForced(exact.account,binding,
            record.getBytes(StandardCharsets.UTF_8));
        Observation check=inspect(exact.account);
        if(!check.generationBound||check.worldGeneration!=generation||
           !exact.reservationSha.equals(check.reservation.reservationSha))
            throw new IOException("G21.196 BINDING_READBACK_MISMATCH");
    }

    static Observation inspect(Path accountFile)throws IOException{
        Path account=canonical(accountFile);
        safeParent(account);
        Reservation reservation=readReservation(account);
        Path binding=bindingPath(account);
        if(!Files.exists(binding,LinkOption.NOFOLLOW_LINKS)){
            // UNBOUND is an unresolved negative, never safe admission.
            return new Observation(reservation,false,-1L);
        }
        String[] lines=new String(readRegular(binding),
            StandardCharsets.UTF_8).split("\n",-1);
        if(lines.length!=6||
           !BIND_MAGIC.equals(lines[0])||
           !lines[1].equals("reservationSha256="+reservation.reservationSha)||
           !lines[2].equals("accountSha256="+reservation.accountSha)||
           !lines[3].startsWith("worldGeneration=")||
           !"state=BOUND_NO_ADMISSION".equals(lines[4])||
           !lines[5].isEmpty())
            throw new IOException("G21.196 BINDING_MISMATCH_NO_AUTHORITY");
        final long generation;
        try{
            generation=Long.parseLong(
                lines[3].substring("worldGeneration=".length()));
        }catch(NumberFormatException invalid){
            throw new IOException("G21.196 BINDING_GENERATION_MALFORMED",invalid);
        }
        if(generation<=0)
            throw new IOException("G21.196 BINDING_GENERATION_INVALID");
        return new Observation(reservation,true,generation);
    }

    private static String sha(byte[] value)throws IOException{
        final byte[] hash;
        try{
            hash=MessageDigest.getInstance("SHA-256").digest(value);
        }catch(NoSuchAlgorithmException unavailable){
            throw new IOException("G21.196 SHA256_UNAVAILABLE",unavailable);
        }
        StringBuilder hex=new StringBuilder();
        for(byte b:hash){
            String pair=Integer.toHexString(b&255);
            if(pair.length()==1)hex.append('0');
            hex.append(pair);
        }
        return hex.toString();
    }
}
