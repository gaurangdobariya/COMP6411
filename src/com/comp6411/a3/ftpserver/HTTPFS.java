package com.comp6411.a3.ftpserver;


import com.comp6411.a3.Packet;
import com.comp6411.a3.RequestParameters;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import java.nio.file.Files;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;


public class HTTPFS {
    private int port;
    private boolean isVerbose;
    private String directory;


    public void createServer(int port, boolean isVerbose, String directory){
        /**
         * Create the socket as per the given config and start listening to client requests.
         * @param port Port number to run Server socket
         * @param isVerbose Flag to indicate if server status is to be printed or not
         * @param directory Root directory for server to work on
         */
        this.port = port;
        this.isVerbose = isVerbose;
        this.directory = directory;

        try(DatagramChannel channel = DatagramChannel.open()){
            channel.bind(new InetSocketAddress(this.port));
            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN).order(ByteOrder.BIG_ENDIAN);
            listenClientRequests(channel, buf);
        } catch (IOException e){
            System.out.println("Trouble creating server socket. Check port number.");
        }
    }

    void listenClientRequests(DatagramChannel channel, ByteBuffer buf){
        /**
         * Keep listening for client requests. On connecting with a client, parse the requests,
         * do the required action, and send appropriate response back to client.
         */
        Map<Integer, Packet> packetBuffer = new HashMap<>();
        if(isVerbose){
            System.out.println("Server running on port: " + this.port);
        }

        while (true){
            try{
                buf.clear();
                SocketAddress router = channel.receive(buf);

                if(router != null){
                    // Parse a packet from the received raw data.
                    buf.flip();
                    Packet packet = Packet.fromBuffer(buf);
                    buf.flip();

                    // get payload request message as a String
                    String requestPayload = new String(packet.getPayload(), UTF_8);

                    if(requestPayload.equals("Client handshake request.")){
                        handleHandshake(packet, channel, router, requestPayload);
                    } else {
                        processRequest(packet, channel, router, requestPayload);
                    }
                }
            } catch (IOException e){
                System.out.println("Error in accepting requests from the server socket.");
            }
        }
    }

    void processRequest(Packet packet, DatagramChannel channel, SocketAddress router,
                        String requestPayload) throws IOException{
        String[] arr = requestPayload.split("\r\n");
        String[] methodHeader = arr[0].split(" ");
        String method = "";
        String path = "";
        if(methodHeader.length<3 || (!methodHeader[0].equalsIgnoreCase("get") &&
                !methodHeader[0].equalsIgnoreCase("post"))){
            // bad request
            if(isVerbose){
                System.out.println("ERROR 400: BAD REQUEST BY THE CLIENT.");
            }
            String badRequest = "HTTP/1.1 400 BAD REQUEST";
            // Date and time
            DateFormat dateFormat=new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Date date=new Date();
            String dateAndTime = dateFormat.format(date);
            String responseHeader = badRequest + "\n" + "Server: httpfs" + "\n" + "Date: " + dateAndTime + "\n";

            // send responseHeader to client
            Packet packet1 = packet.toBuilder().setPayload(responseHeader.getBytes()).create();
            channel.send(packet1.toBuffer(), router);
            return;
        } else {
            method = methodHeader[0];
            path = methodHeader[1];
        }

        // Process client request message
        RequestParameters requestParameters = new RequestParameters(method, path);
        requestParameters.processPath();
        requestParameters.processMessage(requestPayload);
        requestParameters.createURL();
        if(isVerbose){
//            requestParameters.printRequestParameters();
        }

        // Process requests based on their type
        String response = handleClientRequest(requestParameters);
        if(isVerbose){
            System.out.println("Response: \n" + response);
        }

        // send responseHeader to client
        Packet packet1 = packet.toBuilder().setPayload(response.getBytes()).create();
        channel.send(packet1.toBuffer(), router);
    }

    void listenClientRequests(ServerSocket serverSocket){
        /**
         * Keep listening for client requests. On connecting with a client, parse the requests,
         * do the required action, and send appropriate response back to client.
         */
        if(isVerbose){
            System.out.println("Server running on port: " + this.port);
        }

        // Keep listening incoming requests
        while(true){
            try{
                Socket clientServer = serverSocket.accept();
                if(isVerbose){
                    System.out.println("Server connected to a client.");
                }

                // get stream to read from the client
                InputStreamReader inputStreamReader = new InputStreamReader(clientServer.getInputStream(),
                        StandardCharsets.UTF_8);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

                // get stream to write to the client
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(clientServer.getOutputStream(),
                        StandardCharsets.UTF_8);
                BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);

                // Read method of communication and the path
                String clientMessage = bufferedReader.readLine();
//                System.out.println("First message: " + clientMessage);
                String[] clientMessageList = clientMessage.split(" ");
                String method = clientMessageList[0];
                String path = clientMessageList[1];

                if(!method.equalsIgnoreCase("get") && !method.equalsIgnoreCase("post")){
                    // bad request
                    if(isVerbose){
                        System.out.println("ERROR 400: BAD REQUEST BY THE CLIENT.");
                    }
                    String badRequest = "HTTP/1.1 400 BAD REQUEST";
                    // Date and time
                    DateFormat dateFormat=new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                    Date date=new Date();
                    String dateAndTime = dateFormat.format(date);
                    String responseHeader = badRequest + "\n" + "Server: httpfs" + "\n" + "Date: " + dateAndTime + "\n";
                    bufferedWriter.write(responseHeader);
                    bufferedWriter.flush();
                    bufferedWriter.close();
                    continue;
                }

                // Read rest of the message from client
                StringBuilder clientMessageData = new StringBuilder();
                String message;
                while(!(message = bufferedReader.readLine()).isEmpty()){
                    clientMessageData.append(message).append("\n");
//                    System.out.println("Message while reading: " + message);
                }
                clientMessage = clientMessageData.toString();

                // Process client request message
                RequestParameters requestParameters = new RequestParameters(method, path);
                requestParameters.processPath();
                requestParameters.processMessage(clientMessage);
                requestParameters.createURL();
                if(isVerbose){
//                    requestParameters.printRequestParameters();
                }

                // Process requests based on their type
                String response = handleClientRequest(requestParameters);
                if(isVerbose){
                    System.out.println("Response: \n" + response);
                }
                bufferedWriter.write(response);
                bufferedWriter.flush();
                bufferedWriter.close();

            } catch (IOException e){
                System.out.println("Error in accepting requests from the server socket.");
            } catch (NullPointerException e){
                System.out.println("Error loading files. Checking the working directory path.");
            }
        }
    }

    void handleHandshake(Packet packet, DatagramChannel channel, SocketAddress router,
                         String requestPayload) throws IOException{
        String response = "Handshake ACK";
        if(this.isVerbose){
            System.out.println(requestPayload);
            System.out.println("Sending handshake ACK");
        }
        Packet packet1 = packet.toBuilder().setPayload(response.getBytes()).create();

        channel.send(packet1.toBuffer(), router);
    }

    String handleClientRequest(RequestParameters requestParameters){
        /**
         * Process the client request and prepare appropriate response message.
         */
        String okStatus = "HTTP/1.1 200 OK";
        String fileNotFound = "HTTP/1.1 404 FILE NOT FOUND";
        String errorReadingFile = "HTTP/1.1 405 ERROR READING FILE";
        String server = "Server: httpfs";
        String date1 = "Date: ";
        String contentLength = "Content-Length: ";
        String connectionStatus = "Connection: close";
        String accessControlAllowOrigin = "Access-Control-Allow-Origin: *";
        String accessControlAllowCredentials = "Access-Control-Allow-Credentials: true";

        StringBuilder responseBody = new StringBuilder();
        String response = null;

        // Date and time
        DateFormat dateFormat=new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date=new Date();
        String dateAndTime = dateFormat.format(date);

        // add args
        /**hh
        responseBody.append("{\n\t");
        responseBody.append("\"args\": {");
        if(requestParameters.args.size()!=0){
            String comma = "";
            for(String s: requestParameters.args){
                String[] temp = s.split("=");
                responseBody.append(comma);
                responseBody.append("\n\t \"");
                responseBody.append(temp[0]);
                responseBody.append("\": \"");
                responseBody.append(temp[1]);
                responseBody.append("\"");
                comma = ",";
            }
            responseBody.append("\n\t}, \n\t");
        } else {
            responseBody.append("},\n\t");
        }
**/
        if(requestParameters.method.equalsIgnoreCase("get")){
            /**hh
            // add headers
            responseBody.append("\"headers\": {");
            if(requestParameters.headers.size()!=0){
                String comma = "";
                for(String s: requestParameters.headers){
                    String[] temp = s.split(":");
                    responseBody.append(comma);
                    responseBody.append("\n\t \"");
                    responseBody.append(temp[0]);
                    responseBody.append("\": \"");
                    responseBody.append(temp[1]);
                    responseBody.append("\"");
                    comma = ",";
                }
                responseBody.append("\n\t}, \n\t");
            } else {
                responseBody.append("},\n\t");
            }

            // add url
            responseBody.append("\"url\": \"");
            responseBody.append(requestParameters.url);
**/

            // list files or send asked file
            if(requestParameters.file.equals("")){
                responseBody.append("\n}\n");
                response = responseBody.toString();
                String responseHeader = okStatus + "\n" ;
//                + server + "\n" + date1 + dateAndTime + "\n"
//                        + contentLength + response.length() + "\n" + connectionStatus + "\n" +
//                        accessControlAllowOrigin + "\n" + accessControlAllowCredentials + "\n";
                response = responseHeader + response;
            } else {
                try{
                    File file = new File(directory);
                    List<String> listOfFiles = new ArrayList<>();
                    for(File f: file.listFiles()){
                        if(!f.isDirectory())
                            listOfFiles.add(f.getName());
                    }
                    if(listOfFiles.size()==0){
                        responseBody.append("\n}\n");
                        response = responseBody.toString();
                        String responseHeader = okStatus + "\n";
//                        + server + "\n" + date1 + dateAndTime + "\n"
//                                + contentLength + response.length() + "\n" + connectionStatus + "\n" +
//                                accessControlAllowOrigin + "\n" + accessControlAllowCredentials + "\n";
                        response = responseHeader + response;
                    } else if(requestParameters.file.equals("/")){
                        // send list of files
//                        responseBody.append("\", \n");
                        responseBody.append("\n");

                        responseBody.append("\t\"files\": {");
                        String comma = "";
                        for(String s: listOfFiles){
                            responseBody.append(comma);
                            responseBody.append("\n\t\t");
                            responseBody.append(s);
                            comma = ",";
                        }
                        responseBody.append("\n\t} \n");
//                        responseBody.append("}\n");
                        response = responseBody.toString();
                        String responseHeader = okStatus + "\n" ;
//                                server + "\n" + date1 + dateAndTime + "\n"
//                                + contentLength + response.length() + "\n" + connectionStatus + "\n" +
//                                accessControlAllowOrigin + "\n" + accessControlAllowCredentials + "\n";
                        response = responseHeader + response;
                    } else {
                        // send file content
                        boolean containsFile = false;
                        for(String s: listOfFiles){
                            String[] temp = s.split("[.]");
                            if(temp[0].equalsIgnoreCase(requestParameters.file)){
                                containsFile = true;
                                if(!requestParameters.file.equalsIgnoreCase(s))
                                    requestParameters.file = s;
                                break;
                            }
                            if(requestParameters.file.equalsIgnoreCase(s)){
                                containsFile = true;
                                break;
                            }
                        }
                        if(!containsFile){
                            //TODO: Requested file does not exist error
//                            responseBody.append("\n}\n");
                            response = responseBody.toString();
                            String responseHeader = fileNotFound + "\n" ;
//                                    + server + "\n" + date1 + dateAndTime + "\n"
//                                    + contentLength + response.length() + "\n" + connectionStatus + "\n" +
//                                    accessControlAllowOrigin + "\n" + accessControlAllowCredentials + "\n";
                            response = responseHeader + response;
                            return response;
                        }
//                        responseBody.append("\", \n");
                                                responseBody.append("\n");

                        responseBody.append("\t\"data\": {\n");
                        BufferedReader outputFileReader = new BufferedReader(new FileReader(directory+requestParameters.file));
                        String line;
                        while((line = outputFileReader.readLine())!=null){
                            responseBody.append("\t");
                            responseBody.append(line);
                        }
                        responseBody.append("\n\t}");
//                        responseBody.append("\n}\n");
                        String contentType = null;
                        String contentDisposition = "Content-Disposition: inline";
                        if(requestParameters.file.contains(".txt")){
                            contentType = "Content-Type: text";
                        } else if(requestParameters.file.contains(".json")){
                            contentType = "Content-Type: application/json";
                        } else if(requestParameters.file.contains(".xml") || requestParameters.file.contains(".html")){
                            contentType = "Content-Type: xml/html";
                        }
                        response = responseBody.toString();
                        String responseHeader = okStatus + "\n" ;
//                        + server + "\n" + date1 + dateAndTime + "\n"
//                                + contentType + "\n" + contentDisposition + "\n" + contentLength + response.length() +
//                                "\n" + connectionStatus + "\n" + accessControlAllowOrigin + "\n" +
//                                accessControlAllowCredentials + "\n";
                        response = responseHeader + response;
                    }
                } catch (NullPointerException e) {
                    System.out.println("No files found in the directory.");
                    responseBody.append("\n}\n");
                    response = responseBody.toString();
                    String responseHeader = fileNotFound + "\n" + server + "\n" + date1 + dateAndTime + "\n"
                            + contentLength + response.length() + "\n" + connectionStatus + "\n" +
                            accessControlAllowOrigin + "\n" + accessControlAllowCredentials + "\n";
                    response = responseHeader + response;

                } catch (Exception e) {
                    System.out.println("Exception while reading file.");
                    responseBody.append("\n}\n");
                    response = responseBody.toString();
                    String responseHeader = errorReadingFile + "\n" + server + "\n" + date1 + dateAndTime + "\n"
                            + contentLength + response.length() + "\n" + connectionStatus + "\n" +
                            accessControlAllowOrigin + "\n" + accessControlAllowCredentials + "\n";
                    response = responseHeader + response;
                }
            }

        } else {
            // Process post queries
            String outputFileName = this.directory;
            BufferedWriter outputFileWriter = null;

            // add data

            responseBody.append("\"data\": {\n\t");
            responseBody.append(requestParameters.postData);
//            responseBody.append("\t},\n\t");
            responseBody.append("}\t");

/**
            // add files and forms response
            responseBody.append("\"files\": {},\n\t");
            responseBody.append("\"forms\": {},\n\t");

            // add headers
            responseBody.append("\"headers\": {");
            if(requestParameters.headers.size()!=0){
//                responseBody.append("\n\t \"Content-Length\": \"");
//                responseBody.append(requestParameters.postData.length());
//                responseBody.append("\"");
                String comma = "";
                for(String s: requestParameters.headers){
                    String[] temp = s.split(":");
                    responseBody.append(comma);
                    responseBody.append("\n\t \"");
                    responseBody.append(temp[0]);
                    responseBody.append("\": \"");
                    responseBody.append(temp[1]);
                    responseBody.append("\"");
                    comma = ",";
                }
                responseBody.append("\n\t}, \n\t");
            } else {
                responseBody.append("},\n\t");
            }
**/
            //add json header
            /**
            responseBody.append("\"json\": ");
            if(requestParameters.postData.charAt(0)=='{' &&
                    requestParameters.postData.charAt(requestParameters.postData.length()-2)=='}'){
                responseBody.append("{\n\t\t");
                responseBody.append(requestParameters.postData);
                responseBody.append("\n\t}, \n\t");
            } else {
                responseBody.append("null,\n\t");
            }

            // add url
            responseBody.append("\"url\": \"");
            responseBody.append(requestParameters.url);
            responseBody.append("\"\n");
            responseBody.append("}\n");**/

            // Add the post data to the file
            if(requestParameters.file.equals("")){
                response = responseBody.toString();
                String responseHeader = okStatus + "\n" ;
//                + server + "\n" + date1 + dateAndTime + "\n"
//                        + contentLength + response.length() + "\n" + connectionStatus + "\n" +
//                        accessControlAllowOrigin + "\n" + accessControlAllowCredentials + "\n";
                response = responseHeader + response;
            } else {
                try {
                    File file = new File(directory);
                    List<String> listOfFiles = new ArrayList<>();
                    for(File f: file.listFiles()){
                        if(!f.isDirectory())
                            listOfFiles.add(f.getName());
                    }

                    boolean containsFile = false;
                    for(String s: listOfFiles){
                        String[] temp = s.split("[.]");
                        if(temp[0].equalsIgnoreCase(requestParameters.file)){
                            containsFile = true;
                            if(!requestParameters.file.equalsIgnoreCase(s))
                                requestParameters.file = s;
                            outputFileName += s;
                            break;
                        }
                        if(requestParameters.file.equalsIgnoreCase(s)){
                            containsFile = true;
                            outputFileName += s;
                            break;
                        }
                    }
                    if(!containsFile){
                        //TODO: Create file and add data

                        if(requestParameters.file.contains(".txt") || requestParameters.file.contains(".json")
                                || requestParameters.file.contains(".xml")){
                            outputFileName += requestParameters.file;
                        } else {
                            outputFileName += requestParameters.file + ".txt";
                        }
                    }

                    // get the FileWriter
                    File outputFile = new File(outputFileName);
                    outputFileWriter = new BufferedWriter(new FileWriter(outputFile,false));

                    outputFileWriter.write(requestParameters.postData);
                    outputFileWriter.flush();
                    outputFileWriter.close();

                    if(!containsFile){
                        response = responseBody.toString();
                        String newFileCreated = "HTTP/1.1 202 NEW FILE CREATED";
                        String responseHeader = newFileCreated + "\n";
//                        + server + "\n" + date1 + dateAndTime + "\n"
//                                + contentLength + response.length() + "\n" + connectionStatus + "\n" +
//                                accessControlAllowOrigin + "\n" + accessControlAllowCredentials + "\n";
                        response = responseHeader + response;
                    } else {
                        response = responseBody.toString();
                        String responseHeader = okStatus + "\n" ;
//                        + server + "\n" + date1 + dateAndTime + "\n"
//                                + contentLength + response.length() + "\n" + connectionStatus + "\n" +
//                                accessControlAllowOrigin + "\n" + accessControlAllowCredentials + "\n";
                        response = responseHeader + response;
                    }

                } catch(NullPointerException e){
                    System.out.println("No files found in the directory.");
                    response = responseBody.toString();
                    String responseHeader = fileNotFound + "\n" ;
//                    + server + "\n" + date1 + dateAndTime + "\n"
//                            + contentLength + response.length() + "\n" + connectionStatus + "\n" +
//                            accessControlAllowOrigin + "\n" + accessControlAllowCredentials + "\n";
                    response = responseHeader + response;
                } catch (Exception e){
                    System.out.println("Exception while reading file.");
                    response = responseBody.toString();
                    String responseHeader = errorReadingFile + "\n" + server + "\n" + date1 + dateAndTime + "\n"
                            + contentLength + response.length() + "\n" + connectionStatus + "\n" +
                            accessControlAllowOrigin + "\n" + accessControlAllowCredentials + "\n";
                    response = responseHeader + response;
                }
            }
        }
        return response;
    }

}

