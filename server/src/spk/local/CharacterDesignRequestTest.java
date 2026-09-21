package spk.local;

import java.util.Arrays;

public final class CharacterDesignRequestTest {
    public static void main(String[] args){
        byte[] male={
            0,
            0,10,18,26,33,36,42,
            0,1,2,3,4
        };
        CharacterDesignRequest m=CharacterDesignRequest.decode(male);
        if(!m.valid())throw new AssertionError("male request invalid: "+m);
        if(m.gender()!=CharacterDesignProfile.MALE)throw new AssertionError("male gender");
        if(!Arrays.equals(m.kits(),new int[]{0,10,18,26,33,36,42}))
            throw new AssertionError("male kits "+Arrays.toString(m.kits()));
        if(!Arrays.equals(m.colours(),new int[]{0,1,2,3,4}))
            throw new AssertionError("male colours "+Arrays.toString(m.colours()));

        byte[] female={
            1,
            45,(byte)255,56,61,67,70,79,
            11,15,15,5,23
        };
        CharacterDesignRequest f=CharacterDesignRequest.decode(female);
        if(!f.valid())throw new AssertionError("female request invalid: "+f);
        if(f.kits()[1]!=-1)throw new AssertionError("female jaw wire255 not normalized");

        byte[] invalidMale=male.clone();
        invalidMale[2]=(byte)255;
        if(CharacterDesignRequest.decode(invalidMale).valid())
            throw new AssertionError("male jaw -1 accepted");

        byte[] invalidColour=female.clone();
        invalidColour[8]=12;
        if(CharacterDesignRequest.decode(invalidColour).valid())
            throw new AssertionError("hair colour 12 accepted");

        boolean shortRejected=false;
        try{CharacterDesignRequest.decode(new byte[12]);}
        catch(IllegalArgumentException expected){shortRejected=true;}
        if(!shortRejected)throw new AssertionError("short payload accepted");

        System.out.println(
            "CHARACTER_DESIGN_REQUEST_PASS fixed13=true gender01=true femaleJaw255ToMinus1=true rangesFailClosed=true"
        );
    }
}
