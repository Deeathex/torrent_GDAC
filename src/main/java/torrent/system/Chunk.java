package torrent.system;

import com.google.protobuf.ByteString;
import torrent.Torr2;

public class Chunk {
    private Torr2.ChunkInfo chunkInfo;
    private ByteString data;

    public Chunk(Torr2.ChunkInfo chunkInfo, ByteString data) {
        this.chunkInfo = chunkInfo;
        this.data = data;
    }

    public Torr2.ChunkInfo getChunkInfo() {
        return chunkInfo;
    }

    public ByteString getData() {
        return data;
    }
}
