package io.storeyes.accesscontrol.tenant;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String schema) { CURRENT.set(schema); }
    public static String get()            { return CURRENT.get(); }
    public static void clear()            { CURRENT.remove(); }
}
