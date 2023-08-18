package com.example.notesrecorder2;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.LinkedList;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link RecordsListFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RecordsListFragment extends Fragment {

    private static final String TAG = "RecordsListFragment";
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private ViewPagerAdapter mViewPagerAdapter;

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
    public static RecordsListFragment newInstance(ViewPagerAdapter pagerAdapter, String param1, String param2) {
        RecordsListFragment fragment = new RecordsListFragment();
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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_records_list, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");

        refreshData();
    }

    private void toggleViewVisibility(boolean hasRows) {
        ListView listView = (ListView) getView().findViewById(R.id.list_view);
        TextView textView = (TextView) getView().findViewById(R.id.empty);
        if (hasRows) {
            listView.setVisibility(View.VISIBLE);
            textView.setVisibility(View.INVISIBLE);
        } else {
            listView.setVisibility(View.INVISIBLE);
            textView.setVisibility(View.VISIBLE);
        }
    }

    private boolean hasInitializedAdapter = false;

    public void refreshData() {

        if (getView() == null) return;

        //DatabaseManager dbMgr = new DatabaseManager(this.mViewPagerAdapter.getFragmentActivity());
        DatabaseManager dbMgr = DatabaseManager.getInstance(this.mViewPagerAdapter.getFragmentActivity());
        dbMgr.open();

        Cursor cursor = dbMgr.fetch();
        int numRows = cursor.getCount();

        LinkedList<RecordsListElement> llist = new LinkedList<RecordsListElement>();

        while (cursor.moveToNext()) {
            llist.add(new RecordsListElement(cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2)));
        }

        if (getView() == null) {
            // The view still hasn't appeared. Let's not update it first.
            return;
        }
      
        ListView listView = (ListView) getView().findViewById(R.id.list_view);

        if (!hasInitializedAdapter) {
            ListViewAdapter adapter = new ListViewAdapter(this, this.getContext());
            listView.setAdapter(adapter);
            hasInitializedAdapter = true;
        }

        ListViewAdapter adapter = (ListViewAdapter)listView.getAdapter();
        adapter.notesList = llist;
        adapter.notifyDataSetChanged();

        toggleViewVisibility(numRows > 0);
        dbMgr.close();
    }

    private void doDeleteNote(int id) {
        ListView v = (ListView) getView().findViewById(R.id.list_view);
        ListViewAdapter a = (ListViewAdapter)v.getAdapter();
        RecordsListElement e = a.notesList.get(id);

        DatabaseManager dbMgr = new DatabaseManager(this.mViewPagerAdapter.getFragmentActivity());
        dbMgr.open();

        // delete item in database
        dbMgr.delete(Integer.parseInt(e.get_id()));
        // delete audio item
        if (e.get_audio() != null && !e.get_audio().isEmpty()) {
            File f = new File(e.get_audio());
            if (f.exists()) {
                Log.v("doDeleteNote", f.getName());
                f.delete();
            }
        }
        // delete item in linked list
        a.notesList.remove(id);

        dbMgr.close();

        a.notifyDataSetChanged();
    }

    public void notifyDeleteNote(int id) {
        Log.d(TAG, "(notify) Delete: " + id);

        AlertDialog.Builder builder = new AlertDialog.Builder(getView().getContext());
        builder.setTitle("Delete")
                .setMessage("Do you wish to delete this note?")
                .setPositiveButton("DELETE", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        doDeleteNote(id);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        AlertDialog alert = builder.create();
        alert.show();
    }

    public void notifyEditNote(long _id, String textNote, String audioNote) {
        Log.d(TAG, "(notify) Edit: " + _id + ", withContent: " + textNote + ", withAudioNote: " + audioNote);
        // TODO: Not implemented
        Log.e(TAG, "Not implemented");
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        refreshData();

        // TODO: Maybe the next portion is not required anymore? We have edit/delete buttons now.
        // See ListViewAdapter.java

        /* Not needed anymore?
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
        });*/

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}