package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class LocalPetRuntimeCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        NpcRegistry npcs=new NpcRegistry(
            new DevAuthorityWorkbench());

        LocalPetRuntimeCommandHandler handler=
            new LocalPetRuntimeCommandHandler(
                player.petState(),
                player.petEffects(),
                npcs,
                player.movement());

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        List<String> status=
            handler.status();

        assertContains(
            status,
            "V59_PET_STATUS active=false",
            "status");

        if(handler.handle(
                new String[]{"petstatus"},
                writer)!=null)
            throw new AssertionError(
                "raw petstatus route remains"
            );

        int before=wire.size();
        List<String> boost=
            handler.boost(
                writer
            );

        assertContains(
            boost,
            "V593_PET_BOOST_FIXTURE",
            "boost"
        );

        if(wire.size()<=before)
            throw new AssertionError(
                "petboost emitted no packet"
            );

        if(handler.handle(
                new String[]{"petboost"},
                writer)!=null)
            throw new AssertionError(
                "raw petboost route remains"
            );

        int beforeScope=
            wire.size();

        List<String> noScope=
            handler.scopeSnipe(
                writer
            );

        assertContains(
            noScope,
            "REJECTED_NO_ACTIVE_SCOPESIGHT",
            "scope reject"
        );

        if(wire.size()!=beforeScope)
            throw new AssertionError(
                "rejected scopesnipe emitted packet"
            );

        if(handler.handle(
                new String[]{"scopesnipe"},
                writer)!=null)
            throw new AssertionError(
                "raw scopesnipe route remains"
            );

        List<String> armed=handler.handle(
            new String[]{"pettestall"},
            writer);

        assertContains(
            armed,
            "V59_PET_TEST_ALL_ARMED",
            "arm");

        if(!handler.sequenceActive())
            throw new AssertionError(
                "sequence not armed");

        long now=handler.sequenceAt();
        before=wire.size();

        String step=handler.tickSequence(
            now,writer);

        if(step==null||
           !step.contains(
               "V591_PET_TEST_ALL step=1/9"))
            throw new AssertionError(
                "sequence step="+step);

        if(wire.size()<=before)
            throw new AssertionError(
                "sequence step emitted no packet");

        if(handler.sequenceAt()!=now+1800L)
            throw new AssertionError(
                "sequence cadence="+handler.sequenceAt());

        String damage=handler.applyDamage(
            50,
            now,
            writer,
            "TEST");

        if(!damage.contains(
            "result=NO_ACTIVE_CHARGE_PET"))
            throw new AssertionError(
                "damage route="+damage);

        handler.failSequence();
        if(handler.sequenceActive()||
           handler.sequenceAt()!=Long.MAX_VALUE)
            throw new AssertionError(
                "sequence failure reset");

        if(handler.handle(
            new String[]{"devpet","info"},
            writer)!=null)
            throw new AssertionError(
                "unrelated command consumed");

        System.out.println(
            "LOCAL_PET_RUNTIME_COMMAND_HANDLER_PASS statusEffect=true rawPetStatusRoute=false boostEffect=true rawPetBoostRoute=false scopeEffect=true rawScopeSnipeRoute=false sequence=true damageBoundary=true unrelatedRejected=true");
    }

    private static void assertContains(
        List<String> lines,
        String expected,
        String label
    ){
        if(lines==null||
           lines.size()!=1||
           !lines.get(0).contains(expected)){
            throw new AssertionError(
                label+" lines="+lines+
                " expected="+expected);
        }
    }
}
