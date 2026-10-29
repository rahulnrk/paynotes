package in.prardhana.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.Locale;

/** A real data chart. Zero observations remain zero; the chart never fills missing periods with demos. */
final class SpendingChart extends View {
    interface Selection { void choose(int index); }
    private final PaynotesData.Overview data;
    private final Selection selection;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path comparisonPath=new Path();
    private final DashPathEffect comparisonDash;
    private final Typeface chartFont=Typeface.create("sans-serif",Typeface.NORMAL);
    public SpendingChart(Context context){this(context,null,index->{});}
    private int selected = -1;
    private float downX, downY;
    SpendingChart(Context context, PaynotesData.Overview data, Selection selection) {
        super(context); this.data=data;this.selection=selection;comparisonDash=new DashPathEffect(new float[]{dp(3),dp(4)},0);
        setContentDescription("Recorded spending chart. Use the period detail controls below for individual values.");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    float dp(float value){return value*getResources().getDisplayMetrics().density;}
    void select(int index){selected=index;invalidate();}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(data==null)return;
        float left=dp(4), right=getWidth()-dp(4), top=dp(24), bottom=getHeight()-dp(30), width=right-left;
        long max=1; for(long n:data.bars)max=Math.max(max,n);for(long n:data.prior)max=Math.max(max,n);
        paint.setTypeface(chartFont);paint.setTextSize(dp(11));paint.setStrokeWidth(dp(1));
        for(int i=0;i<3;i++) {
            float y=top+(bottom-top)*i/2;
            paint.setColor(0xffe1e8eb);canvas.drawLine(left,y,right,y,paint);
        }
        paint.setColor(0xff586a73);canvas.drawText(compact(max),left,dp(13),paint);
        float step=width/Math.max(data.bars.length,data.prior.length), bar=Math.min(dp(24),step*.58f);
        for(int i=0;i<data.bars.length;i++) {
            float x=left+step*(i+.5f), y=bottom-(bottom-top)*data.bars[i]/max;
            paint.setColor(selected<0||selected==i?0xff087f8c:0xffb9d9dc);
            if(data.bars[i]>0)canvas.drawRoundRect(x-bar/2,y,x+bar/2,bottom,dp(3),dp(3),paint);
        }
        if(data.priorEntries>0) {
            int last=data.period.bucket(Math.max(data.period.millis(data.period.previous),data.period.previousUntil-1),data.period.previous);
            Path path=comparisonPath;path.reset();for(int i=0;i<data.prior.length&&i<=last;i++){float x=left+step*(i+.5f),y=bottom-(bottom-top)*data.prior[i]/max;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
            paint.setColor(0xff82959e);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.6f));paint.setPathEffect(comparisonDash);canvas.drawPath(path,paint);paint.setPathEffect(null);paint.setStyle(Paint.Style.FILL);
        }
        paint.setColor(0xff586a73);paint.setTextAlign(Paint.Align.CENTER);
        for(int i=0;i<data.bars.length;i++) {
            String label="";
            if(data.period.mode.equals("Day")){if(i%6==0)label=String.format(Locale.ENGLISH,"%02d",i);if(i==23)label="23";}
            else if(data.period.mode.equals("Week"))label=data.period.start.plusDays(i).getDayOfWeek().toString().substring(0,1);
            else if(i==0||i==data.bars.length-1||i%7==0)label=""+(i+1);
            if(!label.isEmpty())canvas.drawText(label,left+step*(i+.5f),getHeight()-dp(7),paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }
    static String compact(long paise){double n=paise/100.0;return n>=100000?String.format(Locale.ENGLISH,"₹%.1fL",n/100000):n>=1000?String.format(Locale.ENGLISH,"₹%.1fk",n/1000):String.format(Locale.ENGLISH,"₹%.0f",n);}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        if(data==null)return super.onTouchEvent(e);
        if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}
        if(e.getAction()==MotionEvent.ACTION_UP){if(Math.abs(e.getY()-downY)<dp(20)&&Math.abs(e.getX()-downX)<dp(30)){int index=Math.min(data.bars.length-1,Math.max(0,(int)((e.getX()-dp(4))/(getWidth()-dp(8))*Math.max(data.bars.length,data.prior.length))));select(index);selection.choose(index);performClick();}return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
