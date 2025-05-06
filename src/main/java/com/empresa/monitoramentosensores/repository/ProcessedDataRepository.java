package com.empresa.monitoramentosensores.repository;

import com.empresa.monitoramentosensores.model.ProcessedSensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProcessedDataRepository extends JpaRepository<ProcessedSensorData, Long> {

    // Busca dados processados por tipo de sensor
    List<ProcessedSensorData> findBySensorType(String sensorType);

    // Busca dados processados por ID do sensor
    List<ProcessedSensorData> findBySensorId(String sensorId);

    // Busca dados processados por período
    List<ProcessedSensorData> findByStartPeriodGreaterThanEqualAndEndPeriodLessThanEqual(
            LocalDateTime startPeriod, LocalDateTime endPeriod);

    // Busca dados processados com alerta ativado
    List<ProcessedSensorData> findByAlertTriggeredTrue();

    // Busca os dados processados mais recentes
    List<ProcessedSensorData> findTop100ByOrderByEndPeriodDesc();

    // Busca os dados processados mais recentes para um tipo específico de sensor
    List<ProcessedSensorData> findTop100BySensorTypeOrderByEndPeriodDesc(String sensorType);
}