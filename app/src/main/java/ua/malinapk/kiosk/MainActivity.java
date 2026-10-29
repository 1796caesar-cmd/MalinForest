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
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.MediaPlayer;
import android.text.InputType;

public class MainActivity extends Activity {
  private static final String SITE="https://malinapk.net/";
  private static final String WEATHER_URL="https://api.open-meteo.com/v1/forecast?latitude=50.77&longitude=29.24&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code&timezone=Europe%2FKyiv&forecast_days=2";
  private static final String[] NEWS_URLS={"https://www.openforest.org.ua/feed/", "https://www.openforest.org.ua/section/news/feed/"};
  private static final String FOREST_HOME="https://www.openforest.org.ua/";
  private static final String FOREST_NEWS="https://www.openforest.org.ua/section/news/";
  private static final int NEWS_CACHE_VERSION=9;
  private static final String HOLIDAY_URL="https://daytoday.ua/sogodni/";
  private static final int HOLIDAY_CACHE_VERSION=2;
  private static final int PICK_FOLDER=44, PICK_DATA=45;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final ArrayList<Uri> playlist=new ArrayList<>();
  private final ArrayList<String> holidays=new ArrayList<>();
  private FrameLayout root, content, videoLayer, bottomPanel, tickerViewport, gameOverlay;
  private LinearLayout topPanel;
  private TextView gamesButton, gameTimerLabel;
  private int gameMode=-1, gameLives=3, gameIndex=0;
  private long gameLastTouch=0;
  private boolean gameFinished=false, gameBusy=false;
  private final ArrayList<String> gameQueue=new ArrayList<>(), remainingTrees=new ArrayList<>();
  private final Random gameRandom=new Random();
  private final Runnable gameIdle=new Runnable(){@Override public void run(){if(gameOverlay==null)return;long left=gameTimeoutMs()-(System.currentTimeMillis()-gameLastTouch);if(left<=0){closeGames();return;}if(gameTimerLabel!=null)gameTimerLabel.setText("◷ "+String.format(Locale.US,"%02d:%02d",(left+999)/1000/60,(left+999)/1000%60));handler.postDelayed(this,500);}};
  private FitVideoView video;
  private WebView web;
  private TextView prompt, clock, date, weather, forecast, empty, tickerText;
  private TextSwitcher holidaySwitcher;
  private int index=0, timeout=120000, lastPosition=0, holidayIndex=0;
  private boolean siteOpen=false;
  private Uri folder;
  private long lastTouch;
  private String cachedNews="";
  private String pendingNews=null;
  private String holidayDate="";
  private Uri dataFolder;
  private final LinkedHashMap<String,ArrayList<String>> facts=new LinkedHashMap<>();
  private final HashMap<String,Integer> factPositions=new HashMap<>();
  private final HashMap<String,ArrayList<Integer>> factOrders=new HashMap<>();
  private final Random factRandom=new Random();
  private final ArrayList<Person> people=new ArrayList<>();
  private final ArrayList<String> topItems=new ArrayList<>();
  private TextView newsBadge;
  private TextSwitcher holidayLabel;
  private String currentTopSignature="";
  private int topCursor=0;
  private FrameLayout birthdayOverlay;
  private boolean birthdayShowing=false;
  private MediaPlayer birthdayMusic;
  private String lastBirthdaySlot="";
  private final ArrayList<Person> birthdayQueue=new ArrayList<>();
  private int birthdayQueueIndex=0, categoryIndex=0;
  private long categoryStarted=0;
  private String currentCategory="";
  private final ArrayList<String> activeCategories=new ArrayList<>();
  private boolean tickerRunning=false;
  private static class Person {String name,position,department;int day,month,year;Person(String n,String pos,String dep,int d,int m,int y){name=n;position=pos;department=dep;day=d;month=m;year=y;}}
  private boolean isManagement(Person p){return p.department!=null&&p.department.trim().replaceAll("\\s+"," ").equalsIgnoreCase("Апарату управління");}
  private String birthdayDetail(Person p){String pos=p.position==null?"":p.position.trim(),dep=p.department==null?"":p.department.trim();return pos.isEmpty()?dep:(dep.isEmpty()?pos:pos+" • "+dep);}
  private String birthdayEntry(Person p,boolean monthly){String first=(monthly?String.format(new Locale("uk","UA"),"%02d.%02d ● ",p.day,p.month):"")+p.name;String detail=birthdayDetail(p);return detail.isEmpty()?first:first+"\n"+detail;}
  private boolean enabled(String key){return getPreferences(0).getBoolean(key,true);}
  private boolean anyTicker(){return enabled("news")||enabled("facts");}

