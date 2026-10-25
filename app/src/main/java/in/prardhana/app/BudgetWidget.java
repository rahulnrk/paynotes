package in.prardhana.app;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.Color;
import android.widget.RemoteViews;

public class BudgetWidget extends AppWidgetProvider {
    public static void refresh(Context c) {
        AppWidgetManager m=AppWidgetManager.getInstance(c); int[] ids=m.getAppWidgetIds(new ComponentName(c,BudgetWidget.class));
        for(int id:ids) {
            String period=Store.prefs(c).getString("period","Monthly"); long limit=Store.prefs(c).getLong("budget",0); long spent=Store.spent(c,limit>0?period:"Daily");
            RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.budget_widget);
            v.setTextViewText(R.id.widget_amount,Store.money(limit>0?Math.abs(limit-spent):spent));
            v.setTextViewText(R.id.widget_detail,limit>0?(spent>limit?"Over budget":"Remaining")+" · "+period:"Money out · excluding cash/transfers");
            v.setInt(R.id.widget_root,"setBackgroundColor",Color.parseColor(limit>0&&spent>limit?"#8D3D3A":"#173F36"));
            v.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT)); m.updateAppWidget(id,v);
        }
    }
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids) { refresh(c); }
}
