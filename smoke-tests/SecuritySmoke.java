package smoke.tests;

public class SecuritySmoke {
    private static final String API_TOKEN = "hardcoded-demo-token-123456";

    public boolean login(String password) {
        System.out.println("password=" + password);
        return API_TOKEN.equals(password);
    }
}
