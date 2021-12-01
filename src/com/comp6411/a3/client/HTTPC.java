package com.comp6411.a3.client;

import com.comp6411.a3.Packet;

import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;

import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;


public class HTTPC {

    private int sequenceNumber;
    private List<Long> receivedAcknowledgements;
    private int timeout;
    private int numberOfAcks;
    private boolean debug;


    HTTPC(boolean debug){
        this.sequenceNumber = 0;
        this.receivedAcknowledgements = new ArrayList<>();
        this.timeout = 10000;
        this.numberOfAcks = 0;
        this.debug = debug;
    }

    void handleRequest(String[] userCommand) throws FileNotFoundException, IOException, MalformedURLException {

        // Create a List to hold the command instructions
        List<String> inputCommand = new ArrayList<>();
        for(String s: userCommand){
            inputCommand.add(s);
        }

        String operation = inputCommand.get(1).toLowerCase();

        // If it is a help request, process it directly
        if(operation.equals("help")){
            handleHelp(inputCommand);
            return;
        }

        if(operation.equalsIgnoreCase("get") || operation.equalsIgnoreCase("post")){
            inputCommand.remove(0);
            inputCommand.remove(0);
        } else {
            for(String s: inputCommand){
                if(s.toLowerCase().contains("get")){
                    operation = "get";
                } else if(s.toLowerCase().contains("post")){
                    operation = "post";
                }
            }
            if(!operation.equals("get") && !operation.equals("post")){
                System.out.println("Command doesn't indicate GET/POST. Please refer the help.");
                printHTTPCHelp();
                return;
            }
        }

        // Declare required data structures
        List<String> headers = new ArrayList<>();
        boolean isVerbose = false;
        boolean isO = false;
        String host = "";
        String urlString = "";
        String query = "";
        String path = "/";
        String outputFileName = "";
        URL url = null;
        BufferedWriter outputFileWriter = null;
        int port = 8007;
        final String HttpVersion = "HTTP/1.0";
        final String CRLF = "\r\n";
        StringBuilder createPostData = new StringBuilder();
        resetCtx();

        // If it is a get request, confirm only valid options are used
        if(operation.equals("get") && (inputCommand.contains("-d") || inputCommand.contains("-f"))){
            System.out.println("Cannot use [-d] or [-f] with the GET command. Run \"httpc help get\" for more help.");
            return;
        }

        // If it is a post request, confirm that both -d and -f are not being used
        if(operation.equals("post") && (inputCommand.contains("-d") && inputCommand.contains("-f"))){
            System.out.println("Can use either [-d] or [-f] with the POST command. Run \"httpc help post\" for " +
                    "more help");
            return;
        }

        // Extract information from the input command till it is empty
        while(inputCommand.size()>0){
            if(inputCommand.get(0).equals("-v")){
                isVerbose = true;
                inputCommand.remove(0);
            }
            else if(inputCommand.get(0).equals("-h")){
                inputCommand.remove(0);

                // If key:value pair is missing, print error message and return
                if(inputCommand.isEmpty()){
                    System.out.println("Invalid command. Header option requires a key:value pair.");
                    System.out.println("Check \"httpc help " + operation + "\" for more help.");
                    return;
                }

                // If error in command with key:value pair, print error message and return. Else, add to the headers list.
                String keyValuePair = inputCommand.get(0);
                if(!keyValuePair.contains(":")){
                    System.out.println("Invalid command. Header option requires a key:value pair.");
                    System.out.println("Check \"httpc help " + operation + "\" for more help.");
                    return;
                }
                headers.add(keyValuePair);
                inputCommand.remove(0);
            }
            else if(inputCommand.get(0).equals("-d")) {
                inputCommand.remove(0);

                // If data is missing, print error message and return
                if(inputCommand.isEmpty()){
                    System.out.println("Invalid command. No data found.");
                    System.out.println("Check \"httpc help " + operation + "\" for more help.");
                    return;
                }
                //TODO: Handle space in inline data.

                // Add inline data as post data and remove it from the command list
                createPostData.append(inputCommand.get(0));
                inputCommand.remove(0);
            }
            else if(inputCommand.get(0).equals("-f")){
                inputCommand.remove(0);

                // If file name is missing, print error message and return
                if(inputCommand.isEmpty()){
                    System.out.println("Invalid command. No file name found.");
                    System.out.println("Check \"httpc help " + operation + "\" for more help.");
                    return;
                }

                // If file exists and is readable, read and add data as post data. Else, raise appropriate error.
                try{
                    String fileName = "./resources/" + inputCommand.get(0);
                    List<String> fileData = Files.readAllLines(Paths.get(fileName));
                    for(String line: fileData){
                        createPostData.append(line + "\n");
                    }
                    inputCommand.remove(0);
                } catch (FileNotFoundException FNFE){
                    System.out.println("File not found. Confirm that the file exists or the name is correct.");
                    return;
                } catch (IOException IOE){
                    System.out.println("No such file exists.");
                    return;
                }
            } else if(inputCommand.get(0).equals("-o")){
                inputCommand.remove(0);
                isO = true;

                // If file name is missing, print error message and return
                if(inputCommand.isEmpty()){
                    System.out.println("Invalid command. No file name found.");
                    System.out.println("Check \"httpc help " + operation + "\" for more help.");
                    return;
                }

                outputFileName = "./resources/" + inputCommand.get(0);
                if(!outputFileName.contains(".txt")) {
                    outputFileName += ".txt";
                }
                File outputFile = new File(outputFileName);
                outputFileWriter = new BufferedWriter(new FileWriter(outputFile,true));
                inputCommand.remove(0);
            }
            else{
                // check if it is URL
                urlString = inputCommand.get(0);
                inputCommand.remove(0);
            }
        }

        // Create the url and get host, query, and port. Raise error and return if inappropriate URL.
        try{
            urlString = urlString.replace("'", "");
            urlString = urlString.replace("\"", "");
            url = new URL(urlString);
            host = url.getHost();
            if(host.isEmpty()){
                throw new MalformedURLException();
            }
            if(url.getQuery()!=null){
                query = url.getQuery();
            }
            if(!url.getPath().isEmpty()){
                path = url.getPath();
            }
            if(url.getPort()!=-1){
                port = url.getPort();
            }
        } catch(MalformedURLException MURL){
            System.out.println("Malformed URL. Enter appropriate URL.");
            return;
        }
        // System.out.println("host: " + host + "\nquery: " + query + "\npath: " + path + "\nport: " + port);

        // Create post data string
        String postData = createPostData.toString();
        postData = postData.replace("'", "");

        // establish server hosts and ports for communication with router
        String routerHost = "localhost";
        String serverHost = host;
        int routerPort = 3000;
        int serverPort = port;
        SocketAddress routerAddress = new InetSocketAddress(routerHost, routerPort);
        InetSocketAddress serverAddress = new InetSocketAddress(serverHost, serverPort);

        // open the channel
        try(DatagramChannel channel = DatagramChannel.open()){
            // initiate handshake
            System.out.println("Initiating handshake\n");
            completeHandshake(routerAddress, serverAddress, channel, isVerbose);
            System.out.println("\nHandshake completed.\n");

            // Create the message to send
            StringBuilder messageToServer = new StringBuilder();

            // add operation type and the query parameters if present
            if(!query.isEmpty()){
                messageToServer.append(operation.toUpperCase() + " " +  path + "?" + query + " " + HttpVersion + CRLF);
            }
            else {
                messageToServer.append(operation.toUpperCase() + " " + path + " " + HttpVersion + CRLF);
            }

            // add host name
            messageToServer.append("Host: " + host + CRLF);

            // add headers if present
            if(headers.size()!=0){
                for(String keyValuePair: headers){
                    messageToServer.append(keyValuePair + CRLF);
                }
            }

            // add postdata if present
            if(!postData.isEmpty()){
                messageToServer.append("Content-Length: " + postData.length() + CRLF);
                messageToServer.append(postData + CRLF);
            }

            // Create payload string
            String payload = messageToServer.toString();
//            System.out.println("\nMain payload length: " + payload.getBytes().length + "\n");

            // Send the payload now
            String response;
            do{
                response = transmitPayload(routerAddress, serverAddress, channel, payload, isVerbose);
            }while (response.equalsIgnoreCase(""));

//            System.out.println("RESPONSE IN MAIN FUNC: " + response);
            // Print the response
            if(!isVerbose){
                //System.out.println(response.substring(response.indexOf('{')));
                try{
                    response = response.substring(response.indexOf('{'));
                }catch(StringIndexOutOfBoundsException e){

                }
            }
            if(isO){
                outputFileWriter.write(response + "\n");
                outputFileWriter.flush();
                outputFileWriter.close();
            } else{
                System.out.println(response);
            }

        } catch (IOException e){
            System.out.println("Error opening the channel.");
            e.printStackTrace();
        }
    }

