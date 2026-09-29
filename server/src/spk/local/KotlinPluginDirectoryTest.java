package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;
import spk.content.api.ContentResult;
import spk.event.DomainEventBus;
import spk.plugin.api.Plugin;
import spk.plugin.api.PluginApiVersion;
import spk.plugin.api.PluginContext;
import spk.plugin.api.PluginHandle;
import spk.plugin.api.PluginManager;
import spk.plugin.api.PluginManifest;

public final class KotlinPluginDirectoryTest {
    public static final class ProbeEvent
        implements DomainEventBus.Cancellable {
        private boolean cancelled;

        @Override public boolean isCancelled(){
            return cancelled;
        }

        @Override public void cancel(){
            cancelled=true;
        }
    }

    public static void main(
        String[] args
    )throws Exception{
        if(args.length!=3)
            throw new IllegalArgumentException(
                "expected healthy script, API JAR and Kotlin script runtime JAR"
            );

        Path fixture=
            Paths.get(args[0])
                .toAbsolutePath()
                .normalize();
        Path apiJar=
            Paths.get(args[1])
                .toAbsolutePath()
                .normalize();
        Path runtimeJar=
            Paths.get(args[2])
                .toAbsolutePath()
                .normalize();

        Path temp=
            Files.createTempDirectory(
                "kotlin-plugin-directory-"
            );
        Path root=
            temp.resolve(
                "plugins"
            ).resolve(
                "kotlin"
            );

        String oldApi=
            System.getProperty(
                KotlinPluginDirectory
                    .API_JAR_PROPERTY
            );
        String oldRuntime=
            System.getProperty(
                KotlinPluginDirectory
                    .SCRIPT_RUNTIME_PROPERTY
            );

        World world=
            World.isolatedForTest(
                25L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayerAndStart(
                player,
                "kotlin-directory-player"
            );
        ServerPacketWriter writer=
            new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(
                    new int[]{4,3,2,1}
                )
            );

        boolean rootSymlinkChecked=false;
        boolean ancestorSymlinkChecked=false;
        boolean onDemandSwapChecked=false;
        boolean startupSwapChecked=false;
        boolean onDemandNonRegularSwapChecked=false;
        boolean startupNonRegularSwapChecked=false;

        try{
            int empty=
                KotlinPluginDirectory
                    .loadStartup(
                        world,
                        root
                    );

            if(empty!=0)
                throw new AssertionError(
                    "empty Kotlin directory loaded plugins: "+
                    empty
                );

            assertPreloadFailureCleanup(
                world,
                temp.resolve(
                    "failure-root"
                )
            );

            assertPreloadRuntimeCloseIdentityOnce(
                world,
                temp.resolve(
                    "identity-once-root"
                )
            );

            assertPreloadThrowingDuplicateCloseOnce(
                world,
                temp.resolve(
                    "identity-throw-root"
                )
            );

            assertPreloadDistinctReverseCleanup(
                world,
                temp.resolve(
                    "identity-reverse-root"
                )
            );

            rootSymlinkChecked=
                assertRootSymlinkRejected(
                    world,
                    temp
                );

            ancestorSymlinkChecked=
                assertAncestorSymlinkRejected(
                    world,
                    temp
                );

            onDemandSwapChecked=
                assertFinalChildSwapRejected(
                    world,
                    temp.resolve(
                        "swap-ondemand"
                    ),
                    false
                );

            startupSwapChecked=
                assertFinalChildSwapRejected(
                    world,
                    temp.resolve(
                        "swap-startup"
                    ),
                    true
                );

            onDemandNonRegularSwapChecked=
                assertFinalChildNonRegularSwapRejected(
                    world,
                    temp.resolve(
                        "nonregular-ondemand"
                    ),
                    false
                );

            startupNonRegularSwapChecked=
                assertFinalChildNonRegularSwapRejected(
                    world,
                    temp.resolve(
                        "nonregular-startup"
                    ),
                    true
                );

            assertSnapshotDetachedFromPath(
                world,
                temp.resolve(
                    "snapshot-detached"
                )
            );

            System.setProperty(
                KotlinPluginDirectory
                    .API_JAR_PROPERTY,
                apiJar.toString()
            );
            System.setProperty(
                KotlinPluginDirectory
                    .SCRIPT_RUNTIME_PROPERTY,
                runtimeJar.toString()
            );

            Path startup=
                root.resolve(
                    "01-startup.kts"
                );
            Files.copy(
                fixture,
                startup
            );
            Files.write(
                root.resolve(
                    "README.txt"
                ),
                java.util.Collections
                    .singletonList(
                        "ignored"
                    ),
                StandardCharsets.UTF_8
            );

            int count=
                KotlinPluginDirectory
                    .loadStartup(
                        world,
                        root
                    );

            if(count!=1)
                throw new AssertionError(
                    "startup Kotlin plugin count mismatch: "+
                    count
                );

            assertCommandAfterEvent(
                world,
                player,
                generation,
                writer,
                "kscript alpha beta",
                "KOTLIN_SCRIPT_EVENTS=1;args=alpha,beta;tccl=true"
            );

            String source=
                new String(
                    Files.readAllBytes(
                        fixture
                    ),
                    StandardCharsets.UTF_8
                );
            source=
                source.replace(
                    "fixture.kotlin.script",
                    "fixture.kotlin.script.ondemand"
                ).replace(
                    "\"kscript\"",
                    "\"kscript2\""
                ).replace(
                    "npcId = 301",
                    "npcId = 32001"
                ).replace(
                    "itemId = 201",
                    "itemId = 32002"
                ).replace(
                    "\"fixture.kotlin.button\"",
                    "\"fixture.kotlin.button.ondemand\""
                );

            Path onDemand=
                root.resolve(
                    "02-on-demand.kts"
                );
            Files.write(
                onDemand,
                source.getBytes(
                    StandardCharsets.UTF_8
                )
            );

            PluginHandle handle=
                KotlinPluginDirectory
                    .loadOnDemand(
                        world,
                        root,
                        onDemand
                    );

            if(handle==null||
               !handle.enabled()||
               !"fixture.kotlin.script.ondemand"
                    .equals(
                        handle.manifest()
                            .id()
                    ))
                throw new AssertionError(
                    "on-demand Kotlin plugin handle mismatch"
                );

            assertCommandAfterEvent(
                world,
                player,
                generation,
                writer,
                "kscript2 alpha beta",
                "KOTLIN_SCRIPT_EVENTS=1;args=alpha,beta;tccl=true"
            );

            boolean outsideDenied=false;
            Path outside=
                temp.resolve(
                    "outside.kts"
                );
            Files.copy(
                fixture,
                outside
            );

            try{
                KotlinPluginDirectory
                    .loadOnDemand(
                        world,
                        root,
                        outside
                    );
            }catch(IOException expected){
                outsideDenied=true;
            }

            if(!outsideDenied)
                throw new AssertionError(
                    "on-demand Kotlin loader accepted script outside plugins/kotlin"
                );

            PluginManager manager=
                world.plugins();

            if(!manager.disable(
                    "fixture.kotlin.script.ondemand"))
                throw new AssertionError(
                    "on-demand Kotlin plugin disable failed"
                );

            if(!manager.disable(
                    "fixture.kotlin.script"))
                throw new AssertionError(
                    "startup Kotlin plugin disable failed"
                );
        }finally{
            restore(
                KotlinPluginDirectory
                    .API_JAR_PROPERTY,
                oldApi
            );
            restore(
                KotlinPluginDirectory
                    .SCRIPT_RUNTIME_PROPERTY,
                oldRuntime
            );

            world.close();

            if(Files.exists(temp))
                try(Stream<Path> paths=
                        Files.walk(temp)){
                    paths.sorted(
                            Comparator.reverseOrder()
                        )
                        .forEach(
                            path->{
                                try{
                                    Files.deleteIfExists(
                                        path
                                    );
                                }catch(IOException error){
                                    throw new RuntimeException(
                                        error
                                    );
                                }
                            }
                        );
                }
        }

        System.out.println(
            "KOTLIN_PLUGIN_DIRECTORY_PASS "+
            "emptyStartup=true "+
            "startupDiscovery=true "+
            "deterministicTopLevel=true "+
            "productionArtifactLocator=true "+
            "deterministicPreloadOrder=true "+
            "preloadFailureCleanup=true "+
            "preloadRuntimeCloseIdentityOnce=true "+
            "rootSymlinkChecked="+rootSymlinkChecked+" "+
            "ancestorSymlinkChecked="+ancestorSymlinkChecked+" "+
            "onDemandSwapChecked="+onDemandSwapChecked+" "+
            "startupSwapChecked="+startupSwapChecked+" "+
            "onDemandNonRegularSwapChecked="+onDemandNonRegularSwapChecked+" "+
            "startupNonRegularSwapChecked="+startupNonRegularSwapChecked+" "+
            "captureRegularIdentityPinned=true "+
            "kotlinScriptSourceSnapshot=true "+
            "onDemand=true "+
            "rootConfinement=true "+
            "eventCallback=true "+
            "commandCallback=true"
        );
    }