  private final Runnable idle=()->{if(siteOpen&&System.currentTimeMillis()-lastTouch>=timeout)showVideo();};
  private final Runnable tick=new Runnable(){@Override public void run(){updateClock();handler.postDelayed(this,1000);}};
  private final Runnable refresh=new Runnable(){@Override public void run(){
    if(folder!=null)loadFolder(folder,false);
    loadWeather();loadNews();loadHolidays();if(dataFolder!=null)loadData();
    handler.postDelayed(this,15*60*1000);
  }};
  private final Runnable rotateHoliday=new Runnable(){@Override public void run(){
    updateTopRotation();
    handler.postDelayed(this,topIntervalSeconds()*1000L);
  }};
  private int topIntervalSeconds(){int n=getPreferences(0).getInt("top_interval_seconds",10);return n>=5&&n<=30&&n%5==0?n:10;}
  private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
  private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
  private TextView label(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
  private String todayKey(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}

  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    immersive();
    timeout=getPreferences(0).getInt("timeout",30000);
    root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
    content=new FrameLayout(this);root.addView(content,new FrameLayout.LayoutParams(-1,-1));

    web=new WebView(this);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setMediaPlaybackRequiresUserGesture(false);
    web.getSettings().setAllowFileAccess(false);
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view, String url){
        return blockSocial(url);
      }
      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request){
        return blockSocial(request.getUrl().toString());
      }
    });
    web.setWebChromeClient(new WebChromeClient());
    web.setVisibility(View.GONE);
    FrameLayout.LayoutParams webParams=new FrameLayout.LayoutParams(-1,-1);
    webParams.topMargin=dp(86);
    content.addView(web,webParams);

    videoLayer=new FrameLayout(this);videoLayer.setBackgroundColor(0xFF101A14);
    content.addView(videoLayer,new FrameLayout.LayoutParams(-1,-1));
    video=new FitVideoView(this);
    video.setStretchMode(getPreferences(0).getBoolean("stretch_video",true));
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
    newsBadge=label("ЛІСОВІ НОВИНИ",13,true);
    newsBadge.setSingleLine(true);newsBadge.setPadding(dp(10),dp(7),dp(10),dp(7));
    newsBadge.setBackground(bg(0xAA37684B,7));
    newsRow.addView(newsBadge,new LinearLayout.LayoutParams(-2,-2));
    tickerViewport=new FrameLayout(this);tickerViewport.setClipChildren(true);
    LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(48),1);
    tp.leftMargin=dp(12);newsRow.addView(tickerViewport,tp);
    tickerText=label("Завантажуємо лісові новини…",17,false);
    tickerText.setSingleLine(true);tickerText.setGravity(Gravity.CENTER_VERTICAL);
    tickerViewport.addView(tickerText,new FrameLayout.LayoutParams(-2,-1));
    cachedNews=getPreferences(0).getInt("news_cache_version",0)==NEWS_CACHE_VERSION
      ?getPreferences(0).getString("news_cache",""):"";
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
    holidayLabel=new TextSwitcher(this);
    holidayLabel.setFactory(()->{TextView t=label("",11,false);t.setTextColor(0xFFBED7C7);t.setGravity(Gravity.CENTER);t.setSingleLine(true);return t;});
    holidayBox.addView(holidayLabel);
    holidayLabel.setCurrentText("СЬОГОДНІ ВІДЗНАЧАЮТЬ");
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
    TranslateAnimation titleOut=new TranslateAnimation(0,0,0,-dp(20));titleOut.setDuration(800);titleOut.setInterpolator(new AccelerateDecelerateInterpolator());
    TranslateAnimation titleIn=new TranslateAnimation(0,0,dp(20),0);titleIn.setDuration(800);titleIn.setInterpolator(new AccelerateDecelerateInterpolator());
    holidayLabel.setOutAnimation(titleOut);holidayLabel.setInAnimation(titleIn);
    holidayBox.addView(holidaySwitcher,new LinearLayout.LayoutParams(-1,dp(44)));
    holidaySwitcher.setCurrentText("Завантажуємо свята…");
    holidayDate=getPreferences(0).getString("holiday_date","");
    if(todayKey().equals(holidayDate) && getPreferences(0).getInt("holiday_cache_version",0)==HOLIDAY_CACHE_VERSION){
      try{
        JSONArray saved=new JSONArray(getPreferences(0).getString("holiday_cache","[]"));
        for(int i=0;i<saved.length();i++)holidays.add(saved.getString(i));
        holidays.removeIf(this::excludeHoliday);
        if(!holidays.isEmpty())updateTopRotation();
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
    topPanel=top;root.addView(top,new FrameLayout.LayoutParams(-1,dp(86),Gravity.TOP));

    video.setOnCompletionListener(mp->playNext());
    video.setOnErrorListener((mp,what,extra)->{handler.postDelayed(this::playNext,500);return true;});
    videoLayer.setOnClickListener(v->showSite());video.setOnClickListener(v->showSite());
    prompt.setOnClickListener(v->showSite());empty.setOnClickListener(v->showSite());
    prompt.setOnLongClickListener(v->{settings();return true;});
    gamesButton=label("🎮 ІНТЕРАКТИВНІ ІГРИ",17,true);gamesButton.setGravity(Gravity.CENTER);
    gamesButton.setPadding(dp(16),dp(12),dp(16),dp(12));gamesButton.setBackground(bg(0xEE226A3B,14));
    FrameLayout.LayoutParams gb=new FrameLayout.LayoutParams(-2,-2,Gravity.RIGHT|Gravity.BOTTOM);gb.setMargins(0,0,dp(16),dp(65));
    content.addView(gamesButton,gb);gamesButton.setOnClickListener(v->openGameMenu());
    String saved=getPreferences(0).getString("folder",null);
    if(saved!=null){folder=Uri.parse(saved);loadFolder(folder,true);}
    if(playlist.isEmpty())showEmpty();else playCurrent();
    String dataSaved=getPreferences(0).getString("data_folder",null);
    if(dataSaved!=null){dataFolder=Uri.parse(dataSaved);loadData();}
    updateVisibility();
    handler.post(tick);handler.post(refresh);handler.postDelayed(rotateHoliday,topIntervalSeconds()*1000L);
  }

  // Забороняємо відкривати соцмережі, включно з їхніми deep-link адресами.
  private boolean blockSocial(String address){
    if(address==null)return false;
    String lower=address.toLowerCase(Locale.ROOT);
    try{
      Uri uri=Uri.parse(address);
      String host=uri.getHost();
      if(host!=null){
        host=host.toLowerCase(Locale.ROOT);
        String[] domains={"instagram.com","facebook.com","fb.com","fb.watch",
          "youtube.com","youtu.be","m.youtube.com","youtube-nocookie.com",
          "threads.net","messenger.com"};
        for(String domain:domains)
          if(host.equals(domain)||host.endsWith("."+domain))return true;
      }
    }catch(Exception ignored){}
    return lower.startsWith("instagram:")||lower.startsWith("fb:")||
      lower.startsWith("fb-messenger:")||lower.startsWith("youtube:")||
      lower.startsWith("intent://instagram")||lower.startsWith("intent://youtube")||
      lower.contains("package=com.instagram.android")||
      lower.contains("package=com.facebook.katana")||
      lower.contains("package=com.google.android.youtube");
  }

  // Безперервна стрічка: наступний запис іде відразу після попереднього через ●.
  // Прогрес фіксуємо лише після того, як повідомлення повністю залишило екран.
  private final ArrayList<String> tickerParts=new ArrayList<>();
  private ArrayList<String> tickerItems=new ArrayList<>();
  private String tickerCategory="";
  private boolean tickerIsFact=false;
  private int tickerNextPosition=0;
  private float tickerX=0f;
  private long tickerLastFrame=0L;
  private static final String TICKER_SEPARATOR="     ●     ";
  private final Runnable tickerFrame=new Runnable(){@Override public void run(){
    if(!tickerRunning||tickerText==null||tickerViewport==null)return;
    long now=android.os.SystemClock.uptimeMillis();
    float elapsed=Math.min(0.1f,Math.max(0f,(now-tickerLastFrame)/1000f));tickerLastFrame=now;
    tickerX-=dp(85)*elapsed;
    if(!tickerParts.isEmpty()){
      String first=tickerParts.get(0)+TICKER_SEPARATOR;
      float firstWidth=tickerText.getPaint().measureText(first);
      if(tickerX+firstWidth<=0){
        tickerX+=firstWidth;
        tickerParts.remove(0);
        int completed=tickerNextPosition-tickerParts.size();
        int next=((completed%tickerItems.size())+tickerItems.size())%tickerItems.size();
        factPositions.put(tickerCategory,next);
        getPreferences(0).edit().putInt("ticker_pos_"+tickerCategory,next).apply();
        if(tickerIsFact&&next==0)shuffleFactOrder(tickerCategory,tickerItems);
        if(now-categoryStarted>=300000||next==0){
          tickerRunning=false;nextCategory();return;
        }
        addTickerPart();
        updateTickerStrip();
      }
    }
    tickerText.setTranslationX(tickerX);
    handler.postDelayed(this,16);
  }};
  private void addTickerPart(){
    if(tickerItems.isEmpty())return;
    int pos=tickerNextPosition%tickerItems.size();
    int idx=tickerIsFact?factOrder(tickerCategory,tickerItems).get(pos):pos;
    tickerParts.add(tickerItems.get(idx));tickerNextPosition++;
  }
  private void updateTickerStrip(){
    StringBuilder b=new StringBuilder();
    for(String part:tickerParts)b.append(part).append(TICKER_SEPARATOR);
    String message=b.toString();tickerText.setText(message);
    int width=(int)Math.ceil(tickerText.getPaint().measureText(message))+dp(16);
    FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)tickerText.getLayoutParams();
    lp.width=Math.max(1,width);tickerText.setLayoutParams(lp);
  }
  private void startTicker(){
    if(tickerViewport==null||tickerText==null)return;
    handler.removeCallbacks(tickerFrame);tickerText.clearAnimation();tickerRunning=false;
    tickerText.setTranslationX(0);
    if(!anyTicker())return;
    if(tickerViewport.getWidth()<=0){tickerViewport.postDelayed(this::startTicker,250);return;}
    rebuildCategories();
    if(activeCategories.isEmpty()){tickerText.setText("Немає повідомлень");return;}
    if(categoryIndex>=activeCategories.size())categoryIndex=0;
    String category=activeCategories.get(categoryIndex);
    if(!category.equals(currentCategory)){currentCategory=category;categoryStarted=android.os.SystemClock.uptimeMillis();}
    ArrayList<String> items=category.equals("Лісові новини")?newsItems():facts.get(category);
    if(items==null||items.isEmpty()){nextCategory();return;}
    tickerCategory=category;tickerItems=new ArrayList<>(items);
    tickerIsFact=!category.equals("Лісові новини");
    tickerNextPosition=((factPositions.containsKey(category)?factPositions.get(category):0)%items.size()+items.size())%items.size();
    tickerParts.clear();
    if(tickerIsFact)factOrder(category,tickerItems);
    for(int i=0;i<Math.min(4,items.size());i++)addTickerPart();
    newsBadge.setText(category.toUpperCase(new Locale("uk","UA")));
    updateTickerStrip();tickerX=tickerViewport.getWidth();tickerText.setTranslationX(tickerX);
    tickerLastFrame=android.os.SystemClock.uptimeMillis();tickerRunning=true;
    handler.post(tickerFrame);
  }
  // Для кожної категорії окрема випадкова перестановка без повторень.
  // Порядок та позиція зберігаються після закриття додатка.
  private ArrayList<Integer> factOrder(String category,ArrayList<String> items){
    ArrayList<Integer> existing=factOrders.get(category);
    if(existing!=null&&existing.size()==items.size())return existing;
    String key="fact_shuffle_"+category;
    String saved=getPreferences(0).getString(key,"");
    String fingerprint=Integer.toString(items.toString().hashCode());
    ArrayList<Integer> order=new ArrayList<>();
    try{
      String[] parts=saved.split("\\|");
      if(parts.length==2&&fingerprint.equals(parts[0])){
        boolean[] used=new boolean[items.size()];
        for(String n:parts[1].split(",")){
          int i=Integer.parseInt(n);
          if(i<0||i>=items.size()||used[i])throw new IllegalArgumentException();
          used[i]=true;order.add(i);
        }
        if(order.size()!=items.size())order.clear();
      }
    }catch(Exception ignored){order.clear();}
    if(order.size()!=items.size()){
      order.clear();for(int i=0;i<items.size();i++)order.add(i);
      Collections.shuffle(order,factRandom);
      factPositions.put(category,0);
      getPreferences(0).edit().putInt("ticker_pos_"+category,0).apply();
      persistFactOrder(category,items,order);
    }
    factOrders.put(category,order);return order;
  }
  private void shuffleFactOrder(String category,ArrayList<String> items){
    ArrayList<Integer> order=new ArrayList<>(factOrder(category,items));
    Collections.shuffle(order,factRandom);
    // Не повторювати останній факт попереднього кола одразу на старті нового.
    if(order.size()>1){
      ArrayList<Integer> old=factOrders.get(category);
      int previousLast=old.get(old.size()-1);
      if(order.get(0)==previousLast)Collections.swap(order,0,1);
    }
    factOrders.put(category,order);persistFactOrder(category,items,order);
  }
  private void persistFactOrder(String category,ArrayList<String> items,ArrayList<Integer> order){
    StringBuilder b=new StringBuilder(Integer.toString(items.toString().hashCode())).append('|');
    for(int i=0;i<order.size();i++){if(i>0)b.append(',');b.append(order.get(i));}
    getPreferences(0).edit().putString("fact_shuffle_"+category,b.toString()).apply();
  }
  private ArrayList<String> newsItems(){
    ArrayList<String> result=new ArrayList<>();
    for(String part:cachedNews.split("\\s*[●•]\\s*")){String t=part.trim();if(!t.isEmpty())result.add(t);}
    return result;
  }
  private void nextCategory(){categoryIndex=(categoryIndex+1)%Math.max(1,activeCategories.size());currentCategory="";tickerViewport.post(this::startTicker);}
  private void rebuildCategories(){
    activeCategories.clear();if(enabled("news")&&!cachedNews.trim().isEmpty())activeCategories.add("Лісові новини");
    if(enabled("facts"))for(Map.Entry<String,ArrayList<String>> e:facts.entrySet())if(!e.getValue().isEmpty())activeCategories.add(e.getKey());
  }
  // One entry per tick, grouped in the required order. Today's birthdays are excluded from the monthly group.
  private void updateTopRotation(){
    ArrayList<String> titles=new ArrayList<>(), entries=new ArrayList<>();
    if(enabled("holidays"))for(String h:holidays){if(h!=null&&!h.trim().isEmpty()){
      titles.add("СЬОГОДНІ ВІДЗНАЧАЮТЬ");entries.add(h.trim());}}
    if(enabled("birthdays")){
      Calendar c=Calendar.getInstance();int month=c.get(Calendar.MONTH)+1,day=c.get(Calendar.DAY_OF_MONTH);
      ArrayList<Person> sorted=new ArrayList<>(people);
      Collections.sort(sorted,(a,b)->{int d=Integer.compare(a.day,b.day);return d!=0?d:a.name.compareToIgnoreCase(b.name);});
      for(Person person:sorted)if(person.month==month&&person.day!=day){
        titles.add("ІМЕНИННИКИ МІСЯЦЯ");
        entries.add(birthdayEntry(person,true));
      }
      for(Person person:sorted)if(person.month==month&&person.day==day){
        titles.add("ІМЕНИННИКИ ДНЯ");entries.add(birthdayEntry(person,false));
      }
    }
    if(entries.isEmpty()){
      currentTopSignature="";topCursor=0;
      holidayLabel.setVisibility(View.GONE);holidaySwitcher.setVisibility(View.GONE);return;
    }
    holidayLabel.setVisibility(View.VISIBLE);holidaySwitcher.setVisibility(View.VISIBLE);
    String signature=TextUtils.join("\u001f",titles)+"\u001e"+TextUtils.join("\u001f",entries);
    if(!signature.equals(currentTopSignature)){currentTopSignature=signature;topCursor=0;}
    if(topCursor>=entries.size())topCursor=0;
    String title=titles.get(topCursor),entry=entries.get(topCursor);
    TextView visibleTitle=(TextView)holidayLabel.getCurrentView();
    if(visibleTitle==null||!title.contentEquals(visibleTitle.getText()))holidayLabel.setText(title);
    holidaySwitcher.setText(entry);
    topCursor=(topCursor+1)%entries.size();
  }
  private void updateVisibility(){
    if(bottomPanel!=null)bottomPanel.setVisibility(!siteOpen&&anyTicker()?View.VISIBLE:View.GONE);
    if(gamesButton!=null)gamesButton.setVisibility(!siteOpen&&enabled("games")?View.VISIBLE:View.GONE);
    // Keep the current top item until its scheduled rotation.
    if(tickerViewport!=null){handler.removeCallbacks(tickerFrame);tickerText.clearAnimation();tickerRunning=false;currentCategory="";categoryIndex=0;if(anyTicker())tickerViewport.post(this::startTicker);}
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
    checkBirthdays();
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
    if(siteOpen||birthdayShowing)return;siteOpen=true;lastPosition=video.getCurrentPosition();
    if(video.isPlaying())video.pause();videoLayer.setVisibility(View.GONE);
    bottomPanel.setVisibility(View.GONE);if(gamesButton!=null)gamesButton.setVisibility(View.GONE);web.setVisibility(View.VISIBLE);
    // Верхня панель у режимі сайту непрозора.
    LinearLayout top=topPanel;
    top.setBackgroundColor(0xFF142A20);
    if(web.getUrl()==null||"about:blank".equals(web.getUrl()))web.loadUrl(SITE);
    lastTouch=System.currentTimeMillis();scheduleIdle();
  }
  private void showVideo(){
    if(!siteOpen)return;siteOpen=false;handler.removeCallbacks(idle);
    web.onPause();web.setVisibility(View.GONE);videoLayer.setVisibility(View.VISIBLE);
    bottomPanel.setVisibility(anyTicker()?View.VISIBLE:View.GONE);if(gamesButton!=null)gamesButton.setVisibility(enabled("games")?View.VISIBLE:View.GONE);
    LinearLayout top=topPanel;
    top.setBackgroundColor(0x99142A20);
    web.loadUrl("about:blank");web.onResume();
    if(playlist.isEmpty())showEmpty();else if(lastPosition>0){video.seekTo(lastPosition);video.start();}else playCurrent();
  }
  private void scheduleIdle(){handler.removeCallbacks(idle);handler.postDelayed(idle,timeout);}
  @Override public boolean dispatchTouchEvent(android.view.MotionEvent e){
    if(siteOpen&&e.getAction()==MotionEvent.ACTION_DOWN){lastTouch=System.currentTimeMillis();scheduleIdle();}
    if(gameOverlay!=null&&e.getAction()==MotionEvent.ACTION_DOWN)gameLastTouch=System.currentTimeMillis();
    return super.dispatchTouchEvent(e);
  }
  private void chooseDataFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    startActivityForResult(i,PICK_DATA);}
  private void displaySettings(){
    String[] labels={"Кнопка інтерактивних ігор","Іменинники", "Свята", "Онлайн-новини", "Факти з Excel"};
    String[] keys={"games","birthdays","holidays","news","facts"};
    boolean[] values=new boolean[keys.length];for(int i=0;i<keys.length;i++)values[i]=enabled(keys[i]);
    new AlertDialog.Builder(this).setTitle("Відображення елементів")
      .setMultiChoiceItems(labels,values,(d,i,checked)->{getPreferences(0).edit().putBoolean(keys[i],checked).apply();updateVisibility();})
      .setPositiveButton("Готово",(d,w)->updateVisibility()).show();
  }
  private void topIntervalSettings(){
    final int[] values={5,10,15,20,25,30};
    String[] labels=new String[values.length];int selected=3;
    for(int i=0;i<values.length;i++){labels[i]=values[i]+" секунд";if(values[i]==topIntervalSeconds())selected=i;}
    new AlertDialog.Builder(this).setTitle("Час зміни верхньої панелі")
      .setSingleChoiceItems(labels,selected,(dialog,which)->{
        getPreferences(0).edit().putInt("top_interval_seconds",values[which]).apply();
        handler.removeCallbacks(rotateHoliday);
        handler.postDelayed(rotateHoliday,values[which]*1000L);
        dialog.dismiss();
      }).setNegativeButton("Скасувати",null).show();
  }
  private void chooseFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    startActivityForResult(i,PICK_FOLDER);}
  private int gameTimeoutMs(){return getPreferences(0).getInt("game_timeout",60000);}
  private void gameTimeoutSettings(){
    final int[] values={60000,120000,300000};
    String[] labels={"1 хвилина","2 хвилини","5 хвилин"};
    int selected=0;for(int i=0;i<values.length;i++)if(values[i]==gameTimeoutMs())selected=i;
    new AlertDialog.Builder(this).setTitle("Таймер бездіяльності у грі")
      .setSingleChoiceItems(labels,selected,(d,w)->{getPreferences(0).edit().putInt("game_timeout",values[w]).apply();d.dismiss();})
      .setNegativeButton("Скасувати",null).show();
  }
  private void mainIdleSettings(){
    String[] labels={"30 секунд","1 хвилина","2 хвилини","5 хвилин"};
    int[] values={30000,60000,120000,300000};int selected=0;
    for(int i=0;i<values.length;i++)if(values[i]==timeout)selected=i;
    new AlertDialog.Builder(this).setTitle("Час бездіяльності")
      .setSingleChoiceItems(labels,selected,(d,w)->{timeout=values[w];getPreferences(0).edit().putInt("timeout",timeout).apply();d.dismiss();})
      .setNegativeButton("Скасувати",null).show();
  }
  private void settings(){
    boolean sound=getPreferences(0).getBoolean("sound",false),stretch=getPreferences(0).getBoolean("stretch_video",true);
    String[] opts={"Вибрати папку з відео","Вибрати папку з файлами (MalinForest)","Відображення елементів",
      "Розклад привітань і мелодія","Час бездіяльності","Таймер бездіяльності у грі",
      "Час зміни верхньої панелі","Розтягувати відео: "+(stretch?"увімкнено":"вимкнено"),
      "Звук відео: "+(sound?"увімкнений":"вимкнений"),"Оновити список відео",
      "Оновити свята та новини","Перечитати Excel"};
    new AlertDialog.Builder(this).setTitle("Налаштування MalinForest").setItems(opts,(d,w)->{
      if(w==0)chooseFolder();
      if(w==1)chooseDataFolder();
      if(w==2)displaySettings();
      if(w==3)birthdaySettings();
      if(w==4)mainIdleSettings();
      if(w==5)gameTimeoutSettings();
      if(w==6)topIntervalSettings();
      if(w==7){boolean enabled=!getPreferences(0).getBoolean("stretch_video",true);
        getPreferences(0).edit().putBoolean("stretch_video",enabled).apply();video.setStretchMode(enabled);}
      if(w==8){boolean on=!getPreferences(0).getBoolean("sound",false);
        getPreferences(0).edit().putBoolean("sound",on).apply();}
      if(w==9&&folder!=null)loadFolder(folder,true);
      if(w==10){loadHolidays();loadNews();}
      if(w==11)loadData();
    }).show();
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request==PICK_DATA&&result==RESULT_OK&&data!=null){
      Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
      dataFolder=uri;getPreferences(0).edit().putString("data_folder",uri.toString()).apply();loadData();return;
    }
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
  // A rule-based fact ticker: never invent details not present in the feed.
  private boolean clickbait(String s){
    String v=s.toLowerCase(new Locale("uk","UA"));
    return v.contains("стало відомо")||v.contains("що відомо")||v.contains("що буде")||
      v.contains("читайте")||v.contains("подробиц")||v.contains("експерти розповіли")||
      v.contains("шокув")||v.contains("гороскоп")||v.contains("рецепт")||
      v.contains("назвали причину")||v.contains("все, що потрібно знати")||
      v.endsWith("?");
  }
  private String factText(String title,String description){
    String t=plain(title).replaceAll("\\s+"," ").trim();
    String d=plain(description).replaceAll("\\s+"," ").trim();
    // The description often begins with a clickbait title. Prefer a complete factual sentence.
    String[] sentences=d.split("(?<=[.!?])\\s+");
    String chosen="";
    for(String sentence:sentences){
      String candidate=sentence.trim();
      if(candidate.length()<42||candidate.length()>210||clickbait(candidate))continue;
      if(candidate.contains("©")||candidate.toLowerCase(Locale.ROOT).contains("tsn.ua"))continue;
      if(candidate.matches(".*[.!]$")){chosen=candidate;break;}
    }
    // Clear, self-contained factual titles are better than vague descriptions.
    if(!clickbait(t)&&t.length()>=38&&t.length()<=170&&
       (t.matches(".*[0-9].*")||t.matches("(?iu).*(ухвалив|підписав|відкрили|запровадили|загинули|пошкоджено|отримала|повідомило|затвердив|завершили|збудували|відновили|знищили|засіяли|передали|збільшив|зменшив|розпочали|призначив|затримали|запустили|евакуювали).*")))
      chosen=t;
    if(chosen.isEmpty()&&suitableForestNews(t))chosen=t;
    if(chosen.isEmpty())return "";
    chosen=chosen.replaceAll("(?iu)\\s*[-—|]\\s*(ТСН|Укрінформ|новини України).*$","").trim();
    if(chosen.length()>185)return "";
    if(chosen.endsWith("."))chosen=chosen.substring(0,chosen.length()-1);
    return chosen;
  }
  private ArrayList<String> readFacts(String xml) throws Exception{
    XmlPullParser p=XmlPullParserFactory.newInstance().newPullParser();
    p.setInput(new StringReader(xml));
    ArrayList<String> facts=new ArrayList<>();
    boolean inItem=false;String title="",description="";
    int event=p.getEventType();
    while(event!=XmlPullParser.END_DOCUMENT&&facts.size()<18){
      if(event==XmlPullParser.START_TAG){
        String tag=p.getName();
        if("item".equalsIgnoreCase(tag)||"entry".equalsIgnoreCase(tag)){
          inItem=true;title="";description="";
        }else if(inItem&&"title".equalsIgnoreCase(tag))title=p.nextText();
        else if(inItem&&("description".equalsIgnoreCase(tag)||"summary".equalsIgnoreCase(tag)))description=p.nextText();
      }else if(event==XmlPullParser.END_TAG&&inItem&&
          ("item".equalsIgnoreCase(p.getName())||"entry".equalsIgnoreCase(p.getName()))){
        inItem=false;String fact=factText(title,description);
        if(!fact.isEmpty()&&!facts.contains(fact))facts.add(fact);
      }
      event=p.next();
    }
    return facts;
  }
  // Оцінки, політичні коментарі й клікбейт не потрапляють до інформаційної стрічки.
  // Заголовки цього сайту часто короткі: не застосовуємо старий фільтр ТСН.
  // На сторінці «Новини» не вимагаємо ключового слова в кожному заголовку:
  // інакше відкидаються корисні повідомлення про лісову галузь.
  // Без тематичних, мовних і стилістичних фільтрів заголовків.
  // Відкидаємо лише порожній текст та надто довгі технічні рядки.
  private boolean suitableForestNews(String text){
    if(text==null)return false;
    String title=text.replaceAll("\\s+"," ").trim();
    if(title.isEmpty()||title.length()>350)return false;
    return isUkrainianTitle(title);
  }
  // Без сервісу перекладу: не допускаємо російськомовні та невизначені заголовки.
  // Це консервативний евристичний фільтр мови, а не перекладач.
  private boolean isUkrainianTitle(String title){
    String s=" "+title.toLowerCase(new Locale("uk","UA"))
      .replaceAll("[^\\p{L}]+"," ").trim()+" ";
    if(s.matches(".*[ыэъё].*"))return false;
    String[] russian={" что "," это "," для этого "," который "," которая ",
      " которые "," также "," сегодня "," сейчас "," после "," будет ",
      " были "," года "," леса "," лесов "," древесины "," украины ",
      " россии "," российские "," заявил "," сообщили "," власти ",
      " предприятие "," предприятий "," производство "," согласно ",
      " более "," около "," между "," области "," новый "," новые ",
      " новые "," строительство "," незаконной "," рубки "," в украине ",
      " из за "," из "," по данным "," лесной "," лесные "," деревьев "};
    for(String word:russian)if(s.contains(word))return false;
    // Українські літери — позитивне підтвердження, без них мову не вгадуємо.
    if(s.matches(".*[іїєґ].*"))return true;
    String[] ukrainian={" та "," для "," від "," щодо "," через ",
      " понад "," серед "," під "," над "," після "," сталося ",
      " ухвалили "," лісів "," лісу "," лісові "," лісове ",
      " деревини "," деревину "," громаді "," громад "," заготівлю ",
      " України "," українськ "," області "," року "," роки ",
      " у "," на "," в "," з "," до "," про "};
    // Однієї спільної службової частки замало для визначення мови.
    int distinct=0;
    for(String word:ukrainian){
      if(word.equals(" у ")||word.equals(" на ")||word.equals(" в ")||
         word.equals(" з ")||word.equals(" до ")||word.equals(" про "))continue;
      if(s.contains(word.toLowerCase(new Locale("uk","UA"))))distinct++;
    }
    return distinct>=2;
  }
  // Заголовки сторінок, а також посилання на публікації в блоках, де немає h3.
  // Не додаємо скорочені заголовки з карток сайту.
  private boolean completeNewsTitle(String text){
    if(text==null)return false;
    String t=text.trim();
    return !t.endsWith("...")&&!t.endsWith("…");
  }
  // WordPress REST API повертає лише записи типу post, а не меню чи кнопки.
  private ArrayList<String> readForestPosts(String json) throws Exception{
    JSONArray posts=new JSONArray(json);
    LinkedHashSet<String> result=new LinkedHashSet<>();
    for(int i=0;i<posts.length()&&result.size()<40;i++){
      JSONObject post=posts.optJSONObject(i);
      if(post==null||!"publish".equals(post.optString("status","publish")))continue;
      JSONObject title=post.optJSONObject("title");
      if(title==null)continue;
      String heading=plain(title.optString("rendered", ""));
      if(completeNewsTitle(heading)&&suitableForestNews(heading))result.add(heading);
    }
    return new ArrayList<>(result);
  }
  // RSS: використовуємо справжній заголовок, а не правило для новин ТСН.
  private ArrayList<String> readForestFeed(String xml) throws Exception{
    XmlPullParser p=XmlPullParserFactory.newInstance().newPullParser();
    p.setInput(new StringReader(xml));
    LinkedHashSet<String> result=new LinkedHashSet<>();
    boolean inItem=false;String title="";
    int event=p.getEventType();
    while(event!=XmlPullParser.END_DOCUMENT&&result.size()<40){
      if(event==XmlPullParser.START_TAG){
        String tag=p.getName();
        if("item".equalsIgnoreCase(tag)||"entry".equalsIgnoreCase(tag)){
          inItem=true;title="";
        }else if(inItem&&"title".equalsIgnoreCase(tag))title=p.nextText();
      }else if(event==XmlPullParser.END_TAG&&inItem&&
          ("item".equalsIgnoreCase(p.getName())||"entry".equalsIgnoreCase(p.getName()))){
        inItem=false;
        String heading=plain(title).replaceAll("\\s+"," ").trim();
        if(completeNewsTitle(heading)&&suitableForestNews(heading))result.add(heading);
      }
      event=p.next();
    }
    return new ArrayList<>(result);
  }
  private void loadNews(){new Thread(()->{
    LinkedHashSet<String> facts=new LinkedHashSet<>();
    // RSS містить повні заголовки; він має пріоритет над картками архіву.
    for(String source:NEWS_URLS){
      try{for(String item:readForestFeed(fetch(source))){
        facts.add(item);if(facts.size()>=20)break;
      }}catch(Exception ignored){}
      if(facts.size()>=20)break;
    }
    // Якщо RSS замало, отримуємо тільки реальні WordPress-публікації.
    // Довільні HTML-посилання, меню, «Увійти» та «Коментарі» не читаємо.
    for(int page=1;page<=4&&facts.size()<20;page++){
      String url="https://www.openforest.org.ua/wp-json/wp/v2/posts?per_page=30&page="+page;
      try{
        ArrayList<String> posts=readForestPosts(fetch(url));
        if(posts.isEmpty())break;
        for(String item:posts){facts.add(item);if(facts.size()>=20)break;}
      }catch(Exception ignored){break;}
    }
    if(!facts.isEmpty()){
      String joined=TextUtils.join("     ●     ",facts)+"     ●     ";
      getPreferences(0).edit().putInt("news_cache_version",NEWS_CACHE_VERSION)
        .putString("news_cache",joined).apply();
      runOnUiThread(()->{
        if(!cachedNews.equals(joined)){cachedNews=joined;pendingNews=null;runOnUiThread(()->updateVisibility());}
      });
    }else if(cachedNews.isEmpty())runOnUiThread(()->{
      cachedNews="Лісові новини тимчасово недоступні     ●     ";
      tickerViewport.post(this::startTicker);
    });
  }).start();}
  private String plain(String s){
    String cleaned=s.replaceAll("(?is)<script[^>]*>.*?</script>"," ").replaceAll("(?is)<style[^>]*>.*?</style>"," ");
    cleaned=cleaned.replaceAll("(?is)<[^>]+>"," ");
    return Html.fromHtml(cleaned,Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+"," ").trim();
  }
  private boolean excludeHoliday(String value){
    String s=value.toLowerCase(new Locale("uk","UA"));
    return s.contains("іменин")||s.contains("імена сьогодні")||
      s.contains("день ангела")||s.contains("дні ангела")||
      s.contains("ангела сьогодні")||s.contains("хто народився")||
      s.contains("народилися сьогодні")||s.contains("народження знаменитост")||
      s.contains("день народження відомих")||s.contains("дні народження відомих");
  }
  private void loadHolidays(){new Thread(()->{
    try{
      String html=fetch(HOLIDAY_URL);
      // Read only today's dated holiday cards, not navigation or unrelated links.
      // The page also contains other dates, birthdays and general article links.
      LinkedHashSet<String> result=new LinkedHashSet<>();
      String key=todayKey();
      Calendar kyiv=Calendar.getInstance(TimeZone.getTimeZone("Europe/Kyiv"));
      String[] monthNames={"січня","лютого","березня","квітня","травня","червня",
        "липня","серпня","вересня","жовтня","листопада","грудня"};
      String expectedDate=kyiv.get(Calendar.DAY_OF_MONTH)+" "+monthNames[kyiv.get(Calendar.MONTH)];
      int endSection=html.toLowerCase(Locale.ROOT).indexOf("зараз також тривають");
      if(endSection<0)throw new IOException("Holiday section not found");
      String section=html.substring(0,endSection);
      Pattern headings=Pattern.compile("(?is)<h3\\b[^>]*>(.*?)</h3>");
      Matcher m=headings.matcher(section);
      ArrayList<String> names=new ArrayList<>();
      ArrayList<Integer> positions=new ArrayList<>();
      while(m.find()){
        names.add(plain(m.group(1)));
        positions.add(m.end());
      }
      for(int i=0;i<names.size()&&result.size()<24;i++){
        String s=names.get(i);
        if(s.length()<8||s.length()>105||excludeHoliday(s))continue;
        int from=positions.get(i);
        int to=i+1<positions.size()?section.lastIndexOf("<h3",positions.get(i+1)):section.length();
        if(to<from)to=section.length();
        String details=plain(section.substring(from,to)).toLowerCase(new Locale("uk","UA"));
        // Require an explicit date inside the card itself.
        if(details.contains(expectedDate))result.add(s);
      }
      if(!result.isEmpty()){
        JSONArray arr=new JSONArray();for(String s:result)arr.put(s);
        getPreferences(0).edit().putInt("holiday_cache_version",HOLIDAY_CACHE_VERSION).putString("holiday_date",key)
          .putString("holiday_cache",arr.toString()).apply();
        runOnUiThread(()->{
          if(!todayKey().equals(key))return;
          holidays.clear();holidays.addAll(result);holidayIndex=0;holidayDate=key;
          updateTopRotation();
        });
      }else if(holidays.isEmpty())runOnUiThread(()->holidaySwitcher.setCurrentText("Інформація про свята недоступна"));
    }catch(Exception ignored){
      if(holidays.isEmpty())runOnUiThread(()->holidaySwitcher.setCurrentText("Інформація про свята недоступна"));
    }
  }).start();}
  private String degrees(double v){return (v>0?"+":"")+Math.round(v);}
  private String symbol(int code){if(code==0)return "☀";if(code<=3)return "⛅";if(code<=48)return "☁";if(code<=67)return "🌧";if(code<=77)return "❄";return "🌦";}
  @Override public void onBackPressed(){if(gameOverlay!=null){closeGames();return;}if(siteOpen){if(web.canGoBack()&&!"about:blank".equals(web.getUrl()))web.goBack();else showVideo();}else settings();}
  @Override protected void onResume(){super.onResume();immersive();
    if(!siteOpen&&video!=null&&!playlist.isEmpty()&&!video.isPlaying())video.start();}
  @Override protected void onPause(){if(video!=null&&video.isPlaying())video.pause();super.onPause();}
  @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);
    handler.removeCallbacks(gameIdle);handler.removeCallbacks(tickerFrame);if(tickerText!=null)tickerText.clearAnimation();if(web!=null)web.destroy();stopBirthdayMusic();super.onDestroy();}
  // Ігри читають картинки з уже вибраної папки MalinForest через SAF.
  private Uri gameFile(String... parts){
    if(dataFolder==null)return null;
    try{
      String parent=DocumentsContract.getTreeDocumentId(dataFolder);
      for(String part:parts){
        Uri list=DocumentsContract.buildChildDocumentsUriUsingTree(dataFolder,parent);
        String found=null;
        try(Cursor c=getContentResolver().query(list,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
          if(c!=null)while(c.moveToNext())if(part.equalsIgnoreCase(c.getString(1))){found=c.getString(0);break;}
        }
        if(found==null)return null;
        parent=found;
      }
      return DocumentsContract.buildDocumentUriUsingTree(dataFolder,parent);
    }catch(Exception ignored){return null;}
  }
  private ImageView gameImage(String... path){
    ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
    Uri uri=gameFile(path);
    if(uri!=null){try(InputStream in=getContentResolver().openInputStream(uri)){
      if(in!=null)iv.setImageBitmap(BitmapFactory.decodeStream(in));
    }catch(Exception ignored){}}
    return iv;
  }
  private final String[] treeNames={"дуб","клен","сосна","ялина","каштан","береза","вільха","туя","ялівець"};
  private final String[] treeLabels={"Дуб звичайний","Клен звичайний","Сосна звичайна","Ялина звичайна","Каштан звичайний","Береза повисла","Вільха чорна","Туя західна","Ялівець звичайний"};
  private final String[] animalNames={"вовк","лисиця","лось","олень","косуля","глухар","яструб","курка","собака","кіт","корова","свиня","коза","індик"};
  private final String[] mushroomNames={"маслюк","білий гриб","лисичка","сироїжки","підберезовик","печериці","бліда поганка","мухомор червоний","мухомор зелений","несправжній опеньок","павутинник","печериця темно-луската"};
  private boolean gameCorrect(String item){
    if(gameMode==0)return true;
    String[] names=gameMode==1?animalNames:mushroomNames;
    int i=Arrays.asList(names).indexOf(item);
    return i>=0&&i<(gameMode==1?7:6);
  }
  private TextView gameText(String text,int size,int color){
    TextView v=label(text,size,true);v.setTextColor(color);v.setGravity(Gravity.CENTER);
    v.setShadowLayer(3,1,2,Color.BLACK);return v;
  }
  private TextView gameButton(String text,int color){
    TextView v=gameText(text,21,Color.WHITE);v.setPadding(dp(16),dp(12),dp(16),dp(12));v.setBackground(bg(color,16));return v;
  }
  private void gameResetIdle(){gameLastTouch=System.currentTimeMillis();handler.removeCallbacks(gameIdle);handler.post(gameIdle);}
  private void gameShell(){
    if(gameOverlay!=null)root.removeView(gameOverlay);
    gameOverlay=new FrameLayout(this);root.addView(gameOverlay,new FrameLayout.LayoutParams(-1,-1));
    gameOverlay.setBackgroundColor(0xFF21452B);
    ImageView bg=gameImage("Games","background.png");bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
    gameOverlay.addView(bg,new FrameLayout.LayoutParams(-1,-1));
    if(topPanel!=null)topPanel.setVisibility(View.GONE);
    // Таймер бездіяльності працює приховано: не додаємо лічильник на екран.
    gameTimerLabel=null;
    gameResetIdle();
  }
  private void closeGames(){
    handler.removeCallbacks(gameIdle);
    if(gameOverlay!=null){root.removeView(gameOverlay);gameOverlay=null;}
    gameMode=-1;gameTimerLabel=null;gameFinished=false;gameBusy=false;
    if(topPanel!=null)topPanel.setVisibility(View.VISIBLE);
    if(!siteOpen&&!birthdayShowing&&video!=null&&!playlist.isEmpty())video.start();
  }
  private void gameExitButton(){
    TextView exit=gameButton("Завершити гру",0xDDAC2929);
    FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(-2,dp(52),Gravity.RIGHT|Gravity.BOTTOM);
    ep.setMargins(0,0,dp(14),dp(12));gameOverlay.addView(exit,ep);
    exit.setOnClickListener(v->closeGames());
  }
  private void openGameMenu(){
    if(siteOpen||birthdayShowing||dataFolder==null){
      if(dataFolder==null)Toast.makeText(this,"Спочатку виберіть папку MalinForest у налаштуваннях",Toast.LENGTH_LONG).show();
      return;
    }
    if(video!=null&&video.isPlaying())video.pause();
    gameMode=-1;gameShell();
    TextView title=gameText("ІНТЕРАКТИВНІ ІГРИ",32,Color.WHITE);
    FrameLayout.LayoutParams ttl=new FrameLayout.LayoutParams(-1,dp(65),Gravity.TOP);
    ttl.topMargin=dp(70);gameOverlay.addView(title,ttl);
    LinearLayout cards=new LinearLayout(this);cards.setGravity(Gravity.CENTER);cards.setPadding(dp(20),dp(8),dp(20),dp(8));
    FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-1);cp.topMargin=dp(145);cp.bottomMargin=dp(90);
    gameOverlay.addView(cards,cp);
    String[] names={"Де я росту?","Де я живу?","Їстівний чи неїстівний?"};
    String[][] images={{"Games","DeYaRostu","Дерева","дуб.jpg"},{"Games","DeYaZhyvu","Тварини","лисиця.jpg"},{"Games","Gruby","Гриби","білий гриб.jpg"}};
    for(int i=0;i<3;i++){
      final int mode=i;
      LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(8),dp(8),dp(8),dp(8));
      card.setBackground(bg(0xEAE5F3DC,18));
      LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(8),0,dp(8),0);cards.addView(card,p);
      ImageView image=gameImage(images[i]);card.addView(image,new LinearLayout.LayoutParams(-1,0,1));
      TextView text=gameText(names[i],22,0xFF173F28);text.setShadowLayer(0,0,0,0);
      card.addView(text,new LinearLayout.LayoutParams(-1,dp(66)));
      card.setOnClickListener(v->startGame(mode));
    }
    gameExitButton();
  }
  private void startGame(int mode){
    gameMode=mode;gameLives=3;gameIndex=0;gameFinished=false;gameBusy=false;
    gameQueue.clear();remainingTrees.clear();
    if(mode==0){gameQueue.addAll(Arrays.asList(treeNames));remainingTrees.addAll(Arrays.asList(treeNames));Collections.shuffle(remainingTrees,gameRandom);}
    else if(mode==1)gameQueue.addAll(Arrays.asList(animalNames));
    else gameQueue.addAll(Arrays.asList(mushroomNames));
    Collections.shuffle(gameQueue,gameRandom);
    showGameRound();
  }
  private void showGameRound(){
    if(gameMode<0)return;
    if(gameIndex>=gameQueue.size()||gameLives<=0){showGameEnd();return;}
    gameBusy=false;gameShell();
    String[] names={"Де я росту?","Де я живу?","Їстівний чи неїстівний?"};
    TextView title=gameText(names[gameMode],30,Color.WHITE);
    FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,dp(50),Gravity.TOP);tp.topMargin=dp(14);tp.leftMargin=dp(125);tp.rightMargin=dp(145);
    gameOverlay.addView(title,tp);
    StringBuilder heartsText=new StringBuilder();for(int k=0;k<gameLives;k++)heartsText.append("❤ ");for(int k=gameLives;k<3;k++)heartsText.append("♡ ");
    TextView hearts=gameText(heartsText.toString(),27,0xFFFF4444);
    FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(150),dp(50),Gravity.RIGHT|Gravity.TOP);hp.topMargin=dp(10);gameOverlay.addView(hearts,hp);
    String item=gameQueue.get(gameIndex);
    LinearLayout question=new LinearLayout(this);question.setOrientation(LinearLayout.VERTICAL);question.setGravity(Gravity.CENTER);
    FrameLayout.LayoutParams qp=new FrameLayout.LayoutParams(-1,-1);qp.topMargin=dp(72);qp.bottomMargin=dp(gameMode==0?165:125);
    gameOverlay.addView(question,qp);
    ImageView photo=gameMode==0?gameImage("Games","DeYaRostu","Плоди",item+".jpg"):
      gameMode==1?gameImage("Games","DeYaZhyvu","Тварини",item+".jpg"):
      gameImage("Games","Gruby","Гриби",item+".jpg");
    photo.setBackground(bg(0xCFFFFFFF,14));
    question.addView(photo,new LinearLayout.LayoutParams(-1,0,1));
    if(gameMode!=0){TextView caption=gameText(item.substring(0,1).toUpperCase(new Locale("uk"))+item.substring(1),24,Color.WHITE);
      question.addView(caption,new LinearLayout.LayoutParams(-1,dp(46)));}
    photo.setTranslationY(-dp(110));photo.setAlpha(0f);photo.animate().translationY(0).alpha(1).setDuration(550).start();
    TextView feedback=gameText("",25,Color.WHITE);
    FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-1,dp(52),Gravity.BOTTOM);fp.bottomMargin=dp(gameMode==0?122:85);
    gameOverlay.addView(feedback,fp);
    if(gameMode==0){
      // Усі дерева завжди в одному рядку: ширина ділиться порівну без прокручування.
      LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,dp(122),Gravity.BOTTOM);
      sp.bottomMargin=dp(12);sp.leftMargin=dp(6);sp.rightMargin=dp(165);
      gameOverlay.addView(row,sp);
      for(String tree:remainingTrees){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(bg(0xDDEAF1DE,10));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(118),1f);
        bp.setMargins(dp(2),0,dp(2),0);row.addView(card,bp);
        ImageView img=gameImage("Games","DeYaRostu","Дерева",tree+".jpg");
        card.addView(img,new LinearLayout.LayoutParams(-1,0,1));
        int idx=Arrays.asList(treeNames).indexOf(tree);
        TextView lbl=gameText(idx>=0?treeLabels[idx]:tree,10,0xFF173D29);
        lbl.setShadowLayer(0,0,0,0);lbl.setMaxLines(2);lbl.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(lbl,new LinearLayout.LayoutParams(-1,dp(36)));
        card.setOnClickListener(v->gameAnswer(tree.equals(item),tree,feedback));
      }
    }else{
      LinearLayout choices=new LinearLayout(this);choices.setGravity(Gravity.CENTER);
      FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,dp(75),Gravity.BOTTOM);bp.bottomMargin=dp(12);bp.rightMargin=dp(165);bp.leftMargin=dp(20);
      gameOverlay.addView(choices,bp);
      String[] labels=gameMode==1?new String[]{"ДИКИЙ\n(в лісі)","СВІЙСЬКИЙ\n(в домашніх умовах)"}:new String[]{"ЇСТІВНИЙ","НЕЇСТІВНИЙ"};
      for(int i=0;i<2;i++){
        final boolean pickedFirst=i==0;
        TextView btn=gameButton(labels[i],i==0?0xE322733D:0xDDA74B30);
        if(gameMode==1){btn.setSingleLine(false);btn.setMaxLines(2);btn.setTextSize(18);btn.setGravity(Gravity.CENTER);}
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(65),1);cp.setMargins(dp(8),0,dp(8),0);choices.addView(btn,cp);
        btn.setOnClickListener(v->gameAnswer(pickedFirst==gameCorrect(item),item,feedback));
      }
    }
    gameExitButton();
  }
  private void gameAnswer(boolean correct,String item,TextView feedback){
    if(gameBusy||gameFinished)return;
    gameBusy=true;
    if(correct){
      feedback.setText("Вірно! 🎉");feedback.setTextColor(0xFFB8FF92);
      for(int i=0;i<15;i++){
        TextView conf=gameText(i%2==0?"✦":"●",20,i%3==0?0xFFFFDA42:0xFF92F3FF);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.TOP|Gravity.LEFT);
        int x=gameRandom.nextInt(Math.max(1,getResources().getDisplayMetrics().widthPixels-dp(30)));
        p.leftMargin=x;p.topMargin=dp(80)+gameRandom.nextInt(dp(130));gameOverlay.addView(conf,p);
        conf.animate().translationY(dp(170)+gameRandom.nextInt(dp(100))).rotation(gameRandom.nextInt(360)).alpha(0f).setDuration(1000).start();
      }
      if(gameMode==0)remainingTrees.remove(item);
      gameIndex++;
      handler.postDelayed(()->{if(gameOverlay!=null&&gameMode>=0)showGameRound();},1250);
    }else{
      gameLives--;feedback.setText("Невірно, спробуй ще раз");feedback.setTextColor(0xFFFFE7A8);
      if(gameLives==0)handler.postDelayed(()->{if(gameOverlay!=null&&gameMode>=0)showGameEnd();},1200);
      else handler.postDelayed(()->{if(gameOverlay!=null&&gameMode>=0)showGameRound();},950);
    }
  }
  private void showGameEnd(){
    gameFinished=true;gameShell();
    TextView msg=gameText(gameLives==0?"Гру завершено":"Молодець! Усі відповіді правильні!",30,Color.WHITE);
    FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(-1,dp(95),Gravity.CENTER);gameOverlay.addView(msg,mp);
    LinearLayout buttons=new LinearLayout(this);buttons.setGravity(Gravity.CENTER);
    FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,dp(80),Gravity.BOTTOM);bp.bottomMargin=dp(65);gameOverlay.addView(buttons,bp);
    TextView retry=gameButton("Спробувати ще раз",0xE327743B);
    LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,dp(62));rp.setMargins(dp(10),0,dp(10),0);buttons.addView(retry,rp);
    retry.setOnClickListener(v->startGame(gameMode));
    gameExitButton();
  }
  // Читаємо XLSX як стандартний ZIP/XML без сторонніх залежностей.
  private Uri dataFile(String name){
    if(dataFolder==null)return null;
    try{Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(dataFolder,DocumentsContract.getTreeDocumentId(dataFolder));
      try(Cursor c=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
        if(c!=null)while(c.moveToNext())if(name.equalsIgnoreCase(c.getString(1)))return DocumentsContract.buildDocumentUriUsingTree(dataFolder,c.getString(0));
      }}catch(Exception ignored){}return null;
  }
  private static String xmlText(Element e,String tag){NodeList nodes=e.getElementsByTagNameNS("*",tag);return nodes.getLength()>0?nodes.item(0).getTextContent():"";}
  private static Document xml(byte[] bytes)throws Exception{
    if(bytes==null)throw new IOException("Відсутній XML-файл у XLSX");
    // Android XML parsers may not support Apache-specific setFeature flags.
    // XLSX XML does not require DTD; reject DTD/entity declarations explicitly.
    String header=new String(bytes,java.nio.charset.StandardCharsets.UTF_8).toUpperCase(java.util.Locale.ROOT);
    if(header.contains("<!DOCTYPE")||header.contains("<!ENTITY"))throw new IOException("Неприпустимий DTD у XLSX");
    DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);
    return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));}
  private LinkedHashMap<String,ArrayList<String[]>> readWorkbook(Uri uri)throws Exception{
    HashMap<String,byte[]> files=new HashMap<>();
    try(ZipInputStream z=new ZipInputStream(getContentResolver().openInputStream(uri))){ZipEntry e;ByteArrayOutputStream out;
      while((e=z.getNextEntry())!=null){if(e.getSize()>8*1024*1024)throw new IOException("Завеликий файл");out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=z.read(b))>0){out.write(b,0,n);if(out.size()>8*1024*1024)throw new IOException("Завеликий файл");}files.put(e.getName(),out.toByteArray());}}
    ArrayList<String> shared=new ArrayList<>();
    if(files.containsKey("xl/sharedStrings.xml")){NodeList si=xml(files.get("xl/sharedStrings.xml")).getElementsByTagNameNS("*","si");
      for(int i=0;i<si.getLength();i++)shared.add(si.item(i).getTextContent());}
    Document wb=xml(files.get("xl/workbook.xml"));
    Document rel=xml(files.get("xl/_rels/workbook.xml.rels"));HashMap<String,String> paths=new HashMap<>();
    NodeList rs=rel.getElementsByTagNameNS("*","Relationship");for(int i=0;i<rs.getLength();i++){Element e=(Element)rs.item(i);String path=e.getAttribute("Target");if(path.startsWith("/"))path=path.substring(1);else if(!path.startsWith("xl/"))path="xl/"+path;paths.put(e.getAttribute("Id"),path);}
    LinkedHashMap<String,ArrayList<String[]>> result=new LinkedHashMap<>();
    NodeList sheets=wb.getElementsByTagNameNS("*","sheet");for(int i=0;i<sheets.getLength();i++){
      Element sh=(Element)sheets.item(i);String name=sh.getAttribute("name");String id=sh.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id");
      byte[] sheetBytes=files.get(paths.get(id));if(sheetBytes==null)continue;
      NodeList rows=xml(sheetBytes).getElementsByTagNameNS("*","row");ArrayList<String[]> table=new ArrayList<>();
      for(int r=0;r<rows.getLength();r++){String[] cols=new String[8];Arrays.fill(cols,"");NodeList cells=((Element)rows.item(r)).getElementsByTagNameNS("*","c");
        for(int k=0;k<cells.getLength();k++){Element cell=(Element)cells.item(k);String ref=cell.getAttribute("r");int col=0;while(col<ref.length()&&Character.isLetter(ref.charAt(col)))col++;int idx=0;for(int j=0;j<col;j++)idx=idx*26+(Character.toUpperCase(ref.charAt(j))-'A'+1);idx--;
          if(idx<0||idx>=cols.length)continue;String value=xmlText(cell,"v");String type=cell.getAttribute("t");if("s".equals(type)){try{value=shared.get(Integer.parseInt(value));}catch(Exception ignored){value="";}}
          if("inlineStr".equals(type))value=xmlText(cell,"is");cols[idx]=value.trim();}
        table.add(cols);
      }result.put(name,table);
    }return result;
  }
  private void loadData(){if(dataFolder==null)return;new Thread(()->{
    LinkedHashMap<String,ArrayList<String>> newFacts=new LinkedHashMap<>();ArrayList<Person> newPeople=new ArrayList<>();
    String errors="";
    try{Uri f=dataFile("MalinForest.xlsx");if(f!=null){LinkedHashMap<String,ArrayList<String[]>> wb=readWorkbook(f);
      for(Map.Entry<String,ArrayList<String[]>> sh:wb.entrySet()){
        if("Налаштування".equalsIgnoreCase(sh.getKey()))continue;ArrayList<String> messages=new ArrayList<>();
        for(int i=1;i<sh.getValue().size();i++){String[] row=sh.getValue().get(i);if(!row[1].isEmpty()&&!"Ні".equalsIgnoreCase(row[2]))messages.add(row[1]);}
        newFacts.put(sh.getKey(),messages);
      }}else errors+="Немає MalinForest.xlsx. ";}catch(Exception ex){errors+="Помилка MalinForest.xlsx: "+ex.getMessage()+". ";}
    try{Uri f=dataFile("Іменинники.xlsx");if(f==null)f=dataFile("MalinForest_Іменинники.xlsx");if(f!=null){LinkedHashMap<String,ArrayList<String[]>> wb=readWorkbook(f);
      for(String[] row:wb.values().iterator().next()){
        String name=(row[0]+" "+row[1]+" "+row[2]).trim();if(name.isEmpty()||row[0].equalsIgnoreCase("Прізвище"))continue;
        int[] d=parseBirthday(row[5]);if(d!=null)newPeople.add(new Person(name,row[3],row[4],d[0],d[1],d[2]));
      }}else errors+="Немає Іменинники.xlsx. ";}catch(Exception ex){errors+="Помилка Іменинники.xlsx: "+ex.getMessage();}
    String finalErrors=errors;runOnUiThread(()->{facts.clear();facts.putAll(newFacts);people.clear();people.addAll(newPeople);
      factOrders.clear();
      for(String key:facts.keySet())factPositions.put(key,getPreferences(0).getInt("ticker_pos_"+key,0));
      updateVisibility();if(!finalErrors.isEmpty())Toast.makeText(this,finalErrors,Toast.LENGTH_LONG).show();});
  }).start();}
  private int[] parseBirthday(String raw){try{
    String v=raw.trim();if(v.matches("[0-9]+(\\.[0-9]+)?")){long days=(long)Double.parseDouble(v);Calendar c=Calendar.getInstance();c.set(1899,11,30,12,0);c.add(Calendar.DAY_OF_YEAR,(int)days);return new int[]{c.get(Calendar.DAY_OF_MONTH),c.get(Calendar.MONTH)+1,c.get(Calendar.YEAR)};}
    String[] p=v.split("[./-]");if(p.length==3){int d=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]),y=Integer.parseInt(p[2]);if(y<100)y+=2000; if(d>=1&&d<=31&&m>=1&&m<=12)return new int[]{d,m,y};}
  }catch(Exception ignored){}return null;}
  private String birthdayTimes(){return getPreferences(0).getString("birthday_times","08:45,08:50,08:55");}
  private ArrayList<String> parseTimes(String csv){
    TreeSet<String> sorted=new TreeSet<>();
    for(String part:csv.split(",")){String t=part.trim();if(t.matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))sorted.add(t);}
    return new ArrayList<>(sorted);
  }
  private void birthdaySettings(){
    LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(22),dp(10),dp(22),dp(5));
    TextView help=label("Час показу (ГГ:ХХ). Додайте кілька значень через кому. Наприклад: 08:45,08:50,08:55",15,false);
    help.setTextColor(Color.DKGRAY);layout.addView(help);
    EditText times=new EditText(this);times.setSingleLine(false);times.setText(birthdayTimes());layout.addView(times);
    CheckBox hourly=new CheckBox(this);hourly.setText("Додатково показувати щогодини (о ХХ:00)");hourly.setChecked(getPreferences(0).getBoolean("birthday_hourly",false));layout.addView(hourly);
    CheckBox music=new CheckBox(this);music.setText("Мелодія привітання (birthday.mp3)");music.setChecked(getPreferences(0).getBoolean("birthday_music",true));layout.addView(music);
    TextView vol=label("Гучність мелодії",15,false);vol.setTextColor(Color.DKGRAY);layout.addView(vol);
    SeekBar volume=new SeekBar(this);volume.setMax(100);volume.setProgress(getPreferences(0).getInt("birthday_volume",50));layout.addView(volume);
    new AlertDialog.Builder(this).setTitle("Привітання іменинників").setView(layout)
      .setNegativeButton("Скасувати",null).setPositiveButton("Зберегти",(d,w)->{
        String raw=times.getText().toString().replace(';',',').replace('\n',',');ArrayList<String> list=parseTimes(raw);
        if(!raw.trim().isEmpty()&&list.size()!=raw.split(",").length){Toast.makeText(this,"Деякі значення часу неправильні — їх пропущено",Toast.LENGTH_LONG).show();}
        getPreferences(0).edit().putString("birthday_times",TextUtils.join(",",list))
          .putBoolean("birthday_hourly",hourly.isChecked()).putBoolean("birthday_music",music.isChecked())
          .putInt("birthday_volume",volume.getProgress()).apply();
        if(!music.isChecked())stopBirthdayMusic();
      }).show();
  }
  private void checkBirthdays(){
    if(!enabled("birthdays")||siteOpen||birthdayShowing||dataFolder==null)return;
    Calendar c=Calendar.getInstance();int hour=c.get(Calendar.HOUR_OF_DAY),minute=c.get(Calendar.MINUTE);
    String hhmm=String.format(Locale.US,"%02d:%02d",hour,minute);
    boolean scheduled=parseTimes(birthdayTimes()).contains(hhmm)||
      (getPreferences(0).getBoolean("birthday_hourly",false)&&minute==0);
    if(!scheduled)return;
    String key=todayKey()+"_"+hhmm;
    if(key.equals(lastBirthdaySlot)||getPreferences(0).getBoolean("birthday_shown_"+key,false))return;
    birthdayQueue.clear();int day=c.get(Calendar.DAY_OF_MONTH),month=c.get(Calendar.MONTH)+1;
    for(Person p:people)if(p.day==day&&p.month==month&&isManagement(p))birthdayQueue.add(p);
    if(birthdayQueue.isEmpty())return;
    lastBirthdaySlot=key;
    getPreferences(0).edit().putBoolean("birthday_shown_"+key,true).apply();
    birthdayQueueIndex=0;showNextBirthday();
  }
  private void stopBirthdayMusic(){
    if(birthdayMusic!=null){try{birthdayMusic.stop();}catch(Exception ignored){}
      try{birthdayMusic.release();}catch(Exception ignored){}birthdayMusic=null;}
  }
  private void startBirthdayMusic(){
    stopBirthdayMusic();if(!getPreferences(0).getBoolean("birthday_music",true))return;
    Uri uri=dataFile("birthday.mp3");if(uri==null)return;
    try{birthdayMusic=new MediaPlayer();birthdayMusic.setDataSource(this,uri);
      birthdayMusic.setLooping(true);
      float volume=getPreferences(0).getInt("birthday_volume",50)/100f;
      birthdayMusic.setVolume(volume,volume);birthdayMusic.prepare();birthdayMusic.start();
    }catch(Exception ex){stopBirthdayMusic();}
  }
  private void showNextBirthday(){if(siteOpen){stopBirthdayMusic();birthdayShowing=false;if(birthdayOverlay!=null){root.removeView(birthdayOverlay);birthdayOverlay=null;}return;}if(birthdayQueueIndex>=birthdayQueue.size()){
      if(birthdayOverlay!=null){root.removeView(birthdayOverlay);birthdayOverlay=null;}
      stopBirthdayMusic();birthdayShowing=false;if(!siteOpen&&video!=null&&!playlist.isEmpty())video.start();return;}
    birthdayShowing=true;if(video!=null&&video.isPlaying())video.pause();
    startBirthdayMusic();
    Person p=birthdayQueue.get(birthdayQueueIndex++);int age=Calendar.getInstance().get(Calendar.YEAR)-p.year;
    boolean jubilee=age>=25&&age%5==0;
    if(birthdayOverlay!=null)root.removeView(birthdayOverlay);
    birthdayOverlay=new FrameLayout(this);birthdayOverlay.setBackgroundColor(Color.BLACK);
    root.addView(birthdayOverlay,new FrameLayout.LayoutParams(-1,-1));
    Uri img=dataFile(jubilee?"jubilee.png":"birthday.png");
    if(img!=null){try{ImageView view=new ImageView(this);view.setImageURI(img);view.setScaleType(ImageView.ScaleType.FIT_CENTER);
      birthdayOverlay.addView(view,new FrameLayout.LayoutParams(-1,-1));}catch(Exception ignored){}}
     // Координати прив'язані до оригінальних PNG 1536x1024 та FIT_CENTER.
     // Підкладок немає: лише текст безпосередньо поверх світлого поля.
     final FrameLayout target=birthdayOverlay;
     target.post(()->{
       if(birthdayOverlay!=target)return;
       int w=target.getWidth(),h=target.getHeight();
       float scale=Math.min(w/1536f,h/1024f);
       float left=(w-1536f*scale)/2f,top=(h-1024f*scale)/2f;
       // Звичайна листівка: світле поле приблизно x=575..1460, y=390..705.
       // Ювілейна: x=390..1450, y=520..810.
       int x=jubilee?490:630, y=jubilee?555:470, bw=jubilee?860:770, bh=jubilee?112:130;
       TextView name=label(p.name,28,true);
       name.setBackgroundColor(Color.TRANSPARENT);
       name.setTextColor(0xFF51331B);name.setGravity(Gravity.CENTER);name.setMaxLines(2);
       float base=p.name.length()>35?43:p.name.length()>25?52:62;
       name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,base*scale);
       FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(Math.round(bw*scale),Math.round(bh*scale));
       np.leftMargin=Math.round(left+x*scale);np.topMargin=Math.round(top+y*scale);
       target.addView(name,np);
       if(jubilee){
         TextView ageView=label(age+" років",28,true);
         ageView.setBackgroundColor(Color.TRANSPARENT);
         ageView.setTextColor(0xFF755020);ageView.setGravity(Gravity.CENTER);
         ageView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,55*scale);
         FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(Math.round(700*scale),Math.round(85*scale));
         ap.leftMargin=Math.round(left+570*scale);ap.topMargin=Math.round(top+685*scale);
         target.addView(ageView,ap);
       }
     });
    handler.postDelayed(this::showNextBirthday,60000);
  }



}
