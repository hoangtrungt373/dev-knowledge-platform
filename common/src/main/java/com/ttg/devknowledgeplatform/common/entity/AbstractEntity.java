package com.ttg.devknowledgeplatform.common.entity;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;

import com.ttg.devknowledgeplatform.common.util.DateUtils;
import com.ttg.devknowledgeplatform.common.util.UserUtils;

/**
 * Abstract base entity with automatic sequence generation based on table name.
 *
 * Child entities automatically use sequence: TABLE_NAME_SEQ
 * based on their @Table annotation. No need to declare @GenericGenerator.
 *
 * @author ttg
 */
@MappedSuperclass
@Data
public abstract class AbstractEntity {

    @Id
    @GeneratedValue(generator = "sequence_generator")
    @GenericGenerator(
            name = "sequence_generator",
            type = TableNameSequenceGenerator.class
    )
    @Column(updatable = false, nullable = false)
    @Access(AccessType.PROPERTY)
    protected Integer id;

    @NotNull
    @Size(max = 128)
    @Column(name = "USR_CREATION", length = 128, nullable = false, updatable = false)
    private String usrCreation;

    @NotNull
    @Column(name = "DTE_CREATION", nullable = false, updatable = false)
    private Instant dteCreation;

    @NotNull
    @Size(max = 128)
    @Column(name = "USR_LAST_MODIFICATION", length = 128, nullable = false)
    private String usrLastModification;

    @NotNull
    @Column(name = "DTE_LAST_MODIFICATION", nullable = false)
    private Instant dteLastModification;

    @Version
    @Column(name = "VERSION")
    private Integer version;

    @PrePersist
    protected void beforeSave() {
        String user = UserUtils.getUserName();
        Instant now = DateUtils.getCurrentDateTime();
        setUsrCreation(user);
        setDteCreation(now);
        setUsrLastModification(user);
        setDteLastModification(now);
    }

    @PreUpdate
    protected void beforeUpdate() {
        setUsrLastModification(UserUtils.getUserName());
        setDteLastModification(DateUtils.getCurrentDateTime());
    }
}
