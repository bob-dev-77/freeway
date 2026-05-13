package com.jujin.freeway.test.inject;

/**
 * Secondary implementation of MultiSvc — not @Primary.
 */
public class SecondaryMultiSvcImpl implements MultiSvc {
    @Override
    public String name() {
        return "secondary";
    }
}
