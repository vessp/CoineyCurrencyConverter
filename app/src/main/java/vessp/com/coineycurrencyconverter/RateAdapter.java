package vessp.com.coineycurrencyconverter;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * Adapter for main exchange rates list
 */

public class RateAdapter extends ArrayAdapter<Rate>
{

    Context context;
    int layoutResourceId;
    public ArrayList<Rate> rates = null;

    public RateAdapter(Context context, int layoutResourceId, ArrayList<Rate> rates) {
        super(context, layoutResourceId, rates);
        this.layoutResourceId = layoutResourceId;
        this.context = context;
        this.rates = rates;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        RateHolder holder = null;

        if(row == null)
        {
            LayoutInflater inflater = ((Activity)context).getLayoutInflater();
            row = inflater.inflate(layoutResourceId, parent, false);

            holder = new RateHolder();
            holder.name = (TextView)row.findViewById(R.id.name);
            holder.rate = (TextView)row.findViewById(R.id.rate);

            row.setTag(holder);
        }
        else
        {
            holder = (RateHolder)row.getTag();
        }

        Rate rate = rates.get(position);
        holder.name.setText(rate.name);

        DecimalFormat df = new DecimalFormat("0.0000");
        holder.rate.setText(df.format(rate.value));

        return row;
    }

    static class RateHolder
    {
        TextView name;
        TextView rate;
    }
}
