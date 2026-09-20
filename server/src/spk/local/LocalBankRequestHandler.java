package spk.local;

import java.io.IOException;

/**
 * Bank/trade request orchestration extracted from LocalSession.
 *
 * Raw packet decoding remains in ClientPacketProbe/LocalSession. This handler
 * consumes already-decoded values and delegates to the existing domain state.
 */
final class LocalBankRequestHandler {
    private final WorldPlayer worldPlayer;
    private final BankState bank;

    LocalBankRequestHandler(WorldPlayer worldPlayer,BankState bank){
        this.worldPlayer=java.util.Objects.requireNonNull(worldPlayer,"worldPlayer");
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
    }

    Result handleAmount(int amount,ServerPacketWriter serverPackets)throws IOException{
        String trade=TradeService.handleAmount(worldPlayer,amount);
        if(trade!=null){
            return new Result(
                "V5140_TRADE_AMOUNT opcode=208 amount="+amount+" result="+trade,
                null
            );
        }

        String result=bank.applyAmount(amount,serverPackets);
        return new Result(
            "V522_BANK_AMOUNT opcode=208 amount="+amount+" result="+result,
            "BANK_AMOUNT"
        );
    }

    Result handleDrag(ContainerDrag drag,ServerPacketWriter serverPackets)throws IOException{
        String result=bank.applyDrag(drag,serverPackets);
        String saveReason=
            drag.widgetId==BankState.NORMAL_INVENTORY_CONTAINER
                ?"INVENTORY_DRAG"
                :"BANK_DRAG";
        return new Result(
            "V561_CONTAINER_DRAG "+drag+" result="+result,
            saveReason
        );
    }

    Result handleCommand(String command,ServerPacketWriter serverPackets)throws IOException{
        String result=bank.applyCommand(command,serverPackets);
        if("IGNORED_NON_BANK_COMMAND".equals(result))return null;

        return new Result(
            "V522_BANK_COMMAND command="+command+" result="+result,
            "BANK_COMMAND"
        );
    }

    static final class Result {
        final String logText;
        final String saveReason;

        Result(String logText,String saveReason){
            this.logText=logText;
            this.saveReason=saveReason;
        }
    }
}
