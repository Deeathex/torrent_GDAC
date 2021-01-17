package torrent.system;

import torrent.Torr2;

import java.util.List;

public class File {
    private Torr2.FileInfo fileInfo;
    private List<Chunk> chunks;

    public File(Torr2.FileInfo fileInfo, List<Chunk> chunks) {
        this.fileInfo = fileInfo;
        this.chunks = chunks;
    }

    public Chunk getChunk(int index) {
        for (Chunk chunk : chunks) {
            if (chunk.getChunkInfo().getIndex() == index) {
                return chunk;
            }
        }
        return null;
    }

    public Torr2.FileInfo getFileInfo() {
        return fileInfo;
    }

    public List<Chunk> getChunks() {
        return chunks;
    }
}
