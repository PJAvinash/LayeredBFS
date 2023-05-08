import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;


public class Node {
    private int uid;
    private String hostName;
    private int port;
    private int parentUID;
    private int roundNumber;
    private NodeState state;
    private boolean layeredBFSComplete = false;
    private boolean testingMode = false;
    private boolean parentFound = false;
    private boolean isRoot = false;
    private ArrayList<AdjNode> adjacentNodes = new ArrayList<AdjNode>();

    private List<Message> messageQueue = Collections.synchronizedList(new ArrayList<Message>());
    private boolean logging = true;
    
    public Node(int uid,String hostName,int port,boolean isRoot){
        this.uid = uid;
        this.hostName = hostName;
        this.port = port;
        this.roundNumber = 0;
        this.state = NodeState.FINDPARENT;
        this.layeredBFSComplete = false;
        this.parentFound = false;
        this.isRoot = isRoot;
        this.parentUID = uid;
    }
    public void addNeighbor(int uid,String hostName,int port){
        this.adjacentNodes.add(new AdjNode(uid,hostName,port));
    }

    public synchronized void addMessage(Message inputMessage){
        this.messageQueue.add(inputMessage);
    }
    public NodeState getState(){
        return this.state;
    }
    private void setState(NodeState updateState){
        switch (this.state) {
            case FINDPARENT:
                if (updateState == NodeState.FINDCHILDREN) {
                    this.state = updateState;
                }
                break;
            case FINDCHILDREN:
                if (updateState == NodeState.WAIT_FOR_ACK) {
                    this.state = updateState;
                }
                break;
            case WAIT_FOR_ACK:
                 if(updateState == NodeState.RELAY_BROADCAST){
                    this.state = updateState;
                 }
                 break;
            case RELAY_BROADCAST:
                if (updateState == NodeState.RELAY_CONVERGECAST) {
                    this.state = updateState;
                }
                break;
            case RELAY_CONVERGECAST:
                if (updateState == NodeState.RELAY_BROADCAST) {
                    this.state = updateState;
                }
                break;
        }
    }

    private void updateEdgeType(int uid, EdgeType edgeType) {
        for (AdjNode t : this.adjacentNodes) {
            if (uid == t.getUID()){
                t.setEdgeType(edgeType);
            }
        }
    }

    private void setParent(int parentUID){
        if(!this.parentFound && this.adjacentNodes.stream().filter(t -> t.getUID() == parentUID ).count()> 0){
            this.parentUID = parentUID;
            this.parentFound = true;
            this.updateEdgeType(this.parentUID,EdgeType.PARENT);
        }
    }

