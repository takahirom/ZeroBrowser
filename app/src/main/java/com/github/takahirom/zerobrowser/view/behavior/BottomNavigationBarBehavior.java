package com.github.takahirom.zerobrowser.view.behavior;

import android.content.Context;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;

public class BottomNavigationBarBehavior extends CoordinatorLayout.Behavior<View> {

    private int defaultDependencyTop = -1;

    public BottomNavigationBarBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, View bottomBar, View dependency) {
        return dependency instanceof AppBarLayout;
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, View bottomBar, View dependency) {
        if (defaultDependencyTop == -1) {
            defaultDependencyTop = dependency.getTop();
        }
        ViewCompat.setTranslationY(bottomBar, -dependency.getTop() + defaultDependencyTop);
        return true;
    }

}
