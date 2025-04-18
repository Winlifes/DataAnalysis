// src/main/java/com/winlife/dataanalysis/model/EventSchema.java
package com.winlife.dataanalysis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "event_schemas") // 数据库表名
@Data
@NoArgsConstructor
public class EventSchema {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false) // 事件名称唯一且非空
    private String eventName;

    // 使用 JSON 类型存储参数的结构定义
    // 格式示例: {"param1": "string", "param2": "integer", "param3": {"type": "boolean", "required": true}}
    @Column(columnDefinition = "JSON")
    private String parameterSchema;
}