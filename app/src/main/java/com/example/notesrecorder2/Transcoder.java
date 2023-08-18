package com.example.notesrecorder2;

import java.io.File;

public class Transcoder {
    static {
        System.loadLibrary("avutil");
        System.loadLibrary("avcodec");
        System.loadLibrary("avformat");
        System.loadLibrary("swresample");
        //System.loadLibrary("avfilter"); // maybe not needed?
        System.loadLibrary("transcoder");
    }

    private Transcoder() {}

    public static native boolean convert3gpToMp3(File input, File output);
}
