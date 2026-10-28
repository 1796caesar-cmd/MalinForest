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
import android.text.Html;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.net.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
  private static final String SITE="https://malinapk.net/";
  private static final String WEATHER_URL="https://api.open-meteo.com/v1/forecast?latitude=50.77&longitude=29.24&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code&timezone=Europe%2FKyiv&forecast_days=2";
  private static final String NEWS_URL="https://tsn.ua/rss/full.rss";
  private static final String HOLIDAY_URL="https://daytoday.ua/sogodni/";
  private static final int PICK_FOLDER=44;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final ArrayList<Uri> playlist=new ArrayList<>();
  private final ArrayList<String> holidays=new ArrayList<>();
  private FrameLayout root, content, videoLayer, bottomPanel, tickerViewport;
  private FitVideoView video;
  private WebView web;
  private TextView prompt, clock, date, weather, forecast, empty, tickerText;
  private TextSwitcher holidaySwitcher;
  private int index=0, timeout=120000, lastPosition=0, holidayIndex=0;
  private boolean siteOpen=false;
  private Uri folder;
  private long lastTouch;
  private String cachedNews="";
  private String holidayDate="";
  private final Runnable idle=()->{if(siteOpen&&System.currentTimeMillis()-lastTouch>=timeout)showVideo();};
  private final Runnable tick=new Runnable(){@Override public void run(){updateClock();handler.postDelayed(this,1000);}};
  private final Runnable refresh=new Runnable(){@Override public void run(){
    if(folder!=null)loadFolder(folder,false);
    loadWeather();loadNews();loadHolidays();
    handler.postDelayed(this,15*60*1000);
  }};
  private final Runnable rotateHoliday=new Runnable(){@Override public void run(){
    if(holidays.size()>1){holidayIndex=(holidayIndex+1)%holidays.size();holidaySwitcher.setText(holidays.get(holidayIndex));}
    handler.postDelayed(this,20000);
  }};
  private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
  private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
  private TextView label(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
  private String todayKey(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}

  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    immersive();
    timeout=getPreferences(0).getInt("timeout",120000);
    root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
    content=new FrameLayout(this);root.addView(content,new FrameLayout.LayoutParams(-1,-1));

    web=new WebView(this);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setMediaPlaybackRequiresUserGesture(false);
    web.getSettings().setAllowFileAccess(false);
    web.setWebViewClient(new WebViewClient());web.setWebChromeClient(new WebChromeClient());
    web.setVisibility(View.GONE);
    FrameLayout.LayoutParams webParams=new FrameLayout.LayoutParams(-1,-1);
    webParams.topMargin=dp(86);
    content.addView(web,webParams);

    videoLayer=new FrameLayout(this);videoLayer.setBackgroundColor(0xFF101A14);
    content.addView(videoLayer,new FrameLayout.LayoutParams(-1,-1));
    video=new FitVideoView(this);
    video.setStretchMode(getPreferences(0).getBoolean("stretch_video",false));
    videoLayer.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
    empty=label("",19,false);empty.setGravity(Gravity.CENTER);
    empty.setPadding(dp(20),dp(20),dp(20),dp(20));
    videoLayer.addView(empty,new FrameLayout.LayoutParams(-1,-1));

    // Підказка стоїть НАД нижньою панеллю і не перекриває новини.
    prompt=label("☝  ТОРКНІТЬСЯ ЕКРАНА, ЩОБ ДІЗНАТИСЯ БІЛЬШЕ",19,true);
    prompt.setGravity(Gravity.CENTER);prompt.setPadding(dp(20),dp(13),dp(20),dp(13));
    prompt.setBackground(bg(0xCC0A2017,14));
    FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
    pp.setMargins(dp(15),0,dp(15),dp(68));videoLayer.addView(prompt,pp);
    TranslateAnimation bounce=new TranslateAnimation(0,0,0,-dp(7));
    bounce.setDuration(650);bounce.setRepeatCount(Animation.INFINITE);
    bounce.setRepeatMode(Animation.REVERSE);bounce.setInterpolator(new AccelerateDecelerateInterpolator());
    prompt.startAnimation(bounce);

    // Нижня панель поверх повноекранного відео.
    bottomPanel=new FrameLayout(this);bottomPanel.setBackgroundColor(0x99142A20);
    bottomPanel.setPadding(dp(14),0,dp(14),0);
    FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,dp(48),Gravity.BOTTOM);
    root.addView(bottomPanel,bp);
    LinearLayout newsRow=new LinearLayout(this);newsRow.setGravity(Gravity.CENTER_VERTICAL);
    bottomPanel.addView(newsRow,new FrameLayout.LayoutParams(-1,-1));
    TextView newsBadge=label("ТСН  •  НОВИНИ",13,true);
    newsBadge.setSingleLine(true);newsBadge.setPadding(dp(10),dp(7),dp(10),dp(7));
    newsBadge.setBackground(bg(0xAA37684B,7));
    newsRow.addView(newsBadge,new LinearLayout.LayoutParams(-2,-2));
    tickerViewport=new FrameLayout(this);tickerViewport.setClipChildren(true);
    LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(48),1);
    tp.leftMargin=dp(12);newsRow.addView(tickerViewport,tp);
    tickerText=label("Завантажуємо новини ТСН…",17,false);
    tickerText.setSingleLine(true);tickerText.setGravity(Gravity.CENTER_VERTICAL);
    tickerViewport.addView(tickerText,new FrameLayout.LayoutParams(-2,-1));
    cachedNews=getPreferences(0).getString("news_cache","");
    if(!cachedNews.isEmpty())tickerText.setText(cachedNews);
    tickerViewport.post(this::startTicker);

    // Верхня панель: час / свята / погода.
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(dp(20),dp(8),dp(20),dp(8));top.setBackgroundColor(0x99142A20);
    LinearLayout timeBox=new LinearLayout(this);timeBox.setOrientation(LinearLayout.VERTICAL);
    top.addView(timeBox,new LinearLayout.LayoutParams(0,-2,2));
    clock=label("--:--",28,true);timeBox.addView(clock);
    date=label("",12,false);date.setTextColor(0xFFB8D2C1);timeBox.addView(date);

    LinearLayout holidayBox=new LinearLayout(this);holidayBox.setGravity(Gravity.CENTER);
    holidayBox.setOrientation(LinearLayout.VERTICAL);
    top.addView(holidayBox,new LinearLayout.LayoutParams(0,-1,4));
    TextView holidayLabel=label("СЬОГОДНІ ВІДЗНАЧАЮТЬ",11,false);
    holidayLabel.setTextColor(0xFFBED7C7);holidayLabel.setGravity(Gravity.CENTER);
    holidayBox.addView(holidayLabel);
    holidaySwitcher=new TextSwitcher(this);
    holidaySwitcher.setFactory(()->{
      TextView t=label("",15,true);t.setGravity(Gravity.CENTER);t.setMaxLines(2);
      t.setEllipsize(TextUtils.TruncateAt.END);return t;
    });
    TranslateAnimation out=new TranslateAnimation(0,0,0,-dp(55));
    out.setDuration(800);out.setInterpolator(new AccelerateDecelerateInterpolator());
    TranslateAnimation in=new TranslateAnimation(0,0,dp(55),0);
    in.setDuration(800);in.setInterpolator(new AccelerateDecelerateInterpolator());
    holidaySwitcher.setOutAnimation(out);holidaySwitcher.setInAnimation(in);
    holidayBox.addView(holidaySwitcher,new LinearLayout.LayoutParams(-1,dp(44)));
    holidaySwitcher.setCurrentText("Завантажуємо свята…");
    holidayDate=getPreferences(0).getString("holiday_date","");
    if(todayKey().equals(holidayDate)){
      try{
        JSONArray saved=new JSONArray(getPreferences(0).getString("holiday_cache","[]"));
        for(int i=0;i<saved.length();i++)holidays.add(saved.getString(i));
        if(!holidays.isEmpty())holidaySwitcher.setCurrentText(holidays.get(0));
      }catch(Exception ignored){}
    }

    LinearLayout weatherBox=new LinearLayout(this);weatherBox.setGravity(Gravity.RIGHT);
    weatherBox.setOrientation(LinearLayout.VERTICAL);
    top.addView(weatherBox,new LinearLayout.LayoutParams(0,-2,3));
    weather=label("Малин · погода…",17,true);weather.setGravity(Gravity.RIGHT);
    weather.setMaxLines(1);weatherBox.addView(weather);
    forecast=label("",13,false);forecast.setTextColor(0xFFBED7C7);
    forecast.setGravity(Gravity.RIGHT);forecast.setMaxLines(2);weatherBox.addView(forecast);
    top.setOnLongClickListener(v->{settings();return true;});
    root.addView(top,new FrameLayout.LayoutParams(-1,dp(86),Gravity.TOP));

    video.setOnCompletionListener(mp->playNext());
    video.setOnErrorListener((mp,what,extra)->{handler.postDelayed(this::playNext,500);return true;});
    videoLayer.setOnClickListener(v->showSite());video.setOnClickListener(v->showSite());
    prompt.setOnClickListener(v->showSite());empty.setOnClickListener(v->showSite());
    prompt.setOnLongClickListener(v->{settings();return true;});
    String saved=getPreferences(0).getString("folder",null);
    if(saved!=null){folder=Uri.parse(saved);loadFolder(folder,true);}
    if(playlist.isEmpty())showEmpty();else playCurrent();
    handler.post(tick);handler.post(refresh);handler.postDelayed(rotateHoliday,20000);
  }

  private void startTicker(){
    if(tickerViewport==null||tickerText==null)return;
    tickerText.clearAnimation();
    int width=tickerViewport.getWidth();
    if(width<=0){tickerViewport.postDelayed(this::startTicker,250);return;}
    String content=tickerText.getText().toString();
    int textWidth=(int)Math.ceil(tickerText.getPaint().measureText(content))+dp(24);
    FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)tickerText.getLayoutParams();
    p.width=textWidth;tickerText.setLayoutParams(p);
    TranslateAnimation move=new TranslateAnimation(width,-textWidth,0,0);
    move.setDuration(Math.max(6000,(long)((width+textWidth)*1000.0/dp(70))));
    move.setRepeatCount(Animation.INFINITE);move.setInterpolator(new LinearInterpolator());
    tickerText.startAnimation(move);
  }

  private static class FitVideoView extends VideoView{
    private int videoWidth=0,videoHeight=0;private boolean stretch=false;
    FitVideoView(Context context){super(context);}
    void setVideoDimensions(int w,int h){videoWidth=w;videoHeight=h;requestLayout();}
    void setStretchMode(boolean enabled){stretch=enabled;requestLayout();invalidate();}
    @Override protected void onMeasure(int wSpec,int hSpec){
      int w=MeasureSpec.getSize(wSpec),h=MeasureSpec.getSize(hSpec);
      if(stretch||videoWidth<=0||videoHeight<=0||w<=0||h<=0){setMeasuredDimension(w,h);return;}
      double scale=Math.min((double)w/videoWidth,(double)h/videoHeight);
      setMeasuredDimension(Math.max(1,(int)Math.round(videoWidth*scale)),Math.max(1,(int)Math.round(videoHeight*scale)));
    }
  }
  private void immersive(){getWindow().getDecorView().setSystemUiVisibility(5894|1024|512|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
  private void updateClock(){
    Date now=new Date();clock.setText(new SimpleDateFormat("HH:mm",new Locale("uk","UA")).format(now));
    date.setText(new SimpleDateFormat("EEEE, d MMMM",new Locale("uk","UA")).format(now));
    if(!todayKey().equals(holidayDate)){
      holidayDate=todayKey();holidays.clear();holidayIndex=0;
      holidaySwitcher.setCurrentText("Завантажуємо свята…");loadHolidays();
    }
  }
  private void showEmpty(){video.setVisibility(View.GONE);empty.setVisibility(View.VISIBLE);
    empty.setText("Відео ще не додано\n\nУтримуйте верхню панель, щоб вибрати папку Movies/ForestKiosk");}
  private void playCurrent(){
    if(siteOpen)return;if(playlist.isEmpty()){showEmpty();return;}
    video.setVisibility(View.VISIBLE);empty.setVisibility(View.GONE);
    video.setVideoDimensions(0,0);video.setVideoURI(playlist.get(index%playlist.size()));
    video.setOnPreparedListener(mp->{video.setVideoDimensions(mp.getVideoWidth(),mp.getVideoHeight());
      if(!siteOpen){boolean sound=getPreferences(0).getBoolean("sound",false);
        mp.setVolume(sound?1f:0f,sound?1f:0f);video.start();}});
  }
  private void playNext(){if(playlist.isEmpty()||siteOpen)return;index=(index+1)%playlist.size();lastPosition=0;playCurrent();}
  private void showSite(){
    if(siteOpen)return;siteOpen=true;lastPosition=video.getCurrentPosition();
    if(video.isPlaying())video.pause();videoLayer.setVisibility(View.GONE);
    bottomPanel.setVisibility(View.GONE);web.setVisibility(View.VISIBLE);
    // Верхня панель у режимі сайту непрозора.
    LinearLayout top=(LinearLayout)root.getChildAt(root.getChildCount()-1);
    top.setBackgroundColor(0xFF142A20);
    if(web.getUrl()==null||"about:blank".equals(web.getUrl()))web.loadUrl(SITE);
    lastTouch=System.currentTimeMillis();scheduleIdle();
  }
  private void showVideo(){
    if(!siteOpen)return;siteOpen=false;handler.removeCallbacks(idle);
    web.onPause();web.setVisibility(View.GONE);videoLayer.setVisibility(View.VISIBLE);
    bottomPanel.setVisibility(View.VISIBLE);
    LinearLayout top=(LinearLayout)root.getChildAt(root.getChildCount()-1);
    top.setBackgroundColor(0x99142A20);
    web.loadUrl("about:blank");web.onResume();
    if(playlist.isEmpty())showEmpty();else if(lastPosition>0){video.seekTo(lastPosition);video.start();}else playCurrent();
  }
  private void scheduleIdle(){handler.removeCallbacks(idle);handler.postDelayed(idle,timeout);}
  @Override public boolean dispatchTouchEvent(android.view.MotionEvent e){
    if(siteOpen&&e.getAction()==MotionEvent.ACTION_DOWN){lastTouch=System.currentTimeMillis();scheduleIdle();}
    return super.dispatchTouchEvent(e);
  }
  private void chooseFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    startActivityForResult(i,PICK_FOLDER);}
  private void settings(){
    boolean sound=getPreferences(0).getBoolean("sound",false),stretch=getPreferences(0).getBoolean("stretch_video",false);
    String[] opts={"Вибрати папку Movies/ForestKiosk","Час бездіяльності",
      "Звук відео: "+(sound?"увімкнений":"вимкнений"),"Оновити список відео",
      "Розтягувати відео: "+(stretch?"увімкнено":"вимкнено"),"Оновити свята та новини"};
    new AlertDialog.Builder(this).setTitle("Налаштування MalinForest").setItems(opts,(d,w)->{
      if(w==0)chooseFolder();
      if(w==1){String[] times={"30 секунд","1 хвилина","2 хвилини","5 хвилин"};int[] values={30000,60000,120000,300000};
        int selected=2;for(int i=0;i<values.length;i++)if(values[i]==timeout)selected=i;
        new AlertDialog.Builder(this).setTitle("Повернення до відео")
          .setSingleChoiceItems(times,selected,(dialog,which)->{
            timeout=values[which];getPreferences(0).edit().putInt("timeout",timeout).apply();dialog.dismiss();
          }).setNegativeButton("Скасувати",null).show();}
      if(w==2){boolean on=!getPreferences(0).getBoolean("sound",false);
        getPreferences(0).edit().putBoolean("sound",on).apply();
        Toast.makeText(this,"Звук "+(on?"увімкнено":"вимкнено")+" (із наступного відео)",Toast.LENGTH_LONG).show();}
      if(w==3&&folder!=null)loadFolder(folder,true);
      if(w==4){boolean enabled=!getPreferences(0).getBoolean("stretch_video",false);
        getPreferences(0).edit().putBoolean("stretch_video",enabled).apply();video.setStretchMode(enabled);
        Toast.makeText(this,enabled?"Розтягування увімкнено":"Збереження пропорцій увімкнено",Toast.LENGTH_SHORT).show();}
      if(w==5){loadHolidays();loadNews();Toast.makeText(this,"Оновлюємо дані…",Toast.LENGTH_SHORT).show();}
    }).show();
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request==PICK_FOLDER&&result==RESULT_OK&&data!=null){
      Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
      folder=uri;getPreferences(0).edit().putString("folder",uri.toString()).apply();loadFolder(uri,true);index=0;
      if(!siteOpen)playCurrent();
    }
  }
  private void loadFolder(Uri tree,boolean notify){
    ArrayList<Uri> found=new ArrayList<>();
    try{
      Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
      try(Cursor c=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
        if(c!=null){ArrayList<String[]> entries=new ArrayList<>();
          while(c.moveToNext()){String mime=c.getString(1),name=c.getString(2);
            if(name!=null&&((mime!=null&&mime.startsWith("video/"))||name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mkv|webm|3gp|mov)$")))
              entries.add(new String[]{c.getString(0),name});}
          Collections.sort(entries,(a,b)->a[1].compareToIgnoreCase(b[1]));
          for(String[] e:entries)found.add(DocumentsContract.buildDocumentUriUsingTree(tree,e[0]));
        }
      }
      playlist.clear();playlist.addAll(found);if(index>=playlist.size())index=0;
      if(notify)Toast.makeText(this,"Знайдено відео: "+playlist.size(),Toast.LENGTH_SHORT).show();
    }catch(Exception ex){if(notify)Toast.makeText(this,"Не вдалося прочитати папку. Виберіть її знову.",Toast.LENGTH_LONG).show();}
  }
  private String fetch(String url) throws Exception{
    HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
    c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(true);
    c.setRequestProperty("User-Agent","Mozilla/5.0 (Android; MalinForest Kiosk)");
    try(InputStream in=c.getInputStream();ByteArrayOutputStream b=new ByteArrayOutputStream()){
      byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){b.write(buf,0,n);if(b.size()>2500000)break;}
      return b.toString("UTF-8");
    }finally{c.disconnect();}
  }
  private void loadWeather(){new Thread(()->{
    String w="Малин · погода недоступна",f="Перевірте інтернет";
    try{
      JSONObject json=new JSONObject(fetch(WEATHER_URL));JSONObject current=json.getJSONObject("current");
      double temp=current.getDouble("temperature_2m");int code=current.getInt("weather_code");
      w="Малин  "+symbol(code)+"  "+degrees(temp)+"°C";
      JSONObject hourly=json.getJSONObject("hourly");JSONArray times=hourly.getJSONArray("time"),temps=hourly.getJSONArray("temperature_2m");
      String today=todayKey();StringBuilder sb=new StringBuilder();int[] hours={9,14,19};String[] names={"Ранок","День","Вечір"};
      for(int j=0;j<hours.length;j++){String sought=today+String.format(Locale.US,"T%02d:00",hours[j]);
        for(int i=0;i<times.length();i++)if(times.getString(i).equals(sought)){
          if(sb.length()>0)sb.append("   ·   ");sb.append(names[j]).append(" ").append(degrees(temps.getDouble(i))).append("°");break;
        }
      }f=sb.toString();
    }catch(Exception ignored){}
    final String ww=w,ff=f;runOnUiThread(()->{weather.setText(ww);forecast.setText(ff);});
  }).start();}
  private void loadNews(){new Thread(()->{
    try{
      String xml=fetch(NEWS_URL);
      XmlPullParser p=XmlPullParserFactory.newInstance().newPullParser();p.setInput(new StringReader(xml));
      ArrayList<String> titles=new ArrayList<>();boolean inItem=false,inTitle=false;
      int event=p.getEventType();
      while(event!=XmlPullParser.END_DOCUMENT&&titles.size()<18){
        if(event==XmlPullParser.START_TAG){
          String name=p.getName();if("item".equalsIgnoreCase(name))inItem=true;
          if(inItem&&"title".equalsIgnoreCase(name))inTitle=true;
        }else if(event==XmlPullParser.TEXT&&inItem&&inTitle){
          String s=p.getText().trim();if(!s.isEmpty()&&!titles.contains(s))titles.add(s);
        }else if(event==XmlPullParser.END_TAG){
          if("title".equalsIgnoreCase(p.getName()))inTitle=false;
          if("item".equalsIgnoreCase(p.getName()))inItem=false;
        }
        event=p.next();
      }
      if(!titles.isEmpty()){
        String joined=TextUtils.join("     ●     ",titles)+"     ●     ";
        getPreferences(0).edit().putString("news_cache",joined).apply();
        runOnUiThread(()->{cachedNews=joined;tickerText.setText(joined);tickerViewport.post(this::startTicker);});
      }
    }catch(Exception ignored){
      if(cachedNews.isEmpty())runOnUiThread(()->tickerText.setText("Новини тимчасово недоступні"));
    }
  }).start();}
  private String plain(String s){
    String cleaned=s.replaceAll("(?is)<script[^>]*>.*?</script>"," ").replaceAll("(?is)<style[^>]*>.*?</style>"," ");
    cleaned=cleaned.replaceAll("(?is)<[^>]+>"," ");
    return Html.fromHtml(cleaned,Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+"," ").trim();
  }
  private void loadHolidays(){new Thread(()->{
    try{
      String html=fetch(HOLIDAY_URL);
      // DAY TODAY is an HTML page, not a documented API. Extract holiday headings/links.
      // If the site's layout changes, keep the previously saved data for this date.
      LinkedHashSet<String> result=new LinkedHashSet<>();
      Pattern headings=Pattern.compile("(?is)<(?:h2|h3|h4|a)\\b[^>]*>(.*?)</(?:h2|h3|h4|a)>");
      Matcher m=headings.matcher(html);
      while(m.find()&&result.size()<24){
        String s=plain(m.group(1));
        if(s.length()>=12&&s.length()<=105&&
          (s.matches("(?iu)^(?:Всесвітній|Міжнародний|Всеукраїнський|Національний|Європейський|День|Річниця|Свято|Доба|Тиждень)\\b.*")||
           s.matches("(?iu)^.*\\bдень\\b.*"))&&
           !s.toLowerCase(Locale.ROOT).contains("календар")&&
           !s.toLowerCase(Locale.ROOT).contains("читати"))result.add(s);
      }
      if(!result.isEmpty()){
        JSONArray arr=new JSONArray();for(String s:result)arr.put(s);
        String key=todayKey();getPreferences(0).edit().putString("holiday_date",key)
          .putString("holiday_cache",arr.toString()).apply();
        runOnUiThread(()->{
          if(!todayKey().equals(key))return;
          holidays.clear();holidays.addAll(result);holidayIndex=0;holidayDate=key;
          holidaySwitcher.setCurrentText(holidays.get(0));
        });
      }else if(holidays.isEmpty())runOnUiThread(()->holidaySwitcher.setCurrentText("Свята сьогодні не знайдено"));
    }catch(Exception ignored){
      if(holidays.isEmpty())runOnUiThread(()->holidaySwitcher.setCurrentText("Свята тимчасово недоступні"));
    }
  }).start();}
  private String degrees(double v){return (v>0?"+":"")+Math.round(v);}
  private String symbol(int code){if(code==0)return "☀";if(code<=3)return "⛅";if(code<=48)return "☁";if(code<=67)return "🌧";if(code<=77)return "❄";return "🌦";}
  @Override public void onBackPressed(){if(siteOpen){if(web.canGoBack()&&!"about:blank".equals(web.getUrl()))web.goBack();else showVideo();}else settings();}
  @Override protected void onResume(){super.onResume();immersive();
    if(!siteOpen&&video!=null&&!playlist.isEmpty()&&!video.isPlaying())video.start();}
  @Override protected void onPause(){if(video!=null&&video.isPlaying())video.pause();super.onPause();}
  @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);
    if(tickerText!=null)tickerText.clearAnimation();if(web!=null)web.destroy();super.onDestroy();}
}
