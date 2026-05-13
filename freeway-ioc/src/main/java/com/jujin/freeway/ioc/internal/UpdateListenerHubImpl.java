package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.UpdateListener;
import com.jujin.freeway.ioc.UpdateListenerHub;
import com.jujin.freeway.ioc.annotations.PreventServiceDecoration;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@PreventServiceDecoration
public class UpdateListenerHubImpl implements UpdateListenerHub {
    private final List<WeakReference<UpdateListener>> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void addUpdateListener(UpdateListener listener) {
        assert listener != null;
        listeners.add(new WeakReference<UpdateListener>(listener));
    }

    /**
     * Notifies all {@link UpdateListener}s.
     */
    @Override
    public void fireCheckForUpdates() {
        List<WeakReference<UpdateListener>> deadReferences = new ArrayList<>();

        Iterator<WeakReference<UpdateListener>> i = listeners.iterator();

        while (i.hasNext()) {
            WeakReference<UpdateListener> reference = i.next();

            UpdateListener listener = reference.get();

            if (listener == null)
                deadReferences.add(reference);
            else
                listener.checkForUpdates();
        }

        if (!deadReferences.isEmpty())
            listeners.removeAll(deadReferences);
    }
}
