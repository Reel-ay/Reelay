package com.reelay.video.repository;

import com.reelay.video.entity.VideoScene;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VideoSceneRepository extends JpaRepository<VideoScene, Long> {

    List<VideoScene> findByJobIdOrderByCutOrderAsc(UUID jobId);

    Optional<VideoScene> findByJobIdAndCutOrder(UUID jobId, int cutOrder);
}
