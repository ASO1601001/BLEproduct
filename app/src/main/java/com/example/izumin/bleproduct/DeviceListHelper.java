package com.example.izumin.bleproduct;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public abstract class DeviceListHelper extends AppCompatActivity implements AdapterView.OnItemClickListener{

    static abstract class DeviceListAdapter extends BaseAdapter {}

    // 定数
    public static final int    REQUEST_ENABLEBLUETOOTH = 1; // Bluetooth機能の有効化要求時の識別コード
    public static final long   SCAN_PERIOD             = 10000;    // スキャン時間。単位はミリ秒。
    public static final  String EXTRAS_DEVICE_NAME      = "DEVICE_NAME";
    public static final  String EXTRAS_DEVICE_ADDRESS   = "DEVICE_ADDRESS";


    // デバイスのBluetooth機能の有効化要求
    abstract protected void requestBluetoothFeature();

    // 機能の有効化ダイアログの操作結果
    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data )
    {
        switch( requestCode )
        {
            case REQUEST_ENABLEBLUETOOTH: // Bluetooth有効化要求
                if( Activity.RESULT_CANCELED == resultCode )
                {    // 有効にされなかった
                    Toast.makeText( this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT ).show();
                    finish();    // アプリ終了宣言
                    return;
                }
                break;
        }
        super.onActivityResult( requestCode, resultCode, data );
    }

    //if (Build.VERSION.SDK_INT >= 23) {

}
