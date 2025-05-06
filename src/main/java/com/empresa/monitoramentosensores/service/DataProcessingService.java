package com.empresa.monitoramentosensores.service;

import com.empresa.monitoramentosensores.model.SensorData;
import com.empresa.monitoramentosensores.powerbi.PowerBIConnector;
import com.empresa.monitoramentosensores.repository.ProcessedDataRepository;
import com.empresa.monitoramentosensores.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataProcessingService {

    private final SensorDataRepository sensorDataRepository;
    private final ProcessedDataRepository processedDataRepository;
    private final AlertService alertService;
    private final PowerBIConnector powerBIConnector;

    // Cache para armazenar dados temporários antes do processamento em lote
    private final Map<String, List<SensorData>> sensorDataCache = new ConcurrentHashMap<>();

    public DataProcessingService(SensorDataRepository sensorDataRepository, ProcessedDataRepository processedDataRepository, AlertService alertService, PowerBIConnector powerBIConnector) {
        this.sensorDataRepository = sensorDataRepository;
        this.processedDataRepository = processedDataRepository;
        this.alertService = alertService;
        this.powerBIConnector = powerBIConnector;
    }

    /**
     * Processa os dados recebidos do sensor e os armazena no repositório
     */
    public void processSensorData(SensorData sensorData) {
        // Se o timestamp não foi fornecido, usar o momento atual
        if (sensorData.getTimestamp() == null) {
            sensorData.setTimestamp(LocalDateTime.now());
        }

        // Salva os dados brutos no banco de dados
        sensorDataRepository.save(sensorData);

        // Verificar se há condições de alerta
        alertService.checkAlertConditions(sensorData);

        log.info("Dados do sensor {} processados e armazenados: valor={} {}",
                sensorData.getSensorId(), sensorData.getValue(), sensorData.getUnit());
    }

    /**
     * Processa dados em lote a cada 5 minutos
     */
    @Scheduled(fixedRate = 300000) // 5 minutos
    public void processBatch() {
        log.info("Iniciando processamento em lote dos dados de sensores");
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(5);

        // Busca dados dos últimos 5 minutos
        List<SensorData> recentData = sensorDataRepository.findByTimestampBetween(startTime, endTime);

        if (recentData.isEmpty()) {
            log.info("Nenhum dado recente encontrado para processamento");
            return;
        }

        // Agrupa por tipo de sensor
        Map<String, List<SensorData>> groupedData = recentData.stream()
                .collect(Collectors.groupingBy(SensorData::getSensorType));

        // Processa cada tipo de sensor
        groupedData.forEach(this::processGroupedData);

        // Envia dados processados para o PowerBI
        sendDataToPowerBI();

        log.info("Processamento em lote concluído para {} tipos de sensores", groupedData.size());
    }

    private void processGroupedData(String sensorType, List<SensorData> dataList) {
        log.debug("Processando dados agrupados para o tipo de sensor: {}", sensorType);

        // Agrupa por ID do sensor
        Map<String, List<SensorData>> bySensorId = dataList.stream()
                .collect(Collectors.groupingBy(SensorData::getSensorId));

        // Processa cada sensor individualmente
        bySensorId.forEach((sensorId, sensorDataList) -> {
            DoubleSummaryStatistics stats = sensorDataList.stream()
                    .mapToDouble(SensorData::getValue)
                    .summaryStatistics();

            // Calcula desvio padrão
            double sum = sensorDataList.stream()
                    .mapToDouble(data -> Math.pow(data.getValue() - stats.getAverage(), 2))
                    .sum();
            double stdDev = Math.sqrt(sum / sensorDataList.size());

            // Determina a unidade (assume-se que todos os dados do mesmo sensor têm a mesma unidade)
            String unit = sensorDataList.get(0).getUnit();

            // Cria objeto de dados processados
            ProcessedSensorData processedData = ProcessedSensorData.builder()
                    .sensorId(sensorId)
                    .sensorType(sensorType)
                    .averageValue(stats.getAverage())
                    .minValue(stats.getMin())
                    .maxValue(stats.getMax())
                    .standardDeviation(stdDev)
                    .unit(unit)
                    .area(determineArea(sensorDataList))
                    .startPeriod(sensorDataList.stream()
                            .map(SensorData::getTimestamp)
                            .min(LocalDateTime::compareTo)
                            .orElse(null))
                    .endPeriod(sensorDataList.stream()
                            .map(SensorData::getTimestamp)
                            .max(LocalDateTime::compareTo)
                            .orElse(null))
                    .sampleCount(sensorDataList.size())
                    .alertTriggered(false)
                    .build();

            // Salvar dados processados
            processedDataRepository.save(processedData);

            log.info("Dados processados para sensor {}: média={}, min={}, max={}, amostras={}",
                    sensorId, stats.getAverage(), stats.getMin(), stats.getMax(), stats.getCount());
        });
    }

    private String determineArea(List<SensorData> sensorDataList) {
        // Lógica simples para determinar a área com base nas coordenadas
        // Esta é uma implementação de exemplo. Para aplicações reais,
        // você poderia usar geofencing ou mapas mais complexos

        // Verifica se há dados de coordenadas
        boolean hasCoordinates = sensorDataList.stream()
                .anyMatch(data -> data.getLatitude() != null && data.getLongitude() != null);

        if (!hasCoordinates) {
            return "Desconhecida";
        }

        // Calcula o centroide das coordenadas
        double avgLat = sensorDataList.stream()
                .filter(data -> data.getLatitude() != null)
                .mapToDouble(SensorData::getLatitude)
                .average()
                .orElse(0);

        double avgLon = sensorDataList.stream()
                .filter(data -> data.getLongitude() != null)
                .mapToDouble(SensorData::getLongitude)
                .average()
                .orElse(0);

        // Exemplo simples de determinar uma área com base no centroide
        // Em uma aplicação real, você usaria um mapeamento mais preciso
        if (avgLat > 0) {
            if (avgLon > 0) return "Nordeste";
            else return "Noroeste";
        } else {
            if (avgLon > 0) return "Sudeste";
            else return "Sudoeste";
        }
    }

    private void sendDataToPowerBI() {
        try {
            // Busca os dados processados mais recentes
            List<ProcessedSensorData> recentProcessed =
                    processedDataRepository.findTop100ByOrderByEndPeriodDesc();

            // Envia para o PowerBI
            powerBIConnector.sendDataToPowerBI(recentProcessed);

            log.info("Dados enviados para o PowerBI com sucesso");
        } catch (Exception e) {
            log.error("Erro ao enviar dados para o PowerBI: {}", e.getMessage(), e);
        }
    }
}
