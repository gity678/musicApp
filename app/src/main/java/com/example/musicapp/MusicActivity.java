package com.example.musicapp;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class MusicActivity extends Activity {

    ListView listView;
    TextView pTitle, ct, tt;
    SeekBar seekBar;
    Button btnPlay, btnPrev, btnNext;
    LinearLayout playerLayout;
    MediaPlayer mediaPlayer;
    Handler handler = new Handler();
    List<JSONObject> songs = new ArrayList<>();
    List<JSONObject> filtered = new ArrayList<>();
    int cur = -1;
    boolean playing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music);

        listView   = findViewById(R.id.listView);
        pTitle     = findViewById(R.id.pTitle);
        ct         = findViewById(R.id.ct);
        tt         = findViewById(R.id.tt);
        seekBar    = findViewById(R.id.seekBar);
        btnPlay    = findViewById(R.id.btnPlay);
        btnPrev    = findViewById(R.id.btnPrev);
        btnNext    = findViewById(R.id.btnNext);
        playerLayout = findViewById(R.id.playerLayout);

        mediaPlayer = new MediaPlayer();
        loadSongs();

        btnPlay.setOnClickListener(v -> togglePlay());
        btnNext.setOnClickListener(v -> next());
        btnPrev.setOnClickListener(v -> prev());

        listView.setOnItemClickListener((a, v, i, id) -> playSong(i));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && mediaPlayer != null) mediaPlayer.seekTo(p);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        mediaPlayer.setOnCompletionListener(mp -> next());

        handler.post(updateSeek);
    }

    void loadSongs() {
        new Thread(() -> {
            try {
                URL url = new URL("https://music-worker.ma68.workers.dev/songs");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                JSONArray arr = new JSONArray(sb.toString());
                songs.clear();
                for (int i = 0; i < arr.length(); i++) songs.add(arr.getJSONObject(i));
                filtered = new ArrayList<>(songs);
                runOnUiThread(this::renderList);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "فشل الاتصال", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    String getTitle(JSONObject s) {
        try {
            if (s.has("title") && !s.getString("title").isEmpty()) return s.getString("title");
            return s.getString("id").replace("music/", "").replace("_", " ");
        } catch (Exception e) { return "Unknown"; }
    }

    void renderList() {
        List<String> titles = new ArrayList<>();
        for (JSONObject s : filtered) titles.add(getTitle(s));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_list_item_1, titles);
        listView.setAdapter(adapter);
    }

    void playSong(int i) {
        cur = i;
        try {
            JSONObject s = filtered.get(i);
            String url = s.getString("url");
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                playing = true;
                btnPlay.setText("⏸");
                pTitle.setText(getTitle(s));
                playerLayout.setVisibility(View.VISIBLE);
                seekBar.setMax(mp.getDuration());
            });
        } catch (Exception e) {
            Toast.makeText(this, "خطأ", Toast.LENGTH_SHORT).show();
        }
    }

    void togglePlay() {
        if (cur == -1 && !filtered.isEmpty()) { playSong(0); return; }
        if (playing) {
            mediaPlayer.pause(); playing = false; btnPlay.setText("▶");
        } else {
            mediaPlayer.start(); playing = true; btnPlay.setText("⏸");
        }
    }

    void next() {
        if (filtered.isEmpty()) return;
        playSong((cur + 1) % filtered.size());
    }

    void prev() {
        if (filtered.isEmpty()) return;
        playSong((cur - 1 + filtered.size()) % filtered.size());
    }

    String fmt(int ms) {
        int s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    Runnable updateSeek = new Runnable() {
        public void run() {
            if (mediaPlayer != null && playing) {
                seekBar.setProgress(mediaPlayer.getCurrentPosition());
                ct.setText(fmt(mediaPlayer.getCurrentPosition()));
                tt.setText(fmt(mediaPlayer.getDuration()));
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeek);
        if (mediaPlayer != null) mediaPlayer.release();
    }
}