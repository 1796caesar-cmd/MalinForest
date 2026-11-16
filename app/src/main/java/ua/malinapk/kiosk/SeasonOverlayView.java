package ua.malinapk.kiosk;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.util.Calendar;
import java.util.Random;

/** Decorative overlay: no images, network or touch interception. */
public class SeasonOverlayView extends View {
  private static final float TAU=(float)(Math.PI*2);
  private static class Particle {
    float x,y,size,phase,rotation,angular,drift,depth,twinkle;
    int tone,shape;
  }
  private final Random random=new Random();
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path=new Path();
  private Particle[] particles=new Particle[0];
  private int lastWidth=-1,lastHeight=-1,lastCount=-1,lastSeason=-1;
  private long lastFrame=System.currentTimeMillis();

  public SeasonOverlayView(Context context){
    super(context);
    setClickable(false);setFocusable(false);
    setLayerType(View.LAYER_TYPE_SOFTWARE,null);
  }
  private SharedPreferences prefs(){
    return getContext().getSharedPreferences("MainActivity",Context.MODE_PRIVATE);
  }
  private int season(){
    int chosen=prefs().getInt("season_mode",0);
    if(chosen>=1&&chosen<=4)return chosen;
    int m=Calendar.getInstance().get(Calendar.MONTH);
    return m==11||m<=1?1:m<=4?2:m<=7?3:4;
  }
  private void reset(int w,int h,int count,int mode){
    lastWidth=w;lastHeight=h;lastCount=count;lastSeason=mode;
    particles=new Particle[count];
    for(int i=0;i<count;i++){
      Particle p=new Particle();
      p.x=random.nextFloat()*w;p.y=random.nextFloat()*h;
      p.depth=.45f+random.nextFloat()*.95f;
      p.size=(mode==1?5:mode==3?3:8)+(mode==1?12:mode==3?6:17)*random.nextFloat();
      p.size*=p.depth;
      p.phase=random.nextFloat()*TAU;
      p.rotation=random.nextFloat()*360;
      p.angular=(random.nextBoolean()?1:-1)*(10+random.nextFloat()*30);
      p.drift=10+random.nextFloat()*22;
      p.twinkle=random.nextFloat()*TAU;
      p.tone=random.nextInt(4);
      p.shape=random.nextInt(mode==1?5:mode==4?2:3);
      particles[i]=p;
    }
    lastFrame=System.currentTimeMillis();
  }
  private void style(int color,Paint.Style type,float width){
    paint.clearShadowLayer();paint.setColor(color);paint.setStyle(type);paint.setStrokeWidth(width);
    paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
  }
  private void snowflake(Canvas c,Particle p){
    float r=p.size;
    int color=p.tone==0?0xEAF7FCFF:p.tone==1?0xBDEAF7FF:0xD6FFFFFF;
    c.save();c.translate(p.x,p.y);c.rotate(p.rotation);
    if(p.shape==4){
      style(0xB7FFFFFF,Paint.Style.FILL,1);
      c.drawCircle(0,0,r*.48f,paint);
      style(color,Paint.Style.STROKE,Math.max(.75f,r*.12f));
      for(int k=0;k<6;k++){
        c.save();c.rotate(k*60);
        c.drawLine(0,-r*.4f,0,-r*.95f,paint);
        c.restore();
      }
    }else{
      style(color,Paint.Style.STROKE,Math.max(.7f,r*(p.shape==0?.09f:.065f)));
      int arms=p.shape==3?8:6;
      for(int k=0;k<arms;k++){
        c.save();c.rotate(k*360f/arms);
        c.drawLine(0,0,0,-r,paint);
        if(p.shape!=0){
          for(int b=1;b<=(p.shape==2?2:1);b++){
            float y=-r*(b==1?.52f:.78f),v=r*(b==1?.22f:.15f);
            c.drawLine(0,y,-v,y+v*.7f,paint);
            c.drawLine(0,y,v,y+v*.7f,paint);
          }
        }
        c.restore();
      }
      if(p.shape==2){style(0xE9FFFFFF,Paint.Style.FILL,1);c.drawCircle(0,0,r*.11f,paint);}
    }
    c.restore();
  }
  private void petal(Canvas c,Particle p){
    c.save();c.translate(p.x,p.y);
    c.rotate(p.rotation+(float)Math.sin(p.phase)*24);
    float r=p.size,flatten=.4f+.4f*Math.abs((float)Math.cos(p.phase*.7f));
    c.scale(1,flatten);
    path.reset();path.moveTo(0,-r);
    path.cubicTo(r*.95f,-r*.75f,r*1.08f,r*.4f,0,r);
    path.cubicTo(-r*1.05f,r*.42f,-r*.9f,-r*.72f,0,-r);
    style(p.tone==0?0xDFFFE8F2:p.tone==1?0xD9FFD4E8:p.tone==2?0xD9FFF7FA:0xE3F6BBD2,Paint.Style.FILL,1);
    c.drawPath(path,paint);
    style(0x78D77F9F,Paint.Style.STROKE,Math.max(.55f,r*.035f));
    c.drawLine(0,-r*.68f,0,r*.58f,paint);
    c.restore();
  }
  private void firefly(Canvas c,Particle p){
    float pulse=(float)(.5+.5*Math.sin(p.twinkle));
    int a=(int)(65+175*pulse);
    int rgb=p.tone==0?0xFFFFE17D:p.tone==1?0xFFCBFF9C:0xFFFFD663;
    style(Color.argb((int)(a*.55f),Color.red(rgb),Color.green(rgb),Color.blue(rgb)),Paint.Style.FILL,1);
    paint.setShadowLayer(p.size*2.7f,0,0,rgb);
    c.drawCircle(p.x,p.y,Math.max(1.1f,p.size*.38f),paint);
    paint.clearShadowLayer();
    style(Color.argb(a,255,251,208),Paint.Style.FILL,1);
    c.drawCircle(p.x,p.y,Math.max(.7f,p.size*.2f),paint);
  }
  private void leaf(Canvas c,Particle p){
    c.save();c.translate(p.x,p.y);
    c.rotate(p.rotation+(float)Math.sin(p.phase)*25);
    c.scale(.58f+.42f*Math.abs((float)Math.cos(p.phase*.6f)),1);
    float r=p.size;
    int color=p.tone==0?0xE7F5B53D:p.tone==1?0xE7C95B2F:p.tone==2?0xE9DC8035:0xE6D84C38;
    path.reset();
    if(p.shape==0){
      // Five pointed, serrated maple leaf.
      float[][] v={{0,-1},{-.17f,-.65f},{-.43f,-.83f},{-.4f,-.48f},{-.79f,-.58f},
        {-.61f,-.25f},{-.92f,-.15f},{-.52f,.07f},{-.62f,.35f},{-.22f,.26f},
        {0,.76f},{.22f,.26f},{.62f,.35f},{.52f,.07f},{.92f,-.15f},
        {.61f,-.25f},{.79f,-.58f},{.4f,-.48f},{.43f,-.83f},{.17f,-.65f}};
      for(int i=0;i<v.length;i++){
        if(i==0)path.moveTo(v[i][0]*r,v[i][1]*r);
        else path.lineTo(v[i][0]*r,v[i][1]*r);
      }
    }else{
      // Rounded lobes along both sides of an oak leaf.
      path.moveTo(0,-r);
      path.cubicTo(-.26f*r,-.93f*r,-.18f*r,-.7f*r,-.45f*r,-.67f*r);
      path.cubicTo(-.85f*r,-.64f*r,-.78f*r,-.33f*r,-.46f*r,-.32f*r);
      path.cubicTo(-.9f*r,-.22f*r,-.87f*r,.1f*r,-.49f*r,.1f*r);
      path.cubicTo(-.78f*r,.3f*r,-.52f*r,.56f*r,-.22f*r,.48f*r);
      path.lineTo(0,.78f*r);
      path.cubicTo(.22f*r,.48f*r,.52f*r,.56f*r,.78f*r,.3f*r);
      path.cubicTo(.87f*r,.1f*r,.9f*r,-.22f*r,.49f*r,.1f*r);
      path.cubicTo(.78f*r,-.33f*r,.85f*r,-.64f*r,.45f*r,-.67f*r);
      path.cubicTo(.18f*r,-.7f*r,.26f*r,-.93f*r,0,-r);
    }
    path.close();
    style(color,Paint.Style.FILL,1);c.drawPath(path,paint);
    style(0x93693B1B,Paint.Style.STROKE,Math.max(.65f,r*.05f));
    c.drawLine(0,-r*.82f,0,r*.98f,paint);
    for(int side=-1;side<=1;side+=2){
      c.drawLine(0,-r*.3f,side*r*.38f,-r*.52f,paint);
      c.drawLine(0,r*.07f,side*r*.47f,-r*.12f,paint);
    }
    c.restore();
  }
  @Override protected void onDraw(Canvas canvas){
    super.onDraw(canvas);
    if(getVisibility()!=VISIBLE||getWidth()<=0||getHeight()<=0)return;
    SharedPreferences pref=prefs();
    int mode=season(),density=pref.getInt("season_density",1),speed=pref.getInt("season_speed",0);
    float sizeFactor=Math.max(50,Math.min(200,pref.getInt("season_size",100)))/100f;
    int count=density==0?15:density==2?60:32;
    int w=getWidth(),h=getHeight();
    if(w!=lastWidth||h!=lastHeight||count!=lastCount||mode!=lastSeason)reset(w,h,count,mode);
    long now=System.currentTimeMillis();
    float dt=Math.min(.06f,Math.max(0,(now-lastFrame)/1000f));
    lastFrame=now;
    float factor=speed==0?.6f:speed==2?1.6f:1f;
    for(Particle p:particles){
      p.phase+=dt*(mode==3?.75f:1.5f);
      p.rotation+=p.angular*dt*factor;
      p.twinkle+=dt*(1.1f+p.depth);
      p.x+=(float)Math.cos(p.phase)*p.drift*dt*factor;
      if(mode==3){
        p.x+=(float)Math.sin(p.twinkle*.4f)*8*dt*factor;
        p.y+=(float)Math.sin(p.phase*.73f)*10*dt*factor;
      }else{
        p.y+=(mode==1?23:mode==2?19:30)*dt*factor*p.depth;
      }
      float margin=Math.max(25,p.size*2);
      if(p.y>h+margin)p.y=-margin;
      if(p.y< -margin)p.y=h+margin;
      if(p.x>w+margin)p.x=-margin;
      if(p.x< -margin)p.x=w+margin;
      canvas.save();
      canvas.translate(p.x,p.y);
      canvas.scale(sizeFactor,sizeFactor);
      canvas.translate(-p.x,-p.y);
      if(mode==1)snowflake(canvas,p);
      else if(mode==2)petal(canvas,p);
      else if(mode==3)firefly(canvas,p);
      else leaf(canvas,p);
      canvas.restore();
    }
    paint.clearShadowLayer();
    postInvalidateDelayed(33);
  }
  @Override protected void onVisibilityChanged(View changed,int visibility){
    super.onVisibilityChanged(changed,visibility);
    if(visibility==VISIBLE){lastFrame=System.currentTimeMillis();invalidate();}
  }
}
