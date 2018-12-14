package me.andrew.xhook;

import java.lang.reflect.Member;

/**
 * created by andrew.tong
 * on 18-12-11.
 */

public class Param {
    public Member method; //origin method
    public Object thisObject; //origin this
    public Object[] args; //origin argus

    Object result = null; //hook method return value
    boolean callOrigin = true; //origin method call or not

    public void setResult(Object result) {
        this.result = result;
    }

    public void setCallOrigin(boolean callOrigin) {
        this.callOrigin = callOrigin;
    }

    public boolean isCallOrigin() {
        return callOrigin;
    }
}
