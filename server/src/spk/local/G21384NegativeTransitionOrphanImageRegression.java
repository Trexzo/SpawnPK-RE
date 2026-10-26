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

/** R56: detect orphan, competing and malformed immutable R55 step
 * sidecars without treating any completed chain as login authority. */
public final class G21384NegativeTransitionOrphanImageRegression {
    private G21384NegativeTransitionOrphanImageRegression(){}
    private static final byte[] GENESIS=
        "R56_GENESIS".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CHECKPOINT=
        "R56_CHECKPOINT".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ROGUE=
        "R56_ROGUE_IMAGE".getBytes(StandardCharsets.US_ASCII);
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("R56_ORPHAN "+reason);
    }
    private static boolean has(Throwable t,String marker){
        for(Throwable x=t;x!=null;x=x.getCause())
            if(String.valueOf(x.getMessage()).contains(marker))return true;
        return false;
    }
    @FunctionalInterface interface Action{void run()throws IOException;}
    private static void rejects(String marker,Action action)throws Exception{
        boolean denied=false;
        try{action.run();}
        catch(IOException failure){denied=has(failure,marker);}
        check(denied,"required negative gate not reached: "+marker);
    }
    private static String sha(byte[] b)throws Exception{
        byte[] sum=MessageDigest.getInstance("SHA-256").digest(b);
        StringBuilder result=new StringBuilder(64);
        for(byte n:sum){
            String pair=Integer.toHexString(n&255);
            if(pair.length()==1)result.append('0');
            result.append(pair);
        }
        return result.toString();
    }
    private static Path image(
        Path account,int step,byte[] bytes
    )throws Exception{
        String name=account.getFileName()+".g21383-step-"+
            String.format(java.util.Locale.ROOT,"%02d",step)+
            "-image-"+sha(bytes)+".snapshot";
        return account.resolveSibling(name);
    }
    private static Path base(Path root,String leaf)throws Exception{
        Path account=root.resolve(leaf+".properties");
        LocalNegativeAccountHeadPublicationGate.publish(
            account,"r56-account",56L,GENESIS);
        check(!LocalNegativeAccountTransitionLog.inspect(account)
            .admissionAuthorized,"genesis became admitting");
        return account;
    }
    private static void noLegacyMutation(Path account)throws IOException{
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "negative fixture overwrote legacy account");
        check(Files.isRegularFile(
            LocalNegativeAccountHeadPrototype.headPath(account),
            LinkOption.NOFOLLOW_LINKS),"genesis head missing");
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("r56-orphan-step-");
        String lock=LocalCrossJvmAccountSessionLock.PROPERTY;
        String continuity=LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY;
        String a=System.getProperty(lock),b=System.getProperty(continuity);
        try{
            System.setProperty(lock,"true");
            System.setProperty(continuity,"true");

            Path orphan=base(root,"orphan");
            Path stray=image(orphan,1,ROGUE);
            Files.write(stray,ROGUE,StandardOpenOption.CREATE_NEW);
            byte[] original=Files.readAllBytes(stray);
            rejects("R56_ORPHAN_STEP_IMAGE_NEGATIVE",()->
                LocalNegativeAccountTransitionLog.inspect(orphan));
            rejects("R56_ORPHAN_STEP_IMAGE_NEGATIVE",()->
                LocalNegativeAccountTransitionLog.append(orphan,0,
                    LocalNegativeAccountTransitionLog.Phase.CHECKPOINT,
                    CHECKPOINT,s->{}));
            check(Arrays.equals(original,Files.readAllBytes(stray)),
                "orphan was automatically reconciled or overwritten");
            noLegacyMutation(orphan);

            Path healthy=base(root,"healthy");
            LocalNegativeAccountTransitionLog.Observation checkpoint=
                LocalNegativeAccountTransitionLog.append(healthy,0,
                    LocalNegativeAccountTransitionLog.Phase.CHECKPOINT,
                    CHECKPOINT,s->{});
            check(checkpoint.sequence==1&&
                !checkpoint.admissionAuthorized&&!checkpoint.releaseAuthorized,
                "completed checkpoint granted authority");
            check(LocalNegativeAccountTransitionLog.inspect(healthy)
                .sequence==1,"healthy chain failed readback");
            Path extra=image(healthy,1,ROGUE);
            Files.write(extra,ROGUE,StandardOpenOption.CREATE_NEW);
            rejects("R56_MULTIPLE_STEP_IMAGES_NEGATIVE",()->
                LocalNegativeAccountTransitionLog.inspect(healthy));
            rejects("R56_MULTIPLE_STEP_IMAGES_NEGATIVE",()->
                LocalNegativeAccountTransitionLog.append(healthy,1,
                    LocalNegativeAccountTransitionLog.Phase.FINAL_PENDING,
                    CHECKPOINT,s->{}));
            noLegacyMutation(healthy);

            Path malformed=base(root,"malformed");
            Path malformedStep=malformed.resolveSibling(
                malformed.getFileName()+".g21383-step-01-image-invalid.snapshot");
            Files.write(malformedStep,new byte[]{1},
                StandardOpenOption.CREATE_NEW);
            rejects("R56_MALFORMED_STEP_LEAF_NEGATIVE",()->
                LocalNegativeAccountTransitionLog.inspect(malformed));
            noLegacyMutation(malformed);

            Path independent=base(root,"independent");
            check(LocalNegativeAccountTransitionLog.inspect(independent)
                .sequence==0,"foreign orphan affected independent account");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "session ownership leaked");
            System.out.println("R56_ORPHAN_NEGATIVE_STEP_EVIDENCE_PASS"+
                " orphanImageDenied=true"+
                " committedStepExtraImageDenied=true"+
                " malformedStepLeafDenied=true"+
                " noOrphanAutoReplayOrDelete=true"+
                " unrelatedAccountIsolation=true"+
                " noLegacyAccountMutation=true"+
                " positiveAdmissionNeverAuthorized=true"+
                " hostileWriterAtomicCAS=false"+
                " crashDurableFinalReceipt=false");
        }finally{
            if(a==null)System.clearProperty(lock);
            else System.setProperty(lock,a);
            if(b==null)System.clearProperty(continuity);
            else System.setProperty(continuity,b);
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}
