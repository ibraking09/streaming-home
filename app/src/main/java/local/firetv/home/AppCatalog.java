package local.firetv.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class AppCatalog {
    // Keep the OEM launcher and Settings entry points in the recovery and Settings menus.
    // Fire OS also advertises these internal panels as ordinary launcher activities.
    private static final Set<String> FIRE_OS_PANELS = new HashSet<>(Arrays.asList(
            "com.amazon.tv.launcher", "com.amazon.firehomestarter",
            "com.amazon.ftv.profilepicker", "com.amazon.ftv.screensaver", "com.amazon.ssm",
            "com.amazon.hedwig", "com.amazon.firetv.troubleshooting",
            "com.amazon.smarthomemapviewapp", "com.amazon.whasettings", "com.amazon.minitv.android.app"));
    private static final String TAG = "StreamingHomeCatalog";
    private static final int ARTWORK_LIMIT = 2 * 1024 * 1024;
    private static final int BANNER_EDGE = 384;
    private static final int ICON_EDGE = 160;

    private final PackageManager manager;
    private final String ownPackage;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task ->
            new Thread(task, "Streaming Home artwork"));
    private final LruCache<String, Artwork> cache = new LruCache<String, Artwork>(ARTWORK_LIMIT) {
        @Override
        protected int sizeOf(String key, Artwork art) {
            return art.bitmap == null ? 1 : art.bitmap.getAllocationByteCount();
        }
    };
    private final Map<String, List<Consumer<Artwork>>> requests = new HashMap<>();
    private final Map<String, Integer> artworkRevisions = new HashMap<>();
    private int revision;
    private boolean closed;

    AppCatalog(Context context) {
        ownPackage = context.getPackageName();
        manager = context.getPackageManager();
    }

    void refresh(Collection<String> packages, Consumer<List<App>> complete) {
        if (!packages.isEmpty()) {
            for (String packageName : packages) artworkRevisions.merge(packageName, 1, Integer::sum);
            for (String key : cache.snapshot().keySet()) {
                int end = key.indexOf('/');
                if (end >= 0 && packages.contains(key.substring(0, end))) cache.remove(key);
            }
        }
        int requestedRevision = ++revision;
        worker.execute(() -> {
            Map<String, App> found = new LinkedHashMap<>();
            discover(found, Intent.CATEGORY_LEANBACK_LAUNCHER);
            discover(found, Intent.CATEGORY_LAUNCHER);
            Collator order = Collator.getInstance();
            order.setStrength(Collator.PRIMARY);
            List<App> sorted = new ArrayList<>(found.values());
            sorted.sort((first, second) -> {
                int comparison = order.compare(first.label, second.label);
                return comparison == 0 ? first.packageName.compareTo(second.packageName) : comparison;
            });
            main.post(() -> {
                if (closed || requestedRevision != revision) return;
                Log.i(TAG, "Discovered " + sorted.size() + " launchable apps");
                complete.accept(sorted);
            });
        });
    }

    private void discover(Map<String, App> found, String category) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo result : manager.queryIntentActivities(query, 0)) {
            ActivityInfo info = result.activityInfo;
            if (info == null || info.applicationInfo == null || !info.exported || !info.enabled
                    || !info.applicationInfo.enabled || ownPackage.equals(info.packageName)
                    || FIRE_OS_PANELS.contains(info.packageName) || found.containsKey(info.packageName)
                    || (info.permission != null
                        && manager.checkPermission(info.permission, ownPackage) != PackageManager.PERMISSION_GRANTED)) {
                continue;
            }
            CharSequence label = result.loadLabel(manager);
            String name = visibleLabel(label);
            if (name.isEmpty()) name = visibleLabel(info.applicationInfo.loadLabel(manager));
            if (!name.isEmpty()) found.put(info.packageName, new App(info, name, category));
        }
    }

    private static String visibleLabel(CharSequence label) {
        if (label == null) return "";
        String original = label.toString().trim();
        return original.codePoints().anyMatch(codePoint ->
                !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)
                        && !Character.isISOControl(codePoint)
                        && Character.getType(codePoint) != Character.FORMAT) ? original : "";
    }

    void artwork(App app, Consumer<Artwork> complete) {
        String key = artworkKey(app);
        Artwork cached = cache.get(key);
        if (cached != null) {
            complete.accept(cached);
            return;
        }
        List<Consumer<Artwork>> waiting = requests.get(key);
        if (waiting != null) {
            waiting.add(complete);
            return;
        }
        requests.put(key, new ArrayList<>(Collections.singletonList(complete)));
        worker.execute(() -> {
            Artwork result = loadArtwork(app);
            main.post(() -> {
                List<Consumer<Artwork>> listeners = requests.remove(key);
                if (closed || listeners == null) return;
                if (key.equals(artworkKey(app))) cache.put(key, result);
                for (Consumer<Artwork> listener : listeners) listener.accept(result);
            });
        });
    }

    private String artworkKey(App app) {
        return app.component.flattenToShortString() + "@" + artworkRevisions.getOrDefault(app.packageName, 0);
    }

    private Artwork loadArtwork(App app) {
        try {
            Drawable banner = app.info.loadBanner(manager);
            if (banner != null) {
                Bitmap bitmap = rasterize(banner, BANNER_EDGE, 16, 9);
                Log.i(TAG, app.packageName + " banner " + bitmap.getWidth() + "x" + bitmap.getHeight());
                return new Artwork(bitmap, true);
            }
        } catch (RuntimeException e) {
            Log.i(TAG, "Could not load banner for " + app.packageName);
        }
        try {
            Drawable icon = app.info.loadIcon(manager);
            Bitmap bitmap = icon == null ? null : rasterize(icon, ICON_EDGE, 1, 1);
            String source = icon instanceof AdaptiveIconDrawable ? " adaptive icon" : " icon";
            Log.i(TAG, app.packageName + (bitmap == null ? " no artwork" : source));
            return new Artwork(bitmap, false);
        } catch (RuntimeException e) {
            Log.i(TAG, "Could not load icon for " + app.packageName);
            return new Artwork(null, false);
        }
    }

    private static Bitmap rasterize(Drawable drawable, int edge, int fallbackWidth, int fallbackHeight) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            width = fallbackWidth;
            height = fallbackHeight;
        }
        float scale = Math.min(1f, (float) edge / Math.max(width, height));
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    void close() {
        closed = true;
        requests.clear();
        cache.evictAll();
        worker.shutdownNow();
    }

    static final class Artwork {
        final Bitmap bitmap;
        final boolean banner;

        Artwork(Bitmap bitmap, boolean banner) {
            this.bitmap = bitmap;
            this.banner = banner;
        }
    }

    static final class App {
        final String packageName;
        final String label;
        final ComponentName component;
        private final String category;
        private final ActivityInfo info;

        App(ActivityInfo info, String label, String category) {
            this.info = info;
            this.label = label;
            this.category = category;
            packageName = info.packageName;
            component = new ComponentName(info.packageName, info.name);
        }

        Intent launchIntent() {
            return new Intent(Intent.ACTION_MAIN).addCategory(category).setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
    }
}
