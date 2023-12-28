package com.example.sockettransfer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 蓝牙 Socket 工具
public class BluetoothSocketUtil {

    private static String TAG = "BluetoothSocketUtil";
    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    public BluetoothDevice nowDevice;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Set<BluetoothDevice> pairedDeviceList;
    private List<BluetoothDevice> devices = new ArrayList();
    private ReceiveDataThread receiveDataThread;
    private ListenThread listenThread;

    private final UUID MY_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
//    private final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    private int state = 0;
    private final int STATE_DISCONNECT = 0;
    private final int STATE_CONNECTING = 1;
    private final int STATE_CONNECTED = 2;

    public boolean isConnectedDevice = false;
    public boolean isSendFile = false;
// 单例 ----------------------------------------------------------------
    private static BluetoothSocketUtil bluetoothSocketUtil;
    public static BluetoothSocketUtil getInstance() {
        if (bluetoothSocketUtil == null) {
            bluetoothSocketUtil = new BluetoothSocketUtil();
        }
        return bluetoothSocketUtil;
    }

    public BluetoothSocketUtil() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void init(Context context){
        this.context = context;
        registerBroadcast();
        listen();  // 开启设备连接监听
    }

    public Set<BluetoothDevice> getPairedDeviceList(){
        if(bluetoothAdapter!=null){
            return bluetoothAdapter.getBondedDevices();
        }else {
            return null;
        }
    }
    public void searchDevice(){
        if(bluetoothAdapter==null){return;}
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        devices.clear();
        bluetoothAdapter.startDiscovery();
    }
    public void stopSearch() {
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    public void registerBroadcast() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.bluetooth.device.action.FOUND");
        filter.addAction("android.bluetooth.adapter.action.DISCOVERY_FINISHED");
        filter.addAction("android.bluetooth.device.action.ACL_CONNECTED");
        filter.addAction("android.bluetooth.device.action.ACL_DISCONNECTED");
        context.registerReceiver(receiver, filter);
        Log.e(TAG, "广播注册成功");
    }