    private void completeHandshake(SocketAddress routerAddress,
                                   InetSocketAddress serverAddress, DatagramChannel channel,
                                   boolean isVerbose) throws IOException{
        // send message to initiate handshake with the sequence number 0
        String requestMessage = "Client handshake request.";
        Packet packet = new Packet.Builder()
                .setType(0)
                .setSequenceNumber(this.sequenceNumber)
                .setPortNumber(serverAddress.getPort())
                .setPeerAddress(serverAddress.getAddress())
                .setPayload(requestMessage.getBytes())
                .create();
        channel.send(packet.toBuffer(), routerAddress);

        // Try to receive a packet within timeout.
        channel.configureBlocking(false);
        Selector selector = Selector.open();
        channel.register(selector, OP_READ);

        selector.select(this.timeout);

        Set<SelectionKey> keys = selector.selectedKeys();
        if(keys.isEmpty()){
            if(isVerbose){
                System.out.println("No response in handshake within time limit.");
                System.out.println("Attempting to make handshake request again.");
            }
            completeHandshake(routerAddress, serverAddress, channel, isVerbose);
            return;
        }

        // get the received packet
        ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
        SocketAddress router = channel.receive(buf);
        buf.flip();
        Packet resp = Packet.fromBuffer(buf);
        if(!this.receivedAcknowledgements.contains(resp.getSequenceNumber())){
            String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
//        System.out.println("Payload: " + payload);
            if(payload.equals("Handshake ACK")){
                if(isVerbose){
                    System.out.println("Payload received from server: " + payload);
                }
                this.receivedAcknowledgements.add(resp.getSequenceNumber());
                keys.clear();
            } else{
                if(this.debug){
                    System.out.println("Unexpected response: " + payload);
                }
            }
        }
    }

