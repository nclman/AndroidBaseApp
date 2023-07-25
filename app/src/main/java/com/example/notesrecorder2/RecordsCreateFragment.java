package com.example.notesrecorder2;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link RecordsCreateFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RecordsCreateFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private static final int REQUEST_PERMISSION = 200;
    private EditText editTextInput;
    private Button buttonSaveNote;
    private Button buttonAudioNote;
    private boolean isRecording = false;
    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    //private String audioFileDir;
    private File audioFile;
    private Uri savedUri;
    private DatabaseManager dbManager;

    private ViewPagerAdapter mAdapter;

    public RecordsCreateFragment() {
        // Required empty public constructor
        savedUri = Uri.EMPTY;
    }

    public void setViewPagerAdapter(ViewPagerAdapter adapter) {
        this.mAdapter = adapter;
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment RecordsCreateFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static RecordsCreateFragment newInstance(String param1, String param2) {
        RecordsCreateFragment fragment = new RecordsCreateFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_records_create, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ActivityCompat.requestPermissions(this.getActivity(), new String[]{android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_PERMISSION);

        dbManager = new DatabaseManager(this.getContext());
        dbManager.open();

        editTextInput = getView().findViewById(R.id.text_input);
        buttonSaveNote = getView().findViewById(R.id.button_save_note);

        buttonSaveNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String note = editTextInput.getText().toString();
                Log.i("SaveNote", "note: "+ note);

                // Save text and audio path to database
                dbManager.insert(note, savedUri.getPath());
                mAdapter.refreshListFragment();
            }
        });

        buttonAudioNote = getView().findViewById(R.id.button_audio_note);
        buttonAudioNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isRecording) {
                    stopRecording();
                    buttonAudioNote.setText("Start Recording");
                } else {
                    if (checkPermissions()) {
                        if (startRecording() == true) {
                            buttonAudioNote.setText("Stop Recording");
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        dbManager.close();
    }

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this.getContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this.getContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this.getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this.getActivity(), new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
            return false;
        }

        return true;
    }

    private boolean startRecording() {
        File audioFileDir = requireContext().getExternalCacheDir();
        try {
            audioFile = File.createTempFile("record", ".3gp", audioFileDir);
        } catch (IOException e) {
            Log.e("NotesRecorder", "external storage access error");
            return false;
        }
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            Toast.makeText(this.getContext(), "Recording started", Toast.LENGTH_SHORT).show();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void stopRecording() {
        mediaRecorder.stop();
        mediaRecorder.release();
        mediaRecorder = null;
        isRecording = false;
        //Toast.makeText(this.getContext(), "Recording stopped", Toast.LENGTH_SHORT).show();
        addRecordingToMediaLibrary();
    }

    private void addRecordingToMediaLibrary() {
        //creating content values of size 4
        ContentValues values = new ContentValues(4);
        long current = System.currentTimeMillis();
        values.put(MediaStore.Audio.Media.TITLE, "audio" + audioFile.getName());
        values.put(MediaStore.Audio.Media.DATE_ADDED, (int) (current / 1000));
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/3gpp");
        values.put(MediaStore.Audio.Media.DATA, audioFile.getAbsolutePath());

        //creating content resolver and storing it in the external content uri
        ContentResolver contentResolver = requireContext().getContentResolver();
        Uri base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Uri newUri = contentResolver.insert(base, values);

        //sending broadcast message to scan the media file so that it can be available
        //requireContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, newUri));
        Toast.makeText(this.getContext(), "Saved File " + newUri, Toast.LENGTH_LONG).show();

        // remove an existing recording
        if (savedUri != Uri.EMPTY) {
            Log.i("SaveMedia", "Overwriting media " + savedUri.getPath());
            contentResolver.delete(savedUri, null, null);
        }
        savedUri = newUri;
    }
}