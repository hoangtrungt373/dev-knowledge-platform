package com.ttg.devknowledgeplatform.identity.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(max = 255, message = "First name must be at most 255 characters")
    private String firstName;

    @Size(max = 255, message = "Last name must be at most 255 characters")
    private String lastName;

    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    @Pattern(regexp = "^[a-z0-9_]+$", message = "Username may only contain lowercase letters, numbers, and underscores")
    private String username;
}
