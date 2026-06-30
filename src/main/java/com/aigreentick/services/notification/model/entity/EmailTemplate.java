package com.aigreentick.services.notification.model.entity;

import java.util.List;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.aigreentick.services.notification.model.interfaces.MongoBaseEntity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Document(collection = "email_template")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplate extends MongoBaseEntity {

    @Indexed(unique = true)
    private String templateCode;  //

    private String name;

    private String subject; 
    
    private String body; 

    private String userId;

    private boolean active;

    private List<String> variables;

}
