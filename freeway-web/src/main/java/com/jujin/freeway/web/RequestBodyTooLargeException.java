package com.jujin.freeway.web;

import java.io.IOException;

/**
 * Exception thrown when request body exceeds the configured maximum size.
 * 
 * <p>This exception is automatically mapped to HTTP 413 (Payload Too Large) response
 * by the default exception mapper in {@link WebModule}.</p>
 */
public class RequestBodyTooLargeException extends IOException {
    
    private final long maxSize;
    
    /**
     * Creates a new exception with the configured maximum size.
     * 
     * @param maxSize the maximum allowed body size in bytes
     */
    public RequestBodyTooLargeException(long maxSize) {
        super("Request body exceeds maximum size of " + maxSize + " bytes");
        this.maxSize = maxSize;
    }
    
    /**
     * Returns the maximum allowed body size.
     * 
     * @return max size in bytes
     */
    public long getMaxSize() {
        return maxSize;
    }
}
