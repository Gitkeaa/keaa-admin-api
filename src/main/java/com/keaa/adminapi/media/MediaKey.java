package com.keaa.adminapi.media;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/**
 * THE R2 OBJECT KEY RULE. One definition, used everywhere.
 *
 * <p>This is the Java twin of the frontend's {@code src/data/mediaKey.js} in the website repo.
 * The two MUST agree character for character: if you change one, change the other and re-run
 * the migration manifest. Do not write a second slugifier anywhere in either codebase.
 * In particular, {@code ProductController.slugify} predates this class and is NOT the same
 * thing: it names a single Cloudinary asset, this names an R2 object.
 *
 * <p>Owner's rule (2026-09-23, amended 2026-09-24). Given a Cloudinary public ID and a file
 * extension, in this order:
 * <ol>
 *   <li>NFKD-normalise and strip combining marks, so accented letters become their plain
 *       ASCII base letter. "Böhler" becomes "bohler", NOT "b-hler".</li>
 *   <li>Lowercase.</li>
 *   <li>Replace spaces and every character outside {@code [a-z0-9/._-]} with a hyphen.</li>
 *   <li>Collapse runs of hyphens into one.</li>
 *   <li>Trim leading and trailing hyphens per path segment, then re-collapse.</li>
 *   <li>Append the extension, lowercased.</li>
 * </ol>
 *
 * <p>Worked example, the one the owner signed off:
 * <pre>
 *   MediaKey.of("1.Keaa Assets/Keaa products/Ringlock Tower 281-1", "jpg")
 *     == "1.keaa-assets/keaa-products/ringlock-tower-281-1.jpg"
 * </pre>
 *
 * <p>Why: R2 keys are case sensitive and a space has to be percent-encoded in every URL that
 * references the object, so mixed case and spaces cause bugs that only appear in the browser.
 * {@code '/'}, {@code '.'}, {@code '_'} and {@code '-'} are kept: the slash preserves the
 * folder shape and the rest already appear in Cloudinary's own generated IDs
 * (DJI_0082_p2qmld). Step 1 runs before step 3 on purpose, or the filter would turn every
 * accented letter into a hyphen.
 */
public final class MediaKey {

    private MediaKey() {
    }

    /**
     * The R2 object key for a Cloudinary public ID.
     *
     * @param publicId  Cloudinary public ID, folders and all
     * @param extension file extension, with or without the leading dot; null or blank for none
     * @return the object key
     */
    public static String of(String publicId, String extension) {
        String slug = deaccent(publicId)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9/._-]", "-")
                .replaceAll("-{2,}", "-");

        String[] segments = slug.split("/", -1);
        StringBuilder out = new StringBuilder(slug.length());
        for (int i = 0; i < segments.length; i++) {
            String trimmed = segments[i].replaceAll("^-+|-+$", "").replaceAll("-{2,}", "-");
            if (trimmed.isEmpty()) {
                trimmed = "x";
            }
            if (i > 0) {
                out.append('/');
            }
            out.append(trimmed);
        }
        String key = out.toString();

