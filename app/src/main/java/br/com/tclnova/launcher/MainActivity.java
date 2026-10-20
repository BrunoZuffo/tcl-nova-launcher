package br.com.tclnova.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

/** TCL Nova 0.3 – Android TV 9+, sem root, rede ou serviços de fundo. */
public final class MainActivity extends Activity {
    private static final int WHITE = 0xFFF5F7FF;
    private static final int MUTED = 0xFFC0C9DD;
    private static final int SECONDARY = 0xFF9FAFCB;
    private static final int PANEL = 0xD9142031;
    private static final int PANEL_FOCUS = 0xFF324B6D;
    private static final String PREFS = "tcl_nova_settings";
    private static final String FAVS = "favorite_packages";
    private static final String FAVS_INITIALIZED = "favorites_initialized";
    private static final String EXPANDED = "expanded";
    private static final String SYSTEM_EXPANDED = "system_expanded";
    private static final String THEME = "theme";
    private static final Locale PT_BR = new Locale("pt", "BR");

    private final Map<String, TvApp> appsByPackage = new HashMap<>();
    private List<TvApp> cachedApps = Collections.emptyList();
    private SharedPreferences preferences;
    private ScrollView verticalScroll;
    private TextView clockView;
    private TextView dateView;
    private TextView appsToggle;
    private TextView systemToggle;
    private TextView customizeButton;
    private TextView settingsButton;
    private View firstFavorite;
    private final Map<String, View> focusableCards = new HashMap<>();
    private int focusId = 100;

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockUpdater = new Runnable() {
        @Override public void run() {
            updateClock();
            clockHandler.postDelayed(this, 60000);
        }
    };

    private static final class TvApp {
        final String pkg;
        final String activity;
        final String label;
        final Drawable icon;
        final boolean tv;
        final boolean internal;

        TvApp(String pkg, String activity, String label, Drawable icon,
              boolean tv, boolean internal) {
            this.pkg = pkg;
            this.activity = activity;
            this.label = label;
            this.icon = icon;
            this.tv = tv;
            this.internal = internal;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        cachedApps = loadApps();
        render(null);
    }

    @Override protected void onResume() {
        super.onResume();
        clockHandler.removeCallbacks(clockUpdater);
        clockUpdater.run();
    }

