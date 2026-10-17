package spk.local;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import spk.plugin.api.PluginHandle;
import spk.plugin.api.PluginManager;

/**
 * Deterministic plugins/kotlin discovery and lifecycle entrypoint.
 */
final class KotlinPluginDirectory {
    static final String API_JAR_PROPERTY =
        "spk.plugin.apiJar";
    static final String SCRIPT_RUNTIME_PROPERTY =
        "spk.kotlinScript.runtimeJar";

    interface SourceCaptureHook {
        void beforeOpen(Path source)throws IOException;
        void afterCapture(PluginSource source)throws IOException;
    }

    private static final SourceCaptureHook NO_CAPTURE_HOOK =
        new SourceCaptureHook() {
            @Override public void beforeOpen(Path source){}
            @Override public void afterCapture(PluginSource source){}
        };

    static int loadStartup(
        World world,
        Path root
    )throws Exception{
        List<Path> scripts=
            discover(root);

        if(scripts.isEmpty()){
            System.out.println(
                "KOTLIN_PLUGIN_STARTUP_PASS count=0 directory="+
                normalizeRoot(root)
            );
            return 0;
        }

        return loadStartup(
            world,
            root,
            defaultLoader(),
            scripts,
            NO_CAPTURE_HOOK
        );
    }

    static PluginHandle loadOnDemand(
        World world,
        Path root,
        Path script
    )throws Exception{
        return loadOnDemand(
            world,
            root,
            script,
            defaultLoader()
        );
    }

    static int loadStartup(
        World world,
        Path root,
        PluginLoader loader
    )throws Exception{
        return loadStartup(
            world,
            root,
            loader,
            discover(root),
            NO_CAPTURE_HOOK
        );
    }

    static PluginHandle loadOnDemand(
        World world,
        Path root,
        Path script,
        PluginLoader loader
    )throws Exception{
        if(world==null)
            throw new IllegalArgumentException(
                "world"
            );
        if(loader==null)
            throw new IllegalArgumentException(
                "loader"
            );

        PluginSource source=
            captureScriptWithinRoot(
                root,
                script,
                NO_CAPTURE_HOOK
            );

        PluginRuntime runtime=
            loader.load(
                source
            );

        PluginHandle handle=
            world.plugins()
                .enable(runtime);

        System.out.println(
            "KOTLIN_PLUGIN_ON_DEMAND_PASS id="+
            handle.manifest().id()+
            " source="+
            source.path()
        );

        return handle;
    }

    static int loadStartup(
        World world,
        Path root,
        PluginLoader loader,
        SourceCaptureHook hook
    )throws Exception{
        return loadStartup(
            world,
            root,
            loader,
            discover(root),
            hook
        );
    }

    static PluginHandle loadOnDemand(
        World world,
        Path root,
        Path script,
        PluginLoader loader,
        SourceCaptureHook hook
    )throws Exception{
        if(world==null)
            throw new IllegalArgumentException(
                "world"
            );
        if(loader==null)
            throw new IllegalArgumentException(
                "loader"
            );

        PluginSource source=
            captureScriptWithinRoot(
                root,
                script,
                hook
            );

        return world.plugins()
            .enable(
                loader.load(
                    source
                )
            );
    }

    static List<Path> discover(
        Path root
    )throws IOException{
        Path directory=
            normalizeRoot(root);

        rejectSymlinkComponents(
            directory
        );

        Files.createDirectories(
            directory
        );

        rejectSymlinkComponents(
            directory
        );

        ArrayList<Path> scripts=
            new ArrayList<>();

        try(DirectoryStream<Path> entries=
                Files.newDirectoryStream(
                    directory
                )){
            for(Path entry:entries){
                String name=
                    entry.getFileName()
                        .toString()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(!name.endsWith(
                        ".kts"))
                    continue;

                if(Files.isSymbolicLink(
                        entry))
                    throw new IOException(
                        "Kotlin plugin symlink is not allowed: "+
                        entry
                    );

                if(!Files.isRegularFile(
                        entry,
                        LinkOption.NOFOLLOW_LINKS))
                    continue;

                scripts.add(
                    entry.toAbsolutePath()
                        .normalize()
                );
            }
        }

        scripts.sort(
            Comparator
                .comparing(
                    (Path path)->
                        path.getFileName()
                            .toString(),
                    String.CASE_INSENSITIVE_ORDER
                )
                .thenComparing(
                    path->
                        path.getFileName()
                            .toString()
                )
        );

        return java.util.Collections
            .unmodifiableList(
                scripts
            );
    }

