package me.andrew.testhook;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import me.andrew.xhook.CallbackParam;
import me.andrew.xhook.HookCallback;
import me.andrew.xhook.HookManager;

public class MainActivity extends AppCompatActivity {

    public static Context mApp;
    private String mText = "aa";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mApp = getApplicationContext();

        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                try {
//                    Method srcMethod = MainActivity.class.getDeclaredMethod("showToast", String.class);
//                    Method backup = HookClass.class.getDeclaredMethod("showToast_backup", Object.class, String.class);
//                    Method hook = HookClass.class.getDeclaredMethod("showToast", Object.class, String.class);
//                    MethodHook.hook_native(srcMethod, hook, backup);
//                } catch (NoSuchMethodException e) {
//                    e.printStackTrace();
//                }
                HookManager.addHookMethod(MainActivity.class, "showToast", String.class, new HookCallback() {
                    @Override
                    protected void beforeHookedMethod(CallbackParam param) throws Throwable {
                        super.beforeHookedMethod(param);
//                        mText = "sdfsdfs";
                    }

                    @Override
                    protected void afterHookedMethod(CallbackParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        if (!callOrigin()) {
                            Toast toast = Toast.makeText(MainActivity.this, "bb", Toast.LENGTH_SHORT);
                            toast.show();
                        }
                    }

                    @Override
                    protected boolean callOrigin() {
                        return false;
                    }
                });
                HookManager.startHook();
            }
        });

        findViewById(R.id.hello).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showToast(mText);
            }
        });

        findViewById(R.id.newintent).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, TestActivity.class));
            }
        });
    }

    public void showToast(String msg) {
        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        toast.show();
    }
}
