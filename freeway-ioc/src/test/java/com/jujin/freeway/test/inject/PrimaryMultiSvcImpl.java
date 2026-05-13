package com.jujin.freeway.test.inject;

import com.jujin.freeway.ioc.annotations.Primary;

/**
 * Primary implementation of MultiSvc — marked with @Primary.
 */
@Primary
public class PrimaryMultiSvcImpl implements MultiSvc {
    @Override
    public String name() {
        return "primary";
    }
}