    private static boolean assertFinalChildSwapRejected(
        World world,
        Path temp,
        boolean startup
    )throws Exception{
        Path root=
            temp.resolve(
                "plugins"
            ).resolve(
                "kotlin"
            );
        Files.createDirectories(
            root
        );

        Path outside=
            temp.resolve(
                "outside.kts"
            );
        Files.createDirectories(
            outside.getParent()
        );
        Files.write(
            outside,
            java.util.Collections.singletonList(
                "// outside snapshot must never be admitted"
            ),
            StandardCharsets.UTF_8
        );

        Path replacement=
            temp.resolve(
                "replacement-link.kts"
            );

        try{
            Files.createSymbolicLink(
                replacement,
                outside
            );
        }catch(UnsupportedOperationException|
               java.nio.file.FileSystemException|
               SecurityException unavailable){
            return false;
        }

        Path probe=
            root.resolve(
                "probe.kts"
            );
        Files.write(
            probe,
            java.util.Collections.singletonList(
                "// admitted A"
            ),
            StandardCharsets.UTF_8
        );

        SnapshotLoader loader=
            new SnapshotLoader(
                "// admitted A\n",
                "fixture.kotlin.snapshot.swap."+
                    (startup?"startup":"ondemand")
            );

        final boolean[] hookRan=
            new boolean[1];
        final boolean[] swapInstalled=
            new boolean[1];

        KotlinPluginDirectory.SourceCaptureHook hook=
            new KotlinPluginDirectory.SourceCaptureHook(){
                @Override public void beforeOpen(
                    Path source
                )throws IOException{
                    hookRan[0]=true;
                    Files.delete(
                        source
                    );
                    Files.move(
                        replacement,
                        source
                    );
                    swapInstalled[0]=true;
                }

                @Override public void afterCapture(
                    PluginSource source
                ){
                }
            };

        try{
            if(startup)
                KotlinPluginDirectory
                    .loadStartup(
                        world,
                        root,
                        loader,
                        hook
                    );
            else
                KotlinPluginDirectory
                    .loadOnDemand(
                        world,
                        root,
                        probe,
                        loader,
                        hook
                    );
        }catch(IOException expected){
            if(!hookRan[0])
                throw new AssertionError(
                    "final-child swap rejected before capture boundary",
                    expected
                );

            if(!swapInstalled[0])
                throw new AssertionError(
                    "final-child symlink swap fixture failed before capture",
                    expected
                );

            if(loader.loads!=0)
                throw new AssertionError(
                    "loader observed source after final-child symlink swap"
                );

            if(world.plugins().plugin(
                    loader.id)!=null)
                throw new AssertionError(
                    "plugin published after final-child symlink swap"
                );

            assertNoCapturePins(
                root,
                "symlink swap"
            );

            return true;
        }

        throw new AssertionError(
            "final-child symlink swap reached Kotlin loader"
        );
    }

