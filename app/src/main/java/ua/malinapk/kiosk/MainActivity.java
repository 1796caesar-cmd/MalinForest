```
package ua.malinapk.kiosk;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
  private static final String SITE="https://malinapk.net/";
  private static final String WEATHER_URL="https://api.open-meteo.com/v1/forecast?latitude=50.77&longitude=29.24&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code&timezone=Europe%2FKyiv&forecast_days=2";
  private static final int PICK_FOLDER=44;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final ArrayList<Uri> playlist=new ArrayList<>();
  private FrameLayout root, content, videoLayer;
  private VideoView video;
  private WebView web;
  private TextView prompt, clock, date, weather, forecast, empty;
  private int index=0, timeout=120000, lastPosition=0;
  private boolean siteOpen=false, switching=false;
  private Uri folder;
  private long lastTouch;
  private String weatherLine="Малин · погода завантажується…", forecastLine="";
  private final Runnable idle=()->{if(siteOpen && System.currentTimeMillis()-lastTouch>=timeout)showVideo();};
  private final Runnable tick=new Runnable(){public void run(){updateClock();handler.postDelayed(this,1000);}};
  private final Runnable refresh=new Runnable(){public void run(){if(folder!=null)loadFolder(folder,false);loadWeather();handler.postDelayed(this,15*60*1000);}};
  private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
  private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
  private TextView label(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
  @Override public void onCreate(Bundle state){super.onCreate(state);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    immersive();timeout=getPreferences(0).getInt("timeout",120000);
    root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
    LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);root.addView(page,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(20),dp(8),dp(20),dp(8));top.setBackgroundColor(0xFF142A20);page.addView(top,new LinearLayout.LayoutParams(-1,dp(86)));
    LinearLayout timeBox=new LinearLayout(this);timeBox.setOrientation(LinearLayout.VERTICAL);top.addView(timeBox,new LinearLayout.LayoutParams(0,-2,1));
    clock=label("--:--",28,true);timeBox.addView(clock);date=label("",12,false);date.setTextColor(0xFFB8D2C1);timeBox.addView(date);
    LinearLayout weatherBox=new LinearLayout(this);weatherBox.setGravity(Gravity.RIGHT);weatherBox.setOrientation(LinearLayout.VERTICAL);top.addView(weatherBox,new LinearLayout.LayoutParams(0,-2,2));
    weather=label(weatherLine,17,true);weather.setGravity(Gravity.RIGHT);weather.setMaxLines(1);weatherBox.addView(weather);
    forecast=label("",13,false);forecast.setTextColor(0xFFBED7C7);forecast.setGravity(Gravity.RIGHT);forecast.setMaxLines(2);weatherBox.addView(forecast);
    top.setOnLongClickListener(v->{settings();return true;});
    content=new FrameLayout(this);page.addView(content,new LinearLayout.LayoutParams(-1,0,1));
    web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setMediaPlaybackRequiresUserGesture(false);web.getSettings().setAllowFileAccess(false);web.setWebViewClient(new WebViewClient());web.setWebChromeClient(new WebChromeClient());web.setVisibility(View.GONE);content.addView(web,new FrameLayout.LayoutParams(-1,-1));
    videoLayer=new FrameLayout(this);videoLayer.setBackgroundColor(0xFF101A14);content.addView(videoLayer,new FrameLayout.LayoutParams(-1,-1));
    video=new VideoView(this);videoLayer.addView(video,new FrameLayout.LayoutParams(-1,-1));
    empty=label("",19,false);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(20),dp(20),dp(20),dp(20));videoLayer.addView(empty,new FrameLayout.LayoutParams(-1,-1));
    prompt=label("☝  ТОРКНІТЬСЯ ЕКРАНА, ЩОБ ДІЗНАТИСЯ БІЛЬШЕ",19,true);prompt.setGravity(Gravity.CENTER);prompt.setPadding(dp(20),dp(13),dp(20),dp(13));prompt.setBackground(bg(0xCC0A2017,14));
    FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);pp.setMargins(dp(15),0,dp(15),dp(26));videoLayer.addView(prompt,pp);
    TranslateAnimation bounce=new TranslateAnimation(0,0,0,-dp(7));bounce.setDuration(650);bounce.setRepeatCount(Animation.INFINITE);bounce.setRepeatMode(Animation.REVERSE);bounce.setInterpolator(new AccelerateDecelerateInterpolator());prompt.startAnimation(bounce);
    video.setOnCompletionListener(mp->playNext());video.setOnErrorListener((mp,what,extra)->{handler.postDelayed(()->playNext(),500);return true;});
    videoLayer.setOnClickListener(v->showSite());video.setOnClickListener(v->showSite());prompt.setOnClickListener(v->showSite());empty.setOnClickListener(v->showSite());
    prompt.setOnLongClickListener(v->{settings();return true;});
    String saved=getPreferences(0).getString("folder",null);if(saved!=null){folder=Uri.parse(saved);loadFolder(folder,true);}
    if(playlist.isEmpty())showEmpty();else playCurrent();
    handler.post(tick);handler.post(refresh);
  }
  private void immersive(){getWindow().getDecorView().setSystemUiVisibility(5894|1024|512|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
  private void updateClock(){Date now=new Date();clock.setText(new SimpleDateFormat("HH:mm",new Locale("uk","UA")).format(now));date.setText(new SimpleDateFormat("EEEE, d MMMM",new Locale("uk","UA")).format(now));}
  private void showEmpty(){video.setVisibility(View.GONE);empty.setVisibility(View.VISIBLE);empty.setText("Відео ще не додано\n\nУтримуйте верхню панель, щоб вибрати папку Movies/ForestKiosk");}
  private void playCurrent(){if(siteOpen)return;if(playlist.isEmpty()){showEmpty();return;}video.setVisibility(View.VISIBLE);empty.setVisibility(View.GONE);Uri uri=playlist.get(index%playlist.size());video.setVideoURI(uri);video.setOnPreparedListener(mp->{if(!siteOpen){mp.setVolume(getPreferences(0).getBoolean("sound",false)?1f:0f,getPreferences(0).getBoolean("sound",false)?1f:0f);video.start();}});}
  private void playNext(){if(playlist.isEmpty()||siteOpen)return;index=(index+1)%playlist.size();lastPosition=0;playCurrent();}
  private void showSite(){if(siteOpen)return;siteOpen=true;lastPosition=video.getCurrentPosition();if(video.isPlaying())video.pause();videoLayer.setVisibility(View.GONE);web.setVisibility(View.VISIBLE);if(web.getUrl()==null||"about:blank".equals(web.getUrl()))web.loadUrl(SITE);lastTouch=System.currentTimeMillis();scheduleIdle();}
  private void showVideo(){if(!siteOpen)return;siteOpen=false;handler.removeCallbacks(idle);web.onPause();web.setVisibility(View.GONE);videoLayer.setVisibility(View.VISIBLE);web.loadUrl("about:blank");web.onResume();if(playlist.isEmpty())showEmpty();else if(lastPosition>0){video.seekTo(lastPosition);video.start();}else playCurrent();}
  private void scheduleIdle(){handler.removeCallbacks(idle);handler.postDelayed(idle,timeout);}
  @Override public boolean dispatchTouchEvent(MotionEvent e){if(siteOpen&&e.getAction()==MotionEvent.ACTION_DOWN){lastTouch=System.currentTimeMillis();scheduleIdle();}return super.dispatchTouchEvent(e);}
  private void chooseFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_FOLDER);}
  private void settings(){String[] opts={"Вибрати папку Movies/ForestKiosk","Час бездіяльності","Звук відео: "+(getPreferences(0).getBoolean("sound",false)?"увімкнений":"вимкнений"),"Оновити список відео","Відкрити погоду Sinoptik"};new AlertDialog.Builder(this).setTitle("Налаштування ForestKiosk").setItems(opts,(d,w)->{if(w==0)chooseFolder();if(w==1){String[] times={"30 секунд","1 хвилина","2 хвилини","5 хвилин"};int[] values={30000,60000,120000,300000};new AlertDialog.Builder(this).setTitle("Повернення до відео").setSingleChoiceItems(times,Math.max(0,Arrays.binarySearch(values,timeout)),(dialog,which)->{timeout=values[which];getPreferences(0).edit().putInt("timeout",timeout).apply();dialog.dismiss();}).setNegativeButton("Скасувати",null).show();}if(w==2){boolean on=!getPreferences(0).getBoolean("sound",false);getPreferences(0).edit().putBoolean("sound",on).apply();Toast.makeText(this,"Звук "+(on?"увімкнено":"вимкнено")+" (із наступного відео)",Toast.LENGTH_LONG).show();}if(w==3&&folder!=null)loadFolder(folder,true);if(w==4){showSite();web.loadUrl("https://ua.sinoptik.ua/");}}).show();}
  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PICK_FOLDER&&result==RESULT_OK&&data!=null){Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}folder=uri;getPreferences(0).edit().putString("folder",uri.toString()).apply();loadFolder(uri,true);index=0;if(!siteOpen)playCurrent();}}
  private void loadFolder(Uri tree,boolean notify){ArrayList<Uri> found=new ArrayList<>();try{Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));try(Cursor c=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(c!=null){ArrayList<String[]> entries=new ArrayList<>();while(c.moveToNext()){String mime=c.getString(1),name=c.getString(2);if(name!=null&&((mime!=null&&mime.startsWith("video/"))||name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mkv|webm|3gp|mov)$")))entries.add(new String[]{c.getString(0),name});}Collections.sort(entries,(a,b)->a[1].compareToIgnoreCase(b[1]));for(String[] e:entries)found.add(DocumentsContract.buildDocumentUriUsingTree(tree,e[0]));}}playlist.clear();playlist.addAll(found);if(index>=playlist.size())index=0;if(notify)Toast.makeText(this,"Знайдено відео: "+playlist.size(),Toast.LENGTH_SHORT).show();}catch(Exception ex){if(notify)Toast.makeText(this,"Не вдалося прочитати папку. Виберіть її знову.",Toast.LENGTH_LONG).show();}}
  private void loadWeather(){new Thread(()->{String w="Малин · погода недоступна",f="Перевірте інтернет";try{HttpURLConnection c=(HttpURLConnection)new URL(WEATHER_URL).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(10000);try(InputStream in=c.getInputStream();ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);JSONObject json=new JSONObject(b.toString("UTF-8"));JSONObject current=json.getJSONObject("current");double temp=current.getDouble("temperature_2m");int code=current.getInt("weather_code");w="Малин  "+symbol(code)+"  "+degrees(temp)+"°C";JSONObject hourly=json.getJSONObject("hourly");JSONArray times=hourly.getJSONArray("time"),temps=hourly.getJSONArray("temperature_2m");String today=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());StringBuilder sb=new StringBuilder();int[] hours={9,14,19};String[] names={"Ранок","День","Вечір"};for(int j=0;j<hours.length;j++){String sought=today+String.format(Locale.US,"T%02d:00",hours[j]);for(int i=0;i<times.length();i++)if(times.getString(i).equals(sought)){if(sb.length()>0)sb.append("   ·   ");sb.append(names[j]).append(" ").append(degrees(temps.getDouble(i))).append("°");break;}}f=sb.toString();}finally{c.disconnect();}}catch(Exception ignored){}final String ww=w,ff=f;runOnUiThread(()->{weatherLine=ww;forecastLine=ff;weather.setText(ww);forecast.setText(ff);});}).start();}
  private String degrees(double v){return (v>0?"+":"")+Math.round(v);}
  private String symbol(int code){if(code==0)return "☀";if(code<=3)return "⛅";if(code<=48)return "☁";if(code<=67)return "🌧";if(code<=77)return "❄";return "🌦";}
  @Override public void onBackPressed(){if(siteOpen){if(web.canGoBack()&&!"about:blank".equals(web.getUrl()))web.goBack();else showVideo();}else settings();}
  @Override protected void onResume(){super.onResume();immersive();if(!siteOpen&&video!=null&&!playlist.isEmpty()&&!video.isPlaying())video.start();}
  @Override protected void onPause(){if(video!=null&&video.isPlaying())video.pause();super.onPause();}
  @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(web!=null)web.destroy();super.onDestroy();}
}
```
