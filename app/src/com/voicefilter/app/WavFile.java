package com.voicefilter.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Minimal 16-bit PCM mono WAV reader/writer. */
public final class WavFile {

    public static final class Data {
        public short[] pcm;
        public int sampleRate;
    }

    private WavFile() {
    }

    public static void write(File f, short[] pcm, int sampleRate) throws IOException {
        FileOutputStream out = new FileOutputStream(f);
        try {
            byte[] h = new byte[44];
            int dataLen = pcm.length * 2;
            putAscii(h, 0, "RIFF");
            putIntLE(h, 4, 36 + dataLen);
            putAscii(h, 8, "WAVE");
            putAscii(h, 12, "fmt ");
            putIntLE(h, 16, 16);
            putShortLE(h, 20, (short) 1);
            putShortLE(h, 22, (short) 1);
            putIntLE(h, 24, sampleRate);
            putIntLE(h, 28, sampleRate * 2);
            putShortLE(h, 32, (short) 2);
            putShortLE(h, 34, (short) 16);
            putAscii(h, 36, "data");
            putIntLE(h, 40, dataLen);
            out.write(h);
            byte[] buf = new byte[8192];
            int off = 0;
            while (off < pcm.length) {
                int n = Math.min(4096, pcm.length - off);
                for (int i = 0; i < n; i++) {
                    buf[i * 2] = (byte) (pcm[off + i] & 0xFF);
                    buf[i * 2 + 1] = (byte) ((pcm[off + i] >> 8) & 0xFF);
                }
                out.write(buf, 0, n * 2);
                off += n;
            }
        } finally {
            out.close();
        }
    }

    public static Data read(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            byte[] head = new byte[12];
            readFully(in, head);
            if (!ascii(head, 0, "RIFF") || !ascii(head, 8, "WAVE")) {
                throw new IOException("not a wav file");
            }
            int sampleRate = 44100;
            int channels = 1;
            int bits = 16;
            byte[] data = null;
            byte[] chdr = new byte[8];
            while (readFully(in, chdr)) {
                int len = getIntLE(chdr, 4);
                if (ascii(chdr, 0, "fmt ")) {
                    byte[] fmt = new byte[len];
                    readFully(in, fmt);
                    channels = getShortLE(fmt, 2) & 0xFFFF;
                    sampleRate = getIntLE(fmt, 4);
                    bits = getShortLE(fmt, 14) & 0xFFFF;
                    if ((len & 1) == 1) in.skip(1);
                } else if (ascii(chdr, 0, "data")) {
                    data = new byte[len];
                    readFully(in, data);
                    if ((len & 1) == 1) in.skip(1);
                } else {
                    long skip = len;
                    while (skip > 0) {
                        long s = in.skip(skip);
                        if (s <= 0) break;
                        skip -= s;
                    }
                    if ((len & 1) == 1) in.skip(1);
                }
            }
            if (data == null) {
                throw new IOException("no data chunk");
            }
            if (bits != 16) {
                throw new IOException("only 16-bit pcm supported");
            }
            int frames = data.length / 2 / Math.max(1, channels);
            short[] pcm = new short[frames];
            for (int i = 0; i < frames; i++) {
                int acc = 0;
                for (int c = 0; c < channels; c++) {
                    int o = (i * channels + c) * 2;
                    if (o + 1 >= data.length) break;
                    acc += (short) ((data[o] & 0xFF) | (data[o + 1] << 8));
                }
                pcm[i] = (short) (acc / channels);
            }
            Data d = new Data();
            d.pcm = pcm;
            d.sampleRate = sampleRate;
            return d;
        } finally {
            in.close();
        }
    }

    private static boolean readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) return off > 0;
            off += n;
        }
        return true;
    }

    private static void putAscii(byte[] b, int o, String s) {
        for (int i = 0; i < s.length(); i++) b[o + i] = (byte) s.charAt(i);
    }

    private static boolean ascii(byte[] b, int o, String s) {
        for (int i = 0; i < s.length(); i++) {
            if (b[o + i] != (byte) s.charAt(i)) return false;
        }
        return true;
    }

    private static void putIntLE(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
        b[o + 2] = (byte) (v >> 16);
        b[o + 3] = (byte) (v >> 24);
    }

    private static int getIntLE(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }

    private static void putShortLE(byte[] b, int o, short v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
    }

    private static int getShortLE(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }
}
