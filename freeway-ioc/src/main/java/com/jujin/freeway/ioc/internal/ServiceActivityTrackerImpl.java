package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceDefinition;
import com.jujin.freeway.ioc.advisor.ServiceActivity;
import com.jujin.freeway.ioc.advisor.ServiceActivityScoreboard;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import com.jujin.freeway.ioc.threading.PerThreadValue;
import com.jujin.freeway.ioc.threading.PerthreadManager;

import java.util.*;

public class ServiceActivityTrackerImpl
    implements ServiceActivityScoreboard, ServiceActivityTracker {

    public static class MutableServiceActivity implements ServiceActivity {

        private final ServiceDefinition serviceDef;

        private ServiceStatus status;

        private final PerThreadValue<ServiceStatus> perThreadStatus;

        public MutableServiceActivity(
            ServiceDefinition serviceDef,
            PerthreadManager perthreadManager,
            ServiceStatus status) {
            this.serviceDef = serviceDef;
            if (serviceDef.getServiceScope().equals(InternalUtils.PERTHREAD)) {
                perThreadStatus = perthreadManager.createValue();
                perThreadStatus.set(status);
                this.status = status; // this is now the default status
            } else {
                perThreadStatus = null;
                this.status = status;
            }
        }

        @Override
        public String getServiceId() {
            return serviceDef.getServiceId();
        }

        @Override
        public Class<?> getServiceInterface() {
            return serviceDef.getServiceInterface();
        }

        @Override
        public String getScope() {
            return serviceDef.getServiceScope();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<Class<?>> getMarkers() {
            return (Set<Class<?>>) (Set<?>) serviceDef.getMarkers();
        }

        // Mutable properties must be synchronized

        @Override
        public synchronized ServiceStatus getStatus() {
            if (perThreadStatus != null) {
                if (!perThreadStatus.exists())
                    perThreadStatus.set(status);
                return perThreadStatus.get();
            } else
                return status;
        }

        synchronized void setStatus(ServiceStatus status) {
            if (perThreadStatus != null)
                perThreadStatus.set(status);
            else
                this.status = status;
        }
    }

    private final PerthreadManager perthreadManager;

    public ServiceActivityTrackerImpl(PerthreadManager perthreadManager) {
        this.perthreadManager = perthreadManager;
    }

    /**
     * Tree map keeps everything in order by key (serviceId).
     */
    private final Map<String, MutableServiceActivity> serviceIdToServiceStatus = new TreeMap<String, MutableServiceActivity>();

    @Override
    public synchronized List<ServiceActivity> getServiceActivity() {
        // Need to wrap the values in a new list because
        // a) we don't want people arbitrarily changing the internal state of
        // _serviceIdtoServiceStatus
        // b) values() is Collection and we want to return List

        // Note: ugly code here to keep Sun compiler happy.

        List<ServiceActivity> result = new ArrayList<>();

        result.addAll(serviceIdToServiceStatus.values());

        return result;
    }

    void startup() {
        // Does nothing, first pass does not use a worker thread
    }

    void shutdown() {
        // Does nothing, first pass does not use a worker thread
    }

    @Override
    public synchronized void define(
        ServiceDefinition serviceDef,
        ServiceStatus status) {
        serviceIdToServiceStatus.put(
            serviceDef.getServiceId(),
            new MutableServiceActivity(
                serviceDef,
                perthreadManager,
                    status));
    }

    @Override
    public synchronized void setStatus(String serviceId, ServiceStatus status) {
        serviceIdToServiceStatus.get(serviceId).setStatus(status);
    }
}
