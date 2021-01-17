package torrent.abstractions;

import com.google.protobuf.ByteString;
import torrent.Torr2;
import torrent.system.Chunk;
import torrent.system.File;
import torrent.system.TorrentSystem;

public class DownloadAbstraction implements Abstraction {
    private TorrentSystem torrentSystem;

    public DownloadAbstraction(TorrentSystem torrentSystem) {
        this.torrentSystem = torrentSystem;
    }

    @Override
    public Torr2.Message handle(Torr2.Message requestMessage) {
        if (Torr2.Message.Type.DOWNLOAD_REQUEST.equals(requestMessage.getType())) {
            return Torr2.Message.newBuilder()
                    .setType(Torr2.Message.Type.DOWNLOAD_RESPONSE)
                    .setDownloadResponse(handleDownloadRequest(requestMessage.getDownloadRequest()))
                    .build();
        }
        return null;
    }

    private Torr2.DownloadResponse handleDownloadRequest(Torr2.DownloadRequest downloadRequest) {
        final ByteString fileHash = downloadRequest.getFileHash();

        // do some validations on the input
        Torr2.DownloadResponse.Builder downloadResponse = Torr2.DownloadResponse.newBuilder();
        if (!validateFileHash(downloadResponse, fileHash)) {
            return downloadResponse.build();
        }

        // check if we have the file
        File file = torrentSystem.getFileList().get(fileHash);
        if (file == null) {
            downloadResponse.setStatus(Torr2.Status.UNABLE_TO_COMPLETE);
            downloadResponse.setErrorMessage("File not found.");
            return downloadResponse.build();
        }

        // concatenate all the chunks' data in a byteString, which we add on the response message
        ByteString fileContent = ByteString.EMPTY;
        for (Chunk chunk : file.getChunks()) {
            fileContent = fileContent.concat(chunk.getData());
        }
        downloadResponse.setData(fileContent);
        downloadResponse.setStatus(Torr2.Status.SUCCESS);

        return downloadResponse.build();
    }

    private boolean validateFileHash(Torr2.DownloadResponse.Builder downloadResponse, ByteString fileHash) {
        if (fileHash == null || fileHash.size() != 16) {
            downloadResponse.setStatus(Torr2.Status.MESSAGE_ERROR);
            downloadResponse.setErrorMessage("The filehash is not 16 bytes long.");
            return false;
        }
        return true;
    }
}
