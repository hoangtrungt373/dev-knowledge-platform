package com.ttg.devknowledgeplatform.common.entity;

import com.ttg.devknowledgeplatform.common.enums.UserProvider;
import com.ttg.devknowledgeplatform.common.enums.UserRole;
import com.ttg.devknowledgeplatform.common.enums.UserStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @author ttg
 */
@Entity
@Table(
        name = "USER",
        // No hardcoded schema here (unlike most entities in this reactor) — deliberately, since
        // this class is shared as-is across every standalone deployable (gateway's monolith,
        // ecommerce-service, identity-service), each with its own USER table in its own schema
        // (product/ecommerce/identity respectively). Each app's own `hibernate.default_schema`
        // property resolves the actual schema at runtime; a hardcoded schema here would silently
        // force every deployable's User rows into the same physical table regardless of that
        // per-app setting, defeating per-service-per-schema for the one entity every service needs.
        // PROVIDER_ID is nullable (LOCAL accounts have none); a unique constraint still works
        // here since Postgres treats every NULL as distinct from every other NULL.
        uniqueConstraints = @UniqueConstraint(name = "UK_USER_PROVIDER_PROVIDER_ID", columnNames = {"PROVIDER", "PROVIDER_ID"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@AttributeOverride(name = "id", column = @Column(name = "USER_ID"))
@EqualsAndHashCode(callSuper = true, exclude = {"password"})
@ToString(exclude = {"password"})
public class User extends AbstractEntity {

    @NotNull
    @Size(max = 36)
    @Column(name = "USER_UUID", length = 36)
    private String userUuid;

    @NotNull
    @Size(max = 255)
    @Column(name = "EMAIL", length = 255, unique = true)
    private String email;

    @NotNull
    @Size(max = 255)
    @Column(name = "USERNAME", length = 255, unique = true)
    private String username;

    @NotNull
    @Size(max = 255)
    @Column(name = "PASSWORD", length = 255)
    private String password;

    @Size(max = 255)
    @Column(name = "FIRST_NAME", length = 255)
    private String firstName;

    @Size(max = 255)
    @Column(name = "LAST_NAME", length = 255)
    private String lastName;

    @Column(name = "PROFILE_PICTURE", length = 500)
    private String profilePicture;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "PROVIDER", length = 50)
    @Builder.Default
    private UserProvider provider = UserProvider.LOCAL;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", length = 50)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Size(max = 255)
    @Column(name = "PROVIDER_ID", length = 255)
    private String providerId;

    @NotNull
    @Column(name = "EMAIL_VERIFIED")
    @Builder.Default
    private Boolean emailVerified = false;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 50)
    @Builder.Default
    private UserStatus status = UserStatus.OFFLINE;

    @NotNull
    @Column(name = "ENABLED")
    @Builder.Default
    private Boolean enabled = true;

    // Null for every real/admin-created row; set only by UserSeeder, purely to detect
    // "already seeded" across re-runs without depending on EMAIL/USERNAME staying unchanged.
    @Column(name = "SEED_ID", length = 100)
    private String seedId;

    // The Keycloak realm's subject (`sub` claim) this row is JIT-provisioned from/linked to.
    // Null until the owner's first Keycloak-authenticated request (or a migration backfill).
    // PROVIDER/PROVIDER_ID stay as inert historical columns rather than being reused for this —
    // see Liquibase DKP-0025's comment for why.
    @Size(max = 255)
    @Column(name = "KEYCLOAK_SUBJECT_ID", length = 255, unique = true)
    private String keycloakSubjectId;
}
