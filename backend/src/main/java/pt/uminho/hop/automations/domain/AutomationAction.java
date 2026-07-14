package pt.uminho.hop.automations.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "automation_action")
public class AutomationAction {

    public enum Type { WEBHOOK }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "automation_id", nullable = false)
    private Automation automation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type = Type.WEBHOOK;

    /** JSON: { "url": "...", "method": "POST", "headers": {...}, "payloadTemplate": "..." } */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String config;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    public UUID getId() { return id; }
    public Automation getAutomation() { return automation; }
    public void setAutomation(Automation automation) { this.automation = automation; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}
