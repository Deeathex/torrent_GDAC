package torrent.abstractions;

import torrent.Torr2;
import torrent.system.File;
import torrent.system.TorrentSystem;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class LocalSearchAbstraction implements Abstraction {
    private TorrentSystem torrentSystem;

    public LocalSearchAbstraction(TorrentSystem torrentSystem) {
        this.torrentSystem = torrentSystem;
    }

    @Override
    public Torr2.Message handle(Torr2.Message requestMessage) {
        if (Torr2.Message.Type.LOCAL_SEARCH_REQUEST.equals(requestMessage.getType())) {
            return Torr2.Message.newBuilder()
                    .setType(Torr2.Message.Type.LOCAL_SEARCH_RESPONSE)
                    .setLocalSearchResponse(handleLocalSearchRequest(requestMessage.getLocalSearchRequest()))
                    .build();
        }
        return null;
    }

    private Torr2.LocalSearchResponse handleLocalSearchRequest(Torr2.LocalSearchRequest localSearchRequest) {
        final String regex = localSearchRequest.getRegex();

        // do some validations on the input
        Torr2.LocalSearchResponse.Builder localSearchResponse = Torr2.LocalSearchResponse.newBuilder();
        Pattern regexPattern = compileRegex(localSearchResponse, regex);
        if (regexPattern == null) {
            return localSearchResponse.build();
        }

        // return the file infos for all the files with names that match the given regex
        for (File file : torrentSystem.getFileList().values()) {
            Torr2.FileInfo fileInfo = file.getFileInfo();
            if (regexPattern.matcher(fileInfo.getFilename()).matches()) {
                localSearchResponse.addFileInfo(fileInfo);
            }
        }
        localSearchResponse.setStatus(Torr2.Status.SUCCESS);

        return localSearchResponse.build();
    }

    private Pattern compileRegex(Torr2.LocalSearchResponse.Builder localSearchResponse, String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            localSearchResponse.setStatus(Torr2.Status.MESSAGE_ERROR);
            localSearchResponse.setErrorMessage("Invalid regex.");
        }
        return null;
    }
}
