public class AdjNode {
    private int uid;
    private String hostname;
    private int port;
    private EdgeType edgeType;
    public AdjNode(int uid,String hostname, int port){
        this.uid = uid;
        this.hostname = hostname;
        this.port = port;
        this.edgeType = EdgeType.NEIGHBOR;
    }
    public int getUID(){
        return this.uid;
    }
    public String getHostname(){
        return this.hostname;
    }
    public int getPort(){
        return this.port;
    }
    public EdgeType getEdgeType(){
        return this.edgeType;
    }
    public void setEdgeType(EdgeType inputEdgeType){
        switch(this.edgeType){
            case NEIGHBOR:
                this.edgeType = inputEdgeType;
                break;
            case PARENT:
                break;
            case CHILDREN:
                if(inputEdgeType == EdgeType.CHILDREN_COMPLTE){
                    this.edgeType = inputEdgeType;
                }
                break;
            default:
                break;
            
        }
    }


    
}
