package com.example.sockettransfer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ImageView connect;
    private TextView title,log,send_file,send_text;
    private EditText content;
    private ListDialog bluetoothList;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.gray));  // 设置状态栏灰色
        EventBus.getDefault().register(this);
        BluetoothSocketUtil.getInstance().init(this);
        init_control();
        checkFilePermission();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(EventMsg msg){
        int msgType = msg.msgType;
        switch (msgType){
            case EventMsg.CONNECT_DEVICE:
                title.setText(msg.content);
                log.append("连接成功: "+msg.content+"\n");
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                break;
            case EventMsg.DISCONNECT_DEVICE:
                title.setText("未连接");
                log.append("连接断开"+msg.content+"\n");
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                break;
            case EventMsg.SEND_TEXT:
                log.append("me: "+msg.content+"\n");
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                content.setText("");
                break;
            case EventMsg.SEND_FILE:
                log.append("me: 发送文件-"+msg.content+"\n");
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                break;
            case EventMsg.RECEIVE_TEXT:
                log.append("she: "+msg.content+"\n");
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                break;
            case EventMsg.RECEIVE_FILE:
                log.append("she: 发送文件-"+msg.content+"\n");
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                break;

            default:return;
        }
    }

    private void init_control(){
        connect = findViewById(R.id.connect);
        title = findViewById(R.id.title);
        log = findViewById(R.id.log);
        content = findViewById(R.id.content);
        send_file = findViewById(R.id.send_file);
        send_text = findViewById(R.id.send_text);
        scrollView = findViewById(R.id.scrollView);

        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PopupMenu popupMenu = new PopupMenu(MainActivity.this,view);
                popupMenu.getMenuInflater().inflate(R.menu.message_menu,popupMenu.getMenu());
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        String title = menuItem.getTitle().toString();
                        if(title.equals("连接设备")){
                            showBluetoothList();
                        } else {
                            // 默认是不可被发现的，设置蓝牙可被搜索
                            if (BluetoothAdapter.getDefaultAdapter().getScanMode() !=
                                    android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                                Intent discoverableIntent = new Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 60);
                                startActivity(discoverableIntent);
                                Toast.makeText(MainActivity.this,"请开启设备检测",0).show();
                            }
                        }
                        return false;
                    }
                });
                popupMenu.show();
            }
        });

        send_text.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String content_str = content.getText().toString();
                if(content_str.isEmpty()){
                    Toast.makeText(MainActivity.this,"内容为空!",0).show();
                    return;
                }
                BluetoothSocketUtil.getInstance().send_text(content_str);
            }
        });

        // 发送文件，这里我写死了发送
        send_file.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!checkFilePermission()){return;}
//                String path = "/storage/emulated/0/DCIM/Camera/c13f2a8ca5b96fd11a3398d21e95d6e.jpg";
                String path = "/storage/emulated/0/DCIM/Camera/IMG_20220805_150334.jpg";
                BluetoothSocketUtil.getInstance().send_file(path);
            }
        });

    }

    private void showBluetoothList(){
        if(bluetoothList==null){
            bluetoothList = new ListDialog(this);
        }
        if(bluetoothList.isAdded()){return;}
        bluetoothList.show(getFragmentManager(),"");
    }

    private boolean checkFilePermission(){
        boolean haveAllPermission = false;
        boolean haveReadPermission = false;
        boolean haveWritePermission = false;

        // 检查所有文件权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if(!Environment.isExternalStorageManager()){
                Log.e(TAG, "没有所有文件权限");
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this,"发送文件需要所有文件权限",0).show();
            } else {
                Log.e(TAG, "已有所有文件权限");
                haveAllPermission = true;
            }
        }else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                // 蓝牙扫描需要这个权限
                ActivityCompat.requestPermissions((Activity) this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 666);
                Toast.makeText(this,"发送文件需要读取文件权限",0).show();
                haveReadPermission = false;
            }
            haveReadPermission = true;
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                // 蓝牙扫描需要这个权限
                ActivityCompat.requestPermissions((Activity) this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 666);
                Toast.makeText(this,"发送文件需要写入文件权限",0).show();
                haveWritePermission = false;
            }
            haveWritePermission = true;
        }
        Log.e(TAG, "文件权限："+haveAllPermission+"/"+haveReadPermission+"/"+haveWritePermission);
        return haveAllPermission || (haveReadPermission && haveWritePermission);
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        BluetoothSocketUtil.getInstance().destroy();
        super.onDestroy();
    }
}