package com.palworldadmin.app.repository;

import com.palworldadmin.app.entity.PalworldServer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PalworldServerRepository extends JpaRepository<PalworldServer, Long> {
    List<PalworldServer> findAllByOrderByNameAsc();
}
