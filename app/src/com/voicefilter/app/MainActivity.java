package com.voicefilter.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_MIC = 100;

    private final Handler main = new Handler(Looper.getMainLooper());

    private ViewFlipper flipper;
    private TextView tabStudio, tabLive, tabGame, hudStatus;

    // studio
    private WaveformView studioWave;
    private LevelMeterView studioMeter;
    private FrameLayout recordBtn;
    private ImageView recordIcon;
    private TextView recordStatus, recordTime, studioStatus;
    private TextView chipOriginal, chipMale, chipFemale;
    private SeekBar strengthBar, pitchBar, gainBar;
    private TextView strengthVal, pitchVal, gainVal;
    private Switch gateSwitch, echoSwitch;
    private TextView previewBtn, saveBtn, cloudBtn;

    // live
    private TextView liveChipMale, liveChipFemale, liveToggle, liveStatus, liveStrengthVal;
    private SeekBar liveStrengthBar;
    private LevelMeterView liveMeter;
    private TextView hudBtn, gameHudBtn;

    // game
    private LinearLayout clipList;
    private TextView gameEmpty, gameStatus;
    private Switch forceSpeakerSwitch;

    private int studioMode = VoicePreset.MODE_MALE;
    private int liveMode = VoicePreset.MODE_MALE;
    private float liveStrength = 0.85f;
    private float[] customShelves = null;   // lowDb, lowHz, highDb, highHz from a cloud preset
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
    private final PcmPlayer player = new PcmPlayer();
    private Runnable countdownTick = null;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("voicefilter", MODE_PRIVATE);

        flipper = (ViewFlipper) findViewById(R.id.flipper);
        tabStudio = (TextView) findViewById(R.id.tab_studio);
        tabLive = (TextView) findViewById(R.id.tab_live);
        tabGame = (TextView) findViewById(R.id.tab_game);
        hudStatus = (TextView) findViewById(R.id.hud_status);

        studioWave = (WaveformView) findViewById(R.id.studio_wave);
        studioMeter = (LevelMeterView) findViewById(R.id.studio_meter);
        recordBtn = (FrameLayout) findViewById(R.id.record_btn);
        recordIcon = (ImageView) findViewById(R.id.record_icon);
        recordStatus = (TextView) findViewById(R.id.record_status);
        recordTime = (TextView) findViewById(R.id.record_time);
        studioStatus = (TextView) findViewById(R.id.studio_status);
        chipOriginal = (TextView) findViewById(R.id.chip_original);
        chipMale = (TextView) findViewById(R.id.chip_male);
        chipFemale = (TextView) findViewById(R.id.chip_female);
        strengthBar = (SeekBar) findViewById(R.id.strength_bar);
        pitchBar = (SeekBar) findViewById(R.id.pitch_bar);
        gainBar = (SeekBar) findViewById(R.id.gain_bar);
        strengthVal = (TextView) findViewById(R.id.strength_val);
        pitchVal = (TextView) findViewById(R.id.pitch_val);
        gainVal = (TextView) findViewById(R.id.gain_val);
        gateSwitch = (Switch) findViewById(R.id.gate_switch);
        echoSwitch = (Switch) findViewById(R.id.echo_switch);
        previewBtn = (TextView) findViewById(R.id.preview_btn);
        saveBtn = (TextView) findViewById(R.id.save_btn);
        cloudBtn = (TextView) findViewById(R.id.cloud_btn);

        liveChipMale = (TextView) findViewById(R.id.live_chip_male);
        liveChipFemale = (TextView) findViewById(R.id.live_chip_female);
        liveToggle = (TextView) findViewById(R.id.live_toggle);
        liveStatus = (TextView) findViewById(R.id.live_status);
        liveStrengthBar = (SeekBar) findViewById(R.id.live_strength_bar);
        liveStrengthVal = (TextView) findViewById(R.id.live_strength_val);
        liveMeter = (LevelMeterView) findViewById(R.id.live_meter);
        hudBtn = (TextView) findViewById(R.id.hud_btn);
        gameHudBtn = (TextView) findViewById(R.id.game_hud_btn);

        clipList = (LinearLayout) findViewById(R.id.clip_list);
        gameEmpty = (TextView) findViewById(R.id.game_empty);
        gameStatus = (TextView) findViewById(R.id.game_status);
        forceSpeakerSwitch = (Switch) findViewById(R.id.force_speaker_switch);

        tabStudio.setOnClickListener(tabClick(0));
        tabLive.setOnClickListener(tabClick(1));
        tabGame.setOnClickListener(tabClick(2));

        View.OnClickListener chipClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v == chipOriginal) studioMode = VoicePreset.MODE_ORIGINAL;
                else if (v == chipMale) studioMode = VoicePreset.MODE_MALE;
                else studioMode = VoicePreset.MODE_FEMALE;
                customShelves = null;
                customName = null;
                pitchBar.setProgress(VoicePreset.defaultPitchPct(studioMode) - 50);
                refreshStudioChips();
                updateSliderLabels();
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
                if (HudService.running()) HudService.get().setVoice(liveMode);
            }
        };
        liveChipMale.setOnClickListener(liveChipClick);
        liveChipFemale.setOnClickListener(liveChipClick);
        refreshLiveChips();

        SeekBar.OnSeekBarChangeListener studioSeek = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateSliderLabels();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        };
        strengthBar.setOnSeekBarChangeListener(studioSeek);
        pitchBar.setOnSeekBarChangeListener(studioSeek);
        gainBar.setOnSeekBarChangeListener(studioSeek);
        updateSliderLabels();

        liveStrengthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                liveStrength = progress / 100f;
                liveStrengthVal.setText(Math.round(liveStrength * 100) + "%");
                if (liveChanger != null) liveChanger.setParams(VoicePreset.params(liveMode, liveStrength));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        liveStrengthVal.setText("85%");

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

        View.OnClickListener hudClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleHud();
            }
        };
        hudBtn.setOnClickListener(hudClick);
        gameHudBtn.setOnClickListener(hudClick);

        forceSpeakerSwitch.setChecked(BroadcastPlayer.forceSpeaker(this));
        forceSpeakerSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton button, boolean checked) {
                prefs.edit().putBoolean("force_speaker", checked).apply();
            }
        });

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        }
        showTab(0);
    }

    private View.OnClickListener tabClick(final int index) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTab(index);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHudUi();
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
        styleTab(tabStudio, index == 0);
        styleTab(tabLive, index == 1);
        styleTab(tabGame, index == 2);
    }

    private void styleTab(TextView tab, boolean selected) {
        int color = getResources().getColor(selected ? R.color.cyan : R.color.tab_idle, null);
        tab.setTextColor(color);
        tab.setCompoundDrawableTintList(ColorStateList.valueOf(color));
        tab.setBackgroundResource(selected ? R.drawable.bg_button_ghost : 0);
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

    private void updateSliderLabels() {
        strengthVal.setText(strengthBar.getProgress() + "%");
        pitchVal.setText((pitchBar.getProgress() + 50) + "%");
        int db = gainBar.getProgress() - 6;
        gainVal.setText((db >= 0 ? "+" : "") + db + " dB");
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
            recordBtn.setBackgroundResource(R.drawable.bg_ring_record);
            recordIcon.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.cyan, null)));
            recordStatus.setText(R.string.processing);
            return;
        }
        player.stopNow();
        rawPcm = null;
        studioWave.setPcm(null);
        recordStart = System.currentTimeMillis();
        recordBtn.setBackgroundResource(R.drawable.bg_ring_record_on);
        recordIcon.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.pink, null)));
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
                        studioWave.setPcm(pcm);
                        recordBtn.setBackgroundResource(R.drawable.bg_ring_record);
                        recordIcon.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.cyan, null)));
                        recordStatus.setText(R.string.rec_idle);
                        recordTime.setText(String.format(java.util.Locale.US, "%.1f s", millis / 1000f));
                        studioStatus.setText(pcm.length > 0 ? "Take ready - filter and preview it below" : "No audio captured");
                    }
                });
            }
        });
        recorder.start();
        main.postDelayed(timerTick, 100);
    }

    private VoiceEngine.Params currentStudioParams() {
        VoiceEngine.Params p = new VoiceEngine.Params();
        p.strength = strengthBar.getProgress() / 100f;
        p.pitch = (pitchBar.getProgress() + 50) / 100f;
        p.gainDb = gainBar.getProgress() - 6;
        p.gateDb = gateSwitch.isChecked() ? -50f : -95f;
        p.echoMs = echoSwitch.isChecked() ? 180 : 0;
        p.echoFb = 0.35f;
        if (customShelves != null) {
            p.lowShelfDb = customShelves[0];
            p.lowShelfHz = customShelves[1];
            p.highShelfDb = customShelves[2];
            p.highShelfHz = customShelves[3];
        } else {
            VoiceEngine.Params base = VoicePreset.params(studioMode, 1f);
            p.lowShelfDb = base.lowShelfDb;
            p.lowShelfHz = base.lowShelfHz;
            p.highShelfDb = base.highShelfDb;
            p.highShelfHz = base.highShelfHz;
        }
        return p;
    }

    private void previewOrSave(final boolean save) {
        if (rawPcm == null || rawPcm.length == 0) {
            toast(getString(R.string.nothing_recorded));
            return;
        }
        studioStatus.setText(R.string.processing);
        final VoiceEngine.Params params = currentStudioParams();
        String name = customName != null ? customName : VoicePreset.label(studioMode);
        final String label = name + (echoSwitch.isChecked() ? "_echo" : "");
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
                            player.play(out, sr, false, null);
                            studioStatus.setText("Playing filtered preview (pitch " + (pitchBar.getProgress() + 50) + "%)");
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
                        liveStatus.setText("filtering live - block latency ~" + millis + " ms");
                    }
                });
            }
        });
        liveChanger.setParams(VoicePreset.params(liveMode, liveStrength));
        liveChanger.start();
        liveToggle.setText(R.string.live_stop);
        liveToggle.setBackgroundResource(R.drawable.bg_button_ghost);
        liveToggle.setTextColor(getResources().getColor(R.color.pink, null));
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

    // ---------------------------------------------------------------- hud

    private void toggleHud() {
        if (HudService.running()) {
            HudService.stop(this);
            refreshHudUi();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            toast(getString(R.string.hud_need_perm));
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivity(i);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            }
            return;
        }
        HudService.start(this);
        refreshHudUi();
    }

    private void refreshHudUi() {
        boolean on = HudService.running();
        hudStatus.setText(on ? R.string.hud_on : R.string.hud_off);
        hudStatus.setTextColor(getResources().getColor(on ? R.color.cyan : R.color.violet, null));
        hudBtn.setText(on ? R.string.hud_disable : R.string.hud_enable);
        gameHudBtn.setText(on ? R.string.hud_disable : R.string.hud_enable);
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
                    String.format(java.util.Locale.US, "%.1f s - broadcast into your game", clip.seconds));
            final ClipLibrary.Clip c = clip;
            row.findViewById(R.id.clip_play).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    BroadcastPlayer.broadcast(MainActivity.this, c, new BroadcastPlayer.Status() {
                        @Override
                        public void onStatus(String text) {
                            gameStatus.setText(text);
                        }
                    });
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
        Uri uri = Uri.parse("content://com.voicefilter.app.clips/" + clip.file.getName());
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("audio/x-wav");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.share)));
    }

    // ---------------------------------------------------------------- cloud presets

    private void showServerDialog() {
        final EditText urlInput = new EditText(this);
        urlInput.setHint("https://your-server.example");
        urlInput.setText(prefs.getString("server_url", ""));
        urlInput.setSingleLine(true);
        final EditText tokenInput = new EditText(this);
        tokenInput.setHint(getString(R.string.api_token));
        tokenInput.setText(prefs.getString("server_token", ""));
        tokenInput.setSingleLine(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(urlInput);
        box.addView(tokenInput);
        new AlertDialog.Builder(this)
                .setTitle(R.string.server_url)
                .setView(box)
                .setPositiveButton("Load", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String url = urlInput.getText().toString().trim();
                        final String token = tokenInput.getText().toString().trim();
                        prefs.edit().putString("server_url", url).putString("server_token", token).apply();
                        if (url.isEmpty()) return;
                        studioStatus.setText("Contacting server...");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final List<PresetClient.CloudPreset> list = PresetClient.fetch(url, token);
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
                        VoiceEngine.Params pr = p.params;
                        customShelves = new float[]{pr.lowShelfDb, pr.lowShelfHz, pr.highShelfDb, pr.highShelfHz};
                        customName = p.name;
                        pitchBar.setProgress(Math.round(pr.pitch * 100) - 50);
                        strengthBar.setProgress(Math.round(pr.strength * 100));
                        gainBar.setProgress(Math.round(pr.gainDb) + 6);
                        echoSwitch.setChecked(pr.echoMs > 0);
                        updateSliderLabels();
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
        player.stopNow();
        BroadcastPlayer.cancel();
        if (recorder != null) {
            recorder.stop();
            recorder = null;
            main.removeCallbacks(timerTick);
            recordBtn.setBackgroundResource(R.drawable.bg_ring_record);
            recordIcon.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.cyan, null)));
            recordStatus.setText(R.string.rec_idle);
        }
    }
}
