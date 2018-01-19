package com.navigationhybrid.androidnavigation;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.navigationhybrid.R;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Created by Listen on 2018/1/11.
 */

public class AwesomeFragment extends Fragment implements LifecycleObserver, FragmentManager.OnBackStackChangedListener {

    public static final String TAG = "AndroidNavigation";

    public static final String ARGS_SCENE_ID = "sceneId";
    public static final String ARGS_REQUEST_CODE = "requestCode";
    public static final String ARGS_ANIMATION = "animation";
    public static final String ARGS_TAB_BAR_ITEM = "tab_bar_item";

    /**
     * 隐藏软键盘
     */
    public static void hideSoftInput(View view) {
        if (view == null || view.getContext() == null) return;
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    // ------- lifecycle methods -------

    private PresentableActivity presentableActivity;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Activity activity = getActivity();
        if (!(activity instanceof PresentableActivity)) {
            throw new IllegalArgumentException("Activity must implements PresentableActivity!");
        }
        presentableActivity = (PresentableActivity) activity;
    }

    @Override
    public void onDetach() {
        presentableActivity = null;
        super.onDetach();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLifecycle().addObserver(this);
        getChildFragmentManager().addOnBackStackChangedListener(this);
    }

    @Override
    public void onDestroy() {
        getChildFragmentManager().removeOnBackStackChangedListener(this);
        super.onDestroy();
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        if(!isContainer()) {
            setBackgroundDrawable(root, new ColorDrawable(preferredBackgroundColor()));
        }

        AwesomeFragment parent = getParent();
        if (parent instanceof NavigationFragment) {
            this.topBar = createTopBar(getView());
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    private void setBackgroundDrawable(View root, Drawable drawable) {
        root.setBackground(drawable);
        getWindow().setBackgroundDrawable(null);
    }

    protected int preferredBackgroundColor() {
        return Color.WHITE;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isContainer()) {
            // Log.w(TAG, getDebugTag() + "#onResume-");
            setNeedsStatusBarAppearanceUpdate();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && isAdded()) {
            if (!isContainer()) {
                // Log.w(TAG, getDebugTag() + "#onHiddenChanged:-");
                setNeedsStatusBarAppearanceUpdate();
            }
        }

        // Log.w(TAG, getDebugTag() + "#onHiddenChanged:" + hidden);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser && isAdded()) {
            // Log.w(TAG, getDebugTag() + "#isVisibleToUser:-");
            setNeedsStatusBarAppearanceUpdate();
        }
        //  Log.w(TAG, getDebugTag() + "#isVisibleToUser:" + isVisibleToUser);
    }

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        PresentAnimation animation = getAnimation();

        handleHideBottomBarWhenPushed(transit, enter, animation);
        // ---------
        Log.d(TAG, getDebugTag() + "  " + animation.name() + " transit:" + transit + " enter:" + enter);

