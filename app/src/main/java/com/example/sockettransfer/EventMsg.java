package com.example.sockettransfer;

public class EventMsg {

    public final static int SEND_TEXT = 0;
    public final static int SEND_FILE = 1;
    public final static int RECEIVE_TEXT = 2;
    public final static int RECEIVE_FILE = 3;
    public final static int CONNECT_DEVICE = 4;
    public final static int DISCONNECT_DEVICE = 5;

    public int msgType;
    public String content = "";

}