    private static boolean
        assertFinalChildNonRegularSwapRejected(
            World world,
            Path temp,
            boolean startup
        )throws Exception{
        Path root=
            temp.resolve(
                "plugins"
            ).resolve(
                "kotlin"
            );
        Files.createDirectories(
            root
        );

        Path probe=
            root.resolve(
                "probe.kts"
            );
        Files.write(
            probe,
            java.util.Collections.singletonList(
                "// admitted regular A"
            ),
            StandardCharsets.UTF_8
        );

        SnapshotLoader loader=
            new SnapshotLoader(
                "// admitted regular A\n",
                "fixture.kotlin.snapshot.nonregular."+
                    (startup?"startup":"ondemand")
            );

        final boolean[] hookRan=
            new boolean[1];
        final boolean[] swapInstalled=
            new boolean[1];

        KotlinPluginDirectory.SourceCaptureHook hook=
            new KotlinPluginDirectory.SourceCaptureHook(){
                @Override public void beforeOpen(
                    Path source
                )throws IOException{
                    hookRan[0]=true;
                    Files.delete(
                        source
                    );
                    Files.createDirectory(
                        source
                    );
                    swapInstalled[0]=true;
                }

                @Override public void afterCapture(
                    PluginSource source
                ){
                }
            };

        try{
            if(startup)
                KotlinPluginDirectory
                    .loadStartup(
                        world,
                        root,
                        loader,
                        hook
                    );
            else
                KotlinPluginDirectory
                    .loadOnDemand(
                        world,
                        root,
                        probe,
                        loader,
                        hook
                    );
        }catch(IOException expected){
            if(!hookRan[0])
                throw new AssertionError(
                    "non-regular swap rejected before capture boundary",
                    expected
                );

            if(!swapInstalled[0]||
               !Files.isDirectory(
                    probe,
                    LinkOption.NOFOLLOW_LINKS))
                throw new AssertionError(
                    "non-regular swap fixture was not installed",
                    expected
                );

            if(loader.loads!=0)
                throw new AssertionError(
                    "loader observed source after final-child non-regular swap"
                );

            if(world.plugins().plugin(
                    loader.id)!=null)
                throw new AssertionError(
                    "plugin published after final-child non-regular swap"
                );

            assertNoCapturePins(
                root,
                "non-regular swap"
            );

            return true;
        }finally{
            if(Files.isDirectory(
                    probe,
                    LinkOption.NOFOLLOW_LINKS))
                Files.deleteIfExists(
                    probe
                );
        }

        throw new AssertionError(
            "final-child non-regular swap reached Kotlin loader"
        );
    }

