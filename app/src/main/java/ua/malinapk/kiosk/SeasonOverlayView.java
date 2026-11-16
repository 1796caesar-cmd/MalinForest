package ua.malinapk.kiosk;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.util.Calendar;
import java.util.Random;

/** Lightweight seasonal overlay; draws particles without intercepting touches. */
public class SeasonOverlayView extends View {
  private static class Particle {float x,y,size,phase,sway;int tone;}
  private final Random random=new Random();
  private final Paint paint=new Paint(3);
  private Particle[] particles=new Particle[0];
  private int lastWidth,lastHeight,lastCount,lastSeason=-1;
  private long lastFrame=System.currentTimeMillis();
  public SeasonOverlayView(Context context){super(context);setClickable(false);setFocusable(false);}
  private int season(){
    int mode=getContext().getSharedPreferences("MainActivity",0).getInt("season_mode",0);
    if(mode!=0)return mode;
    int m=Calendar.getInstance().get(Calendar.MONTH);
    return m==11||m<=1?1:m<=4?2:m<=7?3:4;
  }
  private void reset(int w,int h,int count,int mode){
    lastWidth=w;lastHeight=h;lastCount=count;lastSeason=mode;
    particles=new Particle[count];
    for(int i=0;i<count;i++){
      Particle p=new Particle();p.x=random.nextFloat()*w;p.y=random.nextFloat()*h;
      p.size=2+random.nextFloat()*6;p.phase=random.nextFloat()*6.283f;
      p.sway=0.5f+random.nextFloat()*1.5f;p.tone=random.nextInt(3);particles[i]=p;
    }
  }
  @Override protected void onDraw(Canvas canvas){
    super.onDraw(canvas);
    if(getVisibility()!=VISIBLE||getWidth()==0||getHeight()==0)return;
    android.content.SharedPreferences prefs=getContext().getSharedPreferences("MainActivity",0);
    int mode=season(),density=prefs.getInt("season_density",1),speed=prefs.getInt("season_speed",0);
    int count=(density==0?18:density==2?75:38);
    int w=getWidth(),h=getHeight();
    if(lastWidth!=w||lastHeight!=h||lastCount!=count||lastSeason!=mode)reset(w,h,count,mode);
    long now=System.currentTimeMillis();
    float dt=Math.min(0.06f,Math.max(0.001f,(now-lastFrame)/1000f));lastFrame=now;
    float factor=speed==0?0.55f:speed==2?1.65f:1f;
    for(Particle p:particles){
      p.phase+=dt*(mode==3?1.2f:2.0f);
      float drift=(float)Math.sin(p.phase)*p.sway;
      p.x+=drift*factor;
      p.y+=(mode==3?-7:mode==2?16:mode==4?26:20)*dt*factor*(0.5f+p.size/7f);
      if(p.y>h+20)p.y=-20;
      if(p.y< -20)p.y=h+20;
      if(p.x>w+20)p.x=-20;if(p.x< -20)p.x=w+20;
      if(mode==1){paint.setColor(0xDDEFF9FF);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(p.x,p.y,p.size*.55f,paint);}
      else if(mode==2){paint.setColor(p.tone==0?0xCFFFF1FA:0xDDEFFFF5);paint.setStyle(Paint.Style.FILL);canvas.save();canvas.rotate((float)Math.sin(p.phase)*35,p.x,p.y);canvas.drawOval(p.x-p.size,p.y-p.size*.35f,p.x+p.size,p.y+p.size*.35f,paint);canvas.restore();}
      else if(mode==3){int alpha=110+(int)(95*(0.5+0.5*Math.sin(p.phase)));paint.setColor(Color.argb(alpha,255,220,105));paint.setStyle(Paint.Style.FILL);canvas.drawCircle(p.x,p.y,p.size*.5f,paint);}
      else {paint.setColor(p.tone==0?0xDDE6B14D:p.tone==1?0xDDBF6735:0xDDEB8B2B);paint.setStyle(Paint.Style.FILL);canvas.save();canvas.rotate(p.phase*50,p.x,p.y);canvas.drawOval(p.x-p.size,p.y-p.size*.55f,p.x+p.size,p.y+p.size*.55f,paint);canvas.restore();}
    }
    postInvalidateDelayed(33);
  }
  @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(visibility==VISIBLE){lastFrame=System.currentTimeMillis();invalidate();}}
}
