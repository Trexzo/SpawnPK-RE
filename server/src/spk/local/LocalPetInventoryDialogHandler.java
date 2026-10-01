package spk.local;

import java.io.IOException;
import java.util.Arrays;

/**
 * Pet inventory/dialog coordinator.
 *
 * Owns only the already-established mini-pet, accessory and colour-dialog
 * interaction state. Account persistence and LocalLab dialog-key file updates
 * remain session concerns.
 */
final class LocalPetInventoryDialogHandler {
    enum KeyAction { NONE, PUBLISH_2482_2485, CLEAR_AFTER_LOG }

    static final class Result {
        final String logText;
        final String saveReason;
        final KeyAction keyAction;

        Result(String logText,String saveReason,KeyAction keyAction){
            this.logText=logText;
            this.saveReason=saveReason;
            this.keyAction=keyAction==null?KeyAction.NONE:keyAction;
        }

        static Result log(String text){
            return new Result(text,null,KeyAction.NONE);
        }

        static Result open(String text){
            return new Result(text,null,KeyAction.PUBLISH_2482_2485);
        }

        static Result close(String text){
            return new Result(text,null,KeyAction.CLEAR_AFTER_LOG);
        }

        static Result saveClose(String text,String reason){
            return new Result(text,reason,KeyAction.CLEAR_AFTER_LOG);
        }

        static Result save(String text,String reason){
            return new Result(text,reason,KeyAction.NONE);
        }
    }

    static final class CloseState {
        final boolean petColorWasOpen;
        final boolean miniConfigWasOpen;
        final boolean petAccessoryWasOpen;

        CloseState(
            boolean petColorWasOpen,
            boolean miniConfigWasOpen,
            boolean petAccessoryWasOpen
        ){
            this.petColorWasOpen=petColorWasOpen;
            this.miniConfigWasOpen=miniConfigWasOpen;
            this.petAccessoryWasOpen=petAccessoryWasOpen;
        }

        boolean hadAny(){
            return petColorWasOpen||miniConfigWasOpen||petAccessoryWasOpen;
        }
    }

    private final BankState bank;
    private final MiniPetService miniPets;
    private final PetState petState;
    private final NpcRegistry npcs;
    private final MovementState movement;
    private final PetAccessoryState petAccessoryState;

    private int pendingPetColorSlot=-1;
    private int[] pendingPetColorItems;
    private String pendingPetColorFamily;
    private int pendingMiniConfigureSlot=-1;
    private int pendingMiniConfigureItem=-1;
    private int pendingPetAccessorySlot=-1;
    private int pendingPetAccessoryItem=-1;

    LocalPetInventoryDialogHandler(
        BankState bank,
        MiniPetService miniPets,
        PetState petState,
        NpcRegistry npcs,
        MovementState movement,
        PetAccessoryState petAccessoryState
    ){
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.miniPets=java.util.Objects.requireNonNull(miniPets,"miniPets");
        this.petState=java.util.Objects.requireNonNull(petState,"petState");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.petAccessoryState=java.util.Objects.requireNonNull(
            petAccessoryState,"petAccessoryState");
    }

