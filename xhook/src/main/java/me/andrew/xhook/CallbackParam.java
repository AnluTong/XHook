package me.andrew.xhook;

import java.lang.reflect.Member;

/**
 * created by andrew.tong
 * on 18-12-11.
 */

public class CallbackParam {
    public Member method;
    public Object thisObject;
    public Object[] args;

    private Object result = null;
    private Throwable throwable = null;
    boolean returnEarly = false;

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
        this.throwable = null;
        this.returnEarly = true;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public boolean hasThrowable() {
        return throwable != null;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
        this.result = null;
        this.returnEarly = true;
    }

    public Object getResultOrThrowable() throws Throwable {
        if (throwable != null)
            throw throwable;
        return result;
    }
}
