package top.fusb.deploybot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模板定义了构建脚本、发布脚本以及对外暴露的变量结构。
 */
@Data
@Entity
@Table(name = "templates")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TemplateEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模板名称。 */
    @Column(nullable = false, unique = true)
    private String name;

    /** 模板说明。 */
    @Column(length = 1000)
    private String description;

    /** 模板类型。 */
    @Column(name = "icon_type", length = 100)
    private String templateType;

    /** 本机构建脚本。 */
    @Lob
    @Column(nullable = false)
    private String buildScriptContent;

    /** 目标主机发布脚本。 */
    @Lob
    private String deployScriptContent;

    /** 模板变量定义 JSON。 */
    @Lob
    @Column(nullable = false)
    private String variablesSchema;

    /** 是否需要记录并管理发布后的进程。 */
    @Column
    private Boolean monitorProcess = false;
}
