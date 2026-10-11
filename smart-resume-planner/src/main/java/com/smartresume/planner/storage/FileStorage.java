package com.smartresume.planner.storage;

import java.io.IOException;
import java.io.InputStream;

/** Abstraction over where uploaded and generated files live (local disk now, S3 later). */
public interface FileStorage {

    /** Stores the bytes and returns an opaque key. Keys are generated here, never taken from users. */
    String store(byte[] content, String extension) throws IOException;

    InputStream open(String key) throws IOException;

    void delete(String key);
}
