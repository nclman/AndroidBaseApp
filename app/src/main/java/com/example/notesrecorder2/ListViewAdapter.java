package com.example.notesrecorder2;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.LinkedList;

public class ListViewAdapter extends BaseAdapter {

    private static final String TAG = "ListViewAdapter";
    public LinkedList<RecordsListElement> notesList;
    private final LayoutInflater inflater;
    private final RecordsListFragment mListFragment;

    private LinearLayout linearLayout;
    private static final int BUTTON_DELETE_BASE_ID = 11011000;
    private static final int BUTTON_EDIT_BASE_ID = 55055000;

    public ListViewAdapter(RecordsListFragment listFragment, Context c) {
        this.mListFragment = listFragment;
        this.inflater = LayoutInflater.from(c);
    }

    @Override
    public int getCount() {
        if (this.notesList == null) {
            return 0;
        } else {
            return this.notesList.size();
        }
    }

    @Override
    public Object getItem(int i) {
        return (Object) notesList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewgroup) {
        view = inflater.inflate(R.layout.record_list_item, null);
        TextView id_note = (TextView) view.findViewById(R.id._id);
        TextView text_note = (TextView) view.findViewById(R.id.textnote);

        RecordsListElement e = notesList.get(i);
        id_note.setText(e.get_id());
        if (e.get_txt() != null && e.get_txt().length() > 20) {
            text_note.setText(e.get_txt().substring(0, 20) + "...");
            text_note.setClickable(true);
        } else {
            text_note.setText(e.get_txt());
        }
        linearLayout = (LinearLayout) this.mListFragment.getView().findViewById(R.id.linearLayout1);
        text_note.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Make a popup to show the full text
                View customView = inflater.inflate(R.layout.popup,null);

                TextView textview = (TextView) customView.findViewById(R.id.popup_text);
                textview.setText(e.get_txt());

                Button closePopupBtn = (Button) customView.findViewById(R.id.closePopupBtn);

                //instantiate popup window
                PopupWindow popupWindow = new PopupWindow(customView,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);

                //display the popup window
                popupWindow.showAtLocation(linearLayout, Gravity.CENTER, 0, 0);

                //close the popup window on button click
                closePopupBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        popupWindow.dismiss();
                    }
                });
            }
        });

        Button btnDelete = (Button) view.findViewById(R.id.buttonDelete);
        btnDelete.setId(BUTTON_DELETE_BASE_ID + i);
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDeleteClick(v);
            }
        });
        Button btnEdit = (Button) view.findViewById(R.id.buttonEdit);
        btnEdit.setId(BUTTON_EDIT_BASE_ID + i);
        btnEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onEditClick(v);
            }
        });

        Button btnPlay = (Button) view.findViewById(R.id.buttonPlay);
        btnPlay.setId(i);
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playAudio(v.getId());
            }
        });

        if (e.get_audio() == null || e.get_audio().isEmpty()) {
            btnPlay.setEnabled(false);
        }

        return view;
    }

    private void onDeleteClick(View v) {
        int selectedIndex = v.getId() - BUTTON_DELETE_BASE_ID;
        Log.v(TAG, "Selected Index (delete): " + selectedIndex);
        this.mListFragment.notifyDeleteNote(selectedIndex);
    }

    private void onEditClick(View v) {
        int selectedIndex = v.getId() - BUTTON_EDIT_BASE_ID;
        Log.v(TAG, "Selected Index (edit): " + selectedIndex);
        this.mListFragment.notifyEditNote(
                Long.parseLong(this.notesList.get(selectedIndex).get_id()),
                this.notesList.get(selectedIndex).get_txt(),
                this.notesList.get(selectedIndex).get_audio());
    }

    private void playAudio(int index) {
        Uri uri = Uri.fromFile(new File(this.notesList.get(index).get_audio()));
        Log.d(TAG, "Playing " + uri.getPath());
        MediaPlayer mp = MediaPlayer.create(this.mListFragment.getContext(), uri);
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.stop();
                mp.release();
                Log.d(TAG, "Play completed " + uri.getPath());
            }
        });

        if (mp.isPlaying() == false) {
            mp.start();
        } else {
            mp.stop();
        }
    }
}
