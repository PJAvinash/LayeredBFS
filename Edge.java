public class Edge {
    private int source;
    private int destination;
    public Edge(int node1, int node2) {
        this.source = Math.min(node1,node2);
        this.destination = Math.max(node1,node2);;
    }
    public int getSource() {
        return source;
    }
    public int getDestination() {
        return destination;
    }
    public int getOtherEnd(int end1){
        return (end1==source)? destination:source;
    }
}
