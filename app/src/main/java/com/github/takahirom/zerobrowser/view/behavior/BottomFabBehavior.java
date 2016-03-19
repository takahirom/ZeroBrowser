package com.github.takahirom.zerobrowser.view.behavior;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.Display;
import android.view.View;

public class BottomFabBehavior extends CoordinatorLayout.Behavior<FloatingActionButton> {

    private final int screenHeight;
    private int defaultDependencyTop = -1;

    public BottomFabBehavior(Context context, AttributeSet attrs) {
        super();
        Display display = ((Activity) context).getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        screenHeight = size.y;
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, FloatingActionButton fab, View dependency) {
        return dependency instanceof Toolbar;
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, FloatingActionButton fab, View dependency) {
        if (dependency.getTranslationY() >= dependency.getHeight() / 2) {
            fab.hide();
        } else {
            fab.show();
        }
        return true;
    }

}