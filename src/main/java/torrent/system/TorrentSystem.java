package torrent.system;

import com.google.protobuf.ByteString;
import torrent.Torr2;
import torrent.abstractions.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TorrentSystem {
    private static final int CHUNK_SIZE = 1024;

    private final List<Abstraction> abstractionList = new ArrayList<>();
    private final Map<ByteString, File> fileList = new HashMap<>();
    private final String hubIP;
    private final int hubPort;
    private Torr2.NodeId currentNode;

    public TorrentSystem(String owner, int ownerIndex, int nodePort, String hubIP, int hubPort) {
        this.hubIP = hubIP;
        this.hubPort = hubPort;

        currentNode = Torr2.NodeId.newBuilder()
                .setHost(hubIP)
                .setPort(nodePort)
                .setOwner(owner)
                .setIndex(ownerIndex)
                .build();

        abstractionList.add(new DownloadAbstraction(this));
        abstractionList.add(new UploadAbstraction(this));
        abstractionList.add(new LocalSearchAbstraction(this));
        abstractionList.add(new SearchAbstraction(this));
        abstractionList.add(new ChunkAbstraction(this));
        abstractionList.add(new ReplicateAbstraction(this));
    }

    /**
     * Handles a request message, by passing it along to every abstraction.
     * When a request is handled, its response is returned as a message.
     * <p>
     * If an abstraction cannot handle a request, the abstraction's 'handle' method returns null,
     * and the request is passed along to the next abstraction for an attempted handling.
     * <p>
     * If no abstractions can handle the given request, this method returns null (should never happen,
     * given that there are abstractions implemented for every message type).
     *
     * @param requestMessage
     */
    public Torr2.Message trigger(Torr2.Message requestMessage) {
        for (Abstraction abstraction : abstractionList) {
            Torr2.Message responseMessage = abstraction.handle(requestMessage);
            if (responseMessage != null) {
                return responseMessage;
            }
        }
        return null;
    }

    public Torr2.NodeId getCurrentNode() {
        return currentNode;
    }

    public Map<ByteString, File> getFileList() {
        return fileList;
    }

    /**
     * Hashes the given byte string using the MD5 algorithm. Returns the hash.
     *
     * @param bytesToHash
     * @return
     */
    public ByteString hashBytes(final ByteString bytesToHash) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(bytesToHash.toByteArray());
            byte[] hashedBytes = messageDigest.digest();
            return ByteString.copyFrom(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * This method is used to parse a file data (as a byte array) to a chunk list.
     *
     * @param fileData
     * @return
     */
    public List<Chunk> parseFileData(ByteString fileData) {
        List<Chunk> chunks = new ArrayList<>();

        // determine the chunk count in the given data
        // (dividing it to 1024 and adding 1 if we have a smaller chunk at the end)
        int chunkCount = fileData.size() / CHUNK_SIZE + (fileData.size() % CHUNK_SIZE > 0 ? 1 : 0);

        // cut the data into chunks and store them
        int chunkOffset = 0;
        for (int i = 0; i < chunkCount; i++) {
            // check that we are not at the final chunk, which could be shorter
            int endOffset = chunkOffset + CHUNK_SIZE;
            if (endOffset > fileData.size()) {
                endOffset = fileData.size();
            }

            // extract the data of the current chunk from the file data
            ByteString chunkData = fileData.substring(chunkOffset, endOffset);

            // create the chunk and store it
            chunks.add(new Chunk(Torr2.ChunkInfo.newBuilder()
                    .setIndex(i)
                    .setSize(chunkData.size())
                    .setHash(hashBytes(chunkData))
                    .build(),
                    chunkData));

            // move to the next chunk
            chunkOffset += CHUNK_SIZE;
        }

        return chunks;
    }

    public List<Torr2.NodeId> sendSubnetRequest(int subnetId) {
        return NetworkManager.sendSubnetRequest(subnetId, hubIP, hubPort);
    }

    public Torr2.Message sendLocalSearchRequest(Torr2.NodeId node, String regex) {
        // build the local search request with the given regex
        Torr2.Message localSearchRequest = Torr2.Message.newBuilder()
                .setType(Torr2.Message.Type.LOCAL_SEARCH_REQUEST)
                .setLocalSearchRequest(Torr2.LocalSearchRequest.newBuilder()
                        .setRegex(regex)
                        .build())
                .build();

        // for the current node, process the local search through a trigger
        if (node.getPort() == currentNode.getPort()) {
            return trigger(localSearchRequest);
        }

        // for the other nodes, send a request over the network
        return NetworkManager.sendRequest(localSearchRequest, node.getHost(), node.getPort());
    }

    public Torr2.Message sendChunkRequest(Torr2.FileInfo fileInfo, Torr2.ChunkInfo chunkInfo, Torr2.NodeId nodeId) {
        return NetworkManager.sendRequest(
                Torr2.Message.newBuilder()
                        .setType(Torr2.Message.Type.CHUNK_REQUEST)
                        .setChunkRequest(Torr2.ChunkRequest.newBuilder()
                                .setFileHash(fileInfo.getHash())
                                .setChunkIndex(chunkInfo.getIndex())
                                .build())
                        .build(),
                nodeId.getHost(), nodeId.getPort());
    }
}