    private static void assertSnapshotDetachedFromPath(
        World world,
        Path temp
    )throws Exception{
        Path root=
            temp.resolve(
                "plugins"
            ).resolve(
                "kotlin"
            );
        Files.createDirectories(
            root
        );

        Path probe=
            root.resolve(
                "probe.kts"
            );
        String admitted=
            "// immutable snapshot A\n";
        Files.write(
            probe,
            admitted.getBytes(
                StandardCharsets.UTF_8
            )
        );

        SnapshotLoader loader=
            new SnapshotLoader(
                admitted,
                "fixture.kotlin.snapshot.detached"
            );

        PluginHandle handle=
            KotlinPluginDirectory
                .loadOnDemand(
                    world,
                    root,
                    probe,
                    loader,
                    new KotlinPluginDirectory.SourceCaptureHook(){
                        @Override public void beforeOpen(
                            Path source
                        ){
                        }

                        @Override public void afterCapture(
                            PluginSource source
                        )throws IOException{
                            Files.write(
                                source.path(),
                                "// mutated path B\n"
                                    .getBytes(
                                        StandardCharsets.UTF_8
                                    )
                            );
                        }
                    }
                );

        if(loader.loads!=1||
           !handle.enabled()||
           !loader.id.equals(
                handle.manifest().id()
            )||
           !probe.toAbsolutePath()
                .normalize()
                .equals(
                    loader.observedPath
                ))
            throw new AssertionError(
                "captured snapshot/origin was not delivered to loader"
            );

        if(!world.plugins().disable(
                loader.id))
            throw new AssertionError(
                "snapshot-detached fixture did not disable"
            );

        assertNoCapturePins(
            root,
            "healthy snapshot capture"
        );
    }

    private static void assertNoCapturePins(
        Path root,
        String phase
    )throws Exception{
        if(!Files.isDirectory(
                root,
                LinkOption.NOFOLLOW_LINKS))
            return;

        try(Stream<Path> entries=
                Files.list(
                    root
                )){
            Path leaked=
                entries.filter(
                    path->
                        path.getFileName()
                            .toString()
                            .startsWith(
                                ".spawnpk-kts-capture-"
                            )
                )
                .findFirst()
                .orElse(
                    null
                );

            if(leaked!=null)
                throw new AssertionError(
                    phase+
                    " retained capture identity pin: "+
                    leaked
                );
        }
    }

    private static boolean assertRootSymlinkRejected(
        World world,
        Path temp
    )throws Exception{
        Path realRoot=
            temp.resolve(
                "real-kotlin-root"
            );
        Files.createDirectories(
            realRoot
        );

        Path linkRoot=
            temp.resolve(
                "linked-kotlin-root"
            );

        try{
            Files.createSymbolicLink(
                linkRoot,
                realRoot
            );
        }catch(UnsupportedOperationException|
               java.nio.file.FileSystemException|
               SecurityException unavailable){
            return false;
        }

        boolean startupRejected=false;

        try{
            KotlinPluginDirectory
                .loadStartup(
                    world,
                    linkRoot,
                    new TrackingLoader()
                );
        }catch(IOException expected){
            startupRejected=
                expected.getMessage()!=null&&
                expected.getMessage()
                    .contains(
                        "symlink component"
                    );
        }

        if(!startupRejected)
            throw new AssertionError(
                "Kotlin startup accepted symlinked plugin root"
            );

        Path realScript=
            realRoot.resolve(
                "ondemand.kts"
            );
        Files.write(
            realScript,
            java.util.Collections.singletonList(
                "// symlink root probe"
            ),
            StandardCharsets.UTF_8
        );

        boolean onDemandRejected=false;

        try{
            KotlinPluginDirectory
                .loadOnDemand(
                    world,
                    linkRoot,
                    linkRoot.resolve(
                        "ondemand.kts"
                    ),
                    new TrackingLoader()
                );
        }catch(IOException expected){
            onDemandRejected=
                expected.getMessage()!=null&&
                expected.getMessage()
                    .contains(
                        "symlink component"
                    );
        }

        if(!onDemandRejected)
            throw new AssertionError(
                "Kotlin on-demand loader accepted symlinked plugin root"
            );

        return true;
    }

