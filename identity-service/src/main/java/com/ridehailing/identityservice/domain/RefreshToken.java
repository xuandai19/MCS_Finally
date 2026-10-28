package com.ridehailing.identityservice.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="refresh_tokens", indexes=@Index(name="idx_refresh_user",columnList="user_id"))
public class RefreshToken {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="user_id",nullable=false) private UUID userId;
 @Column(name="token_hash",nullable=false,unique=true,length=128) private String tokenHash;
 @Column(name="expires_at",nullable=false) private Instant expiresAt;
 @Column(name="revoked_at") private Instant revokedAt;
 protected RefreshToken(){}
 public RefreshToken(UUID userId,String tokenHash,Instant expiresAt){this.userId=userId;this.tokenHash=tokenHash;this.expiresAt=expiresAt;}
 public boolean isActive(Instant now){return revokedAt==null&&expiresAt.isAfter(now);}
 public void revoke(){revokedAt=Instant.now();}
 public UUID getUserId(){return userId;}
}
