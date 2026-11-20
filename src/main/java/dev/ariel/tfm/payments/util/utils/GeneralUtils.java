package dev.ariel.tfm.payments.util.utils;

public class GeneralUtils {

    private GeneralUtils() {}

    public static String coalesce(String... values) {
        for (String v : values)
            if (v != null) return v;
        return "";
    }

}