        if (extension == null || extension.isEmpty()) {
            return key;
        }
        String ext = deaccent(extension)
                .toLowerCase(Locale.ROOT)
                .replaceAll("^[.]+", "")
                .replaceAll("[^a-z0-9]", "");
        return ext.isEmpty() ? key : key + "." + ext;
    }

    /**
     * The 6 character collision suffix for a key that is already taken.
     *
     * <p>Owner's decision (2026-09-24): when the computed key already exists, append the last
     * 6 characters of the SHA-1 of the ORIGINAL public ID, not of the slug, before the
     * extension. Both the winner and the loser are logged in the migration report.
     */
    public static String collisionSuffix(String originalPublicId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(originalPublicId.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(digest);
            return hex.substring(hex.length() - 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by the platform", e);
        }
    }

    /** Insert a collision suffix into a key, before its extension if it has one. */
    public static String withSuffix(String key, String suffix) {
        int dot = key.lastIndexOf('.');
        int slash = key.lastIndexOf('/');
        if (dot > slash) {
            return key.substring(0, dot) + "-" + suffix + key.substring(dot);
        }
        return key + "-" + suffix;
    }

    /**
     * The file extension implied by a file's first bytes, or null if nothing matches.
     *
     * <p>Owner's decision (2026-09-24): an upload that arrives with no extension must have its
     * type sniffed from its content rather than guessed from its name, and EVERY such case must
     * be logged. Cloudinary stores "raw" assets with no format recorded, which is how
     * "1.Keaa Assets/Keaa Resumes/file_klapaj" turned out to be a PDF that nothing declared.
     *
     * <p>Never trust the browser-supplied filename or Content-Type here. Both are attacker
     * controlled on a public upload form, such as the job application form; the bytes are the
     * only honest evidence. This is the Java twin of {@code extensionFromMagicBytes} in
     * {@code src/data/mediaKey.js}.
     *
     * @param bytes at least the first 64 bytes of the file
     * @return a lowercase extension with no dot, or null if unrecognised
     */
    public static String extensionFromMagicBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (ascii(bytes, 0, "%PDF-")) return "pdf";
        if (at(bytes, 0) == 0xFF && at(bytes, 1) == 0xD8 && at(bytes, 2) == 0xFF) return "jpg";
        if (at(bytes, 0) == 0x89 && ascii(bytes, 1, "PNG") && at(bytes, 4) == 0x0D && at(bytes, 5) == 0x0A) return "png";
        if (ascii(bytes, 0, "GIF87a") || ascii(bytes, 0, "GIF89a")) return "gif";
        if (ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) return "webp";
        if (at(bytes, 0) == 0x1A && at(bytes, 1) == 0x45 && at(bytes, 2) == 0xDF && at(bytes, 3) == 0xA3) return "webm";
        if (ascii(bytes, 0, "BM")) return "bmp";
        if (ascii(bytes, 0, "II") && at(bytes, 2) == 0x2A && at(bytes, 3) == 0x00) return "tiff";
        if (ascii(bytes, 0, "MM") && at(bytes, 2) == 0x00 && at(bytes, 3) == 0x2A) return "tiff";
        // Old Office binary formats (.doc, .xls, .ppt) all share the OLE2 compound file header.
        if (at(bytes, 0) == 0xD0 && at(bytes, 1) == 0xCF && at(bytes, 2) == 0x11 && at(bytes, 3) == 0xE0) return "doc";

        // ISO base media: the brand at offset 8 says which flavour.
        if (ascii(bytes, 4, "ftyp")) {
            if (ascii(bytes, 8, "qt  ")) return "mov";
            if (ascii(bytes, 8, "avif") || ascii(bytes, 8, "avis")) return "avif";
            if (ascii(bytes, 8, "heic") || ascii(bytes, 8, "heix")
                    || ascii(bytes, 8, "hevc") || ascii(bytes, 8, "mif1")) return "heic";
            return "mp4";
        }

        // Zip container: the first entry's name says whether it is an Office document.
        if (ascii(bytes, 0, "PK") && at(bytes, 2) == 0x03 && at(bytes, 3) == 0x04) {
            if (ascii(bytes, 30, "word/")) return "docx";
            if (ascii(bytes, 30, "xl/")) return "xlsx";
            if (ascii(bytes, 30, "ppt/")) return "pptx";
            return "zip";
        }

        // SVG is text, so skip a byte order mark and any leading whitespace before looking.
        int i = (at(bytes, 0) == 0xEF && at(bytes, 1) == 0xBB && at(bytes, 2) == 0xBF) ? 3 : 0;
        while (i < bytes.length
                && (at(bytes, i) == 0x20 || at(bytes, i) == 0x09 || at(bytes, i) == 0x0A || at(bytes, i) == 0x0D)) {
            i++;
        }
        if (ascii(bytes, i, "<?xml") || ascii(bytes, i, "<svg")) return "svg";

        return null;
    }

    /** The unsigned value of one byte, or -1 past the end of the array. */
    private static int at(byte[] b, int i) {
        return (i >= 0 && i < b.length) ? (b[i] & 0xFF) : -1;
    }

    /** Whether the ASCII text sits at this offset. */
    private static boolean ascii(byte[] b, int offset, String text) {
        for (int i = 0; i < text.length(); i++) {
            if (at(b, offset + i) != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** Strip the diacritics off a string without touching the base letters. */
    private static String deaccent(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
    }
}
