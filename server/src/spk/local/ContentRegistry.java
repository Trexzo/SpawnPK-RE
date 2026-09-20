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
            if(installedModules.contains(
                    moduleId))
                throw new IllegalStateException(
                    "content module already installed: "+
                    moduleId
                );
        }

        Registrar registrar=
            new Registrar(
                moduleId,
                provenance
            );

        module.register(registrar);

        synchronized(this){
            if(installedModules.contains(
                    moduleId))
                throw new IllegalStateException(
                    "content module already installed: "+
                    moduleId
                );

            LinkedHashMap<String,CommandBinding>
                next=
                    new LinkedHashMap<>(
                        commands
                    );

            for(PendingCommand pending:
                    registrar.pendingCommands)
                applyCommand(
                    next,
                    pending
                );

            commands.clear();
            commands.putAll(next);
            installedModules.add(moduleId);
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

    private static void applyCommand(
        Map<String,CommandBinding> target,
        PendingCommand pending
    ){
        CommandBinding existing=
            target.get(
                pending.info.key
            );

        if(existing==null){
            target.put(
                pending.info.key,
                new CommandBinding(
                    pending.info,
                    pending.handler
                )
            );
            return;
        }

        if(pending.info.priority==
                existing.info.priority)
            throw new IllegalStateException(
                "content binding conflict kind=COMMAND key="+
                pending.info.key+
                " priority="+
                pending.info.priority+
                " existingModule="+
                existing.info.moduleId+
                " incomingModule="+
                pending.info.moduleId
            );

        if(pending.info.priority>
                existing.info.priority)
            target.put(
                pending.info.key,
                new CommandBinding(
                    pending.info,
                    pending.handler
                )
            );
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

    private static final class PendingCommand {
        final BindingInfo info;
        final ContentCommandHandler handler;

        PendingCommand(
            BindingInfo info,
            ContentCommandHandler handler
        ){
            this.info=info;
            this.handler=handler;
        }
    }

    private static final class Registrar
        implements ContentRegistrar {

        private final String moduleId;
        private final ContentProvenance provenance;
        private final ArrayList<PendingCommand>
            pendingCommands=new ArrayList<>();

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
            String key=canonical(name);

            if(key.isEmpty())
                throw new IllegalArgumentException(
                    "command name"
                );

            Objects.requireNonNull(
                handler,
                "handler"
            );

            pendingCommands.add(
                new PendingCommand(
                    new BindingInfo(
                        "COMMAND",
                        key,
                        moduleId,
                        priority,
                        provenance
                    ),
                    handler
                )
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
