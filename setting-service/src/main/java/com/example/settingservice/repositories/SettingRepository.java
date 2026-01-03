package com.example.settingservice.repositories;


import com.example.settingservice.entities.SettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<SettingEntity, String> {}