    public void transitionRoot(){
        switch (this.state) {
            case FINDPARENT:
                this.setState(NodeState.FINDCHILDREN);
                this.transitionRoot();
                break;
            case FINDCHILDREN:
                this.sendChildRequest();
                this.setState(NodeState.WAIT_FOR_ACK);
                break;
            case WAIT_FOR_ACK:
                List<Message> ackMessages = this.messageQueue.stream().filter(t -> (t.mtype == MessageType.POSACK || t.mtype == MessageType.NEGACK)).collect(Collectors.toList());
                if (this.adjacentNodes.stream().filter(t -> t.getEdgeType() == EdgeType.NEIGHBOR).map(t -> t.getUID()).collect(Collectors.toSet()).equals(ackMessages.stream().map(t -> t.from).collect(Collectors.toSet()))) {
                    ackMessages.stream().filter(t -> t.mtype == MessageType.POSACK).forEach(t -> this.updateEdgeType(t.from, EdgeType.CHILDREN));
                    ackMessages.stream().filter(t -> t.mtype == MessageType.NEGACK).forEach(t -> this.updateEdgeType(t.from, EdgeType.NEIGHBOR_REJECT));
                    this.messageQueue.removeAll(ackMessages);
                    this.setState(NodeState.RELAY_BROADCAST);
                    this.transitionRoot();
                }
                break;
            case RELAY_BROADCAST:
                this.sendChildGo();
                this.setState(NodeState.RELAY_CONVERGECAST);
                break;
            case RELAY_CONVERGECAST:
                List<Message> convergeCastMessages = this.messageQueue.stream().filter(t -> (t.mtype == MessageType.SAFE || t.mtype == MessageType.COMPLTE)&& t.roundNumber == this.roundNumber).collect(Collectors.toList());
                if (convergeCastMessages.stream().map(t -> t.from).collect(Collectors.toSet()).equals(this.adjacentNodes.stream().filter(t -> t.getEdgeType() == EdgeType.CHILDREN).map(t -> t.getUID()).collect(Collectors.toSet()))) {
                    convergeCastMessages.stream().filter(t -> t.mtype == MessageType.COMPLTE).forEach(t -> this.updateEdgeType(t.from, EdgeType.CHILDREN_COMPLTE));
                    if (convergeCastMessages.stream().filter(t -> t.mtype == MessageType.SAFE).count() > 0) {
                        this.setState(NodeState.RELAY_BROADCAST);
                    } else {
                        // print adjacent layers.
                        this.printAdjacent();
                    }
                    this.roundNumber = this.roundNumber + 1;
                    this.messageQueue.removeAll(convergeCastMessages);
                }
                break;
        }

    }
    public void transitionNonRoot() {
        this.sendNegAck();
        switch(this.state){
            case FINDPARENT:
            // listen for parent
            // set the first request as parent and send the posak
            // change state to FINDCHILDREM
            List <Message> childRequests = this.messageQueue.stream().filter(t -> t.mtype == MessageType.CHILDREQUEST).collect(Collectors.toList());
            if(childRequests.size() > 0){
                this.roundNumber = childRequests.get(0).roundNumber;
                this.setParent(childRequests.get(0).from);
                this.messageQueue.remove(childRequests.get(0));
                this.sendPosAck();
                this.sendNegAck();
                this.setState(NodeState.FINDCHILDREN);
            }
            break;
            case FINDCHILDREN:
            //listen from parent
            //send child requests to all neighbours;
            //change to wait for ack
            List<Message> goMessages = this.messageQueue.stream().filter(t -> t.mtype == MessageType.GO && t.roundNumber == this.roundNumber).collect(Collectors.toList());
            if(goMessages.size() > 0){
                this.sendChildRequest();
                this.setState(NodeState.WAIT_FOR_ACK);
                this.messageQueue.removeAll(goMessages);
            }
            break;
            case WAIT_FOR_ACK:
            //wait for acks 
            //update all list of children
            //send safe to parent change state to relay broad cast
            List<Message> ackMessages = this.messageQueue.stream().filter(t -> (t.mtype == MessageType.POSACK ||t.mtype ==  MessageType.NEGACK)).collect(Collectors.toList());
            if(this.adjacentNodes.stream().filter(t -> t.getEdgeType() == EdgeType.NEIGHBOR).map(t -> t.getUID()).collect(Collectors.toSet()).equals(ackMessages.stream().map(t -> t.from).collect(Collectors.toSet()))){
                ackMessages.stream().filter(t -> t.mtype == MessageType.POSACK).forEach(t -> this.updateEdgeType(t.from, EdgeType.CHILDREN));
                ackMessages.stream().filter(t -> t.mtype == MessageType.NEGACK).forEach(t -> this.updateEdgeType(t.from, EdgeType.NEIGHBOR_REJECT));
                this.sendParentSafe();
                this.setState(NodeState.RELAY_BROADCAST);
                this.messageQueue.removeAll(ackMessages);
            }
            break;
            case RELAY_BROADCAST:
            //if root send GO to all children
            // listen for parent GO at new round number
            // send GO to all Children
            // if there are no children send complete message parent.
            // change state to relay converge cast;
            List<Message> goMessagesRelay = this.messageQueue.stream().filter(t -> t.mtype == MessageType.GO && t.roundNumber == (this.roundNumber+1) && t.from == this.parentUID).collect(Collectors.toList());
            if(goMessagesRelay.size() > 0){
                if(this.adjacentNodes.stream().filter(t -> t.getEdgeType() == EdgeType.CHILDREN).count() > 0){
                    this.sendChildGo();
                }else{
                    this.sendParentComplete();
                    //print adjacent
                }
                this.roundNumber = this.roundNumber+1;
                this.setState(NodeState.RELAY_CONVERGECAST);
                this.messageQueue.removeAll(goMessagesRelay);
            }
            break;
            case RELAY_CONVERGECAST:
            //if root and all the chidren are not complete send a go, else algo is complete
            // if there are no children set algo is complete. 
            // if there are children  wait until all the children reply safe or complete. 
            // once all the children send safe or complete reply to parent safe or complete
            List<Message> convergeCastMessages = this.messageQueue.stream().filter(t -> (t.mtype == MessageType.SAFE || t.mtype == MessageType.COMPLTE) && t.roundNumber == this.roundNumber).collect(Collectors.toList());
            if(convergeCastMessages.stream().map(t -> t.from).collect(Collectors.toSet()).equals(this.adjacentNodes.stream().filter(t -> t.getEdgeType() == EdgeType.CHILDREN).map(t -> t.getUID()).collect(Collectors.toSet()))){
                convergeCastMessages.stream().filter(t-> t.mtype == MessageType.COMPLTE).forEach(t -> this.updateEdgeType(t.from, EdgeType.CHILDREN_COMPLTE));
                if(convergeCastMessages.stream().filter(t-> t.mtype == MessageType.SAFE).count() > 0){
                    this.sendParentSafe();
                    this.setState(NodeState.RELAY_BROADCAST);
                    // print adjacent layers.
                }else{
                    this.sendParentComplete();
                }
                this.messageQueue.removeAll(convergeCastMessages);
            }
            break;
        }
    }
    public void transition(){
        if(messageQueue.size() > 0){
            this.consolelog(" message :" + messageQueue.get(messageQueue.size() -1 ).toString());
        }
        if(this.isRoot){
            this.transitionRoot();
        }else{
            this.transitionNonRoot();
        }   
    }

