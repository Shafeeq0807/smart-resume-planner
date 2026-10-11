package com.smartresume.planner.export.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** HTML (XHTML) to PDF with the built-in PDF fonts, plus helpers to read the result back for the self-check. */
@Component
public class PdfRenderer {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#.@/-]{2,}");

    public record Rendered(byte[] bytes, int pageCount, String extractedText) { }

    public Rendered render(String xhtml) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(toLatin(xhtml), null);
        builder.toStream(out);
        try {
            builder.run();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("PDF rendering failed: " + e.getMessage(), e);
        }
        byte[] bytes = out.toByteArray();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(doc);
            return new Rendered(bytes, doc.getNumberOfPages(), text);
        }
    }

    /** Share (0..100) of the resume's distinctive words that a text extractor can read back out of the PDF. */
    public static int selfCheckScore(String expectedText, String extractedPdfText) {
        Set<String> expected = words(expectedText);
        if (expected.isEmpty()) {
            return 0;
        }
        Set<String> actual = words(extractedPdfText);
        long found = expected.stream().filter(actual::contains).count();
        return (int) Math.round(100.0 * found / expected.size());
    }

    private static Set<String> words(String text) {
        Set<String> out = new HashSet<>();
        var m = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /**
     * The built-in PDF fonts only cover Windows-1252. Typographic characters are mapped to ASCII equivalents
     * and anything else outside Latin-1 becomes '?', so rendering never fails on an unsupported glyph.
     */
    static String toLatin(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '‘', '’', '‚', '′' -> sb.append('\'');
                case '“', '”', '„', '″' -> sb.append('"');
                case '–', '—', '−', '‐', '‑' -> sb.append('-');
                case '•', '●', '▪', '■', '◦', '○', '·', '‣' -> sb.append('-');
                case '…' -> sb.append("...");
                case '→', '➜' -> sb.append("->");
                case ' ', ' ', ' ', ' ' -> sb.append(' ');
                default -> {
                    if (c <= 0xFF) {
                        sb.append(c);
                    } else if (Character.isLowSurrogate(c)) {
                        // second half of a pair already replaced
                    } else if (Character.isHighSurrogate(c)) {
                        sb.append('?');
                    } else {
                        sb.append(Character.isWhitespace(c) ? ' ' : '?');
                    }
                }
            }
        }
        return sb.toString();
    }
}
