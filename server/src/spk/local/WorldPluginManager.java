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

    private final ContentRegistry content;
    private final DomainEventBus events;
    private final BooleanSupplier worldOpen;
    private final LinkedHashMap<String,Entry>
        enabled=new LinkedHashMap<>();

    private boolean closed;
    private boolean resourcesClosed;

    WorldPluginManager(
        ContentRegistry content,
        DomainEventBus events,
        BooleanSupplier worldOpen
    ){
        this.content=Objects.requireNonNull(
            content,
            "content"
        );
        this.events=Objects.requireNonNull(
            events,
            "events"
        );
        this.worldOpen=Objects.requireNonNull(
            worldOpen,
            "worldOpen"
        );
    }

    @Override public synchronized PluginHandle enable(
        Plugin plugin
    )throws Exception{
        requireOpen();
        return enableOne(plugin);
    }

    @Override public synchronized List<PluginHandle>
        enableAll(
            Collection<? extends Plugin> plugins
        )throws Exception{
        requireOpen();

        List<Plugin> ordered=
            dependencyOrder(plugins);

        ArrayList<Entry> added=
            new ArrayList<>();

        try{
            for(Plugin plugin:ordered)
                added.add(
                    enableOne(plugin)
                );
        }catch(Throwable failure){
            for(int i=added.size()-1;i>=0;i--)
                disableEntry(
                    added.get(i),
                    "BATCH_ROLLBACK"
                );

            rethrow(failure);
        }

        return Collections.unmodifiableList(
            new ArrayList<PluginHandle>(added)
        );
    }

    @Override public synchronized boolean disable(
        String pluginId
    ){
        String id=canonicalId(pluginId);
        Entry entry=enabled.get(id);

        if(entry==null)
            return false;

        for(Entry candidate:enabled.values())
            if(candidate!=entry&&
               candidate.enabled&&
               candidate.manifest.dependencies()
                    .contains(id))
                throw new IllegalStateException(
                    "plugin has enabled dependent: "+
                    candidate.manifest.id()+
                    " -> "+id
                );

        disableEntry(
            entry,
            "EXPLICIT_DISABLE"
        );
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
    synchronized void closeResources(){
        if(resourcesClosed)
            return;

        closed=true;

        ArrayList<Entry> entries=
            new ArrayList<>(enabled.values());

        for(int i=entries.size()-1;i>=0;i--)
            disableEntry(
                entries.get(i),
                "WORLD_CLOSE"
            );

        resourcesClosed=true;
    }

    @Override public void close(){
        beginClose();
        closeResources();
    }

    private Entry enableOne(
        Plugin plugin
    )throws Exception{
        Objects.requireNonNull(
            plugin,
            "plugin"
        );

        PluginManifest manifest=
            Objects.requireNonNull(
                plugin.manifest(),
                "plugin manifest"
            );

        validateCompatibility(manifest);

        String id=manifest.id();

        if(enabled.containsKey(id))
            throw new IllegalStateException(
                "plugin already enabled: "+id
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

        EventTracker tracker=
            new EventTracker();

        PluginContentModule module=
            new PluginContentModule(
                moduleId,
                plugin,
                tracker
            );

        try{
            content.installCustom(module);

            Entry entry=
                new Entry(
                    plugin,
                    manifest,
                    moduleId,
                    tracker
                );

            enabled.put(id,entry);
            return entry;
        }catch(Throwable failure){
            module.seal();

            if(module.enableAttempted())
                try{
                    plugin.disable();
                }catch(Throwable cleanup){
                    failure.addSuppressed(cleanup);
                }

            tracker.close();

            try{
                content.uninstallModule(
                    moduleId
                );
            }catch(Throwable cleanup){
                failure.addSuppressed(cleanup);
            }

            rethrow(failure);
            throw new AssertionError(
                "unreachable"
            );
        }
    }

    private void disableEntry(
        Entry entry,
        String reason
    ){
        if(entry==null||!entry.enabled)
            return;

        entry.enabled=false;
        enabled.remove(
            entry.manifest.id()
        );

        try{
            entry.plugin.disable();
        }catch(Throwable failure){
            System.err.println(
                "[plugins] disable callback failed id="+
                entry.manifest.id()+
                " reason="+reason+
                " error="+failure
            );
        }

        entry.events.close();

        try{
            content.uninstallModule(
                entry.moduleId
            );
        }catch(Throwable failure){
            System.err.println(
                "[plugins] content uninstall failed id="+
                entry.manifest.id()+
                " reason="+reason+
                " error="+failure
            );
        }
    }

    private List<Plugin> dependencyOrder(
        Collection<? extends Plugin> plugins
    ){
        Objects.requireNonNull(
            plugins,
            "plugins"
        );

        TreeMap<String,Plugin> pending=
            new TreeMap<>();

        for(Plugin plugin:plugins){
            Objects.requireNonNull(
                plugin,
                "plugin"
            );

            PluginManifest manifest=
                Objects.requireNonNull(
                    plugin.manifest(),
                    "plugin manifest"
                );

            validateCompatibility(
                manifest
            );

            if(enabled.containsKey(
                    manifest.id()))
                throw new IllegalStateException(
                    "plugin already enabled: "+
                    manifest.id()
                );

            Plugin duplicate=
                pending.put(
                    manifest.id(),
                    plugin
                );

            if(duplicate!=null)
                throw new IllegalStateException(
                    "duplicate plugin id in batch: "+
                    manifest.id()
                );
        }

        for(Plugin plugin:pending.values())
            for(String dependency:
                    plugin.manifest()
                        .dependencies())
                if(!enabled.containsKey(dependency)&&
                   !pending.containsKey(dependency))
                    throw new IllegalStateException(
                        "plugin dependency missing: "+
                        plugin.manifest().id()+
                        " -> "+dependency
                    );

        ArrayList<Plugin> ordered=
            new ArrayList<>();
        HashSet<String> resolved=
            new HashSet<>(
                enabled.keySet()
            );

        while(!pending.isEmpty()){
            String ready=null;

            for(Map.Entry<String,Plugin> candidate:
                    pending.entrySet()){
                if(resolved.containsAll(
                        candidate.getValue()
                            .manifest()
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

            Plugin plugin=
                pending.remove(ready);

            ordered.add(plugin);
            resolved.add(ready);
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

    private static void rethrow(
        Throwable failure
    )throws Exception{
        if(failure instanceof PluginEnableFailure){
            Throwable cause=
                failure.getCause();

            if(cause!=null)
                rethrow(cause);
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
        private volatile ScopedPluginContext context;
        private volatile boolean enableAttempted;

        PluginContentModule(
            String moduleId,
            Plugin plugin,
            EventTracker tracker
        ){
            this.moduleId=moduleId;
            this.plugin=plugin;
            this.tracker=tracker;
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
                    tracker
                );

            context=local;
            enableAttempted=true;

            try{
                plugin.enable(local);
            }catch(Throwable failure){
                throw new PluginEnableFailure(
                    failure
                );
            }finally{
                local.seal();
            }
        }

        void seal(){
            ScopedPluginContext current=context;
            if(current!=null)
                current.seal();
        }

        boolean enableAttempted(){
            return enableAttempted;
        }
    }

    private final class ScopedPluginContext
        implements PluginContext {

        private final ScopedContentRegistrar content;
        private final ScopedPluginEvents events;
        private boolean open=true;

        ScopedPluginContext(
            ContentRegistrar registrar,
            EventTracker tracker
        ){
            content=
                new ScopedContentRegistrar(
                    registrar,
                    this
                );
            events=
                new ScopedPluginEvents(
                    tracker,
                    this
                );
        }

        @Override public ContentRegistrar content(){
            requireOpen();
            return content;
        }

        @Override public PluginEvents events(){
            requireOpen();
            return events;
        }

        synchronized boolean open(){
            return open;
        }

        synchronized void seal(){
            open=false;
        }

        void requireOpen(){
            if(!open())
                throw new IllegalStateException(
                    "plugin registration context closed"
                );
        }
    }

    private final class ScopedPluginEvents
        implements PluginEvents {

        private final EventTracker tracker;
        private final ScopedPluginContext context;

        ScopedPluginEvents(
            EventTracker tracker,
            ScopedPluginContext context
        ){
            this.tracker=tracker;
            this.context=context;
        }

        @Override public <E extends DomainEventBus.Event>
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

        @Override public <E extends DomainEventBus.Event>
            DomainEventBus.Subscription subscribe(
                Class<E> type,
                DomainEventBus.Priority priority,
                boolean receiveCancelled,
                DomainEventBus.Listener<? super E> listener
            ){
            context.requireOpen();

            DomainEventBus.Subscription subscription=
                events.subscribe(
                    type,
                    priority,
                    receiveCancelled,
                    listener
                );

            tracker.add(subscription);
            return subscription;
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

        synchronized void close(){
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
                    System.err.println(
                        "[plugins] event unsubscribe failed error="+
                        failure
                    );
                }
            }

            subscriptions.clear();
        }
    }

    private static final class Entry
        implements PluginHandle {

        final Plugin plugin;
        final PluginManifest manifest;
        final String moduleId;
        final EventTracker events;
        volatile boolean enabled=true;

        Entry(
            Plugin plugin,
            PluginManifest manifest,
            String moduleId,
            EventTracker events
        ){
            this.plugin=plugin;
            this.manifest=manifest;
            this.moduleId=moduleId;
            this.events=events;
        }

        @Override public PluginManifest manifest(){
            return manifest;
        }

        @Override public boolean enabled(){
            return enabled;
        }
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

        private final ContentRegistrar delegate;
        private final ScopedPluginContext context;

        ScopedContentRegistrar(
            ContentRegistrar delegate,
            ScopedPluginContext context
        ){
            this.delegate=delegate;
            this.context=context;
        }

        private void requireOpen(){
            context.requireOpen();
        }

        @Override public ContentRegistration command(
            String name,
            int priority,
            ContentCommandHandler handler
        ){
            requireOpen();
            return delegate.command(
                name,
                priority,
                handler
            );
        }

        @Override public ContentRegistration objectOption(
            int objectId,
            int option,
            int priority,
            ContentObjectOptionHandler handler
        ){
            requireOpen();
            return delegate.objectOption(
                objectId,
                option,
                priority,
                handler
            );
        }

        @Override public ContentRegistration itemOption(
            int itemId,
            int option,
            int priority,
            ContentItemOptionHandler handler
        ){
            requireOpen();
            return delegate.itemOption(
                itemId,
                option,
                priority,
                handler
            );
        }

        @Override public ContentRegistration itemOnNpc(
            int itemId,
            int npcDefinitionId,
            int priority,
            ContentItemOnNpcHandler handler
        ){
            requireOpen();
            return delegate.itemOnNpc(
                itemId,
                npcDefinitionId,
                priority,
                handler
            );
        }

        @Override public ContentRegistration itemOnGroundItem(
            int itemId,
            int groundItemId,
            int priority,
            ContentItemOnGroundItemHandler handler
        ){
            requireOpen();
            return delegate.itemOnGroundItem(
                itemId,
                groundItemId,
                priority,
                handler
            );
        }

        @Override public ContentRegistration itemOnItem(
            int selectedItemId,
            int targetItemId,
            int priority,
            ContentItemOnItemHandler handler
        ){
            requireOpen();
            return delegate.itemOnItem(
                selectedItemId,
                targetItemId,
                priority,
                handler
            );
        }

        @Override public ContentRegistration itemOnObject(
            int itemId,
            int objectId,
            int priority,
            ContentItemOnObjectHandler handler
        ){
            requireOpen();
            return delegate.itemOnObject(
                itemId,
                objectId,
                priority,
                handler
            );
        }

        @Override public ContentRegistration itemOnPlayer(
            int itemId,
            int priority,
            ContentItemOnPlayerHandler handler
        ){
            requireOpen();
            return delegate.itemOnPlayer(
                itemId,
                priority,
                handler
            );
        }

        @Override public ContentRegistration npcOption(
            int npcDefinitionId,
            int option,
            int priority,
            ContentNpcOptionHandler handler
        ){
            requireOpen();
            return delegate.npcOption(
                npcDefinitionId,
                option,
                priority,
                handler
            );
        }
    }
}