    private String transmitPayload(SocketAddress routerAddress,
                                   InetSocketAddress serverAddress, DatagramChannel channel, String payload,
                                   boolean isVerbose) throws
            IOException{
        // Prepare byte array of payload
        byte[] requestMessage = payload.getBytes();
        if(requestMessage.length>Packet.MAX_LEN){
            System.out.println("Way too large message. Need to do something about this!");
            return "";
        }

        //Prepare and send the packet
        this.sequenceNumber = this.sequenceNumber + 1;
        Packet packet = new Packet.Builder()
                .setType(0)
                .setSequenceNumber(this.sequenceNumber)
                .setPortNumber(serverAddress.getPort())
                .setPeerAddress(serverAddress.getAddress())
                .setPayload(requestMessage)
                .create();
        channel.send(packet.toBuffer(), routerAddress);

        // Try to receive a packet within timeout.
        channel.configureBlocking(false);
        Selector selector = Selector.open();
        channel.register(selector, OP_READ);
        selector.select(this.timeout);

        // Check if any packer is received or not.
        Set<SelectionKey> keys = selector.selectedKeys();
        if(keys.isEmpty()){
            if(isVerbose){
                System.out.println("No response within time limit.");
            }
            do{
                payload = transmitPayload(routerAddress, serverAddress, channel, payload, isVerbose);
            }while (payload.equalsIgnoreCase(""));

            return payload;
        }

        // get the received packet
        ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
        SocketAddress router = channel.receive(buf);
        buf.flip();
        Packet resp = Packet.fromBuffer(buf);
        if(!this.receivedAcknowledgements.contains(resp.getSequenceNumber())){
            payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
            this.receivedAcknowledgements.add(resp.getSequenceNumber());
            // System.out.println("Payload: \n" + payload);
            return payload;
        }
        return "";
    }

