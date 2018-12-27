package com.voting.client.utils;

import android.support.annotation.NonNull;
import android.util.Log;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BlockchainClientUtils {
    private static final String masterNodeUrl = "http://30866167.ngrok.io/";

    public static Map<String, String> candidatesMap = new HashMap<>();
    public static List<String> nodesList = new ArrayList<>();

    public static void syncNodesList() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(masterNodeUrl).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("BlockchainClientUtils", e.getMessage(), e);
                call.cancel();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws
                    IOException {
                ResponseBody responseBody = response.body();

                if (responseBody != null) {
                    String urls = responseBody.string();
                    urls = urls.replace("\"", "")
                            .replace("[", "")
                            .replace("]", "");

                    List<String> responseUrlsList = new ArrayList<>(Arrays.asList(urls.split(",")));

                    if (!responseUrlsList.isEmpty()) {
                        nodesList = new ArrayList<>(responseUrlsList);
                    }
                }
            }
        });
    }

}
