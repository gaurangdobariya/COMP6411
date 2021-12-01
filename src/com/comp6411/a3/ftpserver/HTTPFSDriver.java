package com.comp6411.a3.ftpserver;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HTTPFSDriver {
    public static void main(String[] args) {
        String[] command;
        HTTPFS httpfs = new HTTPFS();
        HTTPFSDriver httpfsDriver = new HTTPFSDriver();
        boolean isVerbose = false;

        // default port number and default directory
        int port = 8080;
        String directory = "server/";

        try {
            BufferedReader input_command = new BufferedReader(new InputStreamReader(System.in));
//            input_command.close();
            command = input_command.readLine().split(" ");
//            String in = "httpfs -v -p 8007";
//            command = in.split(" ");

            // basic checks
            if (!httpfsDriver.checkCommandValidity(command)) {
                return;
            }

            // add to arraylist for further processing
            List<String> inputCommand = new ArrayList<>(Arrays.asList(command));
            inputCommand.remove(0);
            while (inputCommand.size() > 0) {
                if (inputCommand.get(0).equalsIgnoreCase("-v")) {
                    isVerbose = true;
                    inputCommand.remove(0);
                } else if (inputCommand.get(0).equalsIgnoreCase("-p")) {
                    inputCommand.remove(0);
                    port = Integer.parseInt(inputCommand.get(0));
                    inputCommand.remove(0);
                } else if (inputCommand.get(0).equalsIgnoreCase("-d")) {
                    inputCommand.remove(0);
                    directory = directory + inputCommand.get(0) + "/";
                    inputCommand.remove(0);
                }
            }

//            System.out.println("Port: " + port + " |Verbose: " + isVerbose + " |directory: " + directory);
            httpfs.createServer(port, isVerbose, directory);
        } catch (IOException e){
            System.out.println("Input error.");
        } catch(NumberFormatException e){
            System.out.println("Invalid command. Port number should be an integer.");
        }
    }

    boolean checkCommandValidity(String[] command){
        /**
         * Makes initial checks of the command to discard an obviously incorrect command.
         * @param command User command as a String array.
         * @return true, if valid command. Else, false.
         */
        if(command[0].isEmpty()){
            System.out.println("Emtpy command. Here's something that might help.");
            printHelp();
            return false;
        }
        if(!command[0].equalsIgnoreCase("httpfs")){
            System.out.println("Invalid command. Here's something that might help.");
            printHelp();
            return false;
        }
        return true;
    }

    void printHelp(){
        System.out.println("httpfs is a simple file server.\n" +
                "usage: httpfs [-v] [-p PORT] [-d PATH-TO-DIR]\n" +
                "-v\tPrints debugging messages.\n" +
                "-p\tSpecifies the port number that the server will listen and serve at. Default is 8080\n" +
                "-d\tSpecifies the directory that the server will use to read/write " +
                "requested files. Default is the current directory when launching the " +
                "application.");
    }
}
