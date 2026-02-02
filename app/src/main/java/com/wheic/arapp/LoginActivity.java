package com.wheic.arapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LoginActivity extends AppCompatActivity {

    EditText txtUsername, txtPassword;
    CardView cardViewLogin;
    TextView tvRegister;
    TextView tvShowHide;
    String Username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        SharedPreferences sh = getSharedPreferences("Volver", Context.MODE_PRIVATE);

        Username = sh.getString("username", "");

        if(!Username.isEmpty())
        {
            Intent intent= new Intent(LoginActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
        }

        txtUsername = findViewById(R.id.txtUsername);
        txtPassword = findViewById(R.id.txtPassword);
        cardViewLogin = findViewById(R.id.cardViewLogin);
        tvRegister = findViewById(R.id.tvRegister);
        tvShowHide = findViewById(R.id.tvShowHide);

        tvShowHide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(tvShowHide.getText().toString().equals("SHOW"))
                {
                    tvShowHide.setText("HIDE");
                    txtPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                }
                else
                {
                    tvShowHide.setText("SHOW");
                    txtPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                }
            }
        });

        txtUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                LoginChecker();
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                LoginChecker();
            }

            @Override
            public void afterTextChanged(Editable editable) {
                LoginChecker();
            }
        });

        txtPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                LoginChecker();
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                LoginChecker();
            }

            @Override
            public void afterTextChanged(Editable editable) {
                LoginChecker();
            }
        });

        tvRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent= new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });

        cardViewLogin.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                String url = URLDatabase.URL_LOGIN;

                RequestQueue queue = Volley.newRequestQueue(LoginActivity.this);

                StringRequest request = new StringRequest(Request.Method.POST, url, new com.android.volley.Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject jsonObject = new JSONObject(response);
                            if (jsonObject.getString("user_id").equals("") || jsonObject.getString("user_id").equals("null"))
                            {
                                Toast.makeText(LoginActivity.this, "Invalid credentials.", Toast.LENGTH_SHORT).show();
                            }
                            else
                            {
                                SharedPreferences sharedPreferences = getSharedPreferences("Volver",MODE_PRIVATE);

                                SharedPreferences.Editor myEdit = sharedPreferences.edit();

                                myEdit.putString("username", txtUsername.getText().toString());

                                myEdit.commit();

                                Intent intent= new Intent(LoginActivity.this, HomeActivity.class);
                                startActivity(intent);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new com.android.volley.Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        Toast.makeText(LoginActivity.this, error.toString(), Toast.LENGTH_SHORT).show();
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
                        params.put("username", txtUsername.getText().toString());
                        params.put("password", txtPassword.getText().toString());
                        return params;
                    }
                };
                queue.add(request);
            }
        });
    }

    void LoginChecker()
    {
        if(txtPassword.getText().toString().isEmpty() || txtUsername.getText().toString().isEmpty())
        {
            cardViewLogin.setAlpha(0.2f);
            cardViewLogin.setClickable(false);
            cardViewLogin.setFocusable(false);
        }
        else
        {
            cardViewLogin.setAlpha(1);
            cardViewLogin.setClickable(true);
            cardViewLogin.setFocusable(true);
        }
    }
}