package spk.local;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

public final class LocalAuxArchivePathTest {
    public static void main(
        String[] args
    ){
        String home=
            Paths.get(
                "fixture-home"
            ).toAbsolutePath()
             .normalize()
             .toString();

        assertPath(
            home,
            "/cache.zip",
            "cache.zip"
        );
        assertPath(
            home,
            "/sprites.zip",
            "sprites.zip"
        );
        assertPath(
            home,
            "/configs.zip",
            "configs.zip"
        );

        AtomicInteger unknownHomeCalls=
            new AtomicInteger();

        Path unknown=
            LocalAuxArchivePath.resolve(
                "/other.bin",
                ()->{
                    unknownHomeCalls.incrementAndGet();
                    return home;
                }
            );

        if(unknown!=null||
           unknownHomeCalls.get()!=0)
            throw new AssertionError(
                "unknown archive target consulted home or resolved path"
            );

        if(LocalAuxArchivePath.resolve(
                "/cache.zip",
                ()->null
            )!=null)
            throw new AssertionError(
                "missing user.home did not fail closed"
            );

        if(LocalAuxArchivePath.resolve(
                "/cache.zip",
                ()->""
            )!=null)
            throw new AssertionError(
                "empty user.home did not fail closed"
            );

        if(LocalAuxArchivePath.resolve(
                "/cache.zip",
                ()->{
                    throw new SecurityException(
                        "fixture-home-security"
                    );
                }
            )!=null)
            throw new AssertionError(
                "user.home SecurityException did not fail closed"
            );

        if(LocalAuxArchivePath.resolve(
                "/cache.zip",
                ()->
                    "bad"+
                    (char)0+
                    "home"
            )!=null)
            throw new AssertionError(
                "invalid user.home path did not fail closed"
            );

        RuntimeException expected=
            new IllegalStateException(
                "fixture-home-runtime"
            );
        Throwable observed=null;

        try{
            LocalAuxArchivePath.resolve(
                "/cache.zip",
                ()->{ throw expected; }
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected)
            throw new AssertionError(
                "unrelated home RuntimeException was swallowed or wrapped",
                observed
            );

        Error fatal=
            new AssertionError(
                "fixture-home-error"
            );
        observed=null;

        try{
            LocalAuxArchivePath.resolve(
                "/cache.zip",
                ()->{ throw fatal; }
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=fatal)
            throw new AssertionError(
                "unrelated home Error was swallowed or wrapped",
                observed
            );

        System.out.println(
            "LOCAL_AUX_ARCHIVE_PATH_PASS "+
            "cache=true "+
            "sprites=true "+
            "configs=true "+
            "unknownNoLookup=true "+
            "missingHomeUnavailable=true "+
            "emptyHomeUnavailable=true "+
            "homeSecurityUnavailable=true "+
            "invalidHomeUnavailable=true "+
            "runtimeUnswept=true "+
            "fatalUnswept=true"
        );
    }

    private static void assertPath(
        String home,
        String target,
        String name
    ){
        Path expected=
            Paths.get(
                home,
                ".spawnpk",
                name
            );
        Path actual=
            LocalAuxArchivePath.resolve(
                target,
                ()->home
            );

        if(!expected.equals(
                actual))
            throw new AssertionError(
                "archive path mismatch target="+
                target+
                " expected="+
                expected+
                " actual="+
                actual
            );
    }

    private LocalAuxArchivePathTest(){}
}
