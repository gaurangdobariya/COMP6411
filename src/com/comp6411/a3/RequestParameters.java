package com.comp6411.a3;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RequestParameters {
    public List<String> headers = new ArrayList<>();
    public String postData = "";
    public String host;
    public String method;
    public String path;
    public String url;
    public String file = "/";
    public  List<String> args = new ArrayList<>();

    public RequestParameters(String method, String path){
        this.method = method;
        this.path = path;
    }

    public void processMessage(String clientMessage){
        String[] messageParts = clientMessage.split("\r\n");
        List<String> messageList = new ArrayList<>(Arrays.asList(messageParts));
        messageList.remove(0); // removes the method type

        // Add to headers
        while(messageList.size()!=0){
            String temp = messageList.get(0);
            messageList.remove(0);
            headers.add(temp);
            if(temp.contains("Content-Length"))
                break;
            if(temp.contains("Host")){
                String[] buffer = temp.split(" ");
                host = buffer[1];
            }
        }

        // Rest of the part is data
        if(messageList.size()!=0)
            if(messageList.get(0).equals(""))
                messageList.remove(0);
        while(messageList.size()!=0){
            postData += messageList.get(0) + "\n";
            messageList.remove(0);
        }


    }

    public  void processPath(){
        if(path.length()==1){
            return;
        }

        String[] pathParts = path.split("[/]");
        String lastPart = pathParts[pathParts.length-1];
        if(lastPart.contains("?")){
            file = lastPart.substring(0, lastPart.indexOf("?"));
            if(file.equalsIgnoreCase("get") || file.equalsIgnoreCase("post")){
                file = "/";
            }
        } else if(!lastPart.equalsIgnoreCase("get") && !lastPart.equalsIgnoreCase("post")){
            file = lastPart;
        } else{
            file = "/";
        }
        if(path.contains("?")){
            String queryString = path.substring(path.indexOf("?")+1);
            String[] queryParts = queryString.split("&");
            Collections.addAll(args, queryParts);
        }
    }

    public void createURL(){
        url = "http://" + host + path;
    }

    public void printRequestParameters(){
        System.out.println("\n***Printing request parameters***");
        System.out.println("Method: " + method + "\n" +
                "Path: " + path + "\n" +
                "Host: " + host + "\n" +
                "URL: " + url + "\n" +
                "file: " + file + "\n" +
                "Post data: " + postData + "\n" +
                "Headers: ");
        for(String s: headers){
            System.out.println("\t" + s);
        }
        System.out.println("Args: ");
        for(String s: args){
            System.out.println("\t" + s);
        }
        System.out.println("***End of request parameters***\n");
    }
}
