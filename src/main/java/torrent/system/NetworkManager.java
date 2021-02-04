package torrent.system;

import torrent.Torr2;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkManager {
    private final int nodePort;
    private final String hubIP;
    private final int hubPort;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final TorrentSystem torrentSystem;
    private final String owner;
    private final int ownerIndex;

    public NetworkManager(String owner, int ownerIndex, int nodePort, String hubIP, int hubPort) {
        this.owner = owner;
        this.ownerIndex = ownerIndex;
        this.nodePort = nodePort;
        this.hubIP = hubIP;
        this.hubPort = hubPort;
        torrentSystem = new TorrentSystem(owner, ownerIndex, nodePort, hubIP, hubPort);

        // send a registration request
        Torr2.Message registrationResponse = sendRegistrationRequest();

        // hub is probably offline or corrupt, since we received no response or response of different type
        if (registrationResponse == null || !Torr2.Message.Type.REGISTRATION_RESPONSE.equals(registrationResponse.getType())) {
            System.out.println("Could not connect to the hub for node " + ownerIndex + ". Hub may be offline.");
            return;
        }

        // if the registration response doesn't have the SUCCESS status, we stop
        if (!Torr2.Status.SUCCESS.equals(registrationResponse.getRegistrationResponse().getStatus())) {
            System.out.println("Could not connect to the hub for node "
                    + ownerIndex
                    + ". Error message: "
                    + registrationResponse.getRegistrationResponse().getErrorMessage());
            return;
        }

        // if we registered to the hub successfully, we start listening for potential messages
        start(nodePort);
    }

    public static List<Torr2.NodeId> sendSubnetRequest(int subnetId, String hubIP, int hubPort) {
        // do a subnet request
        Torr2.Message subnetResponse = sendRequest(
                Torr2.Message.newBuilder()
                        .setType(Torr2.Message.Type.SUBNET_REQUEST)
                        .setSubnetRequest(Torr2.SubnetRequest.newBuilder()
                                .setSubnetId(subnetId)
                                .build())
                        .build(),
                hubIP, hubPort);

        // if we received a correct subnet response (non-null, correct type, SUCCESS status) we return the nodes list
        if (subnetResponse != null
                && Torr2.Message.Type.SUBNET_RESPONSE.equals(subnetResponse.getType())
                && Torr2.Status.SUCCESS.equals(subnetResponse.getSubnetResponse().getStatus())) {
            return subnetResponse.getSubnetResponse().getNodesList();
        }

        // otherwise we return null
        return null;
    }

    /**
     * Sends a request to the given destination. Returns the response message.
     *
     * @param request
     * @param destinationIP
     * @param destinationPort
     */
    public static Torr2.Message sendRequest(Torr2.Message request, String destinationIP, int destinationPort) {
        try {
            Socket socket = new Socket(destinationIP, destinationPort);
            sendMessageOnSocket(request, socket);
            Torr2.Message response = readMessageFromSocket(socket);
            socket.close();
            return response;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * When the node starts, a registration request is sent to the hub, so that the hub
     * is aware of this node.
     *
     * @return
     */
    private Torr2.Message sendRegistrationRequest() {
        Torr2.Message registrationRequest = Torr2.Message.newBuilder()
                .setType(Torr2.Message.Type.REGISTRATION_REQUEST)
                .setRegistrationRequest(Torr2.RegistrationRequest.newBuilder()
                        .setOwner(owner)
                        .setIndex(ownerIndex)
                        .setPort(nodePort)
                        .build())
                .build();
        return NetworkManager.sendRequest(registrationRequest, hubIP, hubPort);
    }

    private static Torr2.Message readMessageFromSocket(Socket socket) {
        try {
            DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());

            // first 4 bytes: length of the message
            // the rest: the actual message
            int length = dataInputStream.readInt();
            if (length > 0) {
                byte[] messageBytes = new byte[length];
                dataInputStream.readFully(messageBytes, 0, messageBytes.length);
                return Torr2.Message.parseFrom(messageBytes);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void sendMessageOnSocket(Torr2.Message message, Socket socket) {
        try {
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(messageToByteArray(message));
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Prepares a message by converting it to a binary array with its length in the first 4 bytes,
     * followed by the actual message in byte array form.
     *
     * @param message
     * @return
     */
    private static byte[] messageToByteArray(Torr2.Message message) {
        byte[] messageBytes = message.toByteArray();
        return ByteBuffer.allocate(Integer.BYTES + messageBytes.length)
                .putInt(messageBytes.length).put(messageBytes).array();
    }

    /**
     * Starts a server socket that listens for any incoming messages, processing them as they come and sending
     * back a response.
     *
     * @param nodePort
     */
    private void start(int nodePort) {
        try {
            ServerSocket serverSocket = new ServerSocket(nodePort);
            executorService.execute(() -> {
                while (true) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        executorService.execute(() -> {
                            try {
                                Torr2.Message request = readMessageFromSocket(clientSocket);
                                Torr2.Message response = torrentSystem.trigger(request);
                                sendMessageOnSocket(response, clientSocket);
                                clientSocket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}