package com.example.musicapp;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class RadioActivity extends Activity {

    GridView gridView;
    LinearLayout playerLayout;
    TextView rTitle, rSong;
    Button btnPlay, btnPrev, btnNext;
    MediaPlayer mediaPlayer;
    List<JSONObject> radios = new ArrayList<>();
    int cur = -1;
    boolean playing = false;

    int dp(int v) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v,
            getResources().getDisplayMetrics()
        );
    }

    GradientDrawable roundRect(int color, int borderColor, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(radius));
        d.setColor(color);
        if (borderColor != 0) d.setStroke(dp(1), borderColor);
        return d;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ===== ROOT =====
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0f2027"));
        setContentView(root);

        // ===== GRID =====
        gridView = new GridView(this);
        gridView.setNumColumns(2);
        gridView.setHorizontalSpacing(dp(6));
        gridView.setVerticalSpacing(dp(6));
        gridView.setPadding(dp(8), dp(8), dp(8), dp(8));
        gridView.setClipToPadding(false);
        gridView.setDivider(null);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(gridView, gridParams);

        // ===== PLAYER =====
        playerLayout = new LinearLayout(this);
        playerLayout.setOrientation(LinearLayout.VERTICAL);
        playerLayout.setBackgroundColor(Color.parseColor("#1a1a2e"));
        playerLayout.setPadding(dp(16), dp(16), dp(16), dp(16));
        playerLayout.setVisibility(View.GONE);
        root.addView(playerLayout, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- INFO ROW ---
        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        infoParams.bottomMargin = dp(12);
        playerLayout.addView(infoRow, infoParams);

        // Thumb
        LinearLayout thumb = new LinearLayout(this);
        thumb.setBackground(roundRect(Color.parseColor("#6d1b3a"), 0, 10));
        thumb.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        thumbParams.rightMargin = dp(12);
        TextView thumbEmoji = new TextView(this);
        thumbEmoji.setText("📻");
        thumbEmoji.setTextSize(20);
        thumb.addView(thumbEmoji);
        infoRow.addView(thumb, thumbParams);

        // Text column
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        infoRow.addView(textCol, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        rTitle = new TextView(this);
        rTitle.setTextColor(Color.WHITE);
        rTitle.setTextSize(15);
        rTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        rTitle.setSingleLine(true);
        rTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(rTitle);

        rSong = new TextView(this);
        rSong.setTextColor(Color.parseColor("#e91e63"));
        rSong.setTextSize(12);
        LinearLayout.LayoutParams songParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        songParams.topMargin = dp(2);
        textCol.addView(rSong, songParams);

        // --- CONTROLS ROW ---
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(android.view.Gravity.CENTER);
        playerLayout.addView(controls, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        btnPrev = makeBtn("⏮");
        btnPlay = makeBtn("▶");
        btnNext = makeBtn("⏭");

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        btnParams.rightMargin = dp(8);
        controls.addView(btnPrev, btnParams);
        controls.addView(btnPlay, btnParams);
        controls.addView(btnNext, new LinearLayout.LayoutParams(dp(48), dp(48)));

        // ===== LISTENERS =====
        btnPlay.setOnClickListener(v -> togglePlay());
        btnNext.setOnClickListener(v -> next());
        btnPrev.setOnClickListener(v -> prev());
        gridView.setOnItemClickListener((a, v, i, id) -> playStation(i));

        mediaPlayer = new MediaPlayer();
        loadRadios();
    }

    Button makeBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(18);
        b.setTextColor(Color.parseColor("#cccccc"));
        b.setBackground(roundRect(Color.parseColor("#1affffff"), 0, 12));
        return b;
    }

    void loadRadios() {
        new Thread(() -> {
            try {
                URL url = new URL("https://radio-worker.ma68.workers.dev/radios");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                JSONArray arr = new JSONArray(sb.toString());
                radios.clear();
                for (int i = 0; i < arr.length(); i++) radios.add(arr.getJSONObject(i));
                runOnUiThread(this::renderGrid);
            } catch (Exception e) {
                runOnUiThread(() ->
                    Toast.makeText(this, "فشل الاتصال", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    void renderGrid() {
        gridView.setAdapter(new BaseAdapter() {
            public int getCount() { return radios.size(); }
            public Object getItem(int i) { return radios.get(i); }
            public long getItemId(int i) { return i; }

            public View getView(int i, View v, ViewGroup p) {
                LinearLayout card = new LinearLayout(RadioActivity.this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(android.view.Gravity.CENTER_VERTICAL);
                card.setPadding(dp(8), dp(8), dp(8), dp(8));
                card.setActivated(i == cur);

                if (i == cur) {
                    card.setBackground(roundRect(
                        Color.parseColor("#33e91e63"),
                        Color.parseColor("#e91e63"), 10));
                } else {
                    card.setBackground(roundRect(
                        Color.parseColor("#59000000"), 0, 10));
                }

                // Thumb box
                LinearLayout thumbBox = new LinearLayout(RadioActivity.this);
                thumbBox.setBackground(roundRect(Color.parseColor("#1affffff"), 0, 8));
                thumbBox.setGravity(android.view.Gravity.CENTER);
                TextView emoji = new TextView(RadioActivity.this);
                emoji.setText("📻");
                emoji.setTextSize(16);
                thumbBox.addView(emoji);
                card.addView(thumbBox, new LinearLayout.LayoutParams(dp(38), dp(38)));

                // Name
                TextView name = new TextView(RadioActivity.this);
                try { name.setText(radios.get(i).optString("name", "Radio")); }
                catch (Exception ignored) {}
                name.setTextColor(i == cur ?
                    Color.parseColor("#e91e63") : Color.WHITE);
                name.setTextSize(11);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                nameParams.leftMargin = dp(8);
                card.addView(name, nameParams);

                return card;
            }
        });
    }

    void playStation(int i) {
        cur = i;
        renderGrid();
        try {
            JSONObject r = radios.get(i);
            mediaPlayer.reset();
            mediaPlayer.setDataSource(r.optString("url", ""));
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