# Add project specific ProGuard rules here.
-keep class me.pj.jqb.hook.** { *; }
-keep class me.pj.jqb.model.** { *; }
-keep class me.pj.jqb.ClipBoxApp {
    public boolean isXposedModuleActive();
}
