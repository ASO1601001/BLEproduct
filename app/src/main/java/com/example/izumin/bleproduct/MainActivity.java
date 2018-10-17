package com.example.izumin.bleproduct;

import android.app.Activity;
import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.utils.Transformer;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    // 定数（Bluetooth LE Gatt UUID）
    // Public Service
    private static final UUID UUID_SERVICE        = UUID.fromString( "0000180a-0000-1000-8000-00805f9b34fb" );
    private static final UUID UUID_CHARACTERISTIC1 = UUID.fromString( "00002a29-0000-1000-8000-00805f9b34fb" );
    // Battery Service
    private static final UUID UUID_SERVICE_BATTERY = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID UUID_BATTERY         = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    // for Notification
    private static final UUID UUID_SERVICE_PRIVATE = UUID.fromString("039AFFF0-2C94-11E3-9E06-0002A5D5C51B"); //mwm独自のサービス
    private static final UUID UUID_NOTIFY          = UUID.fromString( "039AFFB0-2C94-11E3-9E06-0002A5D5C51B" ); // センサー取得状態
    private static final UUID UUID_NOTIFY_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); //脳波取得状態の通知用
    private static final UUID UUID_NOTIFY2         = UUID.fromString("039AFFA0-2C94-11E3-9E06-0002A5D5C51B"); //脳波取得開始のUUID
    private static final UUID UUID_NOTIFY3         = UUID.fromString("039AFFF6-2C94-11E3-9E06-0002A5D5C51B"); //脳波Rawデータ

    // 定数
    private static final int REQUEST_ENABLEBLUETOOTH = 1; // Bluetooth機能の有効化要求時の識別コード
    private static final int REQUEST_CONNECTDEVICE   = 2; // デバイス接続要求時の識別コード

    // メンバー変数
    private BluetoothAdapter mBluetoothAdapter;    // BluetoothAdapter : Bluetooth処理で必要
    private String        mDeviceAddress = "";    // デバイスアドレス
    private BluetoothGatt mBluetoothGatt = null;    // Gattサービスの検索、キャラスタリスティックの読み書き

    // GUIアイテム
    private Button   mButton_Connect;    // 接続ボタン
    private Button   mButton_Disconnect;    // 切断ボタン
