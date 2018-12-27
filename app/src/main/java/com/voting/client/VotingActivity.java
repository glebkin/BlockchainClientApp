package com.voting.client;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.voting.client.utils.BlockchainClientUtils;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VotingActivity extends AppCompatActivity {
    private DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!isLoggedIn()) {
            Intent loginIntent = new Intent(this, LoginActivity.class);
            this.startActivity(loginIntent);
        }

        if (isVoted()) {
            Intent resultIntent = new Intent(this, ResultsActivity.class);
            this.startActivity(resultIntent);
        }

        BlockchainClientUtils.syncNodesList();

        setContentView(R.layout.activity_voting);


        final RadioGroup radioGroup = findViewById(R.id.candidates_list);
        databaseReference = FirebaseDatabase.getInstance().getReference();
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                radioGroup.removeAllViews();
                BlockchainClientUtils.candidatesMap.clear();

                for (DataSnapshot candidatesList : dataSnapshot.child("candidates").getChildren()) {
                    for (DataSnapshot namesList : candidatesList.getChildren()) {
                        Object name = namesList.getValue();
                        if (name != null) {
                            BlockchainClientUtils.candidatesMap.put(candidatesList.getKey(), name.toString());

                            RadioButton radioButton = new RadioButton(getApplicationContext());
                            radioButton.setText(namesList.getValue().toString());
                            radioGroup.addView(radioButton);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                radioGroup.setAlpha(.3f);
                ProgressBar progressBar = VotingActivity.this.findViewById(R.id.progressBar);
                progressBar.setVisibility(View.VISIBLE);

                while (BlockchainClientUtils.nodesList.isEmpty()) {
                    BlockchainClientUtils.syncNodesList();
                }
                RadioButton radioButton = findViewById(checkedId);

                for (Map.Entry<String, String> pair : BlockchainClientUtils.candidatesMap.entrySet()) {
                    if (radioButton.getText().toString().equals(pair.getValue())) {
                        String userKey = new String(Hex.encodeHex(DigestUtils.sha256(
                                UUID.randomUUID().toString() + System.currentTimeMillis())));
                        String candidateKey = pair.getKey();

                        vote(userKey, candidateKey, 0);
                    }
                }
            }
        });
    }

    public void vote(final String userKey, final String candidateKey, final int nodeIndex) {
        try {
            if (BlockchainClientUtils.nodesList.size() > nodeIndex) {

                OkHttpClient client = new OkHttpClient();
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("sender", userKey);
                jsonObject.put("recipient", candidateKey);
                RequestBody requestBody = RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"), jsonObject.toString());

                Request request = new Request.Builder()
                        .url(BlockchainClientUtils.nodesList.get(nodeIndex) + "/transactions/add")
                        .post(requestBody)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        call.cancel();
                        Log.e("VotingActivity", "Voting call failed");
                        BlockchainClientUtils.nodesList.remove(nodeIndex);
                        VotingActivity.this.vote(userKey, candidateKey, nodeIndex + 1);
                    }

                    @Override
                    public void onResponse(@NonNull final Call call, @NonNull final Response response) throws IOException {

                        VotingActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (response.isSuccessful()) {
                                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(VotingActivity.this);
                                    preferences.edit().putBoolean("isVoted", true).apply();

                                    Intent resultIntent = new Intent(VotingActivity.this, ResultsActivity.class);
                                    VotingActivity.this.startActivity(resultIntent);
                                } else {
                                    call.cancel();
                                    VotingActivity.this.vote(userKey, candidateKey, nodeIndex + 1);
                                }
                            }
                        });
                    }
                });
            } else {
                RadioGroup rg = VotingActivity.this.findViewById(R.id.candidates_list);
                rg.setAlpha(1);
                ProgressBar progressBar = VotingActivity.this.findViewById(R.id.progressBar);
                progressBar.setVisibility(View.INVISIBLE);

                BlockchainClientUtils.syncNodesList();
            }
        } catch (Exception e) {
            Log.e("VotingActivity", e.getMessage(), e);
        }
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
            case R.id.action_add_candidate:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                final EditText editText = new EditText(this);
                builder.setView(editText);
                builder.setTitle("Добавить кандидата")
                        .setPositiveButton("Добавить", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String candidateName = editText.getText().toString();
                                if (StringUtils.isNotBlank(candidateName)) {
                                    String uid = UUID.randomUUID().toString();
                                    databaseReference.child("candidates").child(
                                            new String(Hex.encodeHex(DigestUtils.sha256(
                                                    uid + System.currentTimeMillis()))))
                                            .child("name").setValue(candidateName);
                                    Toast.makeText(VotingActivity.this,
                                            "Новый кандидат добавлен",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(VotingActivity.this,
                                            "Пустое поле ввода",
                                            Toast.LENGTH_SHORT).show();
                                }


                            }
                        })
                        .setNegativeButton("Отмена", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
                break;
            case R.id.action_clear_candidates:
                databaseReference.child("candidates").removeValue();
                Toast.makeText(VotingActivity.this, "Все кандидаты были удалены",
                        Toast.LENGTH_SHORT).show();
                break;
        }

        return true;
    }

    private boolean isLoggedIn() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        return preferences.getBoolean("isLoggedIn", false);
    }

    private boolean isVoted() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        return preferences.getBoolean("isVoted", false);
    }
}
