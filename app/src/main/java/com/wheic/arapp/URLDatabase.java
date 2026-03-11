package com.wheic.arapp;

public class URLDatabase {
    private static final String BASE = "https://jstnagls.shop/volver/";

    // Existing endpoints
    public static String URL_CHECK_ACCOUNT        = BASE + "URL_CHECK_ACCOUNT.php";
    public static String URL_REGISTER             = BASE + "URL_REGISTER.php";
    public static String URL_LOGIN                = BASE + "URL_LOGIN.php";
    public static String URL_HOME                 = BASE + "URL_HOME.php";
    public static String URL_ACCOUNT_SETTING_UPDATE = BASE + "URL_ACCOUNT_SETTING_UPDATE.php";

    // Blockchain / mission-tracking endpoints (new PHP scripts in backend/ folder)
    public static String URL_COMPLETE_MISSION     = BASE + "complete_mission.php";
    public static String URL_GET_MISSIONS         = BASE + "get_missions.php";
    public static String URL_SAVE_WALLET          = BASE + "save_wallet.php";
    public static String URL_WHITELIST_WALLET     = BASE + "whitelist_wallet.php";
}
