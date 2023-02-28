/*
 * Certain versions of software and/or documents ("Material") accessible here may contain branding from
 * Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 * the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 * and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 * marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * (c) Copyright 2012-2023 Micro Focus or one of its affiliates.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ___________________________________________________________________
 */

package com.microfocus.application.automation.tools.results.parser.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimeUtil {
	public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
	public static final String DATE_FORMAT = "yyyy-MM-dd"; // "MM/dd/yyyy");
    public static final String TIME_FORMAT = "HH:mm:ss";	
	
    public static SimpleDateFormat getDateFormatter() {
        return new SimpleDateFormat(DATE_FORMAT); 
    }
    
    public static SimpleDateFormat getDateFormatter(String format) {
        return new SimpleDateFormat(format); 
    }
    
    public static String dateToString(java.util.Date date) {
        try {
            return ((date == null) ? "" : getDateFormatter().format(date));
        } catch (Exception e) {
        	return "";
        }
    }	
	
    public static java.util.Date stringToDate(String source) {
        TimeZone localTimeZone = getDateFormatter().getTimeZone();
        return stringToDate(source, localTimeZone);
    }
    
    public static DateFormat getFullTimestampFormatter(Locale locale) {
        DateFormat dateFormat =
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, locale);
        
        String localeString = locale.toString();
        if (localeString.equals("zh_SG")) {
            return new SimpleDateFormat("dd/MM/yyyy a hh:mm");
        } else if (localeString.equals("ko")) {
            return changeDateFormatPattern(dateFormat, "y+\\. *M+\\. *d+", "yyyy-MM-dd");
        }
        
        // change year format to long format (e.g. 2012)
        return changeDateFormatPattern(dateFormat, "y+", "yyyy");
    }
    
    private static DateFormat changeDateFormatPattern(
            final DateFormat dateFormat,
            String regex,
            String replacement) {
        if (dateFormat instanceof SimpleDateFormat) {
            SimpleDateFormat simpleDateFormat = (SimpleDateFormat) dateFormat;
            
            String pattern = simpleDateFormat.toPattern().replaceAll(regex, replacement);
            simpleDateFormat.applyPattern(pattern);
            
            return simpleDateFormat;
        }
        return dateFormat;
    }
    
    public static SimpleDateFormat getFullTimestampFormatter() {
        return new SimpleDateFormat(DATE_TIME_FORMAT); // m_fullTimestampFormatter;
    }
    
    
    public static SimpleDateFormat getTimeFormatter() {
        return new SimpleDateFormat(TIME_FORMAT); // m_timeFormatter;
    }
    
    public static java.util.Date stringToDate(String source, TimeZone timeZone) {
        // check input
        if (source == null) {
            throw new IllegalArgumentException("null argument not allowed");
        }
        
        java.util.Date result;
        try {
            boolean hasColon = (source.indexOf(':') != -1);
            boolean hasMinusSign = (source.indexOf('-') != -1);
            int length = source.length();
            
            // datetime format
            if ((length == DATE_TIME_FORMAT.length())
                || (hasColon && hasMinusSign)) {
                SimpleDateFormat fullTimestampFormatter =
                        getFullTimestampFormatter();
                fullTimestampFormatter.setTimeZone(timeZone);
                result = fullTimestampFormatter.parse(source);
                // date format
            } else if ((source.length() == DATE_FORMAT.length())
                       || (hasMinusSign)) {
                SimpleDateFormat dateFormatter = getDateFormatter();
                dateFormatter.setTimeZone(timeZone);
                result = dateFormatter.parse(source);
                // time format
            } else if ((source.length() == TIME_FORMAT.length()) || (hasColon)) {
                SimpleDateFormat timeFormatter = getTimeFormatter();
                timeFormatter.setTimeZone(timeZone);
                result = timeFormatter.parse(source);
            } else {
                throw new IllegalArgumentException("unsupported date format: \"" + source + "\"");
            }
        } catch (java.text.ParseException e) {
        	return new Date();
        }
        
        return result;
    }
    
    public static String timeToString(java.util.Date date) {
        try {
            return ((date == null) ? "" : getTimeFormatter().format(date));
        } catch (Exception e) {
        	return "";
        }
    }
}
