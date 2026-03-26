package top.fusb.deploybot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 服务记录用于关联部署后的后台进程状态。
 */
@Data
@Entity
@Table(name = "services")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"pipeline", "lastDeployment"})
public class ServiceEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 服务归属流水线。 */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "pipeline_id")
    private PipelineEntity pipeline;

    /** 最近一次影响该服务的部署。 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "last_deployment_id")
    private DeploymentEntity lastDeployment;

    /** 服务名称。 */
    private String serviceName;

    /** 当前受管 PID。 */
    private Long currentPid;

    /** 服务状态。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private ServiceStatus status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
