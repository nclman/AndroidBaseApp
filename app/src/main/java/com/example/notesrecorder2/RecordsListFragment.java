package com.example.notesrecorder2;

import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link RecordsListFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RecordsListFragment extends Fragment {

    private static String TAG = "RecordsListFragment";
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private ListView listView;
    private ListViewAdapter adapter;

    final String[] from = new String[] { DatabaseHelper._ID,
            DatabaseHelper.TEXT_NOTE, DatabaseHelper.AUDIO_NOTE };

    final int[] to = new int[] { R.id._id, R.id.textnote, R.id.audionote };

    public RecordsListFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment RecordsListFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static RecordsListFragment newInstance(String param1, String param2) {
        RecordsListFragment fragment = new RecordsListFragment();
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
        return inflater.inflate(R.layout.fragment_records_list, container, false);
    }

    public void refreshData() {
        DatabaseManager dbMgr = new DatabaseManager(this.getContext());
        dbMgr.open();

        Cursor cursor = dbMgr.fetch();
        int numRows = cursor.getCount();

        String[] ids    = new String[numRows];
        String[] texts  = new String[numRows];
        String[] audios = new String[numRows];

        int i = 0;

        while (cursor.moveToNext()) {
            ids[i]    = cursor.getString(0);
            texts[i]  = cursor.getString(1);
            audios[i] = cursor.getString(2);
            Log.i(TAG, texts[i] + ": " + audios[i]);
            i++;
        }

        listView = (ListView) getView().findViewById(R.id.list_view);
        //listView.setEmptyView(getView().findViewById(R.id.empty));

        adapter = new ListViewAdapter(this.getContext(), ids, texts, audios);
        adapter.notifyDataSetChanged();

        listView.setAdapter(adapter);

        dbMgr.close();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refreshData();
      
        listView.setClickable(true);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            // One click to perhaps play audio and display full text message in a pop-up
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //Object o = listView.getItemAtPosition(position);

                Log.i("OnClick", "position:" + position + " id: " + id);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            // Long click to provide more options, such as to delete note, edit note, etc.
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Log.i("LongClick", "position:" + position + " id: " + id);
                return false;
            }
        });

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}