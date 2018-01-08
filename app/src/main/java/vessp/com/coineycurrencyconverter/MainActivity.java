package vessp.com.coineycurrencyconverter;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity
{

    private static final String PREFS_NAME = "prefs_file";
    private static final String PREFS_CURRENCY_KEY0 = "currency_key0";
    private static final String PREFS_LAST_FETCH_DATE = "last_fetch_date";
    private static final String FILE_RATES_CACHE = "exchange_rates.json";
    private static final String URL_EXCHANGE_RATES = "http://android.coiney.com:1337/exchange_rates";

    public static JSONObject exchangeRates = null;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Initialize exchangeRatesList adapter
        final ListView exchangeRatesList = (ListView)findViewById(R.id.exchange_rates_list);
        exchangeRatesList.setAdapter(new RateAdapter(this, R.layout.rate_list_item, new ArrayList<Rate>()));

        //Initialize currencySpinner listener
        Spinner currencySpinner = (Spinner)findViewById(R.id.currency_spinner);
        currencySpinner.setOnItemSelectedListener(new OnItemSelectedListener()
        {
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long arg3)
            {
                String selectedItem = parent.getItemAtPosition(position).toString();

                //Save selection into prefs
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
                Editor editor = prefs.edit();
                editor.putString(PREFS_CURRENCY_KEY0, selectedItem);
                editor.apply();

                //Populate rates list when currencySpinner selection changes
                try {
                    ArrayList<Rate> rates = ((RateAdapter)exchangeRatesList.getAdapter()).rates;
                    rates.clear();

                    JSONObject rateMap = (JSONObject) exchangeRates.get(selectedItem);
                    Iterator<?> rateKeys = rateMap.keys();
                    while( rateKeys.hasNext() ) {
                        String rateKey = (String)rateKeys.next();
                        rates.add(new Rate(rateKey, (Double) rateMap.get(rateKey)));
                    }
                    ((RateAdapter) exchangeRatesList.getAdapter()).notifyDataSetChanged();
                } catch (JSONException e)
                {
                    e.printStackTrace();
                }

            }

            public void onNothingSelected(AdapterView<?> arg0)
            {
                // TODO Auto-generated method stub
            }
        });
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        restoreRates();
        fetchExchangeRates();
