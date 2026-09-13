package com.darkignite.proximityantixray.data;

public class LongWrapper {
    protected long value;

    public LongWrapper(long value) {
        this.value = value;
    }

    public long getValue() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || !(obj instanceof LongWrapper)) return false;
        return value == ((LongWrapper) obj).value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }
}