    @Override protected void onPause() {
        clockHandler.removeCallbacks(clockUpdater);
        super.onPause();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private TextView text(String label, int sp, int color, boolean bold) {
        TextView result = new TextView(this);
        result.setText(label);
        result.setTextSize(sp);
        result.setTextColor(color);
        result.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        result.setGravity(Gravity.CENTER_VERTICAL);
        result.setIncludeFontPadding(false);
        return result;
    }

    private LinearLayout vertical() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout horizontal() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private void addSpace(LinearLayout root, int heightDp) {
        root.addView(new View(this), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private GradientDrawable shape(int color, int radiusDp, boolean focused) {
        GradientDrawable out = new GradientDrawable();
        out.setColor(focused ? PANEL_FOCUS : color);
        out.setCornerRadius(dp(radiusDp));
        // Foco óbvio: borda branca forte, inclusive nos botões pequenos.
        out.setStroke(dp(focused ? 4 : 1),
            focused ? Color.WHITE : 0x55879AB8);
        return out;
    }

    /** Todos os controles recebem exatamente a mesma indicação de foco. */
    private void decorateFocusable(View view, int radiusDp, int normalColor) {
        view.setId(++focusId);
        view.setFocusable(true);
        view.setClickable(true);
        view.setBackground(shape(normalColor, radiusDp, false));
        view.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(shape(normalColor, radiusDp, focused));
            v.setElevation(focused ? dp(9) : 0);
            if (v instanceof TextView) {
                ((TextView) v).setTextColor(focused ? Color.WHITE : WHITE);
            }
            if (focused) keepFocusVisible(v);
        });
    }

    /** Mantém o elemento ativo dentro de margens seguras da tela e da fileira. */
    private void keepFocusVisible(View focused) {
        if (verticalScroll == null || focused == null) return;
        verticalScroll.post(() -> {
            if (!focused.isAttachedToWindow() || !focused.hasFocus()) return;
            int[] p = new int[2];
            int[] container = new int[2];
            focused.getLocationOnScreen(p);
            verticalScroll.getLocationOnScreen(container);
            int top = p[1] - container[1];
            int bottom = top + focused.getHeight();
            int margin = Math.min(dp(78), verticalScroll.getHeight() / 5);
            int delta = 0;
            if (top < margin) delta = top - margin;
            else if (bottom > verticalScroll.getHeight() - margin) {
                delta = bottom - (verticalScroll.getHeight() - margin);
            }
            if (delta != 0) verticalScroll.smoothScrollBy(0, delta);

            ViewParent parent = focused.getParent();
            while (parent instanceof View) {
                if (parent instanceof HorizontalScrollView) {
                    HorizontalScrollView row = (HorizontalScrollView) parent;
                    int[] rowP = new int[2];
                    row.getLocationOnScreen(rowP);
                    int midpoint = p[0] - rowP[0] + focused.getWidth() / 2;
                    int change = midpoint - row.getWidth() / 2;
                    if (Math.abs(change) > dp(35)) row.smoothScrollBy(change, 0);
                    break;
                }
                if (parent == verticalScroll) break;
                parent = parent.getParent();
            }
        });
    }

    private TextView button(String title, Runnable action, boolean compact) {
        TextView view = text(title, compact ? 12 : 14, WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(compact ? 15 : 22), 0, dp(compact ? 15 : 22), 0);
        decorateFocusable(view, compact ? 14 : 17, PANEL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(compact ? 43 : 51));
        params.setMargins(dp(10), 0, 0, 0);
        view.setLayoutParams(params);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void render(String requestedFocus) {
        focusId = 100;
        firstFavorite = null;
        focusableCards.clear();
        FrameLayout window = new FrameLayout(this);
        int theme = preferences.getInt(THEME, 0);
        window.addView(new ScenicWallpaper(this, theme),
            new FrameLayout.LayoutParams(-1, -1));

        verticalScroll = new ScrollView(this);
        verticalScroll.setFocusable(false);
        verticalScroll.setFillViewport(true);
        verticalScroll.setVerticalScrollBarEnabled(false);
        verticalScroll.setClipChildren(false);
        verticalScroll.setClipToPadding(false);
        LinearLayout root = vertical();
        root.setPadding(dp(46), dp(26), dp(46), dp(26));
        root.setClipChildren(false);
        root.setClipToPadding(false);
        verticalScroll.addView(root);
        window.addView(verticalScroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(window);

        LinearLayout header = horizontal();
        LinearLayout branding = vertical();
        branding.addView(text("N O V A", 24, WHITE, true));
        branding.addView(text("TCL  /  HOME EDITION", 10, SECONDARY, true));
        header.addView(branding, new LinearLayout.LayoutParams(0, dp(56), 1));

        LinearLayout clock = vertical();
        clock.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        clockView = text("", 25, WHITE, true);
        clockView.setGravity(Gravity.RIGHT);
        dateView = text("", 10, MUTED, false);
        dateView.setGravity(Gravity.RIGHT);
        clock.addView(clockView);
        clock.addView(dateView);
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(dp(155), dp(56));
        clockParams.rightMargin = dp(20);
        header.addView(clock, clockParams);
        customizeButton = button("Personalizar", this::showCustomization, true);
        settingsButton = button("Configurações", this::openSystemSettings, true);
        header.addView(customizeButton);
        header.addView(settingsButton);
        customizeButton.setNextFocusRightId(settingsButton.getId());
        settingsButton.setNextFocusLeftId(customizeButton.getId());
        root.addView(header);
        updateClock();

        addSpace(root, 37);
        TextView eyebrow = text("SUA EXPERIÊNCIA, SEM DISTRAÇÕES", 12, 0xFFABC8E3, true);
        root.addView(eyebrow);
        addSpace(root, 11);
        TextView hero = text("Sua TV. Do seu jeito.", 37, WHITE, true);
        root.addView(hero);
        addSpace(root, 11);
        root.addView(text("Escolha o que assistir e aproveite. Sem anúncios na sua tela inicial.",
            14, MUTED, false));
        addSpace(root, 36);

        Set<String> saved = preferences.getStringSet(FAVS, Collections.emptySet());
        boolean initialized = preferences.getBoolean(FAVS_INITIALIZED, false);
        List<TvApp> favorites = new ArrayList<>();
        List<TvApp> ordinary = new ArrayList<>();
        List<TvApp> system = new ArrayList<>();
        for (TvApp app : cachedApps) {
            if (saved.contains(app.pkg) || (!initialized && isDefaultFavorite(app))) {
                favorites.add(app);
            } else if (app.internal) {
                system.add(app);
            } else {
                ordinary.add(app);
            }
        }
        root.addView(text("SEUS FAVORITOS", 15, WHITE, true));
        addSpace(root, 10);
        if (!favorites.isEmpty()) {
            addAppRow(root, favorites);
        } else {
            root.addView(text("Nenhum favorito ainda. Abra seus aplicativos para escolher.",
                13, MUTED, false));
        }

        addSpace(root, 26);
        LinearLayout libraryHeader = horizontal();
        libraryHeader.addView(text("APLICATIVOS", 15, WHITE, true),
            new LinearLayout.LayoutParams(0, dp(52), 1));
        boolean expanded = preferences.getBoolean(EXPANDED, false);
        appsToggle = button(expanded ? "Fechar aplicativos  ▲" : "Mostrar aplicativos  ▼", () -> {
            preferences.edit().putBoolean(EXPANDED, !expanded).apply();
            render("apps");
        }, false);
        libraryHeader.addView(appsToggle);
        root.addView(libraryHeader);
        if (expanded) {
            addSpace(root, 13);
            if (ordinary.isEmpty()) {
                root.addView(text("Todos os seus apps já estão nos favoritos.",
                    13, MUTED, false));
            } else {
                addAppRow(root, ordinary);
            }
        }

        addSpace(root, 26);
        LinearLayout systemHeader = horizontal();
        systemHeader.addView(text("OPÇÕES AVANÇADAS", 11, SECONDARY, true),
            new LinearLayout.LayoutParams(0, dp(43), 1));
        boolean showSystem = preferences.getBoolean(SYSTEM_EXPANDED, false);
        systemToggle = button(showSystem ? "Ocultar utilitários  ▲"
            : "Utilitários e sistema  ▼", () -> {
            preferences.edit().putBoolean(SYSTEM_EXPANDED, !showSystem).apply();
            render("system");
        }, true);
        systemHeader.addView(systemToggle);
        root.addView(systemHeader);
        if (showSystem) {
            addSpace(root, 8);
            root.addView(text("Apps internos da TCL e do Android. Exibição apenas; nenhum foi desativado.",
                12, MUTED, false));
            addSpace(root, 12);
            addAppRow(root, system);
        }
        addSpace(root, 28);
        root.addView(text("Segure OK em um aplicativo para adicionar ou remover dos favoritos.",
            12, MUTED, false));
        addSpace(root, 9);
        root.addView(text("TCL Nova 0.3  •  Interface leve  •  Sem coleta de dados",
            11, SECONDARY, false));

        // Não perder o foco ao expandir/recolher uma seção.
        View restore = "apps".equals(requestedFocus) ? appsToggle
            : "system".equals(requestedFocus) ? systemToggle
            : "customize".equals(requestedFocus) ? customizeButton
            : requestedFocus != null && requestedFocus.startsWith("app:")
                ? focusableCards.get(requestedFocus.substring(4))
                : firstFavorite != null ? firstFavorite : appsToggle;
        if (restore == null) restore = appsToggle;
        final View restoreTarget = restore;
        root.post(() -> {
            if (restoreTarget.isAttachedToWindow()) {
                restoreTarget.requestFocus();
                keepFocusVisible(restoreTarget);
            }
        });
    }

    private void addAppRow(LinearLayout root, List<TvApp> apps) {
        if (apps.isEmpty()) {
            root.addView(text("Nenhum aplicativo nesta categoria.", 13, MUTED, false));
            return;
        }
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setFocusable(false);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(dp(5), dp(14), dp(5), dp(14));
        LinearLayout row = horizontal();
        row.setClipChildren(false);
        row.setClipToPadding(false);
        for (TvApp app : apps) {
            View card = appCard(app);
            if (firstFavorite == null) firstFavorite = card;
            row.addView(card);
        }
        scroller.addView(row);
        root.addView(scroller);
    }

    private View appCard(TvApp app) {
        LinearLayout card = vertical();
        card.setGravity(Gravity.CENTER);
        decorateFocusable(card, 19, PANEL);
        focusableCards.put(app.pkg, card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(175), dp(123));
        params.setMargins(0, 0, dp(15), 0);
        card.setLayoutParams(params);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(dp(60), dp(60)));
        TextView label = text(app.label, 14, WHITE, true);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, dp(24));
        labelParams.setMargins(dp(8), dp(10), dp(8), 0);
        card.addView(label, labelParams);
        card.setContentDescription("Abrir " + app.label + ". Segure OK para editar favoritos.");
        card.setOnClickListener(v -> launchApp(app));
        card.setOnLongClickListener(v -> {
            toggleFavorite(app.pkg);
            Toast.makeText(this, "Favoritos atualizados: " + app.label,
                Toast.LENGTH_SHORT).show();
            render("app:" + app.pkg);
            return true;
        });
        return card;
    }

    private void updateClock() {
        if (clockView == null || dateView == null) return;
        Calendar now = Calendar.getInstance();
        clockView.setText(new SimpleDateFormat("HH:mm", PT_BR).format(now.getTime()));
        dateView.setText(new SimpleDateFormat("EEE, d MMM", PT_BR).format(now.getTime()));
    }

    private void showCustomization() {
        final String[] options = {"Papel de parede e cores", "Atualizar aplicativos"};
        new AlertDialog.Builder(this)
            .setTitle("Personalizar TCL Nova")
            .setItems(options, (dialog, which) -> {
                if (which == 0) showThemes();
                else {
                    cachedApps = loadApps();
                    render("customize");
                    Toast.makeText(this, "Aplicativos atualizados", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Fechar", null)
            .show();
    }

    private void showThemes() {
        String[] names = {"Montanhas ao entardecer", "Noite de grafite", "Aurora rubi"};
        new AlertDialog.Builder(this)
            .setTitle("Escolher cenário")
            .setSingleChoiceItems(names, preferences.getInt(THEME, 0), (dialog, item) -> {
                preferences.edit().putInt(THEME, item).apply();
                dialog.dismiss();
                render("customize");
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void openSystemSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Configurações indisponíveis neste modelo",
                Toast.LENGTH_LONG).show();
        }
    }

    private void launchApp(TvApp app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(app.tv ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
        intent.setClassName(app.pkg, app.activity);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception error) {
            try {
                Intent fallback = getPackageManager().getLaunchIntentForPackage(app.pkg);
                if (fallback == null) throw new ActivityNotFoundException();
                startActivity(fallback);
            } catch (Exception ignored) {
                Toast.makeText(this, "Não foi possível abrir " + app.label,
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    private void toggleFavorite(String pkg) {
        Set<String> fav = new HashSet<>(preferences.getStringSet(FAVS, Collections.emptySet()));
        if (!preferences.getBoolean(FAVS_INITIALIZED, false)) {
            for (TvApp app : cachedApps) if (isDefaultFavorite(app)) fav.add(app.pkg);
        }
        if (!fav.add(pkg)) fav.remove(pkg);
        preferences.edit().putStringSet(FAVS, fav)
            .putBoolean(FAVS_INITIALIZED, true).apply();
    }

    private boolean isDefaultFavorite(TvApp app) {
        String lower = app.label.toLowerCase(PT_BR);
        return lower.contains("netflix") || lower.contains("youtube") ||
            lower.contains("prime video") || lower.contains("globoplay");
    }

    /**
     * FLAG_SYSTEM é só um indício; apps de entretenimento pré-instalados
     * continuam na biblioteca comum. Nenhum pacote é desativado.
     */
    private boolean isInternal(String packageName, String label, ApplicationInfo info) {
        boolean builtIn = (info.flags & (ApplicationInfo.FLAG_SYSTEM |
            ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        if (!builtIn) return false;
        String name = label.toLowerCase(PT_BR);
        String[] common = {"netflix", "youtube", "prime video", "amazon", "globoplay",
            "disney", "spotify", "hbo", "max", "plex", "crunchyroll", "twitch",
            "play store", "google play", "paramount", "apple tv",
            "google tv", "telecine", "deezer", "pluto tv"};
        if (packageName.equals("com.android.vending")) return false;
        for (String token : common) if (name.contains(token)) return false;
        return true;
    }

    private List<TvApp> loadApps() {
        appsByPackage.clear();
        PackageManager pm = getPackageManager();
        collectApps(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true);
        collectApps(pm, Intent.CATEGORY_LAUNCHER, false);
        List<TvApp> apps = new ArrayList<>(appsByPackage.values());
        apps.sort(Comparator.comparingInt(this::priority)
            .thenComparing(a -> a.label.toLowerCase(PT_BR)));
        return apps;
    }

    private void collectApps(PackageManager pm, String category, boolean forTv) {
        Intent search = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> found = pm.queryIntentActivities(search, 0);
        for (ResolveInfo resolved : found) {
            if (resolved.activityInfo == null) continue;
            String pkg = resolved.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || appsByPackage.containsKey(pkg)) continue;
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                String label = resolved.loadLabel(pm).toString();
                Drawable icon = resolved.loadIcon(pm);
                appsByPackage.put(pkg, new TvApp(pkg, resolved.activityInfo.name, label,
                    icon, forTv, isInternal(pkg, label, info)));
            } catch (PackageManager.NameNotFoundException ignored) {
                // Pacote removido entre a consulta e a leitura.
            }
        }
    }

    private int priority(TvApp app) {
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

    /** Cena fixa em Canvas, sem vídeo, internet, bitmap ou animação de fundo. */
    private static final class ScenicWallpaper extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int theme;
        private Shader sky;
        private Shader horizon;
        private final Path farMountains = new Path();
        private final Path nearMountains = new Path();

        ScenicWallpaper(Activity context, int theme) {
            super(context);
            this.theme = theme;
        }

        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            if (w <= 0 || h <= 0) return;
            int[] skyColors;
            int bloom;
            if (theme == 1) {
                skyColors = new int[]{0xFF1A2435, 0xFF151C2A, 0xFF0A111B};
                bloom = 0x558FA5BA;
            } else if (theme == 2) {
                skyColors = new int[]{0xFF382339, 0xFF372338, 0xFF0C1323};
                bloom = 0x88D27181;
            } else {
                skyColors = new int[]{0xFF162A49, 0xFF2D365B, 0xFF131A2B};
                bloom = 0x88A5B2E9;
            }
            sky = new LinearGradient(0, 0, w, h, skyColors, null, Shader.TileMode.CLAMP);
            horizon = new RadialGradient(w * .72f, h * .39f, w * .52f,
                new int[]{bloom, 0x006E84B0}, null, Shader.TileMode.CLAMP);
            farMountains.reset();
            farMountains.moveTo(0, h);
            farMountains.lineTo(0, h * .81f);
            farMountains.lineTo(w * .15f, h * .65f);
            farMountains.lineTo(w * .27f, h * .74f);
            farMountains.lineTo(w * .48f, h * .54f);
            farMountains.lineTo(w * .64f, h * .71f);
            farMountains.lineTo(w * .83f, h * .58f);
            farMountains.lineTo(w, h * .75f);
            farMountains.lineTo(w, h);
            farMountains.close();
            nearMountains.reset();
            nearMountains.moveTo(0, h);
            nearMountains.lineTo(0, h * .93f);
            nearMountains.lineTo(w * .16f, h * .81f);
            nearMountains.lineTo(w * .34f, h * .92f);
            nearMountains.lineTo(w * .57f, h * .77f);
            nearMountains.lineTo(w * .79f, h * .91f);
            nearMountains.lineTo(w, h * .79f);
            nearMountains.lineTo(w, h);
            nearMountains.close();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (sky == null) return;
            paint.setShader(sky);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(horizon);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);
            paint.setColor(theme == 2 ? 0xB226203B : 0xB01B324D);
            canvas.drawPath(farMountains, paint);
            paint.setColor(0xED0D1727);
            canvas.drawPath(nearMountains, paint);
        }
    }
}
