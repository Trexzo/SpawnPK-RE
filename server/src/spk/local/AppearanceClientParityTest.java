package spk.local;

import java.lang.reflect.*;
import java.util.Arrays;

/**
 * Runs only when the original client jar is also on the classpath.
 *
 * Covers both the historical minimal/default block and a non-default female
 * Make-over Mage appearance against the pinned client's real rs.a.k parser.
 */
public final class AppearanceClientParityTest {
    public static void main(String[] args) throws Exception {
        verifyDefault();
        verifyMakeoverFemale();
        verifyRoleHeadOverrideSeparation();

        System.out.println(
            "APPEARANCE_CLIENT_PARSER_PARITY_PASS "+
            "default=true makeoverFemale=true "+
            "appearanceRoleAc=true wornHeadVsOverrideBs=true "+
            "fullConsumption=true"
        );
    }

    private static void verifyDefault() throws Exception {
        byte[] data=
            BootstrapPackets.appearanceBlock(
                "localtest"
            );

        Parsed parsed=parse(data);

        if(parsed.consumed!=data.length)
            throw new AssertionError(
                "default consumed="+
                parsed.consumed+
                " len="+data.length
            );

        if(!"localtest".equals(parsed.name))
            throw new AssertionError(
                "default display name="+
                parsed.name
            );
    }

    private static void verifyMakeoverFemale()
        throws Exception{
        PlayerState state=
            new PlayerState();

        int[] kits={
            45,-1,56,61,67,70,79
        };
        int[] colours={
            11,15,14,5,23
        };

        if(!state.setCharacterAppearance(
                CharacterDesignProfile.FEMALE,
                kits,
                colours))
            throw new AssertionError(
                "female setup rejected"
            );

        byte[] data=
            BootstrapPackets.appearanceBlock(
                "makeovertest",
                null,
                state
            );

        Parsed parsed=parse(data);

        int[] expectedAppearance=
            new int[12];

        int[] kitSlots={
            8,11,4,6,9,7,10
        };

        for(int i=0;i<kitSlots.length;i++)
            if(kits[i]>=0)
                expectedAppearance[
                    kitSlots[i]
                ]=256+kits[i];

        if(parsed.consumed!=data.length)
            throw new AssertionError(
                "makeover consumed="+
                parsed.consumed+
                " len="+data.length
            );

        if(parsed.gender!=
                CharacterDesignProfile.FEMALE)
            throw new AssertionError(
                "makeover gender="+
                parsed.gender
            );

        if(!Arrays.equals(
                parsed.appearance,
                expectedAppearance))
            throw new AssertionError(
                "makeover appearance="+
                Arrays.toString(
                    parsed.appearance
                )+
                " expected="+
                Arrays.toString(
                    expectedAppearance
                )
            );

        if(!Arrays.equals(
                parsed.colours,
                colours))
            throw new AssertionError(
                "makeover colours="+
                Arrays.toString(
                    parsed.colours
                )
            );

        if(parsed.appearance[11]!=0)
            throw new AssertionError(
                "female jaw slot="+
                parsed.appearance[11]
            );

        if(!"makeovertest".equals(
                parsed.name))
            throw new AssertionError(
                "makeover display name="+
                parsed.name
            );

        System.out.println(
            "APPEARANCE_CLIENT_MAKEOVER_FEMALE_PASS "+
            "bytes="+data.length+
            " consumed="+parsed.consumed+
            " gender="+parsed.gender+
            " appearance="+
            Arrays.toString(
                parsed.appearance
            )+
            " colours="+
            Arrays.toString(
                parsed.colours
            )+
            " femaleJawSlot="+
            parsed.appearance[11]
        );
    }

