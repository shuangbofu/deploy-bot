package top.fusb.deploybot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import top.fusb.deploybot.model.converter.ApiTokenScopeListJsonConverter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 面向外部 Agent/CLI 的 API Token。
 */
@Data
@Entity
@Table(name = "api_tokens")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"tokenHash", "user"})
public class ApiTokenEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Token 名称。 */
    @Column(nullable = false, length = 100)
    private String name;

    /** Token 哈希，永远不保存明文。 */
    @JsonIgnore
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** Token 前缀，用于列表识别。 */
    @Column(nullable = false, length = 32)
    private String tokenPrefix;

    /** Token 归属用户。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /** Token 权限范围。 */
    @Convert(converter = ApiTokenScopeListJsonConverter.class)
    @Column(name = "scopes_json", columnDefinition = "CLOB")
    private List<ApiTokenScope> scopes = new ArrayList<>();

    /** 过期时间，空表示不过期。 */
    private LocalDateTime expiresAt;

    /** 最近使用时间。 */
    private LocalDateTime lastUsedAt;

    /** 最近使用 IP。 */
    @Column(length = 100)
    private String lastUsedIp;

    /** 是否启用。 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 撤销时间，非空表示不可再使用。 */
    private LocalDateTime revokedAt;
}
