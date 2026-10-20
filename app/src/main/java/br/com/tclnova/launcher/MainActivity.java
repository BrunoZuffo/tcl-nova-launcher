package br.com.tclnova.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * TCL Nova v0.1 – launcher próprio para Android TV 9+.
 * Não exibe publicidade, não exige root e não coleta dados.
 */
public final class MainActivity extends Activity {
    private static final int WHITE = Color.rgb(249, 248, 251);
    private static final int MUTED = Color.rgb(200, 193, 204);
    private static final int PANEL = Color.argb(188, 25, 22, 32);
    private static final String PREFS = "tcl_nova_settings";
    private static final String FAVS = "favorite_packages";
    private static final String FAVS_INITIALIZED = "favorites_initialized";
    private static final Locale PT_BR = new Locale("pt", "BR");

    private final Map<String, App> appMap = new HashMap<>();
    private LinearLayout content;
    private SharedPreferences preferences;
    private List<App> cachedApps = Collections.emptyList();

    private static final class App {
        final String pkg;
        final String cls;
        final String label;
        final Drawable icon;
        final boolean tvApp;

        App(String pkg, String cls, String label, Drawable icon, boolean tvApp) {
            this.pkg = pkg;
            this.cls = cls;
            this.label = label;
            this.icon = icon;
            this.tvApp = tvApp;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        cachedApps = loadApps();
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Mantem a tela existente ao voltar dos aplicativos: menos trabalho e piscadas.
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, float radius, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(selected ? 3 : 1), selected ? 0xFFFFE1D5 : 0x66FFFFFF);
        return drawable;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        textView.setGravity(Gravity.CENTER_VERTICAL);
        return textView;
    }

    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout horizontal() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        return h;
    }

    private void gap(int height) {
        View v = new View(this);
        content.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void render() {
        List<App> apps = cachedApps;
        FrameLayout frame = new FrameLayout(this);
        frame.addView(new Backdrop(this, preferences.getInt("theme",0)), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout scrollRoot = vertical();
        scrollRoot.setPadding(dp(48), dp(30), dp(40), dp(24));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = scrollRoot;
        scroll.addView(scrollRoot);
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);

        LinearLayout header = horizontal();
        header.setGravity(Gravity.TOP);
        LinearLayout heading = vertical();
        heading.addView(label(greeting(), 12, MUTED, true));
        String time = new SimpleDateFormat("HH:mm", PT_BR).format(Calendar.getInstance().getTime());
        heading.addView(label(time, 42, WHITE, true));
        String date = new SimpleDateFormat("EEEE, d 'de' MMMM", PT_BR)
            .format(Calendar.getInstance().getTime());
        heading.addView(label(date, 14, MUTED, false));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(customizeChip());
        header.addView(chip("Configurações", Settings.ACTION_SETTINGS));
        content.addView(header);

        gap(36);
        content.addView(label("Boa sessão.", 30, WHITE, true));
        content.addView(label("Sua TV, seus aplicativos. Sem publicidade na tela inicial.",
            13, MUTED, false));
        gap(30);

        Set<String> savedFavorites = preferences.getStringSet(FAVS, Collections.emptySet());
        List<App> favorites = new ArrayList<>();
        for (App app : apps) {
            if (savedFavorites.contains(app.pkg) ||
                (!preferences.getBoolean(FAVS_INITIALIZED, false) && defaultFavorite(app))) {
                favorites.add(app);
            }
        }
        if (!favorites.isEmpty()) {
            addRow("Favoritos", favorites);
            gap(24);
        }

        gap(15);
        TextView showAll = label(preferences.getBoolean("expanded",false)
            ? "OCULTAR OUTROS APPS  ▲" : "VER TODOS OS APPS  ▼", 15, WHITE, true);
        showAll.setFocusable(true);
        showAll.setPadding(dp(18),dp(14),dp(18),dp(14));
        showAll.setBackground(rounded(PANEL, 16, false));
        showAll.setOnClickListener(v -> {
            preferences.edit().putBoolean("expanded",
                !preferences.getBoolean("expanded",false)).apply();
            render();
        });
        content.addView(showAll);
        if(preferences.getBoolean("expanded",false)) {
            List<App> remaining = new ArrayList<>();
            Set<String> displayed = new HashSet<>();
            for (App fav: favorites) displayed.add(fav.pkg);
            for (App app: apps) if (!displayed.contains(app.pkg)) remaining.add(app);
            gap(20);
            addRow("Outros aplicativos", remaining);
        }
        gap(30);
        content.addView(label(
            "Dica: segure OK sobre um aplicativo para adicionar ou remover dos favoritos.",
            12, MUTED, false));
        gap(8);
        content.addView(label("TCL Nova 0.2  •  Personalizado  •  Sem rastreamento",
            11, 0xFFAB9FAA, false));
    }

    private TextView customizeChip() {
        TextView chip = label("Personalizar", 13, WHITE, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(16), 0, dp(16), 0);
        chip.setFocusable(true);
        chip.setBackground(rounded(PANEL, 22, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(40));
        params.setMargins(dp(10), 0, 0, 0);
        chip.setLayoutParams(params);
        chip.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("TCL Nova - personalizar")
            .setItems(new String[]{"Vinho", "Grafite", "Azul noturno", "Atualizar aplicativos"},
                (d, selected) -> {
                    if(selected == 3) cachedApps = loadApps();
                    else preferences.edit().putInt("theme", selected).apply();
                    render();
                }).setNegativeButton("Cancelar",null).show());
        return chip;
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour < 12 ? "BOM DIA" : hour < 18 ? "BOA TARDE" : "BOA NOITE";
    }

    private TextView chip(String title, String action) {
        TextView chip = label(title, 13, WHITE, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(18), 0, dp(18), 0);
        chip.setFocusable(true);
        chip.setBackground(rounded(PANEL, 22, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(40));
        params.setMargins(dp(10), 0, 0, 0);
        chip.setLayoutParams(params);
        chip.setOnFocusChangeListener((v, focused) -> {
            chip.setBackground(rounded(focused ? 0xFF933A48 : PANEL, 22, focused));
            chip.animate().scaleX(focused ? 1.05f : 1f)
                .scaleY(focused ? 1.05f : 1f).setDuration(130).start();
        });
        chip.setOnClickListener(v -> {
            try {
                startActivity(new Intent(action));
            } catch (ActivityNotFoundException | SecurityException ex) {
                Toast.makeText(this, "Configuração indisponível neste modelo de TV", Toast.LENGTH_LONG).show();
            }
        });
        return chip;
    }

    private void addRow(String title, List<App> apps) {
        content.addView(label(title, 18, WHITE, true));
        if (apps.isEmpty()) {
            content.addView(label("Nenhum aplicativo encontrado.", 14, MUTED, false));
            return;
        }
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setClipChildren(false);
        scroller.setPadding(dp(4), dp(14), dp(4), dp(14));
        LinearLayout cards = horizontal();
        cards.setClipToPadding(false);
        cards.setClipChildren(false);
        for (App app : apps) cards.addView(createCard(app));
        scroller.addView(cards);
        content.addView(scroller);
    }

    private View createCard(App app) {
        LinearLayout card = vertical();
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(rounded(PANEL, 17, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(177), dp(112));
        params.setMargins(0, 0, dp(14), 0);
        card.setLayoutParams(params);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(dp(55), dp(55)));
        TextView name = label(app.label, 14, WHITE, true);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-1, dp(24));
        textParams.setMargins(dp(8), dp(4), dp(8), 0);
        card.addView(name, textParams);

        card.setContentDescription("Abrir " + app.label);
        card.setOnFocusChangeListener((view, focus) -> {
            card.setBackground(rounded(focus ? 0xFF883447 : PANEL, 17, focus));
            card.animate().scaleX(focus ? 1.06f : 1f)
                .scaleY(focus ? 1.06f : 1f).setDuration(130).start();
        });
        card.setOnClickListener(v -> launchApp(app));
        card.setOnLongClickListener(v -> {
            toggleFavorite(app.pkg);
            Toast.makeText(this, "Favoritos atualizados: " + app.label, Toast.LENGTH_SHORT).show();
            render();
            return true;
        });
        return card;
    }

    private void launchApp(App app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(app.tvApp ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
        intent.setClassName(app.pkg, app.cls);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Intent alternate = getPackageManager().getLaunchIntentForPackage(app.pkg);
            try {
                if (alternate != null) startActivity(alternate);
                else throw new ActivityNotFoundException();
            } catch (Exception ignored) {
                Toast.makeText(this, "Não foi possível abrir " + app.label, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void toggleFavorite(String pkg) {
        Set<String> favorites = new HashSet<>(preferences.getStringSet(FAVS, Collections.emptySet()));
        // Na primeira edição, salva favoritos padrão para não perder os demais.
        if (!preferences.getBoolean(FAVS_INITIALIZED, false)) {
            for (App app : appMap.values()) if (defaultFavorite(app)) favorites.add(app.pkg);
        }
        if (!favorites.add(pkg)) favorites.remove(pkg);
        preferences.edit().putStringSet(FAVS, favorites).putBoolean(FAVS_INITIALIZED, true).apply();
    }

    private boolean defaultFavorite(App app) {
        String title = app.label.toLowerCase(PT_BR);
        return title.contains("netflix") || title.contains("youtube") ||
            title.contains("prime video") || title.contains("globoplay");
    }

    private List<App> loadApps() {
        appMap.clear();
        PackageManager pm = getPackageManager();
        collectApps(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true);
        collectApps(pm, Intent.CATEGORY_LAUNCHER, false);
        List<App> list = new ArrayList<>(appMap.values());
        list.sort(Comparator.comparingInt(this::priority)
            .thenComparing(app -> app.label.toLowerCase(PT_BR)));
        return list;
    }

    private void collectApps(PackageManager pm, String category, boolean forTv) {
        Intent search = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> found = pm.queryIntentActivities(search, 0);
        for (ResolveInfo result : found) {
            if (result.activityInfo == null) continue;
            String pkg = result.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || appMap.containsKey(pkg)) continue;
            String label = result.loadLabel(pm).toString();
            Drawable icon = result.loadIcon(pm);
            appMap.put(pkg, new App(pkg, result.activityInfo.name, label, icon, forTv));
        }
    }

    private int priority(App app) {
        String name = app.label.toLowerCase(PT_BR);
        if (name.contains("netflix")) return 0;
        if (name.contains("youtube")) return 1;
        if (name.contains("prime video")) return 2;
        if (name.contains("globoplay")) return 3;
        if (name.contains("disney")) return 4;
        if (name.contains("max")) return 5;
        if (name.contains("spotify")) return 6;
        return 50;
    }

    /** Fundo gerado no dispositivo: sem download, imagens externas ou anúncios. */
    private static final class Backdrop extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final int theme;
        Backdrop(Activity context, int theme) {
            super(context);
            this.theme = theme;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            int[] colors = theme == 1
                ? new int[]{0xFF292C34, 0xFF1B1E27, 0xFF090B10}
                : theme == 2
                ? new int[]{0xFF18406D, 0xFF17233B, 0xFF0C1020}
                : new int[]{0xFF782A3C, 0xFF271D30, 0xFF0E101C};
            paint.setShader(new LinearGradient(0, 0, w, h,
                colors,
                null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(new RadialGradient(w * 0.48f, h * 0.12f, w * 0.62f,
                new int[]{0x99D42839, 0x44B12748, 0x00B12748},
                null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }
    }
}
