package torrent;

import torrent.system.NetworkManager;s

public class Main {
    private static final String OWNER = "node";
    private static final int NODE_PORT = 5010;
    private static final int HUB_PORT = 5000;
    private static final String HUB_IP = "localhost";
    private static final int NODE_COUNT = 3;

    public static void main(String[] args) {
        for (int i = 1; i <= NODE_COUNT; i++) {
            new NetworkManager(OWNER, i, NODE_PORT + i, HUB_IP, HUB_PORT);
        }
    }
}
