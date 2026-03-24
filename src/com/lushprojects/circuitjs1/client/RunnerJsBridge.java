package com.lushprojects.circuitjs1.client;


import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

final class RunnerJsBridge {
	@JsFunction
	private interface Hook0 {
		void call();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "window")
	private static class RunnerWindowLike {
		@JsProperty(name = "__runnerStepFn") static native void setRunnerStepFn(Hook0 fn);
	}

	private RunnerJsBridge() {
	}

	static void setRunnerStepFn(final Runnable stepFn) {
		if (stepFn == null) {
			RunnerWindowLike.setRunnerStepFn(null);
			return;
		}
		RunnerWindowLike.setRunnerStepFn(new Hook0() {
			public void call() {
				stepFn.run();
			}
		});
	}
}