    private static boolean assertAncestorSymlinkRejected(
        World world,
        Path temp
    )throws Exception{
        Path realParent=
            temp.resolve(
                "ancestor-real"
            );
        Path realRoot=
            realParent.resolve(
                "kotlin"
            );
        Files.createDirectories(
            realRoot
        );

        Path linkedParent=
            temp.resolve(
                "ancestor-link"
            );

        try{
            Files.createSymbolicLink(
                linkedParent,
                realParent
            );
        }catch(UnsupportedOperationException|
               java.nio.file.FileSystemException|
               SecurityException unavailable){
            return false;
        }

        Path realScript=
            realRoot.resolve(
                "ancestor.kts"
            );
        Files.write(
            realScript,
            java.util.Collections.singletonList(
                "// ancestor symlink probe"
            ),
            StandardCharsets.UTF_8
        );

        Path lexicalRoot=
            linkedParent.resolve(
                "kotlin"
            );
        Path lexicalScript=
            lexicalRoot.resolve(
                "ancestor.kts"
            );

        TrackingLoader startupLoader=
            new TrackingLoader();
        boolean startupRejected=false;

        try{
            KotlinPluginDirectory
                .loadStartup(
                    world,
                    lexicalRoot,
                    startupLoader
                );
        }catch(IOException expected){
            startupRejected=
                expected.getMessage()!=null&&
                expected.getMessage()
                    .contains(
                        "symlink component"
                    );
        }

        if(!startupRejected)
            throw new AssertionError(
                "Kotlin startup accepted symlinked root ancestor"
            );

        if(!startupLoader.paths.isEmpty())
            throw new AssertionError(
                "Kotlin startup reached loader through symlinked root ancestor"
            );

        TrackingLoader onDemandLoader=
            new TrackingLoader();
        boolean onDemandRejected=false;

        try{
            KotlinPluginDirectory
                .loadOnDemand(
                    world,
                    lexicalRoot,
                    lexicalScript,
                    onDemandLoader
                );
        }catch(IOException expected){
            onDemandRejected=
                expected.getMessage()!=null&&
                expected.getMessage()
                    .contains(
                        "symlink component"
                    );
        }

        if(!onDemandRejected)
            throw new AssertionError(
                "Kotlin on-demand loader accepted symlinked root ancestor"
            );

        if(!onDemandLoader.paths.isEmpty())
            throw new AssertionError(
                "Kotlin on-demand reached loader through symlinked root ancestor"
            );

        return true;
    }

    private static void assertPreloadFailureCleanup(
        World world,
        Path root
    )throws Exception{
        Files.createDirectories(
            root
        );

        Files.write(
            root.resolve(
                "b.kts"
            ),
            java.util.Collections.singletonList(
                "// b"
            ),
            StandardCharsets.UTF_8
        );
        Files.write(
            root.resolve(
                "A.kts"
            ),
            java.util.Collections.singletonList(
                "// A"
            ),
            StandardCharsets.UTF_8
        );

        Path nested=
            root.resolve(
                "nested"
            );
        Files.createDirectories(
            nested
        );
        Files.write(
            nested.resolve(
                "ignored.kts"
            ),
            java.util.Collections.singletonList(
                "// ignored"
            ),
            StandardCharsets.UTF_8
        );

        TrackingLoader loader=
            new TrackingLoader();

        boolean failed=false;

        try{
            KotlinPluginDirectory
                .loadStartup(
                    world,
                    root,
                    loader
                );
        }catch(IOException expected){
            failed=
                "fixture-preload-failure"
                    .equals(
                        expected.getMessage()
                    );
        }

        if(!failed)
            throw new AssertionError(
                "startup preload failure was not propagated"
            );

        if(loader.paths.size()!=2||
           !"A.kts".equals(
                loader.paths.get(0)
            )||
           !"b.kts".equals(
                loader.paths.get(1)
            ))
            throw new AssertionError(
                "Kotlin startup discovery order mismatch: "+
                loader.paths
            );

        if(loader.first==null||
           !loader.first.closed)
            throw new AssertionError(
                "startup preload failure retained first unowned runtime"
            );
    }

