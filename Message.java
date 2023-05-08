package LayeredBFS;

import java.io.Serializable;

public class Message implements Serializable{
    int roundNumber;
    int from;
    MessageType mtype;
    public Message(int from, MessageType mtype,int roundNumber){
        this.from = from;
        this.mtype = mtype;
        this.roundNumber = roundNumber;
    }
}