package com.voicemorph.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.io.File;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_MIC = 100;

    private final Handler main = new Handler(Looper.getMainLooper());

    private ViewFlipper flipper;
    private TextView tabStudio, tabLive, tabGame;

    // studio
    private LevelMeterView studioMeter;
    private FrameLayout recordBtn;
    private TextView recordStatus, recordTime, studioStatus;
    private TextView chipOriginal, chipMale, chipFemale;
    private SeekBar strengthBar;
    private TextView previewBtn, saveBtn, cloudBtn;

    // live
    private TextView liveChipMale, liveChipFemale, liveToggle, liveStatus;
    private SeekBar liveStrengthBar;
    private LevelMeterView liveMeter;

    // game
    private LinearLayout clipList;
    private TextView gameEmpty, gameStatus;

    private int studioMode = VoicePreset.MODE_MALE;
    private int liveMode = VoicePreset.MODE_MALE;
    private float studioStrength = 0.85f;
    private float liveStrength = 0.85f;
    private VoiceEngine.Params customParams = null;
    private String customName = null;

    private short[] rawPcm = null;
    private int rawSr = RecorderEngine.SAMPLE_RATE;
    private RecorderEngine recorder = null;
    private long recordStart = 0;
    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (recorder == null) return;
            recordTime.setText(String.format(java.util.Locale.US, "%.1f s", (System.currentTimeMillis() - recordStart) / 1000f));
            main.postDelayed(this, 100);
        }
    };

    private LiveChanger liveChanger = null;

    private AudioTrack playerTrack = null;
    private Thread playerThread = null;
    private volatile boolean playerStop = false;

    private MediaPlayer broadcastPlayer = null;
    private Runnable countdownTick = null;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("voicemorph", MODE_PRIVATE);

        flipper = (ViewFlipper) findViewById(R.id.flipper);
        tabStudio = (TextView) findViewById(R.id.tab_studio);
        tabLive = (TextView) findViewById(R.id.tab_live);
        tabGame = (TextView) findViewById(R.id.tab_game);

        studioMeter = (LevelMeterView) findViewById(R.id.studio_meter);
        recordBtn = (FrameLayout) findViewById(R.id.record_btn);
        recordStatus = (TextView) findViewById(R.id.record_status);
        recordTime = (TextView) findViewById(R.id.record_time);
        studioStatus = (TextView) findViewById(R.id.studio_status);
        chipOriginal = (TextView) findViewById(R.id.chip_original);
        chipMale = (TextView) findViewById(R.id.chip_male);
        chipFemale = (TextView) findViewById(R.id.chip_female);
        strengthBar = (SeekBar) findViewById(R.id.strength_bar);
        previewBtn = (TextView) findViewById(R.id.preview_btn);
        saveBtn = (TextView) findViewById(R.id.save_btn);
        cloudBtn = (TextView) findViewById(R.id.cloud_btn);

        liveChipMale = (TextView) findViewById(R.id.live_chip_male);
        liveChipFemale = (TextView) findViewById(R.id.live_chip_female);
        liveToggle = (TextView) findViewById(R.id.live_toggle);
        liveStatus = (TextView) findViewById(R.id.live_status);
        liveStrengthBar = (SeekBar) findViewById(R.id.live_strength_bar);
        liveMeter = (LevelMeterView) findViewById(R.id.live_meter);

        clipList = (LinearLayout) findViewById(R.id.clip_list);
        gameEmpty = (TextView) findViewById(R.id.game_empty);
        gameStatus = (TextView) findViewById(R.id.game_status);

        tabStudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTab(0);
            }
        });
        tabLive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTab(1);
            }
        });
        tabGame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTab(2);
            }
        });

        View.OnClickListener chipClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v == chipOriginal) studioMode = VoicePreset.MODE_ORIGINAL;
                else if (v == chipMale) studioMode = VoicePreset.MODE_MALE;
                else studioMode = VoicePreset.MODE_FEMALE;
                customParams = null;
                customName = null;
                refreshStudioChips();
                studioStatus.setText("");
            }
        };
        chipOriginal.setOnClickListener(chipClick);
        chipMale.setOnClickListener(chipClick);
        chipFemale.setOnClickListener(chipClick);
        refreshStudioChips();

        View.OnClickListener liveChipClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                liveMode = (v == liveChipMale) ? VoicePreset.MODE_MALE : VoicePreset.MODE_FEMALE;
                refreshLiveChips();
                if (liveChanger != null) liveChanger.setParams(VoicePreset.params(liveMode, liveStrength));
            }
        };
        liveChipMale.setOnClickListener(liveChipClick);
        liveChipFemale.setOnClickListener(liveChipClick);
        refreshLiveChips();

        strengthBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                studioStrength = progress / 100f;
            }
        });
        liveStrengthBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                liveStrength = progress / 100f;
                if (liveChanger != null) liveChanger.setParams(VoicePreset.params(liveMode, liveStrength));
            }
        });

        recordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRecord();
            }
        });

        previewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                previewOrSave(false);
            }
        });
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                previewOrSave(true);
            }
        });
        cloudBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showServerDialog();
            }
        });

        liveToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLive();
            }
        });

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        }
        showTab(0);
    }

    // ---------------------------------------------------------------- tabs

    private void showTab(int index) {
        flipper.setDisplayedChild(index);
        if (index != 1 && liveChanger != null) {
            stopLive();
        }
        if (index == 2) {
            refreshClipList();
        }
        tabStudio.setTextColor(getResources().getColor(index == 0 ? R.color.primary : R.color.tab_idle, null));
        tabLive.setTextColor(getResources().getColor(index == 1 ? R.color.primary : R.color.tab_idle, null));
        tabGame.setTextColor(getResources().getColor(index == 2 ? R.color.primary : R.color.tab_idle, null));
    }

    // ---------------------------------------------------------------- studio

    private void refreshStudioChips() {
        applyChip(chipOriginal, studioMode == VoicePreset.MODE_ORIGINAL);
        applyChip(chipMale, studioMode == VoicePreset.MODE_MALE);
        applyChip(chipFemale, studioMode == VoicePreset.MODE_FEMALE);
    }

    private void refreshLiveChips() {
        applyChip(liveChipMale, liveMode == VoicePreset.MODE_MALE);
        applyChip(liveChipFemale, liveMode == VoicePreset.MODE_FEMALE);
    }

    private void applyChip(TextView tv, boolean selected) {
        tv.setSelected(selected);
        tv.setTextColor(getResources().getColor(selected ? R.color.window_bg : R.color.text_primary, null));
    }

    private boolean micGranted() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void toggleRecord() {
        if (!micGranted()) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            toast(getString(R.string.need_mic));
            return;
        }
        if (recorder != null) {
            recorder.stop();
            recorder = null;
            main.removeCallbacks(timerTick);
            recordBtn.setBackgroundResource(R.drawable.bg_record);
            recordStatus.setText(R.string.processing);
            return;
        }
        stopPlayer();
        rawPcm = null;
        recordStart = System.currentTimeMillis();
        recordBtn.setBackgroundResource(R.drawable.bg_record_on);
        recordStatus.setText(R.string.rec_running);
        recorder = RecorderEngine.forStudio(new RecorderEngine.Listener() {
            @Override
            public void onLevel(final float rms) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        studioMeter.setLevel(rms);
                    }
                });
            }

            @Override
            public void onChunk(short[] samples, int count) {
            }

            @Override
            public void onStopped(final short[] pcm, final int sampleRate, final long millis) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        rawPcm = pcm;
                        rawSr = sampleRate;
                        recordStatus.setText(R.string.rec_idle);
                        recordTime.setText(String.format(java.util.Locale.US, "%.1f s", millis / 1000f));
                        studioStatus.setText(pcm.length > 0
                                ? "Take ready: " + VoicePreset.label(studioMode) + " preview available"
                                : "No audio captured");
                    }
                });
            }
        });
        recorder.start();
        main.postDelayed(timerTick, 100);
    }

    private VoiceEngine.Params currentStudioParams() {
        if (customParams != null) {
            VoiceEngine.Params p = customParams.copy();
            p.strength = studioStrength;
            return p;
        }
        return VoicePreset.params(studioMode, studioStrength);
    }

    private void previewOrSave(final boolean save) {
        if (rawPcm == null || rawPcm.length == 0) {
            toast(getString(R.string.nothing_recorded));
            return;
        }
        studioStatus.setText(R.string.processing);
        final VoiceEngine.Params params = currentStudioParams();
        final String label = customName != null ? customName : VoicePreset.label(studioMode);
        final int sr = rawSr;
        final short[] input = rawPcm;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final short[] out = VoiceEngine.process(input, sr, params);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!save) {
                            playPcm(out, sr);
                            studioStatus.setText("Playing " + label + " morph (" + Math.round(params.pitch * 100) + "% pitch)");
                        } else {
                            try {
                                ClipLibrary.save(MainActivity.this, out, sr, label);
                                toast(getString(R.string.saved));
                                refreshClipList();
                            } catch (Exception e) {
                                toast("Save failed: " + e.getMessage());
                            }
                        }
                    }
                });
            }
        }).start();
    }

    // ---------------------------------------------------------------- player

    private void stopPlayer() {
        playerStop = true;
        if (playerThread != null) {
            try {
                playerThread.join(800);
            } catch (InterruptedException ignored) {
            }
            playerThread = null;
        }
    }

    private void playPcm(final short[] pcm, final int sr) {
        stopPlayer();
        playerStop = false;
        playerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (minBuf <= 0) minBuf = 8192;
                AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, sr, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf * 2, 16384), AudioTrack.MODE_STREAM);
                playerTrack = t;
                if (t.getState() != AudioTrack.STATE_INITIALIZED) {
                    t.release();
                    return;
                }
                t.play();
                int off = 0;
                while (!playerStop && off < pcm.length) {
                    int n = Math.min(4096, pcm.length - off);
                    t.write(pcm, off, n);
                    off += n;
                }
                if (!playerStop) {
                    try {
                        t.stop();
                    } catch (IllegalStateException ignored) {
                    }
                }
                t.release();
                playerTrack = null;
            }
        }, "voicemorph-play");
        playerThread.start();
    }

    // ---------------------------------------------------------------- live

    private void toggleLive() {
        if (liveChanger != null) {
            stopLive();
            return;
        }
        if (!micGranted()) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            toast(getString(R.string.need_mic));
            return;
        }
        liveChanger = new LiveChanger(new LiveChanger.LevelListener() {
            @Override
            public void onLevel(final float rms) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        liveMeter.setLevel(rms);
                    }
                });
            }

            @Override
            public void onLatency(final long millis) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        liveStatus.setText("morphing - block latency ~" + millis + " ms");
                    }
                });
            }
        });
        liveChanger.setParams(VoicePreset.params(liveMode, liveStrength));
        liveChanger.start();
        liveToggle.setText(R.string.live_stop);
        liveToggle.setBackgroundResource(R.drawable.bg_button_ghost);
        liveToggle.setTextColor(getResources().getColor(R.color.accent, null));
    }

    private void stopLive() {
        if (liveChanger != null) {
            liveChanger.stop();
            liveChanger = null;
        }
        liveToggle.setText(R.string.live_start);
        liveToggle.setBackgroundResource(R.drawable.bg_button);
        liveToggle.setTextColor(getResources().getColor(R.color.window_bg, null));
        liveStatus.setText("");
    }

    // ---------------------------------------------------------------- game / clips

    private void refreshClipList() {
        clipList.removeAllViews();
        List<ClipLibrary.Clip> clips = ClipLibrary.list(this);
        gameEmpty.setVisibility(clips.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ClipLibrary.Clip clip : clips) {
            View row = inflater.inflate(R.layout.item_clip, clipList, false);
            ((TextView) row.findViewById(R.id.clip_name)).setText(clip.name);
            ((TextView) row.findViewById(R.id.clip_meta)).setText(
                    String.format(java.util.Locale.US, "%.1f s - tap play to broadcast into your game", clip.seconds));
            final ClipLibrary.Clip c = clip;
            row.findViewById(R.id.clip_play).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    broadcast(c);
                }
            });
            row.findViewById(R.id.clip_share).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    share(c);
                }
            });
            row.findViewById(R.id.clip_delete).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ClipLibrary.delete(c);
                    refreshClipList();
                }
            });
            clipList.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void share(ClipLibrary.Clip clip) {
        Uri uri = Uri.parse("content://com.voicemorph.app.clips/" + clip.file.getName());
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("audio/x-wav");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.share)));
    }

    private void broadcast(final ClipLibrary.Clip clip) {
        if (broadcastPlayer != null) {
            broadcastPlayer.release();
            broadcastPlayer = null;
        }
        if (countdownTick != null) main.removeCallbacks(countdownTick);
        final int[] step = {3};
        gameStatus.setText(getString(R.string.countdown, step[0]));
        countdownTick = new Runnable() {
            @Override
            public void run() {
                step[0]--;
                if (step[0] > 0) {
                    gameStatus.setText(getString(R.string.countdown, step[0]));
                    main.postDelayed(this, 1000);
                } else {
                    countdownTick = null;
                    startBroadcast(clip);
                }
            }
        };
        main.postDelayed(countdownTick, 1000);
    }

    private void startBroadcast(ClipLibrary.Clip clip) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0);
        gameStatus.setText(R.string.broadcasting);
        try {
            MediaPlayer mp = new MediaPlayer();
            broadcastPlayer = mp;
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDataSource(clip.file.getAbsolutePath());
            mp.setVolume(1f, 1f);
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.release();
                    broadcastPlayer = null;
                    gameStatus.setText("");
                }
            });
            mp.prepare();
            mp.start();
        } catch (Exception e) {
            gameStatus.setText("Broadcast failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- cloud presets

    private void showServerDialog() {
        final EditText input = new EditText(this);
        input.setHint("https://your-server.example");
        input.setText(prefs.getString("server_url", ""));
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.server_url)
                .setView(input)
                .setPositiveButton("Load", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String url = input.getText().toString().trim();
                        prefs.edit().putString("server_url", url).apply();
                        if (url.isEmpty()) return;
                        studioStatus.setText("Contacting server...");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final List<PresetClient.CloudPreset> list = PresetClient.fetch(url);
                                    main.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            showPresetDialog(list);
                                        }
                                    });
                                } catch (final Exception e) {
                                    main.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            studioStatus.setText(getString(R.string.presets_failed));
                                            toast(getString(R.string.presets_failed) + ": " + e.getMessage());
                                        }
                                    });
                                }
                            }
                        }).start();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPresetDialog(final List<PresetClient.CloudPreset> list) {
        if (list.isEmpty()) {
            toast("Server returned no presets");
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            names[i] = list.get(i).name + "  (" + list.get(i).author + ")";
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.presets_loaded)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        PresetClient.CloudPreset p = list.get(which);
                        customParams = p.params;
                        customName = p.name;
                        studioStatus.setText("cloud preset active: " + p.name);
                    }
                })
                .show();
    }

    // ---------------------------------------------------------------- misc

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC && grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            toast(getString(R.string.need_mic));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLive();
        stopPlayer();
        if (recorder != null) {
            recorder.stop();
            recorder = null;
            main.removeCallbacks(timerTick);
            recordBtn.setBackgroundResource(R.drawable.bg_record);
            recordStatus.setText(R.string.rec_idle);
        }
        if (broadcastPlayer != null) {
            broadcastPlayer.release();
            broadcastPlayer = null;
            gameStatus.setText("");
        }
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
