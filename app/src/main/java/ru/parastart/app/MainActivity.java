package ru.parastart.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.Color;
import android.text.util.Linkify;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

public class MainActivity extends Activity {
  static final String PREFS="parastart", KEY="links";
  LinearLayout root, entries;
  TextView info;
  final ArrayList<String> links=new ArrayList<>();
  @Override public void onCreate(Bundle s){super.onCreate(s); PDFBoxResourceLoader.init(getApplicationContext()); load(); screen(); receive(getIntent());}
  void load(){ String v=getSharedPreferences(PREFS,0).getString(KEY,""); if(!v.isEmpty())for(String line:v.split("\n"))if(!line.isEmpty())links.add(line); }
  void save(){getSharedPreferences(PREFS,0).edit().putString(KEY,android.text.TextUtils.join("\n",links)).apply();}
  void screen(){
    ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(32,45,32,32);scroll.addView(root);setContentView(scroll);
    TextView title=new TextView(this);title.setText("ПараСтарт");title.setTextSize(29);title.setTextColor(Color.rgb(21,70,147));root.addView(title);
    info=new TextView(this);info.setText("Импортируй PDF из MAX через «Поделиться» либо выбери файл. Для ссылок из уведомлений включи доступ к уведомлениям.");info.setPadding(0,18,0,18);root.addView(info);
    button("Загрузить PDF",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/pdf");startActivityForResult(i,100);});
    button("Разрешить чтение уведомлений MAX",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
    button("Добавить ссылку вручную",()->addDialog());
    button("Обновить список",()->list());
    entries=new LinearLayout(this);entries.setOrientation(1);root.addView(entries);list();
  }
  void button(String label,Runnable call){Button b=new Button(this);b.setText(label);root.addView(b);b.setOnClickListener(v->call.run());}
  void addDialog(){EditText edit=new EditText(this);edit.setHint("https://vks.knn-nk.ru/...");new AlertDialog.Builder(this).setTitle("Ссылка на пару").setView(edit).setPositiveButton("Сохранить",(d,w)->{add(edit.getText().toString());}).setNegativeButton("Отмена",null).show();}
  void add(String v){if(v.startsWith("https://")&&!links.contains(v)){links.add(0,v);save();list();}}
  void list(){if(entries==null)return;entries.removeAllViews();TextView t=new TextView(this);t.setText("Найденные конференции ("+links.size()+")");t.setTextSize(20);t.setPadding(0,20,0,12);entries.addView(t);for(String link:links){Button b=new Button(this);b.setAllCaps(false);b.setText(link);entries.addView(b);b.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link)));}catch(Exception ex){Toast.makeText(this,"Не удалось открыть",0).show();}});b.setOnLongClickListener(v->{new AlertDialog.Builder(this).setMessage("Удалить ссылку?").setPositiveButton("Удалить",(d,w)->{links.remove(link);save();list();}).setNegativeButton("Отмена",null).show();return true;});}}
  void receive(Intent intent){if(intent==null)return;if(Intent.ACTION_SEND.equals(intent.getAction())){Uri uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(uri!=null)parse(uri);} }
  @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==100&&res==RESULT_OK&&data!=null)parse(data.getData());}
  void parse(Uri uri){info.setText("Читаю PDF…");new Thread(()->{try(InputStream input=getContentResolver().openInputStream(uri);PDDocument doc=PDDocument.load(input)){String text=new PDFTextStripper().getText(doc);ArrayList<String> found=extract(text);runOnUiThread(()->{for(String s:found)add(s); info.setText("PDF обработан. Найдено ссылок: "+found.size()+". Даты и время необходимо проверить по расписанию.");});}catch(Exception e){runOnUiThread(()->info.setText("Не удалось прочитать PDF: "+e.getMessage()));}}).start();}
  static ArrayList<String> extract(String text){ArrayList<String> result=new ArrayList<>();Matcher m=Pattern.compile("https?://[^\\s<>\"']+",Pattern.CASE_INSENSITIVE).matcher(text);while(m.find()){String u=m.group().replaceAll("[.,;)]+$","");if(!result.contains(u))result.add(u);}return result;}
}
