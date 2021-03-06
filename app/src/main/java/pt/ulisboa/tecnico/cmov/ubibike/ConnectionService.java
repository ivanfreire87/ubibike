package pt.ulisboa.tecnico.cmov.ubibike;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Messenger;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;


import pt.inesc.termite.wifidirect.SimWifiP2pBroadcast;
import pt.inesc.termite.wifidirect.SimWifiP2pDevice;
import pt.inesc.termite.wifidirect.SimWifiP2pDeviceList;
import pt.inesc.termite.wifidirect.SimWifiP2pManager;
import pt.inesc.termite.wifidirect.SimWifiP2pManager.PeerListListener;
import pt.inesc.termite.wifidirect.service.SimWifiP2pService;
import pt.inesc.termite.wifidirect.sockets.SimWifiP2pSocket;
import pt.inesc.termite.wifidirect.sockets.SimWifiP2pSocketManager;
import pt.inesc.termite.wifidirect.sockets.SimWifiP2pSocketServer;



/**
 * Created by ivanf on 09/05/2016.
 */
public class ConnectionService extends Service implements PeerListListener , LocationListener {
    private SimWifiP2pManager mManager = null;
    private SimWifiP2pManager.Channel mChannel = null;
    private Messenger mService = null;
    private SimWifiP2pSocketServer mSrvSocket = null;
    private SimWifiP2pSocket mCliSocket = null;
    public boolean mBound = true;
    Activity bindedActivity;
    InetAddress ip;
    List<LatLng> routePoints = new ArrayList<LatLng>();

    private Socket clientSocket;
    private String serverIp;
    private PrintWriter printwriter;


    Callbacks activity;
    protected SimWifiP2pBroadcastReceiver mReceiver;

    @Override
    public void onCreate() {
        Log.d("MainActivity", "CREATED SERVICE ");


        Intent intent = new Intent(this, SimWifiP2pService.class);

        // initialize the WDSim API
        SimWifiP2pSocketManager.Init(getApplicationContext());

        // register broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_NETWORK_MEMBERSHIP_CHANGED_ACTION);
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_GROUP_OWNERSHIP_CHANGED_ACTION);

