package com.winlife.dataanalysis.repository;

import com.winlife.dataanalysis.model.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByUserId(Long userId);

    List<Folder> findByIcon(String icon);
}