package spk.local;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * R52: two independent JVMs attempt the same negative-HEAD
 * publication. The stable G21.185 OS lock excludes a cooperative
 * competitor until the holder finishes or is force-killed.
 * Failed partial publication remains permanent negative evidence;
 * the competing JVM cannot silently resume/clear it.
 */
public final class G21380CrossJvmNegativeHeadPublicationGateRegression {
    private G21380CrossJvmNegativeHeadPublicationGateRegression(){}
    private static final byte[] IMAGE=
        "R52-fixture-only-account-image".getBytes(
            StandardCharsets.US_ASCII);
    private static void check(boolean ok,String what){
        if(!ok)throw new AssertionError("R52_HEAD_OS_LOCK "+what);
    }
    private static void enable(){
        System.setProperty(
            LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
    }
    private static boolean contains(IOException ex,String token){
        for(Throwable e=ex;e!=null;e=e.getCause())
            if(String.valueOf(e.getMessage()).contains(token))return true;
        return false;
    }

    private static void inspect(
        Path account,boolean expectHead
    )throws IOException{
        if(expectHead){
            LocalNegativeAccountHeadPrototype.NegativeObservation n=
                LocalNegativeAccountHeadPrototype.inspectNegative(account);
            check(!n.admissionAuthorized&&!n.releaseAuthorized,
                "completed negative HEAD falsely admitted");
            check(n.untrustedWorldGeneration==72L&&
                    "r52-owner".equals(n.username)&&
                    n.snapshotLength==IMAGE.length,
                "unexpected HEAD identity");
        }else{
            boolean denied=false;
            try{
                LocalNegativeAccountHeadPrototype.inspectNegative(account);
            }catch(IOException negative){
                denied=contains(negative,
                    "R50_HEAD_MISSING_NEGATIVE_NO_ADMISSION");
            }
            check(denied,"missing HEAD permitted positive admission");
        }
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "unwired gate changed legacy account");
    }

    private static void holder(
        Path account,Path ready,Path release,String stage
    )throws Exception{
        enable();
        LocalNegativeAccountHeadPrototype.Stage target=
            LocalNegativeAccountHeadPrototype.Stage.valueOf(stage);
        LocalNegativeAccountHeadPublicationGate.publish(
            account,"r52-owner",72L,IMAGE,reached->{
                if(reached!=target)return;
                Files.write(ready,new byte[]{1},
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
                System.out.println("R52_HOLDER_LOCK_HELD stage="+stage);
                System.out.flush();
                long end=System.nanoTime()+
                    TimeUnit.SECONDS.toNanos(90);
                while(!Files.exists(release,LinkOption.NOFOLLOW_LINKS)&&
                        System.nanoTime()<end){
                    try{Thread.sleep(10L);}
                    catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();
                        throw new IOException(
                            "R52_HOLDER_INTERRUPTED",interrupted);
                    }
                }
                check(Files.exists(release,LinkOption.NOFOLLOW_LINKS),
                    "holder not released or killed");
            });
        System.out.println("R52_HOLDER_COMPLETED");
    }

    private static void challenger(
        Path account,String expect
    )throws Exception{
        enable();
        boolean blocked=false;
        try{
            LocalNegativeAccountHeadPublicationGate.publish(
                account,"r52-owner",73L,IMAGE);
        }catch(IOException negative){
            blocked=contains(negative,expect);
        }
        check(blocked,"unexpected concurrent result expected "+expect);
        System.out.println("R52_CHALLENGER_DENIED "+expect);
    }

