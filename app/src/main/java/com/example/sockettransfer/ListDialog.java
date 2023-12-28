package com.example.sockettransfer;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ListDialog extends DialogFragment{

    private final String TAG = "ListDialog";
    Context my_context;
    TextView refresh_paired,connect_status,refresh_new,disconnect;
    RecyclerView paired_devices,new_devices;
    BluetoothAdapter pairedListAdapter,newListAdapter;
    Set<BluetoothDevice> pairedList;
    List<BluetoothDevice> newDeviceList = new ArrayList<>();

    public ListDialog(Context context){
        my_context = context;
    }
    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            DisplayMetrics dm = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
            dialog.getWindow().setLayout((int) (dm.widthPixels * 0.8), (int) (dm.heightPixels * 0.5));
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View viewContent = inflater.inflate(R.layout.dialog_list, container, false);
        getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);  // 去掉dialog默认标题
        pairedListAdapter = new BluetoothAdapter(my_context,new ArrayList<>());
        pairedListAdapter.setOnItemClickListener(new BluetoothAdapter.OnItemClickListener() {
            @Override
            public void onClick(BluetoothDevice bluetoothDevice) {
                BluetoothSocketUtil.getInstance().connect(bluetoothDevice);
            }
        });
        paired_devices = viewContent.findViewById(R.id.paired_devices);
        paired_devices.setAdapter(pairedListAdapter);
        paired_devices.setLayoutManager(new LinearLayoutManager(my_context));

        refresh_paired = viewContent.findViewById(R.id.refresh_paired);
        refresh_paired.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(android.bluetooth.BluetoothAdapter.getDefaultAdapter().isEnabled()){
                    pairedList = BluetoothSocketUtil.getInstance().getPairedDeviceList();
                    if(pairedList == null || pairedList.size()<=0){
                        Toast.makeText(my_context,"暂无已连接蓝牙",0).show();
                    }else {
                        pairedListAdapter.notifyDataSetChanged(new ArrayList<>(pairedList));
                    }
                }else {
                    Intent enableBtIntent = new Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    ((Activity)my_context).startActivityForResult(enableBtIntent, 6666);
                    Toast.makeText(my_context,"请先开启蓝牙",0).show();
                }
            }
        });

        refresh_new = viewContent.findViewById(R.id.refresh_new);
        refresh_new.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ContextCompat.checkSelfPermission(my_context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    // 蓝牙扫描需要这个权限
                    ActivityCompat.requestPermissions((Activity) my_context, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 666);
                    Toast.makeText(my_context,"蓝牙扫描需要位置权限",0).show();
                    return;
                }

                if(android.bluetooth.BluetoothAdapter.getDefaultAdapter().isEnabled()){
                    BluetoothSocketUtil.getInstance().searchDevice();
                }else {
                    Intent enableBtIntent = new Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    ((Activity)my_context).startActivityForResult(enableBtIntent, 6666);
                    Toast.makeText(my_context,"请先开启蓝牙",0).show();
                }
            }
        });


        newListAdapter = new BluetoothAdapter(my_context,new ArrayList<>());
        new_devices = viewContent.findViewById(R.id.new_devices);
        new_devices.setAdapter(newListAdapter);
        new_devices.setLayoutManager(new LinearLayoutManager(my_context));
        newListAdapter.setOnItemClickListener(new BluetoothAdapter.OnItemClickListener() {
            @Override
            public void onClick(BluetoothDevice bluetoothDevice) {
                BluetoothSocketUtil.getInstance().connect(bluetoothDevice);
            }

        });

        connect_status = viewContent.findViewById(R.id.connect_status);
        if(BluetoothSocketUtil.getInstance().isConnectedDevice){
            connect_status.setText(BluetoothSocketUtil.getInstance().nowDevice.getName());
            pairedList = BluetoothSocketUtil.getInstance().getPairedDeviceList();
            if(pairedList.size()<=0){
                //
            }else {
                pairedListAdapter.notifyDataSetChanged(new ArrayList<>(pairedList));
            }
        }

        disconnect = viewContent.findViewById(R.id.disconnect);
        disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BluetoothSocketUtil.getInstance().disconnect();
            }
        });

        BluetoothSocketUtil.getInstance().setOnBluetoothSocketWork(new BluetoothSocketUtil.OnBluetoothSocketWork() {
            @Override
            public void onConnecting() {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        connect_status.setText("正在连接");
                    }
                });
            }

            @Override
            public void onConnected(String device_name) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        connect_status.setText(device_name);
                    }
                });
            }

            @Override
            public void onDisconnect() {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        connect_status.setText("未连接");
                    }
                });
            }

            @Override
            public void onDiscoverNewDevice(List<BluetoothDevice> devices) {
                newDeviceList = devices;
                if(newDeviceList.size()>0){
                    Log.e(TAG, "更新蓝牙列表" );
                    newListAdapter.notifyDataSetChanged(newDeviceList);
                }
            }
        });

        return viewContent;
    }


    @Override
    public void onStop() {
        BluetoothSocketUtil.getInstance().stopSearch();
        super.onStop();
    }
}
