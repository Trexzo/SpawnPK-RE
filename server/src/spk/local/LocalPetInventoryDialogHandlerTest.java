package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalPetInventoryDialogHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();
        PetState petState=player.petState();
        MiniPetService miniPets=player.miniPets();
        MovementState movement=player.movement();
        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        NpcRegistry npcs=
            new NpcRegistry(dev);
        PetAccessoryState accessory=
            new PetAccessoryState();

        LocalPetInventoryDialogHandler handler=
            new LocalPetInventoryDialogHandler(
                bank,
                miniPets,
                petState,
                npcs,
                movement,
                accessory
            );

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        bank.spawnItem(22088,1,writer);
        int miniSlot=findSlot(bank,22088);
        if(miniSlot<0)throw new AssertionError("mini item not spawned");

        ItemContainerAction miniRead=new ItemContainerAction(
            122,
            BankState.NORMAL_INVENTORY_CONTAINER,
            miniSlot,
            22088,
            0,
            "INVENTORY_OPTION_1"
        );

        LocalPetInventoryDialogHandler.Result miniOpen=
            handler.handleItemAction(miniRead,writer);

        if(miniOpen==null||
           miniOpen.keyAction!=
               LocalPetInventoryDialogHandler.KeyAction.PUBLISH_2482_2485||
           !miniOpen.logText.contains("V5127_MINIPET_CONFIGURE"))
            throw new AssertionError("mini open="+
                (miniOpen==null?null:miniOpen.logText));

        LocalPetInventoryDialogHandler.Result miniActivate=
            handler.handleWidget(2482,writer);

        if(miniActivate==null||
           !"MINIPET_CONFIGURE".equals(miniActivate.saveReason)||
           miniActivate.keyAction!=
               LocalPetInventoryDialogHandler.KeyAction.CLEAR_AFTER_LOG||
           !petState.miniConfigured()||
           petState.miniItemId()!=22088)
            throw new AssertionError("mini activate failure");

        bank.spawnItem(24016,1,writer);
        int colorSlot=findSlot(bank,24016);
        if(colorSlot<0)throw new AssertionError("color item not spawned");

        ItemContainerAction colorAction=new ItemContainerAction(
            75,
            BankState.NORMAL_INVENTORY_CONTAINER,
            colorSlot,
            24016,
            0,
            "INVENTORY_OPTION_3"
        );

        LocalPetInventoryDialogHandler.Result colorOpen=
            handler.handleItemAction(colorAction,writer);

        if(colorOpen==null||
           !colorOpen.logText.contains(
               "family=SCOOBY_BEHEMOTH")||
           colorOpen.keyAction!=
               LocalPetInventoryDialogHandler.KeyAction.PUBLISH_2482_2485)
            throw new AssertionError("color open="+
                (colorOpen==null?null:colorOpen.logText));

        LocalPetInventoryDialogHandler.Result colorSwitch=
            handler.handleWidget(2483,writer);

        if(colorSwitch==null||
           !"PET_SWITCH_COLOR".equals(colorSwitch.saveReason)||
           bank.inventoryAt(colorSlot)==null||
           bank.inventoryAt(colorSlot).itemId!=24017)
            throw new AssertionError("color switch="+
                (colorSwitch==null?null:colorSwitch.logText));

        LocalPetInventoryDialogHandler.Result compat=
            handler.openScoobyColorCompat(24017,writer);

        if(compat==null||
           !compat.logText.contains("result=DIALOG_OPEN")||
           !handler.hasAnyOpen())
            throw new AssertionError("compat color open");

        LocalPetInventoryDialogHandler.CloseState close=
            handler.clearAll();

        if(!close.petColorWasOpen||
           close.miniConfigWasOpen||
           close.petAccessoryWasOpen||
           handler.hasAnyOpen())
            throw new AssertionError("close state");

        testPetAccessoryPublicationAtomicity();

        System.out.println(
            "LOCAL_PET_INVENTORY_DIALOG_HANDLER_PASS mini=true color=true compat=true pendingStateOwned=true accessoryNoPetAtomic=true accessoryActivePetAtomic=true accessoryDetachAtomic=true accessoryDialogFailurePreserved=true");
    }

    private static void testPetAccessoryPublicationAtomicity()
        throws Exception
    {
        noPetAccessoryAtomicity();
        activePetAccessoryAtomicity();
    }

    private static void noPetAccessoryAtomicity()
        throws Exception
    {
        WorldPlayer player=
            new WorldPlayer();
        BankState bank=player.bank();
        MovementState movement=player.movement();
        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        NpcRegistry npcs=
            new NpcRegistry(dev);
        PetAccessoryState accessory=
            new PetAccessoryState();
        LocalPetInventoryDialogHandler handler=
            new LocalPetInventoryDialogHandler(
                bank,
                player.miniPets(),
                player.petState(),
                npcs,
                movement,
                accessory
            );

        ServerPacketWriter healthy=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{21,22,23,24}
                )
            );

        bank.spawnItem(
            20543,
            1,
            healthy
        );
        int slot=findSlot(
            bank,
            20543
        );

        openAccessory(
            handler,
            slot,
            20543,
            healthy
        );

        OutboundPacketQueue activateQueue=
            fullQueue();
        boolean activateFailed=false;

        try{
            handler.handleWidget(
                2482,
                queueWriter(
                    activateQueue,
                    new int[]{25,26,27,28}
                )
            );
        }catch(java.io.IOException expected){
            activateFailed=true;
        }

        if(!activateFailed||
           accessory.activeItem()!=0||
           dev.petParticleSelector()!=null||
           !handler.hasAnyOpen()||
           activateQueue.queuedBytes()!=1024)
            throw new AssertionError(
                "failed no-pet accessory activate changed state"
            );

        LocalPetInventoryDialogHandler.Result activated=
            handler.handleWidget(
                2482,
                healthy
            );

        if(activated==null||
           !"PET_ACCESSORY_ACTIVATE".equals(
                activated.saveReason
           )||
           accessory.activeItem()!=20543||
           !Integer.valueOf(2).equals(
                dev.petParticleSelector()
           )||
           handler.hasAnyOpen())
            throw new AssertionError(
                "no-pet accessory activate retry failed"
            );

        openAccessory(
            handler,
            slot,
            20543,
            healthy
        );

        OutboundPacketQueue detachQueue=
            fullQueue();
        boolean detachFailed=false;

        try{
            handler.handleWidget(
                2483,
                queueWriter(
                    detachQueue,
                    new int[]{29,30,31,32}
                )
            );
        }catch(java.io.IOException expected){
            detachFailed=true;
        }

        if(!detachFailed||
           accessory.activeItem()!=20543||
           !Integer.valueOf(2).equals(
                dev.petParticleSelector()
           )||
           !handler.hasAnyOpen()||
           detachQueue.queuedBytes()!=1024)
            throw new AssertionError(
                "failed no-pet accessory detach changed state"
            );

        LocalPetInventoryDialogHandler.Result detached=
            handler.handleWidget(
                2483,
                healthy
            );

        if(detached==null||
           !"PET_ACCESSORY_DETACH".equals(
                detached.saveReason
           )||
           accessory.activeItem()!=0||
           dev.petParticleSelector()!=null||
           handler.hasAnyOpen())
            throw new AssertionError(
                "no-pet accessory detach retry failed"
            );
    }

    private static void activePetAccessoryAtomicity()
        throws Exception
    {
        WorldPlayer player=
            new WorldPlayer();
        BankState bank=player.bank();
        MovementState movement=player.movement();
        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        NpcRegistry npcs=
            new NpcRegistry(dev);
        PetAccessoryState accessory=
            new PetAccessoryState();
        LocalPetInventoryDialogHandler handler=
            new LocalPetInventoryDialogHandler(
                bank,
                player.miniPets(),
                player.petState(),
                npcs,
                movement,
                accessory
            );

        ServerPacketWriter healthy=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{33,34,35,36}
                )
            );

        PetDefinitionRepository.Def petDef=
            PetDefinitionRepository.all()
                .iterator()
                .next();

        String spawned=
            npcs.spawnPet(
                petDef,
                movement,
                healthy
            );

        if(spawned==null||
           !spawned.contains(
                "PET_SPAWN_OK"
           )||
           npcs.pet()==null)
            throw new AssertionError(
                "active-pet accessory fixture spawn failed result="+
                spawned
            );

        NpcEntity canonicalPet=
            npcs.pet();

        bank.spawnItem(
            20543,
            1,
            healthy
        );
        bank.spawnItem(
            20544,
            1,
            healthy
        );

        int redSlot=findSlot(
            bank,
            20543
        );
        int greenSlot=findSlot(
            bank,
            20544
        );

        openAccessory(
            handler,
            redSlot,
            20543,
            healthy
        );
        handler.handleWidget(
            2482,
            healthy
        );

        if(accessory.activeItem()!=20543||
           !Integer.valueOf(2).equals(
                dev.petParticleSelector()
           )||
           npcs.pet()!=canonicalPet)
            throw new AssertionError(
                "active-pet baseline accessory activation failed"
            );

        openAccessory(
            handler,
            greenSlot,
            20544,
            healthy
        );

        OutboundPacketQueue replaceQueue=
            fullQueue();
        boolean replaceFailed=false;

        try{
            handler.handleWidget(
                2482,
                queueWriter(
                    replaceQueue,
                    new int[]{37,38,39,40}
                )
            );
        }catch(java.io.IOException expected){
            replaceFailed=true;
        }

        if(!replaceFailed||
           accessory.activeItem()!=20543||
           !Integer.valueOf(2).equals(
                dev.petParticleSelector()
           )||
           npcs.pet()!=canonicalPet||
           !handler.hasAnyOpen()||
           replaceQueue.queuedBytes()!=1024)
            throw new AssertionError(
                "failed active-pet accessory replacement changed canonical state"
            );

        LocalPetInventoryDialogHandler.Result replaced=
            handler.handleWidget(
                2482,
                healthy
            );

        if(replaced==null||
           !"PET_ACCESSORY_ACTIVATE".equals(
                replaced.saveReason
           )||
           accessory.activeItem()!=20544||
           !Integer.valueOf(3).equals(
                dev.petParticleSelector()
           )||
           npcs.pet()!=canonicalPet||
           handler.hasAnyOpen())
            throw new AssertionError(
                "active-pet accessory replacement retry failed"
            );

        openAccessory(
            handler,
            greenSlot,
            20544,
            healthy
        );

        OutboundPacketQueue detachQueue=
            fullQueue();
        boolean detachFailed=false;

        try{
            handler.handleWidget(
                2483,
                queueWriter(
                    detachQueue,
                    new int[]{41,42,43,44}
                )
            );
        }catch(java.io.IOException expected){
            detachFailed=true;
        }

        if(!detachFailed||
           accessory.activeItem()!=20544||
           !Integer.valueOf(3).equals(
                dev.petParticleSelector()
           )||
           npcs.pet()!=canonicalPet||
           !handler.hasAnyOpen()||
           detachQueue.queuedBytes()!=1024)
            throw new AssertionError(
                "failed active-pet accessory detach changed canonical state"
            );

        LocalPetInventoryDialogHandler.Result detached=
            handler.handleWidget(
                2483,
                healthy
            );

        if(detached==null||
           !"PET_ACCESSORY_DETACH".equals(
                detached.saveReason
           )||
           accessory.activeItem()!=0||
           dev.petParticleSelector()!=null||
           npcs.pet()!=canonicalPet||
           handler.hasAnyOpen())
            throw new AssertionError(
                "active-pet accessory detach retry failed"
            );
    }

    private static void openAccessory(
        LocalPetInventoryDialogHandler handler,
        int slot,
        int itemId,
        ServerPacketWriter writer
    )throws Exception{
        LocalPetInventoryDialogHandler.Result opened=
            handler.handleItemAction(
                new ItemContainerAction(
                    122,
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    slot,
                    itemId,
                    0,
                    "INVENTORY_OPTION_1"
                ),
                writer
            );

        if(opened==null||
           opened.keyAction!=
                LocalPetInventoryDialogHandler
                    .KeyAction.PUBLISH_2482_2485||
           !handler.hasAnyOpen())
            throw new AssertionError(
                "pet accessory dialog did not open item="+
                itemId
            );
    }

    private static OutboundPacketQueue fullQueue()
        throws Exception
    {
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);
        queue.offer(
            new byte[1024]
        );
        return queue;
    }

    private static ServerPacketWriter queueWriter(
        OutboundPacketQueue queue,
        int[] seed
    ){
        return new ServerPacketWriter(
            queue,
            new IsaacCipher(seed)
        );
    }

    private static int findSlot(BankState bank,int itemId){
        for(int i=0;i<bank.inventoryCapacity();i++){
            BankState.Stack stack=bank.inventoryAt(i);
            if(stack!=null&&stack.itemId==itemId&&stack.qty>0)return i;
        }
        return -1;
    }
}