    Result handleItemAction(
        ItemContainerAction action,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(action==null)return null;

        if(action.opcode==122 &&
           action.widgetId==BankState.NORMAL_INVENTORY_CONTAINER &&
           MiniPetDefinitionRepository.isMiniPetItem(action.itemId)){
            BankState.Stack stack=bank.inventoryAt(action.slot);
            if(stack==null||stack.itemId!=action.itemId||stack.qty<=0){
                return Result.log(
                    "V5127_MINIPET_CONFIGURE "+action+
                    " result=REJECTED_INVENTORY_MISMATCH");
            }

            String semantic=
                ItemActionResolver.inventoryOption1Semantic(action.itemId);
            if(!"Configure".equalsIgnoreCase(semantic)){
                return Result.log(
                    "V5127_MINIPET_CONFIGURE "+action+
                    " result=REJECTED_ACTION_SEMANTIC semantic="+semantic);
            }

            openMiniConfigureDialog(
                action.slot,action.itemId,serverPackets);

            return Result.open(
                "V5127_MINIPET_CONFIGURE "+action+
                " result=DIALOG_OPEN authority=EXACT_ACTION_RECONSTRUCTED_SERVER_WORDING");
        }

        if(action.opcode==122 &&
           action.widgetId==BankState.NORMAL_INVENTORY_CONTAINER &&
           PetAccessoryAuthority.isAccessory(action.itemId)){
            BankState.Stack stack=bank.inventoryAt(action.slot);
            if(stack==null||stack.itemId!=action.itemId||stack.qty<=0){
                return Result.log(
                    "V5130_PET_ACCESSORY "+action+
                    " result=REJECTED_INVENTORY_MISMATCH");
            }

            String semantic=
                ItemActionResolver.inventoryOption1Semantic(action.itemId);
            if(!"Read".equalsIgnoreCase(semantic)){
                return Result.log(
                    "V5130_PET_ACCESSORY "+action+
                    " result=REJECTED_ACTION_SEMANTIC semantic="+semantic);
            }

            openPetAccessoryDialog(
                action.slot,action.itemId,serverPackets);

            return Result.open(
                "V5130_PET_ACCESSORY_READ item="+action.itemId+
                " name="+PetAccessoryAuthority.name(action.itemId)+
                " result=DIALOG_OPEN wording=RECONSTRUCTED_SERVER_RESPONSE"+
                " officialSemantics=TOGGLE_INFINITE_USE currentActive="+
                (petAccessoryState.activeItem()==0
                    ?"NONE"
                    :petAccessoryState.activeItem()));
        }

        if(action.opcode==75 &&
           action.widgetId==BankState.NORMAL_INVENTORY_CONTAINER &&
           action.itemId==28807){
            String result=bank.splitInventoryOne(
                action.slot,28807,3241,28824,serverPackets);

            return Result.save(
                "V57_DOPPELGANGER_REMOVE_DYE "+action+
                " result="+result+
                " baseItem=3241 returnedDye=28824 decoderAligned=true",
                result.startsWith("INVENTORY_SPLIT_OK")
                    ?"DOPPELGANGER_REMOVE_DYE"
                    :null
            );
        }

        if(action.opcode==75 &&
           action.widgetId==BankState.NORMAL_INVENTORY_CONTAINER){
            int[] family=petColorFamily(action.itemId);
            if(family!=null){
                String name=petColorFamilyName(action.itemId);
                openPetColorDialog(
                    action.slot,family,name,serverPackets);

                return Result.open(
                    "V59_PET_COLOR_DIALOG_OPEN "+action+
                    " family="+name+
                    " choices="+Arrays.toString(family)+
                    " chatboxRoot=2480 transport=S2C164");
            }
        }

        return null;
    }

