package com.example.sockettransfer;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BluetoothAdapter extends RecyclerView.Adapter<BluetoothAdapter.MyViewHolder> {

    protected Context context;
    private List<BluetoothDevice> blueToothList;
    public BluetoothAdapter(Context context, List<BluetoothDevice> list) {
        this.context = context;
        blueToothList = list;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        int view_id = R.layout.bluetooth_item;
        View view = ((Activity)context).getLayoutInflater().inflate(view_id, viewGroup, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder myViewHolder, int i) {
        BluetoothDevice bluetoothDevice = blueToothList.get(i);
        myViewHolder.bluetoothName.setText(bluetoothDevice.getName());
        myViewHolder.adders.setText(bluetoothDevice.getAddress());
        myViewHolder.areaLL.setOnClickListener(v -> onItemClickListener.onClick(bluetoothDevice));
    }

    @Override
    public int getItemCount() {
        return blueToothList.size();
    }

    public void notifyDataSetChanged(List<BluetoothDevice> list) {
        blueToothList = list;
        notifyDataSetChanged();
    }

    class MyViewHolder extends RecyclerView.ViewHolder {
        TextView bluetoothName, adders;
        RelativeLayout areaLL;
        MyViewHolder(View itemView) {
            super(itemView);
            bluetoothName = itemView.findViewById(R.id.bluetooth_name);
            adders = itemView.findViewById(R.id.bluetooth_adders);
            areaLL = itemView.findViewById(R.id.areaLL);
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private OnItemClickListener onItemClickListener;
    public interface OnItemClickListener { void onClick(BluetoothDevice bluetoothDevice);}
    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }
}