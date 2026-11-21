package com.android.internal.policy;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class PhoneLayoutInflater extends LayoutInflater {
    private static final String[] sClassPrefixList = {
        "android.widget.",
        "android.webkit.",
        "android.app."
    };

    public PhoneLayoutInflater(Context context) {
        super(context);
    }

    protected PhoneLayoutInflater(LayoutInflater original, Context newContext) {
        super(original, newContext);
    }

    @Override
    public View inflate(int resource, ViewGroup root, boolean attachToRoot) {
        View view = super.inflate(resource, root, attachToRoot);
        configureContainers(resource, view);
        return view;
    }

    @Override
    public View inflate(org.xmlpull.v1.XmlPullParser parser, ViewGroup root, boolean attachToRoot) {
        View view = super.inflate(parser, root, attachToRoot);
        configureContainers(0, view);
        return view;
    }

    private void configureContainers(int resource, View view) {
        if (!(view instanceof ViewGroup)) return;
        ViewGroup vg = (ViewGroup) view;

        // If this is a system layout (< 0x7f000000, e.g. PhoneWindow screen_simple)
        if (resource > 0 && resource < 0x7f000000) {
            if (vg.getId() == View.NO_ID && vg.findViewById(android.R.id.content) == null) {
                vg.setId(android.R.id.content);
            }
            return;
        }

        // Safety fallback specifically for AppCompat subdecor (abc_screen_simple)
        try {
            Class<?> rIdClass = Class.forName("androidx.appcompat.R$id", false, getContext().getClassLoader());
            int actionContentId = rIdClass.getField("action_bar_activity_content").getInt(null);
            if (resource == 0x7f0d0015 || vg.getClass().getName().contains("FitWindowsLinearLayout")) {
                if (vg.findViewById(actionContentId) == null) {
                    Class<?> cflClass = Class.forName("androidx.appcompat.widget.ContentFrameLayout", false, getContext().getClassLoader());
                    View cfl = (View) cflClass.getConstructor(Context.class).newInstance(getContext());
                    cfl.setId(actionContentId);
                    vg.addView(cfl, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ));
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    protected View onCreateView(String name, AttributeSet attrs) throws ClassNotFoundException {
        for (String prefix : sClassPrefixList) {
            try {
                View view = createView(name, prefix, attrs);
                if (view != null) {
                    return view;
                }
            } catch (ClassNotFoundException e) {
            }
        }

        return super.onCreateView(name, attrs);
    }

    @Override
    public LayoutInflater cloneInContext(Context newContext) {
        return new PhoneLayoutInflater(this, newContext);
    }
}
