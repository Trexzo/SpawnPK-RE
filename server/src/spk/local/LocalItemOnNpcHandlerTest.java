package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalItemOnNpcHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        PetDefinitionRepository.Def petDef=
            PetDefinitionRepository.all().iterator().next();
        player.petState().activate(petDef);

        NpcRegistry npcs=new NpcRegistry();
        PetAccessoryState accessoryState=new PetAccessoryState();

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        npcs.bootstrap(w,player.movement(),player.petState());

        BankState bank=player.bank();
        bank.spawnItem(20542,1,w);

        LocalItemOnNpcHandler h=new LocalItemOnNpcHandler(
            bank,npcs,player.movement(),accessoryState);

        ItemOnNpcAction attach=new ItemOnNpcAction(
            20542,
            0,
            BankState.NORMAL_INVENTORY_CONTAINER,
            NpcRegistry.PET_INDEX
        );

        LocalItemOnNpcHandler.Result attached=h.handle(attach,w);
        if(attached==null||
           !attached.logText.contains("result=ATTACHED selector=1"))
            throw new AssertionError("accessory attach route="+
                (attached==null?"null":attached.logText));
        if(!"PET_ACCESSORY_USE_ON_PET".equals(attached.saveReason))
            throw new AssertionError("attach save reason="+attached.saveReason);
        if(accessoryState.activeItem()!=20542)
            throw new AssertionError("semantic accessory state not updated");
        if(!Integer.valueOf(1).equals(npcs.petParticleSelector()))
            throw new AssertionError("pet selector not published");

        ItemOnNpcAction moved=new ItemOnNpcAction(
            20542,
            5,
            BankState.NORMAL_INVENTORY_CONTAINER,
            NpcRegistry.PET_INDEX
        );
        LocalItemOnNpcHandler.Result mismatch=h.handle(moved,w);
        if(mismatch==null||
           !mismatch.logText.contains("REJECTED_SOURCE_INVENTORY_MISMATCH"))
            throw new AssertionError("source mismatch route");
        if(mismatch.saveReason!=null)
            throw new AssertionError("source mismatch must not save");

        bank.spawnItem(4151,1,w);
        int swordSlot=-1;
        for(int i=0;i<bank.inventoryCapacity();i++){
            BankState.Stack s=bank.inventoryAt(i);
            if(s!=null&&s.itemId==4151){swordSlot=i;break;}
        }
        if(swordSlot<0)throw new AssertionError("whip spawn precondition");

        ItemOnNpcAction unknown=new ItemOnNpcAction(
            4151,
            swordSlot,
            BankState.NORMAL_INVENTORY_CONTAINER,
            NpcRegistry.PET_INDEX
        );
        LocalItemOnNpcHandler.Result failClosed=h.handle(unknown,w);
        if(failClosed==null||
           !failClosed.logText.contains("DECODED_NO_SEMANTIC_HANDLER"))
            throw new AssertionError("unknown item-on-npc route="+
                (failClosed==null?"null":failClosed.logText));
        if(failClosed.saveReason!=null)
            throw new AssertionError("unknown route must not save");

        if(!PetAccessoryAuthority.isAccessory(20699)||
           PetAccessoryAuthority.selector(20699)!=7||
           !"STRONG_ENCHANTED_CYCLE_BEHAVIOR_NAME_MAPPING".equals(
               PetAccessoryAuthority.selectorAuthority(20699))){
            throw new AssertionError("accessory authority mapping drift");
        }

        System.out.println(
            "LOCAL_ITEM_ON_NPC_HANDLER_PASS accessoryAttach=true stateBeforeVisual=true sourceGuard=true unknownFailClosed=true provenancePreserved=true");
    }
}
