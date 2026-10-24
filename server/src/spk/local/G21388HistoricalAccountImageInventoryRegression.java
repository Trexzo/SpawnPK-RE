package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * R60: every historical snapshot name must be referenced by at least
 * one complete archived HEAD. Newer orphans remain classified by R57,
 * preserving its existing regression and failure taxonomy.
 */
public final class G21388HistoricalAccountImageInventoryRegression {
    private G21388HistoricalAccountImageInventoryRegression(){}
    private static final byte[] INITIAL=
        "r60-original-account".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FINAL=
        "r60-final-account".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ROGUE=
        "r60-unreferenced-image".getBytes(StandardCharsets.US_ASCII);
    private static void check(boolean yes,String msg){
        if(!yes)throw new AssertionError("R60_HISTORICAL_IMAGES "+msg);
    }
    @FunctionalInterface interface Attempt{void run()throws IOException;}
    private static void veto(String fragment,Attempt a)throws Exception{
        boolean refused=false;
        try{a.run();}
        catch(IOException ex){
            for(Throwable t=ex;t!=null;t=t.getCause())
                if(String.valueOf(t.getMessage()).contains(fragment))
                    refused=true;
        }
        check(refused,"not rejected: "+fragment);
    }
    private static String sha(byte[] bytes)throws Exception{
        StringBuilder b=new StringBuilder(64);
        for(byte part:MessageDigest.getInstance("SHA-256")
                .digest(bytes)){
            String h=Integer.toHexString(part&255);
            if(h.length()==1)b.append('0');
            b.append(h);
        }
        return b.toString();
    }
    private static Path image(Path account,int seq,byte[] bytes)
        throws Exception{
        return account.resolveSibling(account.getFileName()+
            ".g21385-image-"+
            String.format(java.util.Locale.ROOT,"%04d",seq)+
            "-"+sha(bytes)+".snapshot");
    }
    private static void enable(){
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
    }
    private static Path history(Path root,String name)throws Exception{
        Path account=root.resolve(name+".properties");
        LocalReusableAccountEpochLab.initialize(account,INITIAL);
        LocalReusableAccountEpochLab.claim(
            account,0,601L,"r60_world_owner_a");
        LocalReusableAccountEpochLab.prepareFinal(
            account,1,601L,"r60_world_owner_a");
        LocalReusableAccountEpochLab.settleLabFinal(
            account,2,601L,"r60_world_owner_a",FINAL);
        LocalReusableAccountEpochLab.claim(
            account,3,701L,"r60_world_owner_b");
        LocalReusableAccountEpochLab.Observation current=
            LocalReusableAccountEpochLab.inspect(account);
        check(current.sequence==4&&current.epoch==3&&
            !current.admissionAuthorized&&!current.releaseAuthorized,
            "LAB history authorized gameplay");
        check(Files.isRegularFile(image(account,0,INITIAL),
            LinkOption.NOFOLLOW_LINKS),"genesis image missing");
        check(Files.isRegularFile(image(account,3,FINAL),
            LinkOption.NOFOLLOW_LINKS),"final image missing");
        return account;
    }
    private static void noAccountMutation(Path account)throws IOException{
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "lab touched legacy account pathname");
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"args");
        Path root=Files.createTempDirectory("r60-inventory-");
        String p=LocalCrossJvmAccountSessionLock.PROPERTY;
        String c=LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY;
        String oldP=System.getProperty(p),oldC=System.getProperty(c);
        try{
            enable();
            Path old=history(root,"old");
            // A rogue image at seq 0 was previously ignored once
            // the currently selected image advanced to seq 3.
            Path orphan=image(old,0,ROGUE);
            Files.write(orphan,ROGUE,StandardOpenOption.CREATE_NEW);
            byte[] keep=Files.readAllBytes(orphan);
            veto("R60_UNREFERENCED_HISTORICAL_IMAGE_NEGATIVE",()->
                LocalReusableAccountEpochLab.inspect(old));
            final Path orphanAccount=old;
            veto("R60_UNREFERENCED_HISTORICAL_IMAGE_NEGATIVE",()->
                LocalReusableAccountEpochLab.prepareFinal(orphanAccount,
                    4,701L,"r60_world_owner_b"));
            check(Arrays.equals(keep,Files.readAllBytes(orphan)),
                "extra historical image was automatically removed");
            noAccountMutation(old);

            Path other=history(root,"other");
            Path gapImage=image(other,1,ROGUE);
            Files.write(gapImage,ROGUE,StandardOpenOption.CREATE_NEW);
            veto("R60_UNREFERENCED_HISTORICAL_IMAGE_NEGATIVE",()->
                LocalReusableAccountEpochLab.inspect(other));
            noAccountMutation(other);

            Path malformed=history(root,"malformed");
            Path leaf=malformed.resolveSibling(malformed.getFileName()+
                ".g21385-image-0000-malformed.snapshot");
            Files.write(leaf,new byte[]{1},StandardOpenOption.CREATE_NEW);
            veto("R60_MALFORMED_HISTORICAL_IMAGE_LEAF_NEGATIVE",()->
                LocalReusableAccountEpochLab.inspect(malformed));
            noAccountMutation(malformed);

            Path future=root.resolve("future.properties");
            LocalReusableAccountEpochLab.initialize(future,INITIAL);
            Path futureImage=image(future,1,ROGUE);
            Files.write(futureImage,ROGUE,StandardOpenOption.CREATE_NEW);
            veto("R57_ORPHAN_FUTURE_IMAGE_MANUAL_REVIEW",()->
                LocalReusableAccountEpochLab.inspect(future));
            noAccountMutation(future);

            Path clean=history(root,"clean");
            check(LocalReusableAccountEpochLab.inspect(clean).sequence==4,
                "unrelated healthy LAB history rejected");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "real World lease leaked");
            System.out.println("R60_EXACT_HISTORICAL_IMAGE_INVENTORY_PASS"+
                " rogueOldDigestDenied=true"+
                " rogueUnusedSequenceDenied=true"+
                " malformedHistoricalLeafDenied=true"+
                " originalFutureOrphanClassificationRetained=true"+
                " noAutoRepairOrCleanup=true"+
                " unrelatedOwnerIndependent=true"+
                " fullArchivedHistoryRequired=true"+
                " noLegacyAccountMutation=true"+
                " noAdmissionOrReleaseGranted=true"+
                " hostileWriterCAS=false"+
                " powerLossProof=false");
        }finally{
            if(oldP==null)System.clearProperty(p);
            else System.setProperty(p,oldP);
            if(oldC==null)System.clearProperty(c);
            else System.setProperty(c,oldC);
            try(Stream<Path> files=Files.walk(root)){
                for(Path f:files.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(f);
            }
        }
    }
}
