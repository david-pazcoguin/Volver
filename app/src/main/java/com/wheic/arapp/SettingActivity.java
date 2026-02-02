package com.wheic.arapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class SettingActivity extends AppCompatActivity {

    LinearLayout linearLayoutBack, linearLayoutLogout, linearLayoutMyAccount;
    String Username;
    TextView tvFullName;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setting_activity);

        linearLayoutBack = findViewById(R.id.linearLayoutBack);
        tvFullName = findViewById(R.id.tvFullName);
        linearLayoutLogout = findViewById(R.id.linearLayoutLogout);
        linearLayoutMyAccount = findViewById(R.id.linearLayoutMyAccount);

        SharedPreferences sh = getSharedPreferences("Volver", Context.MODE_PRIVATE);

        Username = sh.getString("username", "");

        linearLayoutBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        linearLayoutLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Dialog dialog = new Dialog(SettingActivity.this);

                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCancelable(true);
                dialog.setCanceledOnTouchOutside(true);
                dialog.setContentView(R.layout.logout_confirmation_layout);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
                dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                Window window = dialog.getWindow();
                WindowManager.LayoutParams wlp = window.getAttributes();

                wlp.gravity = Gravity.BOTTOM;

                window.setAttributes(wlp);

                CardView cardViewNo, cardViewYes;

                cardViewNo = dialog.findViewById(R.id.cardViewNo);
                cardViewYes = dialog.findViewById(R.id.cardViewYes);

                cardViewNo.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.dismiss();
                    }
                });

                cardViewYes.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.dismiss();

                        SharedPreferences settings = getSharedPreferences("Volver", Context.MODE_PRIVATE);
                        settings.edit().clear().commit();

                        finishAffinity();

                        Intent intent= new Intent(SettingActivity.this, LoginActivity.class);
                        startActivity(intent);
                    }
                });
                dialog.show();
            }
        });

        linearLayoutMyAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent= new Intent(SettingActivity.this, AccountSettingActivity.class);
                startActivity(intent);
            }
        });

        LoadAccount();
    }

    void LoadAccount()
    {
        String url = URLDatabase.URL_HOME;

        RequestQueue queue = Volley.newRequestQueue(SettingActivity.this);

        StringRequest request = new StringRequest(Request.Method.POST, url, new com.android.volley.Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    if(!response.equals("[]"))
                    {
                        JSONObject jsonObject = new JSONObject(response);

                        JSONArray jsonArray = jsonObject.getJSONArray("data");
                        for(int i = 0; i < jsonArray.length(); i++)
                        {
                            JSONObject jsonObjectData = jsonArray.getJSONObject(i);
                            String firstName = jsonObjectData.getString("first_name");
                            String lastName = jsonObjectData.getString("last_name");
                            /*String picture = jsonObjectData.getString("picture");

                            if(!picture.equals("null"))
                            {
                                byte[] decodedString = Base64.decode(picture, Base64.DEFAULT);
                                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                                imgViewPicture.setImageBitmap(decodedByte);
                            }*/

                            tvFullName.setText(firstName + " " + lastName);
                        }
                    }

                } catch (Exception e) {

                    Toast.makeText(SettingActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }, new com.android.volley.Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error)
            {
                Toast.makeText(SettingActivity.this, error.toString(), Toast.LENGTH_SHORT).show();
            }
        }) {
            @Override
            public String getBodyContentType() {
                return "application/x-www-form-urlencoded; charset=UTF-8";
            }

            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String> params = new HashMap<String, String>();
                params.put("username", Username);
                return params;
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(
                10000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        );
        queue.add(request);
    }
}