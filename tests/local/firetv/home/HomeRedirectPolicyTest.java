package local.firetv.home;

public final class HomeRedirectPolicyTest {
    public static void main(String[] args) {
        HomeRedirectPolicy gate = new HomeRedirectPolicy();
        String amazon = HomeRedirectPolicy.AMAZON;
        String home = HomeRedirectPolicy.HOME;

        require(!gate.shouldRedirect(amazon, amazon + ".ui.MainSettingsActivity", 1_000));
        require(!gate.shouldRedirect(amazon, amazon + ".ui.SearchActivity", 1_001));
        require(!gate.shouldRedirect(HomeRedirectPolicy.APPSTORE, home, 1_002));
        require(!gate.shouldRedirect(null, null, 1_003));
        require(gate.shouldRedirect(amazon, home, 1_004));
        require(!gate.shouldRedirect(amazon, home, 1_005));
        require(gate.shouldRedirect(amazon, home, 2_004));

        require(HomeRedirectPolicy.startsAmazonSession(
                HomeRedirectPolicy.APPSTORE, HomeRedirectPolicy.APPSTORE_ENTRY));
        require(!HomeRedirectPolicy.startsAmazonSession(
                HomeRedirectPolicy.APPSTORE, HomeRedirectPolicy.APPSTORE + ".details.AppDetailsActivity"));
        require(!HomeRedirectPolicy.startsAmazonSession(amazon, HomeRedirectPolicy.APPSTORE_ENTRY));
        gate.enterSystemSession();
        require(!gate.shouldRedirect(amazon, home, 10_000));
        require(!gate.shouldRedirect(amazon, home, 1_000_000));
        gate.leaveSystemSession();
        require(gate.shouldRedirect(amazon, home, 1_000_001));

        System.out.println("Home redirect policy checks passed");
    }

    private static void require(boolean expected) {
        if (!expected) throw new AssertionError("Unexpected Home redirect decision");
    }
}