//    private Button   mButton_ReadChara1;    // キャラクタリスティック１の読み込みボタン
    private Button   mButton_ReadChara2;    // キャラクタリスティック２の読み込みボタン
    private Button   mButton_WriteStart;        // キャラクタリスティック２への「脳波取得開始フラグ」書き込みボタン
    private Button   mButton_WriteStop;        // キャラクタリスティック２への「脳波取得停止フラグ」書き込みボタン

    //グラフアイテム
    // LineChartView
    private LineChart mLineChart;
    // 値をプロットするx座標
    private float mX = 0;
    // loopフラグ
    private boolean mIsLoop = true;
    int hertz;

    // 脳波取得中の処理を別スレッドで並行処理するための宣言
    private Handler mHandler = new Handler();
    // 別スレ生成 -> 開始
    private String s;

    //runGyroでジャイロ出力するための宣言
    private TextView[] tx = new TextView[6];
    int gyro;
    int j = 0;

    //ファイル出力用
    File directory;
    String filename;
    final DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
    Date date;


    // BluetoothGattコールバック
    private final BluetoothGattCallback mGattcallback = new BluetoothGattCallback()
    {
        // 接続状態変更（connectGatt()の結果として呼ばれる。）
        @Override
        public void onConnectionStateChange( BluetoothGatt gatt, int status, int newState ) {
            if (BluetoothGatt.GATT_SUCCESS != status) {
                return;
            }

            if (BluetoothProfile.STATE_CONNECTED == newState) {    // 接続完了
                mBluetoothGatt.discoverServices();    // サービス検索
                runOnUiThread(new Runnable() {
                    public void run() {
                        // GUIアイテムの有効無効の設定
                        // 切断ボタンを有効にする
                        mButton_Disconnect.setEnabled(true);
                    }
                });
                return;
            }
            if (BluetoothProfile.STATE_DISCONNECTED == newState) {    // 切断完了（接続可能範囲から外れて切断された）
                // 接続可能範囲に入ったら自動接続するために、mBluetoothGatt.connect()を呼び出す。
                Log.d("services1","終わってる");
                mBluetoothGatt.connect();
                runOnUiThread(new Runnable() {
                    public void run() {
                        // GUIアイテムの有効無効の設定
                        // 読み込みボタンを無効にする（通知チェックボックスはチェック状態を維持。通知ONで切断した場合、再接続時に通知は再開するので）
//                        mButton_ReadChara1.setEnabled(false);
                        mButton_ReadChara2.setEnabled(false);
                        mButton_WriteStart.setEnabled( false );
                        mButton_WriteStop.setEnabled( false );
                    }
                });
                return;
            }
        }

        // サービス検索が完了したときの処理（mBluetoothGatt.discoverServices()の結果として呼ばれる。）
        @Override
        public void onServicesDiscovered( BluetoothGatt gatt, int status )
        {
            if( BluetoothGatt.GATT_SUCCESS != status )
            {
                return;
            }

            // 発見されたサービスのループ
            for( BluetoothGattService service : gatt.getServices() )
            {
                // サービスごとに個別の処理
                if( ( null == service ) || ( null == service.getUuid() ) )
                {
                    continue;
                }
//                if( UUID_SERVICE.equals( service.getUuid() ) )
//                {    // プライベートサービス
//                    runOnUiThread( new Runnable()
//                    {
//                        public void run()
//                        {
//                            // GUIアイテムの有効無効の設定
//                            mButton_ReadChara1.setEnabled( true );
//                        }
//                    } );
//                    continue;
//                }
                if(UUID_SERVICE_BATTERY.equals( service.getUuid() ))
                {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //GUIアイテムの有効無効の設定
                            mButton_ReadChara2.setEnabled( true );
                        }
                    });
                }
                if(UUID_SERVICE_PRIVATE.equals( service.getUuid() ))
                {
                    runOnUiThread( new Runnable()
                    {
                        public void run()
                        {
                            // GUIアイテムの有効無効の設定
                            mButton_WriteStart.setEnabled( true );
                            mButton_WriteStop.setEnabled( true );
                        }
                    } );
                }
            }
        }

        // キャラクタリスティックが読み込まれたときの処理
        @Override
        public void onCharacteristicRead( BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status )
        {
            if( BluetoothGatt.GATT_SUCCESS != status )
            {
                return;
            }
            Log.d("private1","UUID="+characteristic.getUuid().toString());
            // キャラクタリスティックごとに個別の処理
//            if( UUID_CHARACTERISTIC1.equals( characteristic.getUuid() ) )
//            {    // キャラクタリスティック１：データサイズは、2バイト（数値を想定。0～65,535）
////                byte[]       byteChara = characteristic.getValue();
////                ByteBuffer bb        = ByteBuffer.wrap( byteChara );
////                final String strChara  = String.valueOf( bb.getShort() );
//                final String strChara = characteristic.getStringValue(0);
//                runOnUiThread( new Runnable()
//                {
//                    public void run()
//                    {
//                        // GUIアイテムへの反映
//                        ( (TextView)findViewById( R.id.textview_readchara1 ) ).setText( strChara );
//                    }
//                } );
//                return;
//            }
            if( UUID_BATTERY.equals( characteristic.getUuid() ) )
            {    // キャラクタリスティック２：データサイズは、8バイト（文字列を想定。半角文字8文字）
                byte[] b = characteristic.getValue();
                final String strChara = String.valueOf(b[0]);
                runOnUiThread( new Runnable()
                {
                    public void run()
                    {
                        // GUIアイテムへの反映
                        ( (TextView)findViewById( R.id.textview_readchara2 ) ).setText( strChara );
                    }
                } );
                return;
            }
        }

        // キャラクタリスティック変更が通知されたときの処理
        @Override
        public void onCharacteristicChanged( BluetoothGatt gatt, BluetoothGattCharacteristic characteristic )
        {
            // キャラクタリスティックごとに個別の処理
            if( UUID_NOTIFY.equals( characteristic.getUuid() ) )
            {    // キャラクタリスティック１：データサイズは、2バイト（数値を想定。0～65,535）
                byte[]       byteChara = characteristic.getValue();
                ByteBuffer   bb        = ByteBuffer.wrap( byteChara );
                final String strChara  = String.valueOf( bb.get() );
                runOnUiThread( new Runnable()
                {
                    public void run()
                    {
                        // GUIアイテムへの反映
                        ( (TextView)findViewById( R.id.textview_notifychara1 ) ).setText( strChara );
                    }
                } );
                return;
            }
            //実際にとりたい脳波データ
            if( UUID_NOTIFY3.equals( characteristic.getUuid() ) )
            {    // キャラクタリスティック１：データサイズは、2バイト（数値を想定。0～65,535）
                byte[]            byteChara = characteristic.getValue();
                final  ByteBuffer bb        = ByteBuffer.wrap( byteChara );
                final String strChara = "";

                //別スレッドたてる
                HandlerThread handlerThread = new HandlerThread("graph");
                handlerThread.start();

                //作成したHandlerThread(別スレ)内部のLooperを引数として、HandlerThread(のLooper)にメッセージを送るHandlerを生成する。
                Handler handler = new Handler(handlerThread.getLooper());
                //Handlerのpostメソッドでメッセージ(タスク：重たい処理)を送信する。
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        //グラフ出力処理
                        runGraph(bb);
                    }
                });

                //別スレッドたてる
                HandlerThread handlerThread1 = new HandlerThread("gyro");
                handlerThread1.start();

                //作成したHandlerThread(別スレ)内部のLooperを引数として、HandlerThread(のLooper)にメッセージを送るHandlerを生成する。
                Handler handler1 = new Handler(handlerThread1.getLooper());
                //Handlerのpostメソッドでメッセージ(タスク：重たい処理)を送信する。
                handler1.post(new Runnable() {
                    @Override
                    public void run() {
                        //ジャイロ出力処理
                        runGyro(bb);
                    }
                });

                HandlerThread handlerThread2 = new HandlerThread("outputText");
                handlerThread2.start();

                //作成したHandlerThread(別スレ)内部のLooperを引数として、HandlerThread(のLooper)にメッセージを送るHandlerを生成する。
                Handler handler2 = new Handler(handlerThread2.getLooper());
                //Handlerのpostメソッドでメッセージ(タスク：重たい処理)を送信する。
                handler2.post(new Runnable() {
                    @Override
                    public void run() {
                        //テキストファイル出力処理
//                        runGyro(bb);
                    }
                });

                //データ内容参照用、xmlのコメントアウトしてるのも解除してください
