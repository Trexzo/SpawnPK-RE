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

    private static final class ObjectOptionKey {
        final int objectId;
        final int option;

        ObjectOptionKey(
            int objectId,
            int option
        ){
            this.objectId=objectId;
            this.option=option;
        }

        String diagnosticKey(){
            return objectId+":"+option;
        }

        @Override public boolean equals(
            Object other
        ){
            if(this==other)return true;
            if(!(other instanceof ObjectOptionKey))
                return false;
            ObjectOptionKey key=
                (ObjectOptionKey)other;
            return objectId==key.objectId&&
                option==key.option;
        }

        @Override public int hashCode(){
            return 31*objectId+option;
        }
    }

    private static final class ObjectOptionBinding {
        final BindingInfo info;
        final ContentObjectOptionHandler handler;

        ObjectOptionBinding(
            BindingInfo info,
            ContentObjectOptionHandler handler
        ){
            this.info=info;
            this.handler=handler;
        }
    }

    private static final class NpcOptionKey {
        final int npcDefinitionId;
        final int option;

        NpcOptionKey(
            int npcDefinitionId,
            int option
        ){
            this.npcDefinitionId=npcDefinitionId;
            this.option=option;
        }

        String diagnosticKey(){
            return npcDefinitionId+":"+option;
        }

        @Override public boolean equals(
            Object other
        ){
            if(this==other)return true;
            if(!(other instanceof NpcOptionKey))
                return false;
            NpcOptionKey key=
                (NpcOptionKey)other;
            return npcDefinitionId==
                    key.npcDefinitionId&&
                option==key.option;
        }

        @Override public int hashCode(){
            return 31*npcDefinitionId+option;
        }
    }

    private static final class NpcOptionBinding {
        final BindingInfo info;
        final ContentNpcOptionHandler handler;

        NpcOptionBinding(
            BindingInfo info,
            ContentNpcOptionHandler handler
        ){
            this.info=info;
            this.handler=handler;
        }
    }

    private final World world;
    private final LinkedHashMap<String,CommandBinding>
        commands=new LinkedHashMap<>();
    private final LinkedHashMap<ObjectOptionKey,ObjectOptionBinding>
        objectOptions=new LinkedHashMap<>();
    private final LinkedHashMap<NpcOptionKey,NpcOptionBinding>
        npcOptions=new LinkedHashMap<>();

    /*
     * Keep every committed candidate, not just the current priority winner.
     * This lets a lifecycle handle remove one binding by identity and then
     * deterministically restore the next-highest candidate for that key.
     */
    private final ArrayList<CommandRegistration>
        commandRegistrations=new ArrayList<>();
    private final ArrayList<ObjectOptionRegistration>
        objectOptionRegistrations=new ArrayList<>();
    private final ArrayList<NpcOptionRegistration>
        npcOptionRegistrations=new ArrayList<>();

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

        try{
            module.register(registrar);
        }catch(RuntimeException|Error failure){
            registrar.invalidatePending();
            throw failure;
        }

        synchronized(this){
            try{
                if(installedModules.contains(
                        moduleId))
                    throw new IllegalStateException(
                        "content module already installed: "+
                        moduleId
                    );

                ArrayList<CommandRegistration>
                    nextCommandRegistrations=
                        new ArrayList<>(
                            commandRegistrations
                        );

                ArrayList<ObjectOptionRegistration>
                    nextObjectOptionRegistrations=
                        new ArrayList<>(
                            objectOptionRegistrations
                        );

                ArrayList<NpcOptionRegistration>
                    nextNpcOptionRegistrations=
                        new ArrayList<>(
                            npcOptionRegistrations
                        );

                for(CommandRegistration registration:
                        registrar.pendingCommands)
                    if(registration.handle.pending())
                        addCommandRegistration(
                            nextCommandRegistrations,
                            registration
                        );

                for(ObjectOptionRegistration registration:
                        registrar.pendingObjectOptions)
                    if(registration.handle.pending())
                        addObjectOptionRegistration(
                            nextObjectOptionRegistrations,
                            registration
                        );

                for(NpcOptionRegistration registration:
                        registrar.pendingNpcOptions)
                    if(registration.handle.pending())
                        addNpcOptionRegistration(
                            nextNpcOptionRegistrations,
                            registration
                        );

                LinkedHashMap<String,CommandBinding>
                    nextCommands=
                        buildCommandBindings(
                            nextCommandRegistrations
                        );

                LinkedHashMap<ObjectOptionKey,ObjectOptionBinding>
                    nextObjectOptions=
                        buildObjectOptionBindings(
                            nextObjectOptionRegistrations
                        );

                LinkedHashMap<NpcOptionKey,NpcOptionBinding>
                    nextNpcOptions=
                        buildNpcOptionBindings(
                            nextNpcOptionRegistrations
                        );

                commandRegistrations.clear();
                commandRegistrations.addAll(
                    nextCommandRegistrations
                );

                objectOptionRegistrations.clear();
                objectOptionRegistrations.addAll(
                    nextObjectOptionRegistrations
                );

                npcOptionRegistrations.clear();
                npcOptionRegistrations.addAll(
                    nextNpcOptionRegistrations
                );

                commands.clear();
                commands.putAll(nextCommands);

                objectOptions.clear();
                objectOptions.putAll(
                    nextObjectOptions
                );

                npcOptions.clear();
                npcOptions.putAll(
                    nextNpcOptions
                );

                installedModules.add(moduleId);
                registrar.activatePending();
            }catch(RuntimeException|Error failure){
                registrar.invalidatePending();
                throw failure;
            }
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

    ContentInteractionResult dispatchObjectOption(
        int objectId,
        int option,
        int worldX,
        int worldY
    ){
        requireWorldThread();

        ObjectOptionBinding binding;

        synchronized(this){
            binding=objectOptions.get(
                new ObjectOptionKey(
                    objectId,
                    option
                )
            );
        }

        if(binding==null)
            return null;

        return binding.handler.handle(
            new ObjectOptionContext(
                objectId,
                option,
                worldX,
                worldY
            )
        );
    }

    ContentNpcOptionResult dispatchNpcOption(
        int npcDefinitionId,
        int option,
        int worldX,
        int worldY
    ){
        requireWorldThread();

        NpcOptionBinding binding;

        synchronized(this){
            binding=npcOptions.get(
                new NpcOptionKey(
                    npcDefinitionId,
                    option
                )
            );
        }

        if(binding==null)
            return null;

        return binding.handler.handle(
            new NpcOptionContext(
                npcDefinitionId,
                option,
                worldX,
                worldY
            )
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

    synchronized BindingInfo objectOptionBinding(
        int objectId,
        int option
    ){
        ObjectOptionBinding binding=
            objectOptions.get(
                new ObjectOptionKey(
                    objectId,
                    option
                )
            );
        return binding==null
            ?null
            :binding.info;
    }

    synchronized BindingInfo npcOptionBinding(
        int npcDefinitionId,
        int option
    ){
        NpcOptionBinding binding=
            npcOptions.get(
                new NpcOptionKey(
                    npcDefinitionId,
                    option
                )
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

        for(ObjectOptionBinding binding:
                objectOptions.values())
            result.add(binding.info);

        for(NpcOptionBinding binding:
                npcOptions.values())
            result.add(binding.info);

        return Collections.unmodifiableList(
            result
        );
    }

    synchronized String summary(){
        return "ContentRegistry{modules="+
            installedModules.size()+
            ",commands="+commands.size()+
            ",objectOptions="+
                objectOptions.size()+
            ",npcOptions="+
                npcOptions.size()+
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

    private static void addCommandRegistration(
        List<CommandRegistration> target,
        CommandRegistration incoming
    ){
        for(CommandRegistration existing:
                target)
            if(existing.info.key.equals(
                    incoming.info.key)&&
               existing.info.priority==
                    incoming.info.priority)
                throw conflict(
                    incoming.info,
                    existing.info
                );

        target.add(incoming);
    }

    private static void addObjectOptionRegistration(
        List<ObjectOptionRegistration> target,
        ObjectOptionRegistration incoming
    ){
        for(ObjectOptionRegistration existing:
                target)
            if(existing.key.equals(
                    incoming.key)&&
               existing.info.priority==
                    incoming.info.priority)
                throw conflict(
                    incoming.info,
                    existing.info
                );

        target.add(incoming);
    }

    private static void addNpcOptionRegistration(
        List<NpcOptionRegistration> target,
        NpcOptionRegistration incoming
    ){
        for(NpcOptionRegistration existing:
                target)
            if(existing.key.equals(
                    incoming.key)&&
               existing.info.priority==
                    incoming.info.priority)
                throw conflict(
                    incoming.info,
                    existing.info
                );

        target.add(incoming);
    }

    private static LinkedHashMap<String,CommandBinding>
        buildCommandBindings(
            List<CommandRegistration> registrations
        ){
        LinkedHashMap<String,CommandBinding>
            result=new LinkedHashMap<>();

        for(CommandRegistration registration:
                registrations)
            applyCommand(
                result,
                registration
            );

        return result;
    }

    private static LinkedHashMap<ObjectOptionKey,ObjectOptionBinding>
        buildObjectOptionBindings(
            List<ObjectOptionRegistration> registrations
        ){
        LinkedHashMap<ObjectOptionKey,ObjectOptionBinding>
            result=new LinkedHashMap<>();

        for(ObjectOptionRegistration registration:
                registrations)
            applyObjectOption(
                result,
                registration
            );

        return result;
    }

    private static LinkedHashMap<NpcOptionKey,NpcOptionBinding>
        buildNpcOptionBindings(
            List<NpcOptionRegistration> registrations
        ){
        LinkedHashMap<NpcOptionKey,NpcOptionBinding>
            result=new LinkedHashMap<>();

        for(NpcOptionRegistration registration:
                registrations)
            applyNpcOption(
                result,
                registration
            );

        return result;
    }

    private synchronized boolean unregister(
        RegistrationHandle handle
    ){
        if(handle.state==REGISTRATION_REMOVED)
            return false;

        if(handle.state==REGISTRATION_PENDING){
            handle.state=REGISTRATION_REMOVED;
            return true;
        }

        boolean removed=
            commandRegistrations.removeIf(
                registration->
                    registration.handle==handle
            );

        removed=
            objectOptionRegistrations.removeIf(
                registration->
                    registration.handle==handle
            )||removed;

        removed=
            npcOptionRegistrations.removeIf(
                registration->
                    registration.handle==handle
            )||removed;

        if(!removed)
            throw new IllegalStateException(
                "active content registration missing"
            );

        handle.state=REGISTRATION_REMOVED;
        rebuildEffectiveBindings();
        return true;
    }

    private void rebuildEffectiveBindings(){
        LinkedHashMap<String,CommandBinding>
            nextCommands=
                buildCommandBindings(
                    commandRegistrations
                );

        LinkedHashMap<ObjectOptionKey,ObjectOptionBinding>
            nextObjectOptions=
                buildObjectOptionBindings(
                    objectOptionRegistrations
                );

        LinkedHashMap<NpcOptionKey,NpcOptionBinding>
            nextNpcOptions=
                buildNpcOptionBindings(
                    npcOptionRegistrations
                );

        commands.clear();
        commands.putAll(nextCommands);

        objectOptions.clear();
        objectOptions.putAll(
            nextObjectOptions
        );

        npcOptions.clear();
        npcOptions.putAll(
            nextNpcOptions
        );
    }

    private static void applyCommand(
        Map<String,CommandBinding> target,
        CommandRegistration registration
    ){
        CommandBinding existing=
            target.get(
                registration.info.key
            );

        if(existing==null){
            target.put(
                registration.info.key,
                new CommandBinding(
                    registration.info,
                    registration.handler
                )
            );
            return;
        }

        if(registration.info.priority==
                existing.info.priority)
            throw conflict(
                registration.info,
                existing.info
            );

        if(registration.info.priority>
                existing.info.priority)
            target.put(
                registration.info.key,
                new CommandBinding(
                    registration.info,
                    registration.handler
                )
            );
    }

    private static void applyObjectOption(
        Map<ObjectOptionKey,ObjectOptionBinding> target,
        ObjectOptionRegistration registration
    ){
        ObjectOptionBinding existing=
            target.get(
                registration.key
            );

        if(existing==null){
            target.put(
                registration.key,
                new ObjectOptionBinding(
                    registration.info,
                    registration.handler
                )
            );
            return;
        }

        if(registration.info.priority==
                existing.info.priority)
            throw conflict(
                registration.info,
                existing.info
            );

        if(registration.info.priority>
                existing.info.priority)
            target.put(
                registration.key,
                new ObjectOptionBinding(
                    registration.info,
                    registration.handler
                )
            );
    }

    private static void applyNpcOption(
        Map<NpcOptionKey,NpcOptionBinding> target,
        NpcOptionRegistration registration
    ){
        NpcOptionBinding existing=
            target.get(
                registration.key
            );

        if(existing==null){
            target.put(
                registration.key,
                new NpcOptionBinding(
                    registration.info,
                    registration.handler
                )
            );
            return;
        }

        if(registration.info.priority==
                existing.info.priority)
            throw conflict(
                registration.info,
                existing.info
            );

        if(registration.info.priority>
                existing.info.priority)
            target.put(
                registration.key,
                new NpcOptionBinding(
                    registration.info,
                    registration.handler
                )
            );
    }

    private static IllegalStateException conflict(
        BindingInfo incoming,
        BindingInfo existing
    ){
        return new IllegalStateException(
            "content binding conflict kind="+
            incoming.kind+
            " key="+incoming.key+
            " priority="+incoming.priority+
            " existingModule="+
            existing.moduleId+
            " incomingModule="+
            incoming.moduleId
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

    private static final int REGISTRATION_PENDING=0;
    private static final int REGISTRATION_ACTIVE=1;
    private static final int REGISTRATION_REMOVED=2;

    private static final class CommandRegistration {
        final BindingInfo info;
        final ContentCommandHandler handler;
        final RegistrationHandle handle;

        CommandRegistration(
            BindingInfo info,
            ContentCommandHandler handler,
            RegistrationHandle handle
        ){
            this.info=info;
            this.handler=handler;
            this.handle=handle;
        }
    }

    private static final class ObjectOptionRegistration {
        final ObjectOptionKey key;
        final BindingInfo info;
        final ContentObjectOptionHandler handler;
        final RegistrationHandle handle;

        ObjectOptionRegistration(
            ObjectOptionKey key,
            BindingInfo info,
            ContentObjectOptionHandler handler,
            RegistrationHandle handle
        ){
            this.key=key;
            this.info=info;
            this.handler=handler;
            this.handle=handle;
        }
    }

    private static final class NpcOptionRegistration {
        final NpcOptionKey key;
        final BindingInfo info;
        final ContentNpcOptionHandler handler;
        final RegistrationHandle handle;

        NpcOptionRegistration(
            NpcOptionKey key,
            BindingInfo info,
            ContentNpcOptionHandler handler,
            RegistrationHandle handle
        ){
            this.key=key;
            this.info=info;
            this.handler=handler;
            this.handle=handle;
        }
    }

    private final class RegistrationHandle
        implements ContentRegistration {

        private int state=REGISTRATION_PENDING;

        @Override public boolean active(){
            synchronized(ContentRegistry.this){
                return state==
                    REGISTRATION_ACTIVE;
            }
        }

        @Override public boolean unregister(){
            return ContentRegistry.this
                .unregister(this);
        }

        boolean pending(){
            synchronized(ContentRegistry.this){
                return state==
                    REGISTRATION_PENDING;
            }
        }

        void activatePending(){
            synchronized(ContentRegistry.this){
                if(state==REGISTRATION_PENDING)
                    state=REGISTRATION_ACTIVE;
            }
        }

        void invalidatePending(){
            synchronized(ContentRegistry.this){
                if(state==REGISTRATION_PENDING)
                    state=REGISTRATION_REMOVED;
            }
        }
    }

    private final class Registrar
        implements ContentRegistrar {

        private final String moduleId;
        private final ContentProvenance provenance;
        private final ArrayList<CommandRegistration>
            pendingCommands=new ArrayList<>();
        private final ArrayList<ObjectOptionRegistration>
            pendingObjectOptions=
                new ArrayList<>();
        private final ArrayList<NpcOptionRegistration>
            pendingNpcOptions=
                new ArrayList<>();

        Registrar(
            String moduleId,
            ContentProvenance provenance
        ){
            this.moduleId=moduleId;
            this.provenance=provenance;
        }

        @Override public ContentRegistration command(
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

            RegistrationHandle handle=
                new RegistrationHandle();

            pendingCommands.add(
                new CommandRegistration(
                    new BindingInfo(
                        "COMMAND",
                        key,
                        moduleId,
                        priority,
                        provenance
                    ),
                    handler,
                    handle
                )
            );

            return handle;
        }

        @Override public ContentRegistration objectOption(
            int objectId,
            int option,
            int priority,
            ContentObjectOptionHandler handler
        ){
            if(objectId<0)
                throw new IllegalArgumentException(
                    "objectId"
                );

            if(option<1||option>5)
                throw new IllegalArgumentException(
                    "object option"
                );

            Objects.requireNonNull(
                handler,
                "handler"
            );

            ObjectOptionKey key=
                new ObjectOptionKey(
                    objectId,
                    option
                );

            RegistrationHandle handle=
                new RegistrationHandle();

            pendingObjectOptions.add(
                new ObjectOptionRegistration(
                    key,
                    new BindingInfo(
                        "OBJECT_OPTION",
                        key.diagnosticKey(),
                        moduleId,
                        priority,
                        provenance
                    ),
                    handler,
                    handle
                )
            );

            return handle;
        }

        @Override public ContentRegistration npcOption(
            int npcDefinitionId,
            int option,
            int priority,
            ContentNpcOptionHandler handler
        ){
            if(npcDefinitionId<0)
                throw new IllegalArgumentException(
                    "npcDefinitionId"
                );

            if(option<1||option>5)
                throw new IllegalArgumentException(
                    "npc option"
                );

            Objects.requireNonNull(
                handler,
                "handler"
            );

            NpcOptionKey key=
                new NpcOptionKey(
                    npcDefinitionId,
                    option
                );

            RegistrationHandle handle=
                new RegistrationHandle();

            pendingNpcOptions.add(
                new NpcOptionRegistration(
                    key,
                    new BindingInfo(
                        "NPC_OPTION",
                        key.diagnosticKey(),
                        moduleId,
                        priority,
                        provenance
                    ),
                    handler,
                    handle
                )
            );

            return handle;
        }

        void activatePending(){
            for(CommandRegistration registration:
                    pendingCommands)
                registration.handle
                    .activatePending();

            for(ObjectOptionRegistration registration:
                    pendingObjectOptions)
                registration.handle
                    .activatePending();

            for(NpcOptionRegistration registration:
                    pendingNpcOptions)
                registration.handle
                    .activatePending();
        }

        void invalidatePending(){
            for(CommandRegistration registration:
                    pendingCommands)
                registration.handle
                    .invalidatePending();

            for(ObjectOptionRegistration registration:
                    pendingObjectOptions)
                registration.handle
                    .invalidatePending();

            for(NpcOptionRegistration registration:
                    pendingNpcOptions)
                registration.handle
                    .invalidatePending();
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

    private static final class ObjectOptionContext
        implements ContentObjectOptionContext {

        private final int objectId;
        private final int option;
        private final int worldX;
        private final int worldY;

        ObjectOptionContext(
            int objectId,
            int option,
            int worldX,
            int worldY
        ){
            this.objectId=objectId;
            this.option=option;
            this.worldX=worldX;
            this.worldY=worldY;
        }

        @Override public int objectId(){
            return objectId;
        }

        @Override public int option(){
            return option;
        }

        @Override public int worldX(){
            return worldX;
        }

        @Override public int worldY(){
            return worldY;
        }
    }
    private static final class NpcOptionContext
        implements ContentNpcOptionContext {

        private final int npcDefinitionId;
        private final int option;
        private final int worldX;
        private final int worldY;

        NpcOptionContext(
            int npcDefinitionId,
            int option,
            int worldX,
            int worldY
        ){
            this.npcDefinitionId=npcDefinitionId;
            this.option=option;
            this.worldX=worldX;
            this.worldY=worldY;
        }

        @Override public int npcDefinitionId(){
            return npcDefinitionId;
        }

        @Override public int option(){
            return option;
        }

        @Override public int worldX(){
            return worldX;
        }

        @Override public int worldY(){
            return worldY;
        }
    }

}
