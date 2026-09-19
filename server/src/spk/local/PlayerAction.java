package spk.local;

/** Exact current-client player-menu action after S2C104 assigns slots 1..5. */
final class PlayerAction {
    final int opcode;
    final int optionSlot;
    final int playerIndex;
    final String semantic;
    PlayerAction(int opcode,int optionSlot,int playerIndex,String semantic){
        this.opcode=opcode;this.optionSlot=optionSlot;this.playerIndex=playerIndex;this.semantic=semantic;
    }
    public String toString(){return "PlayerAction{opcode="+opcode+", option="+optionSlot+", playerIndex="+playerIndex+", semantic="+semantic+"}";}
}
