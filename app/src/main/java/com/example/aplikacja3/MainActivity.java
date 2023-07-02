package com.example.aplikacja3;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {

    private EditText adresEt;
    private TextView rozmierPlikuTv;
    private TextView typPlikuTv;
    private Button pobierzInfoBt;
    private Button pobierzPlikBt;

    private ProgressBar postepPrb;
    private TextView postepTv;

    private TextView statusTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.adresEt = findViewById(R.id.adres_et);
        this.rozmierPlikuTv = findViewById(R.id.rozmiar_et);
        this.typPlikuTv = findViewById(R.id.typ_et);
        this.pobierzInfoBt = findViewById(R.id.info_bt);
        this.pobierzInfoBt.setOnClickListener(view -> pobierzInfo());
        this.pobierzPlikBt = findViewById(R.id.plik_bt);
        this.pobierzPlikBt.setOnClickListener(view -> pobierzPlik());
        this.postepPrb = findViewById(R.id.progres_pb);
        this.postepTv = findViewById(R.id.progres_et);
        this.statusTv = findViewById(R.id.status_tv);
        this.statusTv.setText("");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        typPlikuTv.setText(sharedPreferences.getString("typ", ""));
        rozmierPlikuTv.setText(String.valueOf(sharedPreferences.getInt("rozmiar", 0)));
        adresEt.setText(sharedPreferences.getString("link", "https://cdn.kernel.org/pub/linux/kernel/v5.x/linux-5.4.36.tar.xz"));

        clearSharedPreferences();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(this.broadcastReceiver, new IntentFilter(DownloadService.POWIADOMIENIE));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(this.broadcastReceiver);
    }

    private void clearSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }

    private void pobierzPlik() {
        pobierzInfo();
        DownloadService.uruchomUsluge(this,this.adresEt.getText().toString());
    }

    private void pobierzInfo() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            final FileInfo fileInfo = pobierzInfo(this.adresEt.getText().toString());
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                this.typPlikuTv.setText(fileInfo.getFileType());
                this.rozmierPlikuTv.setText(Integer.toString(fileInfo.fileSize));
            });
        });
    }


    private FileInfo pobierzInfo(String adres) {
        HttpsURLConnection polaczenie = null;
        final FileInfo fileInfo = new FileInfo();
        try {
            URL url = new URL(adres);
            polaczenie = (HttpsURLConnection) url.openConnection();
            polaczenie.setRequestMethod("GET");
            fileInfo.setFileSize(polaczenie.getContentLength());
            fileInfo.setFileType(polaczenie.getContentType());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if(polaczenie != null){
                polaczenie.disconnect();
            }
        }
        return fileInfo;
    }


    class FileInfo {
        private String fileType;
        private int fileSize;

        public String getFileType() {
            return fileType;
        }

        public void setFileType(String fileType) {
            this.fileType = fileType;
        }

        public int getFileSize() {
            return fileSize;
        }

        public void setFileSize(int fileSize) {
            this.fileSize = fileSize;
        }
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            PostepInfo postepInfo = intent.getParcelableExtra(DownloadService.PROGRESS_INFO_KEY);
            MainActivity.this.postepPrb.setProgress(postepInfo.getProgressValue());
            MainActivity.this.postepTv.setText(Integer.toString((int)(((double)postepInfo.getDownloadBytes()/(double)postepInfo.getFileSize())*100)));
            if (postepInfo.getProgressValue() == 100) {
                MainActivity.this.pobierzPlikBt.setEnabled(true);
            } else {
                MainActivity.this.pobierzPlikBt.setEnabled(false);
            }
            if(postepInfo.getDownloadStatus() == 0){
                MainActivity.this.statusTv.setText("Pobieranie trwa");
            } else if(postepInfo.getDownloadStatus() == 1){
                MainActivity.this.statusTv.setText("Pobieranie zakończone");
            } else {
                MainActivity.this.statusTv.setText("Błąd pobierania");
            }
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("link", MainActivity.this.adresEt.getText().toString());
            editor.putString("typ", MainActivity.this.typPlikuTv.getText().toString());
            editor.putInt("rozmiar", Integer.parseInt(MainActivity.this.rozmierPlikuTv.getText().toString()));
            editor.apply();
        }
    };
}