package com.ttg.devknowledgeplatform.ai.converter;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Custom Hibernate {@link JdbcType} for pgvector's {@code vector} column type.
 *
 * <p>{@code @JdbcTypeCode(SqlTypes.OTHER)} (code 1111) was tried first and rejected: for this
 * project's Hibernate 6 + PostgreSQL combination, code 1111 resolves to Hibernate's built-in
 * {@code VarbinaryJdbcType} (binary/{@code byte[]} handling), which fails trying to unwrap the
 * converted {@code String} into a {@code byte[]} — "Could not convert 'java.lang.String' to
 * '[B'". Referencing {@code java.sql.Types.OTHER} directly wouldn't have helped either; it's
 * numerically identical to {@code SqlTypes.OTHER} (both 1111), so it hits the same resolution.
 *
 * <p>This class bypasses that resolution entirely by implementing the binder directly: it sends
 * the already-converted pgvector text ({@code [x,y,z,...]}, produced by
 * {@link FloatArrayToVectorConverter}) via {@code PreparedStatement.setObject(index, value,
 * Types.OTHER)}, which lets PostgreSQL resolve the value's type from the target column (declared
 * {@code vector(1536)}) instead of going through Hibernate's registry for code 1111 at all.
 * Reads go through pgvector's normal text output, unaffected.
 */
public class PgVectorJdbcType implements JdbcType {

    @Override
    public int getJdbcTypeCode() {
        return Types.OTHER;
    }

    @Override
    public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
        return new BasicBinder<>(javaType, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                st.setObject(index, javaType.unwrap(value, String.class, options), Types.OTHER);
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options) throws SQLException {
                st.setObject(name, javaType.unwrap(value, String.class, options), Types.OTHER);
            }
        };
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
        return new BasicExtractor<>(javaType, this) {
            @Override
            protected X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
                return javaType.wrap(rs.getString(paramIndex), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
                return javaType.wrap(statement.getString(index), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
                return javaType.wrap(statement.getString(name), options);
            }
        };
    }
}
