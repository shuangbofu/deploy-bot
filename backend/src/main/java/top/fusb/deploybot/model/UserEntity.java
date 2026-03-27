package top.fusb.deploybot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 平台用户。
 */
@Data
@Entity
@Table(name = "users")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"passwordHash", "authToken"})
public class UserEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录用户名。 */
    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /** 展示名称。 */
    @Column(nullable = false, length = 100)
    private String displayName;

    /** 密码哈希。 */
    @JsonIgnore
    @Column(nullable = false, length = 255)
    private String passwordHash;

    /** 用户角色。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    /** 是否启用。 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 当前登录令牌。 */
    @JsonIgnore
    @Column(length = 255)
    private String authToken;

    /** 令牌过期时间。 */
    @JsonIgnore
    private LocalDateTime authTokenExpiresAt;

    /** 创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
