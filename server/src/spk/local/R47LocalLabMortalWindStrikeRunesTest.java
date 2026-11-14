package spk.local;

import java.io.ByteArrayOutputStream;

/** R47 exact air/mind rune plan, no partial charge and default-off controls. */
public final class R47LocalLabMortalWindStrikeRunesTest {
    public static void main(String[] args)throws Exception {
        String r6=System.getProperty("spk.local.r6MortalFixture");
        String r45=System.getProperty("spk.local.r45MortalMagic");
        String r47=System.getProperty("spk.local.r47WindStrikeRunes");
        try{
            System.clearProperty("spk.local.r6MortalFixture");
            System.clearProperty("spk.local.r45MortalMagic");
            System.clearProperty("spk.local.r47WindStrikeRunes");
            SpellDefinitionRepository.Spell wind=
                SpellDefinitionRepository.byWidget(1152);
            if(wind==null||!"Wind strike".equals(wind.name)||
               wind.resources.length!=2||
               wind.resources[0].required!=1||
               wind.resources[1].required!=1)
                throw new AssertionError("client-known Wind strike resource definition drift");
            if(LocalLabMortalWindStrikeRunes.enabled())
                throw new AssertionError("default rune spending enabled");
            System.setProperty("spk.local.r47WindStrikeRunes","true");
            if(LocalLabMortalWindStrikeRunes.enabled())
                throw new AssertionError("r47 without r6/r45 admitted");
            System.setProperty("spk.local.r6MortalFixture","true");
            if(LocalLabMortalWindStrikeRunes.enabled())
                throw new AssertionError("r47 without r45 admitted");
            System.setProperty("spk.local.r45MortalMagic","true");
            if(!LocalLabMortalWindStrikeRunes.enabled())
                throw new AssertionError("r47 exact pairing denied");

            BankState bank=new BankState();
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            ServerPacketWriter writer=new ServerPacketWriter(
                output,new IsaacCipher(new int[]{1,2,3,4}));
            if(LocalLabMortalWindStrikeRunes.plan(bank,wind)!=null)
                throw new AssertionError("rune-free inventory admitted");
            bank.addInventoryAmount(556,2,writer);
            if(LocalLabMortalWindStrikeRunes.plan(bank,wind)!=null)
                throw new AssertionError("missing mind rune admitted");
            bank.addInventoryAmount(558,2,writer);
            LocalLabMortalWindStrikeRunes.Plan cost=
                LocalLabMortalWindStrikeRunes.plan(bank,wind);
            if(cost==null||cost.airSlot==cost.mindSlot)
                throw new AssertionError("valid exact rune stock denied");
            if(bank.inventoryCount(556)!=2||bank.inventoryCount(558)!=2)
                throw new AssertionError("plan should not spend runes");
            int before=output.size();
            cost.consume(bank,writer);
            if(bank.inventoryCount(556)!=1||bank.inventoryCount(558)!=1)
                throw new AssertionError("exact two rune costs not charged");
            if(output.size()<=before)
                throw new AssertionError("no native S2C53 inventory update");
            boolean staleDenied=false;
            // Stale plan must fail before any slot mutation.
            bank.consumeInventoryAmountSemantic(cost.mindSlot,558,1);
            try{ cost.consume(bank,writer); }
            catch(IllegalStateException expected){ staleDenied=true; }
            if(!staleDenied||bank.inventoryCount(556)!=1||
               bank.inventoryCount(558)!=0)
                throw new AssertionError("stale rune plan partially charged");

            System.out.println(
                "R47_WIND_STRIKE_RUNE_COST_TEST_PASS defaultOff=true "+
                "pairingRequired=true bothRunesRequired=true "+
                "singleEachCharged=true inventory53=true stalePlanFailClosed=true");
        }finally{
            reset("spk.local.r6MortalFixture",r6);
            reset("spk.local.r45MortalMagic",r45);
            reset("spk.local.r47WindStrikeRunes",r47);
        }
    }
    private static void reset(String name,String value){
        if(value==null)System.clearProperty(name);
        else System.setProperty(name,value);
    }
}
