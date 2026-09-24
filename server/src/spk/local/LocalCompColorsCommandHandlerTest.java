package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class LocalCompColorsCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        PlayerPresentationService presentation=
            new PlayerPresentationService(dev);
        LocalCompColorsCommandHandler h=
            new LocalCompColorsCommandHandler(
                player.playerState(),
                player.equipment(),
                presentation
            );

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter w=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        int[] before=
            player.playerState()
                .compSelectors();

        LocalCompColorsCommandHandler.Result bad=
            h.apply(
                new int[]{1,2,3,4,5,99},
                "::compcolors 1 2 3 4 5 99",
                "opensrc",
                w
            );

        if(bad==null||
           !bad.logText.contains(
                "REJECTED_SELECTOR_RANGE"))
            throw new AssertionError(
                "invalid selector effect"
            );

        if(bad.saveReason!=null)
            throw new AssertionError(
                "invalid selectors must not save"
            );

        if(!Arrays.equals(
                before,
                player.playerState()
                    .compSelectors()))
            throw new AssertionError(
                "invalid selectors mutated state"
            );

        LocalCompColorsCommandHandler.Result ok=
            h.apply(
                new int[]{1,2,3,4,5,6},
                "::compcolors 1 2 3 4 5 6",
                "opensrc",
                w
            );

        if(ok==null||
           !ok.logText.contains(
                "result=APPLIED selectors=[1, 2, 3, 4, 5, 6]"))
            throw new AssertionError(
                "valid selector effect="+
                (ok==null
                    ?"null"
                    :ok.logText)
            );

        if(!"COMP_COLORS".equals(
                ok.saveReason))
            throw new AssertionError(
                "save reason="+
                ok.saveReason
            );

        if(!Arrays.equals(
                new int[]{1,2,3,4,5,6},
                player.playerState()
                    .compSelectors()))
            throw new AssertionError(
                "selectors not delegated"
            );

        LocalCompColorsCommandHandler.Result wrongArity=
            h.apply(
                new int[]{1,2},
                "::compcolors 1 2",
                "opensrc",
                w
            );

        if(wrongArity==null||
           wrongArity.saveReason!=null||
           !wrongArity.logText.contains(
               "REJECTED_SELECTOR_RANGE"))
            throw new AssertionError(
                "wrong arity effect"
            );

        System.out.println(
            "LOCAL_COMP_COLORS_COMMAND_HANDLER_PASS "+
            "valid=true "+
            "invalidNoMutation=true "+
            "persistenceSignal=true "+
            "parserAbsent=true"
        );
    }
}
