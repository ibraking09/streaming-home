package local.firetv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import local.firetv.home.AppCatalog.App;
import local.firetv.home.AppCatalog.Artwork;

public final class HomeActivity extends Activity {
    private static final int TEXT = 0xfff2f2f2;
    private static final int MUTED = 0xffb9b9bc;
    private static final int PANEL = 0xff222223;
    private static final int[] BACKGROUNDS = {0xff151515, 0xff080808, 0xff121418};
    private static final String[] BACKGROUND_NAMES = {"Dark", "Black", "Original blue"};
    private static final String[] SIZES = {"Small", "Medium", "Large"};

    private enum Page { HOME, APPS, SETTINGS, EDIT, ADD_FAVORITES }

    private final Map<String, App> apps = new LinkedHashMap<>();
    private final Set<String> changedPackages = new LinkedHashSet<>();
    private final Runnable refreshPackages = () -> refreshCatalog();
    private final BroadcastReceiver packageEvents = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getData() != null) changedPackages.add(intent.getData().getSchemeSpecificPart());
            if (screen != null) {
                screen.removeCallbacks(refreshPackages);
                screen.postDelayed(refreshPackages, 120);
            }
        }
    };

    private LauncherPrefs prefs;
    private AppCatalog catalog;
    private Page page = Page.HOME;
    private Page addFavoritesFrom = Page.EDIT;
    private Screen screen;
    private HomeRow homeRow;
    private GridView grid;
    private AlertDialog dialog;
    private String homeFocus;
    private App selectTarget;
    private Page selectPage;
    private int selectKey;
    private boolean selectWasLong;
    private boolean catalogReady;
    private boolean fullDrawReported;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new LauncherPrefs(this);
        catalog = new AppCatalog(this);
        show(Page.HOME, null);
        IntentFilter changes = new IntentFilter();
        changes.addAction(Intent.ACTION_PACKAGE_ADDED);
        changes.addAction(Intent.ACTION_PACKAGE_REMOVED);
        changes.addAction(Intent.ACTION_PACKAGE_CHANGED);
        changes.addAction(Intent.ACTION_PACKAGE_REPLACED);
        changes.addDataScheme("package");
        registerReceiver(packageEvents, changes);
        refreshCatalog();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (dialog != null) dialog.dismiss();
        if (page != Page.HOME) show(Page.HOME, homeFocus);
    }

    @Override
    protected void onResume() {
        super.onResume();
        HomeRedirect.leaveSystemSession();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageEvents);
        screen.removeCallbacks(refreshPackages);
        catalog.close();
        super.onDestroy();
    }

    private void refreshCatalog() {
        Set<String> packages = new LinkedHashSet<>(changedPackages);
        changedPackages.clear();
        catalog.refresh(packages, updated -> {
            String focus = catalogReady ? focusedKey() : null;
            apps.clear();
            for (App app : updated) apps.put(app.packageName, app);
            catalogReady = true;
            if (page != Page.SETTINGS) show(page, focus);
        });
    }

    private void show(Page next, String focus) {
        if (page == Page.HOME && screen != null && next != Page.HOME) homeFocus = focusedKey();
        clearSelect();
        page = next;
        homeRow = null;
        grid = null;
        Screen previous = screen;
        switch (next) {
            case HOME: screen = buildHome(); break;
            case APPS: screen = buildGrid(false); break;
            case SETTINGS: screen = buildSettings(); break;
            case EDIT: screen = buildEdit(); break;
            case ADD_FAVORITES: screen = buildGrid(true); break;
        }
        if (previous != null) previous.removeCallbacks(refreshPackages);
        setContentView(screen);
        screen.takeFocus(focus);
        if (!changedPackages.isEmpty()) screen.postDelayed(refreshPackages, 120);
    }

    private Screen buildHome() {
        Screen view = new Screen("Home", true);
        HeaderButton allApps = new HeaderButton(false, "All Apps");
        HeaderButton settings = new HeaderButton(true, "Settings");
        view.addHeader("all-apps", allApps, () -> show(Page.APPS, null));
        view.addHeader("settings", settings, () -> show(Page.SETTINGS, null));
        allApps.setNextFocusRightId(settings.getId());
        settings.setNextFocusLeftId(allApps.getId());
        if (!catalogReady) return view;
        List<App> favorites = visibleFavorites();
        if (favorites.isEmpty()) {
            TextView add = button("Add favorites");
            view.setBody(add);
            view.register("add", add);
            add.setOnClickListener(v -> addFavorites(Page.HOME));
            add.setNextFocusUpId(allApps.getId());
            allApps.setNextFocusDownId(add.getId());
            settings.setNextFocusDownId(add.getId());
            view.defaultFocus = add;
            view.postOnAnimation(() -> reportHomeDrawn());
            return view;
        }
        homeRow = new HomeRow();
        view.setBody(homeRow);
        for (App app : favorites) {
            Tile tile = new Tile(true);
            tile.bind(app, prefs.labels(), false);
            tile.setOnClickListener(v -> launch(app));
            tile.setOnLongClickListener(v -> {
                appMenu(app, true);
                return true;
            });
            view.register(app.packageName, tile);
            homeRow.addTile(tile);
        }
        for (int i = 0; i < homeRow.tiles.size(); i++) {
            Tile tile = homeRow.tiles.get(i);
            tile.setNextFocusUpId((i < 2 ? allApps : settings).getId());
            tile.setNextFocusDownId(tile.getId());
        }
        allApps.setNextFocusDownId(homeRow.tiles.get(Math.min(1, favorites.size() - 1)).getId());
        settings.setNextFocusDownId(homeRow.tiles.get(Math.min(3, favorites.size() - 1)).getId());
        view.defaultFocus = homeRow.tiles.get(0);
        return view;
    }

    private Screen buildGrid(boolean editing) {
        Screen view = secondary(editing ? "Add favorites" : "All Apps");
        GridView list = new GridView(this) {
            @Override
            protected void onFocusChanged(boolean gain, int direction, android.graphics.Rect previous) {
                super.onFocusChanged(gain, direction, previous);
                updateGridFocus(this);
            }
        };
        grid = list;
        int cols = 6 - prefs.size();
        list.setNumColumns(cols);
        list.setHorizontalSpacing(dp(12));
        list.setVerticalSpacing(dp(16));
        list.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setClipChildren(false);
        list.setPadding(dp(3), dp(3), dp(3), dp(3));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setSmoothScrollbarEnabled(true);
        list.setAdapter(new AppsAdapter(editing, new ArrayList<>(apps.values()), cols));
        list.setOnItemClickListener((parent, item, position, id) ->
                selectApp((App) parent.getItemAtPosition(position), editing));
        list.setOnItemLongClickListener((parent, item, position, id) -> {
            appMenu((App) parent.getItemAtPosition(position), false);
            return true;
        });
        list.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View item, int position, long id) {
                updateGridFocus(list);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { updateGridFocus(list); }
        });
        view.setBody(list);
        view.register("grid", list);
        list.setNextFocusUpId(view.headers.get(0).getId());
        view.headers.get(0).setNextFocusDownId(list.getId());
        if (!apps.isEmpty()) view.defaultFocus = list;
        return view;
    }

    private void updateGridFocus(GridView list) {
        int selected = list.getSelectedItemPosition() - list.getFirstVisiblePosition();
        for (int i = 0; i < list.getChildCount(); i++) {
            ((Tile) list.getChildAt(i)).highlight(list.hasFocus() && i == selected);
        }
    }

    private Screen buildSettings() {
        Screen view = secondary("Launcher settings");
        LinearLayout options = options(view);
        menuRow(view, options, "edit", "Edit Home", "", () -> show(Page.EDIT, null));
        menuRow(view, options, "background", "Background", BACKGROUND_NAMES[prefs.background()],
                () -> choose("Background", BACKGROUND_NAMES, prefs.background(), index -> {
                    prefs.background(index);
                    show(Page.SETTINGS, "background");
                }));
        menuRow(view, options, "labels", "Application labels", prefs.labels() ? "Shown" : "Hidden", () -> {
            prefs.toggleLabels();
            show(Page.SETTINGS, "labels");
        });
        menuRow(view, options, "size", "Application tile size", SIZES[prefs.size()],
                () -> choose("Application tile size", SIZES, prefs.size(), index -> {
                    prefs.size(index);
                    show(Page.SETTINGS, "size");
                }));
        menuRow(view, options, "animations", "Selection animations", prefs.animations() ? "On" : "Off", () -> {
            prefs.toggleAnimations();
            show(Page.SETTINGS, "animations");
        });
        menuRow(view, options, "defaults", "Restore default launcher preferences", "", () ->
                confirm("Restore default launcher preferences", "This resets favorites and appearance.", () -> {
                    prefs.reset();
                    show(Page.SETTINGS, "defaults");
                }));
        menuRow(view, options, "system", "Fire OS Settings", "", this::openSystemSettings);
        menuRow(view, options, "amazon", "Amazon Home", "", this::openAmazonHome);
        return view;
    }

    private Screen buildEdit() {
        Screen view = secondary("Edit Home");
        LinearLayout options = options(view);
        menuRow(view, options, "add", "Add favorites", "", () -> addFavorites(Page.EDIT));
        for (App app : visibleFavorites()) {
            menuRow(view, options, app.packageName, app.label, "", () -> appMenu(app, true));
        }
        menuRow(view, options, "defaults", "Restore default favorites", "", () ->
                confirm("Restore default favorites", "This replaces the current Home app order.", () -> {
                    prefs.resetFavorites();
                    show(Page.EDIT, "defaults");
                }));
        return view;
    }

    private Screen secondary(String title) {
        Screen view = new Screen(title, false);
        TextView back = button("Back");
        view.addHeader("back", back, this::onBackPressed);
        return view;
    }

    private LinearLayout options(Screen view) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(options);
        view.setBody(scroll);
        return options;
    }

    private void menuRow(Screen screen, LinearLayout options, String key, String label, String value,
                         Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setFocusable(true);
        row.setBackground(buttonBackground(PANEL));
        TextView caption = text(label, 16);
        row.addView(caption, new LinearLayout.LayoutParams(0, -1, 1f));
        if (!value.isEmpty()) {
            TextView detail = text(value, 14);
            detail.setTextColor(MUTED);
            row.addView(detail, new LinearLayout.LayoutParams(-2, -1));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(41));
        params.bottomMargin = dp(5);
        options.addView(row, params);
        screen.register(key, row);
        row.setOnClickListener(v -> action.run());
        if (screen.defaultFocus == null) {
            screen.defaultFocus = row;
            row.setNextFocusUpId(screen.headers.get(0).getId());
            screen.headers.get(0).setNextFocusDownId(row.getId());
        }
    }

    private List<App> visibleFavorites() {
        List<App> visible = new ArrayList<>();
        for (String pkg : prefs.favorites()) {
            App app = apps.get(pkg);
            if (app != null) visible.add(app);
        }
        return visible;
    }

    private void addFavorites(Page from) {
        addFavoritesFrom = from;
        show(Page.ADD_FAVORITES, null);
    }

    private void appMenu(App app, boolean favoriteMenu) {
        Map<String, Runnable> commands = new LinkedHashMap<>();
        List<String> favorites = prefs.favorites();
        boolean pinned = favorites.contains(app.packageName);
        if (favoriteMenu) {
            List<App> visible = visibleFavorites();
            int here = visible.indexOf(app);
            if (here > 0) commands.put("Move left", () -> move(app, visible.get(here - 1)));
            if (here >= 0 && here + 1 < visible.size()) {
                commands.put("Move right", () -> move(app, visible.get(here + 1)));
            }
        } else {
            commands.put(pinned ? "Remove from Home" : "Add to Home", () -> toggleFavorite(app));
            commands.put("Launch application", () -> launch(app));
        }
        if (favoriteMenu && pinned) commands.put("Remove from Home", () -> toggleFavorite(app));
        commands.put("App information", () -> appInformation(app));
        String[] choices = commands.keySet().toArray(new String[0]);
        displayDialog(new AlertDialog.Builder(this).setTitle(app.label)
                .setItems(choices, (d, which) -> commands.get(choices[which]).run()));
    }

    private void selectApp(App app, boolean editing) {
        if (editing) toggleFavorite(app);
        else launch(app);
    }

    private void toggleFavorite(App app) {
        List<String> favorites = prefs.favorites();
        boolean removed = favorites.remove(app.packageName);
        if (!removed) favorites.add(app.packageName);
        prefs.saveFavorites(favorites);
        Toast.makeText(this, removed ? "Removed from Home" : "Added to Home", Toast.LENGTH_SHORT).show();
        show(page, app.packageName);
    }

    private void move(App app, App neighbor) {
        List<String> favorites = prefs.favorites();
        Collections.swap(favorites, favorites.indexOf(app.packageName), favorites.indexOf(neighbor.packageName));
        prefs.saveFavorites(favorites);
        show(page, app.packageName);
    }

    private void choose(String title, String[] choices, int checked, java.util.function.IntConsumer action) {
        displayDialog(new AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(choices, checked, (d, which) -> {
                    d.dismiss();
                    action.accept(which);
                }));
    }

    private void confirm(String title, String content, Runnable action) {
        displayDialog(new AlertDialog.Builder(this).setTitle(title).setMessage(content)
                .setNegativeButton("Cancel", null).setPositiveButton("Restore", (d, which) -> action.run()));
    }

    private void displayDialog(AlertDialog.Builder builder) {
        AlertDialog next = builder.create();
        next.setOnDismissListener(d -> { if (dialog == next) dialog = null; });
        // A popup opened by Select must wait for a new Select gesture.
        long openedAt = SystemClock.uptimeMillis();
        next.setOnKeyListener((d, key, event) -> isSelectKey(key) && event.getDownTime() <= openedAt);
        dialog = next;
        next.show();
    }

    private void launch(App app) {
        if (HomeRedirectPolicy.startsAmazonSession(app.packageName, app.component.getClassName())) {
            openAmazonSession(app.launchIntent(), app.label);
        } else {
            open(app.launchIntent(), app.label);
        }
    }

    private void appInformation(App app) {
        open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName)),
                "App information");
    }

    private void openSystemSettings() {
        Intent direct = new Intent("amazon.intent.action.MAINSETTINGS")
                .setComponent(new ComponentName(HomeRedirect.AMAZON,
                        HomeRedirect.AMAZON + ".ui.MainSettingsActivity"));
        if (!tryOpen(direct)) {
            openAmazonSession(new Intent(Settings.ACTION_SETTINGS), "Fire OS Settings");
        }
    }

    private void openAmazonHome() {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setPackage(HomeRedirect.AMAZON)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        openAmazonSession(intent, "Amazon Home");
    }

    private void openAmazonSession(Intent intent, String label) {
        HomeRedirect.enterSystemSession();
        if (tryOpen(intent)) {
            Toast.makeText(this, "Reopen Streaming Home to resume Home redirection",
                    Toast.LENGTH_LONG).show();
        } else {
            HomeRedirect.leaveSystemSession();
            unavailable(label);
        }
    }

    private void open(Intent intent, String label) {
        if (!tryOpen(intent)) unavailable(label);
    }

    private boolean tryOpen(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private void unavailable(String label) {
        Toast.makeText(this, label + " is unavailable", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        switch (page) {
            case HOME: openAmazonHome(); break;
            case EDIT: show(Page.SETTINGS, "edit"); break;
            case ADD_FAVORITES: show(addFavoritesFrom, "add"); break;
            default: show(Page.HOME, homeFocus); break;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (!isSelectKey(code)) return super.dispatchKeyEvent(event);
        App focused = focusedApp();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0 && focused != null) {
                selectTarget = focused;
                selectPage = page;
                selectKey = code;
                selectWasLong = false;
            }
            if (selectTarget != null && selectKey == code) {
                selectWasLong |= event.isLongPress();
                return true;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP && selectTarget != null && selectKey == code) {
            boolean held = selectWasLong || event.getEventTime() - event.getDownTime()
                    >= ViewConfiguration.getLongPressTimeout();
            boolean sameTarget = selectPage == page && selectTarget == focused;
            clearSelect();
            if (sameTarget && !event.isCanceled()) {
                if (held) appMenu(focused, page == Page.HOME);
                else selectApp(focused, page == Page.ADD_FAVORITES);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) clearSelect();
    }

    private static boolean isSelectKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
    }

    private void clearSelect() { selectTarget = null; }

    private App focusedApp() {
        View focused = getCurrentFocus();
        if (focused == grid && grid != null) return (App) grid.getSelectedItem();
        return focused instanceof Tile ? ((Tile) focused).app : null;
    }

    private String focusedKey() {
        App app = focusedApp();
        if (app != null) return app.packageName;
        View focus = getCurrentFocus();
        return focus != null && focus.getTag() instanceof String ? (String) focus.getTag() : null;
    }

    private void reportHomeDrawn() {
        if (fullDrawReported || page != Page.HOME) return;
        fullDrawReported = true;
        reportFullyDrawn();
    }

    private int dp(float size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private TextView text(String label, float size) {
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(size);
        text.setTextColor(TEXT);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        return text;
    }

    private TextView button(String label) {
        TextView button = text(label, 15);
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setBackground(buttonBackground(PANEL));
        return button;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }

    private GradientDrawable buttonBackground(int color) {
        int[][] states = {{android.R.attr.state_focused}, {}};
        GradientDrawable shape = background(color, 10);
        shape.setStroke(dp(2), new ColorStateList(states, new int[] {TEXT, Color.TRANSPARENT}));
        return shape;
    }

    private static void measureExactly(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, width), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(Math.max(0, height), View.MeasureSpec.EXACTLY));
    }

    private static void place(View view, int x, int y) {
        view.layout(x, y, x + view.getMeasuredWidth(), y + view.getMeasuredHeight());
    }

    private final class Screen extends ViewGroup {
        final boolean home;
        final TextView title;
        final List<View> headers = new ArrayList<>();
        final Map<String, View> focusable = new HashMap<>();
        View body;
        View defaultFocus;

        Screen(String name, boolean home) {
            super(HomeActivity.this);
            this.home = home;
            setBackgroundColor(BACKGROUNDS[prefs.background()]);
            title = text(name, 25);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            addView(title);
        }

        void setBody(View view) {
            body = view;
            addView(view);
        }

        void addHeader(String key, View button, Runnable action) {
            register(key, button);
            headers.add(button);
            addView(button);
            button.setOnClickListener(v -> action.run());
        }

        void register(String key, View view) {
            view.setId(View.generateViewId());
            view.setTag(key);
            focusable.put(key, view);
        }

        void takeFocus(String key) {
            if (body instanceof GridView && key != null && apps.containsKey(key)) {
                int position = new ArrayList<>(apps.keySet()).indexOf(key);
                ((GridView) body).setSelection(position);
            }
            View target = focusable.get(key);
            if (target == null) target = defaultFocus;
            if (target == null && !headers.isEmpty()) target = headers.get(0);
            if (target != null) target.requestFocus();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int height = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width, height);
            int side = Math.round(width * 0.049f);
            measureExactly(title, width / 2, dp(41));
            for (View button : headers) measureExactly(button, dp(home ? 43 : 72), dp(41));
            if (body == null) return;
            if (body instanceof HomeRow) {
                HomeRow row = (HomeRow) body;
                row.size(width - 2 * side);
                measureExactly(row, width, row.tileHeight + dp(12));
            } else if (home) {
                measureExactly(body, dp(160), dp(42));
            } else {
                int top = Math.round(height * 0.169f);
                measureExactly(body, body instanceof GridView ? width - 2 * side : Math.round(width * 0.58f),
                        height - top - dp(20));
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int height = bottom - top;
            int side = Math.round(width * 0.049f);
            int barTop = Math.round(height * 0.061f);
            place(title, side, barTop);
            int x = width - side;
            for (int i = headers.size() - 1; i >= 0; i--) {
                View button = headers.get(i);
                x -= button.getMeasuredWidth();
                place(button, x, barTop);
                x -= dp(7);
            }
            if (body instanceof HomeRow) {
                place(body, 0, Math.round(height * 0.333f) - dp(6));
            } else if (body != null) {
                place(body, side, Math.round(height * (home ? 0.333f : 0.169f)));
            }
        }
    }

    private final class HomeRow extends HorizontalScrollView {
        final LinearLayout row = new LinearLayout(HomeActivity.this);
        final List<Tile> tiles = new ArrayList<>();
        int tileWidth;
        int tileHeight;

        HomeRow() {
            super(HomeActivity.this);
            setHorizontalScrollBarEnabled(false);
            setSmoothScrollingEnabled(true);
            setClipChildren(false);
            setClipToPadding(false);
            setFocusable(false);
            setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            row.setOrientation(LinearLayout.HORIZONTAL);
            addView(row, new FrameLayout.LayoutParams(-2, -1));
        }

        void addTile(Tile tile) {
            tiles.add(tile);
            row.addView(tile, new LinearLayout.LayoutParams(0, 0));
        }

        void size(int available) {
            int capacity = 5 - prefs.size();
            int gap = dp(12);
            int side = Math.round(available / 0.902f * 0.049f);
            tileWidth = (available - gap * (capacity - 1)) / capacity;
            tileHeight = Math.round(tileWidth * 0.5625f) + (prefs.labels() ? dp(32) : 0);
            row.setPadding(side, dp(6), side, dp(6));
            for (int i = 0; i < tiles.size(); i++) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) tiles.get(i).getLayoutParams();
                params.width = tileWidth;
                params.height = tileHeight;
                params.rightMargin = i + 1 == tiles.size() ? 0 : gap;
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            visibleArtwork();
        }

        @Override
        protected void onScrollChanged(int left, int top, int oldLeft, int oldTop) {
            super.onScrollChanged(left, top, oldLeft, oldTop);
            visibleArtwork();
        }

        private void visibleArtwork() {
            if (getWidth() == 0 || tileWidth == 0) return;
            int visibleLeft = getScrollX();
            int visibleRight = visibleLeft + getWidth();
            boolean firstFrameReady = true;
            for (Tile tile : tiles) {
                boolean visible = tile.getRight() > visibleLeft && tile.getLeft() < visibleRight;
                if (visible || tile.isFocused()) tile.loadArtwork();
                else tile.releaseArtwork();
                if (visible && !tile.artworkReady) firstFrameReady = false;
            }
            if (firstFrameReady && !fullDrawReported) postOnAnimation(() -> {
                if (homeRow == this) reportHomeDrawn();
            });
        }
    }

    private final class AppsAdapter extends BaseAdapter {
        private final boolean editing;
        private final List<App> entries;
        private final int columns;

        AppsAdapter(boolean editing, List<App> entries, int columns) {
            this.editing = editing;
            this.entries = entries;
            this.columns = columns;
        }

        @Override public int getCount() { return entries.size(); }
        @Override public App getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View recycled, ViewGroup parent) {
            Tile tile = recycled instanceof Tile ? (Tile) recycled : new Tile(false);
            App app = getItem(position);
            tile.bind(app, prefs.labels(), editing && prefs.favorites().contains(app.packageName));
            int width = ((GridView) parent).getColumnWidth();
            if (width <= 0) width = Math.max(dp(85), (parent.getWidth() - dp(12) * (columns - 1)) / columns);
            int height = Math.round(width * 0.5625f) + (prefs.labels() ? dp(26) : 0);
            ViewGroup.LayoutParams params = tile.getLayoutParams();
            if (params == null) {
                tile.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, height));
            } else if (params.height != height) {
                params.height = height;
                tile.requestLayout();
            }
            tile.highlight(grid != null && grid.hasFocus() && grid.getSelectedItemPosition() == position);
            tile.loadArtwork();
            return tile;
        }
    }

    private final class Tile extends FrameLayout {
        final ArtView artView = new ArtView();
        final TextView label = text("", 14);
        final boolean home;
        App app;
        boolean artworkReady;
        boolean requested;
        boolean highlighted;
        int artworkRequest;

        Tile(boolean home) {
            super(HomeActivity.this);
            this.home = home;
            setFocusable(home);
            setClipChildren(false);
            setClipToPadding(false);
            addView(artView, new FrameLayout.LayoutParams(-1, 0));
            label.setGravity(Gravity.CENTER);
            label.setPadding(dp(4), 0, dp(4), 0);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(label, new FrameLayout.LayoutParams(-1, 0, Gravity.BOTTOM));
            if (home) setOnFocusChangeListener((view, focused) -> {
                highlight(focused);
                if (focused) loadArtwork();
            });
        }

        void bind(App entry, boolean showLabel, boolean pinned) {
            if (app != entry) releaseArtwork();
            app = entry;
            label.setText(entry.label);
            label.setVisibility(showLabel ? VISIBLE : GONE);
            artView.pinned = pinned;
            artView.invalidate();
            setContentDescription(entry.label + (pinned ? ", pinned" : ""));
        }

        void loadArtwork() {
            if (requested || app == null) return;
            requested = true;
            int token = ++artworkRequest;
            catalog.artwork(app, art -> {
                if (token != artworkRequest) return;
                artworkReady = true;
                artView.artwork = art;
                artView.invalidate();
                if (homeRow != null && home) homeRow.visibleArtwork();
            });
        }

        void releaseArtwork() {
            if (!requested) return;
            artworkRequest++;
            requested = false;
            artworkReady = false;
            artView.artwork = null;
            artView.invalidate();
        }

        void highlight(boolean selected) {
            if (highlighted == selected) return;
            highlighted = selected;
            artView.invalidate();
            float scale = selected && prefs.animations() ? 1.025f : 1f;
            artView.animate().cancel();
            if (prefs.animations() && isLaidOut()) {
                artView.animate().scaleX(scale).scaleY(scale).setDuration(110).start();
            } else {
                artView.setScaleX(scale);
                artView.setScaleY(scale);
            }
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int height = MeasureSpec.getSize(heightSpec);
            int labelHeight = label.getVisibility() == VISIBLE ? dp(home ? 32 : 26) : 0;
            artView.getLayoutParams().height = Math.max(0, height - labelHeight);
            label.getLayoutParams().height = labelHeight;
            super.onMeasure(widthSpec, heightSpec);
        }

        private final class ArtView extends View {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            final RectF card = new RectF();
            final RectF destination = new RectF();
            final Path rounded = new Path();
            Artwork artwork;
            boolean pinned;

            ArtView() {
                super(HomeActivity.this);
                setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            }

            @Override
            protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
                card.set(dp(4), dp(4), width - dp(4), height - dp(4));
                rounded.reset();
                rounded.addRoundRect(card, dp(10), dp(10), Path.Direction.CW);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                paint.setColor(PANEL);
                canvas.drawRoundRect(card, dp(10), dp(10), paint);
                if (artwork != null && artwork.bitmap != null) {
                    drawArtwork(canvas, artwork.bitmap, artwork.banner);
                }
                if (pinned) {
                    float right = card.right - dp(4);
                    float top = card.top + dp(4);
                    paint.setColor(0xee222223);
                    canvas.drawRoundRect(right - dp(25), top, right, top + dp(25), dp(7), dp(7), paint);
                    paint.setColor(TEXT);
                    paint.setStrokeWidth(dp(2));
                    float center = right - dp(12);
                    canvas.drawLine(center - dp(5), top + dp(13), center - dp(1), top + dp(17), paint);
                    canvas.drawLine(center - dp(1), top + dp(17), center + dp(6), top + dp(9), paint);
                }
                if (highlighted) {
                    paint.setColor(TEXT);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(2));
                    canvas.drawRoundRect(dp(1), dp(1), getWidth() - dp(1), getHeight() - dp(1),
                            dp(13), dp(13), paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }

            private void drawArtwork(Canvas canvas, Bitmap bitmap, boolean banner) {
                float scale = Math.min(card.width() / bitmap.getWidth(), card.height() / bitmap.getHeight());
                if (!banner) scale *= 0.60f;
                float halfWidth = bitmap.getWidth() * scale / 2;
                float halfHeight = bitmap.getHeight() * scale / 2;
                destination.set(card.centerX() - halfWidth, card.centerY() - halfHeight,
                        card.centerX() + halfWidth, card.centerY() + halfHeight);
                canvas.save();
                canvas.clipPath(rounded);
                canvas.drawBitmap(bitmap, null, destination, paint);
                canvas.restore();
            }
        }
    }

    private final class HeaderButton extends View {
        final boolean settings;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();

        HeaderButton(boolean settings, String label) {
            super(HomeActivity.this);
            this.settings = settings;
            setContentDescription(label);
            setFocusable(true);
            setBackground(buttonBackground(0xff202020));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.translate(getWidth() / 2f, getHeight() / 2f);
            float scale = dp(1);
            canvas.scale(scale, scale);
            paint.setColor(TEXT);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.35f);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if (settings) {
                path.reset();
                for (int point = 0; point < 24; point++) {
                    double angle = point * Math.PI / 12 - Math.PI / 2 - Math.PI / 24;
                    float radius = point % 4 < 2 ? 9.4f : 7.4f;
                    float x = (float) (Math.cos(angle) * radius);
                    float y = (float) (Math.sin(angle) * radius);
                    if (point == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                path.close();
                canvas.drawPath(path, paint);
                canvas.drawCircle(0, 0, 2.9f, paint);
            } else {
                for (int column = -1; column <= 0; column++) {
                    for (int row = -1; row <= 0; row++) {
                        float x = 9 * column + 1;
                        float y = 9 * row + 1;
                        canvas.drawRoundRect(x, y, x + 6, y + 6, 1.2f, 1.2f, paint);
                    }
                }
            }
            canvas.restore();
        }
    }
}