    Result handleWidget(int widget,ServerPacketWriter serverPackets)
        throws IOException{
        if(pendingMiniConfigureItem>=0 &&
           (widget==2482||widget==2483||widget==2484||
            widget==2485||widget==54195)){
            int item=pendingMiniConfigureItem;

            if(widget==54195||widget==2484||widget==2485){
                serverPackets.fixed(219,new byte[0]);
                clearMiniConfigureDialog();

                return Result.close(
                    "V5127_MINIPET_CONFIGURE_DIALOG item="+item+
                    " action=CANCEL widget="+widget);
            }

            if(widget==2482){
                MiniPetService.PreparedConfigure prepared=
                    miniPets.prepareConfigure(
                        item,
                        petState,
                        npcs,
                        movement
                    );

                serverPackets.beginBatch();
                boolean ended=false;
                String actorResult;

                try{
                    actorResult=
                        miniPets.publishPreparedConfigure(
                            prepared,
                            npcs,
                            movement,
                            serverPackets
                        );
                    serverPackets.fixed(
                        219,
                        new byte[0]
                    );
                    serverPackets.endBatch();
                    ended=true;
                }catch(IOException failure){
                    if(!ended)
                        try{serverPackets.endBatch();}catch(Throwable ignored){}
                    throw failure;
                }catch(RuntimeException failure){
                    if(!ended)
                        try{serverPackets.endBatch();}catch(Throwable ignored){}
                    throw failure;
                }catch(Error failure){
                    if(!ended)
                        try{serverPackets.endBatch();}catch(Throwable ignored){}
                    throw failure;
                }

                String result=
                    miniPets.commitPreparedConfigure(
                        prepared,
                        petState,
                        npcs,
                        serverPackets,
                        actorResult
                    );
                clearMiniConfigureDialog();

                return Result.saveClose(
                    "V5127_MINIPET_CONFIGURE_DIALOG item="+item+
                    " action=ACTIVATE result="+result+
                    " actorRequiresMainPet=true",
                    "MINIPET_CONFIGURE");
            }

            if(widget==2483){
                MiniPetService.PreparedOff prepared=
                    miniPets.prepareOff(
                        petState,
                        npcs
                    );

                serverPackets.beginBatch();
                boolean ended=false;
                String actorResult;

                try{
                    actorResult=
                        miniPets.publishPreparedOff(
                            prepared,
                            npcs,
                            serverPackets
                        );
                    serverPackets.fixed(
                        219,
                        new byte[0]
                    );
                    serverPackets.endBatch();
                    ended=true;
                }catch(IOException failure){
                    if(!ended)
                        try{serverPackets.endBatch();}catch(Throwable ignored){}
                    throw failure;
                }catch(RuntimeException failure){
                    if(!ended)
                        try{serverPackets.endBatch();}catch(Throwable ignored){}
                    throw failure;
                }catch(Error failure){
                    if(!ended)
                        try{serverPackets.endBatch();}catch(Throwable ignored){}
                    throw failure;
                }

                String result=
                    miniPets.commitPreparedOff(
                        prepared,
                        petState,
                        npcs,
                        actorResult
                    );
                clearMiniConfigureDialog();

                return Result.saveClose(
                    "V5127_MINIPET_CONFIGURE_DIALOG item="+item+
                    " action=DISABLE result="+result,
                    "MINIPET_DISABLE");
            }
        }

        if(pendingPetAccessoryItem>=0 &&
           (widget==54195||(widget>=2482&&widget<=2485))){
            int item=pendingPetAccessoryItem;

            if(widget==54195||widget==2485){
                serverPackets.fixed(219,new byte[0]);
                clearPetAccessoryDialog();

                return Result.close(
                    "V5130_PET_ACCESSORY_DIALOG item="+item+
                    " action=CLOSE widget="+widget);
            }

            if(widget==2482){
                BankState.Stack stack=
                    bank.inventoryAt(pendingPetAccessorySlot);

                if(stack==null||stack.itemId!=item||stack.qty<=0){
                    serverPackets.fixed(219,new byte[0]);
                    clearPetAccessoryDialog();

                    return Result.close(
                        "V5130_PET_ACCESSORY_DIALOG item="+item+
                        " action=ACTIVATE result=REJECTED_ITEM_MOVED");
                }

                Integer selector=
                    PetAccessoryAuthority.selector(item);

                serverPackets.beginBatch();
                boolean ended=false;
                String visual;

                try{
                    visual=
                        npcs.publishPetParticleSelector(
                            selector,
                            movement,
                            serverPackets
                        );
                    serverPackets.fixed(
                        219,
                        new byte[0]
                    );
                    serverPackets.endBatch();
                    ended=true;
                }catch(IOException failure){
                    if(!ended)
                        try{
                            serverPackets.endBatch();
                        }catch(Throwable ignored){}
                    throw failure;
                }catch(RuntimeException failure){
                    if(!ended)
                        try{
                            serverPackets.endBatch();
                        }catch(Throwable ignored){}
                    throw failure;
                }catch(Error failure){
                    if(!ended)
                        try{
                            serverPackets.endBatch();
                        }catch(Throwable ignored){}
                    throw failure;
                }

                petAccessoryState.setActiveItem(
                    item
                );
                npcs.commitPetParticleSelector(
                    selector
                );
                clearPetAccessoryDialog();

                return Result.saveClose(
                    "V5130_PET_ACCESSORY_DIALOG item="+item+
                    " action=ACTIVATE selector="+selector+
                    " visual={"+visual+"}"+
                    " wording=RECONSTRUCTED_FROM_OFFICIAL_TOGGLE_SEMANTIC"+
                    " provenance="+
                    PetAccessoryAuthority.selectorAuthority(item),
                    "PET_ACCESSORY_ACTIVATE");
            }

            if(widget==2483){
                serverPackets.beginBatch();
                boolean ended=false;
                String visual;

                try{
                    visual=
                        npcs.publishPetParticleSelector(
                            null,
                            movement,
                            serverPackets
                        );
                    serverPackets.fixed(
                        219,
                        new byte[0]
                    );
                    serverPackets.endBatch();
                    ended=true;
                }catch(IOException failure){
                    if(!ended)
                        try{
                            serverPackets.endBatch();
                        }catch(Throwable ignored){}
                    throw failure;
                }catch(RuntimeException failure){
                    if(!ended)
                        try{
                            serverPackets.endBatch();
                        }catch(Throwable ignored){}
                    throw failure;
                }catch(Error failure){
                    if(!ended)
                        try{
                            serverPackets.endBatch();
                        }catch(Throwable ignored){}
                    throw failure;
                }

                petAccessoryState.clear();
                npcs.commitPetParticleSelector(
                    null
                );
                clearPetAccessoryDialog();

                return Result.saveClose(
                    "V5130_PET_ACCESSORY_DIALOG item="+item+
                    " action=DETACH visual={"+visual+"}"+
                    " wording=RECONSTRUCTED_FROM_OFFICIAL_TOGGLE_SEMANTIC",
                    "PET_ACCESSORY_DETACH");
            }

            if(widget==2484){
                serverPackets.fixed(219,new byte[0]);
                clearPetAccessoryDialog();

                return Result.close(
                    "V5130_PET_ACCESSORY_DIALOG item="+item+
                    " action=CANCEL");
            }
        }

        if(pendingPetColorItems!=null &&
           (widget==54195||(widget>=2482&&widget<=2485))){
            String family=pendingPetColorFamily;

            if(widget==54195){
                serverPackets.fixed(219,new byte[0]);
                clearPetColorDialog();

                return Result.close(
                    "V5127_PET_COLOR_DIALOG family="+family+
                    " action=CLOSE_WINDOW widget=54195");
            }

            if(widget==2485&&!"SCOOBY_BEHEMOTH".equals(family)){
                serverPackets.fixed(219,new byte[0]);
                clearPetColorDialog();

                return Result.close(
                    "V57_PET_COLOR_DIALOG family="+family+
                    " action=CANCEL");
            }

            int choice=widget-2482;
            if(choice>=0&&choice<pendingPetColorItems.length){
                BankState.Stack stack=
                    bank.inventoryAt(pendingPetColorSlot);
                int current=stack==null?-1:stack.itemId;

                if(stack==null||
                   !petColorCurrentAllowed(
                       family,current,pendingPetColorItems)){
                    serverPackets.fixed(219,new byte[0]);
                    clearPetColorDialog();

                    return Result.close(
                        "V57_PET_COLOR_DIALOG family="+family+
                        " result=REJECTED_ITEM_MOVED");
                }

                int replacement=pendingPetColorItems[choice];
                BankState.PreparedInventoryTransform prepared=
                    current==replacement
                        ?null
                        :bank.prepareInventoryTransformOne(
                            pendingPetColorSlot,
                            current,
                            replacement
                        );

                serverPackets.beginBatch();
                boolean ended=false;
                String result;

                try{
                    result=
                        prepared==null
                            ?"INVENTORY_TRANSFORM_NOOP"
                            :bank.publishPreparedInventoryTransform(
                                prepared,
                                serverPackets
                            );
                    serverPackets.fixed(
                        219,
                        new byte[0]
                    );
                    serverPackets.endBatch();
                    ended=true;
                }catch(IOException failure){
                    if(!ended)
                        try{serverPackets.endBatch();}catch(Throwable ignored){}
                    throw failure;
                }catch(RuntimeException failure){
                    if(!ended)
                        try{serverPackets.endBatch();}catch(Throwable ignored){}
                    throw failure;
                }catch(Error failure){
                    if(!ended)
                        try{serverPackets.endBatch();}catch(Throwable ignored){}
                    throw failure;
                }

                if(prepared!=null&&prepared.accepted())
                    result=
                        bank.commitPreparedInventoryTransform(
                            prepared
                        );

                clearPetColorDialog();

                String saveReason=
                    result.startsWith("INVENTORY_TRANSFORM_OK")||
                    result.equals("INVENTORY_TRANSFORM_NOOP")
                        ?"PET_SWITCH_COLOR"
                        :null;

                return new Result(
                    "V57_PET_COLOR_DIALOG family="+family+
                    " choice="+(choice+1)+
                    " result="+result+
                    " item="+current+"->"+replacement,
                    saveReason,
                    KeyAction.CLEAR_AFTER_LOG);
            }
        }

        return null;
    }

