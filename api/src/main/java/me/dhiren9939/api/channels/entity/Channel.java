package me.dhiren9939.api.channels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name= "channels")
public class Channel {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID channelId;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String callBackUrl;

    public Channel(UUID tenantId, String callBackUrl) {
        this.tenantId = tenantId;
        this.callBackUrl = callBackUrl;
    }
}
