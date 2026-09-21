package spk.local;

import java.util.Arrays;

/** Typed semantic form of exact-current C2S101 character-design submit. */
final class CharacterDesignRequest {
    static final int WIRE_LENGTH=13;

    private final int gender;
    private final int[] kits;
    private final int[] colours;

    CharacterDesignRequest(
        int gender,
        int[] kits,
        int[] colours
    ){
        this.gender=gender;
        this.kits=kits==null?null:kits.clone();
        this.colours=colours==null?null:colours.clone();
    }

    static CharacterDesignRequest decode(byte[] body){
        if(body==null||body.length!=WIRE_LENGTH)
            throw new IllegalArgumentException(
                "C2S101 len="+(body==null?-1:body.length)
            );

        int gender=body[0]&255;
        int[] kits=new int[CharacterDesignProfile.KIT_COUNT];
        int[] colours=new int[CharacterDesignProfile.COLOUR_COUNT];

        for(int i=0;i<kits.length;i++){
            int raw=body[1+i]&255;
            kits[i]=raw==255?-1:raw;
        }

        for(int i=0;i<colours.length;i++)
            colours[i]=body[8+i]&255;

        return new CharacterDesignRequest(
            gender,
            kits,
            colours
        );
    }

    int gender(){return gender;}
    int[] kits(){return kits.clone();}
    int[] colours(){return colours.clone();}

    boolean valid(){
        return CharacterDesignProfile.valid(
            gender,
            kits,
            colours
        );
    }

    @Override public String toString(){
        return "CharacterDesignRequest{gender="+
            gender+
            ",kits="+Arrays.toString(kits)+
            ",colours="+Arrays.toString(colours)+
            ",valid="+valid()+
            "}";
    }
}
