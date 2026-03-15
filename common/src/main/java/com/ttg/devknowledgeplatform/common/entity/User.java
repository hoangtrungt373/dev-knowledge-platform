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
@Table(name = "USER", schema = "product")
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
    @Column(name = "EMAIL", length = 255)
    private String email;

    @NotNull
    @Size(max = 255)
    @Column(name = "USERNAME", length = 255)
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
}
