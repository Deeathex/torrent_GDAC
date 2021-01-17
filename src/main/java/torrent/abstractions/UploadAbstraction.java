package torrent.abstractions;

import com.google.protobuf.ByteString;
import torrent.Torr2;
import torrent.system.Chunk;
import torrent.system.File;
import torrent.system.TorrentSystem;

import java.util.List;
import java.util.stream.Collectors;

public class UploadAbstraction implements Abstraction {
    private TorrentSystem torrentSystem;

    public UploadAbstraction(TorrentSystem torrentSystem) {
        this.torrentSystem = torrentSystem;
    }

    @Override
    public Torr2.Message handle(Torr2.Message requestMessage) {
        if (Torr2.Message.Type.UPLOAD_REQUEST.equals(requestMessage.getType())) {
            return Torr2.Message.newBuilder()
                    .setType(Torr2.Message.Type.UPLOAD_RESPONSE)
                    .setUploadResponse(handleUploadRequest(requestMessage.getUploadRequest()))
                    .build();
        }
        return null;
    }

    private Torr2.UploadResponse handleUploadRequest(Torr2.UploadRequest uploadRequest) {
        final String filename = uploadRequest.getFilename();
        final ByteString fileData = uploadRequest.getData();

        // do some validations on the input
        Torr2.UploadResponse.Builder uploadResponse = Torr2.UploadResponse.newBuilder();
        if (!validateFilename(uploadResponse, filename)) {
            return uploadResponse.build();
        }

        // parse the file data if we do not have the file yet, and store it
        ByteString fileHash = torrentSystem.hashBytes(fileData);
        File file = torrentSystem.getFileList().get(fileHash);
        if (file == null) {
            List<Chunk> chunkList = torrentSystem.parseFileData(fileData);
            List<Torr2.ChunkInfo> chunkInfoList = chunkList.stream().map(Chunk::getChunkInfo).collect(Collectors.toList());
            file = new File(Torr2.FileInfo.newBuilder()
                    .setHash(fileHash)
                    .setSize(fileData.size())
                    .setFilename(filename)
                    .addAllChunks(chunkInfoList)
                    .build(),
                    chunkList);
            torrentSystem.getFileList().put(fileHash, file);
        }
        uploadResponse.setFileInfo(file.getFileInfo());
        uploadResponse.setStatus(Torr2.Status.SUCCESS);

        return uploadResponse.build();
    }

    private boolean validateFilename(Torr2.UploadResponse.Builder uploadResponse, String filename) {
        if (filename == null || filename.isEmpty()) {
            uploadResponse.setStatus(Torr2.Status.MESSAGE_ERROR);
            uploadResponse.setErrorMessage("The filename is empty.");
            return false;
        }
        return true;
    }
}