    Result openScoobyColorCompat(
        int requested,
        ServerPacketWriter serverPackets
    )throws IOException{
        int slot=-1;
        int current=-1;

        for(int i=0;i<bank.inventoryCapacity();i++){
            BankState.Stack stack=bank.inventoryAt(i);
            if(stack==null)continue;

            if(stack.itemId>=24016&&stack.itemId<=24019&&
               (requested<0||stack.itemId==requested)){
                slot=i;
                current=stack.itemId;
                break;
            }
        }

        if(slot<0){
            return Result.log(
                "V5128_SCOOBY_SWITCH_COLOR"+
                " result=REJECTED_NO_VARIANT_IN_INVENTORY"+
                " requested="+requested);
        }

        int[] family=petColorFamily(current);
        String name=petColorFamilyName(current);
        openPetColorDialog(slot,family,name,serverPackets);

        return Result.open(
            "V5128_SCOOBY_SWITCH_COLOR"+
            " result=DIALOG_OPEN current="+current+
            " slot="+slot+
            " choices="+Arrays.toString(family)+
            " authority=LOCAL_COMPAT_EXTENSION"+
            " native24019MenuAbsent=true");
    }

    CloseState clearAll(){
        CloseState state=new CloseState(
            pendingPetColorItems!=null,
            pendingMiniConfigureItem>=0,
            pendingPetAccessoryItem>=0
        );

        clearPetColorDialog();
        clearMiniConfigureDialog();
        clearPetAccessoryDialog();
        return state;
    }

