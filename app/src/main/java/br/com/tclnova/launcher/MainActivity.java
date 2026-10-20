package br.com.tclnova.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
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
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.File;
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

/** TCL Nova 0.4: interface local e launcher Android TV 9 (API 28). */
public final class MainActivity extends Activity {
    private static final int WHITE = 0xFFF7F7FA;
    private static final int MUTED = 0xFFD5CED7;
    private static final int PANEL = 0xCC17101D;
    private static final int FOCUSED = 0xFF573246;
    private static final String PREFS = "tcl_nova_settings";
    private static final String FAVS = "favorite_packages";
    private static final String FAVS_INITIALIZED = "favorites_initialized";
    private static final String EXPANDED = "expanded";
    private static final String SYSTEM_EXPANDED = "system_expanded";
    private static final String THEME = "theme";
    private static final String WALLPAPER = "custom_wallpaper.jpg";
    private static final String FAV_ORDER = "favorite_order";
    private static final Locale LOCALE = new Locale("pt", "BR");

    private final Map<String, TvApp> appMap = new HashMap<>();
    private final Map<String, View> focusTargets = new HashMap<>();
    private List<TvApp> cachedApps = Collections.emptyList();
    private SharedPreferences prefs;
    private ScrollView page;
    private TextView clockView;
    private TextView dateView;
    private TextView greetingView;
    private TextView activeName;
    private View firstFavorite;
    private String lastFocus = "favorites";
    private UploadServer uploadServer;
    private AlertDialog uploadDialog;
    private int nextId = 200;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            refreshClock();
            handler.postDelayed(this, 60000);
        }
    };

    private static final class TvApp {
        final String pkg, activity, name;
        final Drawable icon, banner;
        final boolean tv, internal;
        TvApp(String pkg, String activity, String name, Drawable icon,
              Drawable banner, boolean tv, boolean internal) {
            this.pkg = pkg;
            this.activity = activity;
            this.name = name;
            this.icon = icon;
            this.banner = banner;
            this.tv = tv;
            this.internal = internal;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        cachedApps = loadApps();
        render("favorites");
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(clockTick);
        clockTick.run();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(clockTick);
        // Upload só existe enquanto o diálogo de recebimento estiver aberto.
        stopUpload();
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopUpload();
        super.onDestroy();
    }

    private int dp(float value) {
        return (int) (getResources().getDisplayMetrics().density * value + .5f);
    }

    private TextView txt(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private void space(LinearLayout l, int h) {
        l.addView(new View(this), new LinearLayout.LayoutParams(1, dp(h)));
    }

    private GradientDrawable bg(int color, int radius, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(focused ? FOCUSED : color);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(focused ? 3 : 1), focused ? Color.WHITE : 0x6685819A);
        return g;
    }

    /** Um único estilo e tratamento de foco para todos os controles. */
    private void attachFocus(View control, String key, int radius, int color,
                             Runnable click, String description) {
        control.setId(++nextId);
        control.setFocusable(true);
        control.setClickable(true);
        control.setBackground(bg(color, radius, false));
        control.setContentDescription(description);
        focusTargets.put(key, control);
        control.setOnFocusChangeListener((view, hasFocus) -> {
            view.setBackground(bg(color, radius, hasFocus));
            view.setElevation(hasFocus ? dp(7) : 0);
            if (hasFocus) {
                lastFocus = key;
                ensureFocusInView(view);
            }
        });
        control.setOnClickListener(v -> click.run());
    }

    private void ensureFocusInView(View target) {
        if (page == null) return;
        page.post(() -> {
            if (!target.isAttachedToWindow() || !target.hasFocus()) return;
            int[] loc = new int[2], root = new int[2];
            target.getLocationOnScreen(loc);
            page.getLocationOnScreen(root);
            int margin = Math.min(dp(58), page.getHeight() / 7);
            int top = loc[1] - root[1];
            int bottom = top + target.getHeight();
            int dy = top < margin ? top - margin :
                bottom > page.getHeight() - margin ? bottom - (page.getHeight() - margin) : 0;
            if (dy != 0) page.scrollBy(0, dy);
            ViewParent ancestor = target.getParent();
            while (ancestor != null) {
                if (ancestor instanceof HorizontalScrollView) {
                    HorizontalScrollView row = (HorizontalScrollView) ancestor;
                    int[] rl = new int[2];
                    row.getLocationOnScreen(rl);
                    int left = loc[0] - rl[0];
                    int right = left + target.getWidth();
                    int padx = dp(24);
                    int dx = left < padx ? left - padx :
                        right > row.getWidth() - padx ? right - (row.getWidth() - padx) : 0;
                    if (dx != 0) row.scrollBy(dx, 0);
                    break;
                }
                ancestor = ancestor.getParent();
            }
        });
    }

    private TextView pill(String label, String key, Runnable action) {
        TextView p = txt(label, 13, WHITE, true);
        p.setGravity(Gravity.CENTER);
        p.setPadding(dp(17), 0, dp(17), 0);
        p.setMinHeight(dp(43));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(43));
        lp.leftMargin = dp(9);
        p.setLayoutParams(lp);
        attachFocus(p, key, 24, 0x99473A53, action, label);
        return p;
    }

    private void render(String focusKey) {
        focusTargets.clear();
        firstFavorite = null;
        nextId = 200;
        int theme = prefs.getInt(THEME, 0);
        FrameLayout frame = new FrameLayout(this);
        frame.addView(new WallpaperView(this, theme, new File(getFilesDir(), WALLPAPER)),
            new FrameLayout.LayoutParams(-1, -1));
        page = new ScrollView(this);
        page.setFillViewport(true);
        page.setFocusable(false);
        page.setVerticalScrollBarEnabled(false);
        LinearLayout root = vertical();
        root.setPadding(dp(48), dp(26), dp(47), dp(23));
        root.setClipToPadding(false);
        root.setClipChildren(false);
        page.addView(root);
        frame.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);

        LinearLayout header = horizontal();
        LinearLayout timeColumn = vertical();
        greetingView = txt("", 10, 0xFFEBC9D6, true);
        timeColumn.addView(greetingView);
        space(timeColumn, 7);
        LinearLayout timeLine = horizontal();
        clockView = txt("", 34, WHITE, false);
        timeLine.addView(clockView);
        dateView = txt("", 12, MUTED, false);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-2, dp(38));
        dateParams.leftMargin = dp(22);
        timeLine.addView(dateView, dateParams);
        timeColumn.addView(timeLine);
        header.addView(timeColumn, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(pill("ENTRADAS", "inputs", this::openTvInputs));
        header.addView(pill("WI-FI", "wifi", () -> launchSettings(Settings.ACTION_WIFI_SETTINGS)));
        header.addView(pill("⚙", "settings", this::showCustomization));
        root.addView(header);
        refreshClock();

        // Reservamos espaço negativo: cartões ficam mais próximos do rodapé, como na referência.
        View heroSpace = new View(this);
        root.addView(heroSpace, new LinearLayout.LayoutParams(1, 0, 1));
        space(root, 78);
        activeName = txt("Seus aplicativos", 28, WHITE, false);
        root.addView(activeName);
        space(root, 16);

        Set<String> saved = prefs.getStringSet(FAVS, Collections.emptySet());
        boolean customized = prefs.getBoolean(FAVS_INITIALIZED, false);
        List<TvApp> favorites = new ArrayList<>(), ordinary = new ArrayList<>(), system = new ArrayList<>();
        for (TvApp app : cachedApps) {
            if (saved.contains(app.pkg) || (!customized && defaultFavorite(app))) favorites.add(app);
            else if (app.internal) system.add(app);
            else ordinary.add(app);
        }
        String preferredOrder = prefs.getString(FAV_ORDER, "");
        if (!preferredOrder.isEmpty()) {
            String[] preferred = preferredOrder.split("\\|");
            Map<String, Integer> positions = new HashMap<>();
            for (int index = 0; index < preferred.length; index++) positions.put(preferred[index], index);
            favorites.sort(Comparator.comparingInt(a -> positions.containsKey(a.pkg)
                ? positions.get(a.pkg) : 1000 + priority(a)));
        }
        // Mesmo sem favoritos, a linha exibe o botao para adicionar aplicativos.
        addAppRow(root, favorites, true);

        space(root, 20);
        boolean expanded = prefs.getBoolean(EXPANDED, false);
        LinearLayout libraryBar = horizontal();
        TextView libraryTitle = txt("APLICATIVOS", 12, MUTED, true);
        libraryBar.addView(libraryTitle, new LinearLayout.LayoutParams(0, dp(45), 1));
        libraryBar.addView(pill(expanded ? "FECHAR BIBLIOTECA  ▲" : "TODOS OS APPS  ▼",
            "library", () -> {
                prefs.edit().putBoolean(EXPANDED, !expanded).apply();
                render("library");
            }));
        root.addView(libraryBar);
        if (expanded) {
            space(root, 7);
            addAppRow(root, ordinary, false);
        }

        space(root, 12);
        boolean systemExpanded = prefs.getBoolean(SYSTEM_EXPANDED, false);
        LinearLayout techBar = horizontal();
        techBar.addView(txt("", 12, MUTED, false), new LinearLayout.LayoutParams(0, dp(39), 1));
        techBar.addView(pill(systemExpanded ? "OCULTAR SISTEMA  ▲" : "UTILITÁRIOS E SISTEMA  ▾",
            "system", () -> {
                prefs.edit().putBoolean(SYSTEM_EXPANDED, !systemExpanded).apply();
                render("system");
            }));
        root.addView(techBar);
        if (systemExpanded) {
            space(root, 8);
            addAppRow(root, system, false);
        }
        space(root, 22);

        View restore = focusTargets.get(focusKey);
        if (restore == null) restore = focusTargets.get(lastFocus);
        if (restore == null) restore = firstFavorite;
        if (restore == null) restore = focusTargets.get("library");
        final View target = restore;
        if (target != null) root.post(() -> {
            if (target.isAttachedToWindow()) {
                target.requestFocus();
                ensureFocusInView(target);
            }
        });
    }

    private void addAppRow(LinearLayout root, List<TvApp> apps, boolean favorites) {
        if (apps.isEmpty() && !favorites) {
            root.addView(txt("Não há aplicativos nesta seção.", 13, MUTED, false));
            return;
        }
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setFocusable(false);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setFillViewport(false);
        strip.setClipToPadding(false);
        strip.setClipChildren(false);
        strip.setPadding(dp(5), dp(8), dp(5), dp(8));
        LinearLayout row = horizontal();
        row.setClipToPadding(false);
        row.setClipChildren(false);
        for (TvApp app : apps) {
            View card = createAppCard(app);
            if (favorites && firstFavorite == null) firstFavorite = card;
            row.addView(card);
        }
        if (favorites) {
            // Sempre no final da fileira principal, inclusive se estiver vazia.
            View add = createAddFavoriteCard();
            if (firstFavorite == null) firstFavorite = add;
            row.addView(add);
        }
        strip.addView(row);
        root.addView(strip);
    }

    /** Botao de descoberta na mesma fileira dos favoritos, 100% D-pad. */
    private View createAddFavoriteCard() {
        LinearLayout card = vertical();
        card.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(174), dp(105));
        lp.rightMargin = dp(13);
        card.setLayoutParams(lp);

        TextView plus = txt("+", 39, WHITE, false);
        plus.setGravity(Gravity.CENTER);
        card.addView(plus, new LinearLayout.LayoutParams(-1, dp(55)));
        TextView caption = txt("Adicionar app", 13, WHITE, true);
        caption.setGravity(Gravity.CENTER);
        card.addView(caption, new LinearLayout.LayoutParams(-1, dp(26)));

        attachFocus(card, "add_favorite", 17, 0xF0231929,
            this::showAddFavoritePicker, "Adicionar aplicativo aos favoritos");
        // Mesmo estilo de foco dos cartoes, e titulo central atualizado.
        card.setOnFocusChangeListener((view, focused) -> {
            view.setBackground(bg(0xF0231929, 17, focused));
            view.setElevation(focused ? dp(7) : 0);
            if (focused) {
                lastFocus = "add_favorite";
                if (activeName != null) activeName.setText("Adicionar aplicativo");
                ensureFocusInView(view);
            }
        });
        return card;
    }

    /** Seleciona um app comum instalado, sem expor servicos internos do Android. */
    private void showAddFavoritePicker() {
        List<TvApp> already = orderedFavorites();
        Set<String> selected = new HashSet<>();
        for (TvApp app : already) selected.add(app.pkg);
        List<TvApp> available = new ArrayList<>();
        for (TvApp app : cachedApps) {
            if (!app.internal && !selected.contains(app.pkg)) available.add(app);
        }
        if (available.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("Adicionar aplicativo")
                .setMessage("Nao ha outros aplicativos disponiveis. Se instalou um recentemente, atualize a lista em Configuracoes.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }
        String[] names = new String[available.size()];
        for (int i = 0; i < available.size(); i++) names[i] = available.get(i).name;
        new AlertDialog.Builder(this)
            .setTitle("Adicionar aos favoritos")
            .setItems(names, (dialog, which) -> {
                TvApp chosen = available.get(which);
                // O metodo ja preserva os favoritos padrao da primeira execucao.
                toggleFavorite(chosen.pkg);
                // Manter a ordem existente e inserir o novo aplicativo ao final.
                StringBuilder order = new StringBuilder();
                for (TvApp app : already) {
                    if (order.length() > 0) order.append('|');
                    order.append(app.pkg);
                }
                if (order.length() > 0) order.append('|');
                order.append(chosen.pkg);
                prefs.edit().putString(FAV_ORDER, order.toString()).apply();
                render("add_favorite");
                Toast.makeText(this, chosen.name + " adicionado aos favoritos",
                    Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private View createAppCard(TvApp app) {
        LinearLayout card = vertical();
        card.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(174), dp(105));
        lp.rightMargin = dp(13);
        card.setLayoutParams(lp);
        // Banner de TV quando disponível: parecido com os cartões da referência.
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(app.banner != null ? app.banner : app.icon);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(logo, new LinearLayout.LayoutParams(dp(154), dp(67)));
        if (app.banner == null) {
            TextView caption = txt(app.name, 12, WHITE, true);
            caption.setSingleLine(true);
            caption.setEllipsize(TextUtils.TruncateAt.END);
            caption.setGravity(Gravity.CENTER);
            card.addView(caption, new LinearLayout.LayoutParams(dp(153), dp(23)));
        }
        String key = "app:" + app.pkg;
        attachFocus(card, key, 17, 0xF0231929, () -> launchApp(app), "Abrir " + app.name);
        card.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(bg(0xF0231929, 17, focused));
            v.setElevation(focused ? dp(7) : 0);
            if (focused) {
                lastFocus = key;
                if (activeName != null) activeName.setText(app.name);
                ensureFocusInView(v);
            }
        });
        card.setOnLongClickListener(v -> {
            showAppActions(app);
            return true;
        });
        return card;
    }

    private void refreshClock() {
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        if (greetingView != null) greetingView.setText(
            h < 12 ? "BOM DIA" : h < 18 ? "BOA TARDE" : "BOA NOITE");
        if (clockView != null) clockView.setText(new SimpleDateFormat("HH:mm", LOCALE).format(now.getTime()));
        if (dateView != null) dateView.setText(new SimpleDateFormat("EEEE, d 'de' MMMM", LOCALE).format(now.getTime()));
    }

    private void launchSettings(String action) {
        try { startActivity(new Intent(action)); }
        catch (Exception e) {
            Toast.makeText(this, "Função indisponível nesta TCL", Toast.LENGTH_SHORT).show();
        }
    }

    private void openTvInputs() {
        // O painel de entradas HDMI depende da firmware TCL. Nunca abrir ajustes de teclado.
        try {
            Intent inputs = new Intent("android.settings.TV_INPUT_SETTINGS");
            if (inputs.resolveActivity(getPackageManager()) == null)
                throw new ActivityNotFoundException();
            startActivity(inputs);
        } catch (Exception e) {
            Toast.makeText(this, "Abra Entradas pelo controle TCL (suporte varia por modelo)",
                Toast.LENGTH_LONG).show();
        }
    }

    private void showCustomization() {
        new AlertDialog.Builder(this)
            .setTitle("Personalizar TCL Nova")
            .setItems(new String[]{"Temas e cores", "Receber papel de parede do celular",
                "Remover papel de parede", "Configurações Android", "Atualizar aplicativos"},
                (dialog, selected) -> {
                    if (selected == 0) showThemes();
                    else if (selected == 1) startReceiving();
                    else if (selected == 2) {
                        File file = new File(getFilesDir(), WALLPAPER);
                        if (file.exists() && !file.delete()) {
                            Toast.makeText(this, "Não foi possível remover a foto", Toast.LENGTH_SHORT).show();
                        } else render("settings");
                    } else if (selected == 3) launchSettings(Settings.ACTION_SETTINGS);
                    else {
                        cachedApps = loadApps();
                        render("settings");
                    }
                })
            .setNegativeButton("Fechar", null).show();
    }

    private void showThemes() {
        String[] themes = {"Vermelho premium", "Grafite", "Azul noturno"};
        new AlertDialog.Builder(this).setTitle("Escolher tema")
            .setSingleChoiceItems(themes, prefs.getInt(THEME, 0), (d, i) -> {
                prefs.edit().putInt(THEME, i).apply();
                d.dismiss();
                render("settings");
            }).setNegativeButton("Cancelar", null).show();
    }

    private void startReceiving() {
        stopUpload();
        String address = UploadServer.localIp();
        if (address == null) {
            new AlertDialog.Builder(this).setMessage("Conecte a TV ao Wi-Fi e tente novamente.")
                .setPositiveButton("OK", null).show();
            return;
        }
        try {
            uploadServer = new UploadServer(getFilesDir(), address, file -> {
                handler.post(() -> {
                    if (uploadDialog != null) uploadDialog.dismiss();
                    showPreview(file);
                });
            });
            uploadServer.start();
            String url = uploadServer.getUrl();
            LinearLayout body = vertical();
            body.setPadding(dp(20), dp(6), dp(20), dp(10));
            TextView instructions = txt("Escaneie o QR Code com o celular conectado à mesma rede.\n",
                15, WHITE, false);
            body.addView(instructions);
            ImageView qr = new ImageView(this);
            qr.setImageBitmap(makeQr(url, 260));
            body.addView(qr, new LinearLayout.LayoutParams(dp(260), dp(260)));
            TextView link = txt(url, 14, WHITE, false);
            link.setTextIsSelectable(true);
            body.addView(link);
            uploadDialog = new AlertDialog.Builder(this).setTitle("Enviar foto para TCL Nova")
                .setView(body).setNegativeButton("Cancelar", (d, i) -> stopUpload()).create();
            uploadDialog.setOnDismissListener(d -> stopUpload());
            uploadDialog.show();
        } catch (Exception err) {
            stopUpload();
            new AlertDialog.Builder(this).setMessage("Não foi possível iniciar transferência: " + err.getMessage())
                .setPositiveButton("OK", null).show();
        }
    }

    private Bitmap makeQr(String url, int size) throws Exception {
        BitMatrix m = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++)
            b.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
        return b;
    }

    private void showPreview(File file) {
        // Não muda o wallpaper existente até o usuário confirmar.
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 700) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap preview = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        if (preview == null) { file.delete(); return; }
        ImageView image = new ImageView(this);
        image.setImageBitmap(preview);
        image.setAdjustViewBounds(true);
        image.setMaxHeight(dp(280));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        new AlertDialog.Builder(this).setTitle("Usar esta fotografia?")
            .setView(image)
            .setPositiveButton("Aplicar", (d, i) -> {
                File dest = new File(getFilesDir(), WALLPAPER);
                File backup = new File(getFilesDir(), "wallpaper_previous.tmp");
                if (backup.exists()) backup.delete();
                boolean hadOld = dest.exists();
                if (hadOld && !dest.renameTo(backup)) {
                    Toast.makeText(this, "Não foi possível proteger o fundo anterior", Toast.LENGTH_LONG).show();
                    return;
                }
                if (!file.renameTo(dest)) {
                    if (hadOld) backup.renameTo(dest);
                    Toast.makeText(this, "Não foi possível salvar a imagem", Toast.LENGTH_LONG).show();
                } else {
                    if (backup.exists()) backup.delete();
                    render("settings");
                }
            })
            .setNegativeButton("Cancelar", (d, i) -> file.delete())
            .setOnCancelListener(d -> file.delete())
            .show();
    }

    private void stopUpload() {
        if (uploadServer != null) { uploadServer.close(); uploadServer = null; }
        uploadDialog = null;
    }

    private void launchApp(TvApp app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(app.tv ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
        intent.setClassName(app.pkg, app.activity);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(intent); }
        catch (Exception error) {
            try {
                Intent alternative = getPackageManager().getLaunchIntentForPackage(app.pkg);
                if (alternative == null) throw new ActivityNotFoundException();
                startActivity(alternative);
            } catch (Exception ignored) {
                Toast.makeText(this, "Não foi possível abrir " + app.name, Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean defaultFavorite(TvApp app) {
        String s = app.name.toLowerCase(LOCALE);
        return s.contains("netflix") || s.contains("youtube") ||
            s.contains("prime video") || s.contains("globoplay");
    }

    private List<TvApp> orderedFavorites() {
        List<TvApp> list = new ArrayList<>();
        Set<String> stored = prefs.getStringSet(FAVS, Collections.emptySet());
        boolean initialized = prefs.getBoolean(FAVS_INITIALIZED, false);
        for (TvApp app : cachedApps)
            if (stored.contains(app.pkg) || (!initialized && defaultFavorite(app))) list.add(app);
        String order = prefs.getString(FAV_ORDER, "");
        if (!order.isEmpty()) {
            String[] pieces = order.split("\\|");
            Map<String, Integer> ranks = new HashMap<>();
            for (int i = 0; i < pieces.length; i++) ranks.put(pieces[i], i);
            list.sort(Comparator.comparingInt(a -> ranks.containsKey(a.pkg)
                ? ranks.get(a.pkg) : 1000 + priority(a)));
        }
        return list;
    }

    private void moveFavorite(String pkg, int direction) {
        List<TvApp> items = orderedFavorites();
        int at = -1;
        for (int i = 0; i < items.size(); i++) if (items.get(i).pkg.equals(pkg)) at = i;
        if (at < 0 || at + direction < 0 || at + direction >= items.size()) return;
        Collections.swap(items, at, at + direction);
        StringBuilder str = new StringBuilder();
        for (TvApp item : items) {
            if (str.length() > 0) str.append('|');
            str.append(item.pkg);
        }
        prefs.edit().putString(FAV_ORDER, str.toString()).apply();
        render("app:" + pkg);
    }

    private void showAppActions(TvApp app) {
        boolean favorite = false;
        for (TvApp item : orderedFavorites()) if (item.pkg.equals(app.pkg)) favorite = true;
        String[] choices = {favorite ? "Remover dos favoritos" : "Adicionar aos favoritos",
            "Mover favorito para a esquerda", "Mover favorito para a direita"};
        new AlertDialog.Builder(this).setTitle(app.name)
            .setItems(choices, (dialog, which) -> {
                if (which == 0) {
                    toggleFavorite(app.pkg);
                    // Ao mudar o conjunto, invalidar ordenação antiga se necessário.
                    prefs.edit().remove(FAV_ORDER).apply();
                    render("app:" + app.pkg);
                } else if (which == 1) moveFavorite(app.pkg, -1);
                else moveFavorite(app.pkg, 1);
            }).setNegativeButton("Cancelar", null).show();
    }

    private void toggleFavorite(String pkg) {
        Set<String> updated = new HashSet<>(prefs.getStringSet(FAVS, Collections.emptySet()));
        if (!prefs.getBoolean(FAVS_INITIALIZED, false)) {
            for (TvApp app : cachedApps) if (defaultFavorite(app)) updated.add(app.pkg);
        }
        if (!updated.add(pkg)) updated.remove(pkg);
        prefs.edit().putStringSet(FAVS, updated).putBoolean(FAVS_INITIALIZED, true).apply();
    }

    private boolean isInternal(String pkg, String name, ApplicationInfo info) {
        boolean system = (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        if (!system) return false;
        if (pkg.equals("com.android.vending")) return false;
        String low = name.toLowerCase(LOCALE);
        String[] ordinary = {"netflix", "youtube", "prime video", "amazon", "globoplay",
            "disney", "spotify", "max", "plex", "crunchyroll", "twitch", "play store",
            "google play", "paramount", "apple tv", "google tv", "deezer", "pluto tv"};
        for (String token : ordinary) if (low.contains(token)) return false;
        return true;
    }

    private List<TvApp> loadApps() {
        appMap.clear();
        PackageManager pm = getPackageManager();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true);
        collect(pm, Intent.CATEGORY_LAUNCHER, false);
        List<TvApp> list = new ArrayList<>(appMap.values());
        list.sort(Comparator.comparingInt(this::priority)
            .thenComparing(app -> app.name.toLowerCase(LOCALE)));
        return list;
    }

    private void collect(PackageManager pm, String category, boolean tv) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo r : pm.queryIntentActivities(query, 0)) {
            if (r.activityInfo == null) continue;
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || appMap.containsKey(pkg)) continue;
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                Drawable banner = null;
                try { banner = pm.getApplicationBanner(pkg); } catch (Exception ignored) { }
                String name = String.valueOf(r.loadLabel(pm));
                appMap.put(pkg, new TvApp(pkg, r.activityInfo.name, name,
                    r.loadIcon(pm), banner, tv, isInternal(pkg, name, info)));
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
    }

    private int priority(TvApp app) {
        String s = app.name.toLowerCase(LOCALE);
        if (s.contains("netflix")) return 0;
        if (s.contains("youtube")) return 1;
        if (s.contains("prime video")) return 2;
        if (s.contains("globoplay")) return 3;
        return 50;
    }

    /** Background estático: shaders cacheados, JPEG decodificado com amostragem. */
    private static final class WallpaperView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final int theme;
        private final File photo;
        private Shader base, glow;
        private Bitmap bitmap;
        private boolean imageLoadStarted;
        private volatile boolean disposed;
        WallpaperView(Activity context, int theme, File photo) {
            super(context);
            this.theme = theme;
            this.photo = photo;
        }
        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            if (w <= 0 || h <= 0) return;
            int[][] colors = {
                {0xFF650011, 0xFFB5001A, 0xFF25051C, 0xFF080D1B},
                {0xFF24242E, 0xFF393643, 0xFF141723, 0xFF080B12},
                {0xFF102A54, 0xFF13385B, 0xFF151A35, 0xFF090F1C}
            };
            base = new LinearGradient(0, 0, w, h, colors[Math.max(0, Math.min(theme, 2))],
                null, Shader.TileMode.CLAMP);
            glow = new RadialGradient(w * .40f, h * .23f, w * .53f,
                new int[]{0x77E80B29, 0x11BA0029, 0x00BA0029}, null, Shader.TileMode.CLAMP);
            // Decodificação fora da thread de UI: evitar engasgos ao abrir o launcher.
            if (photo.isFile() && !imageLoadStarted) {
                imageLoadStarted = true;
                Thread loader = new Thread(() -> {
                    Bitmap result = null;
                    try {
                        BitmapFactory.Options bounds = new BitmapFactory.Options();
                        bounds.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(photo.getAbsolutePath(), bounds);
                        int sample = 1;
                        while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1080)
                            sample *= 2;
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = sample;
                        options.inPreferredConfig = Bitmap.Config.RGB_565;
                        result = BitmapFactory.decodeFile(photo.getAbsolutePath(), options);
                    } catch (RuntimeException ignored) { }
                    final Bitmap loaded = result;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (disposed) {
                            if (loaded != null) loaded.recycle();
                        } else {
                            bitmap = loaded;
                            invalidate();
                        }
                    });
                }, "NovaWallpaperDecode");
                loader.setDaemon(true);
                loader.start();
            }
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            if (bitmap != null) {
                float scale = Math.max((float) w / bitmap.getWidth(), (float) h / bitmap.getHeight());
                float dw = bitmap.getWidth() * scale, dh = bitmap.getHeight() * scale;
                paint.setShader(null);
                paint.setColor(Color.WHITE);
                canvas.drawBitmap(bitmap, null,
                    new RectF((w-dw)/2, (h-dh)/2, (w+dw)/2, (h+dh)/2), paint);
                paint.setColor(0x74070012);
                canvas.drawRect(0, 0, w, h, paint);
            } else if (base != null) {
                paint.setColor(Color.WHITE);
                paint.setShader(base);
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(glow);
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(null);
            }
        }
        @Override protected void onDetachedFromWindow() {
            disposed = true;
            super.onDetachedFromWindow();
            if (bitmap != null) { bitmap.recycle(); bitmap = null; }
        }
    }
}
