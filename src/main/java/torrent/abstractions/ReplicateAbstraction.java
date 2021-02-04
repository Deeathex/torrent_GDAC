package torrent.abstractions;

import com.google.protobuf.ByteString;
import torrent.Torr2;
import torrent.system.File;
import torrent.system.NetworkManager;
import torrent.system.TorrentSystem;

import java.util.ArrayList;
import java.util.List;

public class ReplicateAbstraction implements Abstraction {
    private TorrentSystem torrentSystem;

    public ReplicateAbstraction(TorrentSystem torrentSystem) {
        this.torrentSystem = torrentSystem;
    }

    @Override
    public Torr2.Message handle(Torr2.Message requestMessage) {
        if (Torr2.Message.Type.REPLICATE_REQUEST.equals(requestMessage.getType())) {
            return Torr2.Message.newBuilder()
                    .setType(Torr2.Message.Type.REPLICATE_RESPONSE)
                    .setReplicateResponse(handleReplicateRequest(requestMessage.getReplicateRequest()))
                    .build();
        }
        return null;
    }

    private Torr2.ReplicateResponse handleReplicateRequest(Torr2.ReplicateRequest replicateRequest) {
        final Torr2.FileInfo fileInfo = replicateRequest.getFileInfo();
        final int subnetId = replicateRequest.getSubnetId();

        // do some validations on the input
        Torr2.ReplicateResponse.Builder replicateResponse = Torr2.ReplicateResponse.newBuilder();
        if (!validateFileInfo(replicateResponse, fileInfo)) {
            return replicateResponse.build();
        }

        // if we have the file already, we send it
        if (torrentSystem.getFileList().get(fileInfo.getHash()) != null) {
            for (Torr2.ChunkInfo chunkInfo : fileInfo.getChunksList()) {
                replicateResponse.addNodeStatusList(Torr2.NodeReplicationStatus.newBuilder()
                        .setNode(torrentSystem.getCurrentNode())
                        .setChunkIndex(chunkInfo.getIndex())
                        .setStatus(Torr2.Status.SUCCESS)
                        .build());
            }
            replicateResponse.setStatus(Torr2.Status.SUCCESS);
            return replicateResponse.build();
        }

        // otherwise, we try to replicate the chunks from the other nodes
        // first we do a subnet request
        List<Torr2.NodeId> nodeList = torrentSystem.sendSubnetRequest(subnetId);
        if (nodeList == null) {
            replicateResponse.setStatus(Torr2.Status.PROCESSING_ERROR);
            replicateResponse.setErrorMessage("Error on subnet request.");
            return replicateResponse.build();
        }

        // then we do a chunk request on all of the nodes to get all the chunks in the file
        ByteString fileData = ByteString.EMPTY;
        boolean allChunksReplicated = true;
        for (Torr2.ChunkInfo chunkInfo : fileInfo.getChunksList()) {
            boolean currentChunkReplicated = false;
            for (int i = 0; i < nodeList.size(); i++) {
                Torr2.NodeId nodeId = nodeList.get(i);
                // skip over the current node
                if (nodeId.getPort() == torrentSystem.getCurrentNode().getPort()) {
                    continue;
                }

                // send a chunk request
                Torr2.Message chunkResponseMessage = torrentSystem.sendChunkRequest(fileInfo, chunkInfo, nodeId);
                Torr2.NodeReplicationStatus.Builder nodeReplicationStatus = Torr2.NodeReplicationStatus.newBuilder();
                nodeReplicationStatus.setChunkIndex(chunkInfo.getIndex());
                nodeReplicationStatus.setNode(nodeId);

                // validate it
                if (chunkResponseMessage == null) {
                    nodeReplicationStatus.setStatus(Torr2.Status.NETWORK_ERROR);
                    nodeReplicationStatus.setErrorMessage("Cannot establish connection with node.");
                    replicateResponse.addNodeStatusList(nodeReplicationStatus);
                    // when a node didn't respond, we remove it from the list (not going to ask again for other chunks)
                    nodeList.remove(nodeId);
                    i--;
                    continue;
                }
                if (!Torr2.Message.Type.CHUNK_RESPONSE.equals(chunkResponseMessage.getType())) {
                    nodeReplicationStatus.setStatus(Torr2.Status.MESSAGE_ERROR);
                    nodeReplicationStatus.setErrorMessage("The response is not parsable or has the wrong type.");
                    replicateResponse.addNodeStatusList(nodeReplicationStatus);
                    continue;
                }

                // if the received chunk response is valid, we store the received chunk
                Torr2.ChunkResponse chunkResponse = chunkResponseMessage.getChunkResponse();
                nodeReplicationStatus.setStatus(chunkResponse.getStatus());
                nodeReplicationStatus.setErrorMessage(chunkResponse.getErrorMessage());
                replicateResponse.addNodeStatusList(nodeReplicationStatus);
                if (Torr2.Status.SUCCESS.equals(chunkResponse.getStatus())) {
                    fileData = fileData.concat(chunkResponse.getData());
                    currentChunkReplicated = true;
                    break;
                }
            }

            // if we could not replicate the current chunk, we stop
            if (!currentChunkReplicated) {
                allChunksReplicated = false;
                break;
            }
        }

        if (allChunksReplicated) {
            torrentSystem.getFileList().put(fileInfo.getHash(), new File(fileInfo, torrentSystem.parseFileData(fileData)));
            replicateResponse.setStatus(Torr2.Status.SUCCESS);
        } else {
            replicateResponse.setStatus(Torr2.Status.UNABLE_TO_COMPLETE);
            replicateResponse.setErrorMessage("Could not replicate the given file.");
        }

        return replicateResponse.build();
    }

    private boolean validateFileInfo(Torr2.ReplicateResponse.Builder replicateResponse, Torr2.FileInfo fileInfo) {
        if (fileInfo == null || fileInfo.getFilename() == null || fileInfo.getFilename().isEmpty()) {
            replicateResponse.setStatus(Torr2.Status.MESSAGE_ERROR);
            replicateResponse.setErrorMessage("The filename is empty.");
            return false;
        }
        if (fileInfo.getHash() == null || fileInfo.getHash().size() != 16) {
            replicateResponse.setStatus(Torr2.Status.MESSAGE_ERROR);
            replicateResponse.setErrorMessage("The hash file is not 16 bytes long.");
            return false;
        }
        return true;
    }
}
