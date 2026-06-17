package com.ttg.devknowledgeplatform.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.dto.UserInfoResponse;
import com.ttg.devknowledgeplatform.service.StorageService;

@Mapper(componentModel = "spring")
public abstract class UserMapper {

    @Autowired
    protected StorageService storageService;

    @Mapping(source = "userUuid", target = "id")
    @Mapping(source = "dteCreation", target = "createdAt")
    @Mapping(source = "dteLastModification", target = "lastModified")
    @Mapping(target = "profilePicture", ignore = true)
    public abstract UserInfoResponse toUserInfo(User user);

    @AfterMapping
    protected void resolveProfilePicture(User user, @MappingTarget UserInfoResponse.UserInfoResponseBuilder builder) {
        String pic = user.getProfilePicture();
        if (pic != null && !pic.startsWith("http")) {
            pic = storageService.getPresignedUrl(pic);
        }
        builder.profilePicture(pic);
    }
}
