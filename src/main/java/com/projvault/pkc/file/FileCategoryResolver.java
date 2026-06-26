package com.projvault.pkc.file;

import java.util.Set;

/**
 * Resolves a file extension into the coarse PKC file category.
 */
public final class FileCategoryResolver {

    public static final String DOC = "doc";
    public static final String CONFIG = "config";
    public static final String ARCHIVE = "archive";
    public static final String IMAGE = "image";
    public static final String OTHER = "other";

    private static final Set<String> DOC_EXT = Set.of(
            "doc", "docx", "wps", "xls", "xlsx", "ppt", "pptx", "pdf", "txt", "md", "rtf", "csv",
            "html", "htm", "xhtml");
    private static final Set<String> CONFIG_EXT = Set.of(
            "json", "yml", "yaml", "xml", "properties", "ini", "conf", "cfg", "toml", "env");
    private static final Set<String> ARCHIVE_EXT = Set.of(
            "zip", "rar", "7z", "tar", "gz", "jar", "war");
    private static final Set<String> IMAGE_EXT = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "svg", "tif", "tiff", "webp");

    private FileCategoryResolver() {
    }

    public static String resolve(String ext) {
        if (ext == null || ext.isEmpty()) {
            return OTHER;
        }
        String e = ext.toLowerCase();
        if (DOC_EXT.contains(e)) {
            return DOC;
        }
        if (CONFIG_EXT.contains(e)) {
            return CONFIG;
        }
        if (ARCHIVE_EXT.contains(e)) {
            return ARCHIVE;
        }
        if (IMAGE_EXT.contains(e)) {
            return IMAGE;
        }
        return OTHER;
    }
}
