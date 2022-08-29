package com.gmail.inayakitorikhurram.fdmc;

import net.minecraft.util.Identifier;

public class FDMCConstants {

    public static Identifier MOVE_PLAYER_ID = new Identifier("fdmc:move_player");
    public static int STEP_DISTANCE = 4096;
    public static int CHUNK_STEP_DISTANCE = (int)Math.floor(STEP_DISTANCE/16f);


}