    public void startLayeredBFS() throws IOException{
        this.startListening();
    }

    private void printAdjacent(){
        System.out.println("All Children nodes in BFS Tree are determined .adjacent nodes for : " + this.uid + "  parent: " + this.parentUID);
        this.adjacentNodes.stream().filter(t -> t.getEdgeType() == EdgeType.CHILDREN_COMPLTE).forEach(t-> System.out.println(this.uid+":    ("+t.getUID()+" : "+t.getHostname()+")    "));

    }

    public void sendChildRequest(){
        Message childReqMessage = new Message(this.uid,MessageType.CHILDREQUEST, this.roundNumber);
        this.adjacentNodes.stream().filter(t -> t.getEdgeType() == EdgeType.NEIGHBOR).forEach(t -> this.sendMessage(childReqMessage, t.getUID()));

    }
    public void sendChildGo(){
        Message childGoMessage = new Message(this.uid,MessageType.GO,this.roundNumber);
        this.adjacentNodes.stream().filter(t -> t.getEdgeType() == EdgeType.CHILDREN).forEach(t -> this.sendMessage(childGoMessage, t.getUID()));

    }
    public void sendParentSafe(){
        Message safeMessage = new Message(this.uid,MessageType.SAFE,this.roundNumber);
        this.sendMessage(safeMessage, this.parentUID);

    }
    public void sendParentComplete(){
        Message completeMessage = new Message(this.uid,MessageType.COMPLTE,this.roundNumber);
        this.sendMessage(completeMessage, this.parentUID);
    }

    public void sendPosAck(){
        if(this.parentFound){
            Message posAckMessage = new Message(this.uid,MessageType.POSACK,this.roundNumber);
            this.sendMessage(posAckMessage, this.parentUID);
        }
    }
    public void sendNegAck(){
        if(this.parentFound){
            Message NegAckMessage = new Message(this.uid,MessageType.NEGACK,this.roundNumber);
            List <Message> childRequests = this.messageQueue.stream().filter(t -> t.mtype == MessageType.CHILDREQUEST && t.from != parentUID).collect(Collectors.toList());
            childRequests.stream().forEach(t -> this.sendMessage(NegAckMessage, t.from));
            this.messageQueue.removeAll(childRequests);
        }
    }

    public void startListening() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        this.transition();
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            while (!this.layeredBFSComplete) {
                Socket clientSocket = serverSocket.accept();
                executor.execute(() -> {
                    try (ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream())) {
                        Message message;
                        while (clientSocket.isConnected() && !clientSocket.isClosed() && (message = (Message) input.readObject()) != null) {
                            this.addMessage(message);
                            this.transition();
                        }
                    } catch (EOFException e) {
                        // Stream ended normally, do nothing
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            clientSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } finally {
            serverSocket.close();
            executor.shutdown();
        }
    }

    public void sendMessageTCP(Message message, String host, int port) throws IOException {
        int retryInterval = 5000;
        int maxRetries = 5;
        int retries = 0;
        while (retries <= maxRetries) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), retryInterval);
                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                output.writeObject(message);
                output.flush();
                return;
            } catch (IOException e) {
                retries++;
                if (retries > maxRetries) {
                    throw e; // throw the exception if max retries have been reached
                }
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private void sendMessage(Message inputMessage,int targetUID){
        //logic to send message
        this.adjacentNodes.stream().filter(t-> t.getUID() == targetUID).forEach(t -> {
            try {
                if(!this.testingMode){
                    this.sendMessageTCP(inputMessage,t.getHostname(),t.getPort());
                }
            } catch (IOException e) {
                //e.printStackTrace();
            }
        });
    }

    private void consolelog(String msg){
        if(this.logging){
            System.out.println("uid:"+this.uid+" state:"+this.state.toString() + " : "+msg);
        }
    }
}
