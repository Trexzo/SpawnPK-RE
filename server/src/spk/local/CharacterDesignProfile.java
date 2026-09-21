package spk.local;

import java.util.Arrays;

/** Exact-current v308 character-design option domain recovered from idk.dat/client bytecode. */
final class CharacterDesignProfile {
    static final int MALE=0;
    static final int FEMALE=1;
    static final int KIT_COUNT=7;
    static final int COLOUR_COUNT=5;

    private static final int[][] MALE_KITS={
        range(0,8),
        range(10,17),
        range(18,25),
        range(26,31),
        new int[]{33,34},
        range(36,40),
        new int[]{42,43}
    };

    private static final int[][] FEMALE_KITS={
        range(45,54),
        new int[]{-1},
        range(56,60),
        range(61,65),
        new int[]{67,68},
        range(70,77),
        new int[]{79,80}
    };

    private static final int[] COLOUR_COUNTS={12,16,16,6,24};

    static int[] defaultKits(int gender){
        requireGender(gender);
        int[][] source=gender==MALE?MALE_KITS:FEMALE_KITS;
        int[] out=new int[KIT_COUNT];
        for(int i=0;i<KIT_COUNT;i++)out[i]=source[i][0];
        return out;
    }

    static int[] defaultColours(){
        return new int[COLOUR_COUNT];
    }

    static boolean valid(
        int gender,
        int[] kits,
        int[] colours
    ){
        if(gender!=MALE&&gender!=FEMALE)return false;
        if(kits==null||kits.length!=KIT_COUNT)return false;
        if(colours==null||colours.length!=COLOUR_COUNT)return false;

        int[][] allowed=gender==MALE?MALE_KITS:FEMALE_KITS;
        for(int i=0;i<KIT_COUNT;i++)
            if(!contains(allowed[i],kits[i]))
                return false;

        for(int i=0;i<COLOUR_COUNT;i++)
            if(colours[i]<0||colours[i]>=COLOUR_COUNTS[i])
                return false;

        return true;
    }

    static boolean validKit(
        int gender,
        int index,
        int kit
    ){
        requireGender(gender);
        if(index<0||index>=KIT_COUNT)
            throw new IllegalArgumentException("kit index");
        int[][] source=gender==MALE?MALE_KITS:FEMALE_KITS;
        return contains(source[index],kit);
    }

    static int colourCount(int index){
        if(index<0||index>=COLOUR_COUNT)
            throw new IllegalArgumentException("colour index");
        return COLOUR_COUNTS[index];
    }

    static int[] allowedKits(int gender,int index){
        requireGender(gender);
        if(index<0||index>=KIT_COUNT)
            throw new IllegalArgumentException("kit index");
        int[][] source=gender==MALE?MALE_KITS:FEMALE_KITS;
        return source[index].clone();
    }

    private static void requireGender(int gender){
        if(gender!=MALE&&gender!=FEMALE)
            throw new IllegalArgumentException("gender="+gender);
    }

    private static boolean contains(int[] values,int needle){
        for(int value:values)
            if(value==needle)return true;
        return false;
    }

    private static int[] range(int first,int last){
        int[] out=new int[last-first+1];
        for(int i=0;i<out.length;i++)out[i]=first+i;
        return out;
    }

    private CharacterDesignProfile(){}
}