    private static void assertPreloadRuntimeCloseIdentityOnce(
        World world,
        Path root
    )throws Exception{
        createIdentityPreloadScripts(
            root
        );

        java.util.ArrayList<String> closeOrder=
            new java.util.ArrayList<>();
        RecordingRuntime shared=
            new RecordingRuntime(
                "fixture.kotlin.preload.shared",
                "shared",
                closeOrder,
                null
            );
        IOException primary=
            new IOException(
                "fixture-shared-preload-failure"
            );
        SequenceLoader loader=
            new SequenceLoader(
                primary,
                shared,
                shared
            );

        Throwable observed=null;

        try{
            KotlinPluginDirectory
                .loadStartup(
                    world,
                    root,
                    loader
                );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=primary)
            throw new AssertionError(
                "shared preload failure identity changed"
            );

        assertIdentityPreloadOrder(
            loader.paths
        );

        if(shared.closeCount!=1)
            throw new AssertionError(
                "shared preload runtime close count mismatch: "+
                shared.closeCount
            );

        if(!closeOrder.equals(
                java.util.Collections
                    .singletonList(
                        "shared"
                    )))
            throw new AssertionError(
                "shared preload runtime close order mismatch: "+
                closeOrder
            );

        if(shared.enableCount!=0||
           world.plugins().plugin(
                "fixture.kotlin.preload.shared"
            )!=null)
            throw new AssertionError(
                "shared preload failure reached plugin manager"
            );

        if(primary.getSuppressed().length!=0)
            throw new AssertionError(
                "shared non-throwing preload cleanup added suppression"
            );
    }

    private static void assertPreloadThrowingDuplicateCloseOnce(
        World world,
        Path root
    )throws Exception{
        createIdentityPreloadScripts(
            root
        );

        java.util.ArrayList<String> closeOrder=
            new java.util.ArrayList<>();
        IOException cleanup=
            new IOException(
                "fixture-shared-close-failure"
            );
        RecordingRuntime shared=
            new RecordingRuntime(
                "fixture.kotlin.preload.throwing-shared",
                "shared",
                closeOrder,
                cleanup
            );
        IOException primary=
            new IOException(
                "fixture-shared-loader-failure"
            );
        SequenceLoader loader=
            new SequenceLoader(
                primary,
                shared,
                shared
            );

        Throwable observed=null;

        try{
            KotlinPluginDirectory
                .loadStartup(
                    world,
                    root,
                    loader
                );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=primary)
            throw new AssertionError(
                "throwing shared preload primary changed"
            );

        assertIdentityPreloadOrder(
            loader.paths
        );

        if(shared.closeCount!=1)
            throw new AssertionError(
                "throwing shared preload runtime closed more than once: "+
                shared.closeCount
            );

        Throwable[] suppressed=
            primary.getSuppressed();

        if(suppressed.length!=1||
           suppressed[0]!=cleanup)
            throw new AssertionError(
                "throwing shared preload cleanup suppression mismatch"
            );

        if(!closeOrder.equals(
                java.util.Collections
                    .singletonList(
                        "shared"
                    )))
            throw new AssertionError(
                "throwing shared preload close order mismatch: "+
                closeOrder
            );

        if(shared.enableCount!=0||
           world.plugins().plugin(
                "fixture.kotlin.preload.throwing-shared"
            )!=null)
            throw new AssertionError(
                "throwing shared preload failure reached plugin manager"
            );
    }

