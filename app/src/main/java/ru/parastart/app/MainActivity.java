package ru.parastart.app;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

public class MainActivity extends Activity {
  static final String PREFS = "parastart", KEY = "links", FAV = "favs";

  static final int PRIMARY = 0xFF1E4FD8, PRIMARY_2 = 0xFF4F7DF5, BG = 0xFFF2F4F9, CARD = 0xFFFFFFFF,
      TEXT = 0xFF171C2B, MUTED = 0xFF6B7385, LINE = 0xFFE3E7EF, STAR = 0xFFFFB020, STAR_OFF = 0xFFC9CED8,
      SOFT = 0xFFE8EEFD, DANGER = 0xFFD93C3C;
  static final int[] AVATARS = {0xFF1E4FD8, 0xFF0E9F6E, 0xFF7C3AED, 0xFFE8590C, 0xFFD6336C, 0xFF0B7285, 0xFF5C7CFA, 0xFF2F9E44};

  final ArrayList<String> links = new ArrayList<>();
  final ArrayList<String[]> teachers = new ArrayList<>();
  final HashMap<String, String> nameByUrl = new HashMap<>();
  final HashSet<String> favs = new HashSet<>();

  int tab = 0;
  TextView tabTeachers, tabLinks, info;
  LinearLayout listBox, actionsBox;
  FrameLayout searchBox;
  EditText search;

  @Override public void onCreate(Bundle s) {
    super.onCreate(s);
    PDFBoxResourceLoader.init(getApplicationContext());
    Window w = getWindow();
    w.setStatusBarColor(PRIMARY);
    w.setNavigationBarColor(BG);
    w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    loadTeachers();
    loadFavs();
    loadLinks();
    screen();
    if (receive(getIntent())) selectTab(1);
  }

