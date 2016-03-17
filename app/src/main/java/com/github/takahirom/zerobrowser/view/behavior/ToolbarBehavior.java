package com.github.takahirom.zerobrowser.view.behavior;

import android.app.Activity;
import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.View;

public class ToolbarBehavior extends CoordinatorLayout.Behavior<FloatingActionButton> {

    private final int screenHeight;
    private int defaultDependencyTop = -1;

    public ToolbarBehavior(Context context, AttributeSet attrs) {
        super();
        screenHeight = ((Activity) context).getWindow().getDecorView().getHeight();
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, FloatingActionButton fab, View dependency) {
        return dependency instanceof Toolbar;
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, FloatingActionButton fab, View dependency) {
        if (dependency.getBottom() > screenHeight) {
            fab.hide();
        } else {
            fab.show();
        }
        return true;
    }

}
