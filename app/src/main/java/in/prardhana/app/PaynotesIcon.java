package in.prardhana.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small consistent stroke icons, with labels supplied by their parent controls. */
final class PaynotesIcon extends View {
    private final String kind;
    private final int color;
    private final Path bell=new Path();
    public PaynotesIcon(Context c){this(c,"Overview",PaynotesUi.TEAL);}
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    PaynotesIcon(Context c,String kind,int color){super(c);this.kind=kind;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);bell.moveTo(5,17);bell.lineTo(7,14);bell.lineTo(7,9);bell.cubicTo(7,2,17,2,17,9);bell.lineTo(17,14);bell.lineTo(19,17);bell.close();}
    @Override protected void onDraw(Canvas c){
        c.save();float scale=Math.min(getWidth(),getHeight())/24f;c.translate((getWidth()-24*scale)/2,(getHeight()-24*scale)/2);c.scale(scale,scale);
        p.setColor(color);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
        switch(kind){
            case "Overview" -> {c.drawLine(4,19,4,12,p);c.drawLine(10,19,10,5,p);c.drawLine(16,19,16,9,p);c.drawLine(22,19,22,2,p);}
            case "Activity" -> {for(int y=6;y<=18;y+=6){c.drawCircle(4,y,.7f,p);c.drawLine(9,y,21,y,p);}}
            case "Insights" -> {c.drawRoundRect(3,3,21,18,5,5,p);c.drawLine(7,18,5,22,p);c.drawLine(5,22,13,18,p);c.drawLine(8,9,16,9,p);c.drawLine(8,13,13,13,p);}
            case "Settings" -> {for(int y=6;y<=18;y+=6){c.drawLine(3,y,21,y,p);p.setStyle(Paint.Style.FILL);p.setColor(0xfff7f9fa);float x=y==12?16:8;c.drawCircle(x,y,3,p);p.setColor(color);p.setStyle(Paint.Style.STROKE);c.drawCircle(x,y,2.5f,p);}}
            case "profile" -> {c.drawCircle(12,8,4,p);c.drawArc(4,13,20,28,190,160,false,p);}
            case "chevron" -> {c.drawLine(9,6,15,12,p);c.drawLine(15,12,9,18,p);}
            case "message" -> {c.drawRoundRect(3,4,21,18,3,3,p);c.drawLine(7,18,7,22,p);c.drawLine(7,22,12,18,p);c.drawLine(7,9,17,9,p);c.drawLine(7,13,14,13,p);}
            case "notification" -> {c.drawPath(bell,p);c.drawArc(10,18,14,22,0,180,false,p);}
            case "arrow" -> {c.drawLine(12,19,12,5,p);c.drawLine(6,11,12,5,p);c.drawLine(18,11,12,5,p);}
            default -> {c.drawRoundRect(4,3,20,21,3,3,p);c.drawLine(8,8,16,8,p);c.drawLine(8,12,16,12,p);c.drawLine(8,16,13,16,p);}
        }
        c.restore();
    }
}
