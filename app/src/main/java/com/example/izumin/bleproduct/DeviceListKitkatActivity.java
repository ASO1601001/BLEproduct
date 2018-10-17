package com.example.izumin.bleproduct;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class DeviceListKitkatActivity extends DeviceListHelper {

    static class DeviceListAdapter extends BaseAdapter
    {
        private ArrayList<BluetoothDevice> mDeviceList;
        private LayoutInflater mInflator;

        public DeviceListAdapter( Activity activity )
        {
            super();
            mDeviceList = new ArrayList<BluetoothDevice>();
            mInflator = activity.getLayoutInflater();
        }

        // リストへの追加
        public void addDevice( BluetoothDevice device )
        {
            if( !mDeviceList.contains( device ) )
            {    // 加えられていなければ加える
                mDeviceList.add( device );
                notifyDataSetChanged();    // ListViewの更新
            }
        }

        // リストのクリア
        public void clear()
        {
            mDeviceList.clear();
            notifyDataSetChanged();    // ListViewの更新
        }

        @Override
        public int getCount()
        {
            return mDeviceList.size();
        }

        @Override
        public Object getItem( int position )
        {
            return mDeviceList.get( position );
        }

        @Override
        public long getItemId( int position )
        {
            return position;
        }

        static class ViewHolder
        {
            TextView deviceName;
            TextView deviceAddress;
        }

        @Override
        public View getView( int position, View convertView, ViewGroup parent )
        {
            DeviceListKitkatActivity.DeviceListAdapter.ViewHolder viewHolder;
            // General ListView optimization code.
            if( null == convertView )
            {
                convertView = mInflator.inflate( R.layout.listitem_device, parent, false );
                viewHolder = new DeviceListKitkatActivity.DeviceListAdapter.ViewHolder();
                viewHolder.deviceAddress = (TextView)convertView.findViewById( R.id.textview_deviceaddress );
                viewHolder.deviceName = (TextView)convertView.findViewById( R.id.textview_devicename );
                convertView.setTag( viewHolder );
            }
            else
            {
                viewHolder = (DeviceListKitkatActivity.DeviceListAdapter.ViewHolder)convertView.getTag();
            }

            BluetoothDevice device     = mDeviceList.get( position );
            String          deviceName = device.getName();
            if( null != deviceName && 0 < deviceName.length() )
            {
                viewHolder.deviceName.setText( deviceName );
            }
            else
            {
                viewHolder.deviceName.setText( R.string.unknown_device );
            }
            viewHolder.deviceAddress.setText( device.getAddress() );

            return convertView;
        }
    }

    // メンバー変数
    private BluetoothAdapter mBluetoothAdapter;        // BluetoothAdapter : Bluetooth処理で必要
    private DeviceListKitkatActivity.DeviceListAdapter mDeviceListAdapter;    // リストビューの内容
    private Handler mHandler;                            // UIスレッド操作ハンドラ : 「一定時間後にスキャンをやめる処理」で必要
    private boolean mScanning = false;                // スキャン中かどうかのフラグ

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDeviceListAdapter.addDevice(device);
                    //mDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, DeviceListHelper.SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list_kitkat);

        // 戻り値の初期化
        setResult( Activity.RESULT_CANCELED );

        // リストビューの設定
        mDeviceListAdapter = new DeviceListKitkatActivity.DeviceListAdapter( this ); // ビューアダプターの初期化
        ListView listView = (ListView)findViewById( R.id.devicelist );    // リストビューの取得
        listView.setAdapter( mDeviceListAdapter );    // リストビューにビューアダプターをセット
        listView.setOnItemClickListener( this ); // クリックリスナーオブジェクトのセット

        // UIスレッド操作ハンドラの作成（「一定時間後にスキャンをやめる処理」で使用する）
        mHandler = new Handler();

        // Bluetoothアダプタの取得
        BluetoothManager bluetoothManager = (BluetoothManager)getSystemService( Context.BLUETOOTH_SERVICE );
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if( null == mBluetoothAdapter )
        {    // デバイス（＝スマホ）がBluetoothをサポートしていない
            Toast.makeText( this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT ).show();
            finish();    // アプリ終了宣言
            return;
        }

        Log.d("openning","Kitkat");
    }

    // 初回表示時、および、ポーズからの復帰時
    @Override
    protected void onResume()
    {
        super.onResume();

        // デバイスのBluetooth機能の有効化要求
        requestBluetoothFeature();

        scanLeDevice(true);
    }

    // 別のアクティビティ（か別のアプリ）に移行したことで、バックグラウンドに追いやられた時
    @Override
    protected void onPause()
    {
        super.onPause();

        // スキャンの停止
        scanLeDevice(false);
    }

    // デバイスのBluetooth機能の有効化要求
    @Override
    protected void requestBluetoothFeature()
    {
        if( mBluetoothAdapter.isEnabled() )
        {
            return;
        }
        // デバイスのBluetooth機能が有効になっていないときは、有効化要求（ダイアログ表示）
        Intent enableBtIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
        startActivityForResult( enableBtIntent, REQUEST_ENABLEBLUETOOTH );
    }

    // 機能の有効化ダイアログの操作結果
    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data )
    {
        switch( requestCode )
        {
            case DeviceListHelper.REQUEST_ENABLEBLUETOOTH: // Bluetooth有効化要求
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

    // リストビューのアイテムクリック時の処理
    @Override
    public void onItemClick( AdapterView<?> parent, View view, int position, long id )
    {
        // クリックされたアイテムの取得
        BluetoothDevice device = (BluetoothDevice)mDeviceListAdapter.getItem( position );
        if( null == device )
        {
            return;
        }
        // 戻り値の設定
        Intent intent = new Intent();
        intent.putExtra( EXTRAS_DEVICE_NAME, device.getName() );
        intent.putExtra( EXTRAS_DEVICE_ADDRESS, device.getAddress() );
        setResult( Activity.RESULT_OK, intent );
        finish();
    }

    // オプションメニュー作成時の処理
    @Override
    public boolean onCreateOptionsMenu( Menu menu )
    {
        getMenuInflater().inflate( R.menu.activity_device_list, menu );
        if( !mScanning )
        {
            menu.findItem( R.id.menuitem_stop ).setVisible( false );
            menu.findItem( R.id.menuitem_scan ).setVisible( true );
            menu.findItem( R.id.menuitem_progress ).setActionView( null );
        }
        else
        {
            menu.findItem( R.id.menuitem_stop ).setVisible( true );
            menu.findItem( R.id.menuitem_scan ).setVisible( false );
            menu.findItem( R.id.menuitem_progress ).setActionView( R.layout.actionbar_indeterminate_progress );
        }
        return true;
    }

    // オプションメニューのアイテム選択時の処理
    @Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
        switch( item.getItemId() )
        {
            case R.id.menuitem_scan:
                scanLeDevice(true);    // スキャンの開始
                break;
            case R.id.menuitem_stop:
                scanLeDevice(false);    // スキャンの停止
                break;
        }
        return true;
    }
}
