package com.winlife.dataanalysis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "user_property_schema") // 用户属性结构表
@Data
@NoArgsConstructor
public class UserPropertySchema {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 可以使用固定 ID，比如 1

    // 使用 JSON 类型存储用户属性的结构定义。
    // 格式示例: {"level": "integer", "vip_status": "string", "registration_date": {"type": "long", "required": true}}
    @Column(columnDefinition = "JSON")
    private String propertySchema;
}