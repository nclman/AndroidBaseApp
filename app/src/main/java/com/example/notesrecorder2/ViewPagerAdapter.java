package com.example.notesrecorder2;

import android.content.Context;
import android.util.Log;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ViewPagerAdapter extends FragmentStateAdapter {

    private static String TAG = "ViewPagerAdapter";

    private enum ViewFragments {
        RECORDS_CREATE,
        RECORDS_LIST,
    };

    private Fragment mFragments[];
    private FragmentActivity mMainActivity;

    public ViewPagerAdapter(FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        this.mMainActivity = fragmentActivity;
        initViewFragments();
    }

    public Context getFragmentActivity() {
        return this.mMainActivity.getApplicationContext();
    }

    private void initViewFragments() {
        this.mFragments = new Fragment[ViewFragments.values().length];
        this.mFragments[ViewFragments.RECORDS_LIST.ordinal()]   = RecordsListFragment.newInstance(this, "unused1", "unused2");
        this.mFragments[ViewFragments.RECORDS_CREATE.ordinal()] = RecordsCreateFragment.newInstance(this, "unused1", "unused2");
    }

    public void refreshListFragment() {
        RecordsListFragment listFragment = (RecordsListFragment) this.mFragments[ViewFragments.RECORDS_LIST.ordinal()];
        listFragment.refreshData();
    }

    @Override
    public Fragment createFragment(int position) {
        Log.i(TAG, "position: " + position);

        if (position == ViewFragments.RECORDS_LIST.ordinal()) {
            Log.d(TAG, "Matching " + ViewFragments.RECORDS_LIST);
            return this.mFragments[ViewFragments.RECORDS_LIST.ordinal()];
        } else if (position == ViewFragments.RECORDS_CREATE.ordinal()) {
            Log.d(TAG, "Matching " + ViewFragments.RECORDS_CREATE);
            return this.mFragments[ViewFragments.RECORDS_CREATE.ordinal()];
        } else {
            Log.e(TAG, "Unknown position: " + position);
            return null;
        }
    }

    @Override
    public int getItemCount() {
        return ViewFragments.values().length;
    }
}
