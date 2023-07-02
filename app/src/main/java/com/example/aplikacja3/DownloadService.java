package com.example.aplikacja3;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class DownloadService extends IntentService {

    public static final String ADRES_KEY = "ADRES_KEY";
    private static final int ROZMIAR_BLOKU = 32767;
    private static final String ID_KANALU = "com.example.aplikacja3.DownloadService";
    private static final int ID_POWIADOMIENIA = 1;
    public static final String POWIADOMIENIE = "com.example.aplikacja3.powiadomienia";
    public static final String PROGRESS_INFO_KEY = "PROGRESS_INFO_KEY";
    private NotificationManager managerPowiadomien;
    private PostepInfo postepInfo;

    public DownloadService() {

        super("DownloadService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if(intent == null) {
            return;
        }
        final String adres = intent.getStringExtra(ADRES_KEY);
        przygotujKanalPowiadomien();
        wykonajZadanie(adres);
    }

    private void wykonajZadanie(String adres) {
        FileOutputStream strumienDoPliku = null;
        HttpURLConnection polaczenie = null;
        try {
            URL url = new URL(adres);
            polaczenie = (HttpURLConnection) url.openConnection();
            this.postepInfo= new PostepInfo(polaczenie.getContentLength());
            this.postepInfo.setFileSize(polaczenie.getContentLength());
            File plikRoboczy = new File(url.getFile());
            File plikWyjsciowy = new File(getBaseContext().getFilesDir().getPath() + File.separator + plikRoboczy.getName());
            if (plikWyjsciowy.exists()) {
                plikWyjsciowy.delete();
            }
            DataInputStream czytnik = new DataInputStream(polaczenie.getInputStream());
            strumienDoPliku = new FileOutputStream(plikWyjsciowy.getPath());
            byte bufor[] = new byte[ROZMIAR_BLOKU];
            int pobrano = czytnik.read(bufor, 0, ROZMIAR_BLOKU);
            while (pobrano != -1)
            {
                strumienDoPliku.write(bufor, 0, pobrano);
                this.postepInfo.increaseDownloadBytes(pobrano);
                managerPowiadomien.notify(ID_POWIADOMIENIA, utworzPowiadomienie());
                this.wyslijBroadcast();
                Log.d("DownloadService","Pobrano bajtów: " + this.postepInfo.getDownloadBytes());
                pobrano = czytnik.read(bufor, 0, ROZMIAR_BLOKU);
            }
            this.postepInfo.setDownloadFinished();
        } catch (Exception e) {
            this.postepInfo.setDownloadStatus(PostepInfo.DOWNLOAD_ERROR);
            e.printStackTrace();
        }
        finally {
            if(polaczenie != null){
                polaczenie.disconnect();
            }
            if(strumienDoPliku != null){
                try {
                    strumienDoPliku.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void wyslijBroadcast() {
        Intent zamiar = new Intent(POWIADOMIENIE);
        zamiar.putExtra(PROGRESS_INFO_KEY, this.postepInfo);
        LocalBroadcastManager.getInstance(this).sendBroadcast(zamiar);
    }
    public static void uruchomUsluge(Context context, String adres) {
        Intent zamiar = new Intent(context, DownloadService.class);
        zamiar.putExtra(ADRES_KEY, adres);
        context.startService(zamiar);
    }

    private void przygotujKanalPowiadomien() {
        this.managerPowiadomien = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            NotificationChannel kanal = new NotificationChannel(ID_KANALU, name, NotificationManager.IMPORTANCE_LOW);
            this.managerPowiadomien.createNotificationChannel(kanal);
        }
    }

    private Notification utworzPowiadomienie() {
        Intent intencjaPowiadomienia = new Intent(this, MainActivity.class);
        TaskStackBuilder budowniczyStosu = TaskStackBuilder.create(this);
        budowniczyStosu.addParentStack(MainActivity.class);
        budowniczyStosu.addNextIntent(intencjaPowiadomienia);
        PendingIntent intencjaOczekujaca = budowniczyStosu.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder budowniczyPowiadomien = new Notification.Builder(this);
        budowniczyPowiadomien.setContentTitle(getString(R.string.powiadomienie_tytul))
                .setProgress(100, this.postepInfo.getProgressValue(), false)
                .setContentIntent(intencjaOczekujaca)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setWhen(System.currentTimeMillis())
                .setPriority(Notification.PRIORITY_HIGH);

        if(this.postepInfo.isDownloadFinished()){
            budowniczyPowiadomien.setOngoing(false);
        } else {
            budowniczyPowiadomien.setOngoing(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            budowniczyPowiadomien.setChannelId(ID_KANALU);
        }

        return budowniczyPowiadomien.build();


    }

}
