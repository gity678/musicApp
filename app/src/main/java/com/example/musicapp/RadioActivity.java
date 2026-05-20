package com.example.musicapp;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class RadioActivity extends Activity {

    GridView gridView;
    LinearLayout playerLayout;
    TextView rTitle, rSong;
    ImageView rThumb;
    Button btnPlay, btnPrev, btnNext;
    MediaPlayer mediaPlayer;
    List<JSONObject> radios = new ArrayList<>();
    int cur = -1;
    boolean playing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radio);

        gridView    = findViewById(R.id.gridView);
        playerLayout = findViewById(R.id.playerLayout);
        rTitle      = findViewById(R.id.rTitle);
        rSong       = findViewById(R.id.rSong);
        rThumb      = findViewById(R.id.rThumb);
        btnPlay     = findViewById(R.id.btnPlay);
        btnPrev     = findViewById(R.id.btnPrev);
        btnNext     = findViewById(R.id.btnNext);

        mediaPlayer = new MediaPlayer();

        btnPlay.setOnClickListener(v -> togglePlay());
        btnNext.setOnClickListener(v -> next());
        btnPrev.setOnClickListener(v -> prev());

        gridView.setOnItemClickListener((a, v, i, id) -> playStation(i));

        loadRadios();
    }

    void loadRadios() {
        new Thread(() -> {
            try {
                URL url = new URL("https://radio-worker.ma68.workers.dev/radios");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                JSONArray arr = new JSONArray(sb.toString());
                radios.clear();
                for (int i = 0; i < arr.length(); i++) radios.add(arr.getJSONObject(i));
                runOnUiThread(this::renderGrid);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "فشل الاتصال", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    void renderGrid() {
        List<String[]> items = new ArrayList<>();
        for (JSONObject r : radios) {
            try {
                items.add(new String[]{
                    r.optString("name", "Radio"),
                    r.optString("logo", "")
                });
            } catch (Exception ignored) {}
        }
        gridView.setAdapter(new RadioGridAdapter(items));
    }

    class RadioGridAdapter extends BaseAdapter {
        List<String[]> data;
        RadioGridAdapter(List<String[]> d) { data = d; }

        public int getCount() { return data.size(); }
        public Object getItem(int i) { return data.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int i, View v, android.view.ViewGroup p) {
            if (v == null) {
                v = getLayoutInflater().inflate(R.layout.item_station, p, false);
            }
            String[] item = data.get(i);
            TextView name = v.findViewById(R.id.stationName);
            name.setText(item[0]);

            View card = v.findViewById(R.id.stationCard);
            if (i == cur) {
                card.setBackgroundResource(R.drawable.station_active);
            } else {
                card.setBackgroundResource(R.drawable.station_normal);
            }
            return v;
        }
    }

    void playStation(int i) {
        cur = i;
        renderGrid();
        try {
            JSONObject r = radios.get(i);
            String url = r.optString("url", "");
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                playing = true;
                btnPlay.setText("⏸");
                rTitle.setText(r.optString("name", "Radio"));
                rSong.setText("● LIVE");
                playerLayout.setVisibility(View.VISIBLE);
            });
            mediaPlayer.setOnErrorListener((mp, w, e) -> {
                Toast.makeText(this, "خطأ في التشغيل", Toast.LENGTH_SHORT).show();
                return true;
            });
        } catch (Exception e) {
            Toast.makeText(this, "خطأ", Toast.LENGTH_SHORT).show();
        }
    }

    void togglePlay() {
        if (cur == -1 && !radios.isEmpty()) { playStation(0); return; }
        if (playing) {
            mediaPlayer.pause(); playing = false; btnPlay.setText("▶");
        } else {
            mediaPlayer.start(); playing = true; btnPlay.setText("⏸");
        }
    }

    void next() {
        if (radios.isEmpty()) return;
        playStation((cur + 1) % radios.size());
    }

    void prev() {
        if (radios.isEmpty()) return;
        playStation((cur - 1 + radios.size()) % radios.size());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) mediaPlayer.release();
    }
}