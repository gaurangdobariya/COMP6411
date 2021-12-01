package com.comp6411.a3.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class HTTPCClientDriver {

    public static void main(String[] args) {
        String[] command;
        HTTPC httpc = new HTTPC(true);
        HTTPCClientDriver httpcClientDriver = new HTTPCClientDriver();
        while(true){
            try{
                BufferedReader input_command = new BufferedReader(new InputStreamReader(System.in));
                command = input_command.readLine().split(" ");
                if(!httpcClientDriver.checkCommandValidity(command)) {
                    continue;
                }
                httpc.handleRequest(command);
                System.out.println("");
            } catch (IOException e){
                System.out.println("Input error.");
                e.printStackTrace();
            }
        }
    }

    boolean checkCommandValidity(String[] command){
        /**
         * Makes initial checks of the command to discard an obviously incorrect command.
         * @param command User command as a String array.
         * @return true, if valid command. Else, false.
         */
        if(command[0].isEmpty()){
            System.out.println("Emtpy command. Run \"httpc help\" for help.");
            return false;
        }
        if(!command[0].equalsIgnoreCase("httpc")){
            System.out.println("Invalid command. Run \"httpc help\" for help.");
            return false;
        }
        if(command.length==1){
            System.out.println("Invalid command. Run \"httpc help\" for help.");
            return false;
        }
//        if(!command[1].equalsIgnoreCase("help") && !command[1].equalsIgnoreCase("get") &&
//                !command[1].equalsIgnoreCase("post")){
//            System.out.println("Invalid command. Run \"httpc help\" for help.");
//            return false;
//        }
        return true;
    }
}
