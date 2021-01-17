package torrent.abstractions;

import com.google.protobuf.ByteString;
import torrent.Torr2;
import torrent.system.Chunk;
import torrent.system.File;
import torrent.system.TorrentSystem;

public class ChunkAbstraction implements Abstraction {
    private TorrentSystem torrentSystem;

    public ChunkAbstraction(TorrentSystem torrentSystem) {
        this.torrentSystem = torrentSystem;
    }

    @Override
    public Torr2.Message handle(Torr2.Message requestMessage) {
        if (Torr2.Message.Type.CHUNK_REQUEST.equals(requestMessage.getType())) {
            return Torr2.Message.newBuilder()
                    .setType(Torr2.Message.Type.CHUNK_RESPONSE)
                    .setChunkResponse(handleChunkRequest(requestMessage.getChunkRequest()))
                    .build();
        }
        return null;
    }

    private Torr2.ChunkResponse handleChunkRequest(Torr2.ChunkRequest chunkRequest) {
        final ByteString fileHash = chunkRequest.getFileHash();
        final int chunkIndex = chunkRequest.getChunkIndex();

        // do some validations on the input
        Torr2.ChunkResponse.Builder chunkResponse = Torr2.ChunkResponse.newBuilder();
        if (!validateFileHash(chunkResponse, fileHash)) {
            return chunkResponse.build();
        }
        if (!validateChunkIndex(chunkResponse, chunkIndex)) {
            return chunkResponse.build();
        }

        // check if we have the file
        File file = torrentSystem.getFileList().get(fileHash);
        if (file == null) {
            chunkResponse.setStatus(Torr2.Status.UNABLE_TO_COMPLETE);
            chunkResponse.setErrorMessage("File not found.");
            return chunkResponse.build();
        }

        // then, check if we have the chunk
        final Chunk chunk = file.getChunk(chunkIndex);
        if (chunk == null) {
            chunkResponse.setStatus(Torr2.Status.UNABLE_TO_COMPLETE);
            chunkResponse.setErrorMessage("Chunk not found.");
            return chunkResponse.build();
        }

        // if we have both the file and the chunk, we return the wanted chunk
        chunkResponse.setStatus(Torr2.Status.SUCCESS);
        chunkResponse.setData(chunk.getData());
        return chunkResponse.build();
    }

    private boolean validateFileHash(Torr2.ChunkResponse.Builder chunkResponse, ByteString fileHash) {
        if (fileHash == null || fileHash.size() != 16) {
            chunkResponse.setStatus(Torr2.Status.MESSAGE_ERROR);
            chunkResponse.setErrorMessage("The filehash is not 16 bytes long.");
            return false;
        }
        return true;
    }

    private boolean validateChunkIndex(Torr2.ChunkResponse.Builder chunkResponse, int chunkIndex) {
        if (chunkIndex < 0) {
            chunkResponse.setStatus(Torr2.Status.MESSAGE_ERROR);
            chunkResponse.setErrorMessage("The index is less than zero.");
            return false;
        }
        return true;
    }
}