    boolean hasAnyOpen(){
        return pendingPetColorItems!=null||
            pendingMiniConfigureItem>=0||
            pendingPetAccessoryItem>=0;
    }

    String pendingPetColorFamily(){
        return pendingPetColorFamily;
    }

    private void openMiniConfigureDialog(
        int slot,
        int itemId,
        ServerPacketWriter writer
    )throws IOException{
        writer.beginBatch();
        boolean ended=false;

        try{
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2481,"Configure mini-pet"));
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2482,"Activate this mini-pet"));
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2483,"Disable current mini-pet"));
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2484,"Cancel"));
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2485,"Close"));
            writer.fixed(
                164,
                BootstrapPackets.chatboxInterface164(2480));
            writer.endBatch();
            ended=true;
        }catch(IOException failure){
            if(!ended)
                try{writer.endBatch();}catch(Throwable ignored){}
            throw failure;
        }catch(RuntimeException failure){
            if(!ended)
                try{writer.endBatch();}catch(Throwable ignored){}
            throw failure;
        }catch(Error failure){
            if(!ended)
                try{writer.endBatch();}catch(Throwable ignored){}
            throw failure;
        }

        clearPetColorDialog();
        clearPetAccessoryDialog();
        clearMiniConfigureDialog();
        pendingMiniConfigureSlot=slot;
        pendingMiniConfigureItem=itemId;
    }

    private void clearMiniConfigureDialog(){
        pendingMiniConfigureSlot=-1;
        pendingMiniConfigureItem=-1;
    }

    private void openPetAccessoryDialog(
        int slot,
        int itemId,
        ServerPacketWriter writer
    )throws IOException{
        writer.beginBatch();
        boolean ended=false;

        try{
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2481,"Pet accessory"));
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2482,
                    "Activate "+PetAccessoryAuthority.name(itemId)));
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2483,"Remove active pet accessory"));
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2484,"Cancel"));
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2485,"Close"));
            writer.fixed(
                164,
                BootstrapPackets.chatboxInterface164(2480));
            writer.endBatch();
            ended=true;
        }catch(IOException failure){
            if(!ended)
                try{writer.endBatch();}catch(Throwable ignored){}
            throw failure;
        }catch(RuntimeException failure){
            if(!ended)
                try{writer.endBatch();}catch(Throwable ignored){}
            throw failure;
        }catch(Error failure){
            if(!ended)
                try{writer.endBatch();}catch(Throwable ignored){}
            throw failure;
        }

        clearPetColorDialog();
        clearMiniConfigureDialog();
        clearPetAccessoryDialog();
        pendingPetAccessorySlot=slot;
        pendingPetAccessoryItem=itemId;
    }

    private void clearPetAccessoryDialog(){
        pendingPetAccessorySlot=-1;
        pendingPetAccessoryItem=-1;
    }

    private void openPetColorDialog(
        int slot,
        int[] family,
        String name,
        ServerPacketWriter writer
    )throws IOException{
        int[] nextFamily=family.clone();

        writer.beginBatch();
        boolean ended=false;

        try{
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2481,"Select a color"));

        if("SCOOBY_BEHEMOTH".equals(name)&&family.length==4){
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2482,"Black / white"));
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2483,"Black / orange"));
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2484,"White / blue"));
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2485,"Green / black"));
        }else{
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2482,"Color 1"));
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2483,"Color 2"));
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2484,"Color 3"));
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2485,"Cancel"));
        }

            writer.fixed(
                164,
                BootstrapPackets.chatboxInterface164(2480));
            writer.endBatch();
            ended=true;
        }catch(IOException failure){
            if(!ended)
                try{writer.endBatch();}catch(Throwable ignored){}
            throw failure;
        }catch(RuntimeException failure){
            if(!ended)
                try{writer.endBatch();}catch(Throwable ignored){}
            throw failure;
        }catch(Error failure){
            if(!ended)
                try{writer.endBatch();}catch(Throwable ignored){}
            throw failure;
        }

        clearMiniConfigureDialog();
        clearPetAccessoryDialog();
        clearPetColorDialog();
        pendingPetColorSlot=slot;
        pendingPetColorItems=nextFamily;
        pendingPetColorFamily=name;
    }

    private void clearPetColorDialog(){
        pendingPetColorSlot=-1;
        pendingPetColorItems=null;
        pendingPetColorFamily=null;
    }

    private static boolean contains(int[] values,int target){
        for(int value:values)if(value==target)return true;
        return false;
    }

    private static int[] petColorFamily(int itemId){
        if(itemId>=27340&&itemId<=27342)
            return new int[]{27340,27341,27342};
        if(itemId>=27343&&itemId<=27345)
            return new int[]{27343,27344,27345};
        if(itemId>=24016&&itemId<=24019)
            return new int[]{24016,24017,24018,24019};
        return null;
    }

    private static String petColorFamilyName(int itemId){
        if(itemId>=27340&&itemId<=27342)
            return "RESVANO_EVIL_WOLPER";
        if(itemId>=27343&&itemId<=27345)
            return "RESVANO_ETHEREAL";
        if(itemId>=24016&&itemId<=24019)
            return "SCOOBY_BEHEMOTH";
        return "UNKNOWN";
    }

    private static boolean petColorCurrentAllowed(
        String family,
        int current,
        int[] choices
    ){
        if("SCOOBY_BEHEMOTH".equals(family))
            return current>=24016&&current<=24019;
        return contains(choices,current);
    }
}
