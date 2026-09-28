package spk.local;

import java.util.*;
import java.util.function.BooleanSupplier;
import spk.content.api.*;
import spk.event.DomainEventBus;
import spk.plugin.api.*;

/**
 * World-owned implementation of the public JVM plugin lifecycle.
 *
 * Network/session/runtime internals never cross the spk.plugin.api boundary.
 */
final class WorldPluginManager
    implements PluginManager,AutoCloseable {

    private static final String CONTENT_PREFIX=
        "plugin:";
    private static final int MAX_TERMINAL_DIAGNOSTICS=
        128;

    private final ContentRegistry content;
    private final DomainEventBus events;
    private final GameClock clock;
    private final WorldEventQueue worldEvents;
    private final BooleanSupplier worldOpen;
    private final BooleanSupplier worldExecution;
    private final LinkedHashMap<String,Entry>
        enabled=new LinkedHashMap<>();
    private final HashSet<String>
        disabling=new HashSet<>();
    private final LinkedHashMap<String,Entry>
        terminalizing=new LinkedHashMap<>();
    private final ArrayList<String>
        terminalDiagnostics=new ArrayList<>();

    private int activeCleanups;
    private boolean closed;
    private boolean resourcesClosing;
    private boolean resourcesClosed;

    WorldPluginManager(
        ContentRegistry content,
        DomainEventBus events,
        GameClock clock,
        WorldEventQueue worldEvents,
        BooleanSupplier worldOpen,
        BooleanSupplier worldExecution
    ){
        this.content=Objects.requireNonNull(
            content,
            "content"
        );
        this.events=Objects.requireNonNull(
            events,
            "events"
        );
        this.clock=Objects.requireNonNull(
            clock,
            "clock"
        );
        this.worldEvents=Objects.requireNonNull(
            worldEvents,
            "worldEvents"
        );
        this.worldOpen=Objects.requireNonNull(
            worldOpen,
            "worldOpen"
        );
        this.worldExecution=
            Objects.requireNonNull(
                worldExecution,
                "worldExecution"
            );
    }

    @Override public synchronized PluginHandle enable(
        Plugin plugin
    )throws Exception{
        RuntimeCloseOwnership runtimeClose=
            new RuntimeCloseOwnership(
                plugin
            );

        try{
            requireOpen();

            return enableOne(
                snapshotCandidate(
                    plugin,
                    runtimeClose
                ),
                true,
                new RuntimeAdmissionGate()
            );
        }catch(Throwable failure){
            if(!ownsPluginInstance(
                    plugin))
                runtimeClose.close(
                    failure
                );
            rethrow(failure);
            throw new AssertionError(
                "unreachable"
            );
        }
    }

    @Override public synchronized List<PluginHandle>
        enableAll(
            Collection<? extends Plugin> plugins
        )throws Exception{
        Objects.requireNonNull(
            plugins,
            "plugins"
        );

        ArrayList<Plugin> requested=
            new ArrayList<>();
        ArrayList<Entry> added=
            new ArrayList<>();
        RuntimeAdmissionGate admission=
            new RuntimeAdmissionGate();
        IdentityHashMap<Plugin,RuntimeCloseOwnership>
            runtimeCloses=
                new IdentityHashMap<>();

        try{
            for(Plugin plugin:plugins){
                if(!runtimeCloses.containsKey(
                        plugin))
                    runtimeCloses.put(
                        plugin,
                        new RuntimeCloseOwnership(
                            plugin
                        )
                    );

                requested.add(
                    plugin
                );
            }

            requireOpen();

            List<Candidate> ordered=
                dependencyOrder(
                    snapshotCandidates(
                        requested,
                        runtimeCloses
                    )
                );

            for(Candidate candidate:ordered)
                added.add(
                    enableOne(
                        candidate,
                        false,
                        admission
                    )
                );

            commitRuntime(
                added,
                admission
            );
        }catch(Throwable failure){
            for(int i=added.size()-1;i>=0;i--)
                try{
                    disableEntry(
                        added.get(i),
                        "BATCH_ROLLBACK"
                    );
                }catch(Throwable cleanup){
                    PluginRuntimeSupport
                        .suppressIfDistinct(
                            failure,
                            cleanup
                        );
                }

            for(Plugin plugin:requested)
                if(!ownsPluginInstance(
                        plugin))
                    runtimeCloses.get(
                            plugin
                        ).close(
                            failure
                        );

            rethrow(failure);
        }

        return Collections.unmodifiableList(
            new ArrayList<PluginHandle>(added)
        );
    }

    @Override public boolean disable(
        String pluginId
    ){
        String id=
            canonicalId(pluginId);
        Entry entry;

        synchronized(this){
            entry=enabled.get(id);

            if(entry==null){
                Entry terminal=
                    terminalizing.get(id);

                if(terminal!=null)
                    awaitEntryCleanupLocked(
                        terminal
                    );

                return false;
            }

            for(Entry candidate:
                    enabled.values())
                if(candidate!=entry&&
                   candidate.enabled&&
                   candidate.manifest
                        .dependencies()
                        .contains(id))
                    throw new IllegalStateException(
                        "plugin has enabled dependent: "+
                        candidate.manifest.id()+
                        " -> "+id
                    );

            if(!claimCleanupLocked(
                    entry))
                return false;
        }

        try{
            cleanupEntry(
                entry,
                "EXPLICIT_DISABLE"
            );
        }finally{
            completeCleanup(
                entry
            );
        }

        return true;
    }

    @Override public synchronized PluginHandle plugin(
        String pluginId
    ){
        return enabled.get(
            canonicalId(pluginId)
        );
    }

    @Override public synchronized List<PluginHandle>
        enabled(){
        ArrayList<PluginHandle> out=
            new ArrayList<>();

        for(Entry entry:enabled.values())
            if(entry.enabled)
                out.add(entry);

        return Collections.unmodifiableList(out);
    }

    /** Publish the terminal admission fence without invoking plugin code. */
    synchronized void beginClose(){
        closed=true;
    }

    /** Disable resources after the World pulse has stopped. */
    void closeResources(){
        ArrayList<Entry> entries;

        synchronized(this){
            if(resourcesClosed)
                return;

            if(resourcesClosing){
                awaitResourcesClosed();
                return;
            }

            resourcesClosing=true;
            closed=true;

            awaitExplicitCleanups();

            ArrayList<Entry> snapshot=
                new ArrayList<>(
                    enabled.values()
                );
            entries=
                new ArrayList<>();

            for(Entry entry:snapshot)
                if(claimCleanupLocked(
                        entry))
                    entries.add(entry);
        }

        Throwable failure=null;

        try{
            for(int i=entries.size()-1;
                i>=0;
                i--){
                Entry entry=entries.get(i);

                try{
                    cleanupEntry(
                        entry,
                        "WORLD_CLOSE"
                    );
                }catch(Throwable cleanup){
                    recordTerminalDiagnostic(
                        entry,
                        "WORLD_CLOSE:CLEANUP",
                        cleanup
                    );

                    if(failure==null)
                        failure=cleanup;
                    else
                        PluginRuntimeSupport
                            .suppressIfDistinct(
                                failure,
                                cleanup
                            );
                }finally{
                    completeCleanup(
                        entry
                    );
                }
            }
        }finally{
            synchronized(this){
                resourcesClosed=true;
                resourcesClosing=false;
                notifyAll();
            }
        }

        failure=
            PluginJarLoader
                .retryArchiveCleanupDebtOnce(
                    failure
                );
        failure=
            KotlinClasspathCleanupDebt
                .retryOnce(
                    failure
                );

        rethrowUnchecked(
            failure
        );
    }

    @Override public void close(){
        beginClose();
        closeResources();
    }

    private Candidate snapshotCandidate(
        Plugin plugin,
        RuntimeCloseOwnership runtimeClose
    ){
        Objects.requireNonNull(
            plugin,
            "plugin"
        );
        Objects.requireNonNull(
            runtimeClose,
            "runtimeClose"
        );

        ClassLoader callbackLoader=
            PluginRuntimeSupport.callbackClassLoader(
                plugin
            );

        PluginManifest manifest=
            Objects.requireNonNull(
                PluginThreadContext.callUnchecked(
                    callbackLoader,
                    plugin::manifest
                ),
                "plugin manifest"
            );

        validateCompatibility(
            manifest
        );

        return new Candidate(
            plugin,
            callbackLoader,
            manifest,
            runtimeClose
        );
    }

    private List<Candidate> snapshotCandidates(
        Collection<? extends Plugin> plugins,
        IdentityHashMap<
            Plugin,RuntimeCloseOwnership
        > runtimeCloses
    ){
        ArrayList<Candidate> snapshots=
            new ArrayList<>();

        for(Plugin plugin:
                Objects.requireNonNull(
                    plugins,
                    "plugins"
                ))
            snapshots.add(
                snapshotCandidate(
                    plugin,
                    runtimeCloses.get(
                        plugin
                    )
                )
            );

        return snapshots;
    }

    private Entry enableOne(
        Candidate candidate,
        boolean activateRuntime,
        RuntimeAdmissionGate admission
    )throws Exception{
        Objects.requireNonNull(
            candidate,
            "candidate"
        );

        Plugin plugin=
            candidate.plugin;
        ClassLoader callbackLoader=
            candidate.callbackLoader;
        PluginManifest manifest=
            candidate.manifest;

        String id=manifest.id();

        if(enabled.containsKey(id))
            throw new IllegalStateException(
                "plugin already enabled: "+id
            );

        if(disabling.contains(id))
            throw new IllegalStateException(
                "plugin disable in progress: "+id
            );

        for(String dependency:
                manifest.dependencies())
            if(!enabled.containsKey(dependency))
                throw new IllegalStateException(
                    "plugin dependency not enabled: "+
                    id+" -> "+dependency
                );

        String moduleId=
            CONTENT_PREFIX+id;
        PluginCallbackScope callbackScope=
            new PluginCallbackScope(
                worldExecution,
                worldOpen
            );

        EventTracker tracker=
            new EventTracker();
        Entry entry=
            new Entry(
                plugin,
                manifest,
                moduleId,
                tracker,
                callbackScope,
                admission,
                candidate.runtimeClose,
                callbackLoader
            );
        entry.failureSink=
            (kind,failure)->
                failRuntimeCallback(
                    entry,
                    kind,
                    failure
                );

        PluginTaskTracker tasks=
            new PluginTaskTracker(
                clock,
                worldEvents,
                worldOpen,
                callbackLoader,
                callbackScope,
                entry::runtimeEnabled,
                entry::callbackFailure
            );
        entry.tasks=tasks;

        PluginContentModule module=
            new PluginContentModule(
                moduleId,
                plugin,
                tracker,
                tasks,
                callbackLoader,
                callbackScope,
                entry
            );

        try{
            content.installCustom(module);
            enabled.put(id,entry);

            if(activateRuntime)
                commitRuntime(
                    Collections.singletonList(
                        entry
                    ),
                    admission
                );

            return entry;
        }catch(Throwable failure){
            enabled.remove(id);
            callbackScope.close();
            module.seal();
            tasks.close();

            if(module.enableAttempted())
                try{
                    PluginThreadContext.run(
                        callbackLoader,
                        plugin::disable
                    );
                }catch(Throwable cleanup){
                    PluginRuntimeSupport
                        .suppressIfDistinct(
                            failure,
                            cleanup
                        );
                }

            tracker.close(
                entry::callbackFailure
            );

            try{
                content.uninstallModule(
                    moduleId
                );
            }catch(Throwable cleanup){
                PluginRuntimeSupport
                    .suppressIfDistinct(
                        failure,
                        cleanup
                    );
            }

            candidate.runtimeClose.close(
                failure
            );
            entry.failureSink=null;
            entry.plugin=null;
            entry.callbackLoader=null;
            entry.cleanupClaimed=true;
            entry.cleanupComplete=true;

            rethrow(failure);
            throw new AssertionError(
                "unreachable"
            );
        }
    }

    private void commitRuntime(
        List<Entry> entries,
        RuntimeAdmissionGate admission
    ){
        Objects.requireNonNull(
            entries,
            "entries"
        );
        Objects.requireNonNull(
            admission,
            "admission"
        );

        for(Entry entry:entries){
            if(entry.admission!=admission)
                throw new IllegalStateException(
                    "plugin runtime admission gate mismatch"
                );

            entry.tasks.validateActivation();
        }

        for(Entry entry:entries)
            entry.callbacks.activate();

        for(Entry entry:entries)
            entry.runtimeState=
                RuntimeState.ENABLED;

        /*
         * Keep runtime admission closed until every pending task has been
         * published. A task that becomes due during this short phase defers
         * itself without entering plugin code; one volatile gate publication
         * then makes the whole enable operation runtime-admissible.
         */
        for(Entry entry:entries)
            entry.tasks.activate();

        admission.commit();
    }

    private void disableEntry(
        Entry entry,
        String reason
    ){
        boolean claimed;

        synchronized(this){
            claimed=
                claimCleanupLocked(
                    entry
                );
        }

        if(!claimed)
            return;

        try{
            cleanupEntry(
                entry,
                reason
            );
        }finally{
            completeCleanup(
                entry
            );
        }
    }

    private void detachEntry(
        Entry entry
    ){
        if(entry==null||!entry.enabled)
            return;

        entry.enabled=false;
        entry.callbacks.beginClose();
        entry.tasks.beginClose();
        enabled.remove(
            entry.manifest.id()
        );
    }

    private void cleanupEntry(
        Entry entry,
        String reason
    ){
        if(entry==null)
            return;

        Plugin plugin=
            entry.plugin;

        try{
            ClassLoader callbackLoader=
                entry.callbackLoader;

            entry.callbacks.awaitQuiescent();
            entry.tasks.awaitQuiescent();
            entry.callbacks.finishClose();

            try{
                if(plugin!=null)
                    PluginThreadContext.run(
                        callbackLoader,
                        plugin::disable
                    );
            }catch(Throwable failure){
                recordTerminalDiagnostic(
                    entry,
                    reason+":DISABLE",
                    failure
                );
                System.err.println(
                    "[plugins] disable callback failed id="+
                    entry.manifest.id()+
                    " reason="+reason+
                    " errorClass="+
                    safeFailureClassName(
                        failure
                    )
                );
            }

            entry.tasks.close();
            entry.events.close(
                entry::callbackFailure
            );

            try{
                content.uninstallModule(
                    entry.moduleId
                );
            }catch(Throwable failure){
                recordTerminalDiagnostic(
                    entry,
                    reason+":CONTENT_UNINSTALL",
                    failure
                );
                System.err.println(
                    "[plugins] content uninstall failed id="+
                    entry.manifest.id()+
                    " reason="+reason+
                    " errorClass="+
                    safeFailureClassName(
                        failure
                    )
                );
            }
        }finally{
            Throwable loaderFailure=
                entry.runtimeClose.close(
                    null
                );

            if(loaderFailure!=null)
                recordTerminalDiagnostic(
                    entry,
                    reason+
                    ":CLASSLOADER_CLOSE",
                    loaderFailure
                );

            entry.failureSink=null;
            entry.plugin=null;
            entry.callbackLoader=null;
        }
    }

    private boolean claimCleanupLocked(
        Entry entry
    ){
        if(entry==null||
           entry.cleanupClaimed)
            return false;

        entry.cleanupClaimed=true;
        entry.runtimeState=
            RuntimeState.TERMINAL;
        detachEntry(entry);
        disabling.add(
            entry.manifest.id()
        );
        terminalizing.put(
            entry.manifest.id(),
            entry
        );
        activeCleanups++;
        return true;
    }

    private void completeCleanup(
        Entry entry
    ){
        synchronized(this){
            if(entry==null||
               entry.cleanupComplete)
                return;

            entry.cleanupComplete=true;
            activeCleanups--;
            disabling.remove(
                entry.manifest.id()
            );
            terminalizing.remove(
                entry.manifest.id()
            );
            entry.runtimeClose.release();
            notifyAll();
        }
    }

    private void awaitEntryCleanupLocked(
        Entry entry
    ){
        boolean interrupted=false;

        while(entry!=null&&
              !entry.cleanupComplete)
            try{
                wait();
            }catch(InterruptedException ignored){
                interrupted=true;
            }

        if(interrupted)
            Thread.currentThread()
                .interrupt();
    }

    private void failRuntimeCallback(
        Entry failed,
        String callbackKind,
        Throwable failure
    ){
        FailureBatch batch=null;

        synchronized(this){
            if(failed==null||
               failure==null)
                return;

            String phase=
                callbackKind!=null&&
                callbackKind.startsWith(
                    "CLEANUP:")
                    ?callbackKind
                    :"CALLBACK:"+
                        callbackKind;

            recordTerminalDiagnosticLocked(
                failed,
                phase,
                failure
            );

            if(failed.runtimeState==
                    RuntimeState.ENABLING||
               failed.cleanupClaimed||
               !failed.enabled)
                return;

            LinkedHashSet<String> affected=
                new LinkedHashSet<>();
            affected.add(
                failed.manifest.id()
            );

            boolean changed=true;

            while(changed){
                changed=false;

                for(Entry candidate:
                        enabled.values()){
                    if(affected.contains(
                            candidate.manifest.id()))
                        continue;

                    for(String dependency:
                            candidate.manifest
                                .dependencies())
                        if(affected.contains(
                                dependency)){
                            affected.add(
                                candidate.manifest.id()
                            );
                            changed=true;
                            break;
                        }
                }
            }

            ArrayList<Entry> ordered=
                new ArrayList<>();

            for(Entry candidate:
                    enabled.values())
                if(affected.contains(
                        candidate.manifest.id()))
                    ordered.add(candidate);

            ArrayList<Entry> cleanup=
                new ArrayList<>();

            for(int i=ordered.size()-1;
                i>=0;
                i--){
                Entry candidate=
                    ordered.get(i);

                if(claimCleanupLocked(
                        candidate))
                    cleanup.add(
                        candidate
                    );
            }

            if(!cleanup.isEmpty())
                batch=
                    new FailureBatch(
                        cleanup,
                        failed.manifest.id()
                    );
        }

        if(batch==null)
            return;

        final FailureBatch pending=batch;

        for(Entry entry:
                pending.entries){
            entry.callbacks.onQuiescent(
                ()->tryCleanupFailureBatch(
                    pending
                )
            );
            entry.tasks.onQuiescent(
                ()->tryCleanupFailureBatch(
                    pending
                )
            );
        }

        tryCleanupFailureBatch(
            pending
        );
    }

    private void tryCleanupFailureBatch(
        FailureBatch batch
    ){
        if(batch==null)
            return;

        synchronized(this){
            if(batch.started)
                return;

            for(Entry entry:
                    batch.entries)
                if(!entry.callbacks
                        .quiescent()||
                   !entry.tasks
                        .quiescent())
                    return;

            batch.started=true;
        }

        for(Entry entry:batch.entries)
            try{
                cleanupEntry(
                    entry,
                    "CALLBACK_FAILURE:"+
                    batch.failedPluginId
                );
            }catch(Throwable cleanupFailure){
                recordTerminalDiagnostic(
                    entry,
                    "CALLBACK_FAILURE:CLEANUP",
                    cleanupFailure
                );
                System.err.println(
                    "[plugins] callback-failure cleanup failed id="+
                    entry.manifest.id()+
                    " errorClass="+
                    safeFailureClassName(
                        cleanupFailure
                    )
                );
            }finally{
                completeCleanup(
                    entry
                );
            }
    }

    synchronized List<String>
        terminalDiagnostics(){
        return Collections.unmodifiableList(
            new ArrayList<>(
                terminalDiagnostics
            )
        );
    }

    private void recordTerminalDiagnostic(
        Entry entry,
        String phase,
        Throwable failure
    ){
        synchronized(this){
            recordTerminalDiagnosticLocked(
                entry,
                phase,
                failure
            );
        }
    }

    private void recordTerminalDiagnosticLocked(
        Entry entry,
        String phase,
        Throwable failure
    ){
        if(entry==null||
           failure==null)
            return;

        if(terminalDiagnostics.size()>=
                MAX_TERMINAL_DIAGNOSTICS)
            terminalDiagnostics.remove(0);

        terminalDiagnostics.add(
            "plugin="+
            entry.manifest.id()+
            " phase="+
            boundedDiagnostic(
                phase
            )+
            " errorClass="+
            safeFailureClassName(
                failure
            )+
            " message="+
            safeFailureMessage(
                failure
            )+
            " stack="+
            boundedStack(
                failure
            )
        );
    }

    private static String safeFailureClassName(
        Throwable failure
    ){
        return failure==null
            ?"<null>"
            :boundedDiagnostic(
                failure.getClass()
                    .getName()
            );
    }

    private static String safeFailureMessage(
        Throwable failure
    ){
        if(failure==null)
            return "<null>";

        try{
            return boundedDiagnostic(
                failure.getMessage()
            );
        }catch(Throwable ignored){
            return "<message-unavailable>";
        }
    }

    private static String boundedStack(
        Throwable failure
    ){
        if(failure==null)
            return "<null>";

        StringBuilder out=
            new StringBuilder();
        IdentityHashMap<Throwable,Boolean> seen=
            new IdentityHashMap<>();

        Throwable current=failure;
        int causes=0;

        while(current!=null&&
              causes<3&&
              out.length()<960){
            if(seen.put(
                    current,
                    Boolean.TRUE
                )!=null){
                out.append(
                    " <cause-cycle>"
                );
                break;
            }

            if(causes>0)
                out.append(" causedBy=")
                    .append(
                        safeFailureClassName(
                            current
                        )
                    );

            StackTraceElement[] trace=null;

            try{
                trace=current.getStackTrace();
            }catch(Throwable ignored){
                out.append(
                    " <stack-unavailable>"
                );
            }

            if(trace!=null){
                int frames=
                    Math.min(
                        trace.length,
                        8
                    );

                for(int i=0;
                    i<frames&&
                    out.length()<960;
                    i++){
                    StackTraceElement frame=
                        trace[i];

                    out.append(" at ")
                        .append(
                            frame==null
                                ?"<null-frame>"
                                :frame.toString()
                        );
                }
            }

            Throwable next;

            try{
                next=current.getCause();
            }catch(Throwable ignored){
                out.append(
                    " <cause-unavailable>"
                );
                break;
            }

            current=next;
            causes++;
        }

        String clean=
            out.toString()
                .replace('\n',' ')
                .replace('\r',' ')
                .replace('\t',' ');

        if(clean.length()>960)
            clean=clean.substring(
                0,
                960
            );

        return clean;
    }

    private static String boundedDiagnostic(
        String value
    ){
        String clean=
            value==null
                ?"<null>"
                :value.replace(
                    '\n',
                    ' '
                ).replace(
                    '\r',
                    ' '
                ).replace(
                    '\t',
                    ' '
                );

        if(clean.length()>320)
            clean=clean.substring(
                0,
                320
            );

        return clean;
    }

    private synchronized boolean ownsPluginInstance(
        Plugin plugin
    ){
        if(plugin==null)
            return false;

        for(Entry entry:
                enabled.values())
            if(entry.runtimeClose.owns(
                    plugin))
                return true;

        for(Entry entry:
                terminalizing.values())
            if(entry.runtimeClose.owns(
                    plugin))
                return true;

        return false;
    }

    private synchronized void awaitExplicitCleanups(){
        boolean interrupted=false;

        while(activeCleanups>0)
            try{
                wait();
            }catch(InterruptedException error){
                interrupted=true;
            }

        if(interrupted)
            Thread.currentThread()
                .interrupt();
    }

    private synchronized void awaitResourcesClosed(){
        boolean interrupted=false;

        while(!resourcesClosed)
            try{
                wait();
            }catch(InterruptedException error){
                interrupted=true;
            }

        if(interrupted)
            Thread.currentThread()
                .interrupt();
    }

    private List<Candidate> dependencyOrder(
        Collection<? extends Candidate> candidates
    ){
        Objects.requireNonNull(
            candidates,
            "candidates"
        );

        TreeMap<String,Candidate> pending=
            new TreeMap<>();

        for(Candidate candidate:candidates){
            Objects.requireNonNull(
                candidate,
                "candidate"
            );

            PluginManifest manifest=
                candidate.manifest;

            if(enabled.containsKey(
                    manifest.id()))
                throw new IllegalStateException(
                    "plugin already enabled: "+
                    manifest.id()
                );

            Candidate duplicate=
                pending.put(
                    manifest.id(),
                    candidate
                );

            if(duplicate!=null)
                throw new IllegalStateException(
                    "duplicate plugin id in batch: "+
                    manifest.id()
                );
        }

        for(Candidate candidate:
                pending.values())
            for(String dependency:
                    candidate.manifest
                        .dependencies())
                if(!enabled.containsKey(dependency)&&
                   !pending.containsKey(dependency))
                    throw new IllegalStateException(
                        "plugin dependency missing: "+
                        candidate.manifest.id()+
                        " -> "+dependency
                    );

        ArrayList<Candidate> ordered=
            new ArrayList<>();
        HashSet<String> resolved=
            new HashSet<>(
                enabled.keySet()
            );

        while(!pending.isEmpty()){
            String ready=null;

            for(Map.Entry<String,Candidate> candidate:
                    pending.entrySet()){
                if(resolved.containsAll(
                        candidate.getValue()
                            .manifest
                            .dependencies())){
                    ready=candidate.getKey();
                    break;
                }
            }

            if(ready==null)
                throw new IllegalStateException(
                    "plugin dependency cycle: "+
                    pending.keySet()
                );

            Candidate candidate=
                pending.remove(
                    ready
                );

            ordered.add(
                candidate
            );
            resolved.add(
                ready
            );
        }

        return ordered;
    }

    private static void validateCompatibility(
        PluginManifest manifest
    ){
        if(!PluginApiVersion.compatible(
                manifest.apiVersion()))
            throw new IllegalStateException(
                "plugin API version incompatible id="+
                manifest.id()+
                " required="+
                manifest.apiVersion()+
                " current="+
                PluginApiVersion.CURRENT
            );
    }

    private void requireOpen(){
        if(closed||!worldOpen.getAsBoolean())
            throw new IllegalStateException(
                "plugin manager closed"
            );
    }

    private static String canonicalId(
        String value
    ){
        String clean=
            value==null
                ?""
                :value.trim()
                    .toLowerCase(
                        Locale.ROOT
                    );

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "plugin id"
            );

        return clean;
    }

    private static void rethrowUnchecked(
        Throwable failure
    ){
        if(failure==null)
            return;

        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;

        if(failure instanceof Error)
            throw (Error)failure;

        throw new RuntimeException(
            failure
        );
    }

    private static void rethrow(
        Throwable failure
    )throws Exception{
        if(failure instanceof PluginEnableFailure){
            Throwable cause=
                failure.getCause();

            if(cause!=null){
                PluginRuntimeSupport
                    .transferSuppressedDistinct(
                        failure,
                        cause
                    );
                rethrow(cause);
            }
        }

        if(failure instanceof Exception)
            throw (Exception)failure;

        if(failure instanceof Error)
            throw (Error)failure;

        throw new RuntimeException(failure);
    }

    private final class PluginContentModule
        implements ContentModule {

        private final String moduleId;
        private final Plugin plugin;
        private final EventTracker tracker;
        private final PluginTaskTracker tasks;
        private final ClassLoader callbackLoader;
        private final PluginCallbackScope callbackScope;
        private final Entry owner;
        private volatile ScopedPluginContext context;
        private volatile boolean enableAttempted;

        PluginContentModule(
            String moduleId,
            Plugin plugin,
            EventTracker tracker,
            PluginTaskTracker tasks,
            ClassLoader callbackLoader,
            PluginCallbackScope callbackScope,
            Entry owner
        ){
            this.moduleId=moduleId;
            this.plugin=plugin;
            this.tracker=tracker;
            this.tasks=tasks;
            this.callbackLoader=
                callbackLoader;
            this.callbackScope=
                callbackScope;
            this.owner=
                Objects.requireNonNull(
                    owner,
                    "owner"
                );
        }

        @Override public String id(){
            return moduleId;
        }

        @Override public void register(
            ContentRegistrar registrar
        ){
            ScopedPluginContext local=
                new ScopedPluginContext(
                    registrar,
                    tracker,
                    tasks,
                    callbackLoader,
                    callbackScope,
                    WorldPluginManager.this.events,
                    owner
                );

            context=local;
            enableAttempted=true;

            try{
                PluginThreadContext.run(
                    callbackLoader,
                    ()->plugin.enable(local)
                );
            }catch(Throwable failure){
                throw new PluginEnableFailure(
                    failure
                );
            }finally{
                local.seal();
                context=null;
            }
        }

        void seal(){
            ScopedPluginContext current=context;
            if(current!=null){
                current.seal();
                context=null;
            }
        }

        boolean enableAttempted(){
            return enableAttempted;
        }
    }

    private static final class ScopedPluginContext
        implements PluginContext {

        private ScopedContentRegistrar content;
        private ScopedPluginEvents events;
        private PluginScheduler scheduler;
        private boolean open=true;

        ScopedPluginContext(
            ContentRegistrar registrar,
            EventTracker tracker,
            PluginTaskTracker tasks,
            ClassLoader callbackLoader,
            PluginCallbackScope callbackScope,
            DomainEventBus eventBus,
            Entry owner
        ){
            content=
                new ScopedContentRegistrar(
                    registrar,
                    this,
                    callbackLoader,
                    callbackScope,
                    owner
                );
            events=
                new ScopedPluginEvents(
                    eventBus,
                    tracker,
                    this,
                    callbackLoader,
                    callbackScope,
                    owner
                );
            scheduler=tasks;
        }

        @Override public synchronized ContentRegistrar content(){
            requireOpenLocked();
            return content;
        }

        @Override public synchronized PluginEvents events(){
            requireOpenLocked();
            return events;
        }

        @Override public synchronized PluginScheduler scheduler(){
            requireOpenLocked();
            return scheduler;
        }

        synchronized boolean open(){
            return open;
        }

        void seal(){
            ScopedContentRegistrar oldContent;
            ScopedPluginEvents oldEvents;

            synchronized(this){
                if(!open)
                    return;

                open=false;
                oldContent=content;
                oldEvents=events;
                content=null;
                events=null;
                scheduler=null;
            }

            if(oldContent!=null)
                oldContent.seal();

            if(oldEvents!=null)
                oldEvents.seal();
        }

        synchronized void requireOpen(){
            requireOpenLocked();
        }

        private void requireOpenLocked(){
            if(!open)
                throw new IllegalStateException(
                    "plugin registration context closed"
                );
        }
    }

    private static final class ScopedPluginEvents
        implements PluginEvents {

        private DomainEventBus eventBus;
        private EventTracker tracker;
        private ScopedPluginContext context;
        private ClassLoader callbackLoader;
        private PluginCallbackScope callbackScope;
        private Entry owner;
        private boolean sealed;

        ScopedPluginEvents(
            DomainEventBus eventBus,
            EventTracker tracker,
            ScopedPluginContext context,
            ClassLoader callbackLoader,
            PluginCallbackScope callbackScope,
            Entry owner
        ){
            this.eventBus=
                Objects.requireNonNull(
                    eventBus,
                    "eventBus"
                );
            this.tracker=
                Objects.requireNonNull(
                    tracker,
                    "tracker"
                );
            this.context=
                Objects.requireNonNull(
                    context,
                    "context"
                );
            this.callbackLoader=
                callbackLoader;
            this.callbackScope=
                Objects.requireNonNull(
                    callbackScope,
                    "callbackScope"
                );
            this.owner=
                Objects.requireNonNull(
                    owner,
                    "owner"
                );
        }

        @Override public synchronized
            <E extends DomainEventBus.Event>
            DomainEventBus.Subscription subscribe(
                Class<E> type,
                DomainEventBus.Priority priority,
                DomainEventBus.Listener<? super E> listener
            ){
            return subscribe(
                type,
                priority,
                false,
                listener
            );
        }

        @Override public synchronized
            <E extends DomainEventBus.Event>
            DomainEventBus.Subscription subscribe(
                Class<E> type,
                DomainEventBus.Priority priority,
                boolean receiveCancelled,
                DomainEventBus.Listener<? super E> listener
            ){
            requireOpen();

            DomainEventBus bus=eventBus;
            EventTracker eventOwner=tracker;
            ClassLoader loader=callbackLoader;
            PluginCallbackScope callbacks=
                callbackScope;
            Entry callbackOwner=
                owner;

            DomainEventBus.Subscription subscription=
                bus.subscribe(
                    type,
                    priority,
                    receiveCancelled,
                    event->{
                        if(!callbackOwner
                                .runtimeEnabled())
                            return;

                        try{
                            callbacks.call(
                                loader,
                                lease->{
                                    listener.onEvent(
                                        event
                                    );
                                    return null;
                                }
                            );
                        }catch(PluginCallbackScope.AdmissionException admission){
                            return;
                        }catch(Throwable failure){
                            callbackOwner.callbackFailure(
                                "EVENT:"+
                                type.getName(),
                                failure
                            );
                            System.err.println(
                                "[plugins] event callback failed type="+
                                type.getName()+
                                " errorClass="+
                                safeFailureClassName(
                                    failure
                                )
                            );
                        }
                    }
                );

            eventOwner.add(subscription);
            return subscription;
        }

        synchronized void seal(){
            sealed=true;
            eventBus=null;
            tracker=null;
            context=null;
            callbackLoader=null;
            callbackScope=null;
            owner=null;
        }

        private void requireOpen(){
            if(sealed||context==null)
                throw new IllegalStateException(
                    "plugin registration context closed"
                );

            context.requireOpen();
        }
    }

    private static final class EventTracker {
        private final ArrayList<DomainEventBus.Subscription>
            subscriptions=new ArrayList<>();
        private boolean closed;

        synchronized void add(
            DomainEventBus.Subscription subscription
        ){
            if(closed){
                subscription.unsubscribe();
                throw new IllegalStateException(
                    "plugin event tracker closed"
                );
            }

            subscriptions.add(subscription);
        }

        synchronized void close(
            java.util.function.BiConsumer<
                String,Throwable
            > failureHandler
        ){
            if(closed)
                return;

            closed=true;

            for(int i=subscriptions.size()-1;
                i>=0;
                i--){
                try{
                    subscriptions.get(i)
                        .unsubscribe();
                }catch(Throwable failure){
                    if(failureHandler!=null)
                        failureHandler.accept(
                            "CLEANUP:EVENT_UNSUBSCRIBE",
                            failure
                        );
                    System.err.println(
                        "[plugins] event unsubscribe failed errorClass="+
                        safeFailureClassName(
                            failure
                        )
                    );
                }
            }

            subscriptions.clear();
        }
    }

    private static final class Entry
        implements PluginHandle {

        Plugin plugin;
        final PluginManifest manifest;
        final String moduleId;
        final EventTracker events;
        PluginTaskTracker tasks;
        final PluginCallbackScope callbacks;
        final RuntimeAdmissionGate admission;
        final RuntimeCloseOwnership runtimeClose;
        ClassLoader callbackLoader;
        volatile RuntimeFailureSink failureSink;
        volatile RuntimeState runtimeState=
            RuntimeState.ENABLING;
        volatile boolean enabled=true;
        boolean cleanupClaimed;
        boolean cleanupComplete;

        Entry(
            Plugin plugin,
            PluginManifest manifest,
            String moduleId,
            EventTracker events,
            PluginCallbackScope callbacks,
            RuntimeAdmissionGate admission,
            RuntimeCloseOwnership runtimeClose,
            ClassLoader callbackLoader
        ){
            this.plugin=plugin;
            this.manifest=manifest;
            this.moduleId=moduleId;
            this.events=events;
            this.callbacks=callbacks;
            this.admission=
                Objects.requireNonNull(
                    admission,
                    "admission"
                );
            this.runtimeClose=
                Objects.requireNonNull(
                    runtimeClose,
                    "runtimeClose"
                );
            this.callbackLoader=
                callbackLoader;
        }

        boolean runtimeEnabled(){
            return admission.committed()&&
                runtimeState==
                    RuntimeState.ENABLED;
        }

        void callbackFailure(
            String callbackKind,
            Throwable failure
        ){
            RuntimeFailureSink sink=
                failureSink;

            if(sink!=null)
                sink.failed(
                    callbackKind,
                    failure
                );
        }

        @Override public PluginManifest manifest(){
            return manifest;
        }

        @Override public boolean enabled(){
            return enabled;
        }
    }

    private enum RuntimeState {
        ENABLING,
        ENABLED,
        TERMINAL
    }

    private static final class Candidate {
        final Plugin plugin;
        final ClassLoader callbackLoader;
        final PluginManifest manifest;
        final RuntimeCloseOwnership runtimeClose;

        Candidate(
            Plugin plugin,
            ClassLoader callbackLoader,
            PluginManifest manifest,
            RuntimeCloseOwnership runtimeClose
        ){
            this.plugin=
                Objects.requireNonNull(
                    plugin,
                    "plugin"
                );
            this.callbackLoader=
                callbackLoader;
            this.manifest=
                Objects.requireNonNull(
                    manifest,
                    "manifest"
                );
            this.runtimeClose=
                Objects.requireNonNull(
                    runtimeClose,
                    "runtimeClose"
                );
        }
    }

    private static final class RuntimeCloseOwnership {
        private volatile Plugin plugin;
        private boolean attempted;

        RuntimeCloseOwnership(
            Plugin plugin
        ){
            this.plugin=plugin;
        }

        boolean owns(
            Plugin candidate
        ){
            return plugin==candidate;
        }

        void release(){
            plugin=null;
        }

        synchronized Throwable close(
            Throwable primary
        ){
            if(attempted||
               !(plugin instanceof PluginRuntime))
                return null;

            attempted=true;

            return PluginRuntimeSupport
                .closePluginRuntime(
                    plugin,
                    primary
                );
        }
    }

    private static final class RuntimeAdmissionGate {
        private volatile boolean committed;

        boolean committed(){
            return committed;
        }

        void commit(){
            committed=true;
        }
    }

    private static final class FailureBatch {
        final List<Entry> entries;
        final String failedPluginId;
        boolean started;

        FailureBatch(
            List<Entry> entries,
            String failedPluginId
        ){
            this.entries=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        entries
                    )
                );
            this.failedPluginId=
                failedPluginId;
        }
    }

    @FunctionalInterface
    private interface RuntimeFailureSink {
        void failed(
            String callbackKind,
            Throwable failure
        );
    }

    private static final class PluginEnableFailure
        extends RuntimeException {

        PluginEnableFailure(
            Throwable cause
        ){
            super(cause);
        }
    }

    private static final class ScopedContentRegistrar
        implements ContentRegistrar {

        private ContentRegistrar delegate;
        private ScopedPluginContext context;
        private ClassLoader callbackLoader;
        private PluginCallbackScope callbackScope;
        private Entry owner;
        private boolean sealed;

        ScopedContentRegistrar(
            ContentRegistrar delegate,
            ScopedPluginContext context,
            ClassLoader callbackLoader,
            PluginCallbackScope callbackScope,
            Entry owner
        ){
            this.delegate=
                Objects.requireNonNull(
                    delegate,
                    "delegate"
                );
            this.context=
                Objects.requireNonNull(
                    context,
                    "context"
                );
            this.callbackLoader=
                callbackLoader;
            this.callbackScope=
                Objects.requireNonNull(
                    callbackScope,
                    "callbackScope"
                );
            this.owner=
                Objects.requireNonNull(
                    owner,
                    "owner"
                );
        }

        private void requireOpen(){
            if(sealed||
               delegate==null||
               context==null||
               callbackScope==null)
                throw new IllegalStateException(
                    "plugin registration context closed"
                );

            context.requireOpen();
        }

        private CallbackRuntime runtime(){
            requireOpen();

            return new CallbackRuntime(
                callbackLoader,
                callbackScope,
                owner
            );
        }

        synchronized void seal(){
            sealed=true;
            delegate=null;
            context=null;
            callbackLoader=null;
            callbackScope=null;
            owner=null;
        }

        @Override public synchronized ContentRegistration command(
            String name,
            int priority,
            ContentCommandHandler handler
        ){
            ContentRegistrar registrar=delegate;
            CallbackRuntime runtime=runtime();

            return registrar.command(
                name,
                priority,
                command->
                    runtime.call(
                        "CONTENT_COMMAND",
                        lease->
                            handler.handle(
                                runtime.callbacks
                                    .commandContext(
                                        command,
                                        lease
                                    )
                            )
                    )
            );
        }

        @Override public synchronized ContentRegistration objectOption(
            int objectId,
            int option,
            int priority,
            ContentObjectOptionHandler handler
        ){
            ContentRegistrar registrar=delegate;
            CallbackRuntime runtime=runtime();

            return registrar.objectOption(
                objectId,
                option,
                priority,
                object->
                    runtime.callUnchecked(
                        "CONTENT",
                        lease->handler.handle(object)
                    )
            );
        }

        @Override public synchronized ContentRegistration itemOption(
            int itemId,
            int option,
            int priority,
            ContentItemOptionHandler handler
        ){
            ContentRegistrar registrar=delegate;
            CallbackRuntime runtime=runtime();

            return registrar.itemOption(
                itemId,
                option,
                priority,
                item->
                    runtime.callUnchecked(
                        "CONTENT",
                        lease->handler.handle(item)
                    )
            );
        }

        @Override public synchronized ContentRegistration itemOnNpc(
            int itemId,
            int npcDefinitionId,
            int priority,
            ContentItemOnNpcHandler handler
        ){
            ContentRegistrar registrar=delegate;
            CallbackRuntime runtime=runtime();

            return registrar.itemOnNpc(
                itemId,
                npcDefinitionId,
                priority,
                interaction->
                    runtime.callUnchecked(
                        "CONTENT",
                        lease->handler.handle(interaction)
                    )
            );
        }

        @Override public synchronized ContentRegistration itemOnGroundItem(
            int itemId,
            int groundItemId,
            int priority,
            ContentItemOnGroundItemHandler handler
        ){
            ContentRegistrar registrar=delegate;
            CallbackRuntime runtime=runtime();

            return registrar.itemOnGroundItem(
                itemId,
                groundItemId,
                priority,
                interaction->
                    runtime.callUnchecked(
                        "CONTENT",
                        lease->handler.handle(interaction)
                    )
            );
        }

        @Override public synchronized ContentRegistration itemOnItem(
            int selectedItemId,
            int targetItemId,
            int priority,
            ContentItemOnItemHandler handler
        ){
            ContentRegistrar registrar=delegate;
            CallbackRuntime runtime=runtime();

            return registrar.itemOnItem(
                selectedItemId,
                targetItemId,
                priority,
                interaction->
                    runtime.callUnchecked(
                        "CONTENT",
                        lease->handler.handle(interaction)
                    )
            );
        }

        @Override public synchronized ContentRegistration itemOnObject(
            int itemId,
            int objectId,
            int priority,
            ContentItemOnObjectHandler handler
        ){
            ContentRegistrar registrar=delegate;
            CallbackRuntime runtime=runtime();

            return registrar.itemOnObject(
                itemId,
                objectId,
                priority,
                interaction->
                    runtime.callUnchecked(
                        "CONTENT",
                        lease->handler.handle(interaction)
                    )
            );
        }

        @Override public synchronized ContentRegistration itemOnPlayer(
            int itemId,
            int priority,
            ContentItemOnPlayerHandler handler
        ){
            ContentRegistrar registrar=delegate;
            CallbackRuntime runtime=runtime();

            return registrar.itemOnPlayer(
                itemId,
                priority,
                interaction->
                    runtime.callUnchecked(
                        "CONTENT",
                        lease->
                            handler.handle(
                                runtime.callbacks
                                    .itemOnPlayerContext(
                                        interaction,
                                        lease
                                    )
                            )
                    )
            );
        }

        @Override public synchronized ContentRegistration npcOption(
            int npcDefinitionId,
            int option,
            int priority,
            ContentNpcOptionHandler handler
        ){
            ContentRegistrar registrar=delegate;
            CallbackRuntime runtime=runtime();

            return registrar.npcOption(
                npcDefinitionId,
                option,
                priority,
                npc->
                    runtime.callUnchecked(
                        "CONTENT",
                        lease->handler.handle(npc)
                    )
            );
        }

        @Override public synchronized ContentRegistration dialogue(
            String dialogueKey,
            int priority,
            ContentDialogueHandler handler
        ){
            ContentRegistrar registrar=delegate;
            CallbackRuntime runtime=runtime();

            return registrar.dialogue(
                dialogueKey,
                priority,
                new ContentDialogueHandler(){
                    @Override public ContentDialogueTransition
                        handle(
                            ContentDialogueContext dialogue
                        ){
                        return runtime.callUnchecked(
                            "CONTENT_DIALOGUE",
                                lease->
                                    handler.handle(
                                        runtime.callbacks
                                            .dialogueContext(
                                                dialogue,
                                                lease
                                            )
                                    )
                            );
                    }

                    @Override public ContentDialogueDefinition
                        definition(){
                        return PluginThreadContext
                            .callUnchecked(
                                runtime.loader,
                                handler::definition
                            );
                    }
                }
            );
        }

        @Override public synchronized ContentRegistration action(
            String actionKey,
            int priority,
            ContentActionHandler handler
        ){
            ContentRegistrar registrar=delegate;
            CallbackRuntime runtime=runtime();

            return registrar.action(
                actionKey,
                priority,
                action->
                    runtime.callUnchecked(
                        "CONTENT",
                        lease->
                            handler.handle(
                                runtime.callbacks
                                    .actionContext(
                                        action,
                                        lease
                                    )
                            )
                    )
            );
        }

        private static final class CallbackRuntime {
            final ClassLoader loader;
            final PluginCallbackScope callbacks;
            final Entry owner;

            CallbackRuntime(
                ClassLoader loader,
                PluginCallbackScope callbacks,
                Entry owner
            ){
                this.loader=loader;
                this.callbacks=callbacks;
                this.owner=owner;
            }

            <T> T call(
                String kind,
                PluginCallbackScope.CheckedLeaseFunction<T>
                    action
            )throws Exception{
                if(!owner.runtimeEnabled())
                    throw new PluginCallbackScope.AdmissionException(
                        "plugin runtime callback unavailable"
                    );

                try{
                    return callbacks.call(
                        loader,
                        action
                    );
                }catch(PluginCallbackScope.AdmissionException admission){
                    throw admission;
                }catch(Exception|Error failure){
                    owner.callbackFailure(
                        kind,
                        failure
                    );
                    throw failure;
                }
            }

            <T> T callUnchecked(
                String kind,
                java.util.function.Function<
                    PluginCallbackScope.Lease,T
                > action
            ){
                if(!owner.runtimeEnabled())
                    throw new PluginCallbackScope.AdmissionException(
                        "plugin runtime callback unavailable"
                    );

                try{
                    return callbacks.callUnchecked(
                        loader,
                        action
                    );
                }catch(PluginCallbackScope.AdmissionException admission){
                    throw admission;
                }catch(RuntimeException|Error failure){
                    owner.callbackFailure(
                        kind,
                        failure
                    );
                    throw failure;
                }
            }
        }
    }
}
