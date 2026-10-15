package spk.local;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.227 TEST-ONLY: two distinct normalized account paths can refer
 * to ONE inode (hard-link aliases), yet receive separate live JVM
 * and OS session ownership. This is not an ordinary TCP exploit proof.
 */
public final class G21227CoreHardlinkAliasTwoWorldOwnerGapTest {
    private G21227CoreHardlinkAliasTwoWorldOwnerGapTest(){}
    private static final String USER="g21227-alias";
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.227 "+why);
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21227-hardlink-alias-");
        String[] flags={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] previous=new String[flags.length];
        for(int i=0;i<flags.length;i++){
            previous[i]=System.getProperty(flags[i]);
            System.setProperty(flags[i],"true");
        }
        World a=null,b=null;
        WorldPlayer playerA=null,playerB=null;
        LocalJvmAccountSessionLease.Token leaseA=null,leaseB=null;
        long generationA=-1L,generationB=-1L;
        try{
            Path first=root.resolve("same-inode-A.properties");
            Path alias=root.resolve("same-inode-B.properties");
            Files.writeString(first,"test-only-existing-account-inode");
            Files.createLink(alias,first);
            check(!first.equals(alias)&&Files.isSameFile(first,alias),
                "hard-link fixture does not resolve to one file object");
            Object keyA=Files.readAttributes(
                first,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).fileKey();
            Object keyB=Files.readAttributes(
                alias,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).fileKey();
            check(keyA!=null&&keyA.equals(keyB),
                "alias fixture has no stable shared fileKey");

            // The normal named-account resolver does not expose
            // arbitrary aliases. These isolated test repositories do:
            // this characterizes the actual exact-PATH lease boundary.
            FilePlayerRepository repoA=new FilePlayerRepository(
                ignored->first);
            FilePlayerRepository repoB=new FilePlayerRepository(
                ignored->alias);
            a=World.isolatedForTest(60_000L,repoA);
            b=World.isolatedForTest(60_000L,repoB);
            leaseA=LocalJvmAccountSessionLease.acquire(
                a.persistence(),USER);
            check(leaseA!=null,"first opt-in owner not acquired");
            playerA=new WorldPlayer();
            generationA=a.registerPlayer(playerA,USER);
            leaseA.bindAdmittedWorldOwner(playerA,generationA);
            a.start();

            // Both G21.185 stable sibling lock NAMES differ even
            // though the source account file is the same inode.
            leaseB=LocalJvmAccountSessionLease.acquire(
                b.persistence(),USER);
            check(leaseB!=null,
                "second alias owner unexpectedly rejected");
            playerB=new WorldPlayer();
            generationB=b.registerPlayer(playerB,USER);
            leaseB.bindAdmittedWorldOwner(playerB,generationB);
            b.start();

            check(LocalJvmAccountSessionLease.activeCountForTest()==2,
                "two distinct alias owners were not both admitted");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                first,playerA,generationA);
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                alias,playerB,generationB);
            Path firstLock=first.resolveSibling(
                first.getFileName().toString()+".session.lck");
            Path secondLock=alias.resolveSibling(
                alias.getFileName().toString()+".session.lck");
            check(Files.isRegularFile(firstLock,LinkOption.NOFOLLOW_LINKS)&&
                  Files.isRegularFile(secondLock,LinkOption.NOFOLLOW_LINKS),
                "two independent OS lock domains not held");
            check(Files.isSameFile(first,alias),
                "test accidentally replaced/shared inode before proof");

            System.out.println(
                "G21227_HARDLINK_ALIAS_TWO_WORLD_OWNERS_GAP_PASS"+
                " twoRealWorldGenerations=true"+
                " actualHardlinkSameFileKey=true"+
                " differentNormalizedPaths=true"+
                " bothJvmTokensCurrent=true"+
                " twoDifferentOsSessionLockNames=true"+
                " aliasesAdmittedSimultaneously=true"+
                " productionTcpAliasPathSupported=false"+
                " authenticatedFileObjectEpoch=false"+
                " ownerRecoveryAuthority=false");
        }finally{
            try{
                if(a!=null&&playerA!=null&&generationA>0L)
                    a.unregisterPlayer(playerA,generationA);
                if(b!=null&&playerB!=null&&generationB>0L)
                    b.unregisterPlayer(playerB,generationB);
                if(leaseA!=null)leaseA.releaseAfterInitializationFailure();
                if(leaseB!=null)leaseB.releaseAfterInitializationFailure();
                if(a!=null)a.close();
                if(b!=null)b.close();
            }finally{
                for(int i=0;i<flags.length;i++){
                    if(previous[i]==null)System.clearProperty(flags[i]);
                    else System.setProperty(flags[i],previous[i]);
                }
                try(Stream<Path> paths=Files.walk(root)){
                    for(Path item:paths.sorted(
                            Comparator.reverseOrder()).toArray(Path[]::new))
                        Files.deleteIfExists(item);
                }
            }
        }
    }
}
