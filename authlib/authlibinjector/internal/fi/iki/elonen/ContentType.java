/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.internal.fi.iki.elonen;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ContentType {
    private static final String ASCII_ENCODING = "US-ASCII";
    private static final String MULTIPART_FORM_DATA_HEADER = "multipart/form-data";
    private static final String CONTENT_REGEX = "[ |\t]*([^/^ ^;^,]+/[^ ^;^,]+)";
    private static final Pattern MIME_PATTERN = Pattern.compile("[ |\t]*([^/^ ^;^,]+/[^ ^;^,]+)", 2);
    private static final String CHARSET_REGEX = "[ |\t]*(charset)[ |\t]*=[ |\t]*['|\"]?([^\"^'^;^,]*)['|\"]?";
    private static final Pattern CHARSET_PATTERN = Pattern.compile("[ |\t]*(charset)[ |\t]*=[ |\t]*['|\"]?([^\"^'^;^,]*)['|\"]?", 2);
    private static final String BOUNDARY_REGEX = "[ |\t]*(boundary)[ |\t]*=[ |\t]*['|\"]?([^\"^'^;^,]*)['|\"]?";
    private static final Pattern BOUNDARY_PATTERN = Pattern.compile("[ |\t]*(boundary)[ |\t]*=[ |\t]*['|\"]?([^\"^'^;^,]*)['|\"]?", 2);
    private final String contentTypeHeader;
    private final String contentType;
    private final String encoding;
    private final String boundary;

    public ContentType(String contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
        if (contentTypeHeader != null) {
            this.contentType = this.getDetailFromContentHeader(contentTypeHeader, MIME_PATTERN, "", 1);
            this.encoding = this.getDetailFromContentHeader(contentTypeHeader, CHARSET_PATTERN, null, 2);
        } else {
            this.contentType = "";
            this.encoding = "UTF-8";
        }
        this.boundary = MULTIPART_FORM_DATA_HEADER.equalsIgnoreCase(this.contentType) ? this.getDetailFromContentHeader(contentTypeHeader, BOUNDARY_PATTERN, null, 2) : null;
    }

    private String getDetailFromContentHeader(String contentTypeHeader, Pattern pattern, String defaultValue, int group) {
        Matcher matcher = pattern.matcher(contentTypeHeader);
        return matcher.find() ? matcher.group(group) : defaultValue;
    }

    public String getContentTypeHeader() {
        return this.contentTypeHeader;
    }

    public String getContentType() {
        return this.contentType;
    }

    public String getEncoding() {
        return this.encoding == null ? ASCII_ENCODING : this.encoding;
    }

    public String getBoundary() {
        return this.boundary;
    }

    public boolean isMultipart() {
        return MULTIPART_FORM_DATA_HEADER.equalsIgnoreCase(this.contentType);
    }

    public ContentType tryUTF8() {
        if (this.encoding == null) {
            return new ContentType(this.contentTypeHeader + "; charset=UTF-8");
        }
        return this;
    }
}

