package me.andrew.xhook;

/**
 * created by andrew.tong
 * on 18-12-11.
 */

public abstract class HookCallback {
	public HookCallback() {
	}

	protected void beforeHookedMethod(CallbackParam param) throws Throwable {}
	protected void afterHookedMethod(CallbackParam param) throws Throwable {}

	protected boolean callOrigin() {
		return true;
	}
}
