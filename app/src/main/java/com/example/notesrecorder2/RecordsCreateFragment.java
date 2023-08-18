package com.example.notesrecorder2;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
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
import android.widget.ListView;
import android.widget.Toast;

import com.example.notesrecorder2.ui.login.LoginActivity;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.firebase.auth.FirebaseAuth;

import java.io.File;
import java.io.IOException;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link RecordsCreateFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RecordsCreateFragment extends Fragment {

    private static final String TAG = "RecordsCreateFragment";

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private static final int REQUEST_PERMISSION = 200;
    private Button buttonLogOut;
    private EditText editTextInput;
    private Button buttonSaveNote;
    private Button buttonAudioNote;
    private boolean isRecording = false;
    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    //private String audioFileDir;
    private File audioFile;
    private Uri savedUri = Uri.EMPTY;
    private DatabaseManager dbManager;

    private ViewPagerAdapter mViewPagerAdapter;
    private FirebaseAuth mAuth;
    private AdView mAdView;

    public RecordsCreateFragment() {
        // Required empty public constructor
        savedUri = Uri.EMPTY;
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
    public static RecordsCreateFragment newInstance(ViewPagerAdapter pagerAdapter, String param1, String param2) {
        RecordsCreateFragment fragment = new RecordsCreateFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        fragment.mViewPagerAdapter = pagerAdapter;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        mAuth = FirebaseAuth.getInstance();
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

        ActivityCompat.requestPermissions(requireActivity(), new String[]{android.Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_PERMISSION);

        //dbManager = new DatabaseManager(this.mViewPagerAdapter.getFragmentActivity());
        dbManager = DatabaseManager.getInstance(this.mViewPagerAdapter.getFragmentActivity());
        dbManager.open();
        dbManager.sync();

        editTextInput = getView().findViewById(R.id.text_input);
        buttonSaveNote = getView().findViewById(R.id.button_save_note);
        buttonLogOut = getView().findViewById(R.id.button_log_out);

        final Context context = getContext();

        buttonLogOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAuth.signOut();

                // go back to Login Activity
                Intent intent = new Intent(context, LoginActivity.class);
                startActivity(intent);

                // close MainActivity
                getActivity().finish();
            }
        });

        buttonSaveNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String note = editTextInput.getText().toString();
                Log.i("SaveNote", "note: "+ note);

                // Save text and audio path to database
                dbManager.open();
                dbManager.insert(note, savedUri.getPath());
                dbManager.close();
                Toast.makeText(context, "Note Saved", Toast.LENGTH_LONG).show();

                // So that we don't double save (hence double delete)
                savedUri = Uri.EMPTY;
            }
        });

        buttonAudioNote = getView().findViewById(R.id.button_audio_note);
        buttonAudioNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isRecording) {
                    stopRecording();
                    buttonAudioNote.setText("Start Recording");
                    buttonSaveNote.setEnabled(true);
                } else {
                    if (checkPermissions()) {
                        if (startRecording()) {
                            buttonAudioNote.setText("Stop Recording");
                            buttonSaveNote.setEnabled(false);
                        }
                    }
                }
            }
        });

        mAdView = getView().findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
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
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
            return false;
        }

        return true;
    }

    private boolean startRecording() {
        File audioFileDir = new File(requireContext().getFilesDir(), "audio");
        audioFileDir.mkdir();
        try {
            audioFile = File.createTempFile("audio", ".3gp", audioFileDir);
        } catch (IOException e) {
            Log.e("NotesRecorder", "storage access error");
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
        //MediaScannerConnection.scanFile(getContext(), new String[]{newUri.getPath()}, null, null);
        Toast.makeText(this.getContext(), "Saved File " + audioFile.getName(), Toast.LENGTH_LONG).show();
        // TODO: Perform 3gp to mp3 conversion here.
        // remove an existing recording
        if (savedUri != Uri.EMPTY) {
            Log.i("SaveMedia", "Overwriting media " + savedUri.getPath());
            File f = new File(savedUri.getPath());
            if (f.exists()) {
                f.delete();
            }
        }
        savedUri = Uri.fromFile(audioFile);
    }
}
