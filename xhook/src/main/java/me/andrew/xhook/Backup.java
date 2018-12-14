package me.andrew.xhook;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

/**
 * created by andrew.tong
 * on 18-12-11.
 */

class Backup {

    public Method invoker;
    public Member newMethod;
    public Member oldMethod;
    public Object callback;

    public Backup invoker(Method invoker) {
        this.invoker = invoker;
        return this;
    }

    public Backup newMethod(Member newMethod) {
        this.newMethod = newMethod;
        return this;
    }

    public Backup oldMethod(Member oldMethod) {
        this.oldMethod = oldMethod;
        return this;
    }

    public Backup callback(Object callback) {
        this.callback = callback;
        return this;
    }

}
