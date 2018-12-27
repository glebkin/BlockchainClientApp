package com.voting.client;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.voting.client.utils.BlockchainClientUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ResultsActivity extends AppCompatActivity {
    Map<String, Integer> resultMap = new HashMap<>();
    PieDataSet dataSet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        getResult(0);

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.refresh_chart);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                getResult(0);
            }
        });

        updateChart();
    }

    public void getResult(int nodeIndex) {
        try {
            if (nodeIndex == BlockchainClientUtils.nodesList.size()) {
                BlockchainClientUtils.syncNodesList();
                nodeIndex = 0;
            }

            final OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url(BlockchainClientUtils.nodesList.get(nodeIndex) + "/result")
                    .get()
                    .build();

            final int finalNodeIndex = nodeIndex;
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    call.cancel();
                    getResult(finalNodeIndex + 1);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseStr = response.body().string();
                        responseStr = responseStr.replace("\"", "")
                                .replace("{", "")
                                .replace("}", "");

                        String[] pairs = responseStr.split(",");
                        for (String pair : pairs) {

                            if (StringUtils.isNotBlank(pair)) {
                                String[] resArr = pair.split(":");
                                resultMap.put(resArr[0], Integer.valueOf(resArr[1]));
                            }
                        }

                        ResultsActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ResultsActivity.this.updateChart();
                                SwipeRefreshLayout swipeRefreshLayout = ResultsActivity.this.findViewById(R.id.refresh_chart);
                                swipeRefreshLayout.setRefreshing(false);
                            }
                        });
                    } else {
                        Log.e(ResultsActivity.class.getName(), "Something went wrong");
                        call.cancel();
                        getResult(finalNodeIndex + 1);
                    }
                }
            });
        } catch (Exception e) {
            Log.e("ResultActivity", e.getMessage(), e);
        }

    }

    private void updateChart() {
        PieChart pieChart = findViewById(R.id.results_chart);
        pieChart.getDescription().setEnabled(false);
        pieChart.setUsePercentValues(true);
        pieChart.setEntryLabelColor(Color.BLACK);

        List<PieEntry> values = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : resultMap.entrySet()) {
            values.add(new PieEntry(entry.getValue(), BlockchainClientUtils.candidatesMap.get(entry.getKey())));
        }
        dataSet = new PieDataSet(values, "Результаты голосования");

        ArrayList<Integer> colors = new ArrayList<>();

        for (int c : ColorTemplate.VORDIPLOM_COLORS)
            colors.add(c);

        for (int c : ColorTemplate.JOYFUL_COLORS)
            colors.add(c);

        for (int c : ColorTemplate.COLORFUL_COLORS)
            colors.add(c);

        for (int c : ColorTemplate.LIBERTY_COLORS)
            colors.add(c);

        for (int c : ColorTemplate.PASTEL_COLORS)
            colors.add(c);

        colors.add(ColorTemplate.getHoloBlue());

        dataSet.setColors(colors);

        if (resultMap.isEmpty()) {
            pieChart.setCenterText("Недостаточно данных");
        } else {
            pieChart.setCenterText("");
        }

        PieData pieData = new PieData(dataSet);
        pieData.setValueFormatter(new PercentFormatter());
        pieData.setValueTextSize(11f);

        pieChart.setData(pieData);
        pieChart.invalidate();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_voting, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_logout:
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                preferences.edit().putBoolean("isLoggedIn", false).apply();
                preferences.edit().putBoolean("isVoted", false).apply();
                Intent loginIntent = new Intent(this, LoginActivity.class);
                this.startActivity(loginIntent);
                break;
        }

        return true;
    }
}
