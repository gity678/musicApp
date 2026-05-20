package com.example.musicapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class MusicActivity extends Activity {

    ListView listView;
    EditText searchBox;
    TextView pTitle, ct, tt;
    SeekBar seekBar;
    Button btnPlay, btnPrev, btnNext;
    LinearLayout playerLayout;
    MediaPlayer mediaPlayer;
    Handler handler = new Handler();
    List<JSONObject> songs = new ArrayList<>();
    List<JSONObject> filtered = new ArrayList<>();
    Map<String, Bitmap> thumbCache = new HashMap<>();
    int cur = -1;
    boolean playing = false;

    final String WORKER = "https://music-worker.ma68.workers.dev";

    int dp(int v) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v,
            getResources().getDisplayMetrics());
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
        setContentView(R.layout.activity_music);

        listView     = findViewById(R.id.listView);
        searchBox    = findViewById(R.id.searchBox);
        pTitle       = findViewById(R.id.pTitle);
        ct           = findViewById(R.id.ct);
        tt           = findViewById(R.id.tt);
        seekBar      = findViewById(R.id.seekBar);
        btnPlay      = findViewById(R.id.btnPlay);
        btnPrev      = findViewById(R.id.btnPrev);
        btnNext      = findViewById(R.id.btnNext);
        playerLayout = findViewById(R.id.playerLayout);

        // تصميم شريط البحث مدوّر
        searchBox.setBackground(roundRect(0x99000000, 0, 14));

        mediaPlayer = new MediaPlayer();
        loadSongs();

        btnPlay.setOnClickListener(v -> togglePlay());
        btnNext.setOnClickListener(v -> next());
        btnPrev.setOnClickListener(v -> prev());

        listView.setOnItemClickListener((a, v, i, id) -> playSong(i));

        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { filter(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });

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
                URL url = new URL(WORKER + "/songs");
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

    void loadThumb(String title, int position) {
        new Thread(() -> {
            try {
                String query = URLEncoder.encode(title, "UTF-8");
                URL url = new URL("https://itunes.apple.com/search?term=" + query + "&media=music&limit=1");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                JSONObject json = new JSONObject(sb.toString());
                JSONArray results = json.getJSONArray("results");
                if (results.length() > 0) {
                    String imgUrl = results.getJSONObject(0)
                        .getString("artworkUrl100")
                        .replace("100x100", "300x300");
                    URL imgURL = new URL(imgUrl);
                    Bitmap bmp = BitmapFactory.decodeStream(imgURL.openStream());
                    if (bmp != null) {
                        thumbCache.put(title, bmp);
                        runOnUiThread(this::renderList);
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    void renderList() {
        listView.setAdapter(new BaseAdapter() {
            public int getCount() { return filtered.size(); }
            public Object getItem(int i) { return filtered.get(i); }
            public long getItemId(int i) { return i; }

            public View getView(int i, View v, ViewGroup p) {
                LinearLayout row = new LinearLayout(MusicActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(12), dp(16), dp(12));

                boolean isActive = i == cur;
                if (isActive) {
                    row.setBackgroundColor(Color.parseColor("#26e91e63"));
                } else {
                    row.setBackgroundColor(Color.TRANSPARENT);
                }

                // رقم الأغنية
                TextView num = new TextView(MusicActivity.this);
                num.setText(String.valueOf(i + 1));
                num.setTextSize(13);
                num.setTextColor(isActive ? Color.parseColor("#e91e63") : Color.parseColor("#555555"));
                num.setWidth(dp(22));
                num.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams numParams = new LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.WRAP_CONTENT);
                numParams.rightMargin = dp(12);
                row.addView(num, numParams);

                // الصورة المصغرة
                String title = getTitle(filtered.get(i));
                LinearLayout thumbBox = new LinearLayout(MusicActivity.this);
                thumbBox.setBackground(roundRect(Color.parseColor("#1affffff"), 0, 8));
                thumbBox.setGravity(android.view.Gravity.CENTER);

                if (thumbCache.containsKey(title)) {
                    ImageView img = new ImageView(MusicActivity.this);
                    img.setImageBitmap(thumbCache.get(title));
                    img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    GradientDrawable imgShape = new GradientDrawable();
                    imgShape.setCornerRadius(dp(8));
                    img.setClipToOutline(true);
                    thumbBox.addView(img, new LinearLayout.LayoutParams(dp(44), dp(44)));
                } else {
                    TextView emoji = new TextView(MusicActivity.this);
                    emoji.setText("♪");
                    emoji.setTextSize(19);
                    emoji.setTextColor(Color.WHITE);
                    thumbBox.addView(emoji);
                    loadThumb(title, i);
                }

                LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(dp(44), dp(44));
                thumbParams.rightMargin = dp(12);
                row.addView(thumbBox, thumbParams);

                // اسم الأغنية
                TextView name = new TextView(MusicActivity.this);
                name.setText(title);
                name.setTextSize(14);
                name.setTextColor(isActive ? Color.parseColor("#e91e63") : Color.WHITE);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                // زر القائمة ⋮
                TextView menuBtn = new TextView(MusicActivity.this);
                menuBtn.setText("⋮");
                menuBtn.setTextSize(20);
                menuBtn.setTextColor(Color.parseColor("#555555"));
                menuBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
                final int idx = i;
                menuBtn.setOnClickListener(v2 -> showMenu(idx));
                LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
                row.addView(menuBtn, menuParams);

                row.setOnClickListener(v2 -> playSong(idx));

                return row;
            }
        });
    }

    void showMenu(int i) {
        String title = getTitle(filtered.get(i));
        String[] options = {"Rename", "Delete"};
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options, (dialog, which) -> {
                if (which == 0) renameItem(i);
                else deleteItem(i);
            }).show();
    }

    void renameItem(int i) {
        JSONObject s = filtered.get(i);
        String oldTitle = getTitle(s);
        EditText input = new EditText(this);
        input.setText(oldTitle);
        new AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("OK", (d, w) -> {
                String newName = input.getText().toString().trim();
                if (newName.isEmpty() || newName.equals(oldTitle)) return;
                new Thread(() -> {
                    try {
                        URL url = new URL(WORKER + "/rename");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setDoOutput(true);
                        String body = "{\"public_id\":\"" + s.optString("id") + "\",\"title\":\"" + newName + "\"}";
                        conn.getOutputStream().write(body.getBytes());
                        if (conn.getResponseCode() == 200) runOnUiThread(this::loadSongs);
                    } catch (Exception ignored) {}
                }).start();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    void deleteItem(int i) {
        JSONObject s = filtered.get(i);
        new AlertDialog.Builder(this)
            .setTitle("Delete")
            .setMessage("Supprimer \"" + getTitle(s) + "\"?")
            .setPositiveButton("Delete", (d, w) -> {
                new Thread(() -> {
                    try {
                        URL url = new URL(WORKER + "/delete");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("DELETE");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setDoOutput(true);
                        String body = "{\"public_id\":\"" + s.optString("id") + "\"}";
                        conn.getOutputStream().write(body.getBytes());
                        if (conn.getResponseCode() == 200) runOnUiThread(this::loadSongs);
                    } catch (Exception ignored) {}
                }).start();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    void filter(String q) {
        filtered.clear();
        for (JSONObject s : songs) {
            if (getTitle(s).toLowerCase().contains(q.toLowerCase())) filtered.add(s);
        }
        renderList();
    }

    void playSong(int i) {
        cur = i;
        renderList();
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