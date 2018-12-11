package me.andrew.xhook;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

/**
 * created by andrew.tong
 * on 18-12-11.
 */

class MethodBackup {

    private Method invoker;
    private Member newMethod;
    private Member oldMethod;
    private Object callback;

    public Method getInvoker() {
        return invoker;
    }

    public void setInvoker(Method invoker) {
        this.invoker = invoker;
    }

    public Member getNewMethod() {
        return newMethod;
    }

    public void setNewMethod(Member newMethod) {
        this.newMethod = newMethod;
    }

    public void setOldMethod(Member oldMethod) {
        this.oldMethod = oldMethod;
    }

    public Member getOldMethod() {
        return oldMethod;
    }

    public Object getCallback() {
        return callback;
    }

    public void setCallback(Object callback) {
        this.callback = callback;
    }

}
