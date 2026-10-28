package ru.parastart.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;

/** Opens a VKS room inside the app, fills in the student's name and presses "join". */
public class JoinActivity extends Activity {
  static final String EXTRA_URL = "url", EXTRA_TITLE = "title";

  WebView web;
  String url;
  PermissionRequest pending;

  public static void open(Context c, String url, String title) {
    Intent i = new Intent(c, JoinActivity.class);
    i.putExtra(EXTRA_URL, url);
    i.putExtra(EXTRA_TITLE, title);
    if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    c.startActivity(i);
  }

  int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

  @Override protected void onCreate(Bundle s) {
    super.onCreate(s);
    url = getIntent().getStringExtra(EXTRA_URL);
    if (url == null || !url.startsWith("https://")) { finish(); return; }
    String title = getIntent().getStringExtra(EXTRA_TITLE);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    getWindow().setStatusBarColor(MainActivity.PRIMARY);

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);

    LinearLayout bar = new LinearLayout(this);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setBackgroundColor(MainActivity.PRIMARY);
    bar.setPadding(dp(8), dp(6), dp(8), dp(6));
    TextView close = barButton("✕");
    close.setOnClickListener(v -> confirmExit());
    bar.addView(close);
    TextView t = new TextView(this);
    t.setText(title == null || title.isEmpty() ? "Пара" : title);
    t.setTextColor(Color.WHITE);
    t.setTextSize(16);
    t.setSingleLine(true);
    t.setEllipsize(android.text.TextUtils.TruncateAt.END);
    t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2); tl.weight = 1; tl.leftMargin = dp(6);
    bar.addView(t, tl);
    TextView browser = barButton("В браузере");
    browser.setTextSize(13);
    browser.setOnClickListener(v -> {
      try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); finish(); }
      catch (Exception e) { Toast.makeText(this, "Нет браузера", Toast.LENGTH_SHORT).show(); }
    });
    bar.addView(browser);
    root.addView(bar);

    web = new WebView(this);
    WebSettings ws = web.getSettings();
    ws.setJavaScriptEnabled(true);
    ws.setDomStorageEnabled(true);
    ws.setMediaPlaybackRequiresUserGesture(false);
    ws.setLoadWithOverviewMode(true);
    ws.setUseWideViewPort(true);
    CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
    web.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        String u = r.getUrl().toString();
        if (u.startsWith("https://") || u.startsWith("http://")) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); } catch (Exception ignored) {}
        return true;
      }
      @Override public void onPageFinished(WebView v, String u) { autofill(); listenOnly(); }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override public void onPermissionRequest(PermissionRequest request) {
        runOnUiThread(() -> {
          if (hasMedia()) request.grant(request.getResources());
          else { pending = request; askMedia(); }
        });
      }
    });
    root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(root);
    web.loadUrl(url);
  }

  TextView barButton(String label) {
    TextView b = new TextView(this);
    b.setText(label);
    b.setTextColor(Color.WHITE);
    b.setTextSize(18);
    b.setGravity(Gravity.CENTER);
    b.setPadding(dp(12), dp(8), dp(12), dp(8));
    GradientDrawable g = new GradientDrawable(); g.setColor(0x26FFFFFF); g.setCornerRadius(dp(12));
    b.setBackground(g);
    return b;
  }

  boolean hasMedia() {
    return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
  }

  void askMedia() {
    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.MODIFY_AUDIO_SETTINGS}, 7);
  }

  @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
    super.onRequestPermissionsResult(code, perms, res);
    if (pending != null) {
      if (hasMedia()) pending.grant(pending.getResources()); else pending.deny();
      pending = null;
    }
  }

  /** Fills the name on the room's join page and presses the join button (skipped when an access code or consent is needed). */
  void autofill() {
    String name = getSharedPreferences(MainActivity.PREFS, 0).getString(MainActivity.FIO, "");
    if (name.isEmpty() || web.getUrl() == null || web.getUrl().contains("html5client")) return;
    String js = "(function(n){if(window.__knn)return;window.__knn=1;"
        + "function setv(e,v){var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d.set.call(e,v);"
        + "e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}"
        + "function vis(e){return e.offsetParent!==null;}"
        + "var k=0,t=setInterval(function(){if(++k>80){clearInterval(t);return;}"
        + "var all=[].slice.call(document.querySelectorAll('input')).filter(vis);"
        + "var inp=document.querySelector('input#name,input[name=name],input[autocomplete=name]');"
        + "if(!inp||!vis(inp)){inp=all.filter(function(e){var s=(e.id+' '+e.name+' '+(e.placeholder||'')).toLowerCase();"
        + "return (e.type==='text'||!e.type)&&!/code|код|pass|парол/.test(s);})[0];}"
        + "if(!inp)return;clearInterval(t);"
        + "if(!inp.value)setv(inp,n);"
        + "var other=all.some(function(e){return e!==inp&&(e.type==='text'||e.type==='password'||!e.type)&&!e.value;});"
        + "var cons=all.some(function(e){return e.type==='checkbox'&&!e.checked;});"
        + "if(other||cons)return;"
        + "var b=(inp.form&&inp.form.querySelector('button[type=submit],input[type=submit]'))||"
        + "[].slice.call(document.querySelectorAll('button')).filter(function(b){return vis(b)&&/присоедин|войти|join/i.test(b.textContent);})[0];"
        + "if(b)setTimeout(function(){b.click();},400);"
        + "},400);})(" + JSONObject.quote(name) + ");";
    web.evaluateJavascript(js, null);
  }

  /** In the conference (BigBlueButton) picks "Только слушать" in the audio dialog as soon as it appears. */
  void listenOnly() {
    String js = "(function(){if(window.__knnListen)return;window.__knnListen=1;"
        + "function vis(e){return e&&e.offsetParent!==null;}"
        + "var k=0,t=setInterval(function(){if(++k>400){clearInterval(t);return;}"
        + "var b=document.querySelector('[data-test=listenOnlyBtn],[data-test=listenOnlyButton]');"
        + "if(!vis(b)){b=[].slice.call(document.querySelectorAll('button,[role=button]')).filter(function(e){"
        + "var s=((e.getAttribute('aria-label')||'')+' '+e.textContent).toLowerCase();"
        + "return vis(e)&&/только слушать|listen only|слушать/.test(s);})[0];}"
        + "if(b){clearInterval(t);b.click();}"
        + "},500);})();";
    web.evaluateJavascript(js, null);
  }

  void confirmExit() {
    new AlertDialog.Builder(this).setMessage("Выйти с пары?")
        .setPositiveButton("Выйти", (d, w) -> finish())
        .setNegativeButton("Остаться", null).show();
  }

  @Override public void onBackPressed() { confirmExit(); }

  @Override protected void onDestroy() {
    if (web != null) { web.loadUrl("about:blank"); web.destroy(); }
    super.onDestroy();
  }
}
