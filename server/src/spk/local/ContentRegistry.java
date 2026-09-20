package spk.local;

import java.util.*;
import spk.content.api.*;

/**
 * World-thread content registry.
 *
 * Handler selection is deterministic: highest priority wins; equal-priority
 * collisions fail registration. Provenance is assigned by the core installer,
 * never by module registration calls.
 */
final class ContentRegistry {
    static final class BindingInfo {
        final String kind;
        final String key;
        final String moduleId;
        final int priority;
        final ContentProvenance provenance;

        BindingInfo(
            String kind,
            String key,
            String moduleId,
            int priority,
            ContentProvenance provenance
        ){
            this.kind=kind;
            this.key=key;
            this.moduleId=moduleId;
            this.priority=priority;
            this.provenance=provenance;
        }

        @Override public String toString(){
            return "BindingInfo{kind="+kind+
                ",key="+key+
                ",module="+moduleId+
                ",priority="+priority+
                ",provenance="+provenance+
                "}";
        }
    }

    private static final class CommandBinding {
        final BindingInfo info;
        final ContentCommandHandler handler;

        CommandBinding(
            BindingInfo info,
            ContentCommandHandler handler
        ){
            this.info=info;
            this.handler=handler;
        }
    }

    private final World world;
    private final LinkedHashMap<String,CommandBinding>
        commands=new LinkedHashMap<>();
    private final LinkedHashSet<String>
        installedModules=new LinkedHashSet<>();

    ContentRegistry(World world){
        this.world=Objects.requireNonNull(
            world,
            "world"
        );
    }

    void installCustom(
        ContentModule module
    ){
        installTrusted(
            module,
            ContentProvenance.CUSTOM_LOCALLAB
        );
    }

    void installTrusted(
        ContentModule module,
        ContentProvenance provenance
    ){
        Objects.requireNonNull(
            module,
            "module"
        );
        Objects.requireNonNull(
            provenance,
            "provenance"
        );

        String moduleId=
            cleanModuleId(
                module.id()
            );

        synchronized(this){
            if(!installedModules.add(
                    moduleId))
                throw new IllegalStateException(
                    "content module already installed: "+
                    moduleId
                );
        }

        ContentRegistrar registrar=
            new Registrar(
                moduleId,
                provenance
            );

        try{
            module.register(registrar);
        }catch(Throwable t){
            synchronized(this){
                installedModules.remove(
                    moduleId
                );
                removeModuleBindings(
                    moduleId
                );
            }

            if(t instanceof RuntimeException)
                throw (RuntimeException)t;
            if(t instanceof Error)
                throw (Error)t;
            throw new RuntimeException(t);
        }
    }

    ContentResult dispatchCommand(
        WorldPlayer player,
        String rawCommand,
        ServerPacketWriter writer
    )throws Exception{
        requireWorldThread();

        if(player==null||
           rawCommand==null)
            return null;

        String clean=
            LocalCommandDispatcher.clean(
                rawCommand
            );
        String[] tokens=
            LocalCommandDispatcher.tokens(
                clean
            );

        if(tokens.length==0||
           tokens[0].isEmpty())
            return null;

        CommandBinding binding;

        synchronized(this){
            binding=commands.get(
                canonical(tokens[0])
            );
        }

        if(binding==null)
            return null;

        ArrayList<String> arguments=
            new ArrayList<>();

        for(int i=1;i<tokens.length;i++)
            arguments.add(tokens[i]);

        ContentCommandContext context=
            new CommandContext(
                rawCommand,
                tokens[0],
                Collections.unmodifiableList(
                    arguments
                ),
                ContentRuntimeAdapters.player(
                    player
                ),
                ContentRuntimeAdapters.presentation(
                    writer
                )
            );

        return binding.handler.handle(
            context
        );
    }

    synchronized BindingInfo commandBinding(
        String name
    ){
        CommandBinding binding=
            commands.get(
                canonical(name)
            );
        return binding==null
            ?null
            :binding.info;
    }

    synchronized List<BindingInfo> bindings(){
        ArrayList<BindingInfo> result=
            new ArrayList<>();

        for(CommandBinding binding:
                commands.values())
            result.add(binding.info);

        return Collections.unmodifiableList(
            result
        );
    }

    synchronized String summary(){
        return "ContentRegistry{modules="+
            installedModules.size()+
            ",commands="+commands.size()+
            ",bindings="+bindings()+
            "}";
    }

    private void requireWorldThread(){
        if(!world.pulse()
                .inExecutionContext())
            throw new IllegalStateException(
                "content dispatch must run on World execution context"
            );
    }

    private synchronized void registerCommand(
        String moduleId,
        ContentProvenance provenance,
        String name,
        int priority,
        ContentCommandHandler handler
    ){
        String key=canonical(name);

        if(key.isEmpty())
            throw new IllegalArgumentException(
                "command name"
            );

        Objects.requireNonNull(
            handler,
            "handler"
        );

        CommandBinding existing=
            commands.get(key);

        BindingInfo info=
            new BindingInfo(
                "COMMAND",
                key,
                moduleId,
                priority,
                provenance
            );

        if(existing==null){
            commands.put(
                key,
                new CommandBinding(
                    info,
                    handler
                )
            );
            return;
        }

        if(priority==
                existing.info.priority)
            throw new IllegalStateException(
                "content binding conflict kind=COMMAND key="+
                key+
                " priority="+priority+
                " existingModule="+
                existing.info.moduleId+
                " incomingModule="+moduleId
            );

        if(priority>
                existing.info.priority)
            commands.put(
                key,
                new CommandBinding(
                    info,
                    handler
                )
            );
    }

    private synchronized void removeModuleBindings(
        String moduleId
    ){
        for(Iterator<Map.Entry<String,CommandBinding>>
                it=commands.entrySet()
                    .iterator();
            it.hasNext();){
            Map.Entry<String,CommandBinding>
                entry=it.next();

            if(entry.getValue().info.moduleId
                    .equals(moduleId))
                it.remove();
        }
    }

    private static String cleanModuleId(
        String value
    ){
        String clean=
            value==null
                ?""
                :value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "module id"
            );

        return clean;
    }

    private static String canonical(
        String value
    ){
        return value==null
            ?""
            :value.trim()
                .toLowerCase(
                    Locale.ROOT
                );
    }

    private final class Registrar
        implements ContentRegistrar {

        private final String moduleId;
        private final ContentProvenance provenance;

        Registrar(
            String moduleId,
            ContentProvenance provenance
        ){
            this.moduleId=moduleId;
            this.provenance=provenance;
        }

        @Override public void command(
            String name,
            int priority,
            ContentCommandHandler handler
        ){
            registerCommand(
                moduleId,
                provenance,
                name,
                priority,
                handler
            );
        }
    }

    private static final class CommandContext
        implements ContentCommandContext {

        private final String rawCommand;
        private final String commandName;
        private final List<String> arguments;
        private final ContentPlayer player;
        private final ContentPresentation presentation;

        CommandContext(
            String rawCommand,
            String commandName,
            List<String> arguments,
            ContentPlayer player,
            ContentPresentation presentation
        ){
            this.rawCommand=rawCommand;
            this.commandName=commandName;
            this.arguments=arguments;
            this.player=player;
            this.presentation=presentation;
        }

        @Override public String rawCommand(){
            return rawCommand;
        }

        @Override public String commandName(){
            return commandName;
        }

        @Override public List<String> arguments(){
            return arguments;
        }

        @Override public ContentPlayer player(){
            return player;
        }

        @Override public ContentPresentation presentation(){
            return presentation;
        }
    }
}
