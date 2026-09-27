package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Path-only retirement ownership for private Kotlin script classpath snapshots.
 *
 * This owner never retains Plugin, PluginRuntime or ClassLoader references.
 */
final class KotlinClasspathCleanupDebt {
    private static final ArrayList<Debt> DEBTS=
        new ArrayList<>();

    static int count(){
        synchronized(DEBTS){
            return DEBTS.size();
        }
    }

    static void register(
        Path root,
        List<Path> files
    ){
        Path cleanRoot=
            Objects.requireNonNull(
                root,
                "root"
            ).toAbsolutePath()
                .normalize();
        ArrayList<Path> cleanFiles=
            new ArrayList<>();

        for(Path file:
                Objects.requireNonNull(
                    files,
                    "files"
                ))
            cleanFiles.add(
                Objects.requireNonNull(
                    file,
                    "file"
                ).toAbsolutePath()
                    .normalize()
            );

        synchronized(DEBTS){
            for(Debt existing:DEBTS)
                if(existing.root.equals(
                        cleanRoot))
                    return;

            DEBTS.add(
                new Debt(
                    cleanRoot,
                    cleanFiles
                )
            );
        }
    }

    static Throwable retryOnce(
        Throwable primary
    ){
        synchronized(DEBTS){
            Throwable aggregate=primary;
            Iterator<Debt> iterator=
                DEBTS.iterator();

            while(iterator.hasNext()){
                Debt debt=iterator.next();
                Throwable retry=
                    debt.retire();

                if(retry==null){
                    iterator.remove();
                    continue;
                }

                if(aggregate==null)
                    aggregate=retry;
                else
                    PluginRuntimeSupport
                        .suppressIfDistinct(
                            aggregate,
                            retry
                        );

                System.err.println(
                    "[plugins] Kotlin classpath cleanup debt retry failed root="+
                    debt.root+
                    " errorClass="+
                    retry.getClass()
                        .getName()
                );
            }

            return aggregate;
        }
    }

    static Throwable retire(
        Path root,
        List<Path> files
    ){
        Throwable failure=null;

        for(int i=files.size()-1;
            i>=0;
            i--)
            try{
                Files.deleteIfExists(
                    files.get(i)
                );
            }catch(Throwable cleanup){
                if(failure==null)
                    failure=cleanup;
                else
                    PluginRuntimeSupport
                        .suppressIfDistinct(
                            failure,
                            cleanup
                        );
            }

        try{
            Files.deleteIfExists(
                root
            );
        }catch(Throwable cleanup){
            if(failure==null)
                failure=cleanup;
            else
                PluginRuntimeSupport
                    .suppressIfDistinct(
                        failure,
                        cleanup
                    );
        }

        return failure;
    }

    private static final class Debt {
        final Path root;
        final List<Path> files;

        Debt(
            Path root,
            List<Path> files
        ){
            this.root=root;
            this.files=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        files
                    )
                );
        }

        Throwable retire(){
            return KotlinClasspathCleanupDebt
                .retire(
                    root,
                    files
                );
        }
    }

    private KotlinClasspathCleanupDebt(){}
}
