package spk.local;

import java.lang.reflect.Method;
import java.util.Locale;

public final class NormalDuelPresentationTest {
    public static void main(String[] args)
        throws Exception{
        exactRoots();
        exactSelectorInputs();
        exactLoadLastReconstruction();
        exactChallengeGrammar();
        policyStillUnowned();

        System.out.println(
            "NORMAL_DUEL_PRESENTATION_PASS "+
            "selector25754=true "+
            "rules6575=true "+
            "modes3=true "+
            "invite25763=true "+
            "close65418=true "+
            "loadLast68440Wire2904=true "+
            "challengeSuffixes3=true "+
            "policyOwned=false"
        );
    }

    private static void exactRoots(){
        require(
            NormalDuelPresentation.SELECTOR_ROOT==
                25754,
            "selector root"
        );
        require(
            NormalDuelPresentation.RULES_ROOT==
                6575,
            "rules root"
        );
        require(
            NormalDuelPresentation.WIDGET_ACTION_OPCODE==
                185,
            "C2S185"
        );

        byte[] selector=
            BootstrapPackets.interface97(
                NormalDuelPresentation
                    .SELECTOR_ROOT
            );
        byte[] rules=
            BootstrapPackets.interface97(
                NormalDuelPresentation
                    .RULES_ROOT
            );

        require(
            readU16(selector)==25754,
            "selector S2C97"
        );
        require(
            readU16(rules)==6575,
            "rules S2C97"
        );
    }

    private static void exactSelectorInputs(){
        requireMode(
            25759,
            NormalDuelPresentation
                .DuelMode.STANDARD
        );
        requireMode(
            25760,
            NormalDuelPresentation
                .DuelMode.WHIP_ONLY
        );
        requireMode(
            25761,
            NormalDuelPresentation
                .DuelMode.WHIP_DDS_ONLY
        );

        require(
            NormalDuelPresentation
                .resolveSelectorWidget(25763)
                .kind==
                NormalDuelPresentation
                    .InputKind.INVITE,
            "Invite"
        );
        require(
            NormalDuelPresentation
                .resolveSelectorWidget(65418)
                .kind==
                NormalDuelPresentation
                    .InputKind.CLOSE,
            "Close"
        );

        require(
            NormalDuelPresentation.STANDARD_WIDGET==
                0x649f&&
            NormalDuelPresentation.WHIP_WIDGET==
                0x64a0&&
            NormalDuelPresentation.WHIP_DDS_WIDGET==
                0x64a1&&
            NormalDuelPresentation.INVITE_WIDGET==
                0x64a3&&
            NormalDuelPresentation.CLOSE_WIDGET==
                0xff8a,
            "exact C2S185 bodies"
        );

        require(
            NormalDuelPresentation
                .resolveSelectorWidget(25762)==null&&
            NormalDuelPresentation
                .resolveSelectorWidget(25764)==null,
            "neighbor selector widgets"
        );
    }

    private static void exactLoadLastReconstruction(){
        require(
            NormalDuelPresentation
                .LOAD_LAST_CLIENT_WIDGET==
                68440,
            "client-local load-last id"
        );
        require(
            NormalDuelPresentation
                .LOAD_LAST_WIRE_WIDGET==
                2904,
            "u16 load-last wire id"
        );
        require(
            NormalDuelPresentation
                .LOAD_LAST_WIRE_WIDGET==
                0x0b58,
            "load-last body 0B58"
        );
        require(
            NormalDuelPresentation
                .LOAD_LAST_SPRITE_WIDGET==
                68439,
            "load-last sprite"
        );

        NormalDuelPresentation.Input active=
            NormalDuelPresentation
                .resolveRulesWireWidget(
                    2904,
                    6575
                );

        require(
            active!=null&&
            active.kind==
                NormalDuelPresentation
                    .InputKind
                    .LOAD_LAST_RULES,
            "active-root load-last"
        );

        require(
            NormalDuelPresentation
                .resolveRulesWireWidget(
                    2904,
                    25754
                )==null,
            "selector root must not reinterpret 2904"
        );
        require(
            NormalDuelPresentation
                .resolveRulesWireWidget(
                    2904,
                    0
                )==null,
            "inactive root must not reinterpret 2904"
        );

        expect(
            IllegalArgumentException.class,
            ()->NormalDuelPresentation
                .resolveRulesWireWidget(
                    68440,
                    6575
                ),
            "client-local id cannot arrive on u16 C2S185"
        );
    }

    private static void exactChallengeGrammar(){
        require(
            ":duelreq:".equals(
                NormalDuelPresentation
                    .challengeSuffix(
                        NormalDuelPresentation
                            .DuelMode.STANDARD
                    )
            ),
            "standard challenge"
        );
        require(
            ":whipduelreq:".equals(
                NormalDuelPresentation
                    .challengeSuffix(
                        NormalDuelPresentation
                            .DuelMode.WHIP_ONLY
                    )
            ),
            "whip challenge"
        );
        require(
            ":whipddsreq:".equals(
                NormalDuelPresentation
                    .challengeSuffix(
                        NormalDuelPresentation
                            .DuelMode.WHIP_DDS_ONLY
                    )
            ),
            "whip+dds challenge"
        );
    }

    private static void policyStillUnowned(){
        for(Method method:
                NormalDuelPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("stake")||
               name.contains("escrow")||
               name.contains("arena")||
               name.contains("restrict")||
               name.contains("timeout")||
               name.contains("disconnect")||
               name.contains("persist")||
               name.contains("winner"))
                throw new AssertionError(
                    "unowned Duel policy method "+
                    method.getName()
                );
        }
    }

    private static void requireMode(
        int widgetId,
        NormalDuelPresentation.DuelMode mode
    ){
        NormalDuelPresentation.Input input=
            NormalDuelPresentation
                .resolveSelectorWidget(widgetId);

        require(
            input!=null&&
            input.kind==
                NormalDuelPresentation
                    .InputKind.SELECT_MODE&&
            input.mode==mode,
            "mode widget "+widgetId
        );
    }

    private static int readU16(byte[] body){
        require(
            body!=null&&body.length==2,
            "u16 body"
        );
        return ((body[0]&255)<<8)|
            (body[1]&255);
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private NormalDuelPresentationTest(){}
}
