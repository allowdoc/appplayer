package com.google.gson.reflect;

import com.google.gson.internal.$Gson$Types;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Objects;

/**
 * Universal Resilient TypeToken for Obfuscated Android Bytecode.
 * Gracefully handles ProGuard/R8 builds where generic Signature attributes were stripped,
 * preventing fatal IllegalStateException while maintaining full compatibility with Gson.
 */
public class TypeToken<T> {
    private final Class<? super T> rawType;
    private final Type type;
    private final int hashCode;

    @SuppressWarnings("unchecked")
    protected TypeToken() {
        this.type = getTypeTokenTypeArgument();
        this.rawType = (Class<? super T>) $Gson$Types.getRawType(this.type);
        this.hashCode = this.type.hashCode();
    }

    @SuppressWarnings("unchecked")
    private TypeToken(Type type) {
        this.type = $Gson$Types.canonicalize(Objects.requireNonNull(type));
        this.rawType = (Class<? super T>) $Gson$Types.getRawType(this.type);
        this.hashCode = this.type.hashCode();
    }

    private Type getTypeTokenTypeArgument() {
        Type superclass = getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) superclass;
            if (parameterized.getRawType() == TypeToken.class) {
                return $Gson$Types.canonicalize(parameterized.getActualTypeArguments()[0]);
            }
        } else if (superclass == TypeToken.class) {
            String name = getClass().getName();
            if (name.contains("list") || name.contains("List") || name.contains("ArrayList")) {
                return $Gson$Types.newParameterizedTypeWithOwner(null, java.util.List.class, Object.class);
            }
            if (name.contains("map") || name.contains("Map") || name.contains("HashMap")) {
                return $Gson$Types.newParameterizedTypeWithOwner(null, java.util.Map.class, String.class, Object.class);
            }
            return Object.class;
        }
        return Object.class;
    }

    public final Class<? super T> getRawType() {
        return rawType;
    }

    public final Type getType() {
        return type;
    }

    @Deprecated
    public boolean isAssignableFrom(Class<?> cls) {
        return isAssignableFrom((Type) cls);
    }

    @Deprecated
    public boolean isAssignableFrom(Type from) {
        if (from == null) return false;
        if (type.equals(from)) return true;
        if (type instanceof Class<?>) {
            return rawType.isAssignableFrom($Gson$Types.getRawType(from));
        }
        return false;
    }

    @Deprecated
    public boolean isAssignableFrom(TypeToken<?> token) {
        return isAssignableFrom(token.getType());
    }

    @Override
    public final int hashCode() {
        return this.hashCode;
    }

    @Override
    public final boolean equals(Object o) {
        return o instanceof TypeToken<?> && $Gson$Types.equals(type, ((TypeToken<?>) o).type);
    }

    @Override
    public final String toString() {
        return $Gson$Types.typeToString(type);
    }

    public static TypeToken<?> get(Type type) {
        return new TypeToken<Object>(type);
    }

    public static <T> TypeToken<T> get(Class<T> type) {
        return new TypeToken<T>(type);
    }

    public static TypeToken<?> getParameterized(Type rawType, Type... typeArguments) {
        return new TypeToken<Object>($Gson$Types.newParameterizedTypeWithOwner(null, rawType, typeArguments));
    }

    public static TypeToken<?> getArray(Type componentType) {
        return new TypeToken<Object>($Gson$Types.arrayOf(componentType));
    }
}
