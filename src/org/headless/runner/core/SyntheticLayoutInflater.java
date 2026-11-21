package org.headless.runner.core;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.internal.policy.PhoneLayoutInflater;

import java.lang.reflect.Constructor;

public class SyntheticLayoutInflater extends PhoneLayoutInflater {
    public SyntheticLayoutInflater(Context context) {
        super(context);
    }

    public SyntheticLayoutInflater(LayoutInflater original, Context newContext) {
        super(original, newContext);
    }

    @Override
    public LayoutInflater cloneInContext(Context newContext) {
        return new SyntheticLayoutInflater(this, newContext);
    }

    @Override
    public View inflate(int resource, ViewGroup root, boolean attachToRoot) {
        return super.inflate(resource, root, attachToRoot);
    }
}
