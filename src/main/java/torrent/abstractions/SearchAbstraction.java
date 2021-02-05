package torrent.abstractions;

import torrent.Torr2;
import torrent.system.TorrentSystem;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class SearchAbstraction implements Abstraction {
    private TorrentSystem torrentSystem;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public SearchAbstraction(TorrentSystem torrentSystem) {
        this.torrentSystem = torrentSystem;
    }

    @Override
    public Torr2.Message handle(Torr2.Message requestMessage) {
        if (Torr2.Message.Type.SEARCH_REQUEST.equals(requestMessage.getType())) {
            return Torr2.Message.newBuilder()
                    .setType(Torr2.Message.Type.SEARCH_RESPONSE)
                    .setSearchResponse(handleSearchRequest(requestMessage.getSearchRequest()))
                    .build();
        }
        return null;
    }


    private Torr2.SearchResponse handleSearchRequest(Torr2.SearchRequest searchRequest) {
        final String regex = searchRequest.getRegex();
        final int subnetId = searchRequest.getSubnetId();

        // do some validations on the input
        Torr2.SearchResponse.Builder searchResponse = Torr2.SearchResponse.newBuilder();
        Pattern regexPattern = compileRegex(searchResponse, regex);
        if (regexPattern == null) {
            return searchResponse.build();
        }

        // determine the list of nodes to interrogate with a subnet request
        List<Torr2.NodeId> nodeList = torrentSystem.sendSubnetRequest(subnetId);
        if (nodeList == null) {
            searchResponse.setStatus(Torr2.Status.PROCESSING_ERROR);
            searchResponse.setErrorMessage("Error on subnet request.");
            return searchResponse.build();
        }

        Map<String, Torr2.NodeSearchResult.Builder> searchResults = new ConcurrentHashMap<>();
        Queue<Future<?>> futures = new ConcurrentLinkedQueue<>();
        // search all the nodes
        for (final Torr2.NodeId nodeId : nodeList) {
            futures.add(executorService.submit(() -> {
                // send a local search request
                Torr2.Message localSearchResponse = torrentSystem.sendLocalSearchRequest(nodeId, regex);
                Torr2.NodeSearchResult.Builder nodeSearchResult = Torr2.NodeSearchResult.newBuilder();
                nodeSearchResult.setNode(nodeId);

                // if the response message is null, we consider it a network error
                if (localSearchResponse == null) {
                    nodeSearchResult.setStatus(Torr2.Status.NETWORK_ERROR);
                    nodeSearchResult.setErrorMessage("Cannot connect to the node.");
                    searchResults.put(nodeId.getOwner() + nodeId.getIndex(), nodeSearchResult);
                    return;
                }

                // if the response message is the wrong type, we mark it as such
                if (!Torr2.Message.Type.LOCAL_SEARCH_RESPONSE.equals(localSearchResponse.getType())) {
                    nodeSearchResult.setStatus(Torr2.Status.MESSAGE_ERROR);
                    nodeSearchResult.setErrorMessage("The response is not parsable or has the wrong type.");
                    searchResults.put(nodeId.getOwner() + nodeId.getIndex(), nodeSearchResult);
                    return;
                }

                // if there are no issues, we add the found files to the result (and set the same status)
                nodeSearchResult.addAllFiles(localSearchResponse.getLocalSearchResponse().getFileInfoList());
                nodeSearchResult.setStatus(localSearchResponse.getLocalSearchResponse().getStatus());
                searchResults.put(nodeId.getOwner() + nodeId.getIndex(), nodeSearchResult);
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        TreeMap<String, Torr2.NodeSearchResult.Builder> sortedSearchResults = new TreeMap<>(searchResults);
        for (Map.Entry<String, Torr2.NodeSearchResult.Builder> entry : sortedSearchResults.entrySet()) {
            searchResponse.addResults(entry.getValue());
        }

        return searchResponse.build();
    }

    private Pattern compileRegex(Torr2.SearchResponse.Builder searchResponse, String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            searchResponse.setStatus(Torr2.Status.MESSAGE_ERROR);
            searchResponse.setErrorMessage("Invalid regex.");
        }
        return null;
    }
}
