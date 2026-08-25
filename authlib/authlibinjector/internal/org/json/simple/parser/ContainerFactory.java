/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.internal.org.json.simple.parser;

import java.util.List;
import java.util.Map;

public interface ContainerFactory {
    public Map<String, Object> createObjectContainer();

    public List<Object> creatArrayContainer();
}