    private static void assertPreloadDistinctReverseCleanup(
        World world,
        Path root
    )throws Exception{
        createMixedIdentityPreloadScripts(
            root
        );

        java.util.ArrayList<String> closeOrder=
            new java.util.ArrayList<>();
        IOException cleanupOne=
            new IOException(
                "fixture-close-r1"
            );
        IOException cleanupTwo=
            new IOException(
                "fixture-close-r2"
            );
        IOException cleanupThree=
            new IOException(
                "fixture-close-r3"
            );
        RecordingRuntime first=
            new RecordingRuntime(
                "fixture.kotlin.preload.r1",
                "R1",
                closeOrder,
                cleanupOne
            );
        RecordingRuntime second=
            new RecordingRuntime(
                "fixture.kotlin.preload.r2",
                "R2",
                closeOrder,
                cleanupTwo
            );
        RecordingRuntime third=
            new RecordingRuntime(
                "fixture.kotlin.preload.r3",
                "R3",
                closeOrder,
                cleanupThree
            );
        IOException primary=
            new IOException(
                "fixture-distinct-loader-failure"
            );
        SequenceLoader loader=
            new SequenceLoader(
                primary,
                first,
                second,
                first,
                third
            );

        Throwable observed=null;

        try{
            KotlinPluginDirectory
                .loadStartup(
                    world,
                    root,
                    loader
                );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=primary)
            throw new AssertionError(
                "distinct preload primary changed"
            );

        java.util.List<String> expectedPaths=
            java.util.Arrays.asList(
                "01-a.kts",
                "02-b.kts",
                "03-c.kts",
                "04-d.kts",
                "05-e.kts"
            );

        if(!expectedPaths.equals(
                loader.paths))
            throw new AssertionError(
                "mixed-identity preload discovery order mismatch expected="+
                expectedPaths+
                " actual="+
                loader.paths
            );

        if(first.closeCount!=1||
           second.closeCount!=1||
           third.closeCount!=1)
            throw new AssertionError(
                "mixed-identity preload runtime close counts mismatch R1="+
                first.closeCount+
                " R2="+
                second.closeCount+
                " R3="+
                third.closeCount
            );

        if(!closeOrder.equals(
                java.util.Arrays.asList(
                    "R3",
                    "R1",
                    "R2"
                )))
            throw new AssertionError(
                "mixed-identity reverse close order mismatch: "+
                closeOrder
            );

        Throwable[] suppressed=
            primary.getSuppressed();

        if(suppressed.length!=3||
           suppressed[0]!=cleanupThree||
           suppressed[1]!=cleanupOne||
           suppressed[2]!=cleanupTwo)
            throw new AssertionError(
                "mixed-identity suppression order mismatch"
            );

        if(first.enableCount!=0||
           second.enableCount!=0||
           third.enableCount!=0||
           world.plugins().plugin(
                "fixture.kotlin.preload.r1"
            )!=null||
           world.plugins().plugin(
                "fixture.kotlin.preload.r2"
            )!=null||
           world.plugins().plugin(
                "fixture.kotlin.preload.r3"
            )!=null)
            throw new AssertionError(
                "mixed-identity preload failure reached plugin manager"
            );
    }

    private static void createMixedIdentityPreloadScripts(
        Path root
    )throws Exception{
        Files.createDirectories(
            root
        );

        for(String name:
                new String[]{
                    "01-a.kts",
                    "02-b.kts",
                    "03-c.kts",
                    "04-d.kts",
                    "05-e.kts"
                })
            Files.write(
                root.resolve(
                    name
                ),
                java.util.Collections
                    .singletonList(
                        "// mixed identity preload probe"
                    ),
                StandardCharsets.UTF_8
            );
    }

    private static void createIdentityPreloadScripts(
        Path root
    )throws Exception{
        Files.createDirectories(
            root
        );

        for(String name:
                new String[]{
                    "01-a.kts",
                    "02-b.kts",
                    "03-c.kts"
                })
            Files.write(
                root.resolve(
                    name
                ),
                java.util.Collections
                    .singletonList(
                        "// identity preload probe"
                    ),
                StandardCharsets.UTF_8
            );
    }

    private static void assertIdentityPreloadOrder(
        java.util.List<String> paths
    ){
        java.util.List<String> expected=
            java.util.Arrays.asList(
                "01-a.kts",
                "02-b.kts",
                "03-c.kts"
            );

        if(!expected.equals(
                paths))
            throw new AssertionError(
                "identity preload discovery order mismatch expected="+
                expected+
                " actual="+
                paths
            );
    }

    private static final class SequenceLoader
        implements PluginLoader {

        final java.util.ArrayList<String> paths=
            new java.util.ArrayList<>();
        private final IOException failure;
        private final PluginRuntime[] runtimes;
        private int next;

        SequenceLoader(
            IOException failure,
            PluginRuntime... runtimes
        ){
            this.failure=failure;
            this.runtimes=runtimes;
        }

        @Override public boolean supports(
            PluginSource source
        ){
            return source!=null&&
                !source.hasEntrypoint();
        }

        @Override public PluginRuntime load(
            PluginSource source
        )throws Exception{
            paths.add(
                source.path()
                    .getFileName()
                    .toString()
            );

            if(next<runtimes.length)
                return runtimes[
                    next++
                ];

            throw failure;
        }
    }

