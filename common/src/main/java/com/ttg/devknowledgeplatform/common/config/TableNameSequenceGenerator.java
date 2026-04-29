package com.ttg.devknowledgeplatform.common.config;

import java.util.Properties;

import org.hibernate.MappingException;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.relational.QualifiedName;
import org.hibernate.boot.model.relational.QualifiedNameParser;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.PersistentIdentifierGenerator;
import org.hibernate.id.enhanced.OptimizerDescriptor;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.id.enhanced.StandardOptimizerDescriptor;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

import jakarta.persistence.Table;

/**
 * Custom Hibernate sequence generator that generates sequence names based on table name.
 * 
 * <p>This generator automatically creates sequence names in the format: SCHEMA.TABLE_NAME_SEQ</p>
 * 
 * <h3>Features:</h3>
 * <ul>
 *   <li>Automatically derives sequence name from entity's @Table annotation</li>
 *   <li>Supports schema qualification (e.g., product.USER_SEQ)</li>
 *   <li>Uses pooled-lo optimizer for efficient ID allocation</li>
 *   <li>Allows override via @GenericGenerator parameters</li>
 * </ul>
 * 
 * <h3>Usage:</h3>
 * <pre>
 * &#64;Entity
 * &#64;Table(name = "USER", schema = "product")
 * public class User extends AbstractEntity {
 *     // Automatically uses: product.USER_SEQ
 * }
 * </pre>
 * 
 * @author ttg
 */
public class TableNameSequenceGenerator extends SequenceStyleGenerator {

    private static final String ENTITY_TYPE_CLASS = "entity-type-class";

    @Override
    public void configure(Type type, Properties params, ServiceRegistry serviceRegistry) throws MappingException {
        try {
            params.put(ENTITY_TYPE_CLASS, Class.forName(ConfigurationHelper.getString(IdentifierGenerator.ENTITY_NAME, params)));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        super.configure(type, params, serviceRegistry);
    }

    @Override
    protected QualifiedName determineSequenceName(Properties params, Dialect dialect, JdbcEnvironment jdbcEnv, ServiceRegistry serviceRegistry) {
        String tableName = ConfigurationHelper.getString(PersistentIdentifierGenerator.TABLE, params);

        // Default suffix: "_SEQ" (safe even if Hibernate defaults change)
        String suffix = ConfigurationHelper.getString(
                SequenceStyleGenerator.CONFIG_SEQUENCE_PER_ENTITY_SUFFIX,
                params,
                "_SEQ"
        );

        String defaultSequenceName = tableName + suffix;

        // Allow override via @GenericGenerator(parameters = @Parameter(name="sequence_name", ...))
        String sequenceName = ConfigurationHelper.getString(SEQUENCE_PARAM, params, defaultSequenceName);

        if (sequenceName.contains(".")) {
            return QualifiedNameParser.INSTANCE.parse(sequenceName);
        }

        Identifier catalog = jdbcEnv.getIdentifierHelper().toIdentifier(
                ConfigurationHelper.getString(PersistentIdentifierGenerator.CATALOG, params)
        );

        Identifier schema = jdbcEnv.getIdentifierHelper().toIdentifier(
                ConfigurationHelper.getString(PersistentIdentifierGenerator.SCHEMA, params)
        );

        if (schema == null) {
            Table tableAnnotation = ((Class<?>) params.get(ENTITY_TYPE_CLASS)).getAnnotation(Table.class);
            if (tableAnnotation != null && tableAnnotation.schema() != null && !tableAnnotation.schema().trim().isEmpty()) {
                schema = jdbcEnv.getIdentifierHelper().toIdentifier(tableAnnotation.schema().trim());
            }
        }

        return new QualifiedNameParser.NameParts(
                catalog,
                schema,
                jdbcEnv.getIdentifierHelper().toIdentifier(sequenceName)
        );
    }

    @Override
    protected OptimizerDescriptor determineOptimizationStrategy(Properties params, int incrementSize) {
        // Default to pooled-lo. If OPT_PARAM is set to a known optimizer, honor it.
        String optimizerName = ConfigurationHelper.getString(
                OPT_PARAM,
                params,
                StandardOptimizerDescriptor.POOLED_LO.getExternalName()
        );

        for (StandardOptimizerDescriptor d : StandardOptimizerDescriptor.values()) {
            if (d.getExternalName().equalsIgnoreCase(optimizerName)) {
                return d;
            }
        }

        // Unknown/custom optimizer string: just fall back safely
        return StandardOptimizerDescriptor.POOLED_LO;
    }
}