    private static Process spawn(
        Path root,String mode,Path account,Path ready,Path release,
        String stage,String expected,Path log
    )throws IOException{
        String java=System.getProperty("java.home")+
            File.separator+"bin"+File.separator+
            (System.getProperty("os.name","").toLowerCase().contains("win")
                ?"java.exe":"java");
        ProcessBuilder p=new ProcessBuilder(java,"-cp",
            System.getProperty("java.class.path"),
            G21380CrossJvmNegativeHeadPublicationGateRegression
                .class.getName(),"--child",mode,account.toString(),
            ready.toString(),release.toString(),stage,expected);
        p.directory(root.toFile());
        p.redirectErrorStream(true);
        p.redirectOutput(log.toFile());
        return p.start();
    }
    private static String finish(Process p,Path log,long seconds)
        throws Exception{
        if(!p.waitFor(seconds,TimeUnit.SECONDS)){
            p.destroyForcibly();
            p.waitFor(10L,TimeUnit.SECONDS);
            throw new AssertionError("R52 child timed out "+log);
        }
        String output=new String(
            Files.readAllBytes(log),StandardCharsets.UTF_8);
        check(p.exitValue()==0,
            "child error exit="+p.exitValue()+" "+output);
        return output;
    }
    private static void waitReady(
        Process holder,Path ready,Path log
    )throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<end&&holder.isAlive()&&
                !Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS))
            Thread.sleep(10L);
        check(holder.isAlive()&&Files.isRegularFile(
                ready,LinkOption.NOFOLLOW_LINKS),
            "holder never held lock "+(
                Files.exists(log)?new String(Files.readAllBytes(log),
                    StandardCharsets.UTF_8):""));
    }
    private static void stage(
        Path root,boolean kill
    )throws Exception{
        String label=kill?"killed-before-head":"complete-after-head";
        Path account=root.resolve(label+".properties");
        Path ready=root.resolve(label+".ready");
        Path release=root.resolve(label+".release");
        Path holderLog=root.resolve(label+".holder.log");
        String cutpoint=kill
            ?"OBJECT_DIRECTORY_FORCED_BEFORE_HEAD"
            :"HEAD_DIRECTORY_FORCED_BEFORE_READBACK";
        Process holder=spawn(root,"holder",account,ready,release,
            cutpoint,"none",holderLog);
        try{
            waitReady(holder,ready,holderLog);
            inspect(account,!kill);
            Path challengerLog=root.resolve(label+".challenger.log");
            Process rival=spawn(root,"challenger",account,ready,release,
                cutpoint,"G21.185 ACCOUNT_SESSION_LOCK_BUSY",
                challengerLog);
            check(finish(rival,challengerLog,20L).contains(
                "R52_CHALLENGER_DENIED"),
                "independent JVM bypassed active OS lock");

            // Unrelated account can publish its own negative HEAD
            // during the selected account lock's lifetime.
            Path foreign=root.resolve(label+"-foreign.properties");
            LocalNegativeAccountHeadPublicationGate.publish(
                foreign,"r52-owner",72L,IMAGE);
            inspect(foreign,true);

            if(kill){
                holder.destroyForcibly();
                check(holder.waitFor(10L,TimeUnit.SECONDS)&&
                    holder.exitValue()!=0,"holder did not die");
            }else{
                Files.write(release,new byte[]{1},
                    StandardOpenOption.CREATE_NEW);
                check(finish(holder,holderLog,20L).contains(
                    "R52_HOLDER_COMPLETED"),
                    "holder did not finish after lock exclusivity");
            }
            // The OS lock can be reacquired after graceful close OR
            // hard kill. But permanent negative state must refuse
            // a second HEAD, and crash-stranded immutable objects
            // are not automatically reused/cleaned.
            String reason=kill
                ?"R50_OBJECT_ALREADY_PRESENT_NO_REUSE"
                :"R50_HEAD_ALREADY_PRESENT_NO_OVERWRITE";
            Path afterLog=root.resolve(label+".after.log");
            Process after=spawn(root,"challenger",account,ready,release,
                cutpoint,reason,afterLog);
            check(finish(after,afterLog,20L).contains(
                "R52_CHALLENGER_DENIED"),
                "new JVM resumed/replaced account after lock release");
            inspect(account,!kill);
            Path stableLock=account.resolveSibling(
                account.getFileName()+".session.lck");
            check(Files.isRegularFile(stableLock,
                LinkOption.NOFOLLOW_LINKS),
                "stable session lock leaf was unlinked");
            System.out.println("R52_CROSS_JVM_GATE_STAGE_PASS"+
                " case="+label+
                " independentJvmLockExclusion=true"+
                " osLockReacquirable=true"+
                " strandedNegativeHeadOrObjectRetained=true"+
                " foreignAccountIsolation=true");
        }finally{
            if(holder.isAlive()){
                holder.destroyForcibly();
                holder.waitFor(10L,TimeUnit.SECONDS);
            }
        }
    }
    private static void defaultOff(Path root)throws IOException{
        String p=LocalCrossJvmAccountSessionLock.PROPERTY;
        String c=LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY;
        String a=System.getProperty(p);
        String b=System.getProperty(c);
        Path account=root.resolve("disabled.properties");
        try{
            System.clearProperty(p);
            System.clearProperty(c);
            boolean refused=false;
            try{
                LocalNegativeAccountHeadPublicationGate.publish(
                    account,"r52-owner",72L,IMAGE);
            }catch(IOException expected){
                refused=contains(expected,
                    "R52_NEGATIVE_HEAD_REQUIRES_CROSS_JVM_LOCK_CONTINUITY");
            }
            check(refused,"default-off gate permitted publication");
            check(!Files.exists(account.resolveSibling(
                    account.getFileName()+".session.lck"),
                    LinkOption.NOFOLLOW_LINKS),
                "default-off mode allocated a lock");
            check(!Files.exists(
                    LocalNegativeAccountHeadPrototype.headPath(account),
                    LinkOption.NOFOLLOW_LINKS),
                "default-off mode allocated negative HEAD");
        }finally{
            if(a==null)System.clearProperty(p);
            else System.setProperty(p,a);
            if(b==null)System.clearProperty(c);
            else System.setProperty(c,b);
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==7&&"--child".equals(args[0])){
            Path account=Paths.get(args[2]);
            Path ready=Paths.get(args[3]);
            Path release=Paths.get(args[4]);
            if("holder".equals(args[1]))
                holder(account,ready,release,args[5]);
            else if("challenger".equals(args[1]))
                challenger(account,args[6]);
            else throw new IllegalArgumentException("unknown child role");
            return;
        }
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("r52-cross-jvm-head-");
        String p=LocalCrossJvmAccountSessionLock.PROPERTY;
        String c=LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY;
        String a=System.getProperty(p);
        String b=System.getProperty(c);
        try{
            defaultOff(root);
            enable();
            stage(root,true);
            stage(root,false);
            System.out.println("R52_CROSS_JVM_NEGATIVE_HEAD_PUBLICATION_PASS"+
                " independentJvmContention=true"+
                " killedOwnerLockRelease=true"+
                " cleanOwnerLockRelease=true"+
                " permanentNegativeHeadNoAutoRetirement=true"+
                " strandedObjectNotAutoRecovered=true"+
                " defaultOffNoSideEffects=true"+
                " noLegacyAccountMutation=true"+
                " noLocalSessionOrRawWriterWiring=true"+
                " externalHostileWriterFence=false"+
                " authenticatedEpoch=false"+
                " finalSaveRelease=false");
        }finally{
            if(a==null)System.clearProperty(p);
            else System.setProperty(p,a);
            if(b==null)System.clearProperty(c);
            else System.setProperty(c,b);
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }
}