  @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); if (receive(intent)) selectTab(1); }

  @Override protected void onResume() { super.onResume(); loadLinks(); render(); }

  // ---------- data ----------
  void loadTeachers() {
    try (BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open("teachers.tsv"), StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        String[] p = line.split("\t");
        if (p.length == 2) { teachers.add(p); nameByUrl.put(p[1], p[0]); }
      }
    } catch (IOException ignored) {}
  }

  void loadFavs() { favs.clear(); favs.addAll(getSharedPreferences(PREFS, 0).getStringSet(FAV, new HashSet<>())); }
  void saveFavs() { getSharedPreferences(PREFS, 0).edit().putStringSet(FAV, new HashSet<>(favs)).apply(); }

  void loadLinks() {
    links.clear();
    String v = getSharedPreferences(PREFS, 0).getString(KEY, "");
    for (String line : v.split("\n")) if (!line.isEmpty() && !links.contains(line)) links.add(line);
  }
  void saveLinks() { getSharedPreferences(PREFS, 0).edit().putString(KEY, TextUtils.join("\n", links)).apply(); }

  void add(String v) {
    v = v.trim();
    if (v.startsWith("https://") && !links.contains(v)) { links.add(0, v); saveLinks(); }
  }

  // ---------- layout ----------
  int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

  GradientDrawable round(int color, float r) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); return g; }

  Drawable ripple(Drawable content) { return new RippleDrawable(ColorStateList.valueOf(0x1F1E4FD8), content, null); }

  TextView text(String s, float size, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(s); t.setTextSize(size); t.setTextColor(color);
    t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    return t;
  }

  LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

  void screen() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(BG);

    // Header
    LinearLayout header = new LinearLayout(this);
    header.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{PRIMARY, PRIMARY_2});
    hg.setCornerRadii(new float[]{0, 0, 0, 0, dp(28), dp(28), dp(28), dp(28)});
    header.setBackground(hg);
    header.setPadding(dp(20), dp(18), dp(20), dp(18));
    header.setElevation(dp(4));

    TextView title = text("ПараСтарт", 28, Color.WHITE, true);
    title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    header.addView(title);
    TextView sub = text("Ссылки на пары СК ОПП в один тап", 14, 0xD9FFFFFF, false);
    sub.setPadding(0, dp(2), 0, dp(16));
    header.addView(sub);

    LinearLayout seg = new LinearLayout(this);
    seg.setBackground(round(0x33FFFFFF, 14));
    seg.setPadding(dp(4), dp(4), dp(4), dp(4));
    tabTeachers = segTab("Преподаватели", 0);
    tabLinks = segTab("Мои ссылки", 1);
    seg.addView(tabTeachers);
    seg.addView(tabLinks);
    header.addView(seg);
    root.addView(header);

    // Body
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(16), dp(16), dp(16), dp(24));
    scroll.addView(body);
    root.addView(scroll, lp(-1, 0));
    ((LinearLayout.LayoutParams) scroll.getLayoutParams()).weight = 1;

    // Search
    searchBox = new FrameLayout(this);
    search = new EditText(this);
    search.setHint("Поиск преподавателя");
    search.setHintTextColor(MUTED);
    search.setTextColor(TEXT);
    search.setTextSize(16);
    search.setSingleLine(true);
    search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
    GradientDrawable sb = round(CARD, 14); sb.setStroke(dp(1), LINE);
    search.setBackground(sb);
    search.setPadding(dp(44), dp(14), dp(16), dp(14));
    searchBox.addView(search, new FrameLayout.LayoutParams(-1, -2));
    TextView glass = text("⌕", 22, MUTED, false);
    FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.START);
    gl.leftMargin = dp(14);
    searchBox.addView(glass, gl);
    search.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence a, int b, int c, int d) {}
      public void onTextChanged(CharSequence a, int b, int c, int d) {}
      public void afterTextChanged(Editable e) { renderList(); }
    });
    LinearLayout.LayoutParams sl = lp(-1, -2); sl.bottomMargin = dp(8);
    body.addView(searchBox, sl);

    // Actions (links tab)
    actionsBox = new LinearLayout(this);
    actionsBox.setOrientation(LinearLayout.VERTICAL);
    info = text("Поделись PDF с расписанием из MAX в ПараСтарт или выбери файл — ссылки на пары найдутся сами.", 14, 0xFF2B3A66, false);
    info.setBackground(round(SOFT, 14));
    info.setPadding(dp(14), dp(12), dp(14), dp(12));
    info.setLineSpacing(0, 1.15f);
    actionsBox.addView(info, lp(-1, -2));
    actionsBox.addView(pill("Загрузить PDF", true, () -> {
      Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
      i.addCategory(Intent.CATEGORY_OPENABLE);
      i.setType("application/pdf");
      startActivityForResult(i, 100);
    }));
    LinearLayout row = new LinearLayout(this);
    TextView a = pill("Добавить ссылку", false, this::addDialog);
    TextView b = pill("Уведомления MAX", false, () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
    LinearLayout.LayoutParams l1 = lp(0, -2); l1.weight = 1; l1.rightMargin = dp(5);
    LinearLayout.LayoutParams l2 = lp(0, -2); l2.weight = 1; l2.leftMargin = dp(5);
    row.addView(a, l1); row.addView(b, l2);
    actionsBox.addView(row);
    body.addView(actionsBox, lp(-1, -2));

    listBox = new LinearLayout(this);
    listBox.setOrientation(LinearLayout.VERTICAL);
    body.addView(listBox, lp(-1, -2));

    setContentView(root);
    render();
  }

  TextView segTab(String label, int index) {
    TextView t = text(label, 15, Color.WHITE, true);
    t.setGravity(Gravity.CENTER);
    t.setPadding(0, dp(10), 0, dp(10));
    LinearLayout.LayoutParams p = lp(0, -2); p.weight = 1;
    t.setLayoutParams(p);
    t.setOnClickListener(v -> selectTab(index));
    return t;
  }

  TextView pill(String label, boolean primary, Runnable call) {
    TextView t = text(label, 15, primary ? Color.WHITE : PRIMARY, true);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(12), dp(14), dp(12), dp(14));
    GradientDrawable g = round(primary ? PRIMARY : CARD, 14);
    if (!primary) g.setStroke(dp(1.5f), 0x551E4FD8);
    t.setBackground(ripple(g));
    if (primary) t.setElevation(dp(2));
    LinearLayout.LayoutParams p = lp(-1, -2); p.topMargin = dp(10);
    t.setLayoutParams(p);
    t.setOnClickListener(v -> call.run());
    return t;
  }

  void selectTab(int i) {
    tab = i;
    if (i == 1 && search != null) {
      InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
      im.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }
    render();
  }

  void render() {
    if (listBox == null) return;
    styleTab(tabTeachers, tab == 0);
    styleTab(tabLinks, tab == 1);
    searchBox.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
    actionsBox.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
    renderList();
  }

  void styleTab(TextView t, boolean on) {
    t.setBackground(on ? round(Color.WHITE, 11) : null);
    t.setTextColor(on ? PRIMARY : 0xE6FFFFFF);
  }

  void section(String s) {
    TextView t = text(s.toUpperCase(new Locale("ru")), 12, MUTED, true);
    t.setLetterSpacing(0.06f);
    t.setPadding(dp(4), dp(18), 0, dp(8));
    listBox.addView(t);
  }

  void renderList() {
    if (listBox == null) return;
    listBox.removeAllViews();
    if (tab == 0) renderTeachers(); else renderLinks();
  }

  static String norm(String s) { return s.toLowerCase(new Locale("ru")).replace('ё', 'е'); }

  void renderTeachers() {
    String[] q = norm(search.getText().toString()).trim().split("\\s+");
    ArrayList<String[]> fav = new ArrayList<>(), rest = new ArrayList<>();
    for (String[] t : teachers) {
      String n = norm(t[0]);
      boolean ok = true;
      for (String part : q) if (!part.isEmpty() && !n.contains(part)) { ok = false; break; }
      if (!ok) continue;
      (favs.contains(t[1]) ? fav : rest).add(t);
    }
    if (fav.isEmpty() && rest.isEmpty()) { empty("Никого не нашли", "Попробуй ввести только фамилию"); return; }
    if (!fav.isEmpty()) { section("Избранные"); for (String[] t : fav) teacherCard(t); }
    section((fav.isEmpty() ? "Преподаватели · " : "Остальные · ") + rest.size());
    for (String[] t : rest) teacherCard(t);
  }

  void teacherCard(String[] t) {
    String name = t[0], url = t[1];
    LinearLayout card = cardBase();
    card.addView(avatar(initials(name), AVATARS[Math.abs(name.hashCode()) % AVATARS.length]));

    LinearLayout mid = new LinearLayout(this);
    mid.setOrientation(LinearLayout.VERTICAL);
    String[] w = name.split(" ", 2);
    mid.addView(text(w[0], 16, TEXT, true));
    if (w.length > 1) mid.addView(text(w[1], 14, MUTED, false));
    LinearLayout.LayoutParams ml = lp(0, -2); ml.weight = 1; ml.leftMargin = dp(14);
    card.addView(mid, ml);

    boolean on = favs.contains(url);
    TextView star = text(on ? "★" : "☆", 24, on ? STAR : STAR_OFF, false);
    star.setPadding(dp(10), dp(4), dp(4), dp(4));
    star.setOnClickListener(v -> { if (!favs.remove(url)) favs.add(url); saveFavs(); renderList(); });
    card.addView(star);

    TextView go = text("›", 26, PRIMARY, false);
    go.setPadding(dp(6), 0, 0, dp(2));
    card.addView(go);

    card.setOnClickListener(v -> open(url));
    card.setOnLongClickListener(v -> { copy(url); return true; });
    listBox.addView(card);
  }

  void renderLinks() {
    section("Найденные конференции · " + links.size());
    if (links.isEmpty()) { empty("Пока пусто", "Загрузи PDF с расписанием или добавь ссылку вручную"); return; }
    for (String link : links) {
      String teacher = nameByUrl.get(link);
      LinearLayout card = cardBase();
      card.addView(teacher != null ? avatar(initials(teacher), AVATARS[Math.abs(teacher.hashCode()) % AVATARS.length]) : avatar("▶", 0xFF0E9F6E));
      LinearLayout mid = new LinearLayout(this);
      mid.setOrientation(LinearLayout.VERTICAL);
      mid.addView(text(teacher != null ? teacher : "Конференция", 16, TEXT, true));
      TextView u = text(link.replaceFirst("^https?://", ""), 13, MUTED, false);
      u.setSingleLine(true);
      u.setEllipsize(TextUtils.TruncateAt.MIDDLE);
      mid.addView(u);
      LinearLayout.LayoutParams ml = lp(0, -2); ml.weight = 1; ml.leftMargin = dp(14);
      card.addView(mid, ml);
      TextView del = text("✕", 18, STAR_OFF, false);
      del.setPadding(dp(12), dp(6), dp(4), dp(6));
      del.setOnClickListener(v -> new AlertDialog.Builder(this).setMessage("Удалить ссылку из списка?")
          .setPositiveButton("Удалить", (d, x) -> { links.remove(link); saveLinks(); renderList(); })
          .setNegativeButton("Отмена", null).show());
      card.addView(del);
      card.setOnClickListener(v -> open(link));
      card.setOnLongClickListener(v -> { copy(link); return true; });
      listBox.addView(card);
    }
  }

  LinearLayout cardBase() {
    LinearLayout card = new LinearLayout(this);
    card.setGravity(Gravity.CENTER_VERTICAL);
    card.setPadding(dp(14), dp(12), dp(12), dp(12));
    card.setBackground(ripple(round(CARD, 18)));
    card.setElevation(dp(1.5f));
    LinearLayout.LayoutParams p = lp(-1, -2);
    p.bottomMargin = dp(10);
    p.leftMargin = dp(1); p.rightMargin = dp(1);
    card.setLayoutParams(p);
    return card;
  }

  TextView avatar(String s, int color) {
    TextView a = text(s, 16, Color.WHITE, true);
    a.setGravity(Gravity.CENTER);
    GradientDrawable g = new GradientDrawable();
    g.setShape(GradientDrawable.OVAL);
    g.setColor(color);
    a.setBackground(g);
    a.setLayoutParams(lp(dp(46), dp(46)));
    return a;
  }

  void empty(String head, String sub) {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setGravity(Gravity.CENTER_HORIZONTAL);
    box.setPadding(dp(16), dp(36), dp(16), dp(36));
    TextView h = text(head, 17, TEXT, true); h.setGravity(Gravity.CENTER);
    TextView s = text(sub, 14, MUTED, false); s.setGravity(Gravity.CENTER); s.setPadding(0, dp(6), 0, 0);
    box.addView(h); box.addView(s);
    listBox.addView(box);
  }

  static String initials(String name) {
    String[] w = name.trim().split("\\s+");
    String r = w[0].substring(0, 1);
    if (w.length > 1) r += w[1].substring(0, 1);
    return r.toUpperCase(new Locale("ru"));
  }

  // ---------- actions ----------
  void open(String url) {
    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
    catch (Exception ex) { Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show(); }
  }

  void copy(String url) {
    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    cm.setPrimaryClip(ClipData.newPlainText("ссылка", url));
    Toast.makeText(this, "Ссылка скопирована", Toast.LENGTH_SHORT).show();
  }

  void addDialog() {
    EditText edit = new EditText(this);
    edit.setHint("https://vks.knn-nk.ru/rooms/…");
    edit.setSingleLine(true);
    FrameLayout wrap = new FrameLayout(this);
    wrap.setPadding(dp(20), dp(8), dp(20), 0);
    wrap.addView(edit);
    new AlertDialog.Builder(this).setTitle("Ссылка на пару").setView(wrap)
        .setPositiveButton("Сохранить", (d, w) -> {
          String v = edit.getText().toString().trim();
          if (!v.startsWith("https://")) Toast.makeText(this, "Ссылка должна начинаться с https://", Toast.LENGTH_SHORT).show();
          else { add(v); renderList(); }
        })
        .setNegativeButton("Отмена", null).show();
  }

  boolean receive(Intent intent) {
    if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return false;
    Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
    if (uri == null) return false;
    parse(uri);
    return true;
  }

  @Override protected void onActivityResult(int req, int res, Intent data) {
    super.onActivityResult(req, res, data);
    if (req == 100 && res == RESULT_OK && data != null) { selectTab(1); parse(data.getData()); }
  }

  void parse(Uri uri) {
    if (info != null) info.setText("Читаю PDF…");
    new Thread(() -> {
      try (InputStream input = getContentResolver().openInputStream(uri); PDDocument doc = PDDocument.load(input)) {
        String text = new PDFTextStripper().getText(doc);
        ArrayList<String> found = extract(text);
        runOnUiThread(() -> {
          for (int i = found.size() - 1; i >= 0; i--) add(found.get(i));
          info.setText("PDF обработан, найдено ссылок: " + found.size() + ". Даты и время сверяй с расписанием.");
          renderList();
        });
      } catch (Exception e) {
        runOnUiThread(() -> info.setText("Не удалось прочитать PDF: " + e.getMessage()));
      }
    }).start();
  }

  static ArrayList<String> extract(String text) {
    ArrayList<String> result = new ArrayList<>();
    Matcher m = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE).matcher(text);
    while (m.find()) {
      String u = m.group().replaceAll("[.,;)]+$", "");
      if (!result.contains(u)) result.add(u);
    }
    return result;
  }
}