    private static final class RecordingRuntime
        implements PluginRuntime {

        private final String id;
        private final String label;
        private final java.util.List<String>
            closeOrder;
        private final Exception closeFailure;
        int closeCount;
        int enableCount;

        RecordingRuntime(
            String id,
            String label,
            java.util.List<String> closeOrder,
            Exception closeFailure
        ){
            this.id=id;
            this.label=label;
            this.closeOrder=closeOrder;
            this.closeFailure=closeFailure;
        }

        @Override public PluginManifest manifest(){
            return new PluginManifest(
                id,
                "1.0",
                PluginApiVersion.CURRENT,
                java.util.Collections.emptyList()
            );
        }

        @Override public void enable(
            PluginContext context
        ){
            enableCount++;
        }

        @Override public void disable(){
        }

        @Override public ClassLoader callbackClassLoader(){
            return getClass()
                .getClassLoader();
        }

        @Override public void close()
            throws Exception{
            closeCount++;
            closeOrder.add(
                label
            );

            if(closeFailure!=null)
                throw closeFailure;
        }
    }

    private static final class SnapshotLoader
        implements PluginLoader {
        private final String expectedText;
        final String id;
        int loads;
        Path observedPath;

        SnapshotLoader(
            String expectedText,
            String id
        ){
            this.expectedText=expectedText;
            this.id=id;
        }

        @Override public boolean supports(
            PluginSource source
        ){
            return source!=null&&
                source.hasScriptSnapshot()&&
                !source.hasEntrypoint();
        }

        @Override public PluginRuntime load(
            PluginSource source
        ){
            loads++;
            observedPath=
                source.path();

            if(!source.hasScriptSnapshot())
                throw new AssertionError(
                    "directory handed loader a path-only script source"
                );

            if(!expectedText.equals(
                    source.requireScriptText()))
                throw new AssertionError(
                    "loader observed mutated script bytes instead of admitted snapshot"
                );

            return new SnapshotRuntime(
                id
            );
        }
    }

    private static final class SnapshotRuntime
        implements PluginRuntime {
        private final String id;
        private boolean closed;

        SnapshotRuntime(
            String id
        ){
            this.id=id;
        }

        @Override public PluginManifest manifest(){
            return new PluginManifest(
                id,
                "1.0",
                PluginApiVersion.CURRENT,
                java.util.Collections.emptyList()
            );
        }

        @Override public void enable(
            PluginContext context
        ){
        }

        @Override public void disable(){
        }

        @Override public ClassLoader callbackClassLoader(){
            if(closed)
                throw new IllegalStateException(
                    "snapshot runtime closed"
                );

            return getClass()
                .getClassLoader();
        }

        @Override public void close(){
            closed=true;
        }
    }

    private static final class TrackingLoader
        implements PluginLoader {

        final java.util.ArrayList<String> paths=
            new java.util.ArrayList<>();
        TrackingRuntime first;

        @Override public boolean supports(
            PluginSource source
        ){
            return source!=null&&
                !source.hasEntrypoint();
        }

        @Override public PluginRuntime load(
            PluginSource source
        )throws Exception{
            paths.add(
                source.path()
                    .getFileName()
                    .toString()
            );

            if(paths.size()==1){
                first=
                    new TrackingRuntime();
                return first;
            }

            throw new IOException(
                "fixture-preload-failure"
            );
        }
    }

    private static final class TrackingRuntime
        implements PluginRuntime {

        boolean closed;

        @Override public PluginManifest manifest(){
            return new PluginManifest(
                "fixture.kotlin.preload",
                "1.0",
                PluginApiVersion.CURRENT,
                java.util.Collections.emptyList()
            );
        }

        @Override public void enable(
            PluginContext context
        ){
        }

        @Override public void disable(){
        }

        @Override public ClassLoader callbackClassLoader(){
            if(closed)
                throw new IllegalStateException(
                    "tracking runtime closed"
                );

            return getClass()
                .getClassLoader();
        }

        @Override public void close(){
            closed=true;
        }
    }

    private static void assertCommandAfterEvent(
        World world,
        WorldPlayer player,
        long generation,
        ServerPacketWriter writer,
        String command,
        String expected
    )throws Exception{
        final String[] result=
            new String[1];

        world.submitAndWait(
            player,
            generation,
            ()->{
                ProbeEvent event=
                    new ProbeEvent();
                event.cancel();

                world.domainEvents()
                    .publish(
                        event
                    );

                ContentResult commandResult=
                    world.content()
                        .dispatchCommand(
                            player,
                            command,
                            writer
                        );

                result[0]=
                    commandResult==null
                        ?null
                        :commandResult.logText();
            },
            5_000L
        );

        if(!expected.equals(
                result[0]))
            throw new AssertionError(
                "Kotlin directory command result mismatch command="+
                command+
                " expected="+
                expected+
                " actual="+
                result[0]
            );
    }

    private static void restore(
        String key,
        String value
    ){
        if(value==null)
            System.clearProperty(
                key
            );
        else
            System.setProperty(
                key,
                value
            );
    }

    private KotlinPluginDirectoryTest(){}
}