    private static void verifyRoleHeadOverrideSeparation()
        throws Exception{
        if(EquipmentSlot.HEAD.appearanceIndex!=0)
            throw new AssertionError(
                "exact staff-partyhat branch requires HEAD br[0]"
            );

        int[] worn=new int[12];
        Arrays.fill(worn,-1);
        worn[EquipmentSlot.HEAD.appearanceIndex]=22131;

        Parsed wornParsed=
            parse(
                BootstrapPackets.appearanceBlock(
                    "wornadmin",
                    worn,
                    new PlayerState(),
                    null,
                    45
                )
            );

        if(wornParsed.appearanceRole!=45)
            throw new AssertionError(
                "worn aC="+wornParsed.appearanceRole
            );
        if(wornParsed.appearance[0]!=512+22131)
            throw new AssertionError(
                "worn HEAD br0="+
                wornParsed.appearance[0]
            );
        if(wornParsed.extraAppearanceItem==22131)
            throw new AssertionError(
                "worn HEAD leaked into bs"
            );

        int[] noWorn=new int[12];
        Arrays.fill(noWorn,-1);
        PlayerState overrideState=
            new PlayerState();
        overrideState.cosmetic().set(22131);
        overrideState.syncEquipmentPresentation(
            new EquipmentState()
        );

        Parsed overrideParsed=
            parse(
                BootstrapPackets.appearanceBlock(
                    "overrideadmin",
                    noWorn,
                    overrideState,
                    null,
                    45
                )
            );

        if(overrideParsed.appearanceRole!=45)
            throw new AssertionError(
                "override aC="+
                overrideParsed.appearanceRole
            );
        if(overrideParsed.appearance[0]==512+22131)
            throw new AssertionError(
                "Override falsely occupied HEAD br0"
            );
        if(overrideParsed.extraAppearanceItem!=22131)
            throw new AssertionError(
                "Override bs="+
                overrideParsed.extraAppearanceItem
            );

        for(int fixedHat:new int[]{23481,22133,22130}){
            int[] fixed=new int[12];
            Arrays.fill(fixed,-1);
            fixed[0]=fixedHat;
            Parsed parsed=
                parse(
                    BootstrapPackets.appearanceBlock(
                        "fixedhat",
                        fixed,
                        new PlayerState(),
                        null,
                        0
                    )
                );
            if(parsed.appearance[0]!=512+fixedHat)
                throw new AssertionError(
                    "fixed partyhat HEAD publication id="+
                    fixedHat+
                    " br0="+
                    parsed.appearance[0]
                );
        }

        System.out.println(
            "APPEARANCE_ROLE_HEAD_OVERRIDE_CLIENT_PASS "+
            "aC=45 adminWornBr0="+wornParsed.appearance[0]+
            " adminOverrideBs="+overrideParsed.extraAppearanceItem+
            " overrideHeadGate=false "+
            "fixedModWealthyTevinsHead=true "+
            "ctDerived=false"
        );
    }

    private static Parsed parse(
        byte[] data
    )throws Exception{
        Class<?> playerClass=
            Class.forName(
                "rs.a.k"
            );
        Class<?> bufferClass=
            Class.forName(
                "rs.x.e"
            );

        Object player=
            playerClass
                .getConstructor()
                .newInstance();

        Object buffer=
            bufferClass
                .getConstructor(
                    byte[].class
                )
                .newInstance(
                    (Object)data
                );

        Method parse=
            playerClass.getMethod(
                "a",
                bufferClass
            );

        parse.invoke(
            player,
            buffer
        );

        Field pos=
            bufferClass.getField(
                "h"
            );

        Method displayName=
            playerClass.getMethod(
                "o"
            );

        return new Parsed(
            pos.getInt(buffer),
            playerClass
                .getField("aY")
                .getInt(player),
            playerClass
                .getField("aC")
                .getInt(player),
            ((int[])playerClass
                .getField("br")
                .get(player))
                .clone(),
            playerClass
                .getField("bs")
                .getInt(player),
            ((int[])playerClass
                .getField("aV")
                .get(player))
                .clone(),
            String.valueOf(
                displayName.invoke(
                    player
                )
            )
        );
    }

    private static final class Parsed {
        final int consumed;
        final int gender;
        final int appearanceRole;
        final int[] appearance;
        final int extraAppearanceItem;
        final int[] colours;
        final String name;

        Parsed(
            int consumed,
            int gender,
            int appearanceRole,
            int[] appearance,
            int extraAppearanceItem,
            int[] colours,
            String name
        ){
            this.consumed=consumed;
            this.gender=gender;
            this.appearanceRole=appearanceRole;
            this.appearance=appearance;
            this.extraAppearanceItem=extraAppearanceItem;
            this.colours=colours;
            this.name=name;
        }
    }
}