    private void handleHelp(List<String> inputCommand){
        /**
         * Handles the help request from the user.
         * Point out any error in the help function.
         * @param inputCommand User command.
         */
        // General help
        if(inputCommand.size()==2){
            printHTTPCHelp();
            return;
        }

        // If invalid help command, print general help to assisst
        if(!inputCommand.get(2).equalsIgnoreCase("get")
                && !inputCommand.get(2).equalsIgnoreCase("post")){
            System.out.println("Invalid \"help\" command. Follow the help instructions below " +
                    "to execute correct command");
            printHTTPCHelp();
            return;
        }

        // If invalid extension of "httpc help (get|post)", notify the error and print the closest help to asist
        if(inputCommand.size()>3){
            System.out.println("Invalid \"help\" command. The closest resembling help command is as follows. " +
                    "For more help, enter \"httpc help\".");
        }

        // Execute "httpc help (get|post)"
        if(inputCommand.get(2).equalsIgnoreCase("get")){
            printHTTPCGetHelp();
        } else {
            printHTTPCPostHelp();
        }
        return;
    }

    private void printHTTPCHelp(){
        /**
         * Prints general help for httpc.
         */
        System.out.println("httpc help");
        System.out.println("httpc is a curl-like application but supports HTTP protocol only.");
        System.out.println("Usage:" + "\n\t" + "httpc command [arguments]");
        System.out.println("The commands are:" + "\n\t" +
                "get     executes a HTTP GET request and prints the response." + "\n\t" +
                "post     executes a HTTP POST request and prints the response." + "\n\t" +
                "help     prints this screen.");
        System.out.println("Use \"httpc help [command]\" for more information about a command.");
        return;
    }

    private void printHTTPCGetHelp(){
        /**
         * Prints help for httpc get.
         */
        System.out.println("httpc help get");
        System.out.println("usage: httpc get [-v] [-h key:value] URL");
        System.out.println("Get executes a HTTP GET request for a given URL." + "\n\t" +
                "-v\tPrints the detail of the response such as protocol, status,and headers." + "\n\t" +
                "-h key:value\tAssociates headers to HTTP Request with the format 'key:value'.");
        return;
    }

    private void printHTTPCPostHelp(){
        /**
         * Prints help for httpc post.
         */
        System.out.println("httpc help get");
        System.out.println("usage: httpc post [-v] [-h key:value] [-d inline-data] [-f file] URL");
        System.out.println("Post executes a HTTP POST request for a given URL with inline data or from " +
                "file." + "\n\t" +
                "-v\tPrints the detail of the response such as protocol, status,and headers." + "\n\t" +
                "-h key:value\tAssociates headers to HTTP Request with the format 'key:value'." + "\n\t" +
                "-d string\tAssociates an inline data to the body HTTP POST request." + "\n\t" +
                "-f file\tAssociates the content of a file to the body HTTP POST request.");
        System.out.println("Either [-d] or [-f] can be used but not both.");
        return;
    }

    private void resetCtx(){
        this.sequenceNumber = 0;
        this.receivedAcknowledgements = new ArrayList<>();
        this.numberOfAcks = 0;
    }
}
