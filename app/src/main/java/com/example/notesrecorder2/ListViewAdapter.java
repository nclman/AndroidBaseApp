package com.example.notesrecorder2;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

public class ListViewAdapter extends BaseAdapter {
    private static final String TAG = "ListViewAdapter";
    private final String[] idNotes;
    private final String[] textNotes;
    private final String[] audioNotes;
    private final LayoutInflater inflater;

    private static final int BUTTON_DELETE_BASE_ID = 11011000;
    private static final int BUTTON_EDIT_BASE_ID = 55055000;

    public ListViewAdapter(Context c, String[] idNotes, String[] textNotes, String[] audioNotes) {
        this.idNotes = idNotes;
        this.textNotes = textNotes;
        this.audioNotes = audioNotes;
        inflater = LayoutInflater.from(c);
    }

    @Override
    public int getCount() {
        return textNotes.length;
    }

    @Override
    public Object getItem(int i) {
        return null;
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
        TextView audio_note = (TextView) view.findViewById(R.id.audionote);
        id_note.setText(idNotes[i]);
        text_note.setText(textNotes[i]);
        if (audioNotes[i].isEmpty()) {
            audio_note.setText("(no audio note)");
        } else {
            audio_note.setText(audioNotes[i]);
        }

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

        return view;
    }

    private void onDeleteClick(View v) {
        int selectedIndex = v.getId() - BUTTON_DELETE_BASE_ID;
        Log.v(TAG, "Selected Index (delete): " + selectedIndex);
        //TODO: perform the deletion of the item
    }

    private void onEditClick(View v) {
        int selectedIndex = v.getId() - BUTTON_EDIT_BASE_ID;
        Log.v(TAG, "Selected Index (edit): " + selectedIndex);
        //TODO: perform the editing of the item
    }
}
