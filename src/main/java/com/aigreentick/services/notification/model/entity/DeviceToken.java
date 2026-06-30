package com.aigreentick.services.notification.model.entity;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.aigreentick.services.notification.enums.push.DevicePlatform;
import com.aigreentick.services.notification.model.interfaces.MongoBaseEntity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;



@Document(collection = "device_token")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceToken extends MongoBaseEntity  {
    private String userId;

    @Indexed(unique = true)
    private String deviceToken;

    private DevicePlatform platform;

    private String deviceModel;

    private String osVersion;

    private String appVersion;

    private boolean active;

    private String language;

}
