package com.winlife.dataanalysis.repository;

import com.winlife.dataanalysis.model.UserPropertySchema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPropertySchemaRepository extends JpaRepository<UserPropertySchema, Long> {
    // 获取第一个（也是唯一的）用户属性结构定义
    Optional<UserPropertySchema> findFirstByOrderByIdAsc();
}