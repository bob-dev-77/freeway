package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.annotations.Scope;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import com.jujin.freeway.ioc.threading.ThreadLocale;

import java.util.Locale;

@Scope(InternalUtils.PERTHREAD)
public class ThreadLocaleImpl implements ThreadLocale {
    private Locale locale = Locale.getDefault();

    @Override
    public Locale getLocale() {
        return locale;
    }

    @Override
    public void setLocale(Locale locale) {
        assert locale != null;

        this.locale = locale;
    }
}
