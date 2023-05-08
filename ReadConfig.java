import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class ReadConfig {
    public static Node read(String filePath,int root) throws UnknownHostException{
        Integer numNodes = 0;
        ArrayList<Integer> uidList = new ArrayList<Integer>();
        ArrayList<String> hostnames = new ArrayList<String>();
        ArrayList<Integer> portnumbers = new ArrayList<Integer>();
        ArrayList<Edge> edges = new ArrayList<Edge>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().startsWith("#") || line.trim().isEmpty()) {
                    // Ignore comments and blank lines
                    continue;
                } else if (line.trim().matches("[0-9]+")) {
                    // Parse number of nodes
                    numNodes = Integer.parseInt(line.trim());
                    System.out.println("Number of nodes: " + numNodes);
                } else if (line.trim().matches("[0-9]+\\s+\\S+\\s+[0-9]+")) {
                    // Parse individual node
                    String[] parts = line.trim().split("\\s+");
                    uidList.add(Integer.parseInt(parts[0]));
                    hostnames.add(parts[1]);
                    portnumbers.add(Integer.parseInt(parts[2]));
                  
                } else if (line.trim().matches("\\([0-9]+,[0-9]+\\)\\s+[0-9]+")) {
                    // Parse edge and weight
                    String[] parts = line.trim().split("\\s+");
                    String[] nodes = parts[0].substring(1, parts[0].length() - 1).split(",");
                    int uid1 = Integer.parseInt(nodes[0]);
                    int uid2 = Integer.parseInt(nodes[1]);
                    edges.add(new Edge(uid1, uid2));
                } else {
                    System.err.println("Invalid line: " + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
         // creating the node
         InetAddress ip = InetAddress.getLocalHost();
         String hostname = ip.getHostName();
         int index =  hostnames.indexOf(hostname);
         int uid = uidList.get(index);
         int port = portnumbers.get(index);
         Node processingNode = new Node(uid,hostname,port,root == uid);
         for(int i = 0; i< edges.size(); i++){
            if(edges.get(i).getSource() == uid || edges.get(i).getDestination() ==uid){
                int otherendUID = edges.get(i).getOtherEnd(uid);
                int otherendINDEX = uidList.indexOf(otherendUID);
                String otherendHostname = hostnames.get(otherendINDEX);
                int otherendPort =  portnumbers.get(otherendINDEX);
                processingNode.addNeighbor(otherendUID,otherendHostname, otherendPort);
            }
        }

        return processingNode;

    }
    
}