    private static int loadStartup(
        World world,
        Path root,
        PluginLoader loader,
        List<Path> scripts,
        SourceCaptureHook hook
    )throws Exception{
        if(world==null)
            throw new IllegalArgumentException(
                "world"
            );
        if(loader==null)
            throw new IllegalArgumentException(
                "loader"
            );

        ArrayList<PluginRuntime> runtimes=
            new ArrayList<>();

        try{
            for(Path script:scripts)
                runtimes.add(
                    loader.load(
                        captureScriptWithinRoot(
                            root,
                            script,
                            hook
                        )
                    )
                );
        }catch(Throwable failure){
            closeUnowned(
                runtimes,
                failure
            );

            if(failure instanceof Exception)
                throw (Exception)failure;
            if(failure instanceof Error)
                throw (Error)failure;

            throw new RuntimeException(
                failure
            );
        }

        if(runtimes.isEmpty()){
            System.out.println(
                "KOTLIN_PLUGIN_STARTUP_PASS count=0 directory="+
                normalizeRoot(root)
            );
            return 0;
        }

        PluginManager manager=
            world.plugins();

        List<PluginHandle> handles=
            manager.enableAll(
                runtimes
            );

        if(handles.size()!=
                runtimes.size()){
            for(int i=handles.size()-1;
                i>=0;
                i--)
                manager.disable(
                    handles.get(i)
                        .manifest()
                        .id()
                );

            throw new IllegalStateException(
                "Kotlin plugin startup handle count mismatch expected="+
                runtimes.size()+
                " actual="+
                handles.size()
            );
        }

        System.out.println(
            "KOTLIN_PLUGIN_STARTUP_PASS count="+
            handles.size()+
            " directory="+
            normalizeRoot(root)
        );

        return handles.size();
    }

    private static PluginSource
        captureScriptWithinRoot(
            Path root,
            Path script,
            SourceCaptureHook hook
        )throws IOException{
        Path directory=
            normalizeRoot(root);

        rejectSymlinkComponents(
            directory
        );

        Path source=
            script.toAbsolutePath()
                .normalize();

        if(!source.getParent()
                .equals(
                    directory
                ))
            throw new IOException(
                "Kotlin plugin must be a direct child of "+
                directory+
                ": "+
                source
            );

        rejectSymlinkComponents(
            source
        );

        String name=
            source.getFileName()
                .toString()
                .toLowerCase(
                    Locale.ROOT
                );

        if(!name.endsWith(
                ".kts"))
            throw new IOException(
                "Kotlin plugin must end with .kts: "+
                source
            );

        if(!Files.isRegularFile(
                source,
                LinkOption.NOFOLLOW_LINKS))
            throw new IOException(
                "Kotlin plugin script missing or non-regular: "+
                source
            );

        SourceCaptureHook captureHook=
            hook==null
                ?NO_CAPTURE_HOOK
                :hook;

        captureHook.beforeOpen(
            source
        );

        byte[] bytes=
            capturePinnedRegularFile(
                source
            );

        PluginSource snapshot=
            PluginSource.scriptSnapshot(
                source,
                new String(
                    bytes,
                    StandardCharsets.UTF_8
                )
            );

        captureHook.afterCapture(
            snapshot
        );

        return snapshot;
    }

    private static byte[] capturePinnedRegularFile(
        Path source
    )throws IOException{
        Path pinned=null;

        for(int attempt=0;
            attempt<16;
            attempt++){
            Path candidate=
                source.resolveSibling(
                    ".spawnpk-kts-capture-"+
                    java.util.UUID
                        .randomUUID()
                        .toString()+
                    ".tmp"
                );

            try{
                Files.createLink(
                    candidate,
                    source
                );
                pinned=candidate;
                break;
            }catch(java.nio.file.FileAlreadyExistsException collision){
                continue;
            }catch(UnsupportedOperationException unsupported){
                throw new IOException(
                    "Kotlin plugin filesystem cannot pin source identity with a hard link: "+
                    source,
                    unsupported
                );
            }
        }

        if(pinned==null)
            throw new IOException(
                "Could not allocate Kotlin plugin source identity pin: "+
                source
            );

        IOException primary=null;

        try{
            if(Files.isSymbolicLink(
                    source)||
               Files.isSymbolicLink(
                    pinned)||
               !Files.isRegularFile(
                    source,
                    LinkOption.NOFOLLOW_LINKS)||
               !Files.isRegularFile(
                    pinned,
                    LinkOption.NOFOLLOW_LINKS)||
               !Files.isSameFile(
                    source,
                    pinned))
                throw new IOException(
                    "Kotlin plugin final source identity is not the admitted regular direct child: "+
                    source
                );

            java.util.HashSet<OpenOption> options=
                new java.util.HashSet<>();
            options.add(
                StandardOpenOption.READ
            );
            options.add(
                LinkOption.NOFOLLOW_LINKS
            );

            try(SeekableByteChannel channel=
                    Files.newByteChannel(
                        pinned,
                        options
                    )){
                java.io.ByteArrayOutputStream out=
                    new java.io.ByteArrayOutputStream();
                ByteBuffer buffer=
                    ByteBuffer.allocate(
                        8192
                    );

                while(true){
                    int read=
                        channel.read(
                            buffer
                        );

                    if(read<0)
                        break;
                    if(read==0)
                        continue;

                    out.write(
                        buffer.array(),
                        0,
                        read
                    );
                    buffer.clear();
                }

                return out.toByteArray();
            }
        }catch(IOException denied){
            primary=
                new IOException(
                    "Kotlin plugin final source capture failed closed: "+
                    source,
                    denied
                );
            throw primary;
        }finally{
            try{
                Files.deleteIfExists(
                    pinned
                );
            }catch(IOException cleanup){
                if(primary!=null)
                    primary.addSuppressed(
                        cleanup
                    );
                else
                    throw cleanup;
            }
        }
    }

