import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        String filePath = args[0];
        int root = Integer.parseInt(args[1]);
        Node processingNode = ReadConfig.read(filePath,184);
        processingNode.startLayeredBFS();     
    }
}