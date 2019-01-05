package me.andrew.testhook;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import me.andrew.xhook.Param;
import me.andrew.xhook.HookCallback;
import me.andrew.xhook.HookManager;

public final class MainActivity extends AppCompatActivity {

    private static Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = getApplicationContext();

        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HookManager.addHookMethod(MainActivity.class, "showToast", new Object[] {String.class, int.class}, new HookCallback() {
                    @Override
                    protected void beforeHookedMethod(Param param) throws Throwable {
                        super.beforeHookedMethod(param);
                        param.setCallOrigin(false);
                    }

                    @Override
                    protected void afterHookedMethod(Param param) throws Throwable {
                        super.afterHookedMethod(param);
                        if (!param.isCallOrigin()) {
                            Toast toast = Toast.makeText(MainActivity.this, "toast after", Toast.LENGTH_SHORT);
                            toast.show();
                        }
                    }
                });
                HookManager.addHookConstructor(HookTest.class, new Object[]{Context.class, byte.class}, new HookCallback() {
                    @Override
                    protected void beforeHookedMethod(Param param) throws Throwable {
                        super.beforeHookedMethod(param);
                        param.args[1] = (byte) 1;
                    }

                    @Override
                    protected void afterHookedMethod(Param param) throws Throwable {
                        super.afterHookedMethod(param);
                    }
                });
                HookManager.startHook();
            }
        });

        findViewById(R.id.hello).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showToast("toast before", 1);

                new HookTest(MainActivity.this, (byte) 0).showToast();
            }
        });
    }

    private static final int showToast(String msg, int a) {
        Toast toast = Toast.makeText(mContext, msg, Toast.LENGTH_SHORT);
        toast.show();
        return 1;
    }

    private final static class HookTest {
        private Context mContext;
        private byte mByte;
        public HookTest(Context context, byte a) {
            mByte = a;
            mContext = context;
        }

        public void showToast() {
            Toast toast = Toast.makeText(mContext, "constructor hook byte is " + mByte, Toast.LENGTH_SHORT);
            toast.show();
        }
    }
}