//                StringBuilder v = new StringBuilder("");
//                for(byte b : byteChara){
//                    v.append(b+" ");
//                }
//                final String strChara = v.substring(0);
//                Log.d("脳波",strChara);
                return;
            }
        }

        // キャラクタリスティックが書き込まれたときの処理
        @Override
        public void onCharacteristicWrite( BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status )
        {
            if( BluetoothGatt.GATT_SUCCESS != status )
            {
                mButton_WriteStart.setEnabled( false );
                mButton_WriteStop.setEnabled( false );
                return;
            }
            // キャラクタリスティックごとに個別の処理
            if( UUID_NOTIFY2.equals( characteristic.getUuid() ) )
            {    // キャラクタリスティック２：データサイズは、8バイト（文字列を想定。半角文字8文字）
                runOnUiThread( new Runnable()
                {
                    public void run()
                    {
                        // GUIアイテムの有効無効の設定
                        // 書き込みボタンを有効にする
                        mButton_WriteStart.setEnabled( true );
                        mButton_WriteStop.setEnabled( true );
                    }
                } );
                return;
            }
        }
    };

    public void runGraph(ByteBuffer bb){
        for(int i = 14; i < 94; i += 2){
            hertz += bb.getShort(i);
        }
        hertz = hertz / 80;
        updateGraph(hertz);
        hertz = 0;
    }

    //ジャイロデータの取得と、Viewへの反映
    public void runGyro(ByteBuffer bb){
        for(int i = 2; i < 14; i += 2){
            gyro = bb.getShort(i);
            //MainActivityへのジャイロ値の出力
            updateGyro(j,gyro);
            j++;
        }
//        updateGyro(0,bb);
        j = 0;
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // GUIアイテム
        mButton_Connect = (Button)findViewById( R.id.button_connect );
        mButton_Connect.setOnClickListener( this );
        mButton_Disconnect = (Button)findViewById( R.id.button_disconnect );
        mButton_Disconnect.setOnClickListener( this );
//        mButton_ReadChara1 = (Button)findViewById( R.id.button_readchara1 );
//        mButton_ReadChara1.setOnClickListener( this );
        mButton_ReadChara2 = (Button)findViewById( R.id.button_readchara2 );
        mButton_ReadChara2.setOnClickListener( this );
        mButton_WriteStart = (Button)findViewById( R.id.button_writehello );
        mButton_WriteStart.setOnClickListener( this );
        mButton_WriteStop = (Button)findViewById( R.id.button_writeworld );
        mButton_WriteStop.setOnClickListener( this );
        tx[0] = findViewById(R.id.textview_acc0);
        tx[1] = findViewById(R.id.textview_acc1);
        tx[2] = findViewById(R.id.textview_acc2);
        tx[3] = findViewById(R.id.textview_gyro3);
        tx[4] = findViewById(R.id.textview_gyro4);
        tx[5] = findViewById(R.id.textview_gyro5);

        directory = getExternalFilesDir(null);
        if(!directory.exists())
        {
            boolean res0=directory.mkdirs();
            Log.i("mkdir","result="+res0);
        }

        int[] idList = {1,2,3};
        String[] nameList = {"佐藤", "鈴木", "高橋"};
        generateFile();
        exportCsv(idList, nameList);

        // グラフViewを初期化する
        initChart();

        // Android端末がBLEをサポートしてるかの確認
        if( !getPackageManager().hasSystemFeature( PackageManager.FEATURE_BLUETOOTH_LE ) )
        {
            Toast.makeText( this, R.string.ble_is_not_supported, Toast.LENGTH_SHORT ).show();
            finish();    // アプリ終了宣言
            return;
        }
        // Bluetoothアダプタの取得
        BluetoothManager bluetoothManager = (BluetoothManager)getSystemService( Context.BLUETOOTH_SERVICE );
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if( null == mBluetoothAdapter )
        {    // Android端末がBluetoothをサポートしていない
            Toast.makeText( this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT ).show();
            finish();    // アプリ終了宣言
            return;
        }
    }

    // 初回表示時、および、ポーズからの復帰時
    @Override
    protected void onResume()
    {
        super.onResume();

        // Android端末のBluetooth機能の有効化要求
        requestBluetoothFeature();

        // GUIアイテムの有効無効の設定
        mButton_Connect.setEnabled( false );
        mButton_Disconnect.setEnabled( false );
//        mButton_ReadChara1.setEnabled( false );
        mButton_ReadChara2.setEnabled( false );
        mButton_WriteStart.setEnabled( false );
        mButton_WriteStop.setEnabled( false );

        // デバイスアドレスが空でなければ、接続ボタンを有効にする。
        if( !mDeviceAddress.equals( "" ) )
        {
            mButton_Connect.setEnabled( true );
        }

        // 接続ボタンを押す
        mButton_Connect.callOnClick();
    }

    // 別のアクティビティ（か別のアプリ）に移行したことで、バックグラウンドに追いやられた時
    @Override
    protected void onPause()
    {
        super.onPause();

        // 切断
        disconnect();
    }

    // アクティビティの終了直前
    @Override
    protected void onDestroy()
    {
        mIsLoop = false;
        super.onDestroy();

        if( null != mBluetoothGatt )
        {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }




    // Android端末のBluetooth機能の有効化要求
    private void requestBluetoothFeature()
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
            case REQUEST_ENABLEBLUETOOTH: // Bluetooth有効化要求
                if( Activity.RESULT_CANCELED == resultCode )
                {    // 有効にされなかった
                    Toast.makeText( this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT ).show();
                    finish();    // アプリ終了宣言
                    return;
                }
                break;
            case REQUEST_CONNECTDEVICE: // デバイス接続要求
                String strDeviceName;
                if( Activity.RESULT_OK == resultCode )
                {
                    // デバイスリストアクティビティからの情報の取得
                    strDeviceName = data.getStringExtra( DeviceListHelper.EXTRAS_DEVICE_NAME );
                    mDeviceAddress = data.getStringExtra( DeviceListHelper.EXTRAS_DEVICE_ADDRESS );
                }
                else
                {
                    strDeviceName = "";
                    mDeviceAddress = "";
                }
                ( (TextView)findViewById( R.id.textview_devicename ) ).setText( strDeviceName );
                ( (TextView)findViewById( R.id.textview_deviceaddress ) ).setText( mDeviceAddress );
//                ( (TextView)findViewById( R.id.textview_readchara1 ) ).setText( "" );
                ( (TextView)findViewById( R.id.textview_readchara2 ) ).setText( "" );
                ( (TextView)findViewById( R.id.textview_notifychara1 ) ).setText( "" );
//                ( (TextView)findViewById( R.id.textview_notifychara2 ) ).setText( "" );
                break;
        }
        super.onActivityResult( requestCode, resultCode, data );
    }

    // オプションメニュー作成時の処理
    @Override
    public boolean onCreateOptionsMenu( Menu menu )
    {
        getMenuInflater().inflate( R.menu.activity_main, menu );
        return true;
    }

    // オプションメニューのアイテム選択時の処理
    @Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
        switch( item.getItemId() )
        {
            case R.id.menuitem_search:
                Intent devicelistactivityIntent;
                if (Build.VERSION.SDK_INT >= 21) {
                    Log.d("繊維","21");
                    devicelistactivityIntent = new Intent(this, DeviceListMarshActivity.class);
                }else{
                    Log.d("繊維","20");
                    devicelistactivityIntent = new Intent(this, DeviceListKitkatActivity.class);
                }
                startActivityForResult( devicelistactivityIntent, REQUEST_CONNECTDEVICE );
                return true;
        }
        return false;
    }

    @Override
    public void onClick( View v )
    {
        if( mButton_Connect.getId() == v.getId() )
        {
            mButton_Connect.setEnabled( false );    // 接続ボタンの無効化（連打対策）
            connect();            // 接続
            return;
        }
        if( mButton_Disconnect.getId() == v.getId() )
        {
            mButton_Disconnect.setEnabled( false );    // 切断ボタンの無効化（連打対策）
            disconnect();            // 切断
            return;
        }
//        if( mButton_ReadChara1.getId() == v.getId() )
//        {
//            readCharacteristic( UUID_SERVICE, UUID_CHARACTERISTIC1 );
//            return;
//        }
        if( mButton_ReadChara2.getId() == v.getId() )
        {
            readCharacteristic( UUID_SERVICE_BATTERY, UUID_BATTERY );
            return;
        }
        if( mButton_WriteStart.getId() == v.getId() )
        {
            mButton_WriteStart.setEnabled( false );    // 書き込みボタンの無効化（連打対策）
            mButton_WriteStop.setEnabled( false );    // 書き込みボタンの無効化（連打対策）
            setCharacteristicNotification( UUID_SERVICE_PRIVATE, UUID_NOTIFY, true );
            mHandler.postDelayed(new Runnable() {
                                     public void run() {
                                         setCharacteristicNotification( UUID_SERVICE_PRIVATE, UUID_NOTIFY3, true );
                                     }},200);
            final byte[] start = generateByte(1);
            mHandler.postDelayed(new Runnable() {
                                     public void run() {
                                         writeCharacteristic( UUID_SERVICE_PRIVATE, UUID_NOTIFY2, start );
                                     }},500); //書き込む内容はbyteにする
            return;
        }
        if( mButton_WriteStop.getId() == v.getId() )
        {
            mButton_WriteStart.setEnabled( false );    // 書き込みボタンの無効化（連打対策）
            mButton_WriteStop.setEnabled( false );    // 書き込みボタンの無効化（連打対策）
            byte[] stop = generateByte(0);
            writeCharacteristic( UUID_SERVICE_PRIVATE, UUID_NOTIFY2, stop ); //書き込む内容はbyteにする
            return;
        }
    }

    // 接続
    private void connect()
    {
        if( mDeviceAddress.equals( "" ) )
        {    // DeviceAddressが空の場合は処理しない
            return;
        }

        if( null != mBluetoothGatt )
        {    // mBluetoothGattがnullでないなら接続済みか、接続中。
            return;
        }

        // 接続
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice( mDeviceAddress );

        //apiレベルによって、接続方法を変更
        if (Build.VERSION.SDK_INT >= 23) {
            mBluetoothGatt = device.connectGatt(this, false, mGattcallback,2);
        }else{
            //4.4のドロイドと、イヤホンがつながらないし、そもそもcalbackが動かない
            mBluetoothGatt = device.connectGatt(this, false, mGattcallback);
        }
    }

    // 切断
    private void disconnect()
    {
        if( null == mBluetoothGatt )
        {
            return;
        }

        // 切断
        //   mBluetoothGatt.disconnect()ではなく、mBluetoothGatt.close()しオブジェクトを解放する。
        //   理由：「ユーザーの意思による切断」と「接続範囲から外れた切断」を区別するため。
        //   ①「ユーザーの意思による切断」は、mBluetoothGattオブジェクトを解放する。再接続は、オブジェクト構築から。
        //   ②「接続可能範囲から外れた切断」は、内部処理でmBluetoothGatt.disconnect()処理が実施される。
        //     切断時のコールバックでmBluetoothGatt.connect()を呼んでおくと、接続可能範囲に入ったら自動接続する。
        mBluetoothGatt.close();
        mBluetoothGatt = null;
        // GUIアイテムの有効無効の設定
        // 接続ボタンのみ有効にする
        mButton_Connect.setEnabled( true );
        mButton_Disconnect.setEnabled( false );
//        mButton_ReadChara1.setEnabled( false );
        mButton_ReadChara2.setEnabled( false );
        mButton_WriteStart.setEnabled( false );
        mButton_WriteStop.setEnabled( false );
    }

    // キャラクタリスティックの読み込み
    private void readCharacteristic( UUID uuid_service, UUID uuid_characteristic )
    {
        if( null == mBluetoothGatt )
        {
            return;
        }
        BluetoothGattCharacteristic blechar = mBluetoothGatt.getService( uuid_service ).getCharacteristic( uuid_characteristic );
//        String a = String.valueOf(blechar.getWriteType());
//        Log.d("private1", "blechar="+a);
        mBluetoothGatt.readCharacteristic( blechar );
    }

    // キャラクタリスティック通知の設定
    private void setCharacteristicNotification( UUID uuid_service, UUID uuid_characteristic, boolean enable )
    {
        if( null == mBluetoothGatt )
        {
            return;
        }
        BluetoothGattCharacteristic blechar = mBluetoothGatt.getService( uuid_service ).getCharacteristic( uuid_characteristic );
        mBluetoothGatt.setCharacteristicNotification( blechar, enable );
        //ディスクリプタをキャラによって変えないとかも？
        BluetoothGattDescriptor descriptor = blechar.getDescriptor(UUID_NOTIFY_DESCRIPTOR);
        descriptor.setValue( BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE );
        mBluetoothGatt.writeDescriptor( descriptor );
    }

    // キャラクタリスティックの書き込み
    private void writeCharacteristic( UUID uuid_service, UUID uuid_characteristic, byte[] bytein )
    {
        if( null == mBluetoothGatt )
        {
            return;
        }
        BluetoothGattCharacteristic blechar = mBluetoothGatt.getService( uuid_service ).getCharacteristic( uuid_characteristic );
        blechar.setValue(bytein);
        mBluetoothGatt.writeCharacteristic( blechar );
    }

    //脳波測定開始のためのbyte配列を作成
    private byte[] generateByte(int j){
        byte[] aaa = new byte[20];
        ByteBuffer byteBuffer = ByteBuffer.wrap(aaa);
        int checkSum = 0;
        int mode;
        if(j == 1) {
            mode = 1;
        }else{
            mode = 0;
        }

        //書き込みに必要なタイムスタンプ取得
        Calendar cal = Calendar.getInstance();
        TimeZone tz = TimeZone.getTimeZone("UTC");
        cal.setTimeZone(tz);
        SimpleDateFormat sdf = new SimpleDateFormat("yy");
        int y = Integer.parseInt(sdf.format(cal.getTime()));
        //MMddHHmmss
        sdf.applyPattern("MM");
        int m = Integer.parseInt(sdf.format(cal.getTime()));
        sdf.applyPattern("dd");
        int d = Integer.parseInt(sdf.format(cal.getTime()));
        sdf.applyPattern("HH");
        int h = Integer.parseInt(sdf.format(cal.getTime()));
        sdf.applyPattern("mm");
        int mm = Integer.parseInt(sdf.format(cal.getTime()));
        sdf.applyPattern("ss");
        int s = Integer.parseInt(sdf.format(cal.getTime()));

        byteBuffer.put(0, (byte)119); //0x77固定
        byteBuffer.put(1, (byte)1); //0x01固定
        byteBuffer.put(2, (byte)mode); //モード(0=停止, 1: BLEにてEEG送信,  2: 内蔵メモリへEEGを保存)
        byteBuffer.put(3, (byte)1); //内蔵メモリがフルの時の動作(0=停止, 1=上書き)
        byteBuffer.put(4, (byte)y); //タイムスタンプ：西暦(2000年を0とします)
        byteBuffer.put(5, (byte)m);
        byteBuffer.put(6, (byte)d);
        byteBuffer.put(7, (byte)h);
        byteBuffer.put(8, (byte)mm);
        byteBuffer.put(9, (byte)s); //タイムスタンプ：秒
        byteBuffer.putInt(10, 0); //９桁はリザーブ
        byteBuffer.putInt(14, 0);
        byteBuffer.put(18, (byte)0); //ここまでリザーブ

        //18桁までを合計する
        for(int i=1; i<19; i++) {
            checkSum += byteBuffer.get(i);
        }
        //ここからチェックサムの書き込み処理
        byte[] ddd = ByteBuffer.allocate(4).putInt(checkSum).array();
        byte du = ddd[3];
        byteBuffer.put(19, (byte)~du); //チェックサム(offset1～18までの合計の1補数値)
        return byteBuffer.array();
    }



    /*------- ここからジャイロ出力処理 -------*/
    private void updateGyro(final int j,int value){
        final String val = String.valueOf(value);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("スレッド","tx="+tx[j].getId());
                tx[j].setText(val);
            }
        });
    }


    /*------- ここからグラフ処理 -------*/
    /**
     * グラフViewの初期化
     */
    private void initChart() {
        // 線グラフView
        mLineChart = (LineChart) findViewById(R.id.chart_DynamicLineGraph);

        // グラフ説明テキストを表示するか
        mLineChart.getDescription().setEnabled(true);
        // グラフ説明テキストのテキスト設定
        mLineChart.getDescription().setText(getResources().getString(R.string.DynamicLineGraph_Description));
        // グラフ説明テキストの文字色設定
        mLineChart.getDescription().setTextColor(Color.BLACK);
        // グラフ説明テキストの文字サイズ設定
        mLineChart.getDescription().setTextSize(10f);
        // グラフ説明テキストの表示位置設定
        mLineChart.getDescription().setPosition(0, 0);

        // グラフへのタッチジェスチャーを有効にするか
        mLineChart.setTouchEnabled(false);

        // グラフのスケーリングを有効にするか
        mLineChart.setScaleEnabled(true);
        //mLineChart.setScaleXEnabled(true);     // X軸のみに対しての設定
        //mLineChart.setScaleYEnabled(true);     // Y軸のみに対しての設定

        // グラフのドラッギングを有効にするか
        mLineChart.setDragEnabled(true);

        // グラフのピンチ/ズームを有効にするか
        mLineChart.setPinchZoom(true);

        // グラフの背景色設定
        mLineChart.setBackgroundColor(Color.WHITE);

        // 空のデータをセットする
        mLineChart.setData(new LineData());

        // Y軸(左)の設定
        // Y軸(左)の取得
        YAxis leftYAxis = mLineChart.getAxisLeft();
        // Y軸(左)の最大値設定
        leftYAxis.setAxisMaximum(20000f);
        // Y軸(左)の最小値設定
        leftYAxis.setAxisMinimum(-20000f);

        // Y軸(右)の設定
        // Y軸(右)は表示しない
        mLineChart.getAxisRight().setEnabled(false);

        // X軸の設定
        XAxis xAxis = mLineChart.getXAxis();
        // X軸の値表示設定
        xAxis.setDrawGridLines(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);//横軸の数字を下部に表示する
    }

    /**
     * グラフの更新
     */
    private void updateGraph(int hertz) {
        // 線の情報を取得
        LineData lineData = mLineChart.getData();
        if(lineData == null) {
            return;
        }

        // 0番目の線を取得
        LineDataSet lineDataSet = (LineDataSet) lineData.getDataSetByIndex(0);
        // 0番目の線が初期化されていない場合は初期化する
        if(lineDataSet == null) {
            // LineDataSetオブジェクト生成
            lineDataSet = new LineDataSet(null, getResources().getString(R.string.sine_wave));
            // 線の色設定
            lineDataSet.setColor(Color.rgb(0xb9, 0x40, 0x47));
            // 線にプロット値の点を描画しない
            lineDataSet.setDrawCircles(false);
            // 線にプロット値の値テキストを描画しない
            lineDataSet.setDrawValues(false);
            // 線を追加
            lineData.addDataSet(lineDataSet);
        }
        // y値を作成
//        float val = (float) Math.sin((Math.PI / 10) * mX);
        // 0番目の線に値を追加
        lineData.addEntry(new Entry(mX, hertz), 0);

        // 値更新通知
        mLineChart.notifyDataSetChanged();

        // X軸に表示する最大のEntryの数を指定
        mLineChart.setVisibleXRangeMaximum(15);

        // オシロスコープのように古いデータを左に寄せていくように表示位置をずらす
        mLineChart.moveViewTo(mX, getVisibleYCenterValue(mLineChart), YAxis.AxisDependency.LEFT);

        mX++;
    }

    /**
     * 表示しているY座標の中心値を返す<br>
     *     基準のY座標は左
     * @param lineChart 対象のLineChart
     * @return 表示しているY座標の中心値
     */
    private float getVisibleYCenterValue(LineChart lineChart) {
//        Transformer transformer = lineChart.getTransformer(YAxis.AxisDependency.LEFT);
//        ViewPortHandler viewPortHandler = lineChart.getViewPortHandler();
//
//        float highestVisibleY = (float) transformer.getValuesByTouchPoint(viewPortHandler.contentLeft(),
//                viewPortHandler.contentTop()).y;
//        float highestY = Math.min(lineChart.getAxisLeft().mAxisMaximum, highestVisibleY);
//
//        float lowestVisibleY = (float) transformer.getValuesByTouchPoint(viewPortHandler.contentLeft(),
//                viewPortHandler.contentBottom()).y;
//        float lowestY = Math.max(lineChart.getAxisLeft().mAxisMinimum, lowestVisibleY);

//        return highestY - (Math.abs(highestY - lowestY) / 2);
        return 0;
    }



    /*------- ここからファイル出力処理 -------*/
    public void generateFile(){
        date = new Date(System.currentTimeMillis());
        filename = directory.getAbsolutePath() + "/TGAT2_Raw_" + df.format(date) + ".csv";
        try {
            // 出力ファイルの作成（Android/data/com.example.izumin.bleproduct）
            FileWriter f = new FileWriter(filename);
            PrintWriter p = new PrintWriter(new BufferedWriter(f));

            // ヘッダーを指定する
            p.print("No.");
            p.print(",");
            p.print("Header");
            p.println();

            p.close();

        }catch (Exception ex) {
            ex.printStackTrace();
            Log.d("ファイル","ファイル出力失敗！");
        }
    }

    public void exportCsv(int[] idList, String[] nameList){
        try {
            // 出力ファイルの作成（Android/data/com.example.izumin.bleproduct）
            FileWriter f = new FileWriter(filename,true);
            PrintWriter p = new PrintWriter(new BufferedWriter(f));

            // 内容をセットする
            for(int i = 0; i < idList.length; i++){
                p.print(idList[i]);
                p.print(",");
                p.print(nameList[i]);
                p.println();    // 改行
            }

            // ファイルに書き出し閉じる
            p.close();

            Log.d("ファイル","とじたよ");

        } catch (Exception ex) {
            ex.printStackTrace();
            Log.d("ファイル","ファイル出力失敗！");
        }

    }

}

