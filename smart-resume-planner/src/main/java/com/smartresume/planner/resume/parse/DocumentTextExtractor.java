package com.smartresume.planner.resume.parse;

import com.smartresume.planner.common.config.AppProperties;
import com.smartresume.planner.common.exception.ApiException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Extracts plain text from PDF/DOCX with Apache Tika. The file type is decided from the bytes
 * (magic numbers), never from the client-supplied Content-Type or filename.
 */
@Slf4j
@Component
public class DocumentTextExtractor {

    public static final String PDF = "application/pdf";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final Tika detector = new Tika();
    private final AppProperties.Upload upload;

    public DocumentTextExtractor(AppProperties props) {
        this.upload = props.upload();
    }

    public record Extracted(String text, String mimeType, Integer pageCount) { }

    public Extracted extract(byte[] content) {
        String mime = detector.detect(content);
        if (!Set.copyOf(upload.allowedTypes()).contains(mime)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                    "Only PDF and DOCX resumes are supported (detected: " + mime + ")");
        }
        CompletableFuture<Extracted> future = CompletableFuture.supplyAsync(() -> parse(content, mime));
        try {
            Extracted result = future.get(upload.parseTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (result.text().length() < 50) {
                throw ApiException.badRequest("NO_TEXT_FOUND", "We could not read any text from this file. "
                        + "It may be a scanned image or password-protected. Please upload a text-based PDF or DOCX.");
            }
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PARSE_TIMEOUT",
                    "The file took too long to read. Try re-exporting it from your editor.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PARSE_INTERRUPTED", "Parsing was interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ApiException api) {
                throw api;
            }
            log.warn("Tika failed to parse upload: {}", cause.toString());
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNREADABLE_FILE",
                    "The file looks corrupted or password-protected and could not be read");
        }
    }

    private Extracted parse(byte[] content, String mime) {
        BodyContentHandler handler = new BodyContentHandler(upload.maxExtractedChars());
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
        pdfConfig.setExtractInlineImages(false);
        context.set(PDFParserConfig.class, pdfConfig);
        TesseractOCRConfig ocr = new TesseractOCRConfig();
        ocr.setSkipOcr(true);
        context.set(TesseractOCRConfig.class, ocr);
        try (InputStream in = new ByteArrayInputStream(content)) {
            new AutoDetectParser().parse(in, handler, metadata, context);
        } catch (WriteLimitReachedException limit) {
            log.info("Extracted text truncated at {} chars", upload.maxExtractedChars());
        } catch (Exception e) {
            if (e.getCause() instanceof WriteLimitReachedException) {
                log.info("Extracted text truncated at {} chars", upload.maxExtractedChars());
            } else {
                throw new IllegalStateException(e);
            }
        }
        return new Extracted(normalize(handler.toString()), mime, pageCount(metadata));
    }

    private static Integer pageCount(Metadata metadata) {
        for (String key : new String[] {"xmpTPg:NPages", "meta:page-count", "Page-Count", "nbPages"}) {
            String v = metadata.get(key);
            if (v != null) {
                try {
                    return Integer.parseInt(v.trim());
                } catch (NumberFormatException ignored) {
                    // try the next key
                }
            }
        }
        return null;
    }

    /** Normalises whitespace and glyphs so downstream rules see consistent text. */
    static String normalize(String raw) {
        String s = raw.replace("\r\n", "\n").replace('\r', '\n')
                .replace(' ', ' ').replace('\t', ' ')
                .replace("​", "").replace("﻿", "")
                .replaceAll("[\\p{Cc}&&[^\n]]", "")
                .replaceAll("[ ]{2,}", " ")
                .replaceAll(" +\n", "\n")
                .replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }
}
