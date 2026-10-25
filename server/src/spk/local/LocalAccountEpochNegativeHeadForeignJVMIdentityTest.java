package spk.local;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * R159: a separate attacker JVM changes a CREATE_NEW negative HEAD leaf
 * at three publication cutpoints. Exact same bytes with a different inode,
 * or an extra hardlink on the same inode, must deny the publishing JVM.
 * This never grants account admission, owner handoff or final-save ACK.
 */
public final class LocalAccountEpochNegativeHeadForeignJVMIdentityTest {
    private static final int REFUSED_EXIT=17;
    private static final byte[] SNAPSHOT=
        "R159 foreign process HEAD identity observation".getBytes(
            StandardCharsets.US_ASCII);
    private static int cases;
    private LocalAccountEpochNegativeHeadForeignJVMIdentityTest(){}

    private enum Attack { SAME_BYTES_INODE_SWAP, HARDLINK_ALIAS }

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("R159 "+why);
    }

    private static String name(Path account){
        String leaf=account.getFileName().toString();
        check(leaf.endsWith(".properties"),"bad account leaf");
        return leaf.substring(0,leaf.length()-".properties".length());
    }

    private static LocalAccountEpochHeadCodec.Record marker(Path account)
            throws IOException{
        return new LocalAccountEpochHeadCodec.Record(
            name(account),1,1,159,
            LocalAccountEpochHeadCodec.State.REVIEW,
            LocalAccountEpochHeadCodec.ZERO_SHA,
            LocalAccountEpochHeadCodec.digestSnapshot(SNAPSHOT));
    }

    private static void await(Path signal)throws IOException{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        while(!Files.exists(signal,LinkOption.NOFOLLOW_LINKS)){
            if(System.nanoTime()>until)
                throw new IOException("R159_BARRIER_TIMEOUT "+signal);
            try{Thread.sleep(10L);}
            catch(InterruptedException e){
                Thread.currentThread().interrupt();
                throw new IOException("R159_BARRIER_INTERRUPTED",e);
            }
        }
    }

    private static void publisher(Path account,
        LocalAccountEpochNegativeHeadPublication.Stage selected,
        Path ready,Path completed)throws Exception{
        try{
            LocalAccountEpochNegativeHeadPublication.publish(
                account,marker(account),SNAPSHOT,stage->{
                    if(stage==selected){
                        Files.createFile(ready);
                        await(completed);
                    }
                });
            throw new AssertionError(
                "R159 foreign HEAD modification was accepted");
        }catch(IOException refused){
            check(Files.exists(ready,LinkOption.NOFOLLOW_LINKS)&&
                Files.exists(completed,LinkOption.NOFOLLOW_LINKS),
                "denied before foreign edit happened: "+refused);
            System.out.println("R159_PUBLISHER_DENIED_FOREIGN_EDIT"+
                " stage="+selected+" reason="+refused.getMessage());
            System.out.flush();
            System.exit(REFUSED_EXIT);
        }
    }

    private static void attacker(Path account,Attack attack,Path ready,
                                 Path completed)throws Exception{
        await(ready);
        Path head=LocalAccountEpochNegativeHeadPublication.headPath(account);
        Path foreign=head.resolveSibling(head.getFileName()+".foreign");
        if(attack==Attack.SAME_BYTES_INODE_SWAP){
            Files.move(head,foreign);
            Files.write(head,LocalAccountEpochHeadCodec.encode(
                marker(account)),StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        }else{
            Files.createLink(foreign,head);
        }
        Files.createFile(completed);
        System.out.println("R159_INDEPENDENT_ATTACK_COMPLETED "+attack+
            " foreignEvidenceRetained=true");
    }

    private static void observer(Path account,Attack attack)throws Exception{
        Path head=LocalAccountEpochNegativeHeadPublication.headPath(account);
        if(attack==Attack.SAME_BYTES_INODE_SWAP){
            LocalAccountEpochNegativeHeadPublication.Observation view=
                LocalAccountEpochNegativeHeadPublication.inspectNegative(
                    account);
            check(view.untrusted.snapshotSha256.equals(
                      marker(account).snapshotSha256)&&
                !view.admissionAuthorized&&!view.releaseAuthorized&&
                !view.recoveryAuthorized,
                "fresh JVM treated foreign same-byte negative as trusted");
        }else{
            try{
                LocalAccountEpochNegativeHeadPublication.inspectNegative(
                    account);
                throw new AssertionError(
                    "R159 independently accepted aliased HEAD");
            }catch(IOException denied){
                check(Files.exists(head,LinkOption.NOFOLLOW_LINKS),
                    "HEAD disappeared on independent inspection");
            }
        }
        System.out.println("R159_THIRD_JVM_NEGATIVE_DECISION_PASS"+
            " attack="+attack+" noAdmissionReleaseRecoveryOrAck=true");
    }

    private static String javaBinary(){
        String exe=System.getProperty("os.name").toLowerCase()
            .contains("win")?"java.exe":"java";
        return new File(new File(System.getProperty("java.home"),"bin"),
            exe).getAbsolutePath();
    }

    private static Process launch(Path log,String... args)throws IOException{
        String[] command=new String[4+args.length];
        command[0]=javaBinary();
        command[1]="-cp";
        command[2]=System.getProperty("java.class.path");
        command[3]=LocalAccountEpochNegativeHeadForeignJVMIdentityTest
            .class.getName();
        System.arraycopy(args,0,command,4,args.length);
        return new ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(log.toFile()).start();
    }

    private static String log(Path path)throws IOException{
        return new String(Files.readAllBytes(path),
            StandardCharsets.UTF_8);
    }

    private static int exit(Process p,String label)throws Exception{
        if(!p.waitFor(35,TimeUnit.SECONDS))
            throw new IOException("R159_PROCESS_TIMEOUT "+label);
        return p.exitValue();
    }

    private static void destroy(Process p)throws Exception{
        if(p!=null&&p.isAlive()){
            p.destroyForcibly();
            p.waitFor(10,TimeUnit.SECONDS);
        }
    }

    private static void oneCase(Path root,boolean existing,
            LocalAccountEpochNegativeHeadPublication.Stage stage,
            Attack attack)throws Exception{
        String id="r159case"+cases;
        Path account=root.resolve(id+".properties");
        byte[] old=("R159 unchanged legacy file "+id).getBytes(
            StandardCharsets.US_ASCII);
        Object oldKey=null;
        if(existing){
            Files.write(account,old);
            oldKey=Files.readAttributes(account,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();
        }
        Path ready=root.resolve(id+".ready");
        Path completed=root.resolve(id+".attacked");
        Path publisherLog=root.resolve(id+".publisher.log");
        Path attackerLog=root.resolve(id+".attacker.log");
        Process writer=null,foreignWriter=null;
        try{
            writer=launch(publisherLog,"--publish",account.toString(),
                stage.name(),ready.toString(),completed.toString());
            foreignWriter=launch(attackerLog,"--attack",
                account.toString(),attack.name(),
                ready.toString(),completed.toString());
            int edited=exit(foreignWriter,"attacker");
            int denied=exit(writer,"publisher");
            check(edited==0&&denied==REFUSED_EXIT&&
                log(attackerLog).contains(
                    "R159_INDEPENDENT_ATTACK_COMPLETED "+attack)&&
                log(publisherLog).contains(
                    "R159_PUBLISHER_DENIED_FOREIGN_EDIT stage="+stage),
                "unexpected cross-JVM results: publisher="+denied+
                " attacker="+edited+"\n"+log(publisherLog)+
                "\n"+log(attackerLog));
        }finally{
            destroy(writer);
            destroy(foreignWriter);
        }

        Path head=LocalAccountEpochNegativeHeadPublication.headPath(account);
        Path foreign=head.resolveSibling(head.getFileName()+".foreign");
        byte[] exact=LocalAccountEpochHeadCodec.encode(marker(account));
        check(Arrays.equals(exact,Files.readAllBytes(head))&&
            Arrays.equals(exact,Files.readAllBytes(foreign)),
            "negative HEAD or foreign evidence bytes changed");
        check(attack==Attack.SAME_BYTES_INODE_SWAP?
                !Files.isSameFile(head,foreign):
                Files.isSameFile(head,foreign),
            "unexpected inode identity after foreign edit");
        LocalAccountEpochSnapshotObjects.verify(account,
            marker(account).snapshotSha256,null);
        if(existing){
            check(Arrays.equals(old,Files.readAllBytes(account))&&
                oldKey.equals(Files.readAttributes(account,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                    .fileKey()),
                "legacy bytes or inode changed");
        }else{
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "legacy file unexpectedly created");
        }

        Path observerLog=root.resolve(id+".observer.log");
        Process observer=null;
        try{
            observer=launch(observerLog,"--observe",account.toString(),
                attack.name());
            check(exit(observer,"observer")==0&&
                log(observerLog).contains(
                    "R159_THIRD_JVM_NEGATIVE_DECISION_PASS attack="+attack),
                "observer failed to deny: "+log(observerLog));
        }finally{destroy(observer);}

        byte[] retrySnapshot=("R159 unrelated retry "+id).getBytes(
            StandardCharsets.US_ASCII);
        String retryDigest=LocalAccountEpochHeadCodec.digestSnapshot(
            retrySnapshot);
        Path retryObject=LocalAccountEpochSnapshotObjects.objectPath(
            account,retryDigest);
        check(!Files.exists(retryObject,LinkOption.NOFOLLOW_LINKS),
            "retry object already existed");
        LocalAccountEpochHeadCodec.Record retry=
            new LocalAccountEpochHeadCodec.Record(name(account),1,1,160,
                LocalAccountEpochHeadCodec.State.ACTIVE,
                LocalAccountEpochHeadCodec.ZERO_SHA,retryDigest);
        try{
            LocalAccountEpochNegativeHeadPublication.publish(
                account,retry,retrySnapshot);
            throw new AssertionError("R159 reused a foreign HEAD");
        }catch(IOException denied){
            check(!Files.exists(retryObject,LinkOption.NOFOLLOW_LINKS),
                "denied retry created new immutable snapshot");
        }
        check(Arrays.equals(exact,Files.readAllBytes(head))&&
            Files.exists(foreign,LinkOption.NOFOLLOW_LINKS),
            "retry modified or removed retained negative evidence");
        cases++;
        System.out.println("R159_CROSS_JVM_FOREIGN_HEAD_IDENTITY_PASS"+
            " case="+cases+" attack="+attack+" stage="+stage+
            " existing="+existing+
            " writerDenied=true independentObserverDenied=true"+
            " retainedForeignEvidence=true noLegacyMutation=true");
    }

    public static void main(String[] args)throws Exception{
        if(args.length==5&&"--publish".equals(args[0])){
            publisher(Paths.get(args[1]),
                LocalAccountEpochNegativeHeadPublication.Stage
                    .valueOf(args[2]),Paths.get(args[3]),
                Paths.get(args[4]));
            return;
        }
        if(args.length==5&&"--attack".equals(args[0])){
            attacker(Paths.get(args[1]),Attack.valueOf(args[2]),
                Paths.get(args[3]),Paths.get(args[4]));
            return;
        }
        if(args.length==3&&"--observe".equals(args[0])){
            observer(Paths.get(args[1]),Attack.valueOf(args[2]));
            return;
        }
        check(args.length==0,"unexpected CLI arguments");
        Path root=Files.createTempDirectory("r159-head-foreign-jvm-");
        try{
            for(boolean existing:new boolean[]{false,true})
                for(LocalAccountEpochNegativeHeadPublication.Stage stage:
                        new LocalAccountEpochNegativeHeadPublication.Stage[]{
                            LocalAccountEpochNegativeHeadPublication.Stage
                                .HEAD_WRITTEN_BEFORE_FILE_FORCE,
                            LocalAccountEpochNegativeHeadPublication.Stage
                                .HEAD_FILE_FORCED_BEFORE_DIRECTORY_FORCE,
                            LocalAccountEpochNegativeHeadPublication.Stage
                                .HEAD_DIRECTORY_FORCED_BEFORE_READBACK})
                    for(Attack attack:Attack.values())
                        oneCase(root,existing,stage,attack);
            check(cases==12,"expected twelve separate-JVM edits");
            System.out.println("R159_FOREIGN_JVM_IDENTITY_MATRIX_PASS"+
                " cases=12 publisherJvms=12 attackerJvms=12"+
                " independentObserverJvms=12 noPositiveAuthority=true"+
                " noWorldLoginOrPowerLossProof=true");
        }finally{
            // Dispose only the temporary test-fixture tree.
            try(Stream<Path> tree=Files.walk(root)){
                for(Path item:tree.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(item);
            }
        }
    }
}