        mReceiver = new SimWifiP2pBroadcastReceiver(this);
        registerReceiver(mReceiver, filter);

        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        LocationManager lManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);


        if ( Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission( this, android.Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission( this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "No permission ");
            return;
        }
        lManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 1, this);

        // spawn the chat server background task
        new IncomingCommTask().executeOnExecutor(
                AsyncTask.THREAD_POOL_EXECUTOR);



        Toast.makeText(this, "Connection Service Created", Toast.LENGTH_SHORT).show();

    }

    public List<LatLng> getTrack(){
        return routePoints;
    }

    @Override
    public void onLocationChanged(Location location) {
        LatLng newLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        routePoints.add(newLatLng);
        activity.sendTrack(routePoints);
        Log.d("MainActivity", "Location Changed " + location.toString());
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    //////////////// binder

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        ConnectionService getService() {
            // Return this instance of LocalService so clients can call public methods
            return ConnectionService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    //Here Activity register to the service as Callbacks client
    public void registerClient(Activity activity){
        this.activity = (Callbacks)activity;
        bindedActivity = activity;
    }

    //////////////// methods to comunicate with binded activity

    public String sendMessageToCentralServer(String message){

        Object response = null;

        //connect to center server
        AsyncTask connectingToServerCommTask = new ConnectingToServerCommTask().executeOnExecutor(
                AsyncTask.THREAD_POOL_EXECUTOR,
                message);
        try {
            response = connectingToServerCommTask.get();

        }catch(Exception e){
            Log.d("MainActivity", "DEBUG connectingToServerCommTask InterruptedException "+ e);
        }

        return response.toString();
    }

    public void inRange() {
        mManager.requestPeers(mChannel,ConnectionService.this);
    }

    public void sendMessage(String message){

        new SendMessageCommTask().executeOnExecutor(
                AsyncTask.THREAD_POOL_EXECUTOR,
                message);
    }

    public void sendPoints(String message){

        new SendPointsCommTask().executeOnExecutor(
                AsyncTask.THREAD_POOL_EXECUTOR,
                message);
    }

    public void disconnect(){
        if (mCliSocket != null) {
            try {
                mCliSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mCliSocket = null;
    }

    public void connect(String message){
        new OutgoingCommTask().executeOnExecutor(
                AsyncTask.THREAD_POOL_EXECUTOR,
                message);
    }


    // Asynk tasks
    public class SendMessageCommTask extends AsyncTask<String, String, Void> {

        @Override
        protected Void doInBackground(String... msg) {

            try {
                mCliSocket.getOutputStream().write((msg[0] + "\n").getBytes());
                Log.d("MainActivity", "DEBUG send " + msg[0]);

                BufferedReader sockIn = new BufferedReader(
                        new InputStreamReader(mCliSocket.getInputStream()));
                sockIn.readLine();

            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            activity.eraseInput();
            activity.guiUpdateDisconnectedState();
        }
    }

    public class SendPointsCommTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... msg) {

            try {
                mCliSocket.getOutputStream().write((msg[0] + "\n").getBytes());
                Log.d("MainActivity", "DEBUG send points " + msg[0]);

                BufferedReader sockIn = new BufferedReader(
                        new InputStreamReader(mCliSocket.getInputStream()));
                sockIn.readLine();

            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            activity.eraseInput();
            activity.guiUpdateDisconnectedState();
        }
    }

    public class OutgoingCommTask extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {

            activity.setValidationOutput("Connecting...");
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                mCliSocket = new SimWifiP2pSocket(params[0],
                        Integer.parseInt(getString(R.string.port)));
            } catch (UnknownHostException e) {
                return "Unknown Host:" + e.getMessage();
            } catch (IOException e) {
                return "IO error:" + e.getMessage();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                activity.guiUpdateDisconnectedState();
                activity.setValidationOutput(result);
            } else {
                activity.GuiUpdateConnectedState();
            }
        }
    }

    public class IncomingCommTask extends AsyncTask<Void, String, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            try {
                mSrvSocket = new SimWifiP2pSocketServer(
                        Integer.parseInt(getString(R.string.port)));
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    SimWifiP2pSocket sock = mSrvSocket.accept();
                    try {
                        BufferedReader sockIn = new BufferedReader(
                                new InputStreamReader(sock.getInputStream()));
                        String st = sockIn.readLine();
                        publishProgress(st);
                        sock.getOutputStream().write(("\n").getBytes());
                    } catch (IOException e) {
                        Log.d("Error reading socket:", e.getMessage());
                    } finally {
                        sock.close();
                    }
                } catch (IOException e) {
                    Log.d("Error socket:", e.getMessage());
                    break;
                    //e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            activity.appendValuesOutput(values[0] + "\n");
        }
    }

    public class ConnectingToServerCommTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground( String... clientMsg) {
            Log.d("MainActivity", "DEBUG connecting... " + clientMsg[0]);
            String message = null;

            try {

                clientSocket = null;
                ObjectOutputStream oos = null;
                ObjectInputStream ois = null;
                String sentMessage = clientMsg[0];

                serverIp = /*ip.getHostAddress();*/ "192.168.1.93";
                Log.d("MainActivity", "DEBUG PRE connecting to  " + serverIp);
                clientSocket = new Socket(serverIp, 4444);
                Log.d("MainActivity", "DEBUG connected to  " + serverIp);

                //write to socket using ObjectOutputStream
                oos = new ObjectOutputStream(clientSocket.getOutputStream());

                oos.writeObject(sentMessage);
                Log.d("MainActivity", "DEBUG Message sent to the server : " + sentMessage);
                //read the server response message
                ois = new ObjectInputStream(clientSocket.getInputStream());
                message = (String) ois.readObject();
                //close resources
                ois.close();
                oos.close();

                Log.d("MainActivity", "DEBUG message from server  " + message);

            } catch (UnknownHostException e) {
                Log.d("MainActivity", "DEBUG UnknownHostException " + e.toString() );
                e.printStackTrace();
            } catch (IOException e) {
                Log.d("MainActivity", "DEBUG IOException2 " + e.toString());
                e.printStackTrace();
            }
            catch (Exception e) {
                Log.d("MainActivity", "DEBUG IOException2 " + e.toString());
                e.printStackTrace();
            }finally {
                //Closing the socket
                try {
                    Log.d("MainActivity", "DEBUG closing socket  ");
                    clientSocket.close();
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
            }
        return message;
        }


    }

    private ServiceConnection mConnection = new ServiceConnection() {
        // callbacks for service binding, passed to bindService()

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {

            mService = new Messenger(service);
            mManager = new SimWifiP2pManager(mService);
            mChannel = mManager.initialize(getApplication(), getMainLooper(), null);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {

        }
    };

    @Override
    public void onPeersAvailable(SimWifiP2pDeviceList peers) {

        List<CharSequence> charSequences = new ArrayList<>();
        CharSequence[] devices;

        // compile list of devices in range
        for (SimWifiP2pDevice device : peers.getDeviceList()) {
            String devstr = "" + device.deviceName + " (" + device.getVirtIp() + ")\n";
            charSequences.add(devstr);
        }

        devices = charSequences.toArray(new
                CharSequence[charSequences.size()]);

        // display list of devices in range
        activity.displayDevicesInRange(devices);

    }


    //callbacks interface for communication with service clients!
    public interface Callbacks{
        public void eraseInput();
        public void GuiUpdateConnectedState();
        public void guiUpdateDisconnectedState();
        public void appendValuesOutput(String s);
        public void setValidationOutput(String s);
        public void displayDevicesInRange(CharSequence[] devices);
        public void sendTrack(List<LatLng> list);
    }

}