//        testFetchExchangeRates();
    }

    //Convenience function to parse currencies list from json
    private ArrayList<String> getCurrencyList() {
        ArrayList<String> currencyKeys = new ArrayList<>();
        Iterator<?> keys = exchangeRates.keys();
        while( keys.hasNext() ) {
            String key = (String)keys.next();
            currencyKeys.add(key);
        }
        return currencyKeys;
    }


    //----------------------------------------------------------------------------------------------
    // HTTP FETCH
    //----------------------------------------------------------------------------------------------
    public void fetchExchangeRates() {
        JsonObjectRequest jsObjRequest = new JsonObjectRequest
                (Request.Method.GET, URL_EXCHANGE_RATES, null, new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        onExchangeRates(response);
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMsg = getApplicationContext().getString(R.string.error_fetching_rates);
                        NetworkResponse networkResponse = error.networkResponse;
                        if (networkResponse != null) {
                            errorMsg += " (code: " + networkResponse.statusCode + ")";
                        }
                        Toast.makeText(getApplicationContext(), errorMsg, Toast.LENGTH_LONG).show();
                    }
                });

        RequestQueue queue = Volley.newRequestQueue(this);
        queue.add(jsObjRequest);
    }

    //Test only
    public void testFetchExchangeRates() {
        String jsonString = null;
        try {
            InputStream is = getAssets().open("exchange_rates.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            jsonString = new String(buffer, "UTF-8");
            JSONObject obj = new JSONObject(jsonString);
            onExchangeRates(obj);
        }
        catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }


    //----------------------------------------------------------------------------------------------
    // RENDER MAIN CONTENT
    //----------------------------------------------------------------------------------------------
    private void onExchangeRates(final JSONObject currencyMap) { onExchangeRates(currencyMap, false); }
    private void onExchangeRates(final JSONObject currencyMap, boolean isCachedData)
    {
        findViewById(R.id.rates_layout).setVisibility(View.VISIBLE); //Show currency controls
        findViewById(R.id.error_text).setVisibility(View.INVISIBLE); //Hide error text

        //Only show toast if data is fresh
        if(!isCachedData)
            Toast.makeText(this, R.string.rates_updated, Toast.LENGTH_LONG).show();

        exchangeRates = currencyMap;
        cacheRates();
        updateLastFetchDate();

        //Initialise main content adapters
        Spinner currencySpinner = (Spinner)findViewById(R.id.currency_spinner);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<> (this,android.R.layout.simple_spinner_dropdown_item,getCurrencyList());
        currencySpinner.setAdapter(spinnerAdapter);
        final ListView listView = (ListView)findViewById(R.id.exchange_rates_list);

        //Load last currency from prefs
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
        String cachedCurrencyKey = prefs.getString(PREFS_CURRENCY_KEY0, null);
        if(cachedCurrencyKey != null) {
            int selectionIndex = getCurrencyList().indexOf(cachedCurrencyKey);
            if(selectionIndex != -1)
                currencySpinner.setSelection(selectionIndex);
        }
    }

    private void updateLastFetchDate() {
        String dateString = "Last data update on " + DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(new Date());
        ((TextView)findViewById(R.id.last_fetch_date)).setText(dateString);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
        Editor editor = prefs.edit();
        editor.putString(PREFS_LAST_FETCH_DATE, dateString);
        editor.apply();
    }


    //----------------------------------------------------------------------------------------------
    // CONVERTER
    //----------------------------------------------------------------------------------------------
    private void showConverter() {
        if(exchangeRates == null) {
            Toast.makeText(this, R.string.no_rates_data_converter, Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.MyDialogTheme);
        builder.setTitle(R.string.converter_title);
        LayoutInflater inflater = this.getLayoutInflater();
        View alertView = inflater.inflate(R.layout.converter, null);
        builder.setView(alertView);

        final Spinner fromCurrencySpinner = alertView.findViewById(R.id.from_currency_spinner);
        final Spinner toCurrencySpinner = alertView.findViewById(R.id.to_currency_spinner);
        final EditText balanceInput = alertView.findViewById(R.id.balance_input);
        final TextView resultView = alertView.findViewById(R.id.converter_result);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<> (this,android.R.layout.simple_spinner_dropdown_item,getCurrencyList());
        fromCurrencySpinner.setAdapter(spinnerAdapter);
        ArrayAdapter<String> spinnerAdapter2 = new ArrayAdapter<> (this,android.R.layout.simple_spinner_dropdown_item,getCurrencyList());
        toCurrencySpinner.setAdapter(spinnerAdapter2);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
        String cachedCurrencyKey = prefs.getString(PREFS_CURRENCY_KEY0, null);
        if(cachedCurrencyKey != null)
            fromCurrencySpinner.setSelection(getCurrencyList().indexOf(cachedCurrencyKey));

        OnItemSelectedListener spinnerListener = new OnItemSelectedListener()
        {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long arg3)
            {
                renderConverter(fromCurrencySpinner, toCurrencySpinner, balanceInput, resultView);
            }

            public void onNothingSelected(AdapterView<?> arg0) { }
        };

        fromCurrencySpinner.setOnItemSelectedListener(spinnerListener);
        toCurrencySpinner.setOnItemSelectedListener(spinnerListener);

        balanceInput.addTextChangedListener(new TextWatcher()
        {
            @Override public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            @Override public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            @Override
            public void afterTextChanged(Editable editable)
            {
                renderConverter(fromCurrencySpinner, toCurrencySpinner, balanceInput, resultView);
            }
        });
        balanceInput.setText("1.00"); //Start at 1

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void renderConverter(Spinner fromCurrencySpinner, Spinner toCurrencySpinner, EditText et0, TextView tv) {
        String convertedAnswer = "--";
        try
        {
            Double val0 = Double.parseDouble(et0.getText().toString());

            String currency0 = fromCurrencySpinner.getSelectedItem().toString();
            String currency1 = toCurrencySpinner.getSelectedItem().toString();
            Double exchangeRate = currency0.equals(currency1) ? 1.0d //Rate is 1.0 if currencies are same
                    : exchangeRates.getJSONObject(currency0).getDouble(currency1);
            DecimalFormat df = new DecimalFormat("0.00");
            convertedAnswer = df.format(val0 * exchangeRate);
        } catch(NumberFormatException | JSONException e) {
            //Insufficient information available for conversion, render blank
        }
        tv.setText(convertedAnswer);
    }

    //----------------------------------------------------------------------------------------------
    // LOCAL STORAGE
    //----------------------------------------------------------------------------------------------
    private void cacheRates() {
        try {
            ObjectOutput out = new ObjectOutputStream(new FileOutputStream(new File(getCacheDir(),FILE_RATES_CACHE)));
            out.writeUTF( exchangeRates.toString() );
            out.close();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void restoreRates() {
        try {
            File f = new File(getCacheDir(),FILE_RATES_CACHE);
            if(f.exists()) {
                ObjectInputStream in = new ObjectInputStream(new FileInputStream(f));
                String jsonString = in.readUTF();
                in.close();
                onExchangeRates(new JSONObject(jsonString), true);
            }
        } catch (IOException | JSONException e)
        {
            e.printStackTrace();
        }
    }


    //----------------------------------------------------------------------------------------------
    // TOOLBAR MENU
    //----------------------------------------------------------------------------------------------
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_refresh)
        {
            fetchExchangeRates();
            return true;
        }
        else if (id == R.id.action_converter)
        {
            showConverter();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
