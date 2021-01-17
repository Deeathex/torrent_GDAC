# Torrent

## About the project
This is a project made for the GPU and Distributed Architecture Computing class from Distributed Systems in Internet 
master, Babes-Bolyai University of Cluj-Napoca.

### Requirements
Implement a torrent-like system

1. The communication is done using the Google Protobuffer 3.x messages defined below, over TCP. The exchange will be
   synchronous. When sending a request, open the TCP connection, send the message, get back the response, then close
   the connection. When listening for requests, send back the response, then close the socket.

2. The system consists of several nodes and one hub. Your job is to implement the node. The hub will be provided
   to you by the instructor.
   
3. The evaluation will be done as follows:
       - Your laptops will connect to the wireless network on campus
       - The instructor's laptop will also be connected to the wireless network on campus
       - You will run 3 nodes on your laptop, each on a port chosen by you
       - Your nodes will communicate with your colleagues and the instructor's nodes
       - Initially your nodes will have no data. The instructor's hub will upload various files to various nodes,
         and ask you to replicate locally some of those files. It will also download files form you to make sure they
         are correct.

4. Node referencing: Upon starting, a node will connect to the hub and register. The hub address and port will be
   configured manually.

5. The hub will play the role of bookkeeper for the active nodes, and also of evaluator. Every search and replicate
   request sent by the hub, will contain a subnet ID, identifying the nodes involved in the test. A node, will ask the
   hub for the subnet nodes corresponding to the subnet ID and collaborate only with those node for responding to the
   request.

6. Implement handlers for al the messages below that are supposed to be handled by the node. See the "Hub -> Node",
   "Node -> Hub", and "Node -> Node" markers of each message. For instance, you should handle a ReplicateRequest,
   and send back a ReplicateResponse. You should also be able to handle a ChunkRequest, but also send a ChunkRequest.

7. Every response message contains a status field and an error message.

8. The files/chunks that you receive, do not have to be stored on disk, they can be kept in memory just as well.

9. The standard chunk size will be 1024, with the last chunk being usually smaller.

10. The first chunk index is 0.

11. When broadcasting a request to all nodes, aggregate in the response, everything that you get from other nodes,
    including failed responses, and all responses from the same node. Basically, keep everything. The hub will
    rely on these for evaluating your implementation. For example, when you get a ReplicateRequest, you need to send
    ChunkRequests to all nodes. Some will give you ChunkReponses with data, others with error. Sometimes, you might
    have to ask the same node multiple times. All these responses must be stored in the ReplicateResponse.

12. In order to determine the type of the message you receive, all messages should be wrapped in the wrapper message.
    The messages travel over the network as buffers prefixed with an int containing the length of the buffer. See the
    example below for details
        - Sending example in a mix of languages
            LocalSearchRequest lsr = ...
            Message msg = Message.newBuilder()
                                 .setType(Message.Type.LOCAL_SEARCH_REQUEST)
                                 .setLocalSearchRequest(lsr)
                                 .build();
            byte[] m = msg.toByteArray();
            int len = m.length; // 32-bit integer
            len = convert-big-endian(len);
            send(len)
            send(m)
        - Receiving example in a mix of languages
            int len = receive(sizeof(int)) // 32-bit integer
            len = convert-to-local-endianness(len);
            byte[] m = receive(len);
            Message msg = Message.parse(m);
            if(msg.hasLocalSearchRequest()) {
                do-stuff(msg.getLocalSearchRequest());
            }
13. Reference implementation
    - In the archive posted on the course website you will find the reference implementation compiled for three
      operating systems and two architectures. Use the one that matches your environment.
    - The reference implementation will create a log subdirectory of the workign directory and will store there
      execution log files. The hub and default three nodes will log in file ref.log, while other nodes having other
      aliases will use file <alias>.log (e.g. log/joe.log)
    - Usage: torr-windows-amd64.exe <hub-ip> <hub-port> <nodes-ip> <node-1-port> <node-2-port> <node-3-port> <optional-owner-alias>
        Examples:
        - Run the hub and 3 reference nodes. When no owner alias is given, the hub is started along with three nodes
          having the alias "ref"
             torr-windows-amd64.exe 127.0.0.1 5000 127.0.0.1 5001 5002 5003

        - Run 3 other reference nodes. When given an owner alias, the hub is not started, and three nodes are started
          with the given alias. The given hub should be an already active instance.
             torr-windows-amd64.exe 127.0.0.1 5000 127.0.0.1 5004 5005 5006 abc
    - The reference implementation offers a prompt where you can run a few commands. You will be interested in "help",
      "nodes", "test", "quite". The "on", "off", "done" commands are for managing the nodes during evaluation
    - To just test the reference implementation, do:
          C:>torr-windows-amd64.exe 127.0.0.1 5000 127.0.0.1 5001 5002 5003
          Listening on 127.0.0.1:5000
          torr> nodes
          on   ref        127.0.0.1:5001         127.0.0.1:5002         127.0.0.1:5003
          torr> test ref
          ...
          torr> quit
          Stopping ...
    - To test your implementation against the reference, do:
          - Start the reference implementation with hub
              C:>torr-windows-amd64.exe 127.0.0.1 5000 127.0.0.1 5001 5002 5003
              Listening on 127.0.0.1:5000
          - Start three nodes of your implementation having the hub of the reference implementation
          - Get back to the reference implementation and do:
              torr> nodes
              on   ref        127.0.0.1:5001         127.0.0.1:5002         127.0.0.1:5003
              on   joe        127.0.0.1:5004         127.0.0.1:5005         127.0.0.1:5006
              torr> test ref joe
              ...
              torr> quit
              Stopping ...
              
              
### To run:
```bash 
cd TorrentAlgorithm\src\main\resources\torr-reference-binaries-1

torr-windows-amd64 127.0.0.1 5000 127.0.0.1 5001 5002 5003
```
The command from above will start the hub on localhost and will start the ref nodes directly available in the 
hub on localhsot ports: 5001, 5002, 5003. After running this command, the project should be started by 
running the main.

Afterwards, in the torr console (started from above) the user can trigger the following commands:
```bash 
torr> nodes
```
The *nodes* command from above will show all the nodes registered to the hub.
After starting the main, the command should show the following nodes:
```bash
torr> nodes
on   node       127.0.0.1:5011         127.0.0.1:5012         127.0.0.1:5013
on   ref        127.0.0.1:5001         127.0.0.1:5002         127.0.0.1:5003
```
The following command will start the torrent between ref and node nodes registered to the hub and 
will test the implementation.
```bash 
torr> test ref node
```
