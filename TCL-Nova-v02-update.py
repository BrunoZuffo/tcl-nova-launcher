from pathlib import Path
p=Path('app/src/main/java/br/com/tclnova/launcher/MainActivity.java')
if not p.exists():
    raise SystemExit('Execute na pasta TCL-Nova-Launcher')
s=p.read_text(encoding='utf-8')
if 'TCL Nova 0.2' in s:
    raise SystemExit('Versao 0.2 ja aplicada')
def swap(a,b):
    global s
    if a not in s: raise SystemExit('Trecho nao encontrado; nenhuma alteracao salva: '+a[:90])
    s=s.replace(a,b,1)
swap('import android.app.Activity;', 'import android.app.Activity;\nimport android.app.AlertDialog;')
swap('    private SharedPreferences preferences;', '''    private SharedPreferences preferences;
    private List<App> cachedApps = Collections.emptyList();''')
swap('        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);', '''        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        cachedApps = loadApps();''')
swap('        if (preferences != null) render();','''        // Mantem a tela existente ao voltar dos aplicativos: menos trabalho e piscadas.''')
swap('        appMap.clear();\n        List<App> apps = loadApps();','''        List<App> apps = cachedApps;''')
swap('        header.addView(chip("Wi-Fi", Settings.ACTION_WIFI_SETTINGS));','''        header.addView(customizeChip());''')
swap('        gap(44);\n        content.addView(label("Sua TV. Seu espaço.", 28, WHITE, true));\n        content.addView(label("Apenas seus aplicativos. Sem banners, anúncios ou assinaturas.",\n            13, MUTED, false));\n        gap(38);', '''        gap(36);
        content.addView(label("Boa sessão.", 30, WHITE, true));
        content.addView(label("Sua TV, seus aplicativos. Sem publicidade na tela inicial.",
            13, MUTED, false));
        gap(30);''')
swap('''        addRow("Todos os aplicativos", apps);
        gap(36);''','''        gap(15);
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
        gap(30);''')
swap('"TCL Nova 0.1  •  Interface local  •  Sem rastreamento"','"TCL Nova 0.2  •  Personalizado  •  Sem rastreamento"')
swap('    private String greeting() {', '''    private TextView customizeChip() {
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

    private String greeting() {''')
swap('        PackageManager pm = getPackageManager();\n        collectApps', '        appMap.clear();\n        PackageManager pm = getPackageManager();\n        collectApps')
swap('frame.addView(new Backdrop(this), new FrameLayout.LayoutParams(-1, -1));', 'frame.addView(new Backdrop(this, preferences.getInt("theme",0)), new FrameLayout.LayoutParams(-1, -1));')
swap('''        Backdrop(Activity context) {
            super(context);
        }''','''        private final int theme;
        Backdrop(Activity context, int theme) {
            super(context);
            this.theme = theme;
        }''')
swap('''            paint.setShader(new LinearGradient(0, 0, w, h,
                new int[]{0xFF782A3C, 0xFF271D30, 0xFF0E101C},''','''            int[] colors = theme == 1
                ? new int[]{0xFF292C34, 0xFF1B1E27, 0xFF090B10}
                : theme == 2
                ? new int[]{0xFF18406D, 0xFF17233B, 0xFF0C1020}
                : new int[]{0xFF782A3C, 0xFF271D30, 0xFF0E101C};
            paint.setShader(new LinearGradient(0, 0, w, h,
                colors,''')
p.write_text(s,encoding='utf-8')
print('Atualizacao TCL Nova 0.2 aplicada com sucesso!')
