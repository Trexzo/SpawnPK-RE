package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalNurseCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        PlayerState ps=player.playerState();
        MovementState movement=player.movement();

        ps.setCurrentLevel(PlayerState.HITPOINTS,37);
        ps.setCurrentLevel(PlayerState.PRAYER,12);
        ps.setCurrentLevel(PlayerState.RANGED,44);
        ps.setCurrentLevel(PlayerState.MAGIC,55);
        ps.setSpecialEnergy(17);
        ps.setNegativeEffects(3,4,5);
        movement.setRunEnergy(19);

        LocalNurseCommandHandler h=new LocalNurseCommandHandler(ps,movement);

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(wire,new IsaacCipher(new int[]{1,2,3,4}));

        LocalNurseCommandHandler.Result result=
            h.handle(new String[]{"nurse"},"::nurse",false,w);

        if(result==null||!result.logText.startsWith("V58_NURSE command=::nurse"))
            throw new AssertionError("nurse route");
        if(!"NURSE".equals(result.saveReason))
            throw new AssertionError("save reason="+result.saveReason);

        if(ps.currentLevel(PlayerState.HITPOINTS)!=99)throw new AssertionError("hp");
        if(ps.currentLevel(PlayerState.PRAYER)!=99)throw new AssertionError("prayer");
        if(ps.currentLevel(PlayerState.RANGED)!=99)throw new AssertionError("ranged");
        if(ps.currentLevel(PlayerState.MAGIC)!=99)throw new AssertionError("magic");
        if(ps.specialEnergy()!=100)throw new AssertionError("special");
        if(ps.poison()!=0||ps.venom()!=0||ps.sicken()!=0)throw new AssertionError("status");
        if(movement.runEnergy()!=100)throw new AssertionError("run");
        if(wire.size()==0)throw new AssertionError("nurse presentation emitted no packets");

        if(h.handle(new String[]{"item","4151"},"::item 4151",false,w)!=null)
            throw new AssertionError("unrelated command must remain outside nurse handler");

        System.out.println("LOCAL_NURSE_COMMAND_HANDLER_PASS restore=true statusClear=true run100=true presentation=true persistenceSignal=true unrelatedRejected=true");
    }
}
