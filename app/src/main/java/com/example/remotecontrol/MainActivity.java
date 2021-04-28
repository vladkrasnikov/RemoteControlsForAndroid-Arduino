package com.example.remotecontrol;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.view.KeyEvent.Callback;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import org.jetbrains.annotations.Nullable;

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener,
        AdapterView.OnItemClickListener,
        View.OnClickListener,
        View.OnTouchListener,
        SeekBar.OnSeekBarChangeListener,
        KeyEvent.Callback{

    private static final String TAG = MainActivity.class.getSimpleName();
    public static boolean FlagEventSent = false;
    public static final int REQUEST_CODE_LOC = 1;

    private static final int REQ_ENABLE_BT = 10;
    public static final int BT_BOUNDED = 21;
    public static final int BT_SEARCH = 22;

    public static final int LED_RED = 30;
    public static final int LED_GREEN = 31;
    private static final int BTN_UP = 8;
    private static final int BTN_DOWN = 2;
    private static final int BTN_LEFT = 4;
    private static final int BTN_RIGHT = 6;
    private static final int BTN_STOP = 0;

    private FrameLayout frameMessage;
    private LinearLayout frameControls;

    private RelativeLayout frameLedControls;
    private Button btnDisconnect;
    private Switch switchRedLed;
    private Switch switchGreenLed;

    private Switch switchEnableBt;
    private Button btnEnableSearch;
    private ProgressBar pbProgress;
    private ListView listBtDevices;

    private Button controlUp;
    private Button controlLeft;
    private Button controlRight;
    private Button controlDown;
    private Button controlStop;

    private SeekBar seekBar;
    private TextView mTextView;

    private BluetoothAdapter bluetoothAdapter;
    private BtListAdapter listAdapter;
    private ArrayList<BluetoothDevice> bluetoothDevices;

    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        frameMessage = findViewById(R.id.frame_message);
        frameControls = findViewById(R.id.frame_control);

        switchEnableBt = findViewById(R.id.switch_enable_bt);
        btnEnableSearch = findViewById(R.id.btn_enable_search);
        pbProgress = findViewById(R.id.pb_progress);
        listBtDevices = findViewById(R.id.lv_bt_device);

        frameLedControls = findViewById(R.id.frameLedControls);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        switchGreenLed = findViewById(R.id.switch_led_green);
        switchRedLed = findViewById(R.id.switch_led_red);

        controlUp = findViewById(R.id.buttonUp);
        controlLeft = findViewById(R.id.buttonLeft);
        controlRight = findViewById(R.id.buttonRight);
        controlDown = findViewById(R.id.buttonDown);
        controlStop = findViewById(R.id.buttonStop);

        controlUp.setOnTouchListener(this);
        controlLeft.setOnTouchListener(this);
        controlRight.setOnTouchListener(this);
        controlDown.setOnTouchListener(this);
        controlStop.setOnClickListener(this);

        seekBar = findViewById(R.id.seekBar);
        mTextView = findViewById(R.id.textView);
        /*
        controlUp.setOnClickListener(this);
        controlLeft.setOnClickListener(this);
        controlRight.setOnClickListener(this);
        controlDown.setOnClickListener(this);
        controlStop.setOnClickListener(this);
        */

        seekBar.setOnSeekBarChangeListener(this);

        switchEnableBt.setOnCheckedChangeListener(this);
        btnEnableSearch.setOnClickListener(this);
        listBtDevices.setOnItemClickListener(this);

        btnDisconnect.setOnClickListener(this);
        switchGreenLed.setOnCheckedChangeListener(this);
        switchRedLed.setOnCheckedChangeListener(this);

        bluetoothDevices = new ArrayList<>();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "onCreate: " + getString(R.string.bluetooth_not_supported));
            finish();
        }

        if (bluetoothAdapter.isEnabled()) {
            showFrameControls();
            switchEnableBt.setChecked(true);
            setListAdapter(BT_BOUNDED);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(receiver);

        if (connectThread != null) {
            connectThread.cancel();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onClick(View v) {
        if (v.equals(btnEnableSearch)) {
            enableSearch();
        } else if (v.equals(btnDisconnect)) {
            // TODO отключение от устройства
            if (connectedThread != null) {
                connectedThread.cancel();
            }

            showFrameControls();
        } else if(v.equals(controlStop)){
            Log.d("Destroyed", "All actions stopped");
            callControls(BTN_STOP, 0);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (parent.equals(listBtDevices)) {
            BluetoothDevice device = bluetoothDevices.get(position);
            if (device != null) {
                connectThread = new ConnectThread(device);
                connectThread.start();
            }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.equals(switchEnableBt)) {
            enableBt(isChecked);

            if (!isChecked) {
                showFrameMessage();
            }
        } else if (buttonView.equals(switchRedLed)) {
            // TODO включение или отключение красного светодиода
            enableLed(LED_RED, isChecked);

        } else if (buttonView.equals(switchGreenLed)) {
            // TODO включение или отключение зеленого светодиода
            enableLed(LED_GREEN, isChecked);

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ENABLE_BT) {
            if (resultCode == RESULT_OK && bluetoothAdapter.isEnabled()) {
                showFrameControls();
                setListAdapter(BT_BOUNDED);
            } else if (resultCode == RESULT_CANCELED) {
                enableBt(true);
            }
        }
    }

    private void showFrameMessage() {
        frameMessage.setVisibility(View.VISIBLE);
        frameLedControls.setVisibility(View.GONE);
        frameControls.setVisibility(View.GONE);
    }

    private void showFrameControls() {
        frameMessage.setVisibility(View.GONE);
        frameLedControls.setVisibility(View.GONE);
        frameControls.setVisibility(View.VISIBLE);
    }

    private void showFrameLedControls() {
        frameLedControls.setVisibility(View.VISIBLE);
        frameMessage.setVisibility(View.GONE);
        frameControls.setVisibility(View.GONE);
    }

    private void enableBt(boolean flag) {
        if (flag) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQ_ENABLE_BT);
        } else {
            bluetoothAdapter.disable();
        }
    }

    private void setListAdapter(int type) {

        bluetoothDevices.clear();
        int iconType = R.drawable.ic_bluetooth_bounded_device;

        switch (type) {
            case BT_BOUNDED:
                bluetoothDevices = getBoundedBtDevices();
                iconType = R.drawable.ic_bluetooth_bounded_device;
                break;
            case BT_SEARCH:
                iconType = R.drawable.ic_bluetooth_search_device;
                break;
        }
        listAdapter = new BtListAdapter(this, bluetoothDevices, iconType);
        listBtDevices.setAdapter(listAdapter);
    }

    private ArrayList<BluetoothDevice> getBoundedBtDevices() {
        Set<BluetoothDevice> deviceSet = bluetoothAdapter.getBondedDevices();
        ArrayList<BluetoothDevice> tmpArrayList = new ArrayList<>();
        if (deviceSet.size() > 0) {
            for (BluetoothDevice device : deviceSet) {
                tmpArrayList.add(device);
            }
        }

        return tmpArrayList;
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    private void enableSearch() {
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        } else {
            accessLocationPermission();
            bluetoothAdapter.startDiscovery();
        }
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            switch (action) {
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    btnEnableSearch.setText(R.string.stop_search);
                    pbProgress.setVisibility(View.VISIBLE);
                    setListAdapter(BT_SEARCH);
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    btnEnableSearch.setText(R.string.start_search);
                    pbProgress.setVisibility(View.GONE);
                    break;
                case BluetoothDevice.ACTION_FOUND:
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        bluetoothDevices.add(device);
                        listAdapter.notifyDataSetChanged();
                    }
                    break;
            }
        }
    };

    /**
     * Запрос на разрешение данных о местоположении (для Marshmallow 6.0)
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void accessLocationPermission() {
        int accessCoarseLocation = this.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        int accessFineLocation = this.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION);

        List<String> listRequestPermission = new ArrayList<String>();

        if (accessCoarseLocation != PackageManager.PERMISSION_GRANTED) {
            listRequestPermission.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (accessFineLocation != PackageManager.PERMISSION_GRANTED) {
            listRequestPermission.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!listRequestPermission.isEmpty()) {
            String[] strRequestPermission = listRequestPermission.toArray(new String[listRequestPermission.size()]);
            this.requestPermissions(strRequestPermission, REQUEST_CODE_LOC);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_LOC:

                if (grantResults.length > 0) {
                    for (int gr : grantResults) {
                        // Check if request is granted or not
                        if (gr != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                    }
                    //TODO - Add your code here to start Discovery
                }
                break;
            default:
                return;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (v.equals(controlUp) && event.getAction() == MotionEvent.ACTION_DOWN && !FlagEventSent) {
            //starting ur action
            FlagEventSent = true;
            Log.d("Activated", "Control UP activated");
            callControls(BTN_UP, seekBar.getProgress());

        } else if (v.equals(controlDown) && event.getAction() == MotionEvent.ACTION_DOWN && !FlagEventSent) {
            //starting ur action
            FlagEventSent = true;
            Log.d("Activated", "Control DOWN activated");
            callControls(BTN_DOWN, seekBar.getProgress());

        } else if (v.equals(controlLeft) && event.getAction() == MotionEvent.ACTION_DOWN && !FlagEventSent) {
            //starting ur action
            FlagEventSent = true;
            Log.d("Activated", "Control LEFT activated");
            callControls(BTN_LEFT, seekBar.getProgress());

        } else if (v.equals(controlRight) && event.getAction() == MotionEvent.ACTION_DOWN && !FlagEventSent) {
            //starting ur action
            FlagEventSent = true;
            Log.d("Activated", "Control RIGHT activated");
            callControls(BTN_RIGHT, seekBar.getProgress());

        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            //stops ur action
            FlagEventSent = false;
            Log.d("Disabled", "Controls disabled");
            callControls(BTN_STOP, 0);

        }
        return false;
    }

    //Seekbar controls
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        mTextView.setText(String.valueOf(seekBar.getProgress())+ "%");
    }

    private class ConnectThread extends Thread {

        private BluetoothSocket bluetoothSocket = null;
        private boolean success = false;

        public ConnectThread(BluetoothDevice device) {
            try {
                Method method = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                bluetoothSocket = (BluetoothSocket) method.invoke(device, 1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                bluetoothSocket.connect();
                success = true;
            } catch (IOException e) {
                e.printStackTrace();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Не могу соединиться!", Toast.LENGTH_SHORT).show();
                    }
                });

                cancel();
            }

            if (success) {
                connectedThread = new ConnectedThread(bluetoothSocket);
                connectedThread.start();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showFrameLedControls();
                    }
                });
            }
        }

        public boolean isConnect() {
            return bluetoothSocket.isConnected();
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread extends Thread {

        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ConnectedThread(BluetoothSocket bluetoothSocket) {
            InputStream inputStream = null;
            OutputStream outputStream = null;

            try {
                inputStream = bluetoothSocket.getInputStream();
                outputStream = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            this.inputStream = inputStream;
            this.outputStream = outputStream;
        }

        @Override
        public void run() {

        }

        public void write(String command) {
            byte[] bytes = command.getBytes();
            if (outputStream != null) {
                try {
                    outputStream.write(bytes);
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void cancel() {
            try {
                inputStream.close();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void enableLed(int led, boolean state) {
        if (connectedThread != null && connectThread.isConnect()) {
            String command = "";

            switch (led) {
                case LED_RED:
                    command = (state) ? "red on#" : "red off#";
                    break;
                case LED_GREEN:
                    command = (state) ? "green on#" : "green off#";
                    break;
            }

            connectedThread.write(command);
        }
    }

    private void callControls(int keydown, int power) {
        if (connectedThread != null && connectThread.isConnect()) {
            String command = "";

            switch (keydown) {
                case BTN_UP:
                    //command = (state) ? "up on#" : "up off#";
                    command = "frwrd" + String.valueOf(power) + "#";
                    break;
                case BTN_DOWN:
                    //command = (state) ? "down on#" : "down off#";
                    command = "bkwrd" + String.valueOf(power) + "#";
                    break;
                case BTN_LEFT:
                   // command = (state) ? "left on#" : "left off#";
                    command = "turnL" + String.valueOf(power) + "#";
                    break;
                case BTN_RIGHT:
                    //command = (state) ? "right on#" : "right off#";
                    command = "turnR" + String.valueOf(power) + "#";
                    break;
                case BTN_STOP:
                    command = "stopC#";
                    break;
            }
            connectedThread.write(command);
        }
    }
}
