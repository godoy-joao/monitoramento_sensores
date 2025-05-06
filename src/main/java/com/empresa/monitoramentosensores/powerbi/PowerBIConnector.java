package com.empresa.monitoramentosensores.powerbi;

import com.empresa.monitoramentosensores.model.ProcessedSensorData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class PowerBIConnector {

    @Value("${powerbi.streaming.url}")
    private String powerBIStreamingUrl;

    @Value("${powerbi.api.key:#{null}}")
    private String apiKey;

    private final ObjectMapper objectMapper;

    /**
     * Envia dados processados para o PowerBI utilizando a API de Streaming
     */
    public void sendDataToPowerBI(List<ProcessedSensorData> processedData) {
        if (processedData.isEmpty()) {
            log.info("Nenhum dado para enviar ao PowerBI");
            return;
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // Para cada tipo de sensor, enviamos para um conjunto de dados diferente no PowerBI
            Map<String, List<ProcessedSensorData>> bySensorType = processedData.stream()
                    .collect(Collectors.groupingBy(ProcessedSensorData::getSensorType));

            for (Map.Entry<String, List<ProcessedSensorData>> entry : bySensorType.entrySet()) {
                String sensorType = entry.getKey();
                List<ProcessedSensorData> dataSet = entry.getValue();

                // Transformar para o formato esperado pelo PowerBI
                List<Map<String, Object>> powerbiData = dataSet.stream()
                        .map(this::convertToPowerBIFormat)
                        .collect(Collectors.toList());

                // Envia os dados para o PowerBI
                HttpPost request = createPowerBIRequest(sensorType, powerbiData);

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getCode();

                    if (statusCode >= 200 && statusCode < 300) {
                        log.info("Dados enviados com sucesso para o PowerBI - Tipo de sensor: {}, Status: {}",
                                sensorType, statusCode);
                    } else {
                        log.error("Erro ao enviar dados para o PowerBI - Tipo de sensor: {}, Status: {}",
                                sensorType, statusCode);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erro na comunicação com o PowerBI: {}", e.getMessage(), e);
        }
    }

    private Map<String, Object> convertToPowerBIFormat(ProcessedSensorData data) {
        // Converte o objeto de dados processados para o formato esperado pelo PowerBI
        Map<String, Object> map = new HashMap<>();
        map.put("sensorId", data.getSensorId());
        map.put("sensorType", data.getSensorType());
        map.put("averageValue", Double.toString(data.getAverageValue()));
        map.put("minValue", Double.toString(data.getMinValue()));
        map.put("maxValue", Double.toString(data.getMaxValue()));
        map.put("standardDeviation", Double.toString(data.getStandardDeviation()));
        map.put("unit", data.getUnit());
        map.put("area", data.getArea());
        map.put("startPeriod", data.getStartPeriod().toString());
        map.put("endPeriod", data.getEndPeriod().toString());
        map.put("sampleCount", data.getSampleCount().toString());
        map.put("alertTriggered", data.getAlertTriggered().toString());

    }

    private HttpPost createPowerBIRequest(String sensorType, List<Map<String, Object>> data) throws Exception {
        // A URL pode ter um formato específico para cada tipo de sensor
        String url = powerBIStreamingUrl.replace("{sensorType}", sensorType);

        HttpPost request = new HttpPost(url);
        request.setHeader("Content-Type", "application/json");

        if (apiKey != null && !apiKey.isEmpty()) {
            request.setHeader("Authorization", "Bearer " + apiKey);
        }

        // Converte a lista de dados para JSON
        String jsonData = objectMapper.writeValueAsString(data);
        request.setEntity(new StringEntity(jsonData, ContentType.APPLICATION_JSON));

        return request;
    }
}