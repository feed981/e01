package com.feed01.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class DateTimeFormatterUtil {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public String yyyyMMddHHmmss(Object obj){

        if (obj instanceof Long) {
            long epochSecond = (Long) obj;
            LocalDateTime dateTime = LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC);
            return dateTime.format(formatter);

        } else if (obj instanceof String) {
            try {
                long epochSecond = Long.parseLong((String) obj);
                LocalDateTime dateTime = LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC);
                return dateTime.format(formatter);

            } catch (NumberFormatException e) {
                // Handle invalid string format
                throw new IllegalArgumentException("Invalid timestamp string: " + obj);
            }

        } else if (obj instanceof LocalDateTime) {
            // Directly format LocalDateTime
            return ((LocalDateTime) obj).format(formatter);

        } else {
            throw new IllegalArgumentException("Unsupported type: " + obj.getClass().getSimpleName());
        }
    }
}
