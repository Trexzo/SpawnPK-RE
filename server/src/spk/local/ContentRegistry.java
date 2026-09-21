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

    private static final class ItemOptionKey {
        final int itemId;
        final int option;

        ItemOptionKey(
            int itemId,
            int option
        ){
            this.itemId=itemId;
            this.option=option;
        }

        String diagnosticKey(){
            return itemId+":"+option;
        }

        @Override public boolean equals(
            Object other
        ){
            if(this==other)return true;
            if(!(other instanceof ItemOptionKey))
                return false;
            ItemOptionKey key=
                (ItemOptionKey)other;
            return itemId==key.itemId&&
                option==key.option;
        }

        @Override public int hashCode(){
            return 31*itemId+option;
        }
    }

    private static final class ItemOptionBinding {
        final BindingInfo info;
        final ContentItemOptionHandler handler;

        ItemOptionBinding(
            BindingInfo info,
            ContentItemOptionHandler handler
        ){
            this.info=info;
            this.handler=handler;
        }
    }

    private static final class ItemOnNpcKey {
        final int itemId;
        final int npcDefinitionId;

        ItemOnNpcKey(
            int itemId,
            int npcDefinitionId
        ){
            this.itemId=itemId;
            this.npcDefinitionId=npcDefinitionId;
        }

        String diagnosticKey(){
            return itemId+":"+npcDefinitionId;
        }

        @Override public boolean equals(
            Object other
        ){
            if(this==other)return true;
            if(!(other instanceof ItemOnNpcKey))
                return false;
            ItemOnNpcKey key=
                (ItemOnNpcKey)other;
            return itemId==key.itemId&&
                npcDefinitionId==key.npcDefinitionId;
        }

        @Override public int hashCode(){
            return 31*itemId+npcDefinitionId;
        }
    }

    private static final class ItemOnNpcBinding {
        final BindingInfo info;
        final ContentItemOnNpcHandler handler;

        ItemOnNpcBinding(
            BindingInfo info,
            ContentItemOnNpcHandler handler
        ){
            this.info=info;
            this.handler=handler;
        }
    }

    private static final class ItemOnItemKey {
        final int selectedItemId;
        final int targetItemId;

        ItemOnItemKey(
            int selectedItemId,
            int targetItemId
        ){
            this.selectedItemId=selectedItemId;
            this.targetItemId=targetItemId;
        }

        String diagnosticKey(){
            return selectedItemId+":"+targetItemId;
        }

        @Override public boolean equals(
            Object other
        ){
            if(this==other)return true;
            if(!(other instanceof ItemOnItemKey))
                return false;
            ItemOnItemKey key=
                (ItemOnItemKey)other;
            return selectedItemId==key.selectedItemId&&
                targetItemId==key.targetItemId;
        }

        @Override public int hashCode(){
            return 31*selectedItemId+targetItemId;
        }
    }

    private static final class ItemOnItemBinding {
        final BindingInfo info;
        final ContentItemOnItemHandler handler;

        ItemOnItemBinding(
            BindingInfo info,
            ContentItemOnItemHandler handler
        ){
            this.info=info;
            this.handler=handler;
        }
    }

    private static final class ItemOnGroundItemKey {
        final int itemId;
        final int groundItemId;

        ItemOnGroundItemKey(
            int itemId,
            int groundItemId
        ){
            this.itemId=itemId;
            this.groundItemId=groundItemId;
        }

        String diagnosticKey(){
            return itemId+":"+groundItemId;
        }

        @Override public boolean equals(
            Object other
        ){
            if(this==other)return true;
            if(!(other instanceof ItemOnGroundItemKey))
                return false;
            ItemOnGroundItemKey key=
                (ItemOnGroundItemKey)other;
            return itemId==key.itemId&&
                groundItemId==key.groundItemId;
        }

        @Override public int hashCode(){
            return 31*itemId+groundItemId;
        }
    }

    private static final class ItemOnGroundItemBinding {
        final BindingInfo info;
        final ContentItemOnGroundItemHandler handler;

        ItemOnGroundItemBinding(
            BindingInfo info,
            ContentItemOnGroundItemHandler handler
        ){
            this.info=info;
            this.handler=handler;
        }
    }

    private static final class ItemOnObjectKey {
        final int itemId;
        final int objectId;

        ItemOnObjectKey(
            int itemId,
            int objectId
        ){
            this.itemId=itemId;
            this.objectId=objectId;
        }

        String diagnosticKey(){
            return itemId+":"+objectId;
        }

        @Override public boolean equals(
            Object other
        ){
            if(this==other)return true;
            if(!(other instanceof ItemOnObjectKey))
                return false;
            ItemOnObjectKey key=
                (ItemOnObjectKey)other;
            return itemId==key.itemId&&
                objectId==key.objectId;
        }

        @Override public int hashCode(){
            return 31*itemId+objectId;
        }
    }

    private static final class ItemOnObjectBinding {
        final BindingInfo info;
        final ContentItemOnObjectHandler handler;

        ItemOnObjectBinding(
            BindingInfo info,
            ContentItemOnObjectHandler handler
        ){
            this.info=info;
            this.handler=handler;
        }
    }

    private static final class ItemOnPlayerKey {
        final int itemId;

        ItemOnPlayerKey(
            int itemId
        ){
            this.itemId=itemId;
        }

        String diagnosticKey(){
            return Integer.toString(itemId);
        }

        @Override public boolean equals(
            Object other
        ){
            if(this==other)return true;
            if(!(other instanceof ItemOnPlayerKey))
                return false;
            ItemOnPlayerKey key=
                (ItemOnPlayerKey)other;
            return itemId==key.itemId;
        }

        @Override public int hashCode(){
            return itemId;
        }
    }

    private static final class ItemOnPlayerBinding {
        final BindingInfo info;
        final ContentItemOnPlayerHandler handler;

        ItemOnPlayerBinding(
            BindingInfo info,
            ContentItemOnPlayerHandler handler
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
    private final LinkedHashMap<ItemOptionKey,ItemOptionBinding>
        itemOptions=new LinkedHashMap<>();
    private final LinkedHashMap<ItemOnNpcKey,ItemOnNpcBinding>
        itemOnNpcActions=new LinkedHashMap<>();
    private final LinkedHashMap<ItemOnGroundItemKey,ItemOnGroundItemBinding>
        itemOnGroundItemActions=new LinkedHashMap<>();
    private final LinkedHashMap<ItemOnItemKey,ItemOnItemBinding>
        itemOnItemActions=new LinkedHashMap<>();
    private final LinkedHashMap<ItemOnObjectKey,ItemOnObjectBinding>
        itemOnObjectActions=new LinkedHashMap<>();
    private final LinkedHashMap<ItemOnPlayerKey,ItemOnPlayerBinding>
        itemOnPlayerActions=new LinkedHashMap<>();
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
    private final ArrayList<ItemOptionRegistration>
        itemOptionRegistrations=new ArrayList<>();
    private final ArrayList<ItemOnNpcRegistration>
        itemOnNpcRegistrations=new ArrayList<>();
    private final ArrayList<ItemOnGroundItemRegistration>
        itemOnGroundItemRegistrations=new ArrayList<>();
    private final ArrayList<ItemOnItemRegistration>
        itemOnItemRegistrations=new ArrayList<>();
    private final ArrayList<ItemOnObjectRegistration>
        itemOnObjectRegistrations=new ArrayList<>();
    private final ArrayList<ItemOnPlayerRegistration>
        itemOnPlayerRegistrations=new ArrayList<>();
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

                ArrayList<ItemOptionRegistration>
                    nextItemOptionRegistrations=
                        new ArrayList<>(
                            itemOptionRegistrations
                        );

                ArrayList<ItemOnNpcRegistration>
                    nextItemOnNpcRegistrations=
                        new ArrayList<>(
                            itemOnNpcRegistrations
                        );

                ArrayList<ItemOnGroundItemRegistration>
                    nextItemOnGroundItemRegistrations=
                        new ArrayList<>(
                            itemOnGroundItemRegistrations
                        );

                ArrayList<ItemOnItemRegistration>
                    nextItemOnItemRegistrations=
                        new ArrayList<>(
                            itemOnItemRegistrations
                        );

                ArrayList<ItemOnObjectRegistration>
                    nextItemOnObjectRegistrations=
                        new ArrayList<>(
                            itemOnObjectRegistrations
                        );

                ArrayList<ItemOnPlayerRegistration>
                    nextItemOnPlayerRegistrations=
                        new ArrayList<>(
                            itemOnPlayerRegistrations
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

                for(ItemOptionRegistration registration:
                        registrar.pendingItemOptions)
                    if(registration.handle.pending())
                        addItemOptionRegistration(
                            nextItemOptionRegistrations,
                            registration
                        );

                for(ItemOnNpcRegistration registration:
                        registrar.pendingItemOnNpc)
                    if(registration.handle.pending())
                        addItemOnNpcRegistration(
                            nextItemOnNpcRegistrations,
                            registration
                        );

                for(ItemOnGroundItemRegistration registration:
                        registrar.pendingItemOnGroundItem)
                    if(registration.handle.pending())
                        addItemOnGroundItemRegistration(
                            nextItemOnGroundItemRegistrations,
                            registration
                        );

                for(ItemOnItemRegistration registration:
                        registrar.pendingItemOnItem)
                    if(registration.handle.pending())
                        addItemOnItemRegistration(
                            nextItemOnItemRegistrations,
                            registration
                        );

                for(ItemOnObjectRegistration registration:
                        registrar.pendingItemOnObject)
                    if(registration.handle.pending())
                        addItemOnObjectRegistration(
                            nextItemOnObjectRegistrations,
                            registration
                        );

                for(ItemOnPlayerRegistration registration:
                        registrar.pendingItemOnPlayer)
                    if(registration.handle.pending())
                        addItemOnPlayerRegistration(
                            nextItemOnPlayerRegistrations,
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

                LinkedHashMap<ItemOptionKey,ItemOptionBinding>
                    nextItemOptions=
                        buildItemOptionBindings(
                            nextItemOptionRegistrations
                        );

                LinkedHashMap<ItemOnNpcKey,ItemOnNpcBinding>
                    nextItemOnNpcActions=
                        buildItemOnNpcBindings(
                            nextItemOnNpcRegistrations
                        );

                LinkedHashMap<ItemOnGroundItemKey,ItemOnGroundItemBinding>
                    nextItemOnGroundItemActions=
                        buildItemOnGroundItemBindings(
                            nextItemOnGroundItemRegistrations
                        );

                LinkedHashMap<ItemOnItemKey,ItemOnItemBinding>
                    nextItemOnItemActions=
                        buildItemOnItemBindings(
                            nextItemOnItemRegistrations
                        );

                LinkedHashMap<ItemOnObjectKey,ItemOnObjectBinding>
                    nextItemOnObjectActions=
                        buildItemOnObjectBindings(
                            nextItemOnObjectRegistrations
                        );

                LinkedHashMap<ItemOnPlayerKey,ItemOnPlayerBinding>
                    nextItemOnPlayerActions=
                        buildItemOnPlayerBindings(
                            nextItemOnPlayerRegistrations
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

                itemOptionRegistrations.clear();
                itemOptionRegistrations.addAll(
                    nextItemOptionRegistrations
                );

                itemOnNpcRegistrations.clear();
                itemOnNpcRegistrations.addAll(
                    nextItemOnNpcRegistrations
                );

                itemOnGroundItemRegistrations.clear();
                itemOnGroundItemRegistrations.addAll(
                    nextItemOnGroundItemRegistrations
                );

                itemOnItemRegistrations.clear();
                itemOnItemRegistrations.addAll(
                    nextItemOnItemRegistrations
                );

                itemOnObjectRegistrations.clear();
                itemOnObjectRegistrations.addAll(
                    nextItemOnObjectRegistrations
                );

                itemOnPlayerRegistrations.clear();
                itemOnPlayerRegistrations.addAll(
                    nextItemOnPlayerRegistrations
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

                itemOptions.clear();
                itemOptions.putAll(
                    nextItemOptions
                );

                itemOnNpcActions.clear();
                itemOnNpcActions.putAll(
                    nextItemOnNpcActions
                );

                itemOnGroundItemActions.clear();
                itemOnGroundItemActions.putAll(
                    nextItemOnGroundItemActions
                );

                itemOnItemActions.clear();
                itemOnItemActions.putAll(
                    nextItemOnItemActions
                );

                itemOnObjectActions.clear();
                itemOnObjectActions.putAll(
                    nextItemOnObjectActions
                );

                itemOnPlayerActions.clear();
                itemOnPlayerActions.putAll(
                    nextItemOnPlayerActions
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

    ContentInteractionResult dispatchItemOption(
        int itemId,
        int option
    ){
        requireWorldThread();

        ItemOptionBinding binding;

        synchronized(this){
            binding=itemOptions.get(
                new ItemOptionKey(
                    itemId,
                    option
                )
            );
        }

        if(binding==null)
            return null;

        return binding.handler.handle(
            new ItemOptionContext(
                itemId,
                option
            )
        );
    }

    ContentInteractionResult dispatchItemOnNpc(
        int itemId,
        int npcDefinitionId,
        int worldX,
        int worldY
    ){
        requireWorldThread();

        ItemOnNpcBinding binding;

        synchronized(this){
            binding=itemOnNpcActions.get(
                new ItemOnNpcKey(
                    itemId,
                    npcDefinitionId
                )
            );
        }

        if(binding==null)
            return null;

        return binding.handler.handle(
            new ItemOnNpcContext(
                itemId,
                npcDefinitionId,
                worldX,
                worldY
            )
        );
    }

    ContentInteractionResult dispatchItemOnGroundItem(
        int itemId,
        int groundItemId,
        int worldX,
        int worldY
    ){
        requireWorldThread();

        ItemOnGroundItemBinding binding;

        synchronized(this){
            binding=itemOnGroundItemActions.get(
                new ItemOnGroundItemKey(
                    itemId,
                    groundItemId
                )
            );
        }

        if(binding==null)
            return null;

        return binding.handler.handle(
            new ItemOnGroundItemContext(
                itemId,
                groundItemId,
                worldX,
                worldY
            )
        );
    }

    ContentInteractionResult dispatchItemOnItem(
        int selectedItemId,
        int targetItemId
    ){
        requireWorldThread();

        ItemOnItemBinding binding;

        synchronized(this){
            binding=itemOnItemActions.get(
                new ItemOnItemKey(
                    selectedItemId,
                    targetItemId
                )
            );
        }

        if(binding==null)
            return null;

        return binding.handler.handle(
            new ItemOnItemContext(
                selectedItemId,
                targetItemId
            )
        );
    }

    ContentInteractionResult dispatchItemOnObject(
        int itemId,
        int objectId,
        int worldX,
        int worldY
    ){
        requireWorldThread();

        ItemOnObjectBinding binding;

        synchronized(this){
            binding=itemOnObjectActions.get(
                new ItemOnObjectKey(
                    itemId,
                    objectId
                )
            );
        }

        if(binding==null)
            return null;

        return binding.handler.handle(
            new ItemOnObjectContext(
                itemId,
                objectId,
                worldX,
                worldY
            )
        );
    }

    ContentInteractionResult dispatchItemOnPlayer(
        int itemId,
        WorldPlayer target
    ){
        requireWorldThread();
        Objects.requireNonNull(
            target,
            "target"
        );

        ItemOnPlayerBinding binding;

        synchronized(this){
            binding=itemOnPlayerActions.get(
                new ItemOnPlayerKey(
                    itemId
                )
            );
        }

        if(binding==null)
            return null;

        return binding.handler.handle(
            new ItemOnPlayerContext(
                itemId,
                ContentRuntimeAdapters.player(
                    target
                )
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

    synchronized BindingInfo itemOptionBinding(
        int itemId,
        int option
    ){
        ItemOptionBinding binding=
            itemOptions.get(
                new ItemOptionKey(
                    itemId,
                    option
                )
            );
        return binding==null
            ?null
            :binding.info;
    }

    synchronized BindingInfo itemOnNpcBinding(
        int itemId,
        int npcDefinitionId
    ){
        ItemOnNpcBinding binding=
            itemOnNpcActions.get(
                new ItemOnNpcKey(
                    itemId,
                    npcDefinitionId
                )
            );
        return binding==null
            ?null
            :binding.info;
    }

    synchronized BindingInfo itemOnGroundItemBinding(
        int itemId,
        int groundItemId
    ){
        ItemOnGroundItemBinding binding=
            itemOnGroundItemActions.get(
                new ItemOnGroundItemKey(
                    itemId,
                    groundItemId
                )
            );
        return binding==null
            ?null
            :binding.info;
    }

    synchronized BindingInfo itemOnItemBinding(
        int selectedItemId,
        int targetItemId
    ){
        ItemOnItemBinding binding=
            itemOnItemActions.get(
                new ItemOnItemKey(
                    selectedItemId,
                    targetItemId
                )
            );
        return binding==null
            ?null
            :binding.info;
    }

    synchronized BindingInfo itemOnObjectBinding(
        int itemId,
        int objectId
    ){
        ItemOnObjectBinding binding=
            itemOnObjectActions.get(
                new ItemOnObjectKey(
                    itemId,
                    objectId
                )
            );
        return binding==null
            ?null
            :binding.info;
    }

    synchronized BindingInfo itemOnPlayerBinding(
        int itemId
    ){
        ItemOnPlayerBinding binding=
            itemOnPlayerActions.get(
                new ItemOnPlayerKey(
                    itemId
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

        for(ItemOptionBinding binding:
                itemOptions.values())
            result.add(binding.info);

        for(ItemOnNpcBinding binding:
                itemOnNpcActions.values())
            result.add(binding.info);

        for(ItemOnGroundItemBinding binding:
                itemOnGroundItemActions.values())
            result.add(binding.info);

        for(ItemOnItemBinding binding:
                itemOnItemActions.values())
            result.add(binding.info);

        for(ItemOnObjectBinding binding:
                itemOnObjectActions.values())
            result.add(binding.info);

        for(ItemOnPlayerBinding binding:
                itemOnPlayerActions.values())
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
            ",itemOptions="+
                itemOptions.size()+
            ",itemOnNpc="+
                itemOnNpcActions.size()+
            ",itemOnGroundItem="+
                itemOnGroundItemActions.size()+
            ",itemOnItem="+
                itemOnItemActions.size()+
            ",itemOnObject="+
                itemOnObjectActions.size()+
            ",itemOnPlayer="+
                itemOnPlayerActions.size()+
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

    private static void addItemOptionRegistration(
        List<ItemOptionRegistration> target,
        ItemOptionRegistration incoming
    ){
        for(ItemOptionRegistration existing:
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

    private static void addItemOnNpcRegistration(
        List<ItemOnNpcRegistration> target,
        ItemOnNpcRegistration incoming
    ){
        for(ItemOnNpcRegistration existing:
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

    private static void addItemOnGroundItemRegistration(
        List<ItemOnGroundItemRegistration> target,
        ItemOnGroundItemRegistration incoming
    ){
        for(ItemOnGroundItemRegistration existing:
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

    private static void addItemOnItemRegistration(
        List<ItemOnItemRegistration> target,
        ItemOnItemRegistration incoming
    ){
        for(ItemOnItemRegistration existing:
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

    private static void addItemOnObjectRegistration(
        List<ItemOnObjectRegistration> target,
        ItemOnObjectRegistration incoming
    ){
        for(ItemOnObjectRegistration existing:
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

    private static void addItemOnPlayerRegistration(
        List<ItemOnPlayerRegistration> target,
        ItemOnPlayerRegistration incoming
    ){
        for(ItemOnPlayerRegistration existing:
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

    private static LinkedHashMap<ItemOptionKey,ItemOptionBinding>
        buildItemOptionBindings(
            List<ItemOptionRegistration> registrations
        ){
        LinkedHashMap<ItemOptionKey,ItemOptionBinding>
            result=new LinkedHashMap<>();

        for(ItemOptionRegistration registration:
                registrations)
            applyItemOption(
                result,
                registration
            );

        return result;
    }

    private static LinkedHashMap<ItemOnNpcKey,ItemOnNpcBinding>
        buildItemOnNpcBindings(
            List<ItemOnNpcRegistration> registrations
        ){
        LinkedHashMap<ItemOnNpcKey,ItemOnNpcBinding>
            result=new LinkedHashMap<>();

        for(ItemOnNpcRegistration registration:
                registrations)
            applyItemOnNpc(
                result,
                registration
            );

        return result;
    }

    private static LinkedHashMap<ItemOnGroundItemKey,ItemOnGroundItemBinding>
        buildItemOnGroundItemBindings(
            List<ItemOnGroundItemRegistration> registrations
        ){
        LinkedHashMap<ItemOnGroundItemKey,ItemOnGroundItemBinding>
            result=new LinkedHashMap<>();

        for(ItemOnGroundItemRegistration registration:
                registrations)
            applyItemOnGroundItem(
                result,
                registration
            );

        return result;
    }

    private static LinkedHashMap<ItemOnItemKey,ItemOnItemBinding>
        buildItemOnItemBindings(
            List<ItemOnItemRegistration> registrations
        ){
        LinkedHashMap<ItemOnItemKey,ItemOnItemBinding>
            result=new LinkedHashMap<>();

        for(ItemOnItemRegistration registration:
                registrations)
            applyItemOnItem(
                result,
                registration
            );

        return result;
    }

    private static LinkedHashMap<ItemOnObjectKey,ItemOnObjectBinding>
        buildItemOnObjectBindings(
            List<ItemOnObjectRegistration> registrations
        ){
        LinkedHashMap<ItemOnObjectKey,ItemOnObjectBinding>
            result=new LinkedHashMap<>();

        for(ItemOnObjectRegistration registration:
                registrations)
            applyItemOnObject(
                result,
                registration
            );

        return result;
    }

    private static LinkedHashMap<ItemOnPlayerKey,ItemOnPlayerBinding>
        buildItemOnPlayerBindings(
            List<ItemOnPlayerRegistration> registrations
        ){
        LinkedHashMap<ItemOnPlayerKey,ItemOnPlayerBinding>
            result=new LinkedHashMap<>();

        for(ItemOnPlayerRegistration registration:
                registrations)
            applyItemOnPlayer(
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
            itemOptionRegistrations.removeIf(
                registration->
                    registration.handle==handle
            )||removed;

        removed=
            itemOnNpcRegistrations.removeIf(
                registration->
                    registration.handle==handle
            )||removed;

        removed=
            itemOnGroundItemRegistrations.removeIf(
                registration->
                    registration.handle==handle
            )||removed;

        removed=
            itemOnItemRegistrations.removeIf(
                registration->
                    registration.handle==handle
            )||removed;

        removed=
            itemOnObjectRegistrations.removeIf(
                registration->
                    registration.handle==handle
            )||removed;

        removed=
            itemOnPlayerRegistrations.removeIf(
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

        LinkedHashMap<ItemOptionKey,ItemOptionBinding>
            nextItemOptions=
                buildItemOptionBindings(
                    itemOptionRegistrations
                );

        LinkedHashMap<ItemOnNpcKey,ItemOnNpcBinding>
            nextItemOnNpcActions=
                buildItemOnNpcBindings(
                    itemOnNpcRegistrations
                );

        LinkedHashMap<ItemOnGroundItemKey,ItemOnGroundItemBinding>
            nextItemOnGroundItemActions=
                buildItemOnGroundItemBindings(
                    itemOnGroundItemRegistrations
                );

        LinkedHashMap<ItemOnItemKey,ItemOnItemBinding>
            nextItemOnItemActions=
                buildItemOnItemBindings(
                    itemOnItemRegistrations
                );

        LinkedHashMap<ItemOnObjectKey,ItemOnObjectBinding>
            nextItemOnObjectActions=
                buildItemOnObjectBindings(
                    itemOnObjectRegistrations
                );

        LinkedHashMap<ItemOnPlayerKey,ItemOnPlayerBinding>
            nextItemOnPlayerActions=
                buildItemOnPlayerBindings(
                    itemOnPlayerRegistrations
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

        itemOptions.clear();
        itemOptions.putAll(
            nextItemOptions
        );

        itemOnNpcActions.clear();
        itemOnNpcActions.putAll(
            nextItemOnNpcActions
        );

        itemOnGroundItemActions.clear();
        itemOnGroundItemActions.putAll(
            nextItemOnGroundItemActions
        );

        itemOnItemActions.clear();
        itemOnItemActions.putAll(
            nextItemOnItemActions
        );

        itemOnObjectActions.clear();
        itemOnObjectActions.putAll(
            nextItemOnObjectActions
        );

        itemOnPlayerActions.clear();
        itemOnPlayerActions.putAll(
            nextItemOnPlayerActions
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

    private static void applyItemOption(
        Map<ItemOptionKey,ItemOptionBinding> target,
        ItemOptionRegistration registration
    ){
        ItemOptionBinding existing=
            target.get(
                registration.key
            );

        if(existing==null){
            target.put(
                registration.key,
                new ItemOptionBinding(
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
                new ItemOptionBinding(
                    registration.info,
                    registration.handler
                )
            );
    }

    private static void applyItemOnNpc(
        Map<ItemOnNpcKey,ItemOnNpcBinding> target,
        ItemOnNpcRegistration registration
    ){
        ItemOnNpcBinding existing=
            target.get(
                registration.key
            );

        if(existing==null){
            target.put(
                registration.key,
                new ItemOnNpcBinding(
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
                new ItemOnNpcBinding(
                    registration.info,
                    registration.handler
                )
            );
    }

    private static void applyItemOnGroundItem(
        Map<ItemOnGroundItemKey,ItemOnGroundItemBinding> target,
        ItemOnGroundItemRegistration registration
    ){
        ItemOnGroundItemBinding existing=
            target.get(
                registration.key
            );

        if(existing==null){
            target.put(
                registration.key,
                new ItemOnGroundItemBinding(
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
                new ItemOnGroundItemBinding(
                    registration.info,
                    registration.handler
                )
            );
    }

    private static void applyItemOnItem(
        Map<ItemOnItemKey,ItemOnItemBinding> target,
        ItemOnItemRegistration registration
    ){
        ItemOnItemBinding existing=
            target.get(
                registration.key
            );

        if(existing==null){
            target.put(
                registration.key,
                new ItemOnItemBinding(
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
                new ItemOnItemBinding(
                    registration.info,
                    registration.handler
                )
            );
    }

    private static void applyItemOnObject(
        Map<ItemOnObjectKey,ItemOnObjectBinding> target,
        ItemOnObjectRegistration registration
    ){
        ItemOnObjectBinding existing=
            target.get(
                registration.key
            );

        if(existing==null){
            target.put(
                registration.key,
                new ItemOnObjectBinding(
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
                new ItemOnObjectBinding(
                    registration.info,
                    registration.handler
                )
            );
    }

    private static void applyItemOnPlayer(
        Map<ItemOnPlayerKey,ItemOnPlayerBinding> target,
        ItemOnPlayerRegistration registration
    ){
        ItemOnPlayerBinding existing=
            target.get(
                registration.key
            );

        if(existing==null){
            target.put(
                registration.key,
                new ItemOnPlayerBinding(
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
                new ItemOnPlayerBinding(
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

    private static final class ItemOptionRegistration {
        final ItemOptionKey key;
        final BindingInfo info;
        final ContentItemOptionHandler handler;
        final RegistrationHandle handle;

        ItemOptionRegistration(
            ItemOptionKey key,
            BindingInfo info,
            ContentItemOptionHandler handler,
            RegistrationHandle handle
        ){
            this.key=key;
            this.info=info;
            this.handler=handler;
            this.handle=handle;
        }
    }

    private static final class ItemOnNpcRegistration {
        final ItemOnNpcKey key;
        final BindingInfo info;
        final ContentItemOnNpcHandler handler;
        final RegistrationHandle handle;

        ItemOnNpcRegistration(
            ItemOnNpcKey key,
            BindingInfo info,
            ContentItemOnNpcHandler handler,
            RegistrationHandle handle
        ){
            this.key=key;
            this.info=info;
            this.handler=handler;
            this.handle=handle;
        }
    }

    private static final class ItemOnGroundItemRegistration {
        final ItemOnGroundItemKey key;
        final BindingInfo info;
        final ContentItemOnGroundItemHandler handler;
        final RegistrationHandle handle;

        ItemOnGroundItemRegistration(
            ItemOnGroundItemKey key,
            BindingInfo info,
            ContentItemOnGroundItemHandler handler,
            RegistrationHandle handle
        ){
            this.key=key;
            this.info=info;
            this.handler=handler;
            this.handle=handle;
        }
    }

    private static final class ItemOnItemRegistration {
        final ItemOnItemKey key;
        final BindingInfo info;
        final ContentItemOnItemHandler handler;
        final RegistrationHandle handle;

        ItemOnItemRegistration(
            ItemOnItemKey key,
            BindingInfo info,
            ContentItemOnItemHandler handler,
            RegistrationHandle handle
        ){
            this.key=key;
            this.info=info;
            this.handler=handler;
            this.handle=handle;
        }
    }

    private static final class ItemOnObjectRegistration {
        final ItemOnObjectKey key;
        final BindingInfo info;
        final ContentItemOnObjectHandler handler;
        final RegistrationHandle handle;

        ItemOnObjectRegistration(
            ItemOnObjectKey key,
            BindingInfo info,
            ContentItemOnObjectHandler handler,
            RegistrationHandle handle
        ){
            this.key=key;
            this.info=info;
            this.handler=handler;
            this.handle=handle;
        }
    }

    private static final class ItemOnPlayerRegistration {
        final ItemOnPlayerKey key;
        final BindingInfo info;
        final ContentItemOnPlayerHandler handler;
        final RegistrationHandle handle;

        ItemOnPlayerRegistration(
            ItemOnPlayerKey key,
            BindingInfo info,
            ContentItemOnPlayerHandler handler,
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
        private final ArrayList<ItemOptionRegistration>
            pendingItemOptions=
                new ArrayList<>();
        private final ArrayList<ItemOnNpcRegistration>
            pendingItemOnNpc=
                new ArrayList<>();
        private final ArrayList<ItemOnGroundItemRegistration>
            pendingItemOnGroundItem=
                new ArrayList<>();
        private final ArrayList<ItemOnItemRegistration>
            pendingItemOnItem=
                new ArrayList<>();
        private final ArrayList<ItemOnObjectRegistration>
            pendingItemOnObject=
                new ArrayList<>();
        private final ArrayList<ItemOnPlayerRegistration>
            pendingItemOnPlayer=
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

        @Override public ContentRegistration itemOption(
            int itemId,
            int option,
            int priority,
            ContentItemOptionHandler handler
        ){
            if(itemId<0)
                throw new IllegalArgumentException(
                    "itemId"
                );

            if(option<1)
                throw new IllegalArgumentException(
                    "item option"
                );

            Objects.requireNonNull(
                handler,
                "handler"
            );

            ItemOptionKey key=
                new ItemOptionKey(
                    itemId,
                    option
                );

            RegistrationHandle handle=
                new RegistrationHandle();

            pendingItemOptions.add(
                new ItemOptionRegistration(
                    key,
                    new BindingInfo(
                        "ITEM_OPTION",
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

        @Override public ContentRegistration itemOnNpc(
            int itemId,
            int npcDefinitionId,
            int priority,
            ContentItemOnNpcHandler handler
        ){
            if(itemId<0)
                throw new IllegalArgumentException(
                    "itemId"
                );

            if(npcDefinitionId<0)
                throw new IllegalArgumentException(
                    "npcDefinitionId"
                );

            Objects.requireNonNull(
                handler,
                "handler"
            );

            ItemOnNpcKey key=
                new ItemOnNpcKey(
                    itemId,
                    npcDefinitionId
                );

            RegistrationHandle handle=
                new RegistrationHandle();

            pendingItemOnNpc.add(
                new ItemOnNpcRegistration(
                    key,
                    new BindingInfo(
                        "ITEM_ON_NPC",
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

        @Override public ContentRegistration itemOnGroundItem(
            int itemId,
            int groundItemId,
            int priority,
            ContentItemOnGroundItemHandler handler
        ){
            if(itemId<0)
                throw new IllegalArgumentException(
                    "itemId"
                );

            if(groundItemId<0)
                throw new IllegalArgumentException(
                    "groundItemId"
                );

            Objects.requireNonNull(
                handler,
                "handler"
            );

            ItemOnGroundItemKey key=
                new ItemOnGroundItemKey(
                    itemId,
                    groundItemId
                );

            RegistrationHandle handle=
                new RegistrationHandle();

            pendingItemOnGroundItem.add(
                new ItemOnGroundItemRegistration(
                    key,
                    new BindingInfo(
                        "ITEM_ON_GROUND_ITEM",
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

        @Override public ContentRegistration itemOnItem(
            int selectedItemId,
            int targetItemId,
            int priority,
            ContentItemOnItemHandler handler
        ){
            if(selectedItemId<0)
                throw new IllegalArgumentException(
                    "selectedItemId"
                );

            if(targetItemId<0)
                throw new IllegalArgumentException(
                    "targetItemId"
                );

            Objects.requireNonNull(
                handler,
                "handler"
            );

            ItemOnItemKey key=
                new ItemOnItemKey(
                    selectedItemId,
                    targetItemId
                );

            RegistrationHandle handle=
                new RegistrationHandle();

            pendingItemOnItem.add(
                new ItemOnItemRegistration(
                    key,
                    new BindingInfo(
                        "ITEM_ON_ITEM",
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

        @Override public ContentRegistration itemOnObject(
            int itemId,
            int objectId,
            int priority,
            ContentItemOnObjectHandler handler
        ){
            if(itemId<0)
                throw new IllegalArgumentException(
                    "itemId"
                );

            if(objectId<0)
                throw new IllegalArgumentException(
                    "objectId"
                );

            Objects.requireNonNull(
                handler,
                "handler"
            );

            ItemOnObjectKey key=
                new ItemOnObjectKey(
                    itemId,
                    objectId
                );

            RegistrationHandle handle=
                new RegistrationHandle();

            pendingItemOnObject.add(
                new ItemOnObjectRegistration(
                    key,
                    new BindingInfo(
                        "ITEM_ON_OBJECT",
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

        @Override public ContentRegistration itemOnPlayer(
            int itemId,
            int priority,
            ContentItemOnPlayerHandler handler
        ){
            if(itemId<0)
                throw new IllegalArgumentException(
                    "itemId"
                );

            Objects.requireNonNull(
                handler,
                "handler"
            );

            ItemOnPlayerKey key=
                new ItemOnPlayerKey(
                    itemId
                );

            RegistrationHandle handle=
                new RegistrationHandle();

            pendingItemOnPlayer.add(
                new ItemOnPlayerRegistration(
                    key,
                    new BindingInfo(
                        "ITEM_ON_PLAYER",
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

            for(ItemOptionRegistration registration:
                    pendingItemOptions)
                registration.handle
                    .activatePending();

            for(ItemOnNpcRegistration registration:
                    pendingItemOnNpc)
                registration.handle
                    .activatePending();

            for(ItemOnGroundItemRegistration registration:
                    pendingItemOnGroundItem)
                registration.handle
                    .activatePending();

            for(ItemOnItemRegistration registration:
                    pendingItemOnItem)
                registration.handle
                    .activatePending();

            for(ItemOnObjectRegistration registration:
                    pendingItemOnObject)
                registration.handle
                    .activatePending();

            for(ItemOnPlayerRegistration registration:
                    pendingItemOnPlayer)
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

            for(ItemOptionRegistration registration:
                    pendingItemOptions)
                registration.handle
                    .invalidatePending();

            for(ItemOnNpcRegistration registration:
                    pendingItemOnNpc)
                registration.handle
                    .invalidatePending();

            for(ItemOnGroundItemRegistration registration:
                    pendingItemOnGroundItem)
                registration.handle
                    .invalidatePending();

            for(ItemOnItemRegistration registration:
                    pendingItemOnItem)
                registration.handle
                    .invalidatePending();

            for(ItemOnObjectRegistration registration:
                    pendingItemOnObject)
                registration.handle
                    .invalidatePending();

            for(ItemOnPlayerRegistration registration:
                    pendingItemOnPlayer)
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
    private static final class ItemOptionContext
        implements ContentItemOptionContext {

        private final int itemId;
        private final int option;

        ItemOptionContext(
            int itemId,
            int option
        ){
            this.itemId=itemId;
            this.option=option;
        }

        @Override public int itemId(){
            return itemId;
        }

        @Override public int option(){
            return option;
        }
    }

    private static final class ItemOnNpcContext
        implements ContentItemOnNpcContext {

        private final int itemId;
        private final int npcDefinitionId;
        private final int worldX;
        private final int worldY;

        ItemOnNpcContext(
            int itemId,
            int npcDefinitionId,
            int worldX,
            int worldY
        ){
            this.itemId=itemId;
            this.npcDefinitionId=npcDefinitionId;
            this.worldX=worldX;
            this.worldY=worldY;
        }

        @Override public int itemId(){
            return itemId;
        }

        @Override public int npcDefinitionId(){
            return npcDefinitionId;
        }

        @Override public int worldX(){
            return worldX;
        }

        @Override public int worldY(){
            return worldY;
        }
    }

    private static final class ItemOnGroundItemContext
        implements ContentItemOnGroundItemContext {

        private final int itemId;
        private final int groundItemId;
        private final int worldX;
        private final int worldY;

        ItemOnGroundItemContext(
            int itemId,
            int groundItemId,
            int worldX,
            int worldY
        ){
            this.itemId=itemId;
            this.groundItemId=groundItemId;
            this.worldX=worldX;
            this.worldY=worldY;
        }

        @Override public int itemId(){
            return itemId;
        }

        @Override public int groundItemId(){
            return groundItemId;
        }

        @Override public int worldX(){
            return worldX;
        }

        @Override public int worldY(){
            return worldY;
        }
    }

    private static final class ItemOnItemContext
        implements ContentItemOnItemContext {

        private final int selectedItemId;
        private final int targetItemId;

        ItemOnItemContext(
            int selectedItemId,
            int targetItemId
        ){
            this.selectedItemId=selectedItemId;
            this.targetItemId=targetItemId;
        }

        @Override public int selectedItemId(){
            return selectedItemId;
        }

        @Override public int targetItemId(){
            return targetItemId;
        }
    }

    private static final class ItemOnObjectContext
        implements ContentItemOnObjectContext {

        private final int itemId;
        private final int objectId;
        private final int worldX;
        private final int worldY;

        ItemOnObjectContext(
            int itemId,
            int objectId,
            int worldX,
            int worldY
        ){
            this.itemId=itemId;
            this.objectId=objectId;
            this.worldX=worldX;
            this.worldY=worldY;
        }

        @Override public int itemId(){
            return itemId;
        }

        @Override public int objectId(){
            return objectId;
        }

        @Override public int worldX(){
            return worldX;
        }

        @Override public int worldY(){
            return worldY;
        }
    }

    private static final class ItemOnPlayerContext
        implements ContentItemOnPlayerContext {

        private final int itemId;
        private final ContentPlayer target;

        ItemOnPlayerContext(
            int itemId,
            ContentPlayer target
        ){
            this.itemId=itemId;
            this.target=Objects.requireNonNull(
                target,
                "target"
            );
        }

        @Override public int itemId(){
            return itemId;
        }

        @Override public ContentPlayer target(){
            return target;
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