        if (transit == FragmentTransaction.TRANSIT_FRAGMENT_OPEN) {
            if (enter) {
                return AnimationUtils.loadAnimation(getContext(), animation.enter);
            } else {
                return AnimationUtils.loadAnimation(getContext(), animation.exit);
            }
        } else if (transit == FragmentTransaction.TRANSIT_FRAGMENT_CLOSE) {
            if (enter) {
                return AnimationUtils.loadAnimation(getContext(), animation.popEnter);
            } else {
                return AnimationUtils.loadAnimation(getContext(), animation.popExit);
            }
        }
        return super.onCreateAnimation(transit, enter, nextAnim);
    }

    @Override
    public void onBackStackChanged() {
        FragmentManager fragmentManager = getChildFragmentManager();
        int count = fragmentManager.getBackStackEntryCount();
        for (int i = 0; i < count; i++) {
            FragmentManager.BackStackEntry entry = fragmentManager.getBackStackEntryAt(i);
            Log.d(TAG, getClass().getSimpleName() + " Entry index:" + entry.getId() + " tag:" + entry.getName());
        }
    }

    // ------ lifecycle arch -------

    private boolean active;
    private LinkedList<Runnable> tasks = new LinkedList<>();

    protected void scheduleTask(Runnable runnable) {
        if (getLifecycle().getCurrentState() != Lifecycle.State.DESTROYED) {
            tasks.add(runnable);
            considerExecute();
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_ANY)
    void onStateChange() {
        if (getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
            // 清空队列
            tasks.clear();
            getLifecycle().removeObserver(this);
        } else {
            activeStateChanged(isActiveState(getLifecycle().getCurrentState()));
        }
    }

    protected void activeStateChanged(boolean newActive) {
        if (newActive != this.active) {
            this.active = newActive;
            considerExecute();
        }
    }

    private void considerExecute() {
        if (active) {
            if (isActiveState(getLifecycle().getCurrentState())) {
                if (tasks.size() > 0) {
                    for (Runnable task : tasks) {
                        task.run();
                    }
                    tasks.clear();
                }
            }
        }
    }

    protected boolean isActiveState(Lifecycle.State state) {
        return state.isAtLeast(Lifecycle.State.STARTED);
    }

    protected boolean isAtLeastStarted() {
        return isActiveState(getLifecycle().getCurrentState());
    }

    protected boolean isAtLeastCreated() {
        return getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.CREATED);
    }

    // ------- navigation ------

    private String sceneId;

    public String getSceneId() {
        if (this.sceneId == null) {
            Bundle args = FragmentHelper.getArguments(this);
            String sceneId = args.getString(ARGS_SCENE_ID);
            if (sceneId == null) {
                sceneId = UUID.randomUUID().toString();
                args.putString(ARGS_SCENE_ID, sceneId);
            }

            this.sceneId = sceneId;
        }
        return this.sceneId;
    }

    public void presentFragment(AwesomeFragment fragment, int requestCode) {
        if (presentableActivity != null) {
            Bundle args = FragmentHelper.getArguments(fragment);
            args.putInt(ARGS_REQUEST_CODE, requestCode);
            presentableActivity.presentFragment(fragment);
        }
    }

    public void dismissFragment() {
        AwesomeFragment parent = getParent();
        if (parent != null) {
            parent.setResult(resultCode, result);
            parent.dismissFragment();
            return;
        }

        if (presentableActivity != null) {
            presentableActivity.dismissFragment(this);
        }
    }

    private int requestCode;
    private int resultCode;
    private Bundle result;

    public void setResult(int resultCode, Bundle data) {
        this.result = data;
        this.resultCode = resultCode;
    }

    public int getRequestCode() {
        if (requestCode == 0) {
            Bundle args = FragmentHelper.getArguments(this);
            requestCode = args.getInt(ARGS_REQUEST_CODE);
        }
        return requestCode;
    }

    public int getResultCode() {
        return resultCode;
    }

    public Bundle getResultData() {
        return result;
    }

    public void onFragmentResult(int requestCode, int resultCode, Bundle data) {
        //Log.i(TAG, toString() + "#onFragmentResult requestCode=" + requestCode + " resultCode=" + resultCode + " data=" + data);
        List<AwesomeFragment> fragments = getFragments();
        for (AwesomeFragment child : fragments) {
            child.onFragmentResult(requestCode, resultCode, data);
        }
    }

    public void addFragment(final int containerId, final AwesomeFragment fragment, final PresentAnimation animation) {
        if (isAtLeastStarted()) {
            executeAddFragment(containerId, fragment, animation);
        } else {
            scheduleTask(new Runnable() {
                @Override
                public void run() {
                    executeAddFragment(containerId, fragment, animation);
                }
            });
        }
    }

    private void executeAddFragment(int containerId, AwesomeFragment fragment, PresentAnimation animation) {
        FragmentHelper.addFragment(getChildFragmentManager(), containerId, fragment, animation);
    }

    boolean dispatchBackPressed() {
        FragmentManager fragmentManager = getChildFragmentManager();
        int count = fragmentManager.getBackStackEntryCount();
        Fragment fragment = fragmentManager.getPrimaryNavigationFragment();
        if (fragment != null) {
            AwesomeFragment child = (AwesomeFragment) fragment;
            return child.dispatchBackPressed() || onBackPressed();
        } else if (count > 0) {
            FragmentManager.BackStackEntry backStackEntry = fragmentManager.getBackStackEntryAt(count - 1);
            AwesomeFragment child = (AwesomeFragment) fragmentManager.findFragmentByTag(backStackEntry.getName());
            return child.dispatchBackPressed() || onBackPressed();
        } else {
            return onBackPressed();
        }
    }

    protected boolean onBackPressed() {
        return false;
    }

    public AwesomeFragment getPresentedFragment() {
        AwesomeFragment parent = getParent();
        if (parent != null) {
            return parent.getPresentedFragment();
        }
        return presentableActivity.getPresentedFragment(this);
    }

    public AwesomeFragment getPresentingFragment() {
        AwesomeFragment parent = getParent();
        if (parent != null) {
            return parent.getPresentingFragment();
        }
        return presentableActivity.getPresentingFragment(this);
    }

    public AwesomeFragment getInnermostFragment() {
        FragmentManager fragmentManager = getChildFragmentManager();
        int count = fragmentManager.getBackStackEntryCount();
        Fragment fragment = fragmentManager.getPrimaryNavigationFragment();
        if (fragment != null) {
            AwesomeFragment child = (AwesomeFragment) fragment;
            return child.getInnermostFragment();
        } else if (count > 0) {
            FragmentManager.BackStackEntry backStackEntry = fragmentManager.getBackStackEntryAt(count - 1);
            AwesomeFragment child = (AwesomeFragment) fragmentManager.findFragmentByTag(backStackEntry.getName());
            return child.getInnermostFragment();
        }
        return this;
    }

    public String getDebugTag() {
        if (getActivity() == null) {
            return null;
        }
        AwesomeFragment parent = getParent();
        if (parent == null) {
            return "#" + findIndexAtAdded() + "-" + getClass().getSimpleName();
        } else {
            return parent.getDebugTag() + "#" + findIndexAtAdded() + "-" + getClass().getSimpleName();
        }
    }

    protected int findIndexAtBackStack() {
        return FragmentHelper.findIndexAtBackStack(getFragmentManager(), this);
    }

    protected int findIndexAtAdded() {
        List<Fragment> fragments = getFragmentManager().getFragments();
        return fragments.indexOf(this);
    }

    protected AwesomeFragment getParent() {
        Fragment fragment = getParentFragment();
        if (fragment != null && fragment instanceof AwesomeFragment) {
            return (AwesomeFragment) fragment;
        }
        return null;
    }

    protected List<AwesomeFragment> getFragments() {
        List<Fragment> fragments = getChildFragmentManager().getFragments();
        List<AwesomeFragment> children = new ArrayList<>();
        for (int i = 0, size = fragments.size(); i < size; i++) {
            children.add((AwesomeFragment) fragments.get(i));
        }
        return children;
    }

    protected int getChildFragmentCountAtBackStack() {
        FragmentManager fragmentManager = getChildFragmentManager();
        return fragmentManager.getBackStackEntryCount();
    }

    private PresentAnimation animation = null;

    public void setAnimation(PresentAnimation animation) {
        Bundle bundle = FragmentHelper.getArguments(this);
        bundle.putString(ARGS_ANIMATION, animation.name());
        this.animation = animation;
    }

    public PresentAnimation getAnimation() {
        if (animation == null) {
            Bundle bundle = FragmentHelper.getArguments(this);
            String animationName = bundle.getString(ARGS_ANIMATION);
            if (animationName != null) {
                animation = PresentAnimation.valueOf(animationName);
            } else {
                animation = PresentAnimation.None;
            }
        }
        return animation;
    }

    public boolean isContainer() {
        return false;
    }

    // ------- statusBar --------

    protected String preferredStatusBarStyle() {
        AwesomeFragment childFragmentForStatusBarStyle = childFragmentForStatusBarStyle();
        if (childFragmentForStatusBarStyle != null) {
            return childFragmentForStatusBarStyle.preferredStatusBarStyle();
        }
        return "light-content";
    }

    protected boolean preferredStatusBarHidden() {
        AwesomeFragment childFragmentForStatusBarHidden = childFragmentForStatusBarHidden();
        if (childFragmentForStatusBarHidden != null) {
            return childFragmentForStatusBarHidden.preferredStatusBarHidden();
        }
        return false;
    }

    protected int preferredStatusBarColor() {
        AwesomeFragment childFragmentForStatusBarColor = childFragmentForStatusBarColor();
        if (childFragmentForStatusBarColor != null) {
            return childFragmentForStatusBarColor.preferredStatusBarColor();
        }
        return Color.TRANSPARENT;
    }

    protected boolean preferredStatusBarColorAnimated() {
        AwesomeFragment childFragmentForStatusBarColor = childFragmentForStatusBarColor();
        if (childFragmentForStatusBarColor != null) {
            return childFragmentForStatusBarColor.preferredStatusBarColorAnimated();
        }
        return false;
    }

    protected AwesomeFragment childFragmentForStatusBarStyle() {
        return null;
    }

    protected AwesomeFragment childFragmentForStatusBarHidden() {
        return null;
    }

    protected AwesomeFragment childFragmentForStatusBarColor() {
        return null;
    }

    public void setNeedsStatusBarAppearanceUpdate() {
        AwesomeFragment parent = getParent();
        if (parent != null) {
            parent.setNeedsStatusBarAppearanceUpdate();
        } else {
            // statusBarStyle
            setStatusBarStyle(preferredStatusBarStyle());

            // statusBarHidden
            setStatusBarHidden(preferredStatusBarHidden());

            // statusBarColor
            setStatusBarColor(preferredStatusBarColor(), preferredStatusBarColorAnimated());
        }
    }

    public Window getWindow() {
        if (getActivity() == null) {
            return null;
        }
        return getActivity().getWindow();
    }

    protected void setStatusBarStyle(String style) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    style.equals("dark-content") ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0);
        }
    }

    protected void setStatusBarTranslucent(boolean translucent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View decorView = getWindow().getDecorView();
            if (translucent) {
                decorView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                        WindowInsets defaultInsets = v.onApplyWindowInsets(insets);
                        return defaultInsets.replaceSystemWindowInsets(
                                defaultInsets.getSystemWindowInsetLeft(),
                                0,
                                defaultInsets.getSystemWindowInsetRight(),
                                defaultInsets.getSystemWindowInsetBottom());
                    }
                });
            } else {
                decorView.setOnApplyWindowInsetsListener(null);
            }

            ViewCompat.requestApplyInsets(decorView);
        }
    }

    protected void setStatusBarHidden(boolean hidden) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (hidden) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            } else {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        }
    }

    protected void setStatusBarColor(int color, boolean animated) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            if (animated) {
                int curColor = getWindow().getStatusBarColor();
                ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), curColor, color);

                colorAnimation.addUpdateListener(
                        new ValueAnimator.AnimatorUpdateListener() {
                            @TargetApi(21)
                            @Override
                            public void onAnimationUpdate(ValueAnimator animator) {
                                getWindow()
                                        .setStatusBarColor((Integer) animator.getAnimatedValue());
                            }
                        });
                colorAnimation.setDuration(300).setStartDelay(0);
                colorAnimation.start();
            } else {
                getWindow().setStatusBarColor(color);
            }
        }
    }

    protected void appendStatusBarPaddingAndHeight(View view, int viewHeight) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (view != null) {
                int statusBarHeight = getStatusBarHeight();
                view.setPadding(view.getPaddingLeft(), statusBarHeight, view.getPaddingRight(),
                        view.getPaddingBottom());
                view.getLayoutParams().height = statusBarHeight + viewHeight;
            }
        }
    }

    protected void removeStatusBarPaddingAndHeight(View view, int viewHeight) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (view != null) {
                view.setPadding(view.getPaddingLeft(), 0, view.getPaddingRight(),
                        view.getPaddingBottom());
                view.getLayoutParams().height = viewHeight;
            }
        }
    }

    private int getStatusBarHeight() {
        int statusBarHeight1 = -1;
        //获取status_bar_height资源的ID
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            //根据资源ID获取响应的尺寸值
            statusBarHeight1 = getResources().getDimensionPixelSize(resourceId);
        }
        return statusBarHeight1;
    }

    protected int getTopBarHeight() {
        TypedValue typedValue = new TypedValue();
        int height = 0;
        if (getContext().getTheme().resolveAttribute(R.attr.actionBarSize, typedValue, true)) {
            height = (int) TypedValue.complexToDimension(typedValue.data, getContext().getResources().getDisplayMetrics());
        }
        return height;
    }

    // ------ NavigationFragment -----

    public boolean hidesBottomBarWhenPushed() {
        return true;
    }

    boolean shouldHideBottomBarWhenPushed() {
        NavigationFragment navigationFragment = getNavigationFragment();
        return navigationFragment.getRootFragment().hidesBottomBarWhenPushed();
    }

    protected void handleHideBottomBarWhenPushed(int transit, boolean enter, PresentAnimation animation) {
        // handle hidesBottomBarWhenPushed
        if (!isContainer()) {
            NavigationFragment navigationFragment = getNavigationFragment();
            if (navigationFragment != null) {
                TabBarFragment tabBarFragment = navigationFragment.getTabBarFragment();
                if (tabBarFragment != null) {
                    int index = findIndexAtBackStack();
                    if (transit == FragmentTransaction.TRANSIT_FRAGMENT_OPEN) {
                        if (enter) {
                            if (index == 0) {
                                tabBarFragment.getBottomNavigationBar().setVisibility(View.VISIBLE);
                            } else if (index == 1 && shouldHideBottomBarWhenPushed()) {
                                tabBarFragment.hideBottomNavigationBarAnimatedWhenPush(animation.exit);
                            }
                        }
                    } else if (transit == FragmentTransaction.TRANSIT_FRAGMENT_CLOSE) {
                        if (enter && index == 0 && shouldHideBottomBarWhenPushed()) {
                            tabBarFragment.showBottomNavigationBarAnimatedWhenPop(animation.popEnter);
                        }
                    }
                }
            }
        }
    }

    protected boolean shouldHideBackButton() {
        return false;
    }

    public NavigationFragment getNavigationFragment() {
        if (this instanceof NavigationFragment) {
            return (NavigationFragment) this;
        }
        AwesomeFragment parent = getParent();
        if (parent != null) {
            return parent.getNavigationFragment();
        }
        return null;
    }

    private TopBar topBar;

    public TopBar getTopBar() {
        return topBar;
    }

    protected TopBar createTopBar(View parent) {
        if (getView() == null || getContext() == null) return null;

        TypedValue typedValue = new TypedValue();
        int height = 0;
        if (getContext().getTheme().resolveAttribute(R.attr.actionBarSize, typedValue, true)) {
            height = (int) TypedValue.complexToDimension(typedValue.data, getContext().getResources().getDisplayMetrics());
        }

        TopBar topBar = new TopBar(getContext());
        if (parent instanceof LinearLayout) {
            LinearLayout linearLayout = (LinearLayout) parent;
            linearLayout.addView(topBar, 0, new LinearLayout.LayoutParams(-1, height));
        } else if (parent instanceof FrameLayout) {
            FrameLayout frameLayout = (FrameLayout) parent;
            frameLayout.addView(topBar, new FrameLayout.LayoutParams(-1, height));
        } else {
            throw new UnsupportedOperationException("NavigationFragment 还没适配 " + parent.getClass().getSimpleName());
        }
        appendStatusBarPaddingAndHeight(topBar, getTopBarHeight());
        return topBar;
    }

    private NavigationItem navigationItem;

    public void setNavigationItem(NavigationItem item) {
        navigationItem = item;
    }

    public NavigationItem getNavigationItem() {
        return navigationItem;
    }

    // ------ TabBarFragment -------

    public TabBarFragment getTabBarFragment() {
        if (this instanceof TabBarFragment) {
            return (TabBarFragment) this;
        }
        AwesomeFragment parent = getParent();
        if (parent != null) {
            return parent.getTabBarFragment();
        }
        return null;
    }

    private TabBarItem tabBarItem;

    public void setTabBarItem(TabBarItem item) {
        tabBarItem = item;
        Bundle args = FragmentHelper.getArguments(this);
        args.putParcelable(ARGS_TAB_BAR_ITEM, tabBarItem);
    }

    public TabBarItem getTabBarItem() {
        if (tabBarItem == null) {
            Bundle args = FragmentHelper.getArguments(this);
            tabBarItem = args.getParcelable(ARGS_TAB_BAR_ITEM);
        }
        return tabBarItem;
    }

    // ------ DrawerFragment -------

    public DrawerFragment getDrawerFragment() {
        if (this instanceof DrawerFragment) {
            return (DrawerFragment) this;
        }
        AwesomeFragment parent = getParent();
        if (parent != null) {
            return parent.getDrawerFragment();
        }
        return null;
    }

}