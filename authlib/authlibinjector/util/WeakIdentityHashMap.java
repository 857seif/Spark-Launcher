/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.util;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class WeakIdentityHashMap<K, V>
implements Map<K, V> {
    private final ReferenceQueue<K> queue = new ReferenceQueue();
    private Map<IdentityWeakReference, V> backingStore = new HashMap<IdentityWeakReference, V>();

    @Override
    public void clear() {
        this.backingStore.clear();
        this.reap();
    }

    @Override
    public boolean containsKey(Object key) {
        this.reap();
        return this.backingStore.containsKey(new IdentityWeakReference(key));
    }

    @Override
    public boolean containsValue(Object value) {
        this.reap();
        return this.backingStore.containsValue(value);
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        this.reap();
        HashSet<1> ret = new HashSet<1>();
        for (Map.Entry<IdentityWeakReference, V> ref : this.backingStore.entrySet()) {
            final Object key = ref.getKey().get();
            final V value = ref.getValue();
            Map.Entry entry = new Map.Entry<K, V>(){

                @Override
                public K getKey() {
                    return key;
                }

                @Override
                public V getValue() {
                    return value;
                }

                @Override
                public V setValue(V value2) {
                    throw new UnsupportedOperationException();
                }
            };
            ret.add(entry);
        }
        return Collections.unmodifiableSet(ret);
    }

    @Override
    public Set<K> keySet() {
        this.reap();
        HashSet ret = new HashSet();
        for (IdentityWeakReference ref : this.backingStore.keySet()) {
            ret.add(ref.get());
        }
        return Collections.unmodifiableSet(ret);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WeakIdentityHashMap)) {
            return false;
        }
        return this.backingStore.equals(((WeakIdentityHashMap)o).backingStore);
    }

    @Override
    public V get(Object key) {
        this.reap();
        return this.backingStore.get(new IdentityWeakReference(key));
    }

    @Override
    public V put(K key, V value) {
        this.reap();
        return this.backingStore.put(new IdentityWeakReference(key), value);
    }

    @Override
    public int hashCode() {
        this.reap();
        return this.backingStore.hashCode();
    }

    @Override
    public boolean isEmpty() {
        this.reap();
        return this.backingStore.isEmpty();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
        this.reap();
        return this.backingStore.remove(new IdentityWeakReference(key));
    }

    @Override
    public int size() {
        this.reap();
        return this.backingStore.size();
    }

    @Override
    public Collection<V> values() {
        this.reap();
        return this.backingStore.values();
    }

    private synchronized void reap() {
        Reference<K> zombie = this.queue.poll();
        while (zombie != null) {
            IdentityWeakReference victim = (IdentityWeakReference)zombie;
            this.backingStore.remove(victim);
            zombie = this.queue.poll();
        }
    }

    class IdentityWeakReference
    extends WeakReference<K> {
        int hash;

        IdentityWeakReference(Object obj) {
            super(obj, WeakIdentityHashMap.this.queue);
            this.hash = System.identityHashCode(obj);
        }

        public int hashCode() {
            return this.hash;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IdentityWeakReference)) {
                return false;
            }
            IdentityWeakReference ref = (IdentityWeakReference)o;
            return this.get() == ref.get();
        }
    }
}

