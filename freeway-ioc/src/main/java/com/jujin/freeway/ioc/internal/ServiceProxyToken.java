package com.jujin.freeway.ioc.internal;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * Token that replaces a service proxy when the proxy is serialized.
 */
public class ServiceProxyToken implements Serializable {
    private static final long serialVersionUID = 4119675138731356650L;

    private final String serviceId;

    public ServiceProxyToken(String serviceId) {
        this.serviceId = serviceId;
    }

    Object readResolve() throws ObjectStreamException {
        try {
            return SerializationSupport.readResolve(serviceId);
        } catch (Exception ex) {
            ObjectStreamException ose = new InvalidObjectException(ex.getMessage());
            ose.initCause(ex);

            throw ose;
        }
    }

}
