package me.andrew.testhook;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import me.andrew.xhook.CallbackParam;
import me.andrew.xhook.HookCallback;
import me.andrew.xhook.HookManager;

public final class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HookManager.addHookMethod(MainActivity.class, "showToast", String.class, int.class, new HookCallback() {
                    @Override
                    protected void beforeHookedMethod(CallbackParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                    }

                    @Override
                    protected void afterHookedMethod(CallbackParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        if (!callOrigin()) {
                            Toast toast = Toast.makeText(MainActivity.this, "toast after", Toast.LENGTH_SHORT);
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
                showToast("toast before", 1);
            }
        });
    }

    private final byte showToast(String msg, int a) {
        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        toast.show();
        return 1;
    }
}
