package com.empresa.monitoramentosensores.service;

import com.empresa.monitoramentosensores.model.SensorData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;

@Service
@Slf4j
public class AlertService {

    @Value("${alerts.temperature.max:35.0}")
    private Double maxTemperature;

    @Value("${alerts.temperature.min:10.0}")
    private Double minTemperature;

    @Value("${alerts.humidity.max:80.0}")
    private Double maxHumidity;

    @Value("${alerts.humidity.min:20.0}")
    private Double minHumidity;

    @Value("${alerts.pressure.max:1050.0}")
    private Double maxPressure;

    @Value("${alerts.pressure.min:950.0}")
    private Double minPressure;

    @Value("${alerts.batteryLevel.critical:10}")
    private Integer criticalBatteryLevel;

    // Mapa para armazenar limites de alerta para diferentes tipos de sensores
    private Map<String, AlertThreshold> alertThresholds;

    @PostConstruct
    public void init() {
        alertThresholds = new HashMap<>();

        // Configura limites para sensores de temperatura
        alertThresholds.put("temperature", new AlertThreshold(minTemperature, maxTemperature));

        // Configura limites para sensores de umidade
        alertThresholds.put("humidity", new AlertThreshold(minHumidity, maxHumidity));

        // Configura limites para sensores de pressão
        alertThresholds.put("pressure", new AlertThreshold(minPressure, maxPressure));

        log.info("Limites de alertas configurados para {} tipos de sensores", alertThresholds.size());
    }

    /**
     * Verifica se os dados do sensor atendem a condições para gerar alertas
     */
    public boolean checkAlertConditions(SensorData sensorData) {
        boolean alertTriggered = false;

        // Verifica nível de bateria crítico
        if (sensorData.getBatteryLevel() != null && sensorData.getBatteryLevel() <= criticalBatteryLevel) {
            log.warn("ALERTA: Nível de bateria crítico no sensor {}: {}%",
                    sensorData.getSensorId(), sensorData.getBatteryLevel());
            alertTriggered = true;
        }

        // Verifica se o tipo de sensor tem limites configurados
        String sensorType = sensorData.getSensorType().toLowerCase();
        AlertThreshold threshold = alertThresholds.get(sensorType);

        if (threshold != null && sensorData.getValue() != null) {
            double value = sensorData.getValue();

            // Verifica se o valor está fora dos limites
            if (value < threshold.getMin()) {
                log.warn("ALERTA: Valor abaixo do limite no sensor {} (tipo: {}): {} {} (mínimo: {})",
                        sensorData.getSensorId(), sensorType, value, sensorData.getUnit(), threshold.getMin());
                alertTriggered = true;
            } else if (value > threshold.getMax()) {
                log.warn("ALERTA: Valor acima do limite no sensor {} (tipo: {}): {} {} (máximo: {})",
                        sensorData.getSensorId(), sensorType, value, sensorData.getUnit(), threshold.getMax());
                alertTriggered = true;
            }
        }

        // Atualize o status do sensor com base no alerta
        if (alertTriggered) {
            sensorData.setStatus("ALERTA");
        } else {
            sensorData.setStatus("NORMAL");
        }

        return alertTriggered;
    }

    /**
     * Classe interna para armazenar os limites mínimo e máximo para alertas
     */
    private static class AlertThreshold {
        private final Double min;
        private final Double max;

        public AlertThreshold(Double min, Double max) {
            this.min = min;
            this.max = max;
        }

        public Double getMin() {
            return min;
        }

        public Double getMax() {
            return max;
        }
    }
}
