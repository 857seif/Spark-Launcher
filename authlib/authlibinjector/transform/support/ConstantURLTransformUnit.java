/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.transform.support;

import java.util.Optional;
import moe.yushi.authlibinjector.httpd.URLProcessor;
import moe.yushi.authlibinjector.transform.LdcTransformUnit;

public class ConstantURLTransformUnit
extends LdcTransformUnit {
    private URLProcessor urlProcessor;

    public ConstantURLTransformUnit(URLProcessor urlProcessor) {
        this.urlProcessor = urlProcessor;
    }

    @Override
    protected Optional<String> transformLdc(String input) {
        return this.urlProcessor.transformURL(input);
    }

    public String toString() {
        return "Constant URL Transformer";
    }
}

