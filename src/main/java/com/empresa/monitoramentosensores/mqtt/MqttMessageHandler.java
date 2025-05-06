package com.empresa.monitoramentosensores.mqtt;

import com.empresa.monitoramentosensores.model.SensorData;
import com.empresa.monitoramentosensores.service.DataProcessingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class MqttMessageHandler implements MqttCallback {

    private final ObjectMapper objectMapper;
    private final DataProcessingService dataProcessingService;

    @Override
    public void connectionLost(Throwable cause) {
        log.error("Conexão com o broker MQTT perdida", cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        try {
            log.debug("Mensagem recebida do tópico {}: {}", topic, new String(message.getPayload()));

            // Converte a mensagem JSON para o objeto SensorData
            SensorData sensorData = objectMapper.readValue(message.getPayload(), SensorData.class);

            // Adiciona informação do tópico ao objeto para processamento
            sensorData.setTopic(topic);

            // Processa os dados do sensor
            dataProcessingService.processSensorData(sensorData);

        } catch (Exception e) {
            log.error("Erro ao processar mensagem MQTT: {}", e.getMessage(), e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Este método é chamado quando uma mensagem publicada é entregue
        // Como estamos apenas subscrevendo, não é necessário implementar
    }
}