    // 蓝牙连接监听广播
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e(TAG, "收到广播: "+action);
            if ("android.bluetooth.device.action.FOUND".equals(action)) {
                BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                if (device.getName() == null) {return;}
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    if (!devices.contains(device)) {
                        devices.add(device);
                        if(onBluetoothSocketWork!=null){onBluetoothSocketWork.onDiscoverNewDevice(devices);}
                    }
                }
            }
        }
    };

    public void listen(){
        if(state!=STATE_DISCONNECT){return;}
        if(listenThread!=null){
            listenThread.cancel();
            listenThread = null;
        }
        listenThread = new ListenThread();
        listenThread.start();
    }

    private class ListenThread extends Thread{
        private BluetoothServerSocket bluetoothServerSocket;
        private boolean listen = false;
        public ListenThread(){
            try {
                bluetoothServerSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("name", MY_UUID);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        @Override
        public void run() {
            listen = true;
            Log.e(TAG, "开启设备连接监听"+listen+"/"+(state==STATE_DISCONNECT) );
            while (listen && state==STATE_DISCONNECT){
                try {
                    bluetoothSocket = bluetoothServerSocket.accept();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (bluetoothSocket != null) {
                    try {
                        Log.e(TAG, "监听到设备连接" );
                        state = STATE_CONNECTING;
                        if(onBluetoothSocketWork!=null){onBluetoothSocketWork.onConnecting();}
                        inputStream = bluetoothSocket.getInputStream();
                        outputStream = bluetoothSocket.getOutputStream();
                        state = STATE_CONNECTED;
                        isConnectedDevice = true;
                        nowDevice = bluetoothSocket.getRemoteDevice();
                        receiveDataThread = new ReceiveDataThread();
                        receiveDataThread.start();  // 开启读数据线程

                        if(onBluetoothSocketWork!=null){onBluetoothSocketWork.onConnected(nowDevice.getName());}
                        EventMsg msg = new EventMsg();
                        msg.msgType = EventMsg.CONNECT_DEVICE;
                        msg.content = nowDevice.getName();
                        EventBus.getDefault().post(msg);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        public void cancel(){
            listen = false;
            try {
                if(bluetoothServerSocket!=null){
                    bluetoothServerSocket.close();
                    bluetoothServerSocket=null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void connect(BluetoothDevice device) {
        Log.e(TAG, "连接设备: " + device.getName()+"/"+state);
        if (state == STATE_CONNECTING || state == STATE_CONNECTED) {return;}
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                    state = STATE_CONNECTING;
                    if(onBluetoothSocketWork!=null){onBluetoothSocketWork.onConnecting();}
                    bluetoothSocket.connect();
                    inputStream = bluetoothSocket.getInputStream();
                    outputStream = bluetoothSocket.getOutputStream();
                    state = STATE_CONNECTED;
                    isConnectedDevice = true;
                    nowDevice = device;
                    receiveDataThread = new ReceiveDataThread();
                    receiveDataThread.start();  // 开启读数据线程

                    if(onBluetoothSocketWork!=null){onBluetoothSocketWork.onConnected(device.getName());}
                    EventMsg msg = new EventMsg();
                    msg.msgType = EventMsg.CONNECT_DEVICE;
                    msg.content = device.getName();
                    EventBus.getDefault().post(msg);
                }catch(Exception e){
                    e.printStackTrace();
                    disconnect();
                }
            }
        }).start();
    }

    private byte[] readBuffer = new byte[1024];
    private class ReceiveDataThread extends Thread{
        private boolean receive = false;
        byte[] buffer = new byte[1024];
        @Override
        public void run() {
            if(inputStream==null){return;}
            receive = true;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (receive){
                try{
                    int size = inputStream.read(buffer);
                    if(size>0){
                        baos.write(buffer, 0, size);
                        readBuffer = baos.toByteArray();
                        receiveData(readBuffer);
                        baos.reset();
                    }else if(size==-1){
                        Log.e(TAG, "BluetoothSocket: 断开了");
                        cancel();
                        disconnect();
                        break;
                    }
                }catch (Exception e){
                    e.printStackTrace();
                    // 断开连接了，通常 inputStream.read 时触发这个
                    Log.e(TAG, "BluetoothSocket: 读取数据错误");
                    cancel();
                    disconnect();

                    EventMsg msg = new EventMsg();
                    msg.msgType = EventMsg.DISCONNECT_DEVICE;
                    EventBus.getDefault().post(msg);
                }
            }
        }

        public void cancel(){
            receive = false;
        }
    }

    /**
     * 自定义一个标识头来描述发送的数据
     * 格式：$*x*$0000xxxx
     * 前五位 "$*x*$" 中的x为可变数字，表示发送数据的类型，我这里用到的是 "1"-文本，"2"-图片，根据实际需求自定义
     * 后八位 "0000xxxx" 为发送数据内容的长度，格式为固定8位的16进制数据，不足8位则高位补0，最多可以表示 0xFFFFFFFF 个字节，如果发送的文件超出了这个范围则需要自行修改
     * 例子：
     * 发送文本数据 "测试" 打包标识 "$*1*$" + 将"测试"以GB18030标准转化为byte[]后的长度(hex) —— "$*1*$00000004"，后续发送转化后的byte[]
     * 发送图片数据 打包标识 "$*2*$" + 读取指定路径的文件byte[]的长度(hex) —— "$*2*$0033CE27"，后续发送读取到的文件byte[]
     **/
    public void send_text(String data_str){
        if(outputStream==null){return;}
        if(isSendFile){return;}
        // 建议使用线程池
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] data_bytes = data_str.getBytes("GB18030");
                    String head = "$*1*$"+String.format("%08X", data_bytes.length);
                    Log.e(TAG, "发送文本，打包标识头: "+head );
                    outputStream.write(head.getBytes(StandardCharsets.UTF_8));
                    outputStream.write(data_bytes);

                    EventMsg msg = new EventMsg();
                    msg.msgType = EventMsg.SEND_TEXT;
                    msg.content = data_str;
                    EventBus.getDefault().post(msg);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void send_file(String path){
        if(outputStream==null){return;}
        if(isSendFile){return;}
        new Thread(new Runnable() {
            @Override
            public void run() {
                File file = new File(path);
                if (!file.exists() || !file.isFile()) {
                    Log.e(TAG, "文件不存在");
                    return;
                }else {
                    Log.e(TAG, "开始发送文件");
                    isSendFile = true;
                }

                byte[] file_byte = fileToBytes(path);
                try {
                    Thread.sleep(100);
                    String head;
                    head = "$*2*$"+String.format("%08X", file_byte.length);
                    Log.e(TAG, "发送文件，打包标识头: "+head );
                    outputStream.write(head.getBytes(StandardCharsets.UTF_8));
                    outputStream.write(file_byte);
                    isSendFile = false;

                    EventMsg msg = new EventMsg();
                    msg.msgType = EventMsg.SEND_FILE;
                    msg.content = path;
                    EventBus.getDefault().post(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "文件发送失败", e);
                    isSendFile = false;
                }
            }
        }).start();
    }


    private boolean startReceiveFile = false;  // 是否开始接收文件数据
    private ByteArrayOutputStream file_bytes_baos = new ByteArrayOutputStream();
    private long file_length = 0;  // 文件数据长度
    private int message_type = 0;  // 消息类型：0-初始状态 1-文本 2-图片
    private void receiveData(byte[] data_bytes) {
        Log.e(TAG, "处理数据长度: "+data_bytes.length );
        // 还没收到标识头，如果一直没有收到就舍弃直到收到标识头为止
        if(!startReceiveFile){
            try{
                // 首先判断收到的数据是否包含了标识头
                String data_str = new String(data_bytes,StandardCharsets.UTF_8);
                int head_index = data_str.indexOf("$*");
//                Pattern pattern = Pattern.compile("\\$\\*\\d\\*\\$");
//                Matcher matcher = pattern.matcher(data_str);
                // 有头
                if(head_index>=0){
                    startReceiveFile = true;
                    String head = data_str.substring(head_index,head_index+13);  // $*1*$00339433
                    String msg_type = head.substring(0,5);  // $*1*$
                    if(msg_type.contains("1")){message_type = 1;} else {message_type = 2;}
                    String length_hex = head.substring(5);  // 00339433
                    file_length = Long.parseLong(length_hex,16);  // 解析文件数据长度
                    Log.e(TAG, "解析标识头 head: "+head+" 文件数据长度："+file_length);

                    file_bytes_baos.write(data_bytes,13,data_bytes.length-13);  // 存储标识以外的文件数据
                    // 如果文本数据的话则只有一波，这时要判断收到的数据总长度是否文件数据长度+标识头数据长度
                    if(data_bytes.length==file_length+13){
                        parseData();
                    }
                }else {
                    Log.e(TAG, "receiveData: 没有头"+data_str );
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        // 后续的都是文件数据
        else {
            try {
                file_bytes_baos.write(data_bytes);  // 保存文件数据
                Log.e(TAG, "总长度: "+file_length+" /已接收长度: "+file_bytes_baos.size());
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "文件数据保存失败");
            }
            // 每次接收完数据判断一下存储的文件数据达到数据长度了吗
            if(file_bytes_baos.size()>=file_length){
                parseData();
            }
        }
    }

    public void parseData(){
        if(message_type==0){return;}
        if(message_type==1){
            String content = "";
            try {
                content = new String(file_bytes_baos.toByteArray(),"GB18030");  // 文本消息直接转码
                Log.e(TAG, "数据接收完毕，文本："+content);
            } catch (Exception e) {
                e.printStackTrace();
            }
            // 初始化状态
            startReceiveFile = false;
            file_bytes_baos.reset();
            file_length = 0;
            message_type = 0;

            EventMsg msg = new EventMsg();
            msg.msgType = EventMsg.RECEIVE_TEXT;
            msg.content = content;
            EventBus.getDefault().post(msg);
        }else if(message_type==2){
            Log.e(TAG, "数据接收完毕，图片" );
            // 保存图片数据
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 默认保存在系统的 Download 目录下，自行处理
                        String imgFilePath= Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/receiveImage.jpg";
                        File imageFile = new File(imgFilePath);
                        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                            fos.write(file_bytes_baos.toByteArray());
                        }
                        // 初始化状态
                        startReceiveFile = false;
                        file_bytes_baos.reset();
                        file_length = 0;
                        message_type = 0;

                        EventMsg msg = new EventMsg();
                        msg.msgType = EventMsg.RECEIVE_FILE;
                        msg.content = imgFilePath;
                        EventBus.getDefault().post(msg);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    // 读取文件数据
    public static byte[] fileToBytes(String filePath){
        File file = new File(filePath);
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024*1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            return bos.toByteArray();
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public void disconnect(){
        try {
            if(inputStream!=null){
                inputStream.close();
                inputStream=null;
            }
            if(outputStream!=null){
                outputStream.close();
                outputStream=null;
            }
            if(receiveDataThread!=null){
                receiveDataThread.cancel();
                receiveDataThread = null;
            }
            if(bluetoothSocket!=null){
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
            if(onBluetoothSocketWork!=null){onBluetoothSocketWork.onDisconnect();}
            state = STATE_DISCONNECT;
            isConnectedDevice = false;
            nowDevice = null;
            listen();  // 断开后重新开启设备连接监听
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void destroy(){
        disconnect();
        if(listenThread!=null){listenThread.cancel();listenThread=null;}
        if(context!=null){context.unregisterReceiver(receiver);}
    }

// 接口 ---------------------------------------------
    public interface OnBluetoothSocketWork{
        void onConnecting();
        void onConnected(String device_name);
        void onDisconnect();
        void onDiscoverNewDevice(List<BluetoothDevice> devices);
    }
    public OnBluetoothSocketWork onBluetoothSocketWork;
    public void setOnBluetoothSocketWork(OnBluetoothSocketWork onBluetoothSocketWork){
        this.onBluetoothSocketWork = onBluetoothSocketWork;
    }

}
