package com.empresa.monitoramentosensores.repository;

import com.empresa.monitoramentosensores.model.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SensorDataRepository extends JpaRepository<SensorData, Long> {

    // Busca dados por tipo de sensor
    List<SensorData> findBySensorType(String sensorType);

    // Busca dados por ID do sensor
    List<SensorData> findBySensorId(String sensorId);

    // Busca dados entre um intervalo de tempo
    List<SensorData> findByTimestampBetween(LocalDateTime startTime, LocalDateTime endTime);

    // Busca dados de um sensor específico em um intervalo de tempo
    List<SensorData> findBySensorIdAndTimestampBetween(
            String sensorId, LocalDateTime startTime, LocalDateTime endTime);

    // Busca dados com valor acima de um limite
    List<SensorData> findByValueGreaterThan(Double threshold);

    // Busca os últimos N registros para um sensor específico
    List<SensorData> findTop100BySensorIdOrderByTimestampDesc(String sensorId);
}
