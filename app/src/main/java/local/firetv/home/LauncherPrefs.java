package local.firetv.home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class LauncherPrefs {
    private static final String FAVORITES = "favorites";
    private static final String BACKGROUND = "background";
    private static final String LABELS = "labels";
    private static final String SIZE = "size";
    private static final String ANIMATIONS = "animations";
    private static final List<String> DEFAULT_FAVORITES = Arrays.asList(
            "com.netflix.ninja", "com.amazon.firetv.youtube",
            "com.crunchyroll.crunchyroid", "com.amazon.firebat");

    private final SharedPreferences store;

    LauncherPrefs(Context context) {
        store = context.getSharedPreferences("launcher_preferences", Context.MODE_PRIVATE);
    }

    List<String> favorites() {
        String saved = store.getString(FAVORITES, null);
        if (saved == null) return new ArrayList<>(DEFAULT_FAVORITES);
        if (saved.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(saved.split("\n")));
    }

    void saveFavorites(List<String> favorites) {
        store.edit().putString(FAVORITES, String.join("\n", favorites)).apply();
    }

    void resetFavorites() { store.edit().remove(FAVORITES).apply(); }
    int background() { return store.getInt(BACKGROUND, 0); }
    void background(int value) { store.edit().putInt(BACKGROUND, value).apply(); }
    boolean labels() { return store.getBoolean(LABELS, true); }
    void toggleLabels() { store.edit().putBoolean(LABELS, !labels()).apply(); }
    int size() { return store.getInt(SIZE, 1); }
    void size(int value) { store.edit().putInt(SIZE, value).apply(); }
    boolean animations() { return store.getBoolean(ANIMATIONS, true); }
    void toggleAnimations() { store.edit().putBoolean(ANIMATIONS, !animations()).apply(); }
    void reset() { store.edit().clear().apply(); }
}