    private static void rejectSymlinkComponents(
        Path path
    )throws IOException{
        Path absolute=
            path.toAbsolutePath()
                .normalize();
        Path current=
            absolute.getRoot();

        for(Path component:absolute){
            current=
                current==null
                    ?component
                    :current.resolve(
                        component
                    );

            if(Files.exists(
                    current,
                    LinkOption.NOFOLLOW_LINKS)&&
               Files.isSymbolicLink(
                    current))
                throw new IOException(
                    "Kotlin plugin path symlink component is not allowed: "+
                    current
                );
        }
    }

    private static Path normalizeRoot(
        Path root
    ){
        if(root==null)
            throw new IllegalArgumentException(
                "root"
            );

        return root.toAbsolutePath()
            .normalize();
    }

    private static void closeUnowned(
        List<PluginRuntime> runtimes,
        Throwable primary
    ){
        java.util.Set<PluginRuntime> closed=
            java.util.Collections
                .newSetFromMap(
                    new java.util.IdentityHashMap<>()
                );

        for(int i=runtimes.size()-1;
            i>=0;
            i--){
            PluginRuntime runtime=
                runtimes.get(i);

            if(closed.add(
                    runtime))
                PluginRuntimeSupport
                    .closePluginRuntime(
                        runtime,
                        primary
                    );
        }
    }

    private static PluginLoader
        defaultLoader()
        throws Exception{
        Path apiJar=
            artifact(
                API_JAR_PROPERTY,
                "SpawnPKPluginApi.jar"
            );
        Path runtimeJar=
            artifact(
                SCRIPT_RUNTIME_PROPERTY,
                "SpawnPKKotlinScriptRuntime.jar"
            );

        Class<?> type=
            Class.forName(
                "spk.local.KotlinPluginLoader"
            );

        if(!PluginLoader.class
                .isAssignableFrom(
                    type
                ))
            throw new IllegalStateException(
                "KotlinPluginLoader does not implement PluginLoader"
            );

        Constructor<?> constructor=
            type.getConstructor(
                Path.class,
                List.class
            );

        try{
            return (PluginLoader)
                constructor.newInstance(
                    apiJar,
                    java.util.Collections
                        .singletonList(
                            runtimeJar
                        )
                );
        }catch(InvocationTargetException error){
            Throwable cause=
                error.getCause();

            if(cause instanceof Exception)
                throw (Exception)cause;
            if(cause instanceof Error)
                throw (Error)cause;

            throw error;
        }
    }

    private static Path artifact(
        String property,
        String siblingName
    )throws Exception{
        String explicit=
            System.getProperty(
                property
            );

        Path path;

        if(explicit!=null &&
           !explicit.trim().isEmpty())
            path=
                Paths.get(
                    explicit.trim()
                );
        else{
            URI location=
                Main.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();
            Path codeSource=
                Paths.get(location)
                    .toAbsolutePath()
                    .normalize();
            Path directory=
                Files.isDirectory(
                    codeSource
                )
                    ?codeSource
                    :codeSource.getParent();

            if(directory==null)
                throw new IOException(
                    "Could not resolve server artifact directory from "+
                    codeSource
                );

            path=
                directory.resolve(
                    siblingName
                );
        }

        path=
            path.toAbsolutePath()
                .normalize();

        if(!Files.isRegularFile(
                path))
            throw new IOException(
                "Missing Kotlin plugin artifact "+
                siblingName+
                "; set -D"+
                property+
                "=<path> or place it beside SpawnPKLocalServer.jar: "+
                path
            );

        return path;
    }

    private KotlinPluginDirectory(){}
}
