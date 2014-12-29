package com.platypii.baseline.ui;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;

import com.platypii.baseline.R;


class TabListener<T extends Fragment> implements ActionBar.TabListener {
    private final Activity mActivity;
    private final String mTag;
    private final Class<T> mClass;
    private final Bundle mArgs;
    private Fragment mFragment;

    public TabListener(Activity activity, String tag, Class<T> clz) {
        this(activity, tag, clz, null);
    }

    private TabListener(Activity activity, String tag, Class<T> clz, Bundle args) {
        mActivity = activity;
        mTag = tag;
        mClass = clz;
        mArgs = args;

        // Check to see if we already have a fragment for this tab, probably from a previously saved state. 
        // If so, deactivate it, because our initial state is that a tab isn't shown.
        mFragment = mActivity.getFragmentManager().findFragmentByTag(mTag);
        if(mFragment != null && !mFragment.isDetached()) {
            FragmentTransaction ft = mActivity.getFragmentManager().beginTransaction();
            // ft.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
            // ft.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
            // ft.setCustomAnimations(R.anim.slide_in, R.anim.slide_in);
            // ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE); // set type of animation
            ft.detach(mFragment);
            ft.commit();
        }
    }
    public void onTabSelected(Tab tab, FragmentTransaction ft) {
        if(mFragment == null) {
            mFragment = Fragment.instantiate(mActivity, mClass.getName(), mArgs);
            // ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN); // set type of animation
            // ft.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
            // ft.setCustomAnimations(R.anim.slide_in, R.anim.slide_in);
            ft.add(R.id.tab_container, mFragment, mTag);
        } else {
            ft.attach(mFragment);
        }
    }
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        if(mFragment != null) {
            ft.detach(mFragment);
        }
    }
    public void onTabReselected(Tab tab, FragmentTransaction ft) {}
}
