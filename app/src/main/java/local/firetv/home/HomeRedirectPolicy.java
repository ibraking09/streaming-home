package local.firetv.home;

final class HomeRedirectPolicy {
    static final String AMAZON = "com.amazon.tv.launcher";
    static final String HOME = AMAZON + ".ui.HomeActivity_vNext";
    static final String APPSTORE = "com.amazon.venezia";
    static final String APPSTORE_ENTRY = APPSTORE + ".grid.AppsGridLauncherActivity";

    private boolean systemSession;
    private long lastRedirect = -1;

    static boolean startsAmazonSession(String packageName, String className) {
        return APPSTORE.equals(packageName) && APPSTORE_ENTRY.equals(className);
    }

    void enterSystemSession() { systemSession = true; }
    void leaveSystemSession() { systemSession = false; }

    boolean shouldRedirect(CharSequence packageName, CharSequence className, long elapsedTime) {
        if (systemSession || packageName == null || className == null
                || !AMAZON.contentEquals(packageName) || !HOME.contentEquals(className)) {
            return false;
        }
        // Ignore duplicate window announcements after issuing a redirect.
        if (lastRedirect >= 0 && elapsedTime - lastRedirect < 650) return false;
        lastRedirect = elapsedTime;
        return true;
    }
}
