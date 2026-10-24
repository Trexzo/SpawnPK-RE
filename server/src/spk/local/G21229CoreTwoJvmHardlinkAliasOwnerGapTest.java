package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.229 TEST ONLY. Separate JVMs can each admit a different path to
 * one hardlinked account inode while their independent OS session lock
 * siblings and same-JVM fileKey registries never coordinate.
 *
 * Isolated test path resolvers only; ordinary TCP aliases not claimed.
 */
public final class G21229CoreTwoJvmHardlinkAliasOwnerGapTest {
    private G21229CoreTwoJvmHardlinkAliasOwnerGapTest(){}
    private static final String USER="g21229-hardlinked-owner";
    private static final String READY="CHILD_ALIAS_WORLD_SESSION_HELD";
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.229 "+why);
    }
    private static void flags(){
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,"true");
    }

    private static void child(Path source,Path alias,Path ready,Path finish)
        throws Exception{
        flags();
        check(Files.isSameFile(source,alias),
            "child sees no actual hardlink alias");
        World world=World.isolatedForTest(60_000L,
            new FilePlayerRepository(ignored->alias));
        LocalJvmAccountSessionLease.Token token=null;
        WorldPlayer player=null;
        long generation=-1L;
        try{
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(token!=null,
                "second JVM alias token denied unexpectedly");
            player=new WorldPlayer();
            generation=world.registerPlayer(player,USER);
            token.bindAdmittedWorldOwner(player,generation);
            world.start();
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                alias,player,generation);
            check(Files.isSameFile(source,alias),
                "hardlinked files diverged during admission");
            check(Files.isRegularFile(alias.resolveSibling(
                    alias.getFileName().toString()+".session.lck"),
                LinkOption.NOFOLLOW_LINKS),
                "child OS lock filename missing");
            // Publish readiness only after bytes are complete. A direct
            // write creates the marker before writing its content, so the
            // parent can observe a zero-length file on loaded Linux CI.
            Path partial=ready.resolveSibling(
                ready.getFileName().toString()+".partial");
            Files.writeString(partial,READY,StandardCharsets.UTF_8);
            Files.move(partial,ready,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            long stop=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(!Files.exists(finish,LinkOption.NOFOLLOW_LINKS)){
                check(System.nanoTime()<stop,"child coordination timed out");
                Thread.sleep(25L);
            }
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "child lost its real alias owner while parent held one");
            System.out.println(
                "G21229_CHILD_HARDLINK_ALIAS_OWNER_HELD"+
                " distinctJvm=true sameAccountInode=true"+
                " independentOsLockName=true");
        }finally{
            if(player!=null&&generation>0L)
                world.unregisterPlayer(player,generation);
            if(token!=null)token.releaseAfterInitializationFailure();
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==5&&"--child".equals(args[0])){
            child(Path.of(args[1]),Path.of(args[2]),
                Path.of(args[3]),Path.of(args[4]));
            return;
        }
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21229-hardlink-");
        String[] props={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY
        };
        String[] previous=new String[props.length];
        for(int i=0;i<props.length;i++)
            previous[i]=System.getProperty(props[i]);
        flags();
        Path source=root.resolve("same-inode-A.properties");
        Path alias=root.resolve("same-inode-B.properties");
        Path ready=root.resolve("child-ready.txt");
        Path finish=root.resolve("child-release.txt");
        World world=null;
        WorldPlayer player=null;
        LocalJvmAccountSessionLease.Token token=null;
        Process process=null;
        long generation=-1L;
        try{
            Files.writeString(source,"test-only-account-inode");
            Files.createLink(alias,source);
            check(!source.equals(alias)&&Files.isSameFile(source,alias),
                "two paths are not actual hardlinks");
            world=World.isolatedForTest(60_000L,
                new FilePlayerRepository(ignored->source));
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(token!=null,"parent token missing");
            player=new WorldPlayer();
            generation=world.registerPlayer(player,USER);
            token.bindAdmittedWorldOwner(player,generation);
            world.start();

            Path java=Path.of(System.getProperty("java.home"),"bin",
                "java"+(System.getProperty("os.name").toLowerCase()
                    .contains("win")?".exe":""));
            ProcessBuilder builder=new ProcessBuilder(
                java.toString(),"-cp",System.getProperty("java.class.path"),
                G21229CoreTwoJvmHardlinkAliasOwnerGapTest.class.getName(),
                "--child",source.toString(),alias.toString(),
                ready.toString(),finish.toString());
            builder.redirectErrorStream(true);
            process=builder.start();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while(true){
                if(Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS)){
                    String marker=Files.readString(
                        ready,StandardCharsets.UTF_8);
                    if(READY.equals(marker))break;
                    check(READY.startsWith(marker),
                        "unexpected readiness marker content");
                }
                check(process.isAlive(),
                    "child JVM died before held alias owner");
                check(System.nanoTime()<deadline,
                    "child alias owner never became ready");
                Thread.sleep(30L);
            }
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "parent JVM original owner registry changed");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                source,player,generation);
            check(Files.isSameFile(source,alias),
                "hardlink identity changed");
            check(Files.isRegularFile(source.resolveSibling(
                    source.getFileName().toString()+".session.lck"),
                    LinkOption.NOFOLLOW_LINKS),"parent stable lock missing");
            check(Files.isRegularFile(alias.resolveSibling(
                    alias.getFileName().toString()+".session.lck"),
                    LinkOption.NOFOLLOW_LINKS),"child independent lock missing");
            Files.writeString(finish,"release",StandardCharsets.UTF_8);
            check(process.waitFor(10L,TimeUnit.SECONDS),
                "child process did not exit after marker");
            String output=new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
            check(process.exitValue()==0&&output.contains(
                    "G21229_CHILD_HARDLINK_ALIAS_OWNER_HELD"),
                "child owner proof failed: "+output);

            System.out.println(
                "G21229_TWO_JVM_HARDLINK_INODE_ALIAS_OWNER_GAP_PASS"+
                " twoRealJavaProcesses=true"+
                " sharedHardlinkedAccountInode=true"+
                " independentSessionLockNames=true"+
                " bothWorldGenerationsAdmitted=true"+
                " bothOptInJvmFileKeyGuardsOn=true"+
                " crossJvmInodeEpochImplemented=false"+
                " normalTcpAliasResolverUsed=false"+
                " durableRecovery=false"+
                " rawExternalWriterExclusion=false");
        }finally{
            try{
                Files.writeString(finish,"release",StandardCharsets.UTF_8);
                if(process!=null&&process.isAlive()){
                    process.destroyForcibly();
                    process.waitFor(5L,TimeUnit.SECONDS);
                }
                if(world!=null&&player!=null&&generation>0L)
                    world.unregisterPlayer(player,generation);
                if(token!=null)token.releaseAfterInitializationFailure();
                if(world!=null)world.close();
            }finally{
                for(int i=0;i<props.length;i++){
                    if(previous[i]==null)System.clearProperty(props[i]);
                    else System.setProperty(props[i],previous[i]);
                }
                try(Stream<Path> walk=Files.walk(root)){
                    for(Path path:walk.sorted(Comparator.reverseOrder())
                            .toArray(Path[]::new))
                        Files.deleteIfExists(path);
                }
            }
        }
    }
}
