package com.ksy.recordlib.service.util;

import java.net.URLEncoder;

/**
 * Created by hansentian on 9/18/15.
 */
public class URLConverter {

    public static String convertUrl(String url) {
        if (url == null)
            return url;
        String[] parts = url.split("&");
        StringBuilder builder = new StringBuilder();
        for (String part : parts
                ) {
            if (builder.length() != 0) {
                builder.append("&");
            }
            if (part.contains("=")) {
                String value = part.substring(part.indexOf("=") + 1);
                builder.append(part.replace(value, URLEncoder.encode(value)));
            } else {
                builder.append(part);
            }
        }
        return builder.toString();
    }
}
