package com.assetbooking.AssetBookingPortal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findFirstByAssetType(String assetType);

    Optional<Asset> findByAssetName(String assetName);

    List<Asset> findByAssetType(String assetType);
    long countByStatus(String status);
}