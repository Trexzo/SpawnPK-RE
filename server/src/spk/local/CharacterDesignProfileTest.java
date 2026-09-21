package spk.local;

import java.util.Arrays;

public final class CharacterDesignProfileTest {
    public static void main(String[] args){
        int[] male=CharacterDesignProfile.defaultKits(CharacterDesignProfile.MALE);
        int[] female=CharacterDesignProfile.defaultKits(CharacterDesignProfile.FEMALE);

        if(!Arrays.equals(male,new int[]{0,10,18,26,33,36,42}))
            throw new AssertionError("male defaults "+Arrays.toString(male));
        if(!Arrays.equals(female,new int[]{45,-1,56,61,67,70,79}))
            throw new AssertionError("female defaults "+Arrays.toString(female));

        int[][] expectedMale={
            {0,1,2,3,4,5,6,7,8},
            {10,11,12,13,14,15,16,17},
            {18,19,20,21,22,23,24,25},
            {26,27,28,29,30,31},
            {33,34},
            {36,37,38,39,40},
            {42,43}
        };
        int[][] expectedFemale={
            {45,46,47,48,49,50,51,52,53,54},
            {-1},
            {56,57,58,59,60},
            {61,62,63,64,65},
            {67,68},
            {70,71,72,73,74,75,76,77},
            {79,80}
        };

        for(int i=0;i<7;i++){
            if(!Arrays.equals(
                    CharacterDesignProfile.allowedKits(CharacterDesignProfile.MALE,i),
                    expectedMale[i]))
                throw new AssertionError("male category "+i);
            if(!Arrays.equals(
                    CharacterDesignProfile.allowedKits(CharacterDesignProfile.FEMALE,i),
                    expectedFemale[i]))
                throw new AssertionError("female category "+i);
        }

        int[] colourCounts={12,16,16,6,24};
        for(int i=0;i<colourCounts.length;i++)
            if(CharacterDesignProfile.colourCount(i)!=colourCounts[i])
                throw new AssertionError("colour count "+i);

        System.out.println(
            "CHARACTER_DESIGN_PROFILE_PASS maleSets=7 femaleSets=7 femaleJawNone=true colourCounts=[12,16,16,6,24]"
        );
    }
}